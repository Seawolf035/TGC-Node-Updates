package club.thegamerclub.nodelab;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONObject;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Staff-exclusive, native VRChat Companion dashboard.
 * Deployment access is still checked by StaffGate; user-facing labels omit
 * private recruitment implementation and developer-oriented terminology.
 */
public final class CompanionActivity extends Activity {
    private final ExecutorService gateWorker=Executors.newSingleThreadExecutor();
    private final Handler main=new Handler(Looper.getMainLooper());
    private boolean verifying=false,showingUnlocked=false,advanced=false;
    private TextView verificationNotice;
    private LinearLayout page,root;
    private String tab="home";
    private final Runnable refreshGate=new Runnable(){
        @Override public void run(){
            if(!isFinishing()&&!isDestroyed()){
                revalidate();
                main.postDelayed(this,90_000L);
            }
        }
    };
    private int dp(float n){return CompanionStyle.dp(this,n);}
    private TextView txt(String s,int size,int color,boolean bold){
        return CompanionStyle.text(this,s,size,color,bold);
    }
    private LinearLayout stack(){
        LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;
    }
    private void space(LinearLayout l,int n){
        l.addView(new View(this),new LinearLayout.LayoutParams(1,dp(n)));
    }
    private void header(){
        LinearLayout h=new LinearLayout(this);h.setGravity(Gravity.CENTER_VERTICAL);
        h.setPadding(dp(20),dp(16),dp(20),dp(16));
        h.setBackgroundColor(CompanionStyle.BG);
        root.addView(h);
        ImageView logo=new ImageView(this);logo.setImageResource(R.drawable.tgc_logo);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        h.addView(logo,new LinearLayout.LayoutParams(dp(49),dp(49)));
        LinearLayout brand=stack();
        brand.setPadding(dp(12),0,0,0);
        TextView over=txt("THE GAMER CLUB",11,CompanionStyle.ACCENT,true);
        over.setLetterSpacing(.13f);brand.addView(over);
        brand.addView(txt("Staff Companion",22,CompanionStyle.TEXT,true));
        h.addView(brand,new LinearLayout.LayoutParams(0,-2,1f));
        if(showingUnlocked){
            TextView status=CompanionStyle.chip(this,"● Active",CompanionStyle.GOOD,0xFF173B37);
            h.addView(status);
        }
    }
    private void shell(boolean navigation){
        root=stack();root.setBackgroundColor(CompanionStyle.BG);setContentView(root);
        getWindow().setStatusBarColor(CompanionStyle.BG);
        getWindow().setNavigationBarColor(CompanionStyle.BG);
        header();
        View rule=new View(this);rule.setBackgroundColor(CompanionStyle.BORDER);
        root.addView(rule,new LinearLayout.LayoutParams(-1,dp(1)));
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1f));
        page=stack();page.setPadding(dp(18),dp(22),dp(18),dp(25));
        scroll.addView(page);
        if(navigation)bottomBar();
    }
    private void bottomBar(){
        LinearLayout nav=new LinearLayout(this);nav.setGravity(Gravity.CENTER_VERTICAL);
        nav.setPadding(dp(9),dp(7),dp(9),dp(9));
        nav.setBackgroundColor(CompanionStyle.SURFACE);
        root.addView(nav,new LinearLayout.LayoutParams(-1,dp(66)));
        addTab(nav,"⌂","Home","home");
        addTab(nav,"◇","Explore","explore");
        addTab(nav,"◉","Tracking","tracking");
        addTab(nav,"⚙","Settings","settings");
    }
    private void addTab(LinearLayout nav,String glyph,String name,String id){
        LinearLayout item=stack();item.setGravity(Gravity.CENTER);
        boolean selected=tab.equals(id);
        item.setBackground(CompanionStyle.shape(this,selected?CompanionStyle.RAISED:CompanionStyle.SURFACE,13,
          selected?CompanionStyle.BORDER:0));
        nav.addView(item,new LinearLayout.LayoutParams(0,-1,1f));
        TextView symbol=txt(glyph,20,selected?CompanionStyle.ACCENT:CompanionStyle.MUTED,false);
        symbol.setGravity(Gravity.CENTER);item.addView(symbol);
        TextView title=txt(name,11,selected?CompanionStyle.TEXT:CompanionStyle.MUTED,selected);
        title.setGravity(Gravity.CENTER);item.addView(title);
        item.setOnClickListener(v->{if(!tab.equals(id)){tab=id;renderUnlocked();}});
    }
    private LinearLayout card(){
        LinearLayout c=CompanionStyle.card(this);
        page.addView(c,CompanionStyle.cardParams(this));return c;
    }
    private void headline(String kicker,String title,String subtitle){
        page.addView(CompanionStyle.section(this,kicker));
        page.addView(txt(title,28,CompanionStyle.TEXT,true));
        space(page,6);
        page.addView(txt(subtitle,14,CompanionStyle.MUTED,false));
        space(page,20);
    }
    private void action(LinearLayout parent,String glyph,String name,String description,Runnable callback){
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14),dp(15),dp(12),dp(15));
        row.setBackground(CompanionStyle.shape(this,CompanionStyle.RAISED,14,0));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
        lp.bottomMargin=dp(9);parent.addView(row,lp);
        TextView icon=txt(glyph,22,CompanionStyle.ACCENT,true);
        icon.setGravity(Gravity.CENTER);
        icon.setBackground(CompanionStyle.shape(this,0xFF193D54,11,0));
        row.addView(icon,new LinearLayout.LayoutParams(dp(43),dp(43)));
        LinearLayout middle=stack();middle.setPadding(dp(13),0,dp(9),0);
        row.addView(middle,new LinearLayout.LayoutParams(0,-2,1f));
        middle.addView(txt(name,15,CompanionStyle.TEXT,true));
        if(description!=null&&!description.isEmpty()){
            space(middle,4);middle.addView(txt(description,12,CompanionStyle.MUTED,false));
        }
        row.addView(txt("›",29,CompanionStyle.MUTED,false));
        row.setOnClickListener(v->callback.run());
    }
    private void twinActions(LinearLayout parent,String a,String descA,String glyphA,Runnable clickA,
                             String b,String descB,String glyphB,Runnable clickB){
        LinearLayout line=new LinearLayout(this);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(8);
        parent.addView(line,lp);
        tile(line,a,descA,glyphA,clickA);
        tile(line,b,descB,glyphB,clickB);
    }
    private void tile(LinearLayout line,String name,String description,String glyph,Runnable click){
        LinearLayout tile=stack();
        tile.setPadding(dp(14),dp(14),dp(12),dp(15));
        tile.setBackground(CompanionStyle.shape(this,CompanionStyle.RAISED,14,0));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(132),1f);
        lp.setMargins(dp(3),0,dp(3),0);line.addView(tile,lp);
        tile.addView(txt(glyph,24,CompanionStyle.ACCENT,false));
        space(tile,12);
        tile.addView(txt(name,15,CompanionStyle.TEXT,true));
        space(tile,4);
        tile.addView(txt(description,12,CompanionStyle.MUTED,false));
        tile.setOnClickListener(v->click.run());
    }
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);
        if(saved!=null){tab=saved.getString("tab","home");advanced=saved.getBoolean("advanced",false);}
        locked("Checking your staff access...");
    }
    @Override protected void onSaveInstanceState(Bundle out){
        out.putString("tab",tab);out.putBoolean("advanced",advanced);
        super.onSaveInstanceState(out);
    }
    @Override protected void onResume(){
        super.onResume();
        main.removeCallbacks(refreshGate);
        revalidate();
        main.postDelayed(refreshGate,90_000L);
    }
    @Override protected void onPause(){
        main.removeCallbacks(refreshGate);
        super.onPause();
    }
    @Override protected void onDestroy(){
        main.removeCallbacks(refreshGate);gateWorker.shutdownNow();super.onDestroy();
    }
    private void locked(String reason){
        showingUnlocked=false;verificationNotice=null;
        shell(false);
        page.addView(CompanionStyle.section(this,"STAFF ACCESS"));
        page.addView(txt("Welcome to TGC.",29,CompanionStyle.TEXT,true));
        space(page,8);
        page.addView(txt("Your Companion stays private to approved staff. Link this device once to get started.",
            15,CompanionStyle.MUTED,false));
        space(page,24);
        LinearLayout c=card();
        TextView notice=CompanionStyle.chip(this,"● Access required",CompanionStyle.GOLD,0xFF463923);
        c.addView(notice);space(c,13);
        c.addView(txt(reason,14,CompanionStyle.MUTED,false));
        space(c,18);
        action(c,"↗","Link this device","Use your personal staff setup link",
            ()->startActivity(new Intent(this,MobileDeployActivity.class)));
        action(c,"↓","Check for updates","Install the latest Companion version",
            ()->startActivity(new Intent(this,MobileUpdaterActivity.class)));
        space(page,4);
        page.addView(txt("Your personal VRChat account is connected separately after staff access is approved.",
            12,CompanionStyle.MUTED,false));
    }
    private void revalidate(){
        if(verifying)return;
        verifying=true;
        if(!StaffGate.unlocked(this)&&showingUnlocked)
            locked("Staff access has expired. Reconnecting...");
        gateWorker.execute(()->{
            String failed=null;boolean denied=false;
            try{StaffGate.verifyOnline(this);}
            catch(Exception e){
                denied=e instanceof SecurityException;
                String m=e.getMessage();
                failed=m==null?e.getClass().getSimpleName():m.substring(0,Math.min(110,m.length()));
            }
            final String error=failed;
            final boolean hard=denied;
            runOnUiThread(()->{
                verifying=false;
                if(isFinishing()||isDestroyed())return;
                if(!StaffGate.unlocked(this)){
                    locked(error==null?"Staff access must be verified":
                        hard?"Staff access was denied. Contact a group owner.":
                        "Connection unavailable. Retrying automatically.");
                }else if(!showingUnlocked)renderUnlocked();
                else if(verificationNotice!=null){
                    verificationNotice.setText(error==null?"Your staff access is up to date":
                        "Reconnecting • your access remains active");
                    verificationNotice.setTextColor(error==null?CompanionStyle.GOOD:CompanionStyle.GOLD);
                }
            });
        });
    }
    private void needsStaff(Runnable next){
        if(!StaffGate.unlocked(this)){locked("Checking your staff access...");revalidate();return;}
        next.run();
    }
    private void renderUnlocked(){
        if(!StaffGate.unlocked(this)){locked("Checking your staff access...");return;}
        showingUnlocked=true;shell(true);
        switch(tab){
            case "explore":renderExplore();break;
            case "tracking":renderTracking();break;
            case "settings":renderSettings();break;
            default:renderHome();break;
        }
    }
    private String displayName(){
        try{
            String name=VRChatSession.load(this).optString("displayName","");
            return name.isEmpty()?"Welcome back":"Hey, "+name;
        }catch(Exception e){return "Welcome back";}
    }
    private void renderHome(){
        headline("YOUR SPACE",displayName()+".","Your friends, worlds, and community—all in one place.");
        LinearLayout hero=card();
        hero.setBackground(CompanionStyle.gradient(this,0xFF1B445A,0xFF18283D,19));
        TextView tag=CompanionStyle.chip(this,"● STAFF VERIFIED",CompanionStyle.GOOD,0xFF193E3C);
        hero.addView(tag);space(hero,15);
        hero.addView(txt("Ready when you are.",21,CompanionStyle.TEXT,true));
        space(hero,5);
        verificationNotice=txt("Your staff access is up to date",13,CompanionStyle.MUTED,false);
        hero.addView(verificationNotice);space(hero,16);
        action(hero,"☺","Your VRChat profile","View your account and status",this::openMyProfile);
        page.addView(CompanionStyle.section(this,"QUICK ACCESS"));
        LinearLayout quick=card();
        twinActions(quick,"Friends","See who's around","♧",()->openBrowse("friends_online"),
            "Worlds","Find somewhere to go","◇",()->openBrowse("worlds"));
        twinActions(quick,"Groups","Your communities","◈",()->openBrowse("groups"),
            "Recent players","People you've met","◎",()->openBrowse("encounters"));
        page.addView(CompanionStyle.section(this,"DISCOVER"));
        LinearLayout discover=card();
        action(discover,"⌕","Find people","Search players by name",()->openBrowse("search_users"));
        action(discover,"⌕","Find worlds","Browse and search worlds",()->openBrowse("search_worlds"));
    }
    private void renderExplore(){
        headline("EXPLORE","Your VRChat","Everything you want to discover, without leaving the app.");
        page.addView(CompanionStyle.section(this,"PEOPLE"));
        LinearLayout people=card();
        action(people,"☺","Online friends","See who's around now",()->openBrowse("friends_online"));
        action(people,"◌","Offline friends","Find friends who aren't online",()->openBrowse("friends_offline"));
        action(people,"⌕","Find people","Search VRChat users",()->openBrowse("search_users"));
        action(people,"◎","Recent players","People you've encountered",()->openBrowse("encounters"));
        page.addView(CompanionStyle.section(this,"PLACES & COMMUNITY"));
        LinearLayout places=card();
        action(places,"◇","Favorite worlds","Your saved places",()->openBrowse("worlds"));
        action(places,"↺","Recently visited","Worlds you've explored",()->openBrowse("recent"));
        action(places,"⌕","Find worlds","Search for something new",()->openBrowse("search_worlds"));
        action(places,"◈","My groups","Your VRChat communities",()->openBrowse("groups"));
    }
    private void renderTracking(){
        headline("ACTIVITY","Player tracking","Capture encounters while you're exploring VRChat.");
        android.content.SharedPreferences s=getSharedPreferences("tgc_mobile_node_status",0);
        long heartbeat=s.getLong("service_heartbeat_at",0);
        boolean active=s.getBoolean("running",false) && heartbeat>0 &&
            System.currentTimeMillis()-heartbeat<90000;
        LinearLayout status=card();
        status.addView(CompanionStyle.chip(this,active?"● Tracking active":"○ Tracking paused",
            active?CompanionStyle.GOOD:CompanionStyle.GOLD,active?0xFF183D37:0xFF3E3325));
        space(status,15);
        status.addView(txt(active?"You're connected.":"Tracking isn't active.",20,CompanionStyle.TEXT,true));
        space(status,8);
        status.addView(txt(active?"Your recent encounters are being captured in the background.":
            "Open tracking controls to start or check the connection.",13,CompanionStyle.MUTED,false));
        page.addView(CompanionStyle.section(this,"YOUR ACTIVITY"));
        LinearLayout stats=card();
        twinActions(stats,Integer.toString(s.getInt("captured_total",0)),"Players recorded","◎",
            ()->openBrowse("encounters"),
            Integer.toString(s.getInt("uploaded_total",0)),"Records synced","↗",
            ()->needsStaff(()->startActivity(new Intent(this,MobileDeployActivity.class))));
        action(stats,"⚙","Tracking controls","Start, stop or check your tracking",
            ()->needsStaff(()->startActivity(new Intent(this,MobileDeployActivity.class))));
        page.addView(txt("Tracking works in the background while the Companion is open or closed.",
            12,CompanionStyle.MUTED,false));
    }
    private void renderSettings(){
        headline("PREFERENCES","Settings","Your account, device and Companion.");
        page.addView(CompanionStyle.section(this,"YOUR ACCOUNT"));
        LinearLayout account=card();
        action(account,"☺","My VRChat account","Profile, sign-in and sign-out",
            ()->needsStaff(()->startActivity(new Intent(this,VRChatAccountActivity.class))));
        action(account,"♜","Staff access","View your linked device",
            ()->needsStaff(()->startActivity(new Intent(this,MobileDeployActivity.class))));
        page.addView(CompanionStyle.section(this,"APP"));
        LinearLayout app=card();
        action(app,"↓","Check for updates","Keep your Companion up to date",
            ()->startActivity(new Intent(this,MobileUpdaterActivity.class)));
        action(app,"ⓘ","About","Version "+BuildConfig.VERSION_NAME,()->{
            new android.app.AlertDialog.Builder(this)
                .setTitle("TGC Staff Companion")
                .setMessage("A private companion for The Gamer Club staff.\nVersion "+
                    BuildConfig.VERSION_NAME+"\n\nYour personal account and staff access stay separate.")
                .setPositiveButton("Close",null).show();
        });
        page.addView(CompanionStyle.section(this,"MORE"));
        LinearLayout extra=card();
        action(extra,"⚙",advanced?"Hide advanced tools":"Advanced tools",
            "Optional diagnostics for troubleshooting",()->{advanced=!advanced;renderUnlocked();});
        if(advanced){
            action(extra,"◉","Connection details","Detailed tracking and sync information",
                ()->needsStaff(()->startActivity(new Intent(this,MobileDeployActivity.class)
                    .putExtra("advanced",true))));
            action(extra,"⌘","Tracking diagnostics","Troubleshooting tools",
                ()->needsStaff(()->startActivity(new Intent(this,MainActivity.class))));
        }
    }
    private void openBrowse(String mode){
        needsStaff(()->startActivity(new Intent(this,VRChatBrowseActivity.class).putExtra("mode",mode)));
    }
    private void openMyProfile(){
        needsStaff(()->{
            try{
                JSONObject account=VRChatSession.load(this);
                String id=account.optString("userId","");
                if(id.matches("usr_[0-9a-fA-F-]{36}")){
                    startActivity(new Intent(this,VRChatDetailActivity.class)
                        .putExtra("kind","user").putExtra("id",id));
                }else startActivity(new Intent(this,VRChatAccountActivity.class));
            }catch(Exception e){
                startActivity(new Intent(this,VRChatAccountActivity.class));
            }
        });
    }
}
