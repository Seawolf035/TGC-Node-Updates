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
    private TextView status,codeText,meta;
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
            if(!found)scan();
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
        root.addView(text("Your invite code",27,CompanionStyle.TEXT,true));
        gap(root,10);
        root.addView(text("Open the verification request in Discord as usual. Once Gamer Music 3 sends your private world invite, your code will appear here.",
            14,CompanionStyle.MUTED,false));
        gap(root,21);

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
        help.addView(text("1. Start your normal staff verification in Discord.\n"+
            "2. Receive the invite sent by Gamer Music 3.\n"+
            "3. Copy the code from here and paste it into Discord.",
            14,CompanionStyle.MUTED,false));
        gap(help,11);
        help.addView(text("The Companion only reads the invitation. It does not start or complete verification for you.",
            12,CompanionStyle.MUTED,false));
    }
    private void scan(){
        if(!active || reading || !StaffGate.unlocked(this))return;
        long now=System.currentTimeMillis();
        if(now-lastScan<20_000L)return;
        lastScan=now;
        reading=true;
        if(!found)status.setText("Checking your recent VRChat invites...");
        io.execute(()->{
            VRChatInviteCode.Result result=null;
            String error=null;
            try{result=VRChatInviteCode.scan(this);}
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
                if(!StaffGate.unlocked(this)){finish();return;}
                if(failure!=null){status.setText(failure);return;}
                if(outcome==null)return;
                if(!outcome.code.isEmpty()){
                    code=outcome.code;
                    found=true;
                    expiresAt=outcome.createdAt>0L?outcome.createdAt+10L*60_000L:0L;
                    codeText.setText(code);
                    codeText.setTextColor(CompanionStyle.GOOD);
                    status.setText("Verification invite found!");
                    copy.setEnabled(true);
                    meta.setText("This code is single-use and expires about 10 minutes after the invite was generated.");
                }else{
                    code="";
                    found=false;
                    codeText.setText("— — — —");
                    codeText.setTextColor(CompanionStyle.ACCENT);
                    copy.setEnabled(false);
                    status.setText(outcome.message);
                    meta.setText("The Companion checks again while this screen is open.");
                }
            });
        });
    }
    private void copy(){
        if(code.isEmpty()||!StaffGate.unlocked(this))return;
        ClipboardManager mgr=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
        if(mgr!=null)mgr.setPrimaryClip(ClipData.newPlainText("TGC verification code",code));
        status.setText("Copied. Paste the code into your existing verification prompt in Discord.");
    }
    @Override protected void onDestroy(){
        active=false;
        handler.removeCallbacks(refresh);
        io.shutdownNow();
        super.onDestroy();
    }
}
