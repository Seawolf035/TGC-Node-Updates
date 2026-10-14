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
        body.addView(CompanionStyle.section(this,"LATEST RELEASE · v0.21.3"));
        highlight(body,"Tracking has its own Tools tab",
            "Quest Tools and Floating Tools now have a fifth Tracking tab "+
            "for device capture, setup and player history. Your existing "+
            "tracking service and main Companion dashboard still work as before.");
        highlight(body,"Cleaner Floating Tools",
            "Removed redundant OSC introduction, status-color explanation and "+
            "Tracking help cards. Tracking controls and recent encounters "+
            "now live in the dedicated Tracking tab.");
        body.addView(CompanionStyle.section(this,"PREVIOUS STAFF RELEASE · v0.21.2"));
        highlight(body,"Quest Tools navigation, cleaned up",
            "The four main Quest Tools tabs now offer a clear Main Menu action "+
            "that opens Staff Companion Home. Nested pages have a readable "+
            "single Back action, including the shortcut editor.");
        highlight(body,"More room on Quest Tools Home",
            "Removed the preview-only notice and duplicated Companion exit "+
            "so your quick actions and VRChat controls are immediately visible.");
        body.addView(CompanionStyle.section(this,"PREVIOUS STAFF RELEASE · v0.21.1"));
        highlight(body,"VRChat login status corrected",
            "Your account now distinguishes saved sessions from a login verified "+
            "against VRChat. Expired cookies and temporary connection errors "+
            "are clearly labeled without silently erasing your saved account.");
        highlight(body,"Quest phone preview recovery",
            "After reauthenticating your VRChat account, returning to the "+
            "creator-only phone preview automatically retries identity verification.");
        body.addView(CompanionStyle.section(this,"PREVIOUS STAFF RELEASE · v0.21.0"));
        highlight(body,"Quest & Floating Tools — first overhaul",
            "Cleaner Quick Actions and Social layouts on both devices, "+
            "a single Back control on nested Quest pages, and no Back on Home.");
        highlight(body,"Friend favorites and mutuals",
            "Browse real VRChat favorite friend groups, and view mutual friends "+
            "or groups directly from a person's profile when VRChat permits it.");
        body.addView(CompanionStyle.section(this,"PREVIOUS STAFF RELEASE · v0.20.3"));
        highlight(body,"StaffDex gets its own main tab",
            "Open the Staff Directory, review your collections and send individual "+
            "VRChat friend requests directly from the fifth bottom tab.");
        highlight(body,"Quest setup with FULL logging",
            "First-time guided setup now walks Quest staff through VRChat account login, "+
            "Settings → Debug → Logging → Full, and read-only Documents/Logs permission.");
        highlight(body,"Easy mobile onboarding",
            "Sign in to VRChat to get started on a phone. Shizuku is recommended "+
            "for optional player tracking, with links to the official setup guide.");
        body.addView(CompanionStyle.section(this,"PREVIOUS STAFF RELEASE · v0.20.2"));
        highlight(body,"Explore, simplified",
            "A faster Explore screen with compact shortcuts, featured StaffDex, "+
            "and four clear sections: Friends, Discover, Activity and Inbox.");
        highlight(body,"One Tracking dashboard",
            "The Home Tracking shortcut now leads to the Tracking tab, "+
            "with setup, activity and Device & Capture Controls in the right places.");
        body.addView(CompanionStyle.section(this,"PREVIOUS STAFF RELEASE · v0.20.1"));
        highlight(body,"Admin+ VRChat palette import",
            "Paste six VRChat UI hex colors at once, type individual #RRGGBB colors, "+
            "or export your current Companion theme as VRChat codes. "+
            "Pasted codes apply locally without automatically saving a preset. "+
            "Name and save your own personal palettes under My Saved Presets, "+
            "then reload or delete them whenever you want.");
        body.addView(CompanionStyle.section(this,"PREVIOUS STAFF RELEASE · v0.20.0"));
        highlight(body,"Collect-a-Staff! · StaffDex",
            "Browse your TGC staff directory, open VRChat profiles, send friend requests one at a time, "+
            "and unlock achievements based on verified friendships.");
        highlight(body,"Admin+ Theme Studio",
            "Administrators and above can choose a personal theme or mix their own colors. "+
            "Royalty keeps its distinctive gold trim and identity badges.");
        highlight(body,"Royalty identity on profiles",
            "Staff profiles display staff rank and Royalty cosmetic status sourced from the official TGC roster.");
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
