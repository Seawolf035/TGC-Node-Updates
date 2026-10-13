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
    private final ExecutorService homeWorker=Executors.newSingleThreadExecutor();
    private boolean homeLoading=false;
    private TextView homeNodeLabel,homeOverlayLabel,homeOverlayStatus,homeCaptureLabel,homeQueueLabel,homeFriendsLabel,homeRecentLabel;
    private android.widget.Button homeOverlayButton;
    private final Runnable refreshHome=new Runnable(){
        @Override public void run(){
            if(!isFinishing()&&!isDestroyed()&&showingUnlocked&&"home".equals(tab)){
                updateHomeIndicators();loadHomeHistory();
                main.postDelayed(this,18_000L);
            }
        }
    };
    private final Handler main=new Handler(Looper.getMainLooper());
    private boolean verifying=false,showingUnlocked=false,advanced=false;
    private boolean foreground=false;
    private static final int NOTIFICATION_PERMISSION_REQUEST=3701;
    private static final int PINNED_FRIEND_PERMISSION_REQUEST=3702;
    private TextView verificationNotice;
    private LinearLayout page,root;
    private String tab="home";
    // Quest Back remembers changes between bottom tabs and swipe navigation.
    // Phones keep their preexisting Android Back behavior.
    private final java.util.ArrayDeque<String> questTabs=new java.util.ArrayDeque<>();
    private void openTab(String next){
        if(tab.equals(next))return;
        if(QuestDevice.isQuestDevice()){
            if(questTabs.size()>=24)questTabs.removeLast();
            questTabs.push(tab);
        }
        tab=next;
        renderUnlocked();
    }
    @Override public void onBackPressed(){
        if(!QuestDevice.isQuestDevice()){
            super.onBackPressed();return;
        }
        if(!showingUnlocked||!StaffGate.unlocked(this)){
            super.onBackPressed();return;
        }
        if(!questTabs.isEmpty()){
            tab=questTabs.pop();
            renderUnlocked();
            return;
        }
        if(!"home".equals(tab)){
            tab="home";
            renderUnlocked();
            return;
        }
        super.onBackPressed();
    }
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
        h.setPadding(dp(14),dp(9),dp(14),dp(9));
        h.setBackgroundColor(CompanionStyle.BG);
        root.addView(h);
        ImageView logo=new ImageView(this);logo.setImageResource(R.drawable.tgc_logo);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        h.addView(logo,new LinearLayout.LayoutParams(dp(40),dp(40)));
        LinearLayout brand=stack();
        brand.setPadding(dp(9),0,0,0);
        TextView over=txt("THE GAMER CLUB",10,CompanionStyle.ACCENT,true);
        over.setLetterSpacing(.13f);brand.addView(over);
        brand.addView(txt("Staff Companion",19,CompanionStyle.TEXT,true));
        h.addView(brand,new LinearLayout.LayoutParams(0,-2,1f));
        if(showingUnlocked){
            TextView status=CompanionStyle.chip(this,"● Staff",CompanionStyle.GOOD,0xFF173B37);
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
        ScrollView scroll;
        if(navigation){
            scroll=new SwipePageScrollView(this,this::swipePage);
        }else{
            scroll=new ScrollView(this);
            scroll.setFillViewport(true);
        }
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1f));
        page=stack();page.setPadding(dp(16),dp(17),dp(16),dp(24));
        scroll.addView(page);
        if(navigation)bottomBar();
        CompanionStyle.safeArea(this,root);
    }
    /**
     * Home ↔ Explore ↔ Tracking ↔ Settings, with bottom tabs always available.
     * Only an intentional horizontal gesture advances; vertical scrolling remains native.
     */
    private void swipePage(int direction){
        if(!StaffGate.unlocked(this))return;
        String[] pages={"home","explore","tracking","settings"};
        int position=0;
        for(int i=0;i<pages.length;i++){
            if(pages[i].equals(tab)){position=i;break;}
        }
        int next=position+direction;
        if(next<0||next>=pages.length)return;
        // Apply after the touch event finishes; replacing the entire View tree
        // during dispatch can cancel taps or trigger vendor-specific touch crashes.
        final String expectedTab=tab,nextTab=pages[next];
        main.post(()->{
            if(isFinishing()||isDestroyed()||!StaffGate.unlocked(this))return;
            if(!tab.equals(expectedTab))return;
            openTab(nextTab);
        });
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
        item.setOnClickListener(v->openTab(id));
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
    /** Quest floating windows are permanently disabled, including old opt-ins. */
    private void enforceQuestOverlaySetting(){
        if(!QuestDevice.isQuestDevice())return;
        OverlayPreferences.setDesired(this,false);
        OverlayPreferences.setRecover(this,false);
        // Stop any overlay surviving an in-place upgrade from an older release.
        stopService(new Intent(this,FloatingCompanionService.class));
        getSharedPreferences("tgc_overlay",0).edit()
            .putBoolean("running",false)
            .putLong("service_heartbeat_at",0L).apply();
    }
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);
        enforceQuestOverlaySetting();
        if(saved!=null){
            tab=saved.getString("tab","home");
            advanced=saved.getBoolean("advanced",false);
            java.util.ArrayList<String> history=saved.getStringArrayList("quest_tab_history");
            if(history!=null && QuestDevice.isQuestDevice())
                for(int i=history.size()-1;i>=0;i--)
                    questTabs.push(history.get(i));
        }
        locked("Checking your staff access...");
        InboxAlertSettings.restore(this);
        PinnedFriendAlerts.restore(this);
    }
    @Override protected void onSaveInstanceState(Bundle out){
        out.putString("tab",tab);out.putBoolean("advanced",advanced);
        if(QuestDevice.isQuestDevice())
            out.putStringArrayList("quest_tab_history",
                new java.util.ArrayList<>(questTabs));
        super.onSaveInstanceState(out);
    }
    @Override protected void onResume(){
        super.onResume();
        enforceQuestOverlaySetting();
        foreground=true;
        main.removeCallbacks(refreshGate);
        revalidate();
        main.postDelayed(refreshGate,90_000L);
        main.removeCallbacks(refreshHome);
        main.postDelayed(refreshHome,2_000L);
    }
    @Override protected void onPause(){
        foreground=false;
        main.removeCallbacks(refreshHome);
        main.removeCallbacks(refreshGate);
        super.onPause();
    }
    @Override protected void onDestroy(){
        main.removeCallbacks(refreshGate);
        main.removeCallbacks(refreshHome);
        gateWorker.shutdownNow();homeWorker.shutdownNow();super.onDestroy();
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
                if(StaffGate.unlocked(this))maybeRestoreFloatingTools();
            });
        });
    }
    /**
     * Work around interrupted overlay processes only while this Activity is in
     * the foreground; never bypass Android's background-start limits.
     * User opt-in and a newly approved staff grant are mandatory.
     */
    private void maybeRestoreFloatingTools(){
        if(!QuestDevice.canRunOverlay(this)||
           !foreground||!OverlayPreferences.recover(this)||
            !OverlayPreferences.desired(this)||!StaffGate.unlocked(this)||
            !android.provider.Settings.canDrawOverlays(this))return;
        android.content.SharedPreferences prefs=getSharedPreferences("tgc_overlay",0);
        long now=System.currentTimeMillis(),heartbeat=prefs.getLong("service_heartbeat_at",0);
        if(prefs.getBoolean("running",false)&&heartbeat>0&&now-heartbeat<35_000L)
            return;
        long last=prefs.getLong("last_foreground_restore_attempt",0);
        if(last>0&&now-last<120_000L)return;
        prefs.edit().putLong("last_foreground_restore_attempt",now).apply();
        try{
            androidx.core.content.ContextCompat.startForegroundService(this,
                new Intent(this,FloatingCompanionService.class)
                    .setAction(FloatingCompanionService.ACTION_RESTORE));
        }catch(Exception ignored){
            // Android may refuse a start when the app moves to background.
        }
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
    private void metric(LinearLayout parent,String name,String initial,String symbol,int color,
                        java.util.function.Consumer<TextView> sink){
        LinearLayout cell=stack();
        cell.setPadding(dp(13),dp(13),dp(12),dp(14));
        cell.setBackground(CompanionStyle.shape(this,CompanionStyle.SURFACE,15,CompanionStyle.BORDER));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(110),1f);
        lp.setMargins(dp(3),0,dp(3),0);parent.addView(cell,lp);
        cell.addView(txt(symbol,16,color,true));
        space(cell,6);
        TextView value=txt(initial,19,CompanionStyle.TEXT,true);
        value.setSingleLine(true);
        value.setEllipsize(android.text.TextUtils.TruncateAt.END);
        cell.addView(value);
        space(cell,3);
        TextView label=txt(name,11,CompanionStyle.MUTED,false);
        label.setMaxLines(2);
        cell.addView(label);
        sink.accept(value);
    }
    /** Compact shortcuts make the core tools visible without scrolling past statistics. */
    private void compactQuickTile(LinearLayout line,String icon,String name,String subtitle,Runnable click){
        LinearLayout tile=stack();
        tile.setPadding(dp(12),dp(10),dp(9),dp(8));
        tile.setBackground(CompanionStyle.shape(this,CompanionStyle.RAISED,14,0));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(90),1f);
        lp.setMargins(dp(3),0,dp(3),0);
        line.addView(tile,lp);
        tile.addView(txt(icon,19,CompanionStyle.ACCENT,false));
        space(tile,5);
        TextView label=txt(name,14,CompanionStyle.TEXT,true);
        label.setSingleLine(true);
        label.setEllipsize(android.text.TextUtils.TruncateAt.END);
        tile.addView(label);
        TextView sub=txt(subtitle,11,CompanionStyle.MUTED,false);
        sub.setSingleLine(true);
        sub.setEllipsize(android.text.TextUtils.TruncateAt.END);
        tile.addView(sub);
        tile.setContentDescription(name+". "+subtitle);
        tile.setOnClickListener(v->click.run());
    }
    private void compactQuickPair(LinearLayout parent,
            String glyphA,String nameA,String descA,Runnable clickA,
            String glyphB,String nameB,String descB,Runnable clickB){
        LinearLayout row=new LinearLayout(this);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);
        p.bottomMargin=dp(7);
        parent.addView(row,p);
        compactQuickTile(row,glyphA,nameA,descA,clickA);
        compactQuickTile(row,glyphB,nameB,descB,clickB);
    }
    private void homeButtons(LinearLayout parent){
        // Each status belongs to the action immediately beneath it.
        // Using paired vertical columns prevents status/button mismatches
        // across different screen sizes and font scaling.
        LinearLayout actions=new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.TOP);
        parent.addView(actions);

        LinearLayout toolsColumn=stack();
        LinearLayout.LayoutParams toolsWidth=new LinearLayout.LayoutParams(0,-2,1.4f);
        toolsWidth.setMargins(0,0,dp(6),0);
        actions.addView(toolsColumn,toolsWidth);
        homeOverlayStatus=txt("○ Tools off",12,CompanionStyle.MUTED,false);
        homeOverlayStatus.setGravity(Gravity.CENTER);
        toolsColumn.addView(homeOverlayStatus);
        space(toolsColumn,7);
        final boolean quest=QuestDevice.isQuestDevice();
        android.widget.Button overlay=CompanionStyle.button(this,
            quest?"Quest Tools":"Show floating tools",true);
        homeOverlayButton=overlay;
        overlay.setTextSize(12);
        toolsColumn.addView(overlay,new LinearLayout.LayoutParams(-1,dp(43)));
        overlay.setOnClickListener(v->{
            if(QuestDevice.isQuestDevice()){
                openQuestTools();
                return;
            }
            toggleOverlay();
            main.postDelayed(this::updateHomeIndicators,650L);
        });

        LinearLayout trackingColumn=stack();
        actions.addView(trackingColumn,new LinearLayout.LayoutParams(0,-2,1f));
        homeNodeLabel=txt("○ Checking tracking…",12,CompanionStyle.GOLD,true);
        homeNodeLabel.setGravity(Gravity.CENTER);
        trackingColumn.addView(homeNodeLabel);
        space(trackingColumn,7);
        android.widget.Button controls=CompanionStyle.button(this,"Tracking",false);
        controls.setTextSize(12);
        trackingColumn.addView(controls,new LinearLayout.LayoutParams(-1,dp(43)));
        controls.setOnClickListener(v->needsStaff(
            ()->startActivity(new Intent(this,MobileDeployActivity.class))));
    }
    private void openQuestTools(){
        needsStaff(()->{
            if(!QuestDevice.isQuestDevice())return;
            startActivity(new Intent(this,QuestToolsActivity.class));
        });
    }
    private void renderHome(){
        // Home has its own compact geometry; no large repeated hero/headline block.
        page.setPadding(dp(14),dp(10),dp(14),dp(20));
        TextView welcome=txt(displayName(),23,CompanionStyle.TEXT,true);
        welcome.setMaxLines(1);
        welcome.setEllipsize(android.text.TextUtils.TruncateAt.END);
        page.addView(welcome);
        space(page,8);

        LinearLayout hero=card();
        hero.setPadding(dp(14),dp(12),dp(14),dp(12));
        hero.setBackground(CompanionStyle.gradient(this,0xFF1A4159,0xFF15283E,17));
        homeButtons(hero);
        // Quest has one primary entry point: the Quest Tools button above.
        LinearLayout shortcutsHeading=new LinearLayout(this);
        shortcutsHeading.setGravity(Gravity.CENTER_VERTICAL);
        page.addView(shortcutsHeading);
        shortcutsHeading.addView(CompanionStyle.section(this,"QUICK ACCESS"),
            new LinearLayout.LayoutParams(0,-2,1f));
        shortcutsHeading.addView(txt("Swipe to Explore  →",11,CompanionStyle.MUTED,false));

        // No large surrounding card: six core shortcuts sit immediately below the compact status bar.
        LinearLayout quick=stack();
        page.addView(quick);
        compactQuickPair(quick,"♧","Friends","Who is online",()->openFriendHub("locations"),
            "◎","Players","List & history",()->openPlayerLog("current"));
        compactQuickPair(quick,"✉","Inbox","Invites & requests",()->openInbox("all"),
            "◉","My profile","Your account",this::openMyProfile);
        compactQuickPair(quick,"◇","Worlds","Your favorites",()->openBrowse("worlds"),
            "★","Avatars","Your collection",this::openAvatars);

        page.addView(CompanionStyle.section(this,"YOUR ACTIVITY"));
        LinearLayout summary=card();
        summary.setPadding(dp(12),dp(12),dp(12),dp(8));
        LinearLayout counts=new LinearLayout(this);
        summary.addView(counts);
        metric(counts,"Players seen","—","◎",CompanionStyle.ACCENT,t->homeCaptureLabel=t);
        metric(counts,"Not sent yet","—","↗",CompanionStyle.GOLD,t->homeQueueLabel=t);
        space(summary,7);
        LinearLayout second=new LinearLayout(this);
        summary.addView(second);
        metric(second,"Friends online*","—","♧",CompanionStyle.ACCENT,t->homeFriendsLabel=t);
        metric(second,QuestDevice.isQuestDevice()?"Quest Tools":"Floating tools",
            QuestDevice.isQuestDevice()?"READY":"—","◉",CompanionStyle.GOOD,
            t->homeOverlayLabel=t);
        TextView freshness=txt("* Based on friends checked in the last five minutes.",
            11,CompanionStyle.MUTED,false);
        summary.addView(freshness);

        LinearLayout recent=card();
        recent.setPadding(dp(12),dp(12),dp(12),dp(8));
        recent.addView(txt("RECENT PLAYERS",11,CompanionStyle.ACCENT,true));
        space(recent,7);
        homeRecentLabel=txt("Looking for recent encounters…",12,CompanionStyle.MUTED,false);
        recent.addView(homeRecentLabel);
        space(recent,7);
        action(recent,"↗","View activity","Friend updates and recent players",
            ()->openFriendHub("activity"));

        page.addView(CompanionStyle.section(this,"MORE"));
        LinearLayout essentials=card();
        action(essentials,"◷","Friend history","Online, offline and world changes",
            ()->openFriendHub("log"));
        action(essentials,"◈","Groups","Members and group instances",
            ()->openBrowse("groups"));
        action(essentials,"♢","Verification code","Automatically finds a TGC or Cult invite code",
            this::openInviteCode);
        action(essentials,"⌕","Search people","Find someone on VRChat",
            ()->openBrowse("search_users"));
        updateHomeIndicators();
        loadHomeHistory();
    }
    private boolean homeVisible(){
        return showingUnlocked&&"home".equals(tab)&&StaffGate.unlocked(this);
    }
    private void updateHomeIndicators(){
        if(!homeVisible())return;
        android.content.SharedPreferences node=getSharedPreferences("tgc_mobile_node_status",0);
        boolean running=node.getBoolean("running",false)&&
            System.currentTimeMillis()-node.getLong("service_heartbeat_at",0L)<90_000L;
        android.content.SharedPreferences overlay=getSharedPreferences("tgc_overlay",0);
        boolean overlayRunning=overlay.getBoolean("running",false)&&
            System.currentTimeMillis()-overlay.getLong("service_heartbeat_at",0L)<35_000L;
        if(homeNodeLabel!=null){
            homeNodeLabel.setText(running?"● Tracking on":"○ Tracking off");
            homeNodeLabel.setTextColor(running?CompanionStyle.GOOD:CompanionStyle.GOLD);
        }
        if(homeCaptureLabel!=null)
            homeCaptureLabel.setText(Integer.toString(node.getInt("captured_total",0)));
        boolean quest=QuestDevice.isQuestDevice();
        if(homeOverlayLabel!=null)
            homeOverlayLabel.setText(quest?"READY":overlayRunning?"ON":"OFF");
        if(homeOverlayStatus!=null)
            homeOverlayStatus.setText(quest?
                "⌘ Quest Tools ready":overlayRunning?"● Tools on":"○ Tools off");
        if(homeOverlayButton!=null)
            homeOverlayButton.setText(quest?"Quest Tools":
                overlayRunning?"Hide floating tools":"Show floating tools");
    }
    private void loadHomeHistory(){
        if(!homeVisible()||homeLoading)return;
        homeLoading=true;
        homeWorker.execute(()->{
            int queue=-1,online=-1;
            String last="No player encounters recorded yet.";
            try(MobileOutbox box=new MobileOutbox(this)){
                queue=box.count();
                org.json.JSONArray events=box.recentTrackedEvents(3);
                StringBuilder names=new StringBuilder();
                for(int i=0;i<events.length();i++){
                    JSONObject e=events.optJSONObject(i);
                    if(e==null)continue;
                    String name=e.optString("display_name","");
                    if(name.isEmpty())name=e.optString("user_id","Unknown player");
                    if(names.length()>0)names.append("  ·  ");
                    names.append(name.length()>30?name.substring(0,29)+"…":name);
                }
                if(names.length()>0)last=names.toString();
            }catch(Exception ignored){}
            try{
                JSONObject session=VRChatSession.load(this);
                JSONObject snap=FriendLogStore.snapshot(this,session);
                if(System.currentTimeMillis()-FriendLogStore.lastPoll(this,session)<5*60_000L){
                    online=0;
                    java.util.Iterator<String> ids=snap.keys();
                    while(ids.hasNext()){
                        JSONObject f=snap.optJSONObject(ids.next());
                        if(f!=null&&f.optBoolean("online",false))online++;
                    }
                }
            }catch(Exception ignored){}
            final int pending=queue,friends=online;
            final String recent=last;
            runOnUiThread(()->{
                homeLoading=false;
                if(!homeVisible()||isFinishing()||isDestroyed())return;
                if(homeQueueLabel!=null)homeQueueLabel.setText(
                    pending<0?"—":Integer.toString(pending));
                if(homeFriendsLabel!=null)homeFriendsLabel.setText(
                    friends<0?"—":Integer.toString(friends));
                if(homeRecentLabel!=null)homeRecentLabel.setText(recent);
            });
        });
    }
    private void renderExplore(){
        headline("EXPLORE","Your VRChat","Everything you want to discover, without leaving the app.");
        // Quest Tools is launched from the Home status bar; no duplicate
        // launcher on Explore.
        page.addView(CompanionStyle.section(this,"MESSAGES & INVITES"));
        LinearLayout messages=card();
        action(messages,"✉","Notifications","All incoming VRChat notifications",()->openInbox("all"));
        action(messages,"➜","World invites","Received instance invitations",()->openInbox("invite"));
        action(messages,"◉","Friend requests","See and respond to friend requests",()->openInbox("friend"));
        page.addView(CompanionStyle.section(this,"PEOPLE"));
        LinearLayout people=card();
        action(people,"♧","Online friends","See who's around now",()->openBrowse("friends_online"));
        action(people,"◌","Offline friends","Find friends who aren't online",()->openBrowse("friends_offline"));
        action(people,"⌕","Find people","Search VRChat users",()->openBrowse("search_users"));
        action(people,"◎","Observed player list","Players seen in your current session",
            ()->openPlayerLog("current"));
        action(people,"◷","Join & leave history","Locally saved VRChat Mobile log events",
            ()->openPlayerLog("timeline"));
        action(people,"◎","Recent players","People you've encountered",()->openBrowse("encounters"));
        action(people,"♧","Friends Locations","Friends grouped by their visible instances",()->openFriendHub("locations"));
        action(people,"◷","Friend Log","Search recorded changes over time",()->openFriendHub("log"));
        action(people,"↗","Activity Feed","Observed activity while monitoring",()->openFriendHub("activity"));
        action(people,"✎","Private VRChat notes","Notes saved to your VRChat account",this::openNotes);
        page.addView(CompanionStyle.section(this,"PLACES & COMMUNITY"));
        LinearLayout places=card();
        action(places,"★","Bookmarked worlds","Your personal world shortcuts",()->openBrowse("bookmarks"));
        action(places,"◇","Favorite worlds","Your saved places",()->openBrowse("worlds"));
        action(places,"↺","Recently visited","Worlds you've explored",()->openBrowse("recent"));
        action(places,"⌕","Find worlds","Search for something new",()->openBrowse("search_worlds"));
        action(places,"◈","My groups","Your VRChat communities",()->openBrowse("groups"));
        action(places,"◉","Favorite avatars","View saved avatars and switch your avatar",this::openAvatars);
        action(places,"★","Extended avatar favorites","Private categorized avatar collections • VRC+",this::openExtendedAvatars);
    }
    private void renderTracking(){
        headline("ACTIVITY","Player tracking",QuestDevice.isQuestDevice()?
            "See who joins and leaves while playing VRChat on your Quest.":
            "See who joins and leaves while playing VRChat Mobile.");
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
        action(stats,"◷","Join & leave history","Times, players and instance changes",
            ()->openPlayerLog("timeline"));
        action(stats,"◎","Observed player list","People detected in this session",
            ()->openPlayerLog("current"));
        action(stats,"⚙","Tracking controls","Start, stop or check your tracking",
            ()->needsStaff(()->startActivity(new Intent(this,MobileDeployActivity.class))));
        // One-time VRChat Full logging instructions now live in the tracking
        // onboarding guide instead of occupying the daily Tracking dashboard.
        page.addView(CompanionStyle.section(this,"HOW TRACKING WORKS"));
        LinearLayout explanation=card();
        boolean questTracking=QuestDevice.isQuestDevice();
        explanation.addView(txt(questTracking?
            "Quest: folder permission, no Shizuku required.":
            "Phone: Shizuku required for player tracking.",
            16,CompanionStyle.TEXT,true));
        space(explanation,7);
        explanation.addView(txt(questTracking?
            "In Tracking controls, grant read-only access to Documents/Logs and start tracking before joining an instance. No Shizuku installation is needed for the default Quest method.":
            "On Android phones, player tracking reads protected VRChat Mobile logs through Shizuku. Other Companion features work without Shizuku.",
            13,CompanionStyle.MUTED,false));
        space(explanation,8);
        explanation.addView(txt(
            "Join sightings can help the TGC network; complete raw VRChat logs are never uploaded.",
            13,CompanionStyle.MUTED,false));
        if(questTracking){
            space(explanation,8);
            explanation.addView(txt(
                "If folder access fails, open Tracking controls → Advanced tools → Alternative Quest method to enable the Shizuku fallback.",
                12,CompanionStyle.ACCENT,false));
        }
    }
    private void renderSettings(){
        headline("PREFERENCES","Settings","Your account, device and Companion.");
        page.addView(CompanionStyle.section(this,"YOUR ACCOUNT"));
        LinearLayout account=card();
        action(account,"◉","My VRChat account","Profile, sign-in and sign-out",
            ()->needsStaff(()->startActivity(new Intent(this,VRChatAccountActivity.class))));
        action(account,"♜","Staff access","View your linked device",
            ()->needsStaff(()->startActivity(new Intent(this,MobileDeployActivity.class))));
        action(account,"♢","Verification code",
            "Find and copy your TGC or Cult invite code",this::openInviteCode);
        // Inbox is already on Home and Explore. Avoid a third identical link.
        // Avatar collections and private notes are already in Explore. Keep
        // Settings focused on account configuration instead of duplicate links.
        action(account,"☼","Status & presets","Edit your status and saved presets",this::openStatus);
        page.addView(CompanionStyle.section(this,"VRCHAT NOTIFICATIONS"));
        LinearLayout inboxSettings=card();
        boolean optedIn=InboxAlertSettings.enabled(this);
        boolean allowed=InboxAlertSettings.permissionGranted(this);
        inboxSettings.addView(txt(optedIn?
            allowed?"● Inbox alerts enabled":"○ Inbox alerts need Android permission":
            "○ Inbox alerts are off",15,
            optedIn&&allowed?CompanionStyle.GOOD:CompanionStyle.GOLD,true));
        space(inboxSettings,7);
        inboxSettings.addView(txt(
            "Optional alerts for new VRChat invites and friend requests. "+
            "Checks about every 15 minutes while connected; Android may delay updates "+
            "to save battery. No Shizuku or mobile VRChat installation required.",
            12,CompanionStyle.MUTED,false));
        space(inboxSettings,8);
        action(inboxSettings,"✉",optedIn?"Turn off inbox alerts":"Enable inbox alerts",
            optedIn?"Stop background checks and alerts":
            "Enable notification permission and background checks",
            this::toggleInboxAlerts);
        if(optedIn&&!allowed)
            action(inboxSettings,"↗","Allow Android notifications",
                "Grant notification access for new invites and requests",
                this::requestInboxNotificationPermission);
        page.addView(CompanionStyle.section(this,"PINNED FRIEND ONLINE ALERTS"));
        LinearLayout friendAlertSettings=card();
        boolean friendOpted=PinnedFriendAlerts.enabled(this);
        boolean friendAllowed=PinnedFriendAlerts.permission(this);
        friendAlertSettings.addView(txt(friendOpted?
            (friendAllowed?"● Pinned friend alerts enabled":
                "○ Allow Android notifications"):
            "○ Pinned friend alerts are off",
            15,friendOpted&&friendAllowed?
                CompanionStyle.GOOD:CompanionStyle.GOLD,true));
        space(friendAlertSettings,7);
        friendAlertSettings.addView(txt(
            "Get an Android notification when a pinned VRChat friend "+
            "changes from offline to online. Only selected pins trigger alerts. "+
            "The first complete friend check is a silent baseline. "+
            "Checks happen every 2.5–5 minutes while the floating tools run, "+
            "or approximately every 15 minutes in the background; Android may delay them.",
            12,CompanionStyle.MUTED,false));
        space(friendAlertSettings,8);
        action(friendAlertSettings,"♧",
            friendOpted?"Disable pinned friend alerts":"Enable pinned friend alerts",
            "Separate from invitation and inbox notifications",
            this::togglePinnedFriendAlerts);
        if(friendOpted&&!friendAllowed)
            action(friendAlertSettings,"↗","Allow Android notifications",
                "Grant permission for pinned friend online alerts",
                this::requestPinnedFriendNotificationPermission);
        boolean questSystem=QuestDevice.isQuestDevice();
        page.addView(CompanionStyle.section(this,
            questSystem?"QUEST TOOLS":"FLOATING TOOLS"));
        LinearLayout floating=card();
        if(questSystem){
            floating.addView(txt("Quest Tools replaces floating tools",16,
                CompanionStyle.ACCENT,true));
            space(floating,7);
            floating.addView(txt(
                "Open Quest Tools directly from the Home screen. It provides "+
                "native VRChat tools without a floating overlay. Floating "+
                "windows and experimental floater recovery are permanently "+
                "disabled on Quest. Your tracking Node runs independently.",
                12,CompanionStyle.MUTED,false));
        }else{
        action(floating,"◉","Floating tools",
            "Keep your tools available while playing VRChat",this::toggleOverlay);
        floating.addView(txt("Turning this off won't stop player tracking.",
            12,CompanionStyle.MUTED,false));
        space(floating,10);
        action(floating,"◫","Floating panel size",
            "Current: "+("compact".equals(OverlayPreferences.size(this))?"Compact":
                "expanded".equals(OverlayPreferences.size(this))?"Expanded":"Standard"),
            this::chooseOverlaySize);
        action(floating,"↔","Snap bubble to edge",
            OverlayPreferences.snap(this)?"Enabled":"Disabled",
            ()->{
                OverlayPreferences.setSnap(this,!OverlayPreferences.snap(this));
                renderUnlocked();
            });
        action(floating,"↻","Background recovery",
            OverlayPreferences.recover(this)?
                "On: best-effort recovery after Android interrupts the floater":
                "Off: the floater stays closed if Android stops it",
            this::toggleOverlayRecovery);
        floating.addView(txt(
            "Recovery is optional. It can restart the floating tools only when "+
            "Android permits it and staff access can be reverified. It doesn't "+
            "force-start after an Android force-stop or bypass battery restrictions.",
            12,CompanionStyle.MUTED,false));
        }
        page.addView(CompanionStyle.section(this,"BACKGROUND & BATTERY"));
        LinearLayout background=card();
        action(background,"◉","Background health",
            questSystem?"Tracking and inbox alerts at a glance":
                "Tracking, floating tools and inbox alerts at a glance",
            this::showBackgroundHealth);
        background.addView(txt(questSystem?
            "Android power settings can interrupt tracking or delay alerts. "+
            "Quest Tools is a normal app screen, not an overlay, and doesn't "+
            "run an extra floating service.":
            "Android power settings can delay alerts or interrupt background "+
            "services. Tracking and floating tools run independently; you can "+
            "review the app's battery settings without disabling Android protections.",
            12,CompanionStyle.MUTED,false));
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
    private void togglePinnedFriendAlerts(){
        if(!StaffGate.unlocked(this)){revalidate();return;}
        if(PinnedFriendAlerts.enabled(this)){
            PinnedFriendAlerts.setEnabled(this,false);
            Toast.makeText(this,"Pinned friend alerts disabled",
                Toast.LENGTH_SHORT).show();
            renderUnlocked();return;
        }
        new android.app.AlertDialog.Builder(this)
            .setTitle("Notify when pinned friends come online?")
            .setMessage("Only friends you've pinned will generate online alerts. "+
                "The first successful check is silent to avoid notifying for "+
                "everyone already online. When the bubble is running, checks "+
                "are usually every 2.5–5 minutes; background checks are about "+
                "every 15 minutes and may be delayed by Android.")
            .setNegativeButton("Cancel",null)
            .setPositiveButton("Enable",(d,which)->{
                if(!PinnedFriendAlerts.permission(this))
                    requestPinnedFriendNotificationPermission();
                else{
                    try{
                        PinnedFriendAlerts.setEnabled(this,true);
                        Toast.makeText(this,"Pinned friend alerts enabled",
                            Toast.LENGTH_SHORT).show();
                        renderUnlocked();
                    }catch(Exception e){
                        Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show();
                    }
                }
            }).show();
    }
    private void requestPinnedFriendNotificationPermission(){
        if(android.os.Build.VERSION.SDK_INT>=33 &&
           !PinnedFriendAlerts.permission(this))
            requestPermissions(new String[]{
                android.Manifest.permission.POST_NOTIFICATIONS},
                PINNED_FRIEND_PERMISSION_REQUEST);
        else{
            PinnedFriendAlerts.setEnabled(this,true);
            renderUnlocked();
        }
    }
    private void toggleInboxAlerts(){
        if(!StaffGate.unlocked(this)){revalidate();return;}
        if(InboxAlertSettings.enabled(this)){
            InboxAlertSettings.setEnabled(this,false);
            Toast.makeText(this,"VRChat inbox alerts turned off",Toast.LENGTH_SHORT).show();
            renderUnlocked();return;
        }
        new android.app.AlertDialog.Builder(this)
            .setTitle("Enable VRChat inbox alerts?")
            .setMessage("The Companion can check your personal VRChat invites and requests "+
                "about every 15 minutes in the background. "+
                "Android may delay checks to protect battery life. "+
                "Notification text won't reveal invite messages or send information to TGC.")
            .setNegativeButton("Not now",null)
            .setPositiveButton("Enable",(dialog,which)->{
                if(!InboxAlertSettings.permissionGranted(this)){
                    requestInboxNotificationPermission();
                }else{
                    InboxAlertSettings.setEnabled(this,true);
                    Toast.makeText(this,"Inbox alerts enabled",Toast.LENGTH_SHORT).show();
                    renderUnlocked();
                }
            }).show();
    }
    private void requestInboxNotificationPermission(){
        if(android.os.Build.VERSION.SDK_INT>=33 &&
            !InboxAlertSettings.permissionGranted(this)){
            requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS},
                NOTIFICATION_PERMISSION_REQUEST);
        }else{
            InboxAlertSettings.setEnabled(this,true);
            Toast.makeText(this,"Inbox alerts enabled",Toast.LENGTH_SHORT).show();
            renderUnlocked();
        }
    }
    @Override public void onRequestPermissionsResult(
        int requestCode,String[] permissions,int[] grantResults){
        super.onRequestPermissionsResult(requestCode,permissions,grantResults);
        if(requestCode==PINNED_FRIEND_PERMISSION_REQUEST){
            if(PinnedFriendAlerts.permission(this)){
                try{
                    PinnedFriendAlerts.setEnabled(this,true);
                    Toast.makeText(this,"Pinned friend alerts enabled",
                        Toast.LENGTH_SHORT).show();
                }catch(Exception e){
                    Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show();
                }
            }else Toast.makeText(this,
                "Notification permission was not granted",Toast.LENGTH_LONG).show();
            if(StaffGate.unlocked(this))renderUnlocked();
            return;
        }
        if(requestCode!=NOTIFICATION_PERMISSION_REQUEST)return;
        if(InboxAlertSettings.permissionGranted(this)){
            InboxAlertSettings.setEnabled(this,true);
            Toast.makeText(this,"Inbox alerts enabled",Toast.LENGTH_SHORT).show();
        }else{
            Toast.makeText(this,"Notification permission was not granted",Toast.LENGTH_LONG).show();
        }
        if(StaffGate.unlocked(this))renderUnlocked();
    }
    private void showBackgroundHealth(){
        if(!StaffGate.unlocked(this)){revalidate();return;}
        long now=System.currentTimeMillis();
        android.content.SharedPreferences node=
            getSharedPreferences("tgc_mobile_node_status",0);
        android.content.SharedPreferences overlay=
            getSharedPreferences("tgc_overlay",0);
        long nodeHeartbeat=node.getLong("service_heartbeat_at",0L);
        long overlayHeartbeat=overlay.getLong("service_heartbeat_at",0L);
        boolean nodeLive=node.getBoolean("running",false)&&nodeHeartbeat>0&&
            now-nodeHeartbeat<90_000L;
        boolean overlayLive=overlay.getBoolean("running",false)&&
            overlayHeartbeat>0&&now-overlayHeartbeat<35_000L;
        boolean alerts=InboxAlertSettings.active(this);
        String battery="Managed by Android";
        try{
            android.os.PowerManager pm=
                (android.os.PowerManager)getSystemService(POWER_SERVICE);
            if(pm!=null&&android.os.Build.VERSION.SDK_INT>=23){
                battery=pm.isIgnoringBatteryOptimizations(getPackageName())?
                    "Battery optimization unrestricted":"Battery optimization may limit background work";
            }
        }catch(Exception ignored){}
        boolean quest=QuestDevice.isQuestDevice();
        String details=
            "Player tracking: "+(nodeLive?"Running":"Not currently running")+
            (quest?"\nQuest Tools: available from Home (no floating window)":
              "\nFloating tools: "+(overlayLive?"Running":"Not currently running")+
              "\nAutomatic floating recovery: "+
                (OverlayPreferences.recover(this)?"Enabled":"Off"))+
            "\nInbox alerts: "+(alerts?"Enabled":
                InboxAlertSettings.enabled(this)?"Android permission needed":"Off")+
            "\nBattery status: "+battery+
            (quest?
              "\n\nQuest Tools does not run a floating overlay. Android may "+
              "delay background notifications or interrupt the independent tracking Node.":
              "\n\nAndroid may postpone scheduled inbox checks or stop services under "+
              "memory and battery pressure. The Node's ongoing tracking and the "+
              "floating window are independent. Auto-recovery is best effort and "+
              "cannot bypass a force-stop or Android's background-start restrictions.");
        new android.app.AlertDialog.Builder(this)
            .setTitle("Background health")
            .setMessage(details)
            .setNegativeButton("Close",null)
            .setPositiveButton("Android app settings",(dialog,which)->{
                try{
                    Intent open=new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        android.net.Uri.parse("package:"+getPackageName()));
                    startActivity(open);
                }catch(Exception e){
                    Toast.makeText(this,"Open Android Settings → Apps → TGC Staff Companion",
                        Toast.LENGTH_LONG).show();
                }
            }).show();
    }
    private void chooseOverlaySize(){
        if(!StaffGate.unlocked(this))return;
        String[] labels={"Compact","Standard","Expanded"};
        String[] values={"compact","standard","expanded"};
        int selected=0;
        for(int i=0;i<values.length;i++){
            if(values[i].equals(OverlayPreferences.size(this)))selected=i;
        }
        new android.app.AlertDialog.Builder(this)
            .setTitle("Floating panel size")
            .setSingleChoiceItems(labels,selected,(dialog,which)->{
                OverlayPreferences.setSize(this,values[which]);
                dialog.dismiss();
                Toast.makeText(this,"Size saved. Reopen the floating panel to apply.",
                    Toast.LENGTH_SHORT).show();
                renderUnlocked();
            })
            .setNegativeButton("Cancel",null).show();
    }
    private void toggleOverlayRecovery(){
        if(!StaffGate.unlocked(this)||!QuestDevice.canRunOverlay(this))return;
        boolean enable=!OverlayPreferences.recover(this);
        if(!enable){
            OverlayPreferences.setRecover(this,false);
            updateOverlayRecoveryMode();
            renderUnlocked();return;
        }
        new android.app.AlertDialog.Builder(this)
            .setTitle("Allow floating tools to recover?")
            .setMessage("If Android interrupts the floating service, the Companion "+
                "may try to restore it after rechecking your staff access online. "+
                "Android can still delay or block background service starts, and "+
                "a force-stopped app will not restart automatically. "+
                "The player-tracking Node works independently.")
            .setNegativeButton("Not now",null)
            .setPositiveButton("Enable",(d,w)->{
                OverlayPreferences.setRecover(this,true);
                updateOverlayRecoveryMode();renderUnlocked();
            }).show();
    }
    private void updateOverlayRecoveryMode(){
        if(!QuestDevice.canRunOverlay(this))return;
        android.content.SharedPreferences prefs=getSharedPreferences("tgc_overlay",0);
        boolean live=prefs.getBoolean("running",false)&&
            System.currentTimeMillis()-prefs.getLong("service_heartbeat_at",0L)<35_000L;
        if(live){
            try{startService(new Intent(this,FloatingCompanionService.class)
                .setAction(FloatingCompanionService.ACTION_START));}
            catch(Exception ignored){}
        }
    }
    private void openPlayerLog(String tab){
        needsStaff(()->startActivity(new Intent(this,PlayerActivityLogActivity.class)
            .putExtra("tab",tab)));
    }
    private void openFriendHub(String tab){
        needsStaff(()->startActivity(new Intent(this,FriendHubActivity.class).putExtra("tab",tab)));
    }
    private void toggleOverlay(){
        needsStaff(()->{
            if(!QuestDevice.canRunOverlay(this)){
                openQuestTools();
                return;
            }
            android.content.SharedPreferences overlayState=getSharedPreferences("tgc_overlay",0);
            boolean overlayActive=overlayState.getBoolean("running",false) &&
                System.currentTimeMillis()-overlayState.getLong("service_heartbeat_at",0L)<35_000L;
            if(overlayActive){
                try{
                    startService(new Intent(this,FloatingCompanionService.class)
                        .setAction(FloatingCompanionService.ACTION_STOP));
                }catch(Exception ignored){
                    OverlayPreferences.setDesired(this,false);
                    stopService(new Intent(this,FloatingCompanionService.class));
                }
                Toast.makeText(this,"Floating tools hidden",Toast.LENGTH_SHORT).show();
                return;
            }
            if(!android.provider.Settings.canDrawOverlays(this)){
                new android.app.AlertDialog.Builder(this)
                    .setTitle("Allow floating tools")
                    .setMessage("Allow floating tools to appear over VRChat. Return here afterward to turn them on.")
                    .setNegativeButton("Cancel",null)
                    .setPositiveButton("Open settings",(dialog,which)->{
                        Intent allow=new Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            android.net.Uri.parse("package:"+getPackageName()));
                        startActivity(allow);
                    }).show();
                return;
            }
            try{
                androidx.core.content.ContextCompat.startForegroundService(this,
                    new Intent(this,FloatingCompanionService.class)
                        .setAction(FloatingCompanionService.ACTION_START));
                Toast.makeText(this,"Floating tools enabled",Toast.LENGTH_SHORT).show();
            }catch(Exception e){
                Toast.makeText(this,"Could not start overlay: "+e.getMessage(),Toast.LENGTH_LONG).show();
            }
        });
    }
    private void openBrowse(String mode){
        needsStaff(()->startActivity(new Intent(this,VRChatBrowseActivity.class).putExtra("mode",mode)));
    }
    private void openInbox(String filter){
        needsStaff(()->startActivity(new Intent(this,VRChatInboxActivity.class)
            .putExtra("filter",filter)));
    }
    private void openExtendedAvatars(){
        needsStaff(()->startActivity(new Intent(this,ExtendedAvatarActivity.class)));
    }
    private void openStatus(){
        needsStaff(()->startActivity(new Intent(this,PresenceActivity.class)));
    }
    private void openAvatars(){
        needsStaff(()->startActivity(new Intent(this,VRChatAvatarActivity.class)));
    }
    private void openNotes(){
        needsStaff(()->startActivity(new Intent(this,VRChatNotesActivity.class)));
    }
    private void openInviteCode(){
        needsStaff(()->startActivity(new Intent(this,InviteCodeActivity.class)));
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
