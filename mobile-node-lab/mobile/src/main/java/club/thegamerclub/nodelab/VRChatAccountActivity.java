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
    // A Keystore-saved cookie is NOT evidence of current VRChat authorization.
    private static final int UNKNOWN=0,CHECKING=1,VERIFIED=2,EXPIRED=3,UNAVAILABLE=4;
    private int connectionState=UNKNOWN;
    private boolean connectionRequestRunning=false,showLoginForm=false;
    private String connectionMessage="",checkedUserId="";
    private long checkedAt=0L;
    private long lastAttemptAt=0L;
    // Similar to VRCX's conservative five-minute current-user validation.
    private static final long VERIFY_INTERVAL_MS=300_000L;

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
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE);
        if(!StaffGate.unlocked(this)){finish();return;}
        render();
    }
    @Override protected void onResume(){
        super.onResume();
        if(!StaffGate.unlocked(this)){finish();return;}
        // A rejected or temporarily unavailable session must not trigger a
        // rapid request loop every time this Activity resumes.
        if(!showLoginForm && !connectionRequestRunning &&
            System.currentTimeMillis()-lastAttemptAt>=VERIFY_INTERVAL_MS)
            checkSession(false);
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
            if(!showLoginForm &&
               !session.optString("userId","").isEmpty() &&
               !session.optString("auth","").isEmpty() &&
               !session.optBoolean("needs2fa",true)){
                String id=session.optString("userId","");
                boolean verified=connectionState==VERIFIED&&id.equals(checkedUserId);
                boolean expired=connectionState==EXPIRED;
                boolean unavailable=connectionState==UNAVAILABLE;
                int color=verified?CompanionStyle.GOOD:
                    expired?CompanionStyle.GOLD:CompanionStyle.MUTED;
                form.addView(CompanionStyle.chip(this,
                    verified?"● Signed in · verified with VRChat":
                    expired?"● VRChat session expired":
                    unavailable?"● VRChat connection unverified":
                    "○ Checking saved VRChat session…",
                    color,verified?0xFF163B33:expired?0xFF413025:0xFF24374A));
                gap(form,15);
                form.addView(label(session.optString("displayName","VRChat user"),23));
                gap(form,8);
                TextView hint=label(verified?
                    "VRChat verified your session online. Your account is connected.":
                    expired?
                    "VRChat rejected your saved login. Sign in again to restore "+
                    "your Quest Tools preview, friends and other account features.":
                    unavailable?
                    "Your account is still saved on this device, but VRChat couldn't "+
                    "confirm the connection. Check your network and retry.":
                    "Your saved account is present. Checking with VRChat now...",
                    13);
                hint.setTextColor(CompanionStyle.MUTED);form.addView(hint);gap(form,11);
                if(!connectionMessage.isEmpty()){
                    TextView detail=label(connectionMessage,12);
                    detail.setTextColor(expired?CompanionStyle.GOLD:CompanionStyle.MUTED);
                    form.addView(detail);gap(form,9);
                }
                if(verified){
                    action(form,"View my profile",()->{
                        startActivity(new Intent(this,VRChatDetailActivity.class)
                            .putExtra("kind","user").putExtra("id",id));
                    },true);
                    action(form,"My private VRChat notes",()->
                        startActivity(new Intent(this,VRChatNotesActivity.class)),false);
                    action(form,"Status & presets",()->
                        startActivity(new Intent(this,PresenceActivity.class)),false);
                    action(form,"Extended avatar favorites (VRC+)",()->
                        startActivity(new Intent(this,ExtendedAvatarActivity.class)),false);
                }
                Button retry=action(form,connectionRequestRunning?
                    "Checking VRChat…":"Check connection",
                    ()->checkSession(true),false);
                retry.setEnabled(!connectionRequestRunning);
                action(form,expired?"Sign in again":"Use another VRChat login",()->{
                    showLoginForm=true;
                    render();
                },false);
                action(form,"Sign out",this::confirmLogout,false);
                return;
            }
            if(session.optBoolean("needs2fa")){render2fa();return;}
        }catch(Exception e){VRChatSession.clear(this);}
        form.addView(label("Sign in with VRChat",19));gap(form,4);
        TextView explain=label("Sign in with your VRChat account email address OR original username (not your display name), plus your password. Your password is never saved on this device.",13);
        explain.setTextColor(CompanionStyle.MUTED);form.addView(explain);gap(form,10);
        user=field("EMAIL OR ORIGINAL USERNAME","Email or original username",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_NORMAL);
        pass=field("PASSWORD","Enter your password",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        pass.setTransformationMethod(android.text.method.PasswordTransformationMethod.getInstance());
        Button visibility=CompanionStyle.button(this,"Show password",false);
        form.addView(visibility,CompanionStyle.buttonParams(this));
        visibility.setOnClickListener(v->{
            boolean hidden=pass.getTransformationMethod() instanceof android.text.method.PasswordTransformationMethod;
            int cursor=pass.getSelectionStart();
            pass.setTransformationMethod(hidden?null:android.text.method.PasswordTransformationMethod.getInstance());
            visibility.setText(hidden?"Hide password":"Show password");
            pass.setSelection(Math.max(0,Math.min(cursor,pass.length())));
        });
        gap(form,8);
        action(form,"Sign in",this::login,true);
        if(showLoginForm)action(form,"Cancel · keep saved session",()->{
            showLoginForm=false;
            render();
        },false);
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
    private void checkSession(){checkSession(true);}
    private void checkSession(boolean manual){
        if(!StaffGate.unlocked(this)){finish();return;}
        if(showLoginForm||connectionRequestRunning)return;
        if(!manual && System.currentTimeMillis()-lastAttemptAt<VERIFY_INTERVAL_MS)return;
        final String expected;
        try{
            JSONObject saved=VRChatSession.load(this);
            expected=saved.optString("userId","");
            if(!expected.matches("usr_[0-9a-fA-F-]{36}") ||
               saved.optString("auth","").isEmpty() ||
               saved.optBoolean("needs2fa",true))return;
        }catch(Exception e){return;}
        lastAttemptAt=System.currentTimeMillis();
        connectionRequestRunning=true;
        connectionState=CHECKING;
        connectionMessage="";
        render();
        io.execute(()->{
            Exception failure=null;
            String actual="";
            try{
                JSONObject current=VRChatApi.currentUser(this);
                actual=current.optString("id","");
                if(!expected.equals(actual))
                    throw new SecurityException("VRChat returned another account; please sign in again.");
            }catch(Exception e){failure=e;}
            final Exception problem=failure;
            final String userId=actual;
            runOnUiThread(()->{
                if(isFinishing()||isDestroyed())return;
                connectionRequestRunning=false;
                if(problem==null){
                    checkedUserId=userId;
                    connectionState=VERIFIED;
                    checkedAt=System.currentTimeMillis();
                    connectionMessage="";
                }else{
                    checkedUserId="";
                    checkedAt=0L;
                    String detail=safe(problem);
                    String lower=detail.toLowerCase(java.util.Locale.US);
                    boolean authFailed=problem instanceof SecurityException &&
                        (lower.contains("expired")||lower.contains("sign in")||
                         lower.contains("another account"));
                    connectionState=authFailed?EXPIRED:UNAVAILABLE;
                    connectionMessage=(authFailed?
                        "VRChat rejected this session (relogin may be required): ":
                        "Could not verify right now; saved session was kept: ")+detail;
                }
                render();
            });
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
                runOnUiThread(()->{
                    showLoginForm=false;
                    connectionState=UNKNOWN;
                    checkedUserId="";
                    lastAttemptAt=0L;
                    render();
                    checkSession(false);
                });
            }catch(Exception e){say("Sign-in failed: "+safe(e));}
        });
    }
    private void verify(String mode){
        if(!StaffGate.unlocked(this)){finish();return;}
        String value=code.getText().toString().trim();
        code.setText("");say("Verifying...");
        io.execute(()->{
            try{
                VRChatApi.verify2fa(this,value,mode);
                runOnUiThread(()->{
                    showLoginForm=false;
                    connectionState=UNKNOWN;
                    checkedUserId="";
                    lastAttemptAt=0L;
                    render();
                    checkSession(false);
                });
            }
            catch(Exception e){say("Verification failed: "+safe(e));}
        });
    }
    private void confirmLogout(){
        new AlertDialog.Builder(this).setTitle("Sign out of VRChat?")
            .setMessage("Your staff access and player tracking will stay connected.")
            .setNegativeButton("Cancel",null)
            .setPositiveButton("Sign out",(d,w)->{
                VRChatApi.logout(this);
                connectionState=UNKNOWN;
                checkedUserId="";
                checkedAt=0L;
                lastAttemptAt=0L;
                connectionMessage="";
                showLoginForm=false;
                render();
            }).show();
    }
    private void say(String value){
        runOnUiThread(()->{if(message!=null)message.setText(value);});
    }
    private static String safe(Exception e){
        String m=e.getMessage();return m==null?"Connection error":m.substring(0,Math.min(140,m.length()));
    }
    @Override protected void onDestroy(){io.shutdownNow();super.onDestroy();}
}
