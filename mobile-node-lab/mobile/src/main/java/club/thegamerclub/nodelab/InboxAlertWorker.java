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
            // On a partially available inbox, don't baseline or notify from a
            // truncated set; the next successful check will handle it.
            if(snapshot.partial)return Result.retry();
            InboxAlertSettings.Changes changes=InboxAlertSettings.remember(ctx,snapshot.items);
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
        if(manager==null)return;
        if(Build.VERSION.SDK_INT>=26){
            NotificationChannel channel=new NotificationChannel(CHANNEL,
                "VRChat inbox alerts",NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription("Optional alerts for new VRChat invitations and requests");
            channel.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
            manager.createNotificationChannel(channel);
        }
        Intent launch=new Intent(ctx,VRChatInboxActivity.class)
            .putExtra("filter","unread")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent open=PendingIntent.getActivity(ctx,321,launch,
            PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        String summary=changes.newUnread+" new VRChat notification"+
            (changes.newUnread==1?"":"s");
        String detail=changes.invites+" invite"+(changes.invites==1?"":"s")+
            " · "+changes.friends+" friend update"+
            (changes.friends==1?"":"s");
        NotificationCompat.Builder builder=new NotificationCompat.Builder(ctx,CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle(summary)
            .setContentText(detail)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setGroup("tgc_vrchat_inbox");
        if(InboxAlertSettings.permissionGranted(ctx))
            manager.notify(NOTICE,builder.build());
    }
}
