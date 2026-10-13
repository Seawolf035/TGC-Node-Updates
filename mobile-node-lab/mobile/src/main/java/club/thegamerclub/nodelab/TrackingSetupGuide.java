package club.thegamerclub.nodelab;

import android.content.Context;
import android.content.SharedPreferences;

/** Shared first-time tracking tutorial state, displayed on the Tracking tab.
 * Acknowledgement is local; it does not verify VRChat's logging setting.
 */
public final class TrackingSetupGuide {
    private static final String PREF="tgc_tracking_first_setup_v1";
    private static final String MIGRATED="installation_checked";
    private static final String ACK="logging_guide_acknowledged";

    private TrackingSetupGuide(){}

    public static synchronized void initialize(Context ctx){
        SharedPreferences p=ctx.getSharedPreferences(PREF,0);
        if(p.getBoolean(MIGRATED,false))return;
        boolean alreadyLinked=false;
        try{alreadyLinked=MobileCredentials.load(ctx)!=null;}
        catch(Exception ignored){}
        // Existing linked installs are grandfathered when the tutorial
        // feature first appears; fresh/unlinked installs see it once.
        p.edit().putBoolean(MIGRATED,true)
            .putBoolean(ACK,alreadyLinked).apply();
    }
    public static boolean acknowledged(Context ctx){
        initialize(ctx);
        return ctx.getSharedPreferences(PREF,0).getBoolean(ACK,false);
    }
    public static void acknowledge(Context ctx){
        initialize(ctx);
        ctx.getSharedPreferences(PREF,0).edit().putBoolean(ACK,true).apply();
    }
}
