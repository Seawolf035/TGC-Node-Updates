package club.thegamerclub.nodelab;

import android.content.Context;
import android.os.Build;
import java.util.Locale;

/**
 * Device-specific feature gate: Android's 2D application overlay may not work
 * inside Quest VR. The rest of the Companion remains available as normal.
 *
 * Quest detection uses the headset's own Build identifiers; no network or
 * installed-app inventory is required. Experimental opt-in is stored locally.
 */
public final class QuestDevice {
    private static final String PREF="tgc_quest_compatibility";
    private static final String OVERLAY_ALLOWED="experimental_floating_tools";
    private QuestDevice(){}

    private static String normalized(String value){
        return value==null?"":value.trim().toLowerCase(Locale.US);
    }
    private static boolean questAlias(String value){
        switch(normalized(value)){
            case "hollywood": // Quest 2
            case "seacliff":  // Quest Pro
            case "eureka":    // Quest 3
            case "panther":   // Quest 3S
                return true;
            default:return false;
        }
    }
    public static boolean isQuestDevice(){
        String model=normalized(Build.MODEL);
        String manufacturer=normalized(Build.MANUFACTURER);
        String brand=normalized(Build.BRAND);
        String product=normalized(Build.PRODUCT);
        String device=normalized(Build.DEVICE);
        if(model.contains("quest")||product.contains("quest")||
            device.contains("quest"))return true;
        if(manufacturer.contains("oculus")||brand.contains("oculus"))
            return true;
        // Meta-branded Android hardware may report a codename in DEVICE.
        return (manufacturer.equals("meta")||brand.equals("meta"))&&
            (questAlias(device)||questAlias(product)||
             model.contains("meta quest"));
    }
    public static boolean experimentalOverlayAllowed(Context ctx){
        return ctx.getSharedPreferences(PREF,Context.MODE_PRIVATE)
            .getBoolean(OVERLAY_ALLOWED,false);
    }
    public static boolean canRunOverlay(Context ctx){
        return !isQuestDevice()||experimentalOverlayAllowed(ctx);
    }
    public static void setExperimentalOverlayAllowed(Context ctx,boolean enabled){
        ctx.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit()
            .putBoolean(OVERLAY_ALLOWED,enabled).apply();
    }
}
