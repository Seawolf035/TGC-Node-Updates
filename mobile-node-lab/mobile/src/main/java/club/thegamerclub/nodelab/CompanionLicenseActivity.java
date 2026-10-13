package club.thegamerclub.nodelab;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Explicit Quest/phone license acceptance in a native activity.
 *
 * The agreement remains scrollable while a compact, always-visible action
 * bar is outside that ScrollView. In particular, Quest users no longer
 * depend on interacting with a CheckBox to enable an otherwise disabled
 * consent button. No consent is inferred from opening or scrolling this view.
 */
public final class CompanionLicenseActivity extends Activity {
    private boolean review=false;

    private int dp(float n){return CompanionStyle.dp(this,n);}
    private TextView text(String s,int sz,int color,boolean bold){
        return CompanionStyle.text(this,s,sz,color,bold);
    }

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().setStatusBarColor(CompanionStyle.BG);
        getWindow().setNavigationBarColor(CompanionStyle.BG);
        review=CompanionLicense.accepted(this);

        final String body;
        try{body=CompanionLicense.text(this);}
        catch(Exception e){showMissingAgreement();return;}

        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(CompanionStyle.BG);
        setContentView(root);
        CompanionStyle.safeArea(this,root);

        LinearLayout heading=new LinearLayout(this);
        heading.setOrientation(LinearLayout.VERTICAL);
        heading.setPadding(dp(16),dp(9),dp(16),dp(7));
        root.addView(heading,new LinearLayout.LayoutParams(-1,-2));
        heading.addView(text("TGC STAFF COMPANION",11,CompanionStyle.ACCENT,true));
        heading.addView(text("User agreement & license",19,CompanionStyle.TEXT,true));
        heading.addView(text("Agreement "+CompanionLicense.VERSION,
            11,CompanionStyle.MUTED,false));

        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.setVerticalScrollBarEnabled(true);
        scroll.setContentDescription("Scrollable TGC Staff Companion license agreement");
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1f));
        TextView license=text(body,14,CompanionStyle.TEXT,false);
        // On Quest, text selection can capture controller drags intended to
        // scroll. Keep the text legible and let its parent own scrolling.
        license.setTextIsSelectable(false);
        license.setPadding(dp(16),dp(9),dp(16),dp(14));
        scroll.addView(license,new ScrollView.LayoutParams(-1,-2));

        LinearLayout actions=new LinearLayout(this);
        actions.setOrientation(LinearLayout.VERTICAL);
        actions.setPadding(dp(12),dp(6),dp(12),dp(6));
        actions.setBackgroundColor(CompanionStyle.SURFACE);
        root.addView(actions,new LinearLayout.LayoutParams(-1,-2));

        if(review){
            Button done=CompanionStyle.button(this,"Close",true);
            actions.addView(done,new LinearLayout.LayoutParams(-1,dp(48)));
            done.setOnClickListener(v->finish());
            return;
        }

        TextView notice=text("Select Agree & Continue to accept the agreement above.",
            12,CompanionStyle.TEXT,false);
        notice.setPadding(dp(4),0,dp(4),dp(5));
        actions.addView(notice,new LinearLayout.LayoutParams(-1,-2));

        LinearLayout controls=new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        actions.addView(controls,new LinearLayout.LayoutParams(-1,-2));

        Button decline=CompanionStyle.button(this,"Decline & Exit",false);
        decline.setContentDescription("Decline this agreement and exit");
        LinearLayout.LayoutParams declineParams=new LinearLayout.LayoutParams(0,dp(48),1f);
        declineParams.rightMargin=dp(6);
        controls.addView(decline,declineParams);
        decline.setOnClickListener(v->exitWithoutAgreement());

        Button accept=CompanionStyle.button(this,"Agree & Continue",true);
        accept.setContentDescription("Agree to license "+CompanionLicense.VERSION+" and continue");
        LinearLayout.LayoutParams acceptParams=new LinearLayout.LayoutParams(0,dp(48),1.3f);
        controls.addView(accept,acceptParams);
        // The button itself is the affirmative consent control. No checkbox
        // and no scroll-to-end gate that could trap users on small Quest panels.
        accept.setEnabled(true);
        accept.setOnClickListener(v->{
            accept.setEnabled(false); // prevent a double click while saving
            boolean persisted=CompanionLicense.accept(this);
            if(persisted && CompanionLicense.accepted(this)){
                setResult(RESULT_OK);
                finish();
            }else{
                accept.setEnabled(true);
                Toast.makeText(this,
                    "Unable to save agreement acceptance. Please try again.",
                    Toast.LENGTH_LONG).show();
            }
        });
    }

    private void showMissingAgreement(){
        LinearLayout root=new LinearLayout(this);
        root.setBackgroundColor(CompanionStyle.BG);
        root.setPadding(dp(16),dp(24),dp(16),dp(16));
        root.setOrientation(LinearLayout.VERTICAL);
        setContentView(root);
        CompanionStyle.safeArea(this,root);
        root.addView(text(
            "Unable to load the user agreement. Acceptance is unavailable. " +
            "Try updating the official app without clearing its data.",
            16,CompanionStyle.TEXT,true));
        Button exit=CompanionStyle.button(this,"Exit",true);
        root.addView(exit,CompanionStyle.buttonParams(this));
        exit.setOnClickListener(v->exitWithoutAgreement());
    }

    private void exitWithoutAgreement(){
        if(review){finish();return;}
        finishAffinity();
    }

    @Override public void onBackPressed(){
        if(review)finish();
        else exitWithoutAgreement();
    }
}
