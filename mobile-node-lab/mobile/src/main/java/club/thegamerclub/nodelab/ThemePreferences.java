package club.thegamerclub.nodelab;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import java.util.Locale;

/**
 * Per-device colors, available ONLY under an active Relay-verified Admin+ grant.
 * Importing a VRChat palette is a PERSONAL customization, NOT a public preset.
 */
public final class ThemePreferences {
    private static final String PREF="tgc_companion_admin_theme_v1";
    // Preserve legacy indices/prefs: background, surface, raised, field, accent, border.
    // New indices: icon, button, foreground font, secondary/subtext.
    public static final int BG=0,SURFACE=1,RAISED=2,FIELD=3,ACCENT=4,BORDER=5,
        ICON=6,BUTTON=7,TEXT=8,SUBTEXT=9;
    public static final String[] COLOR_NAMES={
        "Background","Card surface","Raised panels","Input fields","Highlight / accent",
        "Borders","Icons","Buttons","Fonts / primary text","Subtext / secondary text"
    };
    private static final int[] BASE={
        0xFF090F1D,0xFF142338,0xFF1C3048,0xFF233A52,0xFF62D6F2,0xFF324D68,
        0xFF62D6F2,0xFF62D6F2,0xFFF1F8FF,0xFFB8CADE
    };
    // Existing built-in presets intentionally unchanged; no personal examples here.
    private static final int[][] PRESETS={
        {0xFF090F1D,0xFF142338,0xFF1C3048,0xFF233A52,0xFF62D6F2,0xFF324D68},
        {0xFF080D1F,0xFF141936,0xFF20294B,0xFF253A5C,0xFF8B9CFF,0xFF354A74},
        {0xFF170E20,0xFF291A36,0xFF342345,0xFF483054,0xFFFF8CC3,0xFF684868},
        {0xFF080F0D,0xFF152B25,0xFF1E3D32,0xFF294A3F,0xFF68E4B0,0xFF35594D},
        {0xFF151110,0xFF2A211B,0xFF3B2B20,0xFF503B29,0xFFE7B75D,0xFF705437}
    };
    public static final String[] NAMES={"TGC Default","Deep Ocean","Rose Quartz","Emerald","Royal Gold"};
    private ThemePreferences(){}

    public static boolean allowed(Context c){return StaffGate.rankTier(c)>=4;}
    public static synchronized int[] colors(Context c){
        if(!allowed(c))return BASE.clone();
        SharedPreferences prefs=c.getSharedPreferences(PREF,0);
        int idx=prefs.getInt("preset",0);
        if(idx<0||idx>=PRESETS.length)idx=0;
        int[] result=BASE.clone();
        System.arraycopy(PRESETS[idx],0,result,0,6);
        result[ICON]=result[ACCENT];
        result[BUTTON]=result[ACCENT];
        for(int i=0;i<result.length;i++){
            String key="color_"+i;
            if(prefs.contains(key))
                result[i]=0xFF000000|(prefs.getInt(key,result[i])&0xFFFFFF);
        }
        return result;
    }
    public static void preset(Context c,int idx){
        if(!allowed(c)||idx<0||idx>=PRESETS.length)return;
        c.getSharedPreferences(PREF,0).edit().clear().putInt("preset",idx).apply();
        apply(c);
    }
    public static void color(Context c,int index,int color){
        if(!allowed(c)||index<0||index>=BASE.length)return;
        c.getSharedPreferences(PREF,0).edit()
            .putInt("color_"+index,0xFF000000|(color&0xFFFFFF)).apply();
        apply(c);
    }
    /** Accept exactly #RRGGBB or RRGGBB; reject alpha, shorthand, malformed input. */
    public static int parseHex(String value){
        String raw=value==null?"":value.trim();
        if(raw.startsWith("#"))raw=raw.substring(1);
        if(!raw.matches("(?i)[0-9a-f]{6}"))
            throw new IllegalArgumentException("Use a six-digit hex color, for example #295094.");
        return 0xFF000000|(int)Long.parseLong(raw,16);
    }
    public static String hex(int color){
        return String.format(Locale.US,"#%06X",color&0xFFFFFF);
    }
    /** Weighted channel-by-channel blend for matched but legible palette surfaces. */
    private static int mix(int base,int tint,float amount){
        float keep=1f-amount;
        return Color.rgb(
            Math.round(Color.red(base)*keep+Color.red(tint)*amount),
            Math.round(Color.green(base)*keep+Color.green(tint)*amount),
            Math.round(Color.blue(base)*keep+Color.blue(tint)*amount));
    }
    /**
     * VRChat 6-color order: Highlight, Icons, Buttons, Background, Fonts, Subtext.
     * Paste comma-separated codes or one code per line. Import is atomic.
     *
     * App keeps six older configurable surface slots, plus independent UI
     * icon, button and text colors. Derive surface/border shades from the
     * background and colors rather than inventing global preset entries.
     */
    public static void importVrchatPalette(Context c,String pasted){
        if(!allowed(c))throw new SecurityException("Admin+ staff access required");
        String raw=pasted==null?"":pasted.trim();
        String[] parts=raw.split("[,;\\r\\n]+",-1);
        if(parts.length!=6)
            throw new IllegalArgumentException(
                "Paste exactly six colors: Highlight, Icons, Buttons, Background, Fonts, Subtext.");
        int[] vrchat=new int[6];
        for(int i=0;i<6;i++){
            try{vrchat[i]=parseHex(parts[i]);}
            catch(IllegalArgumentException ex){
                throw new IllegalArgumentException(
                    "Color "+(i+1)+" ("+new String[]{
                        "Highlight","Icons","Buttons","Background","Fonts","Subtext"
                    }[i]+"): "+ex.getMessage());
            }
        }
        int[] theme=new int[BASE.length];
        theme[BG]=vrchat[3];
        theme[SURFACE]=mix(vrchat[3],vrchat[1],.14f);
        theme[RAISED]=mix(vrchat[3],vrchat[2],.27f);
        theme[FIELD]=mix(vrchat[3],vrchat[1],.19f);
        theme[ACCENT]=vrchat[0];
        theme[BORDER]=mix(vrchat[3],vrchat[0],.38f);
        theme[ICON]=vrchat[1];
        theme[BUTTON]=vrchat[2];
        theme[TEXT]=vrchat[4];
        theme[SUBTEXT]=vrchat[5];

        SharedPreferences.Editor tx=c.getSharedPreferences(PREF,0).edit().clear()
            .putInt("preset",0);
        for(int i=0;i<theme.length;i++)tx.putInt("color_"+i,theme[i]);
        // Synchronous commit so a process restart cannot retain a half-import.
        if(!tx.commit())throw new IllegalStateException("Could not save the imported palette");
        apply(c);
    }
    public static String exportVrchatPalette(Context c){
        int[] x=colors(c);
        return hex(x[ACCENT])+","+hex(x[ICON])+","+hex(x[BUTTON])+","+
            hex(x[BG])+","+hex(x[TEXT])+","+hex(x[SUBTEXT]);
    }
    public static void reset(Context c){
        if(!allowed(c))return;
        c.getSharedPreferences(PREF,0).edit().clear().apply();
        apply(c);
    }
    public static synchronized void resetRuntime(){
        CompanionStyle.BG=BASE[BG];CompanionStyle.SURFACE=BASE[SURFACE];
        CompanionStyle.RAISED=BASE[RAISED];CompanionStyle.FIELD=BASE[FIELD];
        CompanionStyle.ACCENT=BASE[ACCENT];CompanionStyle.BORDER=BASE[BORDER];
        CompanionStyle.ICON=BASE[ICON];CompanionStyle.BUTTON_FILL=BASE[BUTTON];
        CompanionStyle.TEXT=BASE[TEXT];CompanionStyle.MUTED=BASE[SUBTEXT];
    }
    public static synchronized void apply(Context c){
        if(!allowed(c)){resetRuntime();return;}
        int[] a=colors(c);
        CompanionStyle.BG=a[BG];CompanionStyle.SURFACE=a[SURFACE];
        CompanionStyle.RAISED=a[RAISED];CompanionStyle.FIELD=a[FIELD];
        CompanionStyle.ACCENT=a[ACCENT];CompanionStyle.BORDER=a[BORDER];
        CompanionStyle.ICON=a[ICON];CompanionStyle.BUTTON_FILL=a[BUTTON];
        CompanionStyle.TEXT=a[TEXT];CompanionStyle.MUTED=a[SUBTEXT];
    }
    public static int royalGold(){return 0xFFD6AD45;}
}
