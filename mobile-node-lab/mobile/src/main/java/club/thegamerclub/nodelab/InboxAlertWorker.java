package club.thegamerclub.nodelab;

import android.Manifest;
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
import org.json.JSONArray;
import java.util.concurrent.TimeUnit;

/**
 * User-opted-in, battery-conscious VRChat inbox checks.
 * Never starts a foreground Node, reads Shizuku, or silently changes invites.
 */
public final class InboxAlertWorker extends Worker {
    private static final String CHANNEL="tgc_personal_vrc_inbox";
    private static final int NOTICE=321;
    public InboxAlertWorker(@NonNull Context context,@NonNull WorkerParameters params){
        super(context,params);
    }
    @NonNull @Override public Result doWork(){
        Context ctx=getApplicationContext();
        if(!InboxAlertSettings.active(ctx))return Result.success();
        try{
            // A WorkManager process can start cold. It MUST validate staff access
            // online rather than relying on stored deployment credentials.
            if(!StaffGate.unlocked(ctx))StaffGate.verifyOnline(ctx);
            if(!StaffGate.unlocked(ctx))return Result.retry();
            InboxRepository.Snapshot snapshot=InboxRepository.refresh(ctx);
            // Each available endpoint has its own first-use baseline, so a
            // persistent VRChat API endpoint error doesn't disable alerts.
            InboxAlertSettings.Changes changes=InboxAlertSettings.remember(ctx,snapshot);
            if(changes.newUnread>0 && InboxAlertSettings.active(ctx))notify(ctx,changes);
            return Result.success();
        }catch(SecurityException e){
            // A missing personal session is not a retryable staff credential.
            return Result.success();
        }catch(Exception e){
            return Result.retry();
        }
    }
    private static void notify(Context ctx,InboxAlertSettings.Changes changes){
        NotificationManager manager=(NotificationManager)
            ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if(manager==null||!InboxAlertSettings.active(ctx))return;
        int activeTypes=0;
        if(changes.invites>0){postCategory(ctx,manager,"Invitations",changes.invites,
            "invite","tgc_inbox_invites_v2",322);activeTypes++;}
        if(changes.inviteRequests>0){postCategory(ctx,manager,"Invite requests",
            changes.inviteRequests,"requests","tgc_inbox_requests_v2",323);activeTypes++;}
        if(changes.friends>0){postCategory(ctx,manager,"Friend updates",
            changes.friends,"friend","tgc_inbox_friends_v2",324);activeTypes++;}
        if(changes.other>0){postCategory(ctx,manager,"Other notifications",
            changes.other,"all","tgc_inbox_other_v2",325);activeTypes++;}
        if(activeTypes>1){
            Intent launch=new Intent(ctx,VRChatInboxActivity.class)
                .putExtra("filter","unread")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent open=PendingIntent.getActivity(ctx,321,launch,
                PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
            NotificationCompat.Builder summary=new NotificationCompat.Builder(ctx,
                "tgc_inbox_invites_v2")
                .setSmallIcon(R.drawable.tgc_logo)
                .setContentTitle(changes.newUnread+" new VRChat notifications")
                .setContentText("Invites, requests and friend updates")
                .setContentIntent(open)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setGroup("tgc_vrchat_inbox").setGroupSummary(true)
                .setAutoCancel(true).setOnlyAlertOnce(true);
            manager.notify(321,summary.build());
        }
    }
    private static void postCategory(Context ctx,NotificationManager manager,
        String heading,int count,String filter,String channel,int notificationId){
        if(Build.VERSION.SDK_INT>=26){
            NotificationChannel ch=new NotificationChannel(channel,
                "VRChat · "+heading,NotificationManager.IMPORTANCE_DEFAULT);
            ch.setDescription("Opt-in VRChat "+heading.toLowerCase(java.util.Locale.US)+
                " from The Gamer Club Companion");
            ch.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
            manager.createNotificationChannel(ch);
        }
        Intent launch=new Intent(ctx,VRChatInboxActivity.class)
            .putExtra("filter",filter)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent open=PendingIntent.getActivity(ctx,notificationId,launch,
            PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder notice=new NotificationCompat.Builder(ctx,channel)
            .setSmallIcon(R.drawable.tgc_logo)
            .setContentTitle(count+" new "+heading.toLowerCase(java.util.Locale.US))
            .setContentText("Open your personal VRChat inbox")
            .setContentIntent(open).setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setGroup("tgc_vrchat_inbox");
        if(InboxAlertSettings.active(ctx))manager.notify(notificationId,notice.build());
    }
}
