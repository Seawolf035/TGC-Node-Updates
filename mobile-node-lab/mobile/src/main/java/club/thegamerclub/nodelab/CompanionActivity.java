package club.thegamerclub.nodelab;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import rikka.shizuku.Shizuku;
import org.json.JSONObject;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Everything except owner-issued enrollment and updating is behind StaffGate. */
public final class CompanionActivity extends Activity {
    private static final int BG=0xFF0B1423,CARD=0xFF17263A,PANEL=0xFF213650,
        TEXT=0xFFE7F1FB,MUTED=0xFFACC2D6,ACCENT=0xFF56C8F2,GOLD=0xFFFFD071;
    private final ExecutorService gateWorker=Executors.newSingleThreadExecutor();
    private final Handler main=new Handler(Looper.getMainLooper());
    private boolean verifying=false;
    private boolean showingUnlocked=false;
    private TextView verificationNotice;
    private LinearLayout page;
    private TextView gateLabel;
    private String tab="HOME";
    private final Runnable refreshGate=new Runnable(){
        @Override public void run(){
            if(!isFinishing()){
                // Always check the live relay again: this will enforce revocations.
                revalidate();
                main.postDelayed(this,90000);
            }
        }
    };
    private int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private GradientDrawable rounded(int color,int radius){
        GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;
    }
    private TextView text(String s,int size,int color,boolean bold){
        TextView t=new TextView(this);t.setText(s);t.setTextColor(color);t.setTextSize(size);
        t.setLineSpacing(dp(2),1.05f);
        if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        return t;
    }
    private LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private void gap(LinearLayout root,int n){root.addView(new View(this),new LinearLayout.LayoutParams(1,dp(n)));}
    private void shell(){
        LinearLayout body=column();body.setBackgroundColor(BG);setContentView(body);
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(18),dp(15),dp(18),dp(13));body.addView(header);
        ImageView logo=new ImageView(this);logo.setImageResource(R.drawable.tgc_logo);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        header.addView(logo,new LinearLayout.LayoutParams(dp(54),dp(54)));
        LinearLayout title=column();title.setPadding(dp(12),0,0,0);
        title.addView(text("THE GAMER CLUB",12,ACCENT,true));
        title.addView(text("STAFF COMPANION",20,TEXT,true));
        title.addView(text("ANDROID • "+BuildConfig.VERSION_NAME,11,MUTED,false));header.addView(title);
        ScrollView scroll=new ScrollView(this);body.addView(scroll,new LinearLayout.LayoutParams(-1,0,1f));
        page=column();page.setPadding(dp(18),dp(12),dp(18),dp(30));scroll.addView(page);
    }
    private LinearLayout card(String title,String subtitle){
        LinearLayout c=column();c.setPadding(dp(16),dp(17),dp(16),dp(17));
        c.setBackground(rounded(CARD,15));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(15);
        page.addView(c,p);
        c.addView(text(title,18,TEXT,true));gap(c,7);
        if(subtitle!=null){c.addView(text(subtitle,13,MUTED,false));gap(c,10);}
        return c;
    }
    private void action(LinearLayout parent,String name,String info,Runnable task){
        LinearLayout box=column();box.setPadding(dp(13),dp(13),dp(13),dp(13));
        box.setBackground(rounded(PANEL,12));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(8);
        parent.addView(box,lp);box.addView(text(name,15,TEXT,true));
        if(info!=null){gap(box,3);box.addView(text(info,12,MUTED,false));}
        box.setOnClickListener(v->task.run());
    }
    @Override public void onCreate(Bundle b){
        super.onCreate(b);getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
        locked("Checking your active TGC staff deployment...");
    }
    @Override protected void onResume(){
        super.onResume();
        main.removeCallbacks(refreshGate);
        revalidate();
        main.postDelayed(refreshGate,90000);
    }
    @Override protected void onPause(){main.removeCallbacks(refreshGate);super.onPause();}
    @Override protected void onDestroy(){
        main.removeCallbacks(refreshGate);
        gateWorker.shutdownNow();
        super.onDestroy();
    }
    private void locked(String reason){
        showingUnlocked=false;
        verificationNotice=null;
        shell();
        LinearLayout c=card("Staff access required","The Companion is a benefit for active TGC staff. Installing or copying the APK will not unlock it.");
        gateLabel=text(reason,14,GOLD,true);c.addView(gateLabel);gap(c,12);
        action(c,"Redeem Quick Deployment link","One-time invitation issued by the Main Launcher",()->startActivity(new Intent(this,MobileDeployActivity.class)));
        action(c,"Check Companion updates","Updates and security fixes are available without unlocking",()->startActivity(new Intent(this,MobileUpdaterActivity.class)));
        LinearLayout about=card("Two separate accounts",null);
        about.addView(text("1. TGC staff deployment verifies your Discord staff identity and is checked periodically.\n\n2. Your own VRChat account is used for personal VRCX-style features after unlocking. No shared TGC VRChat login.",13,MUTED,false));
    }
    private void revalidate(){
        if(verifying)return;
        verifying=true;
        // Never invalidate a previously approved grant before asking the relay.
        // Let people keep using their current screen during ordinary refreshes.
        if(!StaffGate.unlocked(this) && showingUnlocked){
            locked("Your previous verification expired. Checking the relay...");
        }
        gateWorker.execute(()->{
            String failed=null;
            try{StaffGate.verifyOnline(this);}
            catch(Exception e){
                String message=e.getMessage();
                failed=message==null?e.getClass().getSimpleName():
                    message.substring(0,Math.min(120,message.length()));
            }
            final String fail=failed;
            runOnUiThread(()->{
                verifying=false;
                if(isFinishing() || isDestroyed())return;
                // A confirmed revocation clears the grant immediately. A temporary
                // HTTP failure does NOT clear it until the bounded grace runs out.
                if(!StaffGate.unlocked(this)){
                    locked(fail==null?"Staff deployment needs verification":
                        "Staff verification unavailable: "+fail+"\nRetrying automatically. You do not need a new deployment link.");
                }else if(!showingUnlocked){
                    renderUnlocked();
                }else if(verificationNotice!=null){
                    verificationNotice.setText(fail==null?
                        "Verified with TGC relay • session remains active":
                        "Relay connection temporarily unavailable • keeping previously verified session");
                    verificationNotice.setTextColor(fail==null?ACCENT:GOLD);
                }
            });
        });
    }
    private void requiresStaff(Runnable task){
        if(!StaffGate.unlocked(this)){locked("Staff verification expired; checking again...");revalidate();return;}
        task.run();
    }
    private void renderUnlocked(){
        if(!StaffGate.unlocked(this)){locked("Deployment needs online verification");return;}
        showingUnlocked=true;
        shell();
        LinearLayout top=card("STAFF VERIFIED","Your staff deployment is verified. Background rechecks won't interrupt your screen.");
        verificationNotice=text("Verified with TGC relay • session remains active",12,ACCENT,false);
        top.addView(verificationNotice);
        gap(top,8);
        action(top,"Your VRChat account","Sign in, verify your session, or log out",()->requiresStaff(()->startActivity(new Intent(this,VRChatAccountActivity.class))));
        action(top,"My VRChat profile","View my own account information here in the app",this::openMyProfile);
        LinearLayout features=card("VRChat companion","Native personal-account views and local history. Nothing here uses the GM1 account.");
        action(features,"Online friends","Personal account • current availability",()->openBrowse("friends_online"));
        action(features,"Offline friends","Personal account • offline friends",()->openBrowse("friends_offline"));
        action(features,"Favorite worlds","Personal account • world favorites",()->openBrowse("worlds"));
        action(features,"Recently visited worlds","Personal account • VRChat recent worlds",()->openBrowse("recent"));
        action(features,"My groups","Personal account • visible group memberships",()->openBrowse("groups"));
        action(features,"Recently encountered","Local Node sightings • up to 30 days",()->openBrowse("encounters"));
        action(features,"Search VRChat users","Find players by display name inside the app",()->openBrowse("search_users"));
        action(features,"Search VRChat worlds","Find public worlds without using a browser",()->openBrowse("search_worlds"));
        LinearLayout node=card("TGC Mobile Invite Node","The background tracker continues to use Shizuku and the existing GM4 → GM1 relay pipeline.");
        action(node,"Node status & controls","Watch joins, queue, relay acknowledgments, or stop tracking",
          ()->requiresStaff(()->startActivity(new Intent(this,MobileDeployActivity.class))));
        action(node,"Advanced tracking diagnostics","Private file-access and watcher status",
          ()->requiresStaff(()->startActivity(new Intent(this,MainActivity.class))));
        LinearLayout settings=card("Maintenance",null);
        action(settings,"Check for app updates","Download SHA-256 and signing-verified APK updates",()->startActivity(new Intent(this,MobileUpdaterActivity.class)));
        action(settings,"VRChat account settings","Personal login and session controls",()->requiresStaff(()->startActivity(new Intent(this,VRChatAccountActivity.class))));
    }
    private void openBrowse(String mode){
        requiresStaff(()->startActivity(new Intent(this,VRChatBrowseActivity.class).putExtra("mode",mode)));
    }
    private void openMyProfile(){
        requiresStaff(()->{
            try{
                JSONObject session=VRChatSession.load(this);
                String id=session.optString("userId","");
                if(id.matches("usr_[0-9a-fA-F-]{36}")){
                    startActivity(new Intent(this,VRChatDetailActivity.class)
                        .putExtra("kind","user").putExtra("id",id));
                }else{
                    Toast.makeText(this,"Sign in to your VRChat account first",Toast.LENGTH_LONG).show();
                    startActivity(new Intent(this,VRChatAccountActivity.class));
                }
            }catch(Exception e){
                Toast.makeText(this,"VRChat login required",Toast.LENGTH_SHORT).show();
                startActivity(new Intent(this,VRChatAccountActivity.class));
            }
        });
    }
}
