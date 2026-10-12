package club.thegamerclub.nodelab;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Read whichever TGC/Cult code arrives; both verifications start and end in Discord. */
public final class InviteCodeActivity extends StaffProtectedActivity {
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private final Handler handler=new Handler(Looper.getMainLooper());
    private boolean visible=false,reading=false,found=false;
    private TextView status,codeText,meta,providerLabel;
    private Button copy,refreshButton;
    private String code="",provider="none";
    private long lastScan=0L,expiresAt=0L;
    private int scanGeneration=0;
    private final Runnable refresh=new Runnable(){
        @Override public void run(){
            if(!visible||isFinishing()||isDestroyed())return;
            if(found&&expiresAt>0L&&System.currentTimeMillis()>=expiresAt){
                code="";found=false;provider="none";
                copy.setEnabled(false);
                codeText.setText("— — — —");
                status.setText("That TGC code has expired. Waiting for a new invite…");
                providerLabel.setText("Waiting for a verification invite");
            }
            // Poll even when showing a code, so a newer verification invite can
            // automatically replace it without choosing a tab.
            scan();
            handler.postDelayed(this,45_000L);
        }
    };
    private int dp(float n){return CompanionStyle.dp(this,n);}
    private TextView text(String s,int size,int color,boolean bold){
        return CompanionStyle.text(this,s,size,color,bold);
    }
    private void space(LinearLayout box,int size){
        box.addView(new View(this),new LinearLayout.LayoutParams(1,dp(size)));
    }
    private Button action(LinearLayout parent,String name,boolean primary,Runnable fn){
        Button b=CompanionStyle.button(this,name,primary);
        parent.addView(b,CompanionStyle.buttonParams(this));
        b.setOnClickListener(v->fn.run());return b;
    }
    @Override protected void onCreate(Bundle bundle){
        super.onCreate(bundle);
        if(!StaffGate.unlocked(this)){finish();return;}
        render();
    }
    @Override protected void onResume(){
        super.onResume();
        if(!StaffGate.unlocked(this)){finish();return;}
        visible=true;
        scan();
        handler.removeCallbacks(refresh);
        handler.postDelayed(refresh,45_000L);
    }
    @Override protected void onPause(){
        visible=false;
        handler.removeCallbacks(refresh);
        super.onPause();
    }
    private void render(){
        getWindow().setStatusBarColor(CompanionStyle.BG);
        getWindow().setNavigationBarColor(CompanionStyle.BG);
        ScrollView scroll=new ScrollView(this);
        scroll.setBackgroundColor(CompanionStyle.BG);
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18),dp(16),dp(18),dp(24));
        scroll.addView(root);setContentView(scroll);
        CompanionStyle.safeArea(this,scroll);

        Button back=CompanionStyle.button(this,"‹ Back",false);
        root.addView(back,new LinearLayout.LayoutParams(dp(120),dp(46)));
        back.setOnClickListener(v->finish());
        space(root,14);
        root.addView(CompanionStyle.section(this,"ACCOUNT VERIFICATION"));
        root.addView(text("Verification code",26,CompanionStyle.TEXT,true));
        space(root,10);
        root.addView(text(
            "Start verification in Gamer Music 3 or Gamer Music 2's Discord. "
                +"This page automatically finds whichever TGC or Cult invite arrives.",
            14,CompanionStyle.MUTED,false));
        space(root,16);

        LinearLayout card=CompanionStyle.card(this);
        root.addView(card,CompanionStyle.cardParams(this));
        providerLabel=text("Waiting for a verification invite",13,CompanionStyle.ACCENT,true);
        card.addView(providerLabel);
        space(card,8);
        status=text("Checking your recent VRChat invites…",13,CompanionStyle.MUTED,false);
        card.addView(status);space(card,14);
        codeText=text("— — — —",29,CompanionStyle.ACCENT,true);
        codeText.setGravity(Gravity.CENTER);
        codeText.setLetterSpacing(.035f);
        codeText.setPadding(0,dp(14),0,dp(14));
        codeText.setBackground(CompanionStyle.shape(this,CompanionStyle.RAISED,14,0));
        card.addView(codeText,new LinearLayout.LayoutParams(-1,-2));
        space(card,11);
        meta=text("Supports TGC-XXXX-XXXX and CULT-XXXX-XXXX",12,
            CompanionStyle.MUTED,false);
        card.addView(meta);
        space(card,9);
        copy=action(card,"Copy code",true,this::copy);
        copy.setEnabled(false);
        refreshButton=action(card,"Check again",false,()->scan());

        LinearLayout guide=CompanionStyle.card(this);
        root.addView(guide,CompanionStyle.cardParams(this));
        guide.addView(text("How it works",17,CompanionStyle.TEXT,true));
        space(guide,8);
        guide.addView(text(
            "1. Start verification in the appropriate Discord server.\n"
            +"2. Receive the private VRChat invite.\n"
            +"3. Copy the code shown here, then paste it into the same Discord prompt.",
            14,CompanionStyle.MUTED,false));
        space(guide,10);
        guide.addView(text(
            "The Companion only reads and copies the code. It never starts or submits Discord verification.",
            12,CompanionStyle.MUTED,false));
    }
    private void scan(){
        if(!visible||reading||!StaffGate.unlocked(this))return;
        long now=System.currentTimeMillis();
        if(now-lastScan<15_000L)return;
        lastScan=now;reading=true;
        final int generation=++scanGeneration;
        if(!found)status.setText("Checking recent VRChat invites…");
        refreshButton.setEnabled(false);
        io.execute(()->{
            VRChatInviteCode.Result result=null;
            String error=null;
            try{result=VRChatInviteCode.scanLatest(this);}
            catch(Exception e){
                error=e instanceof SecurityException?
                    "Your VRChat login may have expired. Sign in again under Settings.":
                    "Couldn't check VRChat invites. Try again shortly.";
            }
            final VRChatInviteCode.Result outcome=result;
            final String issue=error;
            runOnUiThread(()->{
                reading=false;
                if(!visible||isFinishing()||isDestroyed()||generation!=scanGeneration)return;
                if(!StaffGate.unlocked(this)){finish();return;}
                refreshButton.setEnabled(true);
                if(issue!=null){
                    status.setText(issue);
                    return; // Keep any previously found code on transient network errors.
                }
                if(outcome==null)return;
                if(!outcome.code.isEmpty()){
                    provider=outcome.provider;
                    code=outcome.code;
                    found=true;
                    expiresAt="gm3".equals(provider)&&outcome.createdAt>0L?
                        outcome.createdAt+10L*60_000L:0L;
                    // A code known to have expired must not be shown as valid.
                    if(expiresAt>0L&&expiresAt<=System.currentTimeMillis()){
                        found=false;code="";provider="none";expiresAt=0L;
                        codeText.setText("— — — —");copy.setEnabled(false);
                        providerLabel.setText("Waiting for a verification invite");
                        status.setText("That TGC invite has expired. Request a fresh one.");
                        return;
                    }
                    providerLabel.setText("cult".equals(provider)?
                        "Gamer Music 2 · Cult":"Gamer Music 3 · TGC");
                    codeText.setText(code);
                    codeText.setTextColor(CompanionStyle.GOOD);
                    copy.setEnabled(true);
                    status.setText("Verification invite found!");
                    meta.setText("cult".equals(provider)?
                        "Copy and paste into your existing Cult Discord prompt. Cult controls expiration.":
                        "Copy and paste into your existing TGC Discord prompt. TGC codes expire in about 10 minutes.");
                }else if(!found){
                    provider="none";code="";
                    providerLabel.setText("Waiting for a verification invite");
                    codeText.setText("— — — —");
                    codeText.setTextColor(CompanionStyle.ACCENT);
                    copy.setEnabled(false);
                    status.setText(outcome.message);
                    meta.setText("Supports TGC-XXXX-XXXX and CULT-XXXX-XXXX");
                }
            });
        });
    }
    private void copy(){
        if(!found||code.isEmpty()||!StaffGate.unlocked(this))return;
        ClipboardManager clipboard=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
        if(clipboard!=null){
            clipboard.setPrimaryClip(ClipData.newPlainText(
                "cult".equals(provider)?"Cult verification code":"TGC verification code",code));
        }
        status.setText("Copied! Paste it into the Discord verification prompt you started.");
    }
    @Override protected void onDestroy(){
        visible=false;
        handler.removeCallbacks(refresh);
        io.shutdownNow();
        super.onDestroy();
    }
}
