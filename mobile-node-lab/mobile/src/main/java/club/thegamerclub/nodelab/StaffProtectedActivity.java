package club.thegamerclub.nodelab;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Maintains previously authorized staff grants while native detail/list screens
 * are in the foreground. No UI reset during successful revalidation or timeouts.
 * Confirmed revocation still locks the user and returns to the enrollment gate.
 */
public abstract class StaffProtectedActivity extends Activity {
    private final Handler staffHandler=new Handler(Looper.getMainLooper());
    private final ExecutorService staffWorker=Executors.newSingleThreadExecutor();
    private boolean staffVisible=false,checking=false;
    private final Runnable liveCheck=new Runnable(){
        @Override public void run(){
            if(!staffVisible || isFinishing() || isDestroyed())return;
            if(!checking){
                checking=true;
                staffWorker.execute(()->{
                    try{StaffGate.verifyOnline(StaffProtectedActivity.this);}
                    catch(Exception ignored){
                        // StaffGate immediately locks real revocations; temporary
                        // outages keep only the existing time-limited grant.
                    }
                    runOnUiThread(()->{
                        checking=false;
                        if(!staffVisible||isFinishing()||isDestroyed())return;
                        if(!StaffGate.unlocked(StaffProtectedActivity.this)){
                            Toast.makeText(StaffProtectedActivity.this,
                                "Staff verification is required; retrying from Companion",
                                Toast.LENGTH_LONG).show();
                            finish();
                        }
                    });
                });
            }
            staffHandler.postDelayed(this,90_000L);
        }
    };
    @Override protected void onResume(){
        super.onResume();
        staffVisible=true;
        staffHandler.removeCallbacks(liveCheck);
        staffHandler.postDelayed(liveCheck,4_000L);
    }
    @Override protected void onPause(){
        staffVisible=false;
        staffHandler.removeCallbacks(liveCheck);
        super.onPause();
    }
    @Override protected void onDestroy(){
        staffVisible=false;
        staffHandler.removeCallbacks(liveCheck);
        staffWorker.shutdownNow();
        super.onDestroy();
    }
}
