package club.thegamerclub.nodelab;

import android.app.Activity;
import android.os.Bundle;
import android.content.Context;
import android.content.Intent;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * Staff-facing release highlights, not a raw technical commit log.
 * Show once per installed app version after license and staff authorization.
 * Historical highlights remain accessible from Settings.
 */
public final class WhatsNewActivity extends Activity {
    private static final String PREF="tgc_companion_whats_new_v1";
    private static final String SEEN="dismissed_version";
    public static boolean shouldShow(Context c){
        return CompanionLicense.accepted(c)&&StaffGate.unlocked(c)&&
            !BuildConfig.VERSION_NAME.equals(c.getSharedPreferences(PREF,0)
                .getString(SEEN,""));
    }
    public static void markSeen(Context c){
        c.getSharedPreferences(PREF,0).edit()
            .putString(SEEN,BuildConfig.VERSION_NAME).apply();
    }
    private int dp(int n){return CompanionStyle.dp(this,n);}
    private void highlight(LinearLayout holder,String header,String description){
        LinearLayout c=CompanionStyle.card(this);
        holder.addView(c,CompanionStyle.cardParams(this));
        c.addView(CompanionStyle.text(this,header,16,CompanionStyle.TEXT,true));
        c.addView(CompanionStyle.text(this,description,13,CompanionStyle.MUTED,false));
    }
    @Override protected void onCreate(Bundle state){
        super.onCreate(state);
        if(!CompanionLicense.accepted(this)||!StaffGate.unlocked(this)){finish();return;}
        ThemePreferences.apply(this);
        ScrollView scroll=new ScrollView(this);
        scroll.setBackgroundColor(CompanionStyle.BG);
        LinearLayout body=new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(16),dp(10),dp(16),dp(28));
        scroll.addView(body);setContentView(scroll);CompanionStyle.safeArea(this,scroll);
        body.addView(CompanionStyle.section(this,"THE GAMER CLUB"));
        body.addView(CompanionStyle.text(this,"What's New",29,CompanionStyle.TEXT,true));
        body.addView(CompanionStyle.text(this,"Companion v"+BuildConfig.VERSION_NAME+
            " · Staff-facing release highlights",13,CompanionStyle.MUTED,false));
        body.addView(CompanionStyle.section(this,"LATEST RELEASE"));
        highlight(body,"Collect-a-Staff! · StaffDex",
            "Browse your TGC staff directory, open VRChat profiles, send friend requests one at a time, "+
            "and unlock achievements based on verified friendships.");
        highlight(body,"Admin+ Theme Studio",
            "Administrators and above can choose a personal theme or mix their own colors. "+
            "Royalty keeps its distinctive gold trim and identity badges.");
        highlight(body,"Royalty identity on profiles",
            "Staff profiles display verified group ranks, with gold details for confirmed members "+
            "of TGC's Royalty group.");
        highlight(body,"Improved onboarding",
            "See major updates here once per new version. Reopen this history from Settings at any time.");
        body.addView(CompanionStyle.section(this,"RECENT STAFF RELEASES"));
        highlight(body,"v0.19.16 · Development tools",
            "Added an account-verified phone preview for the creator to refine Quest Tools screens.");
        highlight(body,"v0.19.15–0.19.17 · Staff agreement",
            "Expanded the proprietary staff license, added a Settings reader, "+
            "and credited Seawolf as creator.");
        highlight(body,"v0.19.14 · Quest usability",
            "Moved the Quest Back control to the top-left and made the three quick-action buttons "+
            "stay fixed while navigating.");
        Button continueBtn=CompanionStyle.button(this,"Continue to Companion",true);
        body.addView(continueBtn,CompanionStyle.buttonParams(this));
        continueBtn.setOnClickListener(v->{markSeen(this);finish();});
    }
    @Override public void onBackPressed(){markSeen(this);finish();}
}
