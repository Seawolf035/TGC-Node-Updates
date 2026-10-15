package club.thegamerclub.nodelab;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import org.json.JSONObject;
import java.util.concurrent.TimeUnit;

/** Separate, optional app-update alerts for staff and owner-approved delegates.
 * Only the existing validated signed-update feed is queried. No silent install.
 */
public final class CompanionUpdateAlerts {
    private static final String PREF="tgc_app_update_alerts_v1";
    private static final String CHANNEL="tgc_companion_updates_v1";
    private static final String PERIODIC="tgc_companion_update_check";
    private static final String IMMEDIATE="tgc_companion_update_foreground";
    private static final int NOTIFICATION_ID=5768;
    private CompanionUpdateAlerts(){}

    public static boolean enabled(Context ctx){
        return ctx.getSharedPreferences(PREF,0).getBoolean("enabled",true);
    }
    public static boolean permission(Context ctx){
        return Build.VERSION.SDK_INT<33 ||
            ContextCompat.checkSelfPermission(ctx,Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
    }
    public static boolean notificationsAllowed(Context ctx){
        if(!permission(ctx))return false;
        NotificationManager nm=(NotificationManager)ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if(nm==null||!nm.areNotificationsEnabled())return false;
        if(Build.VERSION.SDK_INT>=26){
            NotificationChannel channel=nm.getNotificationChannel(CHANNEL);
            if(channel!=null&&channel.getImportance()==NotificationManager.IMPORTANCE_NONE)return false;
        }
        return true;
    }
    public static void setEnabled(Context ctx,boolean on){
        ctx.getSharedPreferences(PREF,0).edit().putBoolean("enabled",on).apply();
        if(!on){
            WorkManager manager=WorkManager.getInstance(ctx.getApplicationContext());
            manager.cancelUniqueWork(PERIODIC);
            manager.cancelUniqueWork(IMMEDIATE);
            NotificationManager notifier=(NotificationManager)
                ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            if(notifier!=null)notifier.cancel(NOTIFICATION_ID);
        }else restore(ctx);
    }
    public static void ensureChannel(Context ctx){
        if(Build.VERSION.SDK_INT<26)return;
        NotificationManager mgr=(NotificationManager)
            ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if(mgr==null)return;
        NotificationChannel channel=new NotificationChannel(CHANNEL,
            "Companion app updates",NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription("An official signed Companion update is ready. "+
            "Tap to review and install it using the in-app updater.");
        channel.enableVibration(true);
        channel.setShowBadge(true);
        channel.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
        mgr.createNotificationChannel(channel);
    }
    private static Constraints online(){
        return new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build();
    }
    public static void restore(Context ctx){
        if(!enabled(ctx))return;
        ensureChannel(ctx);
        WorkManager.getInstance(ctx.getApplicationContext()).enqueueUniquePeriodicWork(
            PERIODIC,ExistingPeriodicWorkPolicy.KEEP,
            new PeriodicWorkRequest.Builder(CompanionUpdateWorker.class,6,TimeUnit.HOURS)
                .setConstraints(online()).build());
    }
    public static void checkNow(Context ctx){
        if(!enabled(ctx))return;
        WorkManager.getInstance(ctx.getApplicationContext()).enqueueUniqueWork(
            IMMEDIATE,ExistingWorkPolicy.REPLACE,
            new OneTimeWorkRequest.Builder(CompanionUpdateWorker.class)
                .setConstraints(online()).build());
    }
    public static void checkOnOpen(Context ctx){
        if(!enabled(ctx))return;
        long now=System.currentTimeMillis();
        android.content.SharedPreferences pref=ctx.getSharedPreferences(PREF,0);
        long previous=pref.getLong("last_foreground_check",0);
        if(now-previous<30*60_000L && now>=previous)return;
        pref.edit().putLong("last_foreground_check",now).apply();
        WorkManager.getInstance(ctx.getApplicationContext()).enqueueUniqueWork(
            IMMEDIATE,ExistingWorkPolicy.KEEP,
            new OneTimeWorkRequest.Builder(CompanionUpdateWorker.class)
                .setConstraints(online()).build());
    }
    @SuppressWarnings("deprecation")
    static int localCode(Context ctx)throws Exception{
        PackageInfo info=ctx.getPackageManager().getPackageInfo(ctx.getPackageName(),0);
        return Build.VERSION.SDK_INT>=28?(int)info.getLongVersionCode():info.versionCode;
    }
    /** Called by worker after validating schema/channel/digest/APK URL. */
    static synchronized void consider(Context ctx,JSONObject release)throws Exception{
        if(!enabled(ctx))return;
        int installed=localCode(ctx),available=release.getInt("version_code");
        android.content.SharedPreferences pref=ctx.getSharedPreferences(PREF,0);
        NotificationManager nm=(NotificationManager)
            ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if(available<=installed){
            if(nm!=null)nm.cancel(NOTIFICATION_ID);
            return;
        }
        if(!notificationsAllowed(ctx)||nm==null)return;
        // A previously dismissed notification can be reissued on a later app open.
        long previousAt=pref.getLong("last_notified_at",0L);
        if(available<=pref.getInt("last_notified_code",0) &&
            System.currentTimeMillis()-previousAt<24*60*60_000L)return;
        ensureChannel(ctx);
        Intent target=new Intent(ctx,MobileUpdaterActivity.class)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent open=PendingIntent.getActivity(ctx,NOTIFICATION_ID,target,
            PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        String name=release.optString("version",String.valueOf(available));
        Notification notice=new NotificationCompat.Builder(ctx,CHANNEL)
            .setSmallIcon(R.drawable.tgc_logo)
            .setContentTitle("TGC Companion update available")
            .setContentText("Version "+name+" is ready. Tap to update.")
            .setContentIntent(open)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(Notification.DEFAULT_ALL)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .build();
        nm.notify(NOTIFICATION_ID,notice);
        pref.edit().putInt("last_notified_code",available)
            .putLong("last_notified_at",System.currentTimeMillis()).apply();
    }
}
