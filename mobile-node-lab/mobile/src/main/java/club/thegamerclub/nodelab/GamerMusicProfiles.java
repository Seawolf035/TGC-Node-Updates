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
        if(n==2)return "GAMER MUSIC 2 · CULT";
        return n>0?"GAMER MUSIC "+n:"";
    }
    /** Separate accent palette for each GM identity; GM2 represents Cult. */
    public static int accent(String id){
        switch(number(id)){
            case 1:return 0xFF5CEBFF; // original Gamer Music: bright aqua
            case 2:return 0xFFD19BFF; // Cult: violet
            case 3:return 0xFF78EAC3; // third network: green cyan
            case 4:return 0xFFFF9BD4; // fourth network: magenta
            default:return CYAN;
        }
    }
    public static int secondary(String id){
        switch(number(id)){
            case 1:return 0xFFB18CFF;
            case 2:return 0xFFFFD28A;
            case 3:return 0xFF75B8FF;
            case 4:return 0xFF8DA4FF;
            default:return VIOLET;
        }
    }
    /** Visible profile-only neon treatment for the exact four verified IDs. */
    public static void decorate(Context context,String id,LinearLayout nameContainer,
        TextView heading,FrameLayout portrait){
        int number=number(id);
        if(number==0)return;
        final GradientDrawable gradient=CompanionStyle.gradient(
            context,number==2?0xFF39204B:0xFF173C60,
            number==2?0xFF251934:0xFF392365,14);
        gradient.setStroke(CompanionStyle.dp(context,2),accent(id));
        TextView chip=CompanionStyle.text(context,
            "♫ "+name(id)+" · OFFICIAL ACCOUNT",12,accent(id),true);
        chip.setGravity(Gravity.CENTER_VERTICAL);
        chip.setPadding(CompanionStyle.dp(context,10),CompanionStyle.dp(context,8),
            CompanionStyle.dp(context,10),CompanionStyle.dp(context,8));
        chip.setBackground(gradient);
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);
        params.topMargin=CompanionStyle.dp(context,7);
        nameContainer.addView(chip,params);
        heading.setTextColor(secondary(id));
        // Overlay stroke remains visible over asynchronous avatar art.
        View outline=new View(context);
        outline.setBackground(CompanionStyle.shape(context,0x00000000,16,accent(id)));
        portrait.addView(outline,new FrameLayout.LayoutParams(-1,-1));
    }
}
