package club.thegamerclub.nodelab;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Owner-confirmed account-ID cosmetics, distinct from the roster's Royalty rank.
 * NEVER determines staff roles, TGC group privileges or OSC authorization.
 * The former staff member's Gamer Music Jr account is deliberately excluded.
 */
public final class GamerMusicProfiles {
    public static final String GM1="usr_7e47ea74-6034-48a1-8e8d-32e6bf8b0060";
    public static final String GM2="usr_5a0bd6d8-25c6-4a7b-a1a6-9ea85125398f";
    public static final String GM3="usr_677c17a0-a615-4dfd-907a-86706f06cc98";
    public static final String GM4="usr_bc1178b2-e5ad-40d5-a632-31acd5165416";
    public static final String[] SET={GM1,GM2,GM3,GM4};
    public static final int CYAN=0xFF5CEBFF;
    public static final int VIOLET=0xFFB18CFF;
    private GamerMusicProfiles(){}
    public static int number(String id){
        if(GM1.equals(id))return 1;
        if(GM2.equals(id))return 2;
        if(GM3.equals(id))return 3;
        if(GM4.equals(id))return 4;
        return 0;
    }
    public static boolean official(String id){return number(id)>0;}
    public static String name(String id){
        int n=number(id);
        return n>0?"GAMER MUSIC "+n:"";
    }
    /** Visible profile-only neon treatment for the exact four verified IDs. */
    public static void decorate(Context context,String id,LinearLayout nameContainer,
        TextView heading,FrameLayout portrait){
        int number=number(id);
        if(number==0)return;
        final GradientDrawable gradient=CompanionStyle.gradient(
            context,0xFF173C60,0xFF392365,14);
        gradient.setStroke(CompanionStyle.dp(context,2),CYAN);
        TextView chip=CompanionStyle.text(context,"♫ GAMER MUSIC "+number+
            " · OFFICIAL ACCOUNT",12,CYAN,true);
        chip.setGravity(Gravity.CENTER_VERTICAL);
        chip.setPadding(CompanionStyle.dp(context,10),CompanionStyle.dp(context,8),
            CompanionStyle.dp(context,10),CompanionStyle.dp(context,8));
        chip.setBackground(gradient);
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);
        params.topMargin=CompanionStyle.dp(context,7);
        nameContainer.addView(chip,params);
        heading.setTextColor(VIOLET);
        // Overlay stroke remains visible over asynchronous avatar art.
        View outline=new View(context);
        outline.setBackground(CompanionStyle.shape(context,0x00000000,16,CYAN));
        portrait.addView(outline,new FrameLayout.LayoutParams(-1,-1));
    }
}
