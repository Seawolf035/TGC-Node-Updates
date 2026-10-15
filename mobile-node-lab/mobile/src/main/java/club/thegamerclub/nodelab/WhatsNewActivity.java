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
        body.addView(CompanionStyle.section(this,"LATEST RELEASE · v0.21.13"));
        highlight(body,"StaffDex progress and organized achievements",
            "See the percentage of staff you've added and open all achievement "+
            "categories from one compact section.");
        body.addView(CompanionStyle.section(this,"PREVIOUS STAFF RELEASE · v0.21.12"));
        highlight(body,"Refined StaffDex collectible titles",
            "Updated the individual achievements for Lily, Imposter and Gamewitch, "+
            "while preserving all existing unlock requirements and collections.");
        body.addView(CompanionStyle.section(this,"PREVIOUS STAFF RELEASE · v0.21.11"));
        highlight(body,"Compact, expandable StaffDex achievements",
            "Achievements now open in individual categories with unlock counts, "+
            "instead of filling the screen with every collectible at once.");
        highlight(body,"More special StaffDex collectibles",
            "Seawolf and Lily now each have an individual achievement in "+
            "addition to their shared collection, with updated AJ and Asriel titles.");
        body.addView(CompanionStyle.section(this,"PREVIOUS STAFF RELEASE · v0.21.10"));
        highlight(body,"Cleaner, easier-to-navigate Settings",
            "Account, alerts, automation, tools and app settings now live in "+
            "five compact expandable groups, without removing existing controls.");
        highlight(body,"Improved Friends search and browsing",
            "Search the complete friends list by name, status or location; "+
            "pin friends directly and browse your VRChat favorite friend groups.");
        highlight(body,"New StaffDex collectible achievements",
            "Collect special Gamer Music, Cult, leadership and rare-friend "+
            "achievements; numbered Gamer Music profiles have custom accents.");
        body.addView(CompanionStyle.section(this,"PREVIOUS STAFF RELEASE · v0.21.9"));
        highlight(body,"Create VRChat instances from the Companion",
            "Create Public, Friends, Invite or Group instances with region "+
            "and group access settings. After creation, VRChat sends an "+
            "invitation to your own account automatically.");
        highlight(body,"Retry invitations without creating again",
            "If VRChat creates your instance but the self-invite fails, "+
            "retry the invitation without opening a duplicate instance.");
        Button continueBtn=CompanionStyle.button(this,"Continue to Companion",true);
        body.addView(continueBtn,CompanionStyle.buttonParams(this));
        continueBtn.setOnClickListener(v->{markSeen(this);finish();});
    }
    @Override public void onBackPressed(){markSeen(this);finish();}
}
