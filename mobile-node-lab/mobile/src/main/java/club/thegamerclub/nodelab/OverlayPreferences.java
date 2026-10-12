package club.thegamerclub.nodelab;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Purely local floating-window preferences. The foreground Mobile Node has
 * no dependency on these settings and no VRChat credentials are stored here.
 */
public final class OverlayPreferences {
    private static final String PREF="tgc_overlay";
    public static final String[] SHORTCUTS={
        "inbox","friends","status","players","recent","search",
        "instance","activity","verify","joins","groups","worlds"
    };
    private static final String DEFAULT="inbox,friends,status,instance";
    private OverlayPreferences(){}
    private static SharedPreferences prefs(Context ctx){
        return ctx.getSharedPreferences(PREF,Context.MODE_PRIVATE);
    }
    public static String size(Context ctx){
        String value=prefs(ctx).getString("panel_size","standard");
        return "compact".equals(value)||"expanded".equals(value)?
            value:"standard";
    }
    public static void setSize(Context ctx,String size){
        if(!"compact".equals(size)&&!"standard".equals(size)&&!"expanded".equals(size))
            throw new IllegalArgumentException("Unsupported panel size");
        prefs(ctx).edit().putString("panel_size",size).apply();
    }
    public static boolean snap(Context ctx){
        return prefs(ctx).getBoolean("snap_edge",false);
    }
    public static void setSnap(Context ctx,boolean enabled){
        prefs(ctx).edit().putBoolean("snap_edge",enabled).apply();
    }
    public static boolean recover(Context ctx){
        return prefs(ctx).getBoolean("recover_after_stop",false);
    }
    public static void setRecover(Context ctx,boolean enabled){
        prefs(ctx).edit().putBoolean("recover_after_stop",enabled).apply();
    }
    public static boolean desired(Context ctx){
        return prefs(ctx).getBoolean("desired",false);
    }
    public static void setDesired(Context ctx,boolean desired){
        prefs(ctx).edit().putBoolean("desired",desired).apply();
    }
    public static List<String> shortcuts(Context ctx){
        String data=prefs(ctx).getString("pinned",DEFAULT);
        LinkedHashSet<String> valid=new LinkedHashSet<>();
        for(String key:data.split(",")){
            for(String allowed:SHORTCUTS){
                if(allowed.equals(key)){valid.add(key);break;}
            }
            if(valid.size()>=6)break;
        }
        if(valid.isEmpty())valid.add("inbox");
        return new ArrayList<>(valid);
    }
    public static void setShortcuts(Context ctx,List<String> entries){
        LinkedHashSet<String> keys=new LinkedHashSet<>();
        for(String key:entries){
            if(Arrays.asList(SHORTCUTS).contains(key))keys.add(key);
            if(keys.size()>6)throw new IllegalArgumentException("Maximum six shortcuts");
        }
        if(keys.isEmpty())throw new IllegalArgumentException("Choose at least one shortcut");
        prefs(ctx).edit().putString("pinned",android.text.TextUtils.join(",",keys)).apply();
    }
    public static String name(String key){
        switch(key){
            case "inbox":return "Notifications";
            case "friends":return "Friends";
            case "status":return "Your status";
            case "players":return "Player history";
            case "recent":return "Recent worlds";
            case "search":return "Find anything";
            case "instance":return "Your instance";
            case "activity":return "Activity feed";
            case "verify":return "Verification code";
            case "joins":return "Join / leave";
            case "groups":return "Your groups";
            case "worlds":return "Saved worlds";
            default:return key;
        }
    }
    public static String destination(String key){
        switch(key){
            case "inbox":return "mini_inbox";
            case "friends":return "mini_friends";
            case "status":return "mini_presence";
            case "players":return "mini_players";
            case "recent":return "mini_recent";
            case "search":return "mini_find";
            case "instance":return "mini_now";
            case "activity":return "mini_activity";
            case "verify":return "mini_verify";
            case "joins":return "mini_joins";
            case "groups":return "mini_groups";
            case "worlds":return "mini_worlds";
            default:throw new IllegalArgumentException("Invalid shortcut");
        }
    }
}
