package club.thegamerclub.nodelab;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.ComponentName;
import android.net.Uri;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import rikka.shizuku.Shizuku;

/**
 * Staff Companion preview. This does NOT bypass or assert staff authorization.
 * All restricted functionality remains disabled until the backend supports
 * mobile_companion provisioning and verifies staff membership server-side.
 */
public class CompanionActivity extends Activity {
    private static final int BG=0xFF0B1423,CARD=0xFF17263A,PANEL=0xFF213650,
        TEXT=0xFFE7F1FB,MUTED=0xFFACC2D6,ACCENT=0xFF56C8F2,GOLD=0xFFFFD071;
    private LinearLayout page;
    private TextView connection;
    private String tab="HOME";
    private int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private GradientDrawable rounded(int background,int radius){
        GradientDrawable d=new GradientDrawable(); d.setColor(background);d.setCornerRadius(dp(radius));return d;
    }
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);
        getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
        render("HOME");
    }
    @Override protected void onResume(){super.onResume();if(connection!=null)refreshConnection();}
    private TextView text(String value,int sp,int color,boolean heavy){
        TextView t=new TextView(this);t.setText(value);t.setTextColor(color);t.setTextSize(sp);
        t.setLineSpacing(dp(2),1.06f);
        if(heavy)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        return t;
    }
    private void space(LinearLayout col,int n){View v=new View(this); col.addView(v,new LinearLayout.LayoutParams(1,dp(n)));}
    private LinearLayout column(){
        LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;
    }
    private LinearLayout section(String heading,String description){
        LinearLayout c=column();c.setPadding(dp(17),dp(16),dp(17),dp(16));c.setBackground(rounded(CARD,16));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(14);page.addView(c,lp);
        if(heading!=null){c.addView(text(heading,19,TEXT,true));space(c,7);}
        if(description!=null){c.addView(text(description,13,MUTED,false));space(c,13);}
        return c;
    }
    private void action(LinearLayout container,String label,String sub,Runnable handler){
        LinearLayout item=column();item.setPadding(dp(14),dp(13),dp(14),dp(13));
        item.setBackground(rounded(PANEL,12));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(9);container.addView(item,lp);
        item.addView(text(label,15,TEXT,true));
        if(sub!=null){space(item,3);item.addView(text(sub,12,MUTED,false));}
        item.setOnClickListener(v->handler.run());
    }
    private void note(LinearLayout c,String value){c.addView(text(value,13,MUTED,false));}
    private void render(String tabName){
        this.tab=tabName;connection=null;
        LinearLayout body=column();body.setBackgroundColor(BG);setContentView(body);
        LinearLayout masthead=new LinearLayout(this);masthead.setOrientation(LinearLayout.HORIZONTAL);
        masthead.setGravity(Gravity.CENTER_VERTICAL);masthead.setPadding(dp(18),dp(16),dp(18),dp(13));
        body.addView(masthead);
        ImageView logo=new ImageView(this);logo.setImageResource(R.drawable.tgc_logo);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        masthead.addView(logo,new LinearLayout.LayoutParams(dp(54),dp(54)));
        LinearLayout titles=column();titles.setPadding(dp(10),0,0,0);
        titles.addView(text("THE GAMER CLUB",13,ACCENT,true));
        titles.addView(text("STAFF COMPANION",20,TEXT,true));
        titles.addView(text("ANDROID PREVIEW  •  v0.5",11,MUTED,false));
        masthead.addView(titles,new LinearLayout.LayoutParams(0,-2,1f));

        LinearLayout nav=new LinearLayout(this);nav.setPadding(dp(10),0,dp(10),dp(10));
        body.addView(nav,new LinearLayout.LayoutParams(-1,dp(55)));
        String[] tabs={"HOME","NODE","VRCHAT","STAFF"};
        for(String t:tabs){
            TextView item=text(t,12,tab.equals(t)?BG:MUTED,true);
            item.setGravity(Gravity.CENTER);
            item.setBackground(rounded(tab.equals(t)?ACCENT:CARD,10));
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-1,1);
            p.setMargins(dp(3),0,dp(3),0);
            nav.addView(item,p);
            item.setOnClickListener(v->render(t));
        }
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);
        body.addView(scroll,new LinearLayout.LayoutParams(-1,0,1f));
        page=column();page.setPadding(dp(18),dp(12),dp(18),dp(30));scroll.addView(page);
        if(tab.equals("HOME"))home();
        else if(tab.equals("NODE"))node();
        else if(tab.equals("VRCHAT"))vrchat();
        else staff();
    }
    private void home(){
        LinearLayout welcome=section("Welcome to the Companion",
          "An experimental staff-oriented VRChat toolkit. Your automatic Android player detector is already working through Shizuku.");
        connection=text("Checking Shizuku...",14,ACCENT,true);
        welcome.addView(connection);
        refreshConnection();
        LinearLayout engine=section("Live Node", "Automatic VRChat log monitoring, new joins and leaves, and privacy-safe diagnostics.");
        action(engine,"Deploy Mobile Node  →","Redeem an owner-issued staff setup link",this::openDeploy);
        action(engine,"Open Node diagnostics","Start or stop local log monitoring",this::openNode);
        LinearLayout tools=section("Quick access","Useful VRChat tools while the full companion API features are being developed.");
        action(tools,"Check for Companion updates","Download verified releases and open Android installer",this::openUpdater);
        action(tools,"Friends","Open your VRChat friends page",()->openWeb("https://vrchat.com/home/friends"));
        action(tools,"Groups","Open your VRChat groups page",()->openWeb("https://vrchat.com/home/groups"));
        action(tools,"Worlds","Browse VRChat worlds",()->openWeb("https://vrchat.com/home/worlds"));
        LinearLayout gate=section("Staff access",null);
        gate.addView(text("AUTHENTICATION NOT CONNECTED",13,GOLD,true));space(gate,8);
        note(gate,"Owner-issued Quick Deployment and live staff eligibility checks will be required before staff-only services and the TGC relay can be used.");
    }
    private void node(){
        LinearLayout c=section("Automatic Invite Node","The v0.3 Shizuku monitor is built in as the current Node engine. It reads original VRChat Mobile logs, with no exports needed.");
        connection=text("Checking Shizuku...",14,ACCENT,true);c.addView(connection);refreshConnection();
        space(c,12);
        action(c,"Staff Quick Deployment","Paste one-use link, start/stop uploads, view offline queue",this::openDeploy);
        action(c,"Companion updates","Check/download verified APK updates",this::openUpdater);
        action(c,"Open live tracking controls","Start watcher · View results · Stop watcher",this::openNode);
        action(c,"Diagnose log permissions","Original VRChat folder, read status and logcat probes",this::openNode);
        LinearLayout details=section("Current safety mode",null);
        note(details,"The Mobile Companion Node can upload sightings after owner-issued deployment. It preserves GM4 membership checks and GM1-only invites. This build is a test release; staff access is enforced by the upgraded relay.");
        space(details,9);
        note(details,"Sightings are buffered in SQLite and sent through the TGC relay only after staff deployment verification. Stop or unlink the Node from the Staff Deployment screen.");
    }
    private void vrchat(){
        LinearLayout c=section("VRChat","Open the official VRChat web experience without storing a VRChat password in this app.");
        action(c,"Friends","View and manage your connections",()->openWeb("https://vrchat.com/home/friends"));
        action(c,"Worlds","Browse and favorite worlds",()->openWeb("https://vrchat.com/home/worlds"));
        action(c,"Groups","View your groups",()->openWeb("https://vrchat.com/home/groups"));
        action(c,"VRChat Home","Open the official VRChat dashboard",()->openWeb("https://vrchat.com/home"));
        LinearLayout about=section("Coming next",null);
        note(about,"Native friend and world search, notifications, group moderation tools where permitted, and personal recruiting statistics will require API access and your staff authorization flow. These are not connected in this preview.");
    }
    private void staff(){
        LinearLayout gate=section("Staff-only access",null);
        gate.addView(text("STAFF VERIFICATION REQUIRED",15,GOLD,true));space(gate,11);
        note(gate,"Installation alone does not prove TGC staff membership. To use the Node, redeem a single-use owner-issued Mobile Companion link. The relay checks the active roster.");
        space(gate,12);
        note(gate,"The deployment link provides verified credentials. The relay rechecks staff eligibility, and Main Launcher can revoke the deployed Node without erasing historic recruitment credits.");
        action(gate,"Redeem Staff Quick Deployment  →","Link this device, start or stop the Node",this::openDeploy);
        LinearLayout c=section("Planned staff features",null);
        note(c,"• Verified staff identity and rank\n• Private group moderation shortcuts\n• Recruitment statistics and Node attribution\n• TGC-wide release notifications\n• Logout and access revocation");
        LinearLayout privacy=section("Privacy and integrity",null);
        note(privacy,"No secrets are embedded in the APK. Enrolled credentials are Android Keystore-encrypted. The Node uses HTTPS only for authenticated relay requests. VRChat shortcuts still open in your browser. Shizuku requires your permission.");
    }
    private void refreshConnection(){
        if(connection==null)return;
        try{
            boolean connected=Shizuku.pingBinder();
            connection.setText(connected?"● Shizuku connected — Node ready":"○ Shizuku not connected — start Shizuku");
            connection.setTextColor(connected?ACCENT:GOLD);
        }catch(Exception e){
            connection.setText("○ Shizuku connection unavailable");
            connection.setTextColor(GOLD);
        }
    }
    private void openUpdater(){startActivity(new Intent(this,MobileUpdaterActivity.class));}
    private void openDeploy(){startActivity(new Intent(this,MobileDeployActivity.class));}
    private void openNode(){startActivity(new Intent(this,MainActivity.class));}
    private void openWeb(String url){
        try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));}
        catch(ActivityNotFoundException e){Toast.makeText(this,"No browser available",Toast.LENGTH_SHORT).show();}
    }
}
