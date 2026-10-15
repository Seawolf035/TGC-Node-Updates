package club.thegamerclub.nodelab;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import org.json.JSONObject;

/** Best-effort WorkManager check; not server push and never installs APKs. */
public final class CompanionUpdateWorker extends Worker {
    public CompanionUpdateWorker(@NonNull Context context,@NonNull WorkerParameters p){
        super(context,p);
    }
    @NonNull @Override public Result doWork(){
        Context ctx=getApplicationContext();
        if(!CompanionUpdateAlerts.enabled(ctx))return Result.success();
        try{
            // Same approved URL, schema, channel and SHA-256 validation as
            // the existing signed in-app updater; no VRChat/staff API dependency.
            JSONObject release=MobileUpdaterActivity.fetchManifest();
            CompanionUpdateAlerts.consider(ctx,release);
            return Result.success();
        }catch(SecurityException ex){
            // Invalid feed must never trigger an update alert.
            return Result.success();
        }catch(Exception ex){
            return Result.retry();
        }
    }
}
