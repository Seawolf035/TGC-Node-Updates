package club.thegamerclub.nodelab;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.content.pm.PackageManager;
import android.Manifest;
import rikka.shizuku.Shizuku;

/** Opt-in, foreground-only automatic tracking; never opens permission prompts. */
public final class TrackingAutoStart {
    private static final String PREF="tgc_tracking_autostart";
    private static long lastAttempt=0L;
    private TrackingAutoStart(){}
    public static boolean enabled(Context c){
        return c.getSharedPreferences(PREF,0).getBoolean("enabled",false);
    }
    public static void setEnabled(Context c,boolean enabled){
        c.getSharedPreferences(PREF,0).edit().putBoolean("enabled",enabled).apply();
    }
    public static void maybeStart(Activity activity){
        if(!enabled(activity)||!StaffGate.unlocked(activity)||
           !CompanionLicense.accepted(activity)||
           !CompanionSetupState.isComplete(activity)||
           !TrackingSetupGuide.acknowledged(activity))return;
        long now=System.currentTimeMillis();
        if(now-lastAttempt<120_000L)return;
        android.content.SharedPreferences state=
            activity.getSharedPreferences("tgc_mobile_node_status",0);
        long heartbeat=state.getLong("service_heartbeat_at",0L);
        if(state.getBoolean("running",false)&&heartbeat>0L&&
           now-heartbeat<90_000L)return;
        try{
            if(MobileCredentials.load(activity)==null)return;
            if(QuestSafLogWatcher.isSelected(activity)){
                if(!QuestSafLogWatcher.hasGrant(activity))return;
            }else if(!Shizuku.pingBinder()||
                Shizuku.checkSelfPermission()!=PackageManager.PERMISSION_GRANTED)return;
            if(Build.VERSION.SDK_INT>=33 &&
                activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=
                    PackageManager.PERMISSION_GRANTED)return;
            lastAttempt=now;
            Intent start=new Intent(activity,MobileNodeService.class)
                .setAction(MobileNodeService.START);
            if(Build.VERSION.SDK_INT>=26)activity.startForegroundService(start);
            else activity.startService(start);
        }catch(Exception ignored){
            // The user can use the explicit Start action for troubleshooting.
        }
    }
}
