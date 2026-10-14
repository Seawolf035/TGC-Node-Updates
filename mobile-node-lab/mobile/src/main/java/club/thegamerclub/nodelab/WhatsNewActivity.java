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
        body.addView(CompanionStyle.section(this,"LATEST RELEASE · v0.21.7"));
        highlight(body,"Group and event inbox",
            "Groups, group announcements and group events are now distinct "+
            "inbox categories. Open group details directly from a notice, "+
            "including inside Floating and Quest Tools.");
        highlight(body,"More noticeable Android alerts",
            "Invitations, groups, events, friends and pinned-friend alerts "+
            "use new sound-and-vibration-capable notification channels. "+
            "Use Settings to check your phone's channel permissions "+
            "and send yourself a test alert.");
        highlight(body,"Optional LIVE VRChat notifications",
            "Opt in from Settings to maintain a visible foreground WebSocket "+
            "connection for much faster updates when VRChat sends an event. "+
            "Scheduled background checks remain as a fallback.");
        body.addView(CompanionStyle.section(this,"PREVIOUS STAFF RELEASE · v0.21.6"));
        highlight(body,"Linking now opens the setup guide",
            "After linking your phone or Quest, the Companion verifies staff "+
            "access, requests your agreement acceptance when needed, and "+
            "opens guided setup instead of leaving you in Tracking Controls. "+
            "Finishing setup returns to Companion Home.");
        highlight(body,"Shorter release history",
            "What's New now shows the five latest patch versions. Older "+
            "source and release history remain available through GitHub.");
        body.addView(CompanionStyle.section(this,"PREVIOUS STAFF RELEASE · v0.21.5"));
        highlight(body,"Friends sorted like VRCX",
            "VRChat favorite friends first, then shared visible instances, "+
            "solo known instances, and private/unknown locations last. "+
            "Updated across Friends & Activity, full Companion, and Floating/Quest Tools.");
        highlight(body,"Improved account checks",
            "Less frequent VRChat validation requests, more careful expired "+
            "session confirmation, and safer preservation of stored login data.");
        body.addView(CompanionStyle.section(this,"PREVIOUS STAFF RELEASE · v0.21.4"));
        highlight(body,"Faster, local-first Companion",
            "Previously viewed friends, favorite categories, profiles and inbox data "+
            "can load from an encrypted local cache, then refresh from VRChat. "+
            "Saved content stays account-specific; live actions always check VRChat.");
        highlight(body,"VRChat favorite friends first",
            "Your actual VRChat favorite friends appear ahead of other friends "+
            "in the Floating/Quest social view and full Companion lists. "+
            "Companion pinned shortcuts remain a separate feature.");
        highlight(body,"Invite-request assistant · optional",
            "In Settings you can opt in to automatically invite Companion-pinned "+
            "friends who send a new VRChat invite request while you are in a "+
            "valid world instance. Off by default; never changes TGC group invites.");
        highlight(body,"Improved notifications",
            "Separate invitation, invite-request and friend-update alerts, "+
            "including a dedicated Requests filter and saved inbox display.");
        body.addView(CompanionStyle.section(this,"PREVIOUS STAFF RELEASE · v0.21.3"));
        highlight(body,"Tracking has its own Tools tab",
            "Quest Tools and Floating Tools now have a fifth Tracking tab "+
            "for device capture, setup and player history. Your existing "+
            "tracking service and main Companion dashboard still work as before.");
        highlight(body,"Cleaner Floating Tools",
            "Removed redundant OSC introduction, status-color explanation and "+
            "Tracking help cards. Tracking controls and recent encounters "+
            "now live in the dedicated Tracking tab.");
        Button continueBtn=CompanionStyle.button(this,"Continue to Companion",true);
        body.addView(continueBtn,CompanionStyle.buttonParams(this));
        continueBtn.setOnClickListener(v->{markSeen(this);finish();});
    }
    @Override public void onBackPressed(){markSeen(this);finish();}
}
