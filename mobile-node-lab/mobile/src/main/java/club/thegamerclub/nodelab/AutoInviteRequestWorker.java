package club.thegamerclub.nodelab;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

/** Background opt-in invite-request assistant, not a Node or TGC group inviter. */
public final class AutoInviteRequestWorker extends Worker {
    public AutoInviteRequestWorker(@NonNull Context context,
                                   @NonNull WorkerParameters params){
        super(context,params);
    }
    @NonNull @Override public Result doWork(){
        Context ctx=getApplicationContext();
        if(!AutoInviteRequests.enabled(ctx))return Result.success();
        try{
            if(!StaffGate.unlocked(ctx))StaffGate.verifyOnline(ctx);
            if(!StaffGate.unlocked(ctx))return Result.retry();
            AutoInviteRequests.processLive(ctx);
            return Result.success();
        }catch(SecurityException e){
            // Invalid personal login or removed privileges never grants access.
            return Result.success();
        }catch(Exception e){
            // WorkManager backs off during network failures and rate limiting.
            return Result.retry();
        }
    }
}
