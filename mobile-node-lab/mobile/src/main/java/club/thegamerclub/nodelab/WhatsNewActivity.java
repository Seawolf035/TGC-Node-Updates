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
        body.addView(CompanionStyle.section(this,"LATEST RELEASE · v0.21.27"));
        highlight(body,"Minor bug fix",
            "Minor bug fix.");
        body.addView(CompanionStyle.section(this,"PREVIOUS STAFF RELEASE · v0.21.26"));
        highlight(body,"Avatar height presets",
            "Set 0.80 m or 2.00 m eye height from Floating and Quest OSC tools. "+
            "Enter, name, save and reuse personal custom heights."); 
        body.addView(CompanionStyle.section(this,"PREVIOUS STAFF RELEASE · v0.21.25"));
        highlight(body,"Finish Setup button repaired",
            "Finish Setup always responds, tells you which step is missing, "+
            "and rechecks VRChat when you press it. Existing staff and "+
            "VRChat logins, Quest folder grants and setup progress are kept.");
        body.addView(CompanionStyle.section(this,"PREVIOUS STAFF RELEASE · v0.21.24"));
        highlight(body,"Setup and staff access recovery",
            "Fixes a stuck Checking staff access screen after returning from guided "+
            "setup. You can now resume unfinished steps without relinking or "+
            "clearing your saved VRChat login.");
        body.addView(CompanionStyle.section(this,"PREVIOUS STAFF RELEASE · v0.21.23"));
        highlight(body,"Age Check Tool",
            "Calculate possible birth months and exact birthday ranges from a claimed age. "+
            "Available in Floating and Quest Tools, completely offline.");
        Button continueBtn=CompanionStyle.button(this,"Continue to Companion",true);
        body.addView(continueBtn,CompanionStyle.buttonParams(this));
        continueBtn.setOnClickListener(v->{markSeen(this);finish();});
    }
    @Override public void onBackPressed(){markSeen(this);finish();}
}
