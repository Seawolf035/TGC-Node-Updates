package club.thegamerclub.nodelab;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Exact-account cosmetic presentation for notable community members.
 *
 * This has NO role/roster, delegated-access, achievement or entitlement effect.
 * The signature colors apply to the VIEWED profile only; they never select,
 * unlock, or expose an account-exclusive local UI preset.
 */
public final class NotableCommunityProfiles {
    /** Same verified account bound to the existing exclusive Blood Rose theme. */
    public static final String DEVINITY="usr_0f534ae6-c5c4-4025-9478-d5423da1f296";
    public static final int ROSE=0xFFFFB4C8;
    public static final int CRIMSON=0xFFDB3557;
    public static final int SURFACE=0xFF1B1116;
    public static final int RAISED=0xFF321A25;
    private NotableCommunityProfiles(){}

    public static boolean devinity(String viewedVrchatId){
        return DEVINITY.equals(viewedVrchatId);
    }

    private static TextView signature(Context context){
        TextView badge=CompanionStyle.text(context,
            "✦ DEVINITY · BLOOD ROSE",12,ROSE,true);
        badge.setGravity(Gravity.CENTER_VERTICAL);
        int h=CompanionStyle.dp(context,10),v=CompanionStyle.dp(context,7);
        badge.setPadding(h,v,h,v);
        GradientDrawable paint=CompanionStyle.gradient(context,SURFACE,RAISED,12);
        paint.setStroke(CompanionStyle.dp(context,1),CRIMSON);
        badge.setBackground(paint);
        return badge;
    }

    /** Full Companion profile, including StaffDex profile links. */
    public static void decorate(Context context,String id,LinearLayout names,
            TextView heading,FrameLayout portrait){
        if(!devinity(id))return;
        heading.setTextColor(ROSE);
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);
        params.topMargin=CompanionStyle.dp(context,7);
        names.addView(signature(context),params);
        // Place the border in FRONT of asynchronous profile artwork.
        View border=new View(context);
        border.setBackground(CompanionStyle.shape(context,0x00000000,16,CRIMSON));
        portrait.addView(border,new FrameLayout.LayoutParams(-1,-1));
    }

    /** Native Floating / Quest Mini-VRCX player profile. */
    public static void decorateMini(Context context,String id,
            LinearLayout details,TextView heading,ImageView portrait){
        if(!devinity(id))return;
        heading.setTextColor(ROSE);
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);
        params.topMargin=CompanionStyle.dp(context,6);
        details.addView(signature(context),params);
        portrait.setBackground(CompanionStyle.shape(context,SURFACE,11,CRIMSON));
        portrait.setPadding(CompanionStyle.dp(context,3),CompanionStyle.dp(context,3),
            CompanionStyle.dp(context,3),CompanionStyle.dp(context,3));
    }
}
