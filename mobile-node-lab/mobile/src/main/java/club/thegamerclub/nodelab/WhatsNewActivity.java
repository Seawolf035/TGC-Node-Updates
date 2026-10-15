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
        body.addView(CompanionStyle.section(this,"LATEST RELEASE · v0.21.8"));
        highlight(body,"Automatic Companion update alerts",
            "Get an Android notification when a newer Companion version is "+
            "available. Tap to open the built-in verified updater. Alerts "+
            "can be controlled separately from VRChat notifications.");
        body.addView(CompanionStyle.section(this,"PREVIOUS STAFF RELEASE · v0.21.7"));
        highlight(body,"Group notifications and event inbox",
            "New Groups and Events inbox filters, including group "+
            "announcements and group-detail shortcuts in Floating and Quest Tools.");
        highlight(body,"Faster, more noticeable VRChat alerts",
            "Optional live VRChat notifications for quicker updates, "+
            "plus improved Android sound, vibration and notification settings.");
        body.addView(CompanionStyle.section(this,"PREVIOUS STAFF RELEASE · v0.21.6"));
        highlight(body,"Fixed device linking and guided setup",
            "After linking a phone or Quest, the Companion now opens "+
            "the guided setup instead of leaving you in Tracking Controls.");
        body.addView(CompanionStyle.section(this,"PREVIOUS STAFF RELEASE · v0.21.5"));
        highlight(body,"Smarter friend sorting",
            "Favorite friends first, then shared instances and visible "+
            "locations, with private or hidden locations last.");
        highlight(body,"More reliable VRChat login sessions",
            "Reduced unnecessary session checks and fixed cases where "+
            "temporary API errors appeared as expired logins.");
        body.addView(CompanionStyle.section(this,"PREVIOUS STAFF RELEASE · v0.21.4"));
        highlight(body,"Faster VRChat browsing",
            "Previously loaded friends, avatars and inbox data can appear "+
            "from encrypted local cache while fresh results are retrieved.");
        highlight(body,"Optional invite-request assistant",
            "Choose to automatically send an invitation when a "+
            "Companion-pinned friend requests to join your current instance.");
        Button continueBtn=CompanionStyle.button(this,"Continue to Companion",true);
        body.addView(continueBtn,CompanionStyle.buttonParams(this));
        continueBtn.setOnClickListener(v->{markSeen(this);finish();});
    }
    @Override public void onBackPressed(){markSeen(this);finish();}
}
