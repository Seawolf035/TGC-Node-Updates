package club.thegamerclub.nodelab;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.core.content.ContextCompat;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.concurrent.TimeUnit;

/**
 * Opt-in online transitions for PINNED personal VRChat friends only.
 * One shared account-scoped baseline is consumed by the overlay and WorkManager
 * so neither mechanism duplicates the other's notifications.
 */
public final class PinnedFriendAlerts {
    private static final String PREF="tgc_pinned_friend_online_alerts";
    private static final String PERIODIC="tgc_pinned_friend_online_periodic";
    private static final String INITIAL="tgc_pinned_friend_online_initial";
    private static final long OVERLAY_FRESH_MS=7*60_000L;
    private PinnedFriendAlerts(){}
    private static String account(Context ctx){
        String uid;
        try{uid=VRChatSession.load(ctx).optString("userId","");}
        catch(Exception ex){throw new SecurityException("Sign in to VRChat first.",ex);}
        if(!uid.matches("usr_[0-9a-fA-F-]{36}"))
            throw new SecurityException("Sign in to VRChat first.");
        return uid;
    }
    private static SharedPreferences prefs(Context ctx){
        return ctx.getSharedPreferences(PREF,Context.MODE_PRIVATE);
    }
    public static boolean permission(Context ctx){
        return Build.VERSION.SDK_INT<33 ||
            ContextCompat.checkSelfPermission(ctx,Manifest.permission.POST_NOTIFICATIONS)
                ==PackageManager.PERMISSION_GRANTED;
    }
    public static boolean enabled(Context ctx){
        try{return prefs(ctx).getBoolean(account(ctx)+"_enabled",false);}
        catch(Exception ex){return false;}
    }
    public static boolean active(Context ctx){return permission(ctx)&&enabled(ctx);}
    private static Constraints network(){
        return new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED)
            .build();
    }
    public static void setEnabled(Context ctx,boolean enable){
        if(!StaffGate.unlocked(ctx))
            throw new SecurityException("Verified staff access required.");
        if(enable&&!permission(ctx))
            throw new SecurityException("Allow Android notifications first.");
        String uid=account(ctx);
        SharedPreferences.Editor edit=prefs(ctx).edit()
            .putBoolean(uid+"_enabled",enable);
        if(enable){
            // Never announce friends already online when enabling alerts.
            edit.remove(uid+"_seeded").remove(uid+"_states").remove(uid+"_last");
        }
        edit.apply();
        WorkManager wm=WorkManager.getInstance(ctx.getApplicationContext());
        if(!enable){
            wm.cancelUniqueWork(PERIODIC);
            wm.cancelUniqueWork(INITIAL);
            return;
        }
        restore(ctx);
        wm.enqueueUniqueWork(INITIAL,ExistingWorkPolicy.REPLACE,
            new OneTimeWorkRequest.Builder(PinnedFriendAlertWorker.class)
                .setConstraints(network()).build());
    }
    /** Reattach periodic work after reboot/update, never silently opt in. */
    public static void restore(Context ctx){
        if(!active(ctx))return;
        WorkManager.getInstance(ctx.getApplicationContext())
            .enqueueUniquePeriodicWork(PERIODIC,ExistingPeriodicWorkPolicy.KEEP,
                new PeriodicWorkRequest.Builder(PinnedFriendAlertWorker.class,
                    15,TimeUnit.MINUTES).setConstraints(network()).build());
    }
    public static long lastObservation(Context ctx){
        try{return prefs(ctx).getLong(account(ctx)+"_last",0);}
        catch(Exception ex){return 0;}
    }
    /** Avoid unnecessary duplicate background full friend-list queries. */
    public static boolean recentOverlayObservation(Context ctx){
        return System.currentTimeMillis()-lastObservation(ctx)<OVERLAY_FRESH_MS;
    }
    public static final class OnlineFriend {
        public final String id,name;
        OnlineFriend(String userId,String display){id=userId;name=display;}
    }
    /**
     * Use ONLY complete online+offline friend snapshots, never a partial
     * friends page. A new account, newly pinned friend, or first observation
     * creates a baseline without sending an alert.
     */
    public static synchronized ArrayList<OnlineFriend> observe(
        Context ctx,JSONObject session,JSONArray complete)throws Exception{
        ArrayList<OnlineFriend> alerts=new ArrayList<>();
        if(!active(ctx))return alerts;
        String uid=account(ctx);
        if(!uid.equals(session.optString("userId","")))
            throw new SecurityException("VRChat account changed.");
        JSONArray pinned=FriendPinStore.pins(ctx);
        if(pinned.length()==0)return alerts;
        SharedPreferences store=prefs(ctx);
        JSONObject previous;
        try{previous=new JSONObject(store.getString(uid+"_states","{}"));}
        catch(Exception ignored){previous=new JSONObject();}
        boolean seeded=store.getBoolean(uid+"_seeded",false);
        JSONObject actual=new JSONObject();
        for(int i=0;i<complete.length();i++){
            JSONObject f=complete.optJSONObject(i);
            if(f==null)continue;
            String id=f.optString("id","");
            if(!id.matches("usr_[0-9a-fA-F-]{36}"))continue;
            actual.put(id,f);
        }
        JSONObject current=new JSONObject();
        HashSet<String> visited=new HashSet<>();
        for(int i=0;i<pinned.length();i++){
            JSONObject row=pinned.optJSONObject(i);
            if(row==null)continue;
            String id=row.optString("id","");
            if(!visited.add(id))continue;
            JSONObject person=actual.optJSONObject(id);
            // Absent users are never assumed offline or blocked.
            if(person==null)continue;
            boolean online=person.optBoolean("isOnline",false);
            current.put(id,online);
            if(seeded && previous.has(id) &&
               !previous.optBoolean(id,false) && online){
                String display=person.optString("displayName",
                    row.optString("name","Friend"));
                if(display.isEmpty())display="Pinned friend";
                if(display.length()>70)display=display.substring(0,70);
                alerts.add(new OnlineFriend(id,display));
            }
        }
        // Commit synchronously before posting; simultaneous overlay/worker
        // checks cannot create duplicate announcements.
        boolean committed=store.edit()
            .putString(uid+"_states",current.toString())
            .putBoolean(uid+"_seeded",true)
            .putLong(uid+"_last",System.currentTimeMillis())
            .commit();
        if(!committed)alerts.clear();
        return alerts;
    }
}
