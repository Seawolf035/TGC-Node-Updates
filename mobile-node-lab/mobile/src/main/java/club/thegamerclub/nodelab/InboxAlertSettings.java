package club.thegamerclub.nodelab;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import org.json.JSONArray;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.Locale;
import androidx.core.content.ContextCompat;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import java.util.concurrent.TimeUnit;

/**
 * Explicit opt-in only. Background checks don't require Shizuku, the Node,
 * floating windows, or VRChat Mobile on this phone.
 */
public final class InboxAlertSettings {
    private static final String PREF="tgc_inbox_alerts_v1";
    private static final String PERIODIC="tgc_inbox_periodic";
    private static final String FIRST="tgc_inbox_initial";
    private InboxAlertSettings(){}
    public static boolean enabled(Context ctx){
        return ctx.getSharedPreferences(PREF,0).getBoolean("enabled",false);
    }
    public static boolean permissionGranted(Context ctx){
        return Build.VERSION.SDK_INT<33 ||
            ContextCompat.checkSelfPermission(ctx,Manifest.permission.POST_NOTIFICATIONS)
                ==PackageManager.PERMISSION_GRANTED;
    }
    public static boolean active(Context ctx){return enabled(ctx)&&permissionGranted(ctx);}
    public static void setEnabled(Context ctx,boolean enabled){
        if(enabled&&!permissionGranted(ctx))
            throw new SecurityException("Allow Android notifications first.");
        ctx.getSharedPreferences(PREF,0).edit().putBoolean("enabled",enabled).apply();
        WorkManager mgr=WorkManager.getInstance(ctx.getApplicationContext());
        if(!enabled){
            mgr.cancelUniqueWork(PERIODIC);
            mgr.cancelUniqueWork(FIRST);
            return;
        }
        Constraints network=new Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED).build();
        PeriodicWorkRequest recurring=new PeriodicWorkRequest.Builder(
            InboxAlertWorker.class,15,TimeUnit.MINUTES)
            .setConstraints(network).build();
        mgr.enqueueUniquePeriodicWork(PERIODIC,ExistingPeriodicWorkPolicy.KEEP,recurring);
        mgr.enqueueUniqueWork(FIRST,ExistingWorkPolicy.KEEP,
            new OneTimeWorkRequest.Builder(InboxAlertWorker.class)
                .setConstraints(network).build());
    }
    /** Rehydrate the periodic schedule without silently opting anyone in. */
    public static void restore(Context ctx){
        if(enabled(ctx)&&permissionGranted(ctx)){
            Constraints network=new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED).build();
            WorkManager.getInstance(ctx.getApplicationContext()).enqueueUniquePeriodicWork(
                PERIODIC,ExistingPeriodicWorkPolicy.KEEP,
                new PeriodicWorkRequest.Builder(InboxAlertWorker.class,15,TimeUnit.MINUTES)
                    .setConstraints(network).build());
        }
    }
    private static String hash(String raw){
        try{
            byte[] bytes=MessageDigest.getInstance("SHA-256")
                .digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder s=new StringBuilder();
            for(byte b:bytes)s.append(String.format(Locale.US,"%02x",b&0xFF));
            return s.toString();
        }catch(Exception e){throw new IllegalStateException(e);}
    }
    public static final class Changes {
        public final int newUnread,invites,friends;
        Changes(int n,int i,int f){newUnread=n;invites=i;friends=f;}
    }
    /** This stores *only* hashed IDs; never raw messages or VRChat cookies. */
    public static synchronized Changes remember(Context ctx,JSONArray items)throws Exception{
        JSONObject session=VRChatSession.load(ctx);
        String account=session.optString("userId","");
        if(!account.matches("usr_[0-9a-fA-F-]{36}"))
            throw new SecurityException("No signed-in VRChat account.");
        String accountHash=hash(account);
        android.content.SharedPreferences prefs=ctx.getSharedPreferences(PREF,0);
        boolean sameAccount=accountHash.equals(prefs.getString("account",""));
        HashSet<String> known=new HashSet<>();
        if(sameAccount){
            JSONArray old;
            try{old=new JSONArray(prefs.getString("known","[]"));}
            catch(Exception e){old=new JSONArray();}
            for(int i=0;i<old.length();i++)known.add(old.optString(i,""));
        }
        int added=0,invites=0,friends=0;
        // Preserve the entire bounded account-specific ID history, preventing
        // old invites from alerting repeatedly across partial API responses.
        JSONArray merged=new JSONArray();
        for(String prior:known)if(!prior.isEmpty())merged.put(prior);
        for(int i=0;i<items.length();i++){
            JSONObject item=items.optJSONObject(i);
            if(item==null)continue;
            String id=item.optString("id","");
            if(id.isEmpty())continue;
            String key=hash(id);
            if(known.add(key)){
                merged.put(key);
                if(sameAccount&&!item.optBoolean("seen",false)){
                    added++;
                    String type=item.optString("type","").toLowerCase(Locale.US);
                    if(type.contains("invite"))invites++;
                    else if(type.contains("friend"))friends++;
                }
            }
        }
        // Keep max 500 hashes. The initial/account-switch observation establishes
        // a baseline: it must not alert for old pending requests.
        JSONArray bounded=new JSONArray();
        int first=Math.max(0,merged.length()-500);
        for(int i=first;i<merged.length();i++)bounded.put(merged.optString(i,""));
        prefs.edit().putString("account",accountHash)
            .putString("known",bounded.toString()).apply();
        return new Changes(sameAccount?added:0,sameAccount?invites:0,sameAccount?friends:0);
    }
}
