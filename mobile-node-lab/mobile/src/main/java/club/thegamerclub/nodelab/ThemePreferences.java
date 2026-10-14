package club.thegamerclub.nodelab;

import android.content.Context;
import android.content.SharedPreferences;

/** Private per-device colors; an authenticated Admin+ grant is always required. */
public final class ThemePreferences {
    private static final String PREF="tgc_companion_admin_theme_v1";
    private static final int[] BASE={0xFF090F1D,0xFF142338,0xFF1C3048,0xFF233A52,
        0xFF62D6F2,0xFF324D68};
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
        SharedPreferences p=c.getSharedPreferences(PREF,0);
        int idx=p.getInt("preset",0);
        if(idx<0||idx>=PRESETS.length)idx=0;
        int[] result=PRESETS[idx].clone();
        for(int i=0;i<result.length;i++){
            String k="color_"+i;
            if(p.contains(k))result[i]=0xFF000000|(p.getInt(k,result[i])&0xFFFFFF);
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
        c.getSharedPreferences(PREF,0).edit().putInt("color_"+index,
            0xFF000000|(color&0xFFFFFF)).apply();
        apply(c);
    }
    public static void reset(Context c){
        if(!allowed(c))return;
        c.getSharedPreferences(PREF,0).edit().clear().apply();
        apply(c);
    }
    public static synchronized void resetRuntime(){
        CompanionStyle.BG=BASE[0];CompanionStyle.SURFACE=BASE[1];
        CompanionStyle.RAISED=BASE[2];CompanionStyle.FIELD=BASE[3];
        CompanionStyle.ACCENT=BASE[4];CompanionStyle.BORDER=BASE[5];
    }
    public static synchronized void apply(Context c){
        if(!allowed(c)){resetRuntime();return;}
        int[] a=colors(c);
        CompanionStyle.BG=a[0];CompanionStyle.SURFACE=a[1];
        CompanionStyle.RAISED=a[2];CompanionStyle.FIELD=a[3];
        CompanionStyle.ACCENT=a[4];CompanionStyle.BORDER=a[5];
    }
    public static int royalGold(){return 0xFFD6AD45;}
}
