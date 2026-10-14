package club.thegamerclub.nodelab;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import java.util.Locale;

/**
 * Unified REST catch-up delivery for both periodic WorkManager checks and
 * the optional low-latency VRChat WebSocket service. It never changes invites.
 * Android notification categories remain user-adjustable in system settings.
 */
public final class InboxAlertWorker extends Worker {
    private static final String GROUP="tgc_vrchat_personal_inbox_v3";
    private static final String[] CHANNELS={
        "tgc_inbox_invites_v3","tgc_inbox_requests_v3",
        "tgc_inbox_friends_v3","tgc_inbox_groups_v3",
        "tgc_inbox_events_v3","tgc_inbox_other_v3"
    };
    private static final String[] NAMES={
        "VRChat · Invitations","VRChat · Invite requests",
        "VRChat · Friend updates","VRChat · Group notifications",
        "VRChat · Group events","VRChat · Other notifications"
    };
    private static final String SUMMARY="tgc_inbox_summary_v3";
    private static final String LIVE="tgc_vrchat_live_connection_v1";
    private static final int SUMMARY_ID=413;
    public InboxAlertWorker(@NonNull Context ctx,@NonNull WorkerParameters params){
        super(ctx,params);
    }
    public static void ensureChannels(Context ctx){
        if(Build.VERSION.SDK_INT<26)return;
        NotificationManager manager=(NotificationManager)
            ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if(manager==null)return;
        // Fresh v3 IDs: existing v2 IMPORTANCE_DEFAULT channels are immutable
        // and cannot be turned into heads-up alerts by recreating them.
        for(int i=0;i<CHANNELS.length;i++){
            NotificationChannel ch=new NotificationChannel(
                CHANNELS[i],NAMES[i],NotificationManager.IMPORTANCE_HIGH);
            ch.enableVibration(true);
            ch.enableLights(true);
            ch.setShowBadge(true);
            ch.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
            ch.setDescription("High-visibility personal VRChat "+
                NAMES[i].substring(9).toLowerCase(Locale.US)+
                ". Sound, vibration and pop-ups can be adjusted in Android Settings.");
            manager.createNotificationChannel(ch);
        }
        NotificationChannel summary=new NotificationChannel(SUMMARY,
            "VRChat · Inbox summary",NotificationManager.IMPORTANCE_LOW);
        summary.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
        manager.createNotificationChannel(summary);
        NotificationChannel service=new NotificationChannel(LIVE,
            "VRChat · Live connection",NotificationManager.IMPORTANCE_LOW);
        service.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
        manager.createNotificationChannel(service);
    }
    public static String liveChannel(){return LIVE;}
    @NonNull @Override public Result doWork(){
        Context ctx=getApplicationContext();
        if(!InboxAlertSettings.active(ctx))return Result.success();
        try{
            if(!StaffGate.unlocked(ctx))StaffGate.verifyOnline(ctx);
            if(!StaffGate.unlocked(ctx))return Result.retry();
            refreshAndNotify(ctx);
            return Result.success();
        }catch(SecurityException e){
            return Result.success();
        }catch(Exception e){return Result.retry();}
    }
    /** Called on a background thread only; shared first-use baselines dedupe. */
    public static synchronized void refreshAndNotify(Context ctx)throws Exception{
        if(!InboxAlertSettings.active(ctx)||!StaffGate.unlocked(ctx))return;
        InboxRepository.Snapshot snapshot=InboxRepository.refresh(ctx);
        InboxAlertSettings.Changes changes=InboxAlertSettings.remember(ctx,snapshot);
        if(changes.newUnread>0&&InboxAlertSettings.active(ctx))
            notifyChanges(ctx,changes);
    }
    private static PendingIntent open(Context ctx,String filter,int id){
        Intent intent=new Intent(ctx,VRChatInboxActivity.class)
            .putExtra("filter",filter)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(ctx,id,intent,
            PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }
    private static void post(Context ctx,NotificationManager manager,
        int category,int count,String filter){
        if(count<=0)return;
        String noun=NAMES[category].substring(9).toLowerCase(Locale.US);
        NotificationCompat.Builder builder=new NotificationCompat.Builder(ctx,CHANNELS[category])
            .setSmallIcon(R.drawable.tgc_logo)
            .setContentTitle(count+" new VRChat "+noun)
            .setContentText("Open your Companion inbox to review")
            .setContentIntent(open(ctx,filter,430+category))
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(Notification.DEFAULT_ALL)
            .setGroup(GROUP)
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
            .setOnlyAlertOnce(false);
        manager.notify(430+category,builder.build());
    }
    private static void notifyChanges(Context ctx,InboxAlertSettings.Changes change){
        NotificationManager manager=(NotificationManager)
            ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if(manager==null||!InboxAlertSettings.active(ctx))return;
        ensureChannels(ctx);
        int[] counts={change.invites,change.inviteRequests,change.friends,
            change.groups,change.events,change.other};
        String[] filters={"invite","requests","friend","groups","events","other"};
        int categories=0;
        for(int i=0;i<counts.length;i++){
            if(counts[i]>0){post(ctx,manager,i,counts[i],filters[i]);categories++;}
        }
        if(categories>1){
            NotificationCompat.Builder summary=new NotificationCompat.Builder(ctx,SUMMARY)
                .setSmallIcon(R.drawable.tgc_logo)
                .setContentTitle(change.newUnread+" new VRChat notifications")
                .setContentText("Invites, groups, friends and more")
                .setContentIntent(open(ctx,"unread",SUMMARY_ID))
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setGroup(GROUP).setGroupSummary(true)
                .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
                .setOnlyAlertOnce(true);
            manager.notify(SUMMARY_ID,summary.build());
        }
    }
    /** Explicitly user-initiated diagnostic, not a fabricated VRChat event. */
    public static void sendTest(Context ctx){
        if(!InboxAlertSettings.active(ctx))return;
        ensureChannels(ctx);
        NotificationManager manager=(NotificationManager)
            ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if(manager==null)return;
        NotificationCompat.Builder n=new NotificationCompat.Builder(ctx,CHANNELS[3])
            .setSmallIcon(R.drawable.tgc_logo)
            .setContentTitle("Companion notification test")
            .setContentText("If you heard or saw this, group alert delivery is enabled.")
            .setContentIntent(open(ctx,"groups",460))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(Notification.DEFAULT_ALL)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE);
        manager.notify(460,n.build());
    }
}
