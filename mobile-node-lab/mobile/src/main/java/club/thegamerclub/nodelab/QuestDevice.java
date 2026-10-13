package club.thegamerclub.nodelab;

import android.content.Context;
import android.os.Build;
import java.util.Locale;

/**
 * Device-specific feature gate: Android's 2D application overlay may not work
 * inside Quest VR. The rest of the Companion remains available as normal.
 *
 * Quest detection uses the headset's own Build identifiers; no network or
 * installed-app inventory is required. Floating overlays are never supported
 * on Quest: use the native Quest System activity instead.
 */
public final class QuestDevice {
    // The old experimental Quest floater opt-in is intentionally retired.
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
    public static boolean canRunOverlay(Context ctx){
        // Hard gate also protects direct service starts and recovery attempts,
        // including devices with the old "experimental_floating_tools" pref.
        return !isQuestDevice();
    }
}
