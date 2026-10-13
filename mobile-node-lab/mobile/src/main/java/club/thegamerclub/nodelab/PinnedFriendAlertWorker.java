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
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;

/**
 * Optional background alerts (WorkManager: minimum 15 minute interval).
 * While the floating bubble is active, its existing friend polling supplies
 * more frequent notifications through this same account-scoped observer.
 */
public final class PinnedFriendAlertWorker extends Worker {
    private static final String CHANNEL="tgc_vrchat_pinned_friends";
    public PinnedFriendAlertWorker(@NonNull Context ctx,
        @NonNull WorkerParameters params){super(ctx,params);}
    @NonNull @Override public Result doWork(){
        Context ctx=getApplicationContext();
        if(!PinnedFriendAlerts.active(ctx))return Result.success();
        try{
            if(!StaffGate.unlocked(ctx))StaffGate.verifyOnline(ctx);
            if(!StaffGate.unlocked(ctx))return Result.retry();
            if(!PinnedFriendAlerts.active(ctx))return Result.success();
            if(FriendPinStore.pins(ctx).length()==0)return Result.success();
            // The foreground floating service already polls every 2.5–5 min.
            if(PinnedFriendAlerts.recentOverlayObservation(ctx))
                return Result.success();
            JSONObject session=VRChatSession.load(ctx);
            JSONArray full=VRChatApi.friendSnapshot(ctx);
            ArrayList<PinnedFriendAlerts.OnlineFriend> newlyOnline=
                PinnedFriendAlerts.observe(ctx,session,full);
            notifyOnline(ctx,newlyOnline);
            return Result.success();
        }catch(SecurityException error){
            return Result.success();
        }catch(Exception error){
            return Result.retry();
        }
    }
    public static void notifyOnline(Context ctx,
        ArrayList<PinnedFriendAlerts.OnlineFriend> entries){
        if(entries==null||entries.isEmpty()||!PinnedFriendAlerts.active(ctx))return;
        NotificationManager mgr=(NotificationManager)
            ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if(mgr==null)return;
        if(Build.VERSION.SDK_INT>=26){
            NotificationChannel ch=new NotificationChannel(CHANNEL,
                "Pinned VRChat friends online",NotificationManager.IMPORTANCE_DEFAULT);
            ch.setDescription("Notifications when your selected pinned friends come online");
            ch.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
            mgr.createNotificationChannel(ch);
        }
        for(PinnedFriendAlerts.OnlineFriend friend:entries){
            if(!PinnedFriendAlerts.active(ctx))return;
            Intent launch=new Intent(ctx,FriendHubActivity.class)
                .putExtra("filter","friends")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent open=PendingIntent.getActivity(ctx,
                friend.id.hashCode()&0x7fffffff,launch,
                PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
            NotificationCompat.Builder notice=new NotificationCompat.Builder(ctx,CHANNEL)
                .setSmallIcon(R.drawable.tgc_logo)
                .setContentTitle(friend.name+" is online")
                .setContentText("Pinned VRChat friend is now online")
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(open).setAutoCancel(true)
                .setGroup("tgc_vrchat_pinned_online");
            try{
                mgr.notify((friend.id.hashCode()&0x7fffffff)+1000,notice.build());
            }catch(SecurityException ignored){}
        }
    }
}
