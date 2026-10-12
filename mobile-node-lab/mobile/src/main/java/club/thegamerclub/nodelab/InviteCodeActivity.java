package club.thegamerclub.nodelab;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
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

/** One job: read an already received TGC verification invite and copy its code. */
public final class InviteCodeActivity extends StaffProtectedActivity {
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private final Handler handler=new Handler(Looper.getMainLooper());
    private boolean active=false,reading=false,found=false;
    private String provider="gm3";
    private int scanGeneration=0;
    private TextView status,codeText,meta,intro,helpText;
    private Button gm3Tab,cultTab;
    private Button copy;
    private String code="";
    private long lastScan=0L,expiresAt=0L;
    private final Runnable refresh=new Runnable(){
        @Override public void run(){
            if(!active || isFinishing()||isDestroyed())return;
            if(found && expiresAt>0L && System.currentTimeMillis()>expiresAt){
                found=false;
                code="";
                copy.setEnabled(false);
                codeText.setText("— — — —");
                status.setText("That invite code has expired. Waiting for a new one...");
            }
            // Cult expiration depends on Gamer Music 2; do not invent a 10-minute rule.
            if(!found || ("cult".equals(provider) &&
                System.currentTimeMillis()-lastScan>=180_000L))scan();
            handler.postDelayed(this,45_000L);
        }
    };
    private int dp(float n){return CompanionStyle.dp(this,n);}
    private TextView text(String s,int size,int color,boolean bold){
        return CompanionStyle.text(this,s,size,color,bold);
    }
    private void gap(LinearLayout l,int n){
        l.addView(new View(this),new LinearLayout.LayoutParams(1,dp(n)));
    }
    private Button button(LinearLayout parent,String name,boolean main,Runnable click){
        Button b=CompanionStyle.button(this,name,main);
        parent.addView(b,CompanionStyle.buttonParams(this));
        b.setOnClickListener(v->click.run());return b;
    }
    @Override protected void onCreate(Bundle saved){
        super.onCreate(saved);
        if(!StaffGate.unlocked(this)){finish();return;}
        String initial=getIntent().getStringExtra("provider");
        if("cult".equals(initial))provider="cult";
        render();
    }
    @Override protected void onResume(){
        super.onResume();
        if(!StaffGate.unlocked(this)){finish();return;}
        active=true;
        scan();
        handler.removeCallbacks(refresh);
        handler.postDelayed(refresh,45_000L);
    }
    @Override protected void onPause(){
        active=false;
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
        root.setPadding(dp(18),dp(18),dp(18),dp(24));
        scroll.addView(root);
        setContentView(scroll);
        CompanionStyle.safeArea(this,scroll);

        Button back=CompanionStyle.button(this,"‹  Back",false);
        root.addView(back,new LinearLayout.LayoutParams(dp(125),dp(48)));
        back.setOnClickListener(v->finish());
        gap(root,18);
        root.addView(CompanionStyle.section(this,"ACCOUNT VERIFICATION"));
        root.addView(text("Your verification code",25,CompanionStyle.TEXT,true));
        gap(root,10);
        intro=text("",14,CompanionStyle.MUTED,false);
        root.addView(intro);
        gap(root,14);
        LinearLayout providers=new LinearLayout(this);
        root.addView(providers,new LinearLayout.LayoutParams(-1,-2));
        gm3Tab=CompanionStyle.button(this,"Gamer Music 3",true);
        cultTab=CompanionStyle.button(this,"Gamer Music 2 · Cult",false);
        LinearLayout.LayoutParams left=new LinearLayout.LayoutParams(0,dp(49),1f);
        left.setMargins(0,0,dp(5),0);
        providers.addView(gm3Tab,left);
        LinearLayout.LayoutParams right=new LinearLayout.LayoutParams(0,dp(49),1f);
        right.setMargins(dp(5),0,0,0);
        providers.addView(cultTab,right);
        gm3Tab.setTextSize(12);cultTab.setTextSize(12);
        gm3Tab.setOnClickListener(v->selectProvider("gm3"));
        cultTab.setOnClickListener(v->selectProvider("cult"));
        gap(root,14);

        LinearLayout card=CompanionStyle.card(this);
        root.addView(card,CompanionStyle.cardParams(this));
        status=text("Checking your recent VRChat invites...",14,CompanionStyle.MUTED,false);
        card.addView(status);gap(card,15);

        codeText=text("— — — —",32,CompanionStyle.ACCENT,true);
        codeText.setGravity(Gravity.CENTER);
        codeText.setLetterSpacing(.045f);
        codeText.setPadding(0,dp(13),0,dp(13));
        codeText.setBackground(CompanionStyle.shape(this,CompanionStyle.RAISED,14,0));
        card.addView(codeText,new LinearLayout.LayoutParams(-1,-2));
        gap(card,12);
        meta=text("Verification codes expire after 10 minutes.",12,CompanionStyle.MUTED,false);
        card.addView(meta);
        gap(card,12);

        copy=button(card,"Copy code",true,this::copy);
        copy.setEnabled(false);
        button(card,"Check again",false,()->{
            lastScan=0L;
            found=false;
            scan();
        });

        LinearLayout help=CompanionStyle.card(this);
        root.addView(help,CompanionStyle.cardParams(this));
        help.addView(text("How it works",17,CompanionStyle.TEXT,true));
        gap(help,9);
        helpText=text("",14,CompanionStyle.MUTED,false);
        help.addView(helpText);
        gap(help,11);
        help.addView(text("The Companion only reads your VRChat invite. Verification is still completed in Discord.",
            12,CompanionStyle.MUTED,false));
        updateModeLabels();
    }
    private void updateModeLabels(){
        boolean cult="cult".equals(provider);
        gm3Tab.setEnabled(cult);
        cultTab.setEnabled(!cult);
        intro.setText(cult?
            "Start Cult verification in Gamer Music 2's Discord. When its private VRChat invite arrives, the CULT-XXXX-XXXX code can appear here.":
            "Start verification in Gamer Music 3's Discord. When the private VRChat invite arrives, your TGC code appears here.");
        helpText.setText(cult?
            "1. Start the normal Cult verification in Discord.\n"+
            "2. Receive the private invite from Gamer Music 2.\n"+
            "3. Copy the CULT-XXXX-XXXX code and enter it into the Cult verification prompt.":
            "1. Start your normal verification in Discord.\n"+
            "2. Receive the invite sent by Gamer Music 3.\n"+
            "3. Copy the TGC-XXXX-XXXX code and paste it into Discord.");
        meta.setText(cult?
            "Code availability and expiration are controlled by Gamer Music 2.":
            "Gamer Music 3 codes expire after 10 minutes.");
    }
    private void selectProvider(String selected){
        if(provider.equals(selected))return;
        provider=selected;
        scanGeneration++;
        found=false;
        code="";expiresAt=0L;lastScan=0L;
        codeText.setText("— — — —");
        codeText.setTextColor(CompanionStyle.ACCENT);
        copy.setEnabled(false);
        status.setText("Checking recent "+("cult".equals(provider)?"Cult":"TGC")+" VRChat invites…");
        updateModeLabels();
        scan();
    }
    private void scan(){
        if(!active || reading || !StaffGate.unlocked(this))return;
        long now=System.currentTimeMillis();
        if(now-lastScan<20_000L)return;
        lastScan=now;
        final String requestedProvider=provider;
        final int requestGeneration=scanGeneration;
        reading=true;
        if(!found)status.setText("Checking your recent VRChat invites...");
        io.execute(()->{
            VRChatInviteCode.Result result=null;
            String error=null;
            try{result="cult".equals(requestedProvider)?
                VRChatInviteCode.scanCult(this):VRChatInviteCode.scan(this);}
            catch(Exception e){
                error=e instanceof SecurityException?
                    "Your VRChat login may have expired. Sign in again under Settings → My VRChat account.":
                    "Couldn't check VRChat invites right now. Try again shortly.";
            }
            final VRChatInviteCode.Result outcome=result;
            final String failure=error;
            runOnUiThread(()->{
                reading=false;
                if(!active||isFinishing()||isDestroyed())return;
                if(requestGeneration!=scanGeneration || !provider.equals(requestedProvider)){
                    // An old provider's in-flight scan must never appear in the other tab.
                    scan();return;
                }
                if(!StaffGate.unlocked(this)){finish();return;}
                if(failure!=null){status.setText(failure);return;}
                if(outcome==null)return;
                if(!outcome.code.isEmpty()){
                    code=outcome.code;
                    found=true;
                    expiresAt="gm3".equals(provider)&&outcome.createdAt>0L?
                        outcome.createdAt+10L*60_000L:0L;
                    codeText.setText(code);
                    codeText.setTextColor(CompanionStyle.GOOD);
                    status.setText(("cult".equals(provider)?"Cult":"TGC")+" verification invite found!");
                    copy.setEnabled(true);
                    meta.setText("cult".equals(provider)?
                        "Enter this code into Gamer Music 2's Cult verification prompt. Its expiration is controlled by the Cult system.":
                        "This code is single-use and expires about 10 minutes after the invite was generated.");
                }else{
                    code="";
                    found=false;
                    codeText.setText("— — — —");
                    codeText.setTextColor(CompanionStyle.ACCENT);
                    copy.setEnabled(false);
                    status.setText(outcome.message);
                    meta.setText("cult".equals(provider)?
                        "The Companion checks Cult invites while this screen is open.":
                        "The Companion checks TGC invites while this screen is open.");
                }
            });
        });
    }
    private void copy(){
        if(code.isEmpty()||!StaffGate.unlocked(this))return;
        ClipboardManager mgr=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
        if(mgr!=null)mgr.setPrimaryClip(ClipData.newPlainText(
            "cult".equals(provider)?"Cult verification code":"TGC verification code",code));
        status.setText("Copied. Paste the code into your existing verification prompt in Discord.");
    }
    @Override protected void onDestroy(){
        active=false;
        handler.removeCallbacks(refresh);
        io.shutdownNow();
        super.onDestroy();
    }
}
