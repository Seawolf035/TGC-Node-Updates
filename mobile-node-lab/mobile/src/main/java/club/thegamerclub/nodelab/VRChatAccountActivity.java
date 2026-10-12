package club.thegamerclub.nodelab;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
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

/** Personal VRChat account; login details never sent to TGC and password never saved. */
public final class VRChatAccountActivity extends StaffProtectedActivity {
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private LinearLayout page;
    private TextView message;
    private EditText user,pass,code;
    private String selected2fa="totp";

    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private TextView label(String value,int size){
        TextView t=new TextView(this);t.setText(value);t.setTextColor(0xFFE7F1FB);
        t.setTextSize(size);t.setPadding(0,dp(7),0,dp(8));return t;
    }
    private Button action(String title,Runnable callback){
        Button b=new Button(this);b.setText(title);b.setAllCaps(false);
        page.addView(b,new LinearLayout.LayoutParams(-1,dp(52)));
        b.setOnClickListener(v->callback.run());return b;
    }
    @Override protected void onCreate(Bundle state){
        super.onCreate(state);
        if(!StaffGate.unlocked(this)){finish();return;}
        getWindow().setStatusBarColor(0xFF0B1423);render();
    }
    @Override protected void onResume(){
        super.onResume();
        if(!StaffGate.unlocked(this)){finish();return;}
    }
    private void render(){
        ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(0xFF0B1423);
        page=new LinearLayout(this);page.setPadding(dp(18),dp(23),dp(18),dp(25));
        page.setOrientation(LinearLayout.VERTICAL);scroll.addView(page);setContentView(scroll);
        page.addView(label("PERSONAL VRCHAT ACCOUNT",24));
        page.addView(label("This login is separate from your TGC staff deployment. Your credentials go directly to VRChat's API. Passwords and 2FA codes aren't saved; session cookies are encrypted using Android Keystore.",13));
        message=label("Checking session...",14);page.addView(message);
        action("Check my VRChat session",this::checkSession);
        try{
            JSONObject session=VRChatSession.load(this);
            if(!session.optString("userId").isEmpty() && !session.optBoolean("needs2fa")){
                page.addView(label("Saved login: "+session.optString("displayName"),17));
                action("My in-app profile",()->{
                    String ownId=session.optString("userId","");
                    if(!ownId.matches("usr_[0-9a-fA-F-]{36}")){say("Verify account session again.");return;}
                    startActivity(new Intent(this,VRChatDetailActivity.class)
                        .putExtra("kind","user").putExtra("id",ownId));
                });
                action("Search people",()->browse("search_users"));
                action("Search worlds",()->browse("search_worlds"));
                action("View online friends",()->browse("friends_online"));
                action("View offline friends",()->browse("friends_offline"));
                action("World favorites",()->browse("worlds"));
                action("Recent worlds",()->browse("recent"));
                action("My groups",()->browse("groups"));
                action("Log out of VRChat",this::confirmLogout);
                return;
            }
            if(session.optBoolean("needs2fa")){
                render2fa();
                return;
            }
        }catch(Exception e){VRChatSession.clear(this);}
        page.addView(label("Sign in using a VRChat username/password account. Steam/Meta-only sign-in cannot be used here unless linked to a VRChat account.",13));
        user=new EditText(this);user.setSingleLine(true);user.setHint("VRChat username");user.setTextColor(Color.WHITE);
        page.addView(user);
        pass=new EditText(this);pass.setHint("VRChat password");pass.setTextColor(Color.WHITE);
        pass.setSingleLine(true);pass.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        page.addView(pass);
        action("Sign in to my VRChat account",this::login);
    }
    private void render2fa(){
        page.addView(label("VRChat requires two-factor verification.",15));
        code=new EditText(this);code.setHint("Authentication / email / recovery code");
        code.setTextColor(Color.WHITE);code.setSingleLine(true);page.addView(code);
        action("Use authenticator app code",()->{selected2fa="totp";verify();});
        action("Use email verification code",()->{selected2fa="emailotp";verify();});
        action("Use recovery code",()->{selected2fa="otp";verify();});
        action("Cancel VRChat login",()->{VRChatSession.clear(this);render();});
    }
    private void checkSession(){
        if(!StaffGate.unlocked(this)){finish();return;}
        say("Checking your VRChat account...");
        io.execute(()->{
            try{
                JSONObject result=VRChatApi.currentUser(this);
                runOnUiThread(()->{say("Connected: "+result.optString("displayName"));render();});
            }catch(Exception e){say("VRChat session: "+safe(e));}
        });
    }
    private void login(){
        if(!StaffGate.unlocked(this)){finish();return;}
        String username=user.getText().toString().trim();
        String password=pass.getText().toString();
        pass.setText(""); // Never keep password in a view after submit.
        say("Signing in directly to VRChat...");
        io.execute(()->{
            try{
                JSONObject result=VRChatApi.login(this,username,password);
                runOnUiThread(()->{
                    say(result.optBoolean("requires2fa")?"Enter your VRChat two-factor code":
                        "VRChat account connected");
                    render();
                });
            }catch(Exception e){say("VRChat login: "+safe(e));}
        });
    }
    private void verify(){
        if(!StaffGate.unlocked(this)){finish();return;}
        String value=code.getText().toString().trim();
        code.setText("");
        String mode=selected2fa;
        say("Verifying with VRChat...");
        io.execute(()->{
            try{VRChatApi.verify2fa(this,value,mode);runOnUiThread(this::render);}
            catch(Exception e){say("2FA: "+safe(e));}
        });
    }
    private void browse(String mode){
        if(!StaffGate.unlocked(this)){finish();return;}
        startActivity(new Intent(this,VRChatBrowseActivity.class).putExtra("mode",mode));
    }
    private void confirmLogout(){
        new AlertDialog.Builder(this)
            .setTitle("Log out of VRChat?")
            .setMessage("This deletes only your personal VRChat session, not your staff deployment or the background Node.")
            .setNegativeButton("Cancel",null)
            .setPositiveButton("Log out",(d,w)->{VRChatApi.logout(this);render();}).show();
    }
    private void say(String value){runOnUiThread(()->{if(message!=null)message.setText(value);});}
    private static String safe(Exception e){
        String t=e.getMessage();return t==null?e.getClass().getSimpleName():t.substring(0,Math.min(150,t.length()));
    }
    @Override protected void onDestroy(){io.shutdownNow();super.onDestroy();}
}
