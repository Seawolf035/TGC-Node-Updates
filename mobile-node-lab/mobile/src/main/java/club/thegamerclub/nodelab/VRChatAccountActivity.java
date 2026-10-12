package club.thegamerclub.nodelab;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.app.AlertDialog;
import org.json.JSONObject;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Native personal VRChat account; independent from staff deployment. */
public final class VRChatAccountActivity extends StaffProtectedActivity {
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private LinearLayout page,form;
    private TextView message;
    private EditText user,pass,code;

    private int dp(int n){return CompanionStyle.dp(this,n);}
    private TextView label(String text,int size){
        return CompanionStyle.text(this,text,size,CompanionStyle.TEXT,false);
    }
    private void gap(LinearLayout l,int height){
        l.addView(new View(this),new LinearLayout.LayoutParams(1,dp(height)));
    }
    private Button action(LinearLayout root,String title,Runnable callback,boolean primary){
        Button b=CompanionStyle.button(this,title,primary);
        root.addView(b,CompanionStyle.buttonParams(this));
        b.setOnClickListener(v->callback.run());return b;
    }
    private EditText field(String name,String hint,int inputType){
        form.addView(CompanionStyle.section(this,name));
        EditText e=CompanionStyle.input(this,hint);
        e.setInputType(inputType);
        e.setTextColor(CompanionStyle.TEXT);
        e.setHintTextColor(CompanionStyle.MUTED);
        e.setSingleLine(true);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(57));
        p.bottomMargin=dp(9);
        form.addView(e,p);
        return e;
    }
    @Override protected void onCreate(Bundle state){
        super.onCreate(state);
        if(!StaffGate.unlocked(this)){finish();return;}
        render();
    }
    @Override protected void onResume(){
        super.onResume();if(!StaffGate.unlocked(this))finish();
    }
    private void render(){
        getWindow().setStatusBarColor(CompanionStyle.BG);
        getWindow().setNavigationBarColor(CompanionStyle.BG);
        ScrollView scroll=new ScrollView(this);
        scroll.setBackgroundColor(CompanionStyle.BG);
        page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(19),dp(23),dp(19),dp(30));
        scroll.addView(page);setContentView(scroll);
        CompanionStyle.safeArea(this,scroll);
        Button back=CompanionStyle.button(this,"← Back",false);
        LinearLayout.LayoutParams backP=new LinearLayout.LayoutParams(dp(116),dp(44));
        page.addView(back,backP);back.setOnClickListener(v->finish());
        gap(page,19);
        page.addView(CompanionStyle.section(this,"PERSONAL VRCHAT ACCOUNT"));
        TextView title=label("Your account",29);
        title.setTypeface(android.graphics.Typeface.create("sans-serif-medium",0));
        page.addView(title);gap(page,7);
        TextView info=label("Sign in to your own VRChat account to access friends, worlds and groups.",14);
        info.setTextColor(CompanionStyle.MUTED);page.addView(info);gap(page,20);
        form=CompanionStyle.card(this);
        page.addView(form,CompanionStyle.cardParams(this));
        message=label("",13);
        message.setTextColor(CompanionStyle.ACCENT);
        form.addView(message);
        gap(form,9);
        try{
            JSONObject session=VRChatSession.load(this);
            if(!session.optString("userId").isEmpty()&&!session.optBoolean("needs2fa")){
                form.addView(CompanionStyle.chip(this,"● Signed in",CompanionStyle.GOOD,0xFF163B33));
                gap(form,15);
                form.addView(label(session.optString("displayName","VRChat user"),23));
                gap(form,8);
                TextView hint=label("Your account is connected. You can explore the Companion without signing in again.",13);
                hint.setTextColor(CompanionStyle.MUTED);form.addView(hint);gap(form,17);
                action(form,"View my profile",()->{
                    String id=session.optString("userId","");
                    if(!id.matches("usr_[0-9a-fA-F-]{36}")){say("Please check your session.");return;}
                    startActivity(new Intent(this,VRChatDetailActivity.class).putExtra("kind","user").putExtra("id",id));
                },true);
                action(form,"My private VRChat notes",()->startActivity(new Intent(this,VRChatNotesActivity.class)),false);
                action(form,"Status & presets",()->startActivity(new Intent(this,PresenceActivity.class)),false);
                action(form,"Extended avatar favorites (VRC+)",()->startActivity(new Intent(this,ExtendedAvatarActivity.class)),false);
                action(form,"Check connection",this::checkSession,false);
                action(form,"Sign out",this::confirmLogout,false);
                return;
            }
            if(session.optBoolean("needs2fa")){render2fa();return;}
        }catch(Exception e){VRChatSession.clear(this);}
        form.addView(label("Sign in with VRChat",19));gap(form,4);
        TextView explain=label("Use your VRChat username and password. Your password is never saved on this device.",13);
        explain.setTextColor(CompanionStyle.MUTED);form.addView(explain);gap(form,10);
        user=field("USERNAME","VRChat username",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_NORMAL);
        pass=field("PASSWORD","Enter your password",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        gap(form,8);
        action(form,"Sign in",this::login,true);
        TextView note=label("A linked VRChat account is required for Steam or Meta users.",12);
        note.setTextColor(CompanionStyle.MUTED);form.addView(note);
    }
    private void render2fa(){
        form.addView(CompanionStyle.chip(this,"● Extra verification",CompanionStyle.GOLD,0xFF433722));
        gap(form,13);form.addView(label("Two-factor verification",20));
        gap(form,6);
        TextView hint=label("Enter your code below, then select the method you use.",13);
        hint.setTextColor(CompanionStyle.MUTED);form.addView(hint);gap(form,10);
        code=field("VERIFICATION CODE","Your code",InputType.TYPE_CLASS_TEXT);
        action(form,"Authenticator code",()->verify("totp"),true);
        action(form,"Email code",()->verify("emailotp"),false);
        action(form,"Recovery code",()->verify("otp"),false);
        action(form,"Cancel sign-in",()->{VRChatSession.clear(this);render();},false);
    }
    private void checkSession(){
        if(!StaffGate.unlocked(this)){finish();return;}
        say("Checking your account...");
        io.execute(()->{
            try{
                VRChatApi.currentUser(this);
                runOnUiThread(this::render);
            }catch(Exception e){say("Can't connect: "+safe(e));}
        });
    }
    private void login(){
        if(!StaffGate.unlocked(this)){finish();return;}
        String username=user.getText().toString().trim();
        String password=pass.getText().toString();
        pass.setText("");
        say("Signing in...");
        io.execute(()->{
            try{
                VRChatApi.login(this,username,password);
                runOnUiThread(this::render);
            }catch(Exception e){say("Sign-in failed: "+safe(e));}
        });
    }
    private void verify(String mode){
        if(!StaffGate.unlocked(this)){finish();return;}
        String value=code.getText().toString().trim();
        code.setText("");say("Verifying...");
        io.execute(()->{
            try{VRChatApi.verify2fa(this,value,mode);runOnUiThread(this::render);}
            catch(Exception e){say("Verification failed: "+safe(e));}
        });
    }
    private void confirmLogout(){
        new AlertDialog.Builder(this).setTitle("Sign out of VRChat?")
            .setMessage("Your staff access and player tracking will stay connected.")
            .setNegativeButton("Cancel",null)
            .setPositiveButton("Sign out",(d,w)->{VRChatApi.logout(this);render();}).show();
    }
    private void say(String value){
        runOnUiThread(()->{if(message!=null)message.setText(value);});
    }
    private static String safe(Exception e){
        String m=e.getMessage();return m==null?"Connection error":m.substring(0,Math.min(140,m.length()));
    }
    @Override protected void onDestroy(){io.shutdownNow();super.onDestroy();}
}
