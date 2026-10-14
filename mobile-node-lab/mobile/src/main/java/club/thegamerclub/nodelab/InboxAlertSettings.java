package club.thegamerclub.nodelab;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import org.json.JSONArray;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
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
        public final int newUnread,invites,inviteRequests,friends,other;
        Changes(int n,int i,int r,int f,int o){
            newUnread=n;invites=i;inviteRequests=r;friends=f;other=o;
        }
    }
    /**
     * Stores only account-scoped hashes, never raw notifications or VRChat
     * credentials. Each endpoint is baselined independently, so a legacy API
     * outage cannot cause an alert flood when it recovers.
     */
    public static synchronized Changes remember(
        Context ctx,InboxRepository.Snapshot snapshot)throws Exception{
        JSONObject session=VRChatSession.load(ctx);
        String account=session.optString("userId","");
        if(!account.matches("usr_[0-9a-fA-F-]{36}"))
            throw new SecurityException("No signed-in VRChat account.");
        String accountHash=hash(account);
        android.content.SharedPreferences prefs=ctx.getSharedPreferences(PREF,0);
        boolean sameAccount=accountHash.equals(prefs.getString("account",""));
        boolean legacySeeded=sameAccount&&prefs.getBoolean("legacy_seeded",false);
        boolean modernSeeded=sameAccount&&prefs.getBoolean("modern_seeded",false);
        java.util.LinkedHashSet<String> known=new java.util.LinkedHashSet<>();
        if(sameAccount){
            JSONArray previous;
            try{previous=new JSONArray(prefs.getString("known","[]"));}
            catch(Exception e){previous=new JSONArray();}
            for(int i=0;i<previous.length();i++){
                String key=previous.optString(i,"");
                if(!key.isEmpty())known.add(key);
            }
        }
        int added=0,invites=0,inviteRequests=0,friends=0,other=0;
        for(int i=0;i<snapshot.items.length();i++){
            JSONObject item=snapshot.items.optJSONObject(i);
            if(item==null)continue;
            String id=item.optString("id","");
            if(id.isEmpty())continue;
            String key=hash(id);
            if(known.add(key)){
                boolean eligible=item.optBoolean("_modern",false)?
                    modernSeeded:legacySeeded;
                if(sameAccount&&eligible&&!item.optBoolean("seen",false)){
                    added++;
                    String type=item.optString("type","").toLowerCase(Locale.US);
                    if(type.equals("requestinvite"))inviteRequests++;
                    else if(type.contains("invite"))invites++;
                    else if(type.contains("friend"))friends++;
                    else other++;
                }
            }
        }
        JSONArray bounded=new JSONArray();
        int skip=Math.max(0,known.size()-500),index=0;
        for(String key:known){
            if(index++>=skip)bounded.put(key);
        }
        prefs.edit().putString("account",accountHash)
            .putString("known",bounded.toString())
            .putBoolean("legacy_seeded",legacySeeded||snapshot.legacyAvailable)
            .putBoolean("modern_seeded",modernSeeded||snapshot.modernAvailable)
            .apply();
        return new Changes(added,invites,inviteRequests,friends,other);
    }
}
