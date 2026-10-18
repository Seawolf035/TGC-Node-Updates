package club.thegamerclub.nodelab;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Button;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Owner-only Quest 2D quick-tools panel. Horizon OS, not Android's
 * TYPE_APPLICATION_OVERLAY service, owns spatial positioning and pinning.
 * No automatic pin APIs, no background-start bypass and no Quest floater
 * access for ordinary staff or delegated Companions.
 */
public final class QuestFloatingToolsActivity extends Activity {
    private final ExecutorService verifier=Executors.newSingleThreadExecutor();
    private volatile boolean closed=false;
    private volatile boolean allowed=false;
    private TextView gateStatus;
    private LinearLayout actions;
    private int dp(int value){return CompanionStyle.dp(this,value);}

    private TextView label(String value,int size,int color,boolean bold){
        return CompanionStyle.text(this,value,size,color,bold);
    }
    private void button(String label,Runnable run){
        Button b=CompanionStyle.button(this,label,false);
        actions.addView(b,CompanionStyle.buttonParams(this));
        b.setOnClickListener(v->{
            if(!allowed || !OwnerQuestFloatingPreview.eligible(this)){
                allowed=false;hideActions("Authorization changed. Reopen to verify.");return;
            }
            run.run();
        });
    }
    private void open(Class<?> screen){
        startActivity(new Intent(this,screen));
    }
    private void hideActions(String status){
        if(gateStatus!=null)gateStatus.setText(status);
        if(actions!=null)actions.setVisibility(View.GONE);
    }
    @Override public void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        if(!OwnerQuestFloatingPreview.eligible(this)){finish();return;}
        ThemePreferences.apply(this);
        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(CompanionStyle.BG);
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14),dp(14),dp(14),dp(18));
        scroll.addView(root);
        setContentView(scroll);
        CompanionStyle.safeArea(this,scroll);
        root.addView(label("THE GAMER CLUB",12,CompanionStyle.ACCENT,true));
        root.addView(label("Floating Quick Tools",22,CompanionStyle.TEXT,true));
        root.addView(label("Owner-only Quest 2D panel preview",12,CompanionStyle.MUTED,false));
        gateStatus=label("Verifying your VRChat account…",13,CompanionStyle.GOLD,false);
        root.addView(gateStatus);
        actions=new LinearLayout(this);
        actions.setOrientation(LinearLayout.VERTICAL);
        root.addView(actions);
        button("Notifications / inbox",()->open(VRChatInboxActivity.class));
        button("Friends",()->open(FriendHubActivity.class));
        button("Your VRChat status",()->open(PresenceActivity.class));
        button("Current instance",()->open(VRChatInstanceActivity.class));
        button("Player history",()->open(PlayerActivityLogActivity.class));
        LinearLayout ageCard=CompanionStyle.card(this);
        actions.addView(ageCard,CompanionStyle.cardParams(this));
        AgeCheckTool.attach(this,ageCard,null,null);
        button("Full Quest Tools",()->open(QuestToolsActivity.class));
        button("Close floating panel",this::finish);
        actions.setVisibility(View.GONE);
        root.addView(label(
            "Use the Quest window controls to resize, move or pin this panel. "+
            "Horizon OS decides whether it stays visible inside an immersive VR app.",
            12,CompanionStyle.MUTED,false));
    }
    @Override protected void onResume(){
        super.onResume();
        if(gateStatus==null)return;
        allowed=false;
        hideActions("Verifying owner and VRChat identities…");
        if(!OwnerQuestFloatingPreview.eligible(this)){finish();return;}
        verifier.execute(()->{
            String error=null;
            try{OwnerQuestFloatingPreview.verifyLive(this);}
            catch(Exception ex){error=ex.getMessage();}
            final String failure=error;
            runOnUiThread(()->{
                if(closed||isFinishing()||isDestroyed())return;
                allowed=failure==null && OwnerQuestFloatingPreview.eligible(this);
                if(allowed){
                    gateStatus.setText("✓ Verified owner · panel ready");
                    actions.setVisibility(View.VISIBLE);
                }else hideActions("Preview locked: "+(failure==null?"Identity changed":failure));
            });
        });
    }
    @Override protected void onDestroy(){
        closed=true;allowed=false;verifier.shutdownNow();super.onDestroy();
    }
}
