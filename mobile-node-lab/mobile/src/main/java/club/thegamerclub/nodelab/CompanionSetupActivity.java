package club.thegamerclub.nodelab;

import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import org.json.JSONObject;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Guided, device-specific setup. Staff authorization and license acceptance
 * must already be valid. No passwords or OAuth secrets are stored by this UI.
 *
 * Quest: live personal VRChat login -> explicitly confirm FULL logging in
 * VRChat's own Debug menu -> persist read-only Documents/Logs SAF permission.
 * Phone: live VRChat login -> optional Shizuku tracking tutorial (skippable).
 *
 * FULL logging remains a USER-CONFIRMED setting, never "verified" by us.
 */
public final class CompanionSetupActivity extends StaffProtectedActivity {
    private static final int QUEST_LOGS_FOLDER_REQUEST=7157;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private LinearLayout page;
    private TextView connectionStatus,feedback;
    private boolean verifying=false,liveVerified=false,verifyAttempted=false;
    private String verifiedUserId="";
    private boolean finishing=false;
    private int dp(int n){return CompanionStyle.dp(this,n);}
    private TextView text(String label,int size,int color,boolean bold){
        return CompanionStyle.text(this,label,size,color,bold);
    }
    private void message(String content){
        if(feedback!=null)feedback.setText(content);
    }
    private void button(LinearLayout parent,String label,boolean prominent,Runnable action){
        Button b=CompanionStyle.button(this,label,prominent);
        parent.addView(b,CompanionStyle.buttonParams(this));
        b.setOnClickListener(v->action.run());
    }
    private LinearLayout card(String heading,String description){
        LinearLayout c=CompanionStyle.card(this);
        page.addView(c,CompanionStyle.cardParams(this));
        c.addView(text(heading,18,CompanionStyle.TEXT,true));
        if(description!=null){
            TextView detail=text(description,13,CompanionStyle.MUTED,false);
            detail.setPadding(0,dp(8),0,dp(5));
            c.addView(detail);
        }
        return c;
    }
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        if(!StaffGate.unlocked(this)||!CompanionLicense.accepted(this)){
            finish();return;
        }
        ThemePreferences.apply(this);
        render();
    }
    @Override public void onResume(){
        super.onResume();
        if(!StaffGate.unlocked(this)||!CompanionLicense.accepted(this)){
            finish();return;
        }
        ThemePreferences.apply(this);
        render();
        verifyAccount();
    }
    private void verifyAccount(){
        if(verifying||finishing)return;
        if(!CompanionSetupState.vrchatSessionReady(this)){
            liveVerified=false;verifyAttempted=true;
            if(connectionStatus!=null)
                connectionStatus.setText("Not signed in. Open VRChat Login below.");
            return;
        }
        verifying=true;
        if(connectionStatus!=null)connectionStatus.setText("Checking your VRChat session…");
        worker.execute(()->{
            boolean success=false;String result="";
            try{
                JSONObject account=VRChatApi.currentUser(this);
                String userId=account.optString("id","");
                success=userId.matches("usr_[0-9a-fA-F-]{36}") &&
                    CompanionSetupState.vrchatSessionReady(this);
                result=success?userId:"VRChat did not return an authenticated account.";
            }catch(Exception e){result=e.getMessage()==null?"Connection failed":e.getMessage();}
            final boolean valid=success;
            final String note=result;
            runOnUiThread(()->{
                verifying=false;verifyAttempted=true;
                if(finishing||isFinishing()||isDestroyed()||!StaffGate.unlocked(this))return;
                liveVerified=valid;
                verifiedUserId=valid?note:"";
                render();
                if(!valid && connectionStatus!=null)
                    connectionStatus.setText("VRChat verification unavailable: "+note);
            });
        });
    }
    private void render(){
        if(isFinishing()||isDestroyed())return;
        ThemePreferences.apply(this);
        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(CompanionStyle.BG);
        page=new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(17),dp(15),dp(17),dp(30));
        scroll.addView(page);
        setContentView(scroll);
        CompanionStyle.safeArea(this,scroll);
        boolean quest=QuestDevice.isQuestDevice();
        page.addView(CompanionStyle.section(this,"THE GAMER CLUB"));
        page.addView(text(quest?"Quest setup":"Phone setup",28,CompanionStyle.TEXT,true));
        page.addView(text(quest?"Connect VRChat and give the Companion read-only access "+
            "to the Quest log folder. These steps enable full-featured tracking.":
            "Connect your VRChat account. Mobile player tracking is optional "+
            "and can be enabled later with Shizuku.",
            14,CompanionStyle.MUTED,false));
        page.addView(CompanionStyle.section(this,"STEP 1 · VRCHAT LOGIN"));
        boolean signed=CompanionSetupState.vrchatSessionReady(this);
        LinearLayout account=card(
            liveVerified?"✓ VRChat account verified":signed?"VRChat account needs a check":
                "Sign in to your personal VRChat account",
            "Use your VRChat account email or ORIGINAL username (not display name). "+
            "Two-factor authentication is supported. Your session is encrypted on the device.");
        connectionStatus=text(liveVerified?"Signed in and checked with VRChat.":
            verifying?"Checking your VRChat session…":
            signed?"Session saved. Checking account access online…":
            "No connected VRChat account yet.",13,
            liveVerified?CompanionStyle.GOOD:CompanionStyle.MUTED,false);
        account.addView(connectionStatus);
        button(account,signed?"Manage VRChat account":"Open VRChat Login",true,
            ()->startActivity(new Intent(this,VRChatAccountActivity.class)));
        if(signed)button(account,"Check VRChat session",false,this::verifyAccount);

        if(quest){
            page.addView(CompanionStyle.section(this,"STEP 2 · VRCHAT FULL LOGS"));
            boolean confirmed=CompanionSetupState.fullLoggingAcknowledged(this);
            LinearLayout logging=card(confirmed?"✓ FULL logging instruction acknowledged":
                "Enable FULL logging in VRChat",
                "Open VRChat's menu → Settings (gear icon) → Debug (near the bottom "+
                "of the left settings sidebar). Scroll to the 'Logging' row "+
                "and use the left/right arrows until the displayed value is 'Full'. "+
                "Do this BEFORE joining an instance so new joins are recorded.");
            // Faithful visual aid based on the real Quest VRChat Debug screenshot:
            // in the left sidebar Debug is selected, and the Logging row has
            // ◀ Full ▶. Avoid drawing unrelated UI or pretending to read VRChat's setting.
            LinearLayout screenshotGuide=new LinearLayout(this);
            screenshotGuide.setOrientation(LinearLayout.VERTICAL);
            screenshotGuide.setPadding(dp(11),dp(12),dp(11),dp(12));
            screenshotGuide.setBackground(CompanionStyle.shape(this,
                0xFF111A35,12,ThemePreferences.royalGold()));
            LinearLayout.LayoutParams visualSpace=new LinearLayout.LayoutParams(-1,-2);
            visualSpace.topMargin=dp(12);
            logging.addView(screenshotGuide,visualSpace);
            screenshotGuide.addView(text("REFERENCE · VRCHAT SETTINGS → DEBUG",11,
                0xFFC2D9FF,true));
            LinearLayout debugLine=new LinearLayout(this);
            debugLine.setGravity(android.view.Gravity.CENTER_VERTICAL);
            debugLine.setPadding(dp(10),dp(12),dp(10),dp(12));
            debugLine.setBackground(CompanionStyle.shape(this,
                0xFF182B58,8,0xFF638DF5));
            LinearLayout.LayoutParams debugMargins=new LinearLayout.LayoutParams(-1,-2);
            debugMargins.topMargin=dp(8);
            screenshotGuide.addView(debugLine,debugMargins);
            TextView debug=text("⚙  Debug",14,0xFFFFFFFF,true);
            debugLine.addView(debug);
            screenshotGuide.addView(text("Scroll within Debug until you see:",12,
                0xFFC2D9FF,false));
            LinearLayout loggingRow=new LinearLayout(this);
            loggingRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
            loggingRow.setPadding(dp(10),dp(11),dp(10),dp(11));
            loggingRow.setBackground(CompanionStyle.shape(this,
                0xFF10162F,8,ThemePreferences.royalGold()));
            screenshotGuide.addView(loggingRow,debugMargins);
            loggingRow.addView(text("Logging",14,0xFFFFFFFF,true),
                new LinearLayout.LayoutParams(0,-2,1));
            loggingRow.addView(text("◀   Full   ▶",15,0xFFE5EFFF,true));
            screenshotGuide.addView(text(
                "The value should read Full, as shown in the reference screenshot. "+
                "Don't select Share Log File or Share All Log Files.",
                12,0xFFC2D9FF,false));
            logging.addView(text("The Companion cannot see or change this VRChat setting. "+
                "This confirmation means YOU set it to FULL; it is not an automatic verification.",
                12,CompanionStyle.GOLD,false));
            button(logging,confirmed?"FULL logging confirmed · Review again":
                "I've enabled FULL logging in VRChat",true,()->{
                if(!CompanionSetupState.fullLoggingAcknowledged(this))
                    CompanionSetupState.confirmFullLogging(this);
                render();
            });

            page.addView(CompanionStyle.section(this,"STEP 3 · QUEST LOG FOLDER"));
            boolean granted=QuestSafLogWatcher.hasGrant(this);
            LinearLayout folder=card(granted?"✓ Documents/Logs access granted":
                "Grant Documents/Logs read permission",
                "Choose exactly Documents/Logs in the Android folder picker. "+
                "The Companion asks for read-only access, never writes to or uploads "+
                "complete VRChat logs. Select Logs, not the Documents root.");
            folder.addView(text(granted?QuestSafLogWatcher.grantedPath(this):
                "Not yet granted. Select Documents/Logs before completing Quest setup.",
                13,granted?CompanionStyle.GOOD:CompanionStyle.MUTED,false));
            button(folder,granted?"Review log-folder permission":
                "Choose Documents/Logs folder",true,this::pickFolder);
            if(!QuestSafLogWatcher.isSelected(this)){
                folder.addView(text("Your tracker currently uses the advanced Shizuku "+
                    "mode. This guided Quest setup still asks you to grant "+
                    "Documents/Logs permission for the normal log-folder path.",
                    12,CompanionStyle.MUTED,false));
            }
        }else{
            page.addView(CompanionStyle.section(this,"STEP 2 · OPTIONAL MOBILE TRACKING"));
            LinearLayout optional=card("Shizuku · optional, useful for tracking",
                "Everything else in the Companion works without Shizuku. "+
                "To record VRChat Mobile player joins and leaves, Shizuku is "+
                "required because Android protects VRChat's log files.");
            optional.addView(text("Simple setup: (1) install Shizuku, "+
                "(2) start it using Wireless Debugging, (3) grant the "+
                "Companion permission, then (4) start tracking.",
                13,CompanionStyle.TEXT,false));
            button(optional,"Get Shizuku · official website",false,
                ()->openWeb("https://shizuku.rikka.app/download/"));
            button(optional,"Shizuku setup guide",false,
                ()->openWeb("https://shizuku.rikka.app/guide/setup/"));
            button(optional,"Set up phone tracking now",false,
                ()->startActivity(new Intent(this,MobileDeployActivity.class)));
            optional.addView(text("You can skip Shizuku now and enable tracking "+
                "whenever you want from the Tracking tab.",12,CompanionStyle.MUTED,false));
        }
        page.addView(CompanionStyle.section(this,"FINISH SETUP"));
        LinearLayout finishCard=card("Ready to continue?",
            quest?"VRChat login, FULL logging acknowledgement, and Quest log "+
                "folder access are required to finish this guided Quest setup.":
                "Only your personal VRChat login is required. Phone tracking is optional.");
        feedback=text("",12,CompanionStyle.GOLD,false);finishCard.addView(feedback);
        boolean ready=liveVerified && signed &&
            (!quest||(CompanionSetupState.fullLoggingAcknowledged(this)&&
                QuestSafLogWatcher.hasGrant(this)));
        Button finishButton=CompanionStyle.button(this,"Finish Setup & Open Companion",true);
        finishCard.addView(finishButton,CompanionStyle.buttonParams(this));
        finishButton.setEnabled(ready);
        finishButton.setOnClickListener(v->{
            if(!liveVerified||!CompanionSetupState.complete(this)){
                message("Some setup steps aren't finished. Review the steps above.");
                return;
            }
            finishing=true;setResult(RESULT_OK);finish();
        });
        button(finishCard,"I'll finish setup later",false,this::finish);
        if(!ready){
            String missing=!signed?"Sign in to your personal VRChat account first.":
                verifying?"Checking your VRChat session online. Use Check VRChat session if needed.":
                !liveVerified?"Your VRChat session is saved but not verified online. "+
                    "Tap Check VRChat session; you can keep the existing login.":
                quest&&!CompanionSetupState.fullLoggingAcknowledged(this)?
                    "Tap I've enabled FULL logging after choosing Full in VRChat's Debug settings.":
                quest&&!QuestSafLogWatcher.hasGrant(this)?
                    "Grant read-only Documents/Logs folder permission on your Quest.":
                    "Review the incomplete steps above.";
            message(missing+" Your saved device link and completed steps are preserved.");
        }
    }
    private void pickFolder(){
        if(!QuestDevice.isQuestDevice())return;
        Intent picker=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        picker.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION |
            Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try{startActivityForResult(picker,QUEST_LOGS_FOLDER_REQUEST);}
        catch(ActivityNotFoundException e){
            message("Quest could not open the Android folder picker. "+
                "You can retry from Tracking → Device & Capture Controls.");
        }
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request!=QUEST_LOGS_FOLDER_REQUEST)return;
        if(result!=RESULT_OK||data==null||data.getData()==null){
            message("Folder selection canceled. No permission was changed.");
            return;
        }
        try{
            QuestSafLogWatcher.selectFolder(this,data.getData());
            render();
            message("Documents/Logs read permission saved successfully.");
        }catch(Exception e){
            render();
            message("Folder wasn't accepted: "+e.getMessage()+
                ". Select the exact Documents/Logs folder.");
        }
    }
    private void openWeb(String url){
        try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url))
            .addCategory(Intent.CATEGORY_BROWSABLE));}
        catch(Exception e){message("Open this URL in a browser: "+url);}
    }
    @Override protected void onDestroy(){
        finishing=true;worker.shutdownNow();super.onDestroy();
    }
}
