package club.thegamerclub.nodelab;

import android.app.Activity;
import android.content.Context;
import android.view.View;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Shared dark aqua visual language. Native Android views; no WebView or extra dependencies. */
public final class CompanionStyle {
    public static final int BG=0xFF090F1D;
    public static final int SURFACE=0xFF142338;
    public static final int RAISED=0xFF1C3048;
    public static final int FIELD=0xFF233A52;
    public static final int TEXT=0xFFF1F8FF;
    public static final int MUTED=0xFFB8CADE;
    public static final int ACCENT=0xFF62D6F2;
    public static final int GOLD=0xFFFFD28A;
    public static final int BORDER=0xFF324D68;
    public static final int GOOD=0xFF68E4B0;
    private CompanionStyle(){}
    /**
     * Target API 35 draws underneath system bars on recent Android versions.
     * Apply real system-bar and display-cutout insets to the top-level view,
     * preserving its authored padding. This fixes Samsung status/nav overlap
     * without relying on guessed 24dp or 48dp fixed sizes.
     */
    public static void safeArea(Activity activity,View root){
        WindowCompat.setDecorFitsSystemWindows(activity.getWindow(),false);
        final int left=root.getPaddingLeft(),top=root.getPaddingTop();
        final int right=root.getPaddingRight(),bottom=root.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(root,(v,insets)->{
            androidx.core.graphics.Insets safe=insets.getInsets(
                WindowInsetsCompat.Type.systemBars() |
                WindowInsetsCompat.Type.displayCutout());
            v.setPadding(left+safe.left,top+safe.top,right+safe.right,bottom+safe.bottom);
            return insets;
        });
        ViewCompat.requestApplyInsets(root);
    }
    public static int dp(Context c,float size){
        return Math.round(size*c.getResources().getDisplayMetrics().density);
    }
    public static GradientDrawable shape(Context context,int fill,int radius,int strokeColor){
        GradientDrawable d=new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(dp(context,radius));
        if(strokeColor!=0)d.setStroke(dp(context,1),strokeColor);
        return d;
    }
    public static GradientDrawable gradient(Context c,int top,int bottom,int radius){
        GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{top,bottom});
        d.setCornerRadius(dp(c,radius));return d;
    }
    public static TextView text(Context c,String label,int size,int color,boolean bold){
        TextView t=new TextView(c);
        t.setText(label);t.setTextColor(color);t.setTextSize(size);
        t.setLineSpacing(dp(c,2),1.05f);
        if(bold)t.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
        return t;
    }
    public static TextView section(Context c,String name){
        TextView label=text(c,name.toUpperCase(java.util.Locale.US),11,ACCENT,true);
        label.setLetterSpacing(.12f);
        label.setPadding(0,dp(c,10),0,dp(c,12));
        return label;
    }
    public static LinearLayout card(Context c){
        LinearLayout root=new LinearLayout(c);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(c,17),dp(c,17),dp(c,17),dp(c,17));
        root.setBackground(shape(c,SURFACE,18,BORDER));
        return root;
    }
    public static LinearLayout.LayoutParams cardParams(Context c){
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);
        p.bottomMargin=dp(c,13);return p;
    }
    public static EditText input(Context c,String hint){
        EditText e=new EditText(c);
        e.setSingleLine(true);
        e.setTextSize(16);
        e.setTextColor(TEXT);
        e.setHintTextColor(MUTED);
        e.setHighlightColor(0x9962D6F2);
        e.setHint(hint);
        e.setBackground(shape(c,FIELD,13,0xFF50728F));
        e.setPadding(dp(c,16),dp(c,13),dp(c,16),dp(c,13));
        e.setSelectAllOnFocus(false);
        if(Build.VERSION.SDK_INT>=29){
            e.setTextCursorDrawable(shape(c,ACCENT,1,0));
        }
        return e;
    }
    public static Button button(Context c,String title,boolean primary){
        Button b=new Button(c);
        b.setAllCaps(false);
        b.setText(title);
        b.setTextSize(14);
        b.setTextColor(primary?BG:TEXT);
        b.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
        b.setBackground(shape(c,primary?ACCENT:RAISED,13,primary?0:BORDER));
        b.setPadding(dp(c,14),dp(c,10),dp(c,14),dp(c,10));
        return b;
    }
    public static LinearLayout.LayoutParams buttonParams(Context c){
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(c,50));
        p.bottomMargin=dp(c,10);return p;
    }
    public static TextView chip(Context c,String title,int foreground,int background){
        TextView t=text(c,title,12,foreground,true);
        t.setBackground(shape(c,background,24,0));
        t.setPadding(dp(c,11),dp(c,7),dp(c,11),dp(c,7));
        return t;
    }
}
