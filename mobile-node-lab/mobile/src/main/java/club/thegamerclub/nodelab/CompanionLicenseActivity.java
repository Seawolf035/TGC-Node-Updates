package club.thegamerclub.nodelab;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Explicit click-through acceptance; no network, account or Quest overlay needed. */
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
        String body;
        try{body=CompanionLicense.text(this);}
        catch(Exception e){showMissingAgreement();return;}

        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(CompanionStyle.BG);
        setContentView(root);
        CompanionStyle.safeArea(this,root);

        LinearLayout heading=new LinearLayout(this);
        heading.setOrientation(LinearLayout.VERTICAL);
        heading.setPadding(dp(18),dp(17),dp(18),dp(8));
        root.addView(heading);
        heading.addView(text("TGC STAFF COMPANION",12,CompanionStyle.ACCENT,true));
        heading.addView(text("User agreement & license",23,CompanionStyle.TEXT,true));
        heading.addView(text("Agreement "+CompanionLicense.VERSION,
            12,CompanionStyle.MUTED,false));

        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(false);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1f));
        TextView license=text(body,14,CompanionStyle.TEXT,false);
        license.setTextIsSelectable(true);
        license.setPadding(dp(19),dp(15),dp(19),dp(19));
        scroll.addView(license);

        LinearLayout actions=new LinearLayout(this);
        actions.setOrientation(LinearLayout.VERTICAL);
        actions.setPadding(dp(16),dp(9),dp(16),dp(14));
        root.addView(actions);
        if(review){
            Button done=CompanionStyle.button(this,"Close",true);
            actions.addView(done,CompanionStyle.buttonParams(this));
            done.setOnClickListener(v->finish());
            return;
        }
        CheckBox agreed=new CheckBox(this);
        agreed.setText("I have read and agree to the user agreement and license.");
        agreed.setTextColor(CompanionStyle.TEXT);
        agreed.setTextSize(14);
        agreed.setPadding(dp(4),0,0,dp(7));
        actions.addView(agreed);

        Button accept=CompanionStyle.button(this,"I Agree & Continue",true);
        accept.setEnabled(false);
        actions.addView(accept,CompanionStyle.buttonParams(this));
        agreed.setOnCheckedChangeListener((button,checked)->accept.setEnabled(checked));
        accept.setOnClickListener(v->{
            if(!agreed.isChecked())return;
            CompanionLicense.accept(this);
            finish();
        });
        Button decline=CompanionStyle.button(this,"Decline & Exit",false);
        actions.addView(decline,CompanionStyle.buttonParams(this));
        decline.setOnClickListener(v->exitWithoutAgreement());
    }
    private void showMissingAgreement(){
        LinearLayout root=new LinearLayout(this);
        root.setBackgroundColor(CompanionStyle.BG);
        root.setPadding(dp(16),dp(30),dp(16),dp(16));
        root.setOrientation(LinearLayout.VERTICAL);
        setContentView(root);
        root.addView(text(
            "Unable to load the user agreement. Reinstall the official app.",
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
