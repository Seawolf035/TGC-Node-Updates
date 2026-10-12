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
            tab=nextTab;
            renderUnlocked();
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
        main.removeCallbacks(refreshHome);
        main.postDelayed(refreshHome,2_000L);
    }
    @Override protected void onPause(){
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
        android.widget.Button overlay=CompanionStyle.button(this,"Show floating tools",true);
        homeOverlayButton=overlay;
        overlay.setTextSize(12);
        toolsColumn.addView(overlay,new LinearLayout.LayoutParams(-1,dp(43)));
        overlay.setOnClickListener(v->{
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
        metric(second,"Floating tools","—","◉",CompanionStyle.GOOD,t->homeOverlayLabel=t);
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
        if(homeOverlayLabel!=null)
            homeOverlayLabel.setText(overlayRunning?"ON":"OFF");
        if(homeOverlayStatus!=null)
            homeOverlayStatus.setText(overlayRunning?
                "● Tools on":"○ Tools off");
        if(homeOverlayButton!=null)
            homeOverlayButton.setText(overlayRunning?"Hide floating tools":"Show floating tools");
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
        action(places,"◇","Favorite worlds","Your saved places",()->openBrowse("worlds"));
        action(places,"↺","Recently visited","Worlds you've explored",()->openBrowse("recent"));
        action(places,"⌕","Find worlds","Search for something new",()->openBrowse("search_worlds"));
        action(places,"◈","My groups","Your VRChat communities",()->openBrowse("groups"));
        action(places,"◉","Favorite avatars","View saved avatars and switch your avatar",this::openAvatars);
        action(places,"★","Extended avatar favorites","Private categorized avatar collections • VRC+",this::openExtendedAvatars);
    }
    private void renderTracking(){
        headline("ACTIVITY","Player tracking","See who joins and leaves while playing VRChat Mobile.");
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
        page.addView(CompanionStyle.section(this,"HOW TRACKING WORKS"));
        LinearLayout explanation=card();
        explanation.addView(txt("Shizuku is optional.",16,CompanionStyle.TEXT,true));
        space(explanation,7);
        explanation.addView(txt(
            "The Companion works without Shizuku for friends, profiles, notifications, worlds and avatars.",
            13,CompanionStyle.MUTED,false));
        space(explanation,8);
        explanation.addView(txt(
            "If you play VRChat on your phone, Shizuku only lets the Mobile Node read the game's log files. Those logs also give you a player list, join/leave history and world changes in this app. Join sightings can help the TGC network; your full logs stay on your phone.",
            13,CompanionStyle.MUTED,false));
        space(explanation,8);
        explanation.addView(txt(
            "Quest support is being explored, but isn't available yet.",
            12,CompanionStyle.ACCENT,false));
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
        action(account,"✉","Your inbox","Notifications and friend requests",()->openInbox("all"));
        action(account,"◉","Favorite avatars","Browse avatars saved to your VRChat account",this::openAvatars);
        action(account,"✎","Your VRChat notes","View and edit private notes",this::openNotes);
        action(account,"☼","Status & presets","Edit your status and saved presets",this::openStatus);
        action(account,"★","Extended avatar favorites","VRC+-only local avatar collections",this::openExtendedAvatars);
        page.addView(CompanionStyle.section(this,"FLOATING TOOLS"));
        LinearLayout floating=card();
        action(floating,"◉","Floating tools",
            "Keep your tools available while playing VRChat",this::toggleOverlay);
        floating.addView(txt("Turning this off won't stop player tracking.",
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
    private void openPlayerLog(String tab){
        needsStaff(()->startActivity(new Intent(this,PlayerActivityLogActivity.class)
            .putExtra("tab",tab)));
    }
    private void openFriendHub(String tab){
        needsStaff(()->startActivity(new Intent(this,FriendHubActivity.class).putExtra("tab",tab)));
    }
    private void toggleOverlay(){
        needsStaff(()->{
            android.content.SharedPreferences overlayState=getSharedPreferences("tgc_overlay",0);
            boolean overlayActive=overlayState.getBoolean("running",false) &&
                System.currentTimeMillis()-overlayState.getLong("service_heartbeat_at",0L)<35_000L;
            if(overlayActive){
                stopService(new Intent(this,FloatingCompanionService.class));
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
