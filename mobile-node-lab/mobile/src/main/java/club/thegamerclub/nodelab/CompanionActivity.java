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
import android.net.Uri;
import android.content.pm.PackageManager;
import rikka.shizuku.Shizuku;
import org.json.JSONObject;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Staff-exclusive, native VRChat Companion dashboard.
 * Deployment access is still checked by StaffGate; user-facing labels omit
 * private recruitment implementation and developer-oriented terminology.
 */
public final class CompanionActivity extends Activity implements QuestBackHost {
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
    private boolean trackingGuideExpanded=false;
    private boolean trackingInfoExpanded=false;
    private boolean questShizukuExpanded=false;
    private static final int TRACKING_FOLDER_REQUEST=7152;
    private static final int TRACKING_SHIZUKU_REQUEST=7217;
    private String exploreCategory="friends";
    // Long settings groups stay collapsed, while the primary account group
    // opens by default. The current set survives tab redraws.
    private final java.util.Set<String> settingsExpanded=
        new java.util.HashSet<>(java.util.Arrays.asList("account"));
    private StaffDexPanel staffDexPanel;
    private boolean setupLaunched=false;
    private boolean setupAfterLink=false;
    // Back/Finish Later must NOT strand a verified staff member on
    // "Checking your staff access..." with a stale setupLaunched flag.
    private boolean setupDeferred=false;
    private static final int GUIDED_SETUP_REQUEST=7159;
    private boolean licenseLaunched=false;
    private boolean foreground=false;
    private boolean whatsNewLaunched=false;
    private static final int NOTIFICATION_PERMISSION_REQUEST=3701;
    private static final int PINNED_FRIEND_PERMISSION_REQUEST=3702;
    private static final int UPDATE_ALERT_PERMISSION_REQUEST=3703;
    private TextView verificationNotice;
    private LinearLayout page,root;
    private String tab="home";
    private final Shizuku.OnRequestPermissionResultListener trackingPermissionListener=
        (code,result)->{
            if(code!=TRACKING_SHIZUKU_REQUEST)return;
            runOnUiThread(()->{
                if(isFinishing()||isDestroyed())return;
                Toast.makeText(this,result==PackageManager.PERMISSION_GRANTED?
                    "Shizuku permission granted. Start tracking when ready.":
                    "Shizuku permission denied. Tracking remains off.",
                    Toast.LENGTH_LONG).show();
                if("tracking".equals(tab)&&StaffGate.unlocked(this))renderUnlocked();
            });
        };

    @Override public boolean showQuestBack(){
        return showingUnlocked && !"home".equals(tab) && StaffGate.unlocked(this);
    }
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
        QuestBackApplication.refresh(this);
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
            TextView status=CompanionStyle.chip(this,StaffGate.delegated(this)?
                "● Authorized":"● Staff",CompanionStyle.GOOD,0xFF173B37);
            h.addView(status);
        }
    }
    private void shell(boolean navigation){
        if(staffDexPanel!=null){staffDexPanel.dispose();staffDexPanel=null;}
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
     * Home ↔ Explore ↔ StaffDex ↔ Tracking ↔ Settings, with bottom tabs always available.
     * Only an intentional horizontal gesture advances; vertical scrolling remains native.
     */
    private void swipePage(int direction){
        if(!StaffGate.unlocked(this))return;
        String[] pages={"home","explore","staffdex","tracking","settings"};
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
        addTab(nav,"♛","StaffDex","staffdex");
        addTab(nav,"◉","Tracking","tracking");
        addTab(nav,"⚙","Settings","settings");
    }
    private void addTab(LinearLayout nav,String glyph,String name,String id){
        LinearLayout item=stack();item.setGravity(Gravity.CENTER);
        boolean selected=tab.equals(id);
        item.setBackground(CompanionStyle.shape(this,selected?CompanionStyle.RAISED:CompanionStyle.SURFACE,13,
          selected?CompanionStyle.BORDER:0));
        nav.addView(item,new LinearLayout.LayoutParams(0,-1,1f));
        TextView symbol=txt(glyph,20,selected?CompanionStyle.ICON:CompanionStyle.MUTED,false);
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
        boolean compactSettings="settings".equals(tab);
        row.setPadding(dp(compactSettings?10:14),dp(compactSettings?8:15),
            dp(compactSettings?10:12),dp(compactSettings?8:15));
        row.setBackground(CompanionStyle.shape(this,CompanionStyle.RAISED,14,0));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
        lp.bottomMargin=dp(compactSettings?5:9);parent.addView(row,lp);
        TextView icon=txt(glyph,compactSettings?17:22,CompanionStyle.ICON,true);
        icon.setGravity(Gravity.CENTER);
        icon.setBackground(CompanionStyle.shape(this,0xFF193D54,11,0));
        row.addView(icon,new LinearLayout.LayoutParams(dp(compactSettings?34:43),
            dp(compactSettings?34:43)));
        LinearLayout middle=stack();middle.setPadding(dp(13),0,dp(9),0);
        row.addView(middle,new LinearLayout.LayoutParams(0,-2,1f));
        middle.addView(txt(name,compactSettings?14:15,CompanionStyle.TEXT,true));
        if(description!=null&&!description.isEmpty()){
            space(middle,compactSettings?2:4);
            TextView detail=txt(description,compactSettings?11:12,CompanionStyle.MUTED,false);
            if(compactSettings){detail.setMaxLines(2);detail.setEllipsize(android.text.TextUtils.TruncateAt.END);}
            middle.addView(detail);
        }
        row.addView(txt("›",compactSettings?22:29,CompanionStyle.MUTED,false));
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
        tile.addView(txt(glyph,24,CompanionStyle.ICON,false));
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
        TrackingSetupGuide.initialize(this);
        Shizuku.addRequestPermissionResultListener(trackingPermissionListener);
        if(saved!=null){
            tab=saved.getString("tab","home");
            advanced=saved.getBoolean("advanced",false);
            trackingGuideExpanded=saved.getBoolean("tracking_guide_expanded",false);
            trackingInfoExpanded=saved.getBoolean("tracking_info_expanded",false);
            questShizukuExpanded=saved.getBoolean("quest_shizuku_expanded",false);
            setupDeferred=saved.getBoolean("setup_deferred",false);
            String savedCategory=saved.getString("explore_category","friends");
            if(isExploreCategory(savedCategory))exploreCategory=savedCategory;
            java.util.ArrayList<String> history=saved.getStringArrayList("quest_tab_history");
            if(history!=null && QuestDevice.isQuestDevice())
                for(int i=history.size()-1;i>=0;i--)
                    questTabs.push(history.get(i));
        }
        setupAfterLink=(saved!=null&&saved.getBoolean("setup_after_link",false))||
            getIntent().getBooleanExtra("open_setup_after_link",false);
        if(setupAfterLink){tab="home";setupDeferred=false;}
        if(getIntent().getBooleanExtra("open_main_menu",false)){
            tab="home";
            questTabs.clear();
        }else if(getIntent().getBooleanExtra("open_tracking_setup",false)){
            tab="tracking";
            trackingGuideExpanded=true;
        }else if(getIntent().getBooleanExtra("open_tracking_tab",false)){
            tab="tracking";
        }
        locked("Checking your staff access...");
        InboxAlertSettings.restore(this);
        CompanionUpdateAlerts.restore(this);
        CompanionUpdateAlerts.checkOnOpen(this);
        PinnedFriendAlerts.restore(this);
        AutoInviteRequests.restore(this);
    }
    @Override protected void onNewIntent(Intent intent){
        super.onNewIntent(intent);
        setIntent(intent);
        if(intent!=null && intent.getBooleanExtra("open_setup_after_link",false)){
            // A completed deployment must enter onboarding, never the
            // controls screen. Wait for live staff verification onResume.
            setupAfterLink=true;
            setupLaunched=false;
            setupDeferred=false;
            tab="home";
            questTabs.clear();
        }
        if(intent!=null && intent.getBooleanExtra("open_tracking_tab",false)){
            tab="tracking";
            if(showingUnlocked && StaffGate.unlocked(this))renderUnlocked();
        }
        if(intent!=null && intent.getBooleanExtra("open_main_menu",false)){
            // Quest Tools' explicit Main Menu always returns to Home, even
            // when Companion was previously paused on Settings or StaffDex.
            tab="home";
            questTabs.clear();
            if(showingUnlocked && StaffGate.unlocked(this)){
                renderUnlocked();
                QuestBackApplication.refresh(this);
            }
        }
    }
    @Override protected void onSaveInstanceState(Bundle out){
        out.putString("tab",tab);out.putBoolean("advanced",advanced);
        out.putBoolean("setup_after_link",setupAfterLink);
        out.putBoolean("setup_deferred",setupDeferred);
        out.putBoolean("tracking_guide_expanded",trackingGuideExpanded);
        out.putBoolean("tracking_info_expanded",trackingInfoExpanded);
        out.putBoolean("quest_shizuku_expanded",questShizukuExpanded);
        out.putString("explore_category",exploreCategory);
        if(QuestDevice.isQuestDevice())
            out.putStringArrayList("quest_tab_history",
                new java.util.ArrayList<>(questTabs));
        super.onSaveInstanceState(out);
    }
    @Override protected void onResume(){
        super.onResume();
        enforceQuestOverlaySetting();
        foreground=true;
        if(showingUnlocked&&StaffGate.unlocked(this)){
            ThemePreferences.apply(this);
            renderUnlocked();
        }
        main.removeCallbacks(refreshGate);
        revalidate();
        main.postDelayed(refreshGate,90_000L);
        main.removeCallbacks(refreshHome);
        main.postDelayed(refreshHome,2_000L);
        TrackingAutoStart.maybeStart(this);
        CompanionUpdateAlerts.checkOnOpen(this);
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
        if(staffDexPanel!=null){staffDexPanel.dispose();staffDexPanel=null;}
        Shizuku.removeRequestPermissionResultListener(trackingPermissionListener);
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
        action(c,"↗",CompanionLicense.accepted(this)?"Link this device":"Review User Agreement",
            CompanionLicense.accepted(this)?"Use your personal staff setup link":"Accept before linking",
            ()->startActivity(new Intent(this,CompanionLicense.accepted(this)?
                MobileDeployActivity.class:CompanionLicenseActivity.class)));
        action(c,"↻","Retry staff verification","Recheck your existing device link without clearing credentials",
            this::revalidate);
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
                if(StaffGate.unlocked(this)){
                    maybeRestoreFloatingTools();
                    if(foreground&&InboxAlertSettings.liveEnabled(this))
                        VRChatPipelineService.start(this);
                }
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
        if(!CompanionLicense.accepted(this)){
            // An account can be enrolled before its local license was accepted.
            // Never reveal an unlocked Companion behind a declined agreement.
            locked("Read and accept the Companion staff agreement to continue setup.");
            LinearLayout legal=card();
            action(legal,"✓","Review staff agreement",
                "Required before opening the guided setup",
                ()->startActivity(new Intent(this,CompanionLicenseActivity.class)));
            if(!licenseLaunched){
                licenseLaunched=true;
                startActivity(new Intent(this,CompanionLicenseActivity.class));
            }
            return;
        }
        if(setupAfterLink||CompanionSetupState.shouldShow(this)){
            tab="home";
            if(setupDeferred){
                // Staff authorization is already verified. This is an incomplete
                // onboarding checklist, NOT a revoked Relay deployment.
                locked("Staff access verified. Guided setup was paused; "
                    +"your device link and VRChat session were kept.");
                LinearLayout resume=card();
                resume.addView(txt("Continue setup when you're ready.",16,
                    CompanionStyle.TEXT,true));
                action(resume,"✓","Resume setup",
                    "Review unfinished steps and confirm completion",
                    ()->{
                        if(!StaffGate.unlocked(this)){revalidate();return;}
                        setupDeferred=false;
                        setupLaunched=false;
                        renderUnlocked();
                    });
                action(resume,"↻","Recheck existing staff access",
                    "Retry without unlinking your device",this::revalidate);
                return;
            }
            if(!setupLaunched){
                setupLaunched=true;
                setupAfterLink=false;
                startActivityForResult(new Intent(this,CompanionSetupActivity.class),
                    GUIDED_SETUP_REQUEST);
            }
            return;
        }
        // Setup may also finish through Settings. Clear the deferred state
        // once the complete checklist is genuinely persisted.
        setupDeferred=false;
        ThemePreferences.apply(this);
        showingUnlocked=true;shell(true);
        QuestBackApplication.refresh(this);
        switch(tab){
            case "explore":renderExplore();break;
            case "staffdex":renderStaffDex();break;
            case "tracking":renderTracking();break;
            case "settings":renderSettings();break;
            default:renderHome();break;
        }
        // Set up personal VRChat before showing release highlights. Quest
        // additionally needs user-confirmed FULL logging and persisted SAF
        // Documents/Logs access. On phones Shizuku remains OPTIONAL.

        if(!whatsNewLaunched && WhatsNewActivity.shouldShow(this)){
            whatsNewLaunched=true;
            startActivity(new Intent(this,WhatsNewActivity.class));
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
        tile.addView(txt(icon,19,CompanionStyle.ICON,false));
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
        // Keep the beloved small Home Tracking control alongside Floating/Quest
        // Tools. It now enters the ONE Tracking dashboard, not a second page.
        controls.setOnClickListener(v->openTab("tracking"));
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
        action(essentials,"ⓘ","Setup & troubleshooting",
            "Review device linking, permissions, and optional tracking",
            ()->needsStaff(()->startActivity(
                new Intent(this,CompanionSetupActivity.class))));
        action(essentials,"↓","App updates & release notes",
            "Check for the newest signed TGC Companion version",
            ()->startActivity(new Intent(this,MobileUpdaterActivity.class)));
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
    /** Four compact, curated Explore categories instead of a 20-row menu. */
    private boolean isExploreCategory(String value){
        return "friends".equals(value)||"discover".equals(value)||
            "activity".equals(value)||"inbox".equals(value);
    }
    private void exploreSwitch(LinearLayout container,String title,String icon,
                               String category){
        boolean selected=category.equals(exploreCategory);
        LinearLayout holder=stack();holder.setGravity(Gravity.CENTER_VERTICAL);
        holder.setPadding(dp(11),dp(10),dp(11),dp(10));
        holder.setBackground(CompanionStyle.shape(this,
            selected?CompanionStyle.FIELD:CompanionStyle.SURFACE,13,
            selected?CompanionStyle.ACCENT:CompanionStyle.BORDER));
        LinearLayout.LayoutParams width=new LinearLayout.LayoutParams(0,dp(60),1f);
        width.setMargins(dp(3),0,dp(3),0);
        container.addView(holder,width);
        LinearLayout label=new LinearLayout(this);
        label.setGravity(Gravity.CENTER_VERTICAL);
        holder.addView(label);
        label.addView(txt(icon,18,selected?CompanionStyle.ICON:CompanionStyle.MUTED,false));
        TextView text=txt(title,13,selected?CompanionStyle.TEXT:CompanionStyle.MUTED,true);
        text.setPadding(dp(7),0,0,0);
        text.setMaxLines(1);
        text.setEllipsize(android.text.TextUtils.TruncateAt.END);
        label.addView(text);
        holder.setContentDescription("Explore "+title+(selected?", selected":""));
        holder.setOnClickListener(v->{
            if(!StaffGate.unlocked(this))return;
            if(!category.equals(exploreCategory)){
                exploreCategory=category;
                renderUnlocked();
            }
        });
    }
    private void exploreCategoryRow(LinearLayout parent,
                                    String left,String leftIcon,String leftId,
                                    String right,String rightIcon,String rightId){
        LinearLayout row=new LinearLayout(this);
        LinearLayout.LayoutParams layout=new LinearLayout.LayoutParams(-1,-2);
        layout.bottomMargin=dp(7);parent.addView(row,layout);
        exploreSwitch(row,left,leftIcon,leftId);
        exploreSwitch(row,right,rightIcon,rightId);
    }
    /** Denser, theme-aware action rows for the Explore category submenus. */
    private void exploreLink(LinearLayout parent,String glyph,String title,
                             String detail,Runnable open){
        LinearLayout row=new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(11),dp(11),dp(10),dp(11));
        row.setBackground(CompanionStyle.shape(this,CompanionStyle.RAISED,13,0));
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);
        params.bottomMargin=dp(7);parent.addView(row,params);
        TextView symbol=txt(glyph,20,CompanionStyle.ICON,true);
        symbol.setGravity(Gravity.CENTER);
        symbol.setBackground(CompanionStyle.shape(this,CompanionStyle.FIELD,11,0));
        row.addView(symbol,new LinearLayout.LayoutParams(dp(39),dp(39)));
        LinearLayout labels=stack();
        labels.setPadding(dp(12),0,dp(9),0);
        row.addView(labels,new LinearLayout.LayoutParams(0,-2,1f));
        labels.addView(txt(title,15,CompanionStyle.TEXT,true));
        labels.addView(txt(detail,12,CompanionStyle.MUTED,false));
        row.addView(txt("›",26,CompanionStyle.MUTED,false));
        row.setContentDescription(title+". "+detail);
        row.setOnClickListener(v->open.run());
    }
    private void renderStaffDex(){
        page.setPadding(dp(15),dp(12),dp(15),dp(26));
        staffDexPanel=new StaffDexPanel(this);
        page.addView(staffDexPanel,
            new LinearLayout.LayoutParams(-1,-2));
    }
    private void renderExplore(){
        page.setPadding(dp(14),dp(12),dp(14),dp(24));
        page.addView(CompanionStyle.section(this,"EXPLORE VRCHAT"));
        page.addView(txt("What are you looking for?",24,CompanionStyle.TEXT,true));
        space(page,5);
        page.addView(txt("Friends, places and community tools — without a wall of menus.",
            13,CompanionStyle.MUTED,false));
        space(page,14);

        // Frequent entry points stay visible while longer lists are filtered.
        page.addView(CompanionStyle.section(this,"QUICK ACCESS"));
        LinearLayout quick=stack();page.addView(quick);
        compactQuickPair(quick,"♧","Online friends","See who's online",
            ()->openBrowse("friends_online"),
            "✉","Inbox","Invites and requests",()->openInbox("all"));
        compactQuickPair(quick,"⌕","Find people","Search VRChat users",
            ()->openBrowse("search_users"),
            "◇","Favorite worlds","Your saved worlds",()->openBrowse("worlds"));

        page.addView(CompanionStyle.section(this,"BROWSE BY CATEGORY"));
        LinearLayout categories=stack();page.addView(categories);
        exploreCategoryRow(categories,"Friends","♧","friends",
            "Discover","◇","discover");
        exploreCategoryRow(categories,"Activity","◷","activity",
            "Inbox","✉","inbox");
        space(page,6);

        switch(exploreCategory){
            case "discover":{
                page.addView(CompanionStyle.section(this,"WORLDS, GROUPS & AVATARS"));
                LinearLayout links=card();
                exploreLink(links,"★","Bookmarked worlds","Your personal world shortcuts",
                    ()->openBrowse("bookmarks"));
                exploreLink(links,"↺","Recently visited","Revisit worlds you've explored",
                    ()->openBrowse("recent"));
                exploreLink(links,"⌕","Find worlds","Discover something new",
                    ()->openBrowse("search_worlds"));
                exploreLink(links,"◎","Create VRChat instance",
                    "Choose a world and send yourself an invite",
                    ()->startActivity(new Intent(this,VRChatInstanceCreateActivity.class)));
                exploreLink(links,"◈","My groups","Your VRChat communities",
                    ()->openBrowse("groups"));
                exploreLink(links,"◉","Favorite avatars","Browse and equip saved avatars",
                    this::openAvatars);
                exploreLink(links,"★","Extended avatar favorites",
                    "Private VRC+ avatar collections",this::openExtendedAvatars);
                break;
            }
            case "activity":{
                page.addView(CompanionStyle.section(this,"HISTORY & ACTIVITY"));
                LinearLayout links=card();
                exploreLink(links,"◎","Observed players","Players in your recent VRChat sessions",
                    ()->openPlayerLog("current"));
                exploreLink(links,"◷","Join & leave history","Local join, leave and instance events",
                    ()->openPlayerLog("timeline"));
                exploreLink(links,"◎","Recently encountered","People you've met",
                    ()->openBrowse("encounters"));
                exploreLink(links,"♧","Friend history","Search changes over time",
                    ()->openFriendHub("log"));
                exploreLink(links,"↗","Friend activity feed","See monitored friend activity",
                    ()->openFriendHub("activity"));
                break;
            }
            case "inbox":{
                page.addView(CompanionStyle.section(this,"MESSAGES & REQUESTS"));
                LinearLayout links=card();
                exploreLink(links,"✉","All notifications","Review everything in your inbox",
                    ()->openInbox("all"));
                exploreLink(links,"➜","World invites","Accept or decline invitations",
                    ()->openInbox("invite"));
                exploreLink(links,"◉","Friend requests","Respond to incoming friend requests",
                    ()->openInbox("friend"));
                break;
            }
            default:{
                page.addView(CompanionStyle.section(this,"FRIENDS & SOCIAL"));
                LinearLayout links=card();
                exploreLink(links,"◌","Offline friends","See friends who aren't online",
                    ()->openBrowse("friends_offline"));
                exploreLink(links,"★","VRChat favorite friend groups",
                    "Browse your real VRChat favorites categories",
                    ()->openBrowse("friend_favorite_groups"));
                exploreLink(links,"♧","Friends' locations","See where visible friends are playing",
                    ()->openFriendHub("locations"));
                exploreLink(links,"✎","Private VRChat notes","Keep notes attached to VRChat users",
                    this::openNotes);
                break;
            }
        }
    }
    /** Inline first-run setup on Tracking; never buried inside controls.
     * Acknowledgement is instructional, not evidence that VRChat is set to Full.
     */
    private void renderTrackingSetup(){
        final boolean acknowledged=TrackingSetupGuide.acknowledged(this);
        if(!acknowledged||trackingGuideExpanded){
            page.addView(CompanionStyle.section(this,"FIRST-TIME TRACKING SETUP"));
            LinearLayout guide=card();
            guide.addView(txt(acknowledged?"Tracking setup guide":
                "Complete this once before tracking",19,CompanionStyle.TEXT,true));
            space(guide,10);
            guide.addView(txt(
                "1. In VRChat, open Settings → Debug → Logging and set it to FULL.",
                15,CompanionStyle.GOLD,true));
            space(guide,7);
            guide.addView(txt(
                "The Companion cannot read or change VRChat's logging setting.",
                12,CompanionStyle.MUTED,false));
            space(guide,11);
            guide.addView(txt(QuestDevice.isQuestDevice()?
                "2. Grant read-only access to Documents/Logs on Quest. Shizuku is an advanced fallback.":
                "2. Authorize Shizuku for Android phone player tracking.",
                14,CompanionStyle.TEXT,false));
            space(guide,11);
            guide.addView(txt(
                "3. Start tracking before joining a VRChat instance or waiting for new players. Older joins are not replayed.",
                14,CompanionStyle.TEXT,false));
            space(guide,13);
            if(!acknowledged){
                action(guide,"✓","I understand · Continue",
                    "Acknowledge this setup once; it doesn't verify VRChat settings.",
                    ()->{
                        TrackingSetupGuide.acknowledge(this);
                        trackingGuideExpanded=false;
                        renderUnlocked();
                    });
            }else{
                action(guide,"‹","Close setup guide",
                    "Return to your normal Tracking screen",
                    ()->{trackingGuideExpanded=false;renderUnlocked();});
            }
        }else{
            android.widget.Button review=CompanionStyle.button(this,
                "View setup guide",false);
            review.setTextSize(12);
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,dp(39));
            lp.bottomMargin=dp(8);
            page.addView(review,lp);
            review.setOnClickListener(v->{
                trackingGuideExpanded=true;
                renderUnlocked();
            });
        }
    }
    /** One user-facing Tracking tab, including permissions and diagnostics. */
    private void renderTracking(){
        page.setPadding(dp(15),dp(12),dp(15),dp(24));
        page.addView(CompanionStyle.section(this,"TRACKING"));
        page.addView(txt("Player tracking",25,CompanionStyle.TEXT,true));
        space(page,5);
        page.addView(txt("Capture player joins, review your history and manage the connection.",
            13,CompanionStyle.MUTED,false));
        space(page,13);
        renderTrackingSetup();

        android.content.SharedPreferences s=getSharedPreferences("tgc_mobile_node_status",0);
        long heartbeat=s.getLong("service_heartbeat_at",0L);
        boolean active=s.getBoolean("running",false) && heartbeat>0L &&
            System.currentTimeMillis()-heartbeat<90_000L;

        LinearLayout dashboard=card();
        dashboard.addView(CompanionStyle.chip(this,
            active?"● Capturing players":"○ Tracking paused",
            active?CompanionStyle.GOOD:CompanionStyle.GOLD,
            active?0xFF183D37:0xFF3E3325));
        space(dashboard,11);
        dashboard.addView(txt(active?"Tracking is active":
            "Tracking is not running",16,CompanionStyle.TEXT,true));
        space(dashboard,5);
        dashboard.addView(txt(active?
            "New VRChat player sightings are being recorded. Review the event history below.":
            "Start capture here. Advanced permissions and troubleshooting remain available below.",
            13,CompanionStyle.MUTED,false));
        space(dashboard,11);
        boolean linked=false;
        try{linked=MobileCredentials.load(this)!=null;}
        catch(Exception e){linked=false;}
        dashboard.addView(txt(linked?"● Device linked":"○ Device not linked",13,
            linked?CompanionStyle.GOOD:CompanionStyle.GOLD,true));
        space(dashboard,7);
        dashboard.addView(txt("Recorded: "+s.getInt("captured_total",0)+
            "  ·  Synced: "+s.getInt("uploaded_total",0),
            13,CompanionStyle.MUTED,false));
        space(dashboard,9);
        android.widget.CheckBox auto=new android.widget.CheckBox(this);
        auto.setText("Start tracking automatically when Companion opens");
        auto.setTextColor(CompanionStyle.TEXT);
        auto.setTextSize(12);
        auto.setChecked(TrackingAutoStart.enabled(this));
        auto.setOnCheckedChangeListener((v,checked)->{
            TrackingAutoStart.setEnabled(this,checked);
            if(checked)TrackingAutoStart.maybeStart(this);
        });
        dashboard.addView(auto);
        android.widget.Button start=CompanionStyle.button(this,"Start",true);
        LinearLayout controls=new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        dashboard.addView(controls);
        controls.addView(start,new LinearLayout.LayoutParams(0,dp(46),1f));
        start.setEnabled(!active);
        start.setOnClickListener(v->needsStaff(this::startTrackingInline));
        android.widget.Button stop=CompanionStyle.button(this,"Stop",false);
        controls.addView(stop,new LinearLayout.LayoutParams(0,dp(46),1f));
        stop.setEnabled(active);
        stop.setOnClickListener(v->needsStaff(()->{
            startService(new Intent(this,MobileNodeService.class)
                .setAction(MobileNodeService.STOP));
            Toast.makeText(this,"Stopping tracking…",Toast.LENGTH_SHORT).show();
            main.postDelayed(this::renderUnlocked,1200L);
        }));
        android.widget.Button refresh=CompanionStyle.button(this,"Refresh",false);
        controls.addView(refresh,new LinearLayout.LayoutParams(0,dp(46),1f));
        refresh.setOnClickListener(v->renderUnlocked());
        // No secondary Device & Capture Controls screen: every tracker control
        // and permission is available on this one scrollable Tracking page.

        page.addView(CompanionStyle.section(this,"YOUR ACTIVITY"));
        LinearLayout stats=card();
        exploreLink(stats,"◷","Join & leave history",
            "Review events and instance changes",()->openPlayerLog("timeline"));
        exploreLink(stats,"◎","Observed player list",
            "Browse players captured on this device",()->openPlayerLog("current"));

        page.addView(CompanionStyle.section(this,"SETUP & HELP"));
        LinearLayout help=card();
        boolean quest=QuestDevice.isQuestDevice();
        help.addView(txt(quest?
            "Quest: Documents/Logs folder access":
            "Phone: Shizuku for VRChat log access",16,CompanionStyle.TEXT,true));
        space(help,6);
        help.addView(txt(quest?
            "Quest tracking uses read-only VRChat log-folder access by default.":
            "Phone tracking needs Shizuku access to the protected VRChat Mobile logs.",
            13,CompanionStyle.MUTED,false));
        space(help,8);
        exploreLink(help,trackingInfoExpanded?"⌃":"ⓘ",
            trackingInfoExpanded?"Hide details":"How tracking works",
            "Instructions, privacy and alternative methods",
            ()->{trackingInfoExpanded=!trackingInfoExpanded;renderUnlocked();});
        if(trackingInfoExpanded){
            help.addView(txt(
                "Set VRChat → Settings → Debug → Logging to FULL before joining an instance. "+
                "The Companion cannot change this VRChat setting, and joins from "+
                "before the tracker started are not replayed.",
                13,CompanionStyle.MUTED,false));
            space(help,8);
            help.addView(txt(quest?
                "Quest: select Documents/Logs below. If read-only folder tracking "+
                "fails, stop the tracker and select Shizuku fallback below.":
                "Android phone: authorize Shizuku in the permissions section below. "+
                "Other Companion features work without Shizuku.",
                13,CompanionStyle.MUTED,false));
            space(help,8);
            help.addView(txt(
                "Selected player sightings may assist the TGC invite network. "+
                "Complete raw VRChat logs are not uploaded.",
                12,CompanionStyle.MUTED,false));
        }

        page.addView(CompanionStyle.section(this,"DEVICE PERMISSIONS & CONNECTION"));
        LinearLayout access=card();
        boolean hasNode=false;
        try{hasNode=MobileCredentials.load(this)!=null;}
        catch(Exception ignored){}
        if(!hasNode){
            access.addView(txt("This device is not linked to the invite network.",
                13,CompanionStyle.GOLD,false));
            exploreLink(access,"🔗","Link this device",
                "Use your authorized one-time setup link",
                ()->needsStaff(()->startActivity(new Intent(this,MobileDeployActivity.class))));
        }
        if(quest){
            final boolean folderSelected=QuestSafLogWatcher.isSelected(this);
            final boolean folderGranted=QuestSafLogWatcher.hasGrant(this);
            access.addView(txt("Quest source: "+(folderSelected?"Documents/Logs folder":"Shizuku fallback")+
                " · Folder permission: "+(folderGranted?"Granted":"Not granted"),
                13,folderGranted?CompanionStyle.GOOD:CompanionStyle.GOLD,false));
            access.addView(txt("Folder: "+QuestSafLogWatcher.grantedPath(this),
                12,CompanionStyle.MUTED,false));
            exploreLink(access,"▣",folderGranted?"Change Documents/Logs folder":
                    "Grant Documents/Logs folder access",
                active?"Stop tracking before changing the log source":
                    "Read-only folder permission · recommended for Quest",
                ()->needsStaff(()->{
                    if(trackingRunning())trackingNotice("Stop tracking before changing the log source.");
                    else chooseQuestLogFolder();
                }));
            if(!folderSelected){
                exploreLink(access,"✓","Use folder tracking (recommended)",
                    "Select the normal Quest log-reader method",()->needsStaff(()->{
                        if(trackingRunning())trackingNotice("Stop tracking before changing modes.");
                        else if(!QuestSafLogWatcher.hasGrant(this))chooseQuestLogFolder();
                        else{QuestSafLogWatcher.selectFolderMode(this);renderUnlocked();}
                    }));
            }
            exploreLink(access,"◇","Shizuku alternative (Quest)",
                "Use only if Documents/Logs permission does not work",
                ()->{questShizukuExpanded=!questShizukuExpanded;renderUnlocked();});
            if(!folderSelected && questShizukuExpanded){
                access.addView(txt("Shizuku fallback selected. Stop tracking before changing methods.",
                    12,CompanionStyle.GOLD,false));
            }
            if(folderSelected && questShizukuExpanded){
                exploreLink(access,"◇","Switch to Shizuku fallback",
                    "Advanced method; requires a running Shizuku service",
                    ()->needsStaff(()->{
                        if(trackingRunning())trackingNotice("Stop tracking first.");
                        else{QuestSafLogWatcher.selectShizuku(this);renderUnlocked();}
                    }));
            }
        }
        if(!quest || !QuestSafLogWatcher.isSelected(this)){
            boolean shizukuAvailable=false,shizukuGranted=false;
            try{
                shizukuAvailable=Shizuku.pingBinder();
                shizukuGranted=shizukuAvailable &&
                    Shizuku.checkSelfPermission()==PackageManager.PERMISSION_GRANTED;
            }catch(Exception ignored){}
            access.addView(txt("Shizuku: "+(shizukuGranted?"Ready":
                shizukuAvailable?"Permission required":"Not running"),
                13,shizukuGranted?CompanionStyle.GOOD:CompanionStyle.GOLD,false));
            exploreLink(access,"✓","Request Shizuku permission",
                "Authorizes read-only access for phone / fallback log tracking",
                ()->needsStaff(this::requestTrackingShizuku));
            exploreLink(access,"ⓘ","Shizuku setup guide",
                "Open the official installation and setup instructions",
                ()->openTrackingGuideUrl("https://shizuku.rikka.app/guide/setup/"));
        }
        if(hasNode){
            exploreLink(access,"⊗","Unlink this device",
                "Stops tracking and clears local Node setup and unsent sightings",
                ()->needsStaff(this::confirmUnlinkTrackingDevice));
        }
        exploreLink(access,"⚙",advanced?"Hide connection diagnostics":
            "Show connection diagnostics","Service, watcher, Relay and capture counts",
            ()->{advanced=!advanced;renderUnlocked();});
        if(advanced){
            String serviceMode=s.getString("tracking_mode","Not started");
            long hb=s.getLong("service_heartbeat_at",0);
            access.addView(txt("Log method: "+serviceMode+
                "\nService heartbeat: "+(hb==0?"Never":Math.max(0,
                (System.currentTimeMillis()-hb)/1000)+" seconds ago")+
                "\nWatcher: "+s.getString("watcher","Not started")+
                "\nLog source found: "+s.getBoolean("watch_source",false)+
                "\nWatcher polls: "+s.getInt("watch_polls",0)+
                "\nNew joins: "+s.getInt("watch_joins",0)+
                "\nRead errors: "+s.getInt("watch_errors",0)+
                "\nBytes processed: "+s.getLong("watch_bytes",0)+
                "\nRelay: "+s.getString("relay","Not checked")+
                "\nRejected records: "+s.getInt("rejected_total",0),
                12,CompanionStyle.MUTED,false));
        }
    }
    private void confirmUnlinkTrackingDevice(){
        new android.app.AlertDialog.Builder(this)
            .setTitle("Unlink this device?")
            .setMessage("This stops player tracking and clears this device's Node link, "+
                "unsent sightings and saved local player/log history. "+
                "Your personal VRChat account remains signed in.")
            .setNegativeButton("Cancel",null)
            .setPositiveButton("Unlink",(d,w)->{
                startService(new Intent(this,MobileNodeService.class)
                    .setAction(MobileNodeService.STOP));
                MobileCredentials.clear(this);
                try(MobileOutbox outbox=new MobileOutbox(this)){outbox.wipe();}
                StaffGate.lock();
                trackingNotice("Device unlinked. Request a new deployment link to reconnect.");
                renderUnlocked();
            }).show();
    }
    private void trackingNotice(String message){
        Toast.makeText(this,message,Toast.LENGTH_LONG).show();
    }
    private boolean trackingRunning(){
        android.content.SharedPreferences s=getSharedPreferences("tgc_mobile_node_status",0);
        long hb=s.getLong("service_heartbeat_at",0);
        return s.getBoolean("running",false)&&hb>0 &&
            System.currentTimeMillis()-hb<90_000L;
    }
    private void openTrackingGuideUrl(String url){
        try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));}
        catch(Exception ignored){trackingNotice("Visit https://shizuku.rikka.app/guide/setup/");}
    }
    private void chooseQuestLogFolder(){
        if(!QuestDevice.isQuestDevice() || trackingRunning())return;
        Intent pick=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        pick.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION |
            Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try{startActivityForResult(pick,TRACKING_FOLDER_REQUEST);}
        catch(Exception ex){trackingNotice("Quest could not open the folder picker.");}
    }
    private void requestTrackingShizuku(){
        try{
            if(!Shizuku.pingBinder()){trackingNotice("Start Shizuku first, then retry.");return;}
            if(Shizuku.checkSelfPermission()!=PackageManager.PERMISSION_GRANTED){
                Shizuku.requestPermission(TRACKING_SHIZUKU_REQUEST);
                trackingNotice("Allow the Shizuku permission prompt.");
            }else trackingNotice("Shizuku permission already granted.");
        }catch(Exception ex){trackingNotice("Shizuku connection unavailable.");}
    }
    private void startTrackingInline(){
        if(!TrackingSetupGuide.acknowledged(this)){
            trackingGuideExpanded=true;renderUnlocked();
            trackingNotice("Complete the tracking setup guide first.");return;
        }
        try{
            if(MobileCredentials.load(this)==null){
                trackingNotice("Link this device in Device Permissions below first.");return;
            }
            if(QuestSafLogWatcher.isSelected(this)){
                if(!QuestSafLogWatcher.hasGrant(this)){
                    trackingNotice("Grant Documents/Logs permission below first.");
                    chooseQuestLogFolder();return;
                }
            }else if(!Shizuku.pingBinder()){
                trackingNotice("Start Shizuku first; setup is on this page.");return;
            }else if(Shizuku.checkSelfPermission()!=PackageManager.PERMISSION_GRANTED){
                requestTrackingShizuku();return;
            }
            if(android.os.Build.VERSION.SDK_INT>=33 &&
                checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=
                    PackageManager.PERMISSION_GRANTED)
                requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS},451);
            Intent start=new Intent(this,MobileNodeService.class).setAction(MobileNodeService.START);
            if(android.os.Build.VERSION.SDK_INT>=26)startForegroundService(start);
            else startService(start);
            trackingNotice("Starting player tracking…");
            main.postDelayed(this::renderUnlocked,1200L);
        }catch(Exception ex){trackingNotice("Couldn't start tracking: "+ex.getMessage());}
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request==GUIDED_SETUP_REQUEST){
            setupLaunched=false;
            setupAfterLink=false;
            // Never turn a canceled guide into a failed staff authentication.
            // A result is OK only when the setup state itself is complete.
            setupDeferred=result!=RESULT_OK || !CompanionSetupState.isComplete(this);
            if(StaffGate.unlocked(this))renderUnlocked();
            else{
                locked("Reconnecting to your existing staff authorization...");
                revalidate();
            }
            return;
        }
        if(request!=TRACKING_FOLDER_REQUEST)return;
        if(result!=RESULT_OK||data==null||data.getData()==null){
            trackingNotice("Folder selection canceled.");return;
        }
        try{
            QuestSafLogWatcher.selectFolder(this,data.getData());
            trackingNotice("Documents/Logs selected. Start tracking when ready.");
        }catch(Exception ex){trackingNotice("Folder permission failed: "+ex.getMessage());}
        if("tracking".equals(tab)&&StaffGate.unlocked(this))renderUnlocked();
    }
    private LinearLayout settingsChild(LinearLayout root){
        LinearLayout section=new LinearLayout(this);
        section.setOrientation(LinearLayout.VERTICAL);
        section.setPadding(0,dp(3),0,dp(3));
        root.addView(section,new LinearLayout.LayoutParams(-1,-2));
        return section;
    }
    /** Compact expandable settings group; content exists only when opened. */
    private LinearLayout settingsPanel(String id,String title,String summary){
        LinearLayout panel=card();
        boolean open=settingsExpanded.contains(id);
        LinearLayout header=new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(0,dp(2),0,dp(3));
        panel.addView(header,new LinearLayout.LayoutParams(-1,-2));
        LinearLayout copy=stack();
        header.addView(copy,new LinearLayout.LayoutParams(0,-2,1f));
        copy.addView(txt(title,16,CompanionStyle.TEXT,true));
        TextView detail=txt(summary,12,CompanionStyle.MUTED,false);
        detail.setMaxLines(2);copy.addView(detail);
        header.addView(txt(open?"⌃":"⌄",26,CompanionStyle.ACCENT,true));
        header.setContentDescription(title+", "+(open?"expanded":"collapsed")+". "+summary);
        header.setOnClickListener(v->{
            if(!StaffGate.unlocked(this)){revalidate();return;}
            if(!settingsExpanded.add(id))settingsExpanded.remove(id);
            renderUnlocked();
        });
        if(!open)return null;
        View rule=new View(this);
        rule.setBackgroundColor(CompanionStyle.BORDER);
        LinearLayout.LayoutParams divider=new LinearLayout.LayoutParams(-1,dp(1));
        divider.setMargins(0,dp(11),0,dp(8));panel.addView(rule,divider);
        return settingsChild(panel);
    }
    private void renderSettings(){
        headline("PREFERENCES","Settings","Accounts, alerts, tools and app.");
        boolean questSystem=QuestDevice.isQuestDevice();
        LinearLayout settingsBody;
        settingsBody=settingsPanel("account","ACCOUNT & SETUP",
            "VRChat sign-in, device link, setup and status");
        if(settingsBody!=null){
        settingsBody.addView(CompanionStyle.section(this,"GETTING STARTED"));
        LinearLayout onboarding=settingsChild(settingsBody);
        action(onboarding,"✓","Guided device setup",
            "VRChat login, "+(QuestDevice.isQuestDevice()?
                "Quest FULL logs and Documents/Logs access":"optional Shizuku tracking")+
                " · review any time",
            ()->needsStaff(()->startActivity(
                new Intent(this,CompanionSetupActivity.class))));

        settingsBody.addView(CompanionStyle.section(this,"YOUR ACCOUNT"));
        LinearLayout account=settingsChild(settingsBody);
        action(account,"◉","My VRChat account","Profile, sign-in and sign-out",
            ()->needsStaff(()->startActivity(new Intent(this,VRChatAccountActivity.class))));
        action(account,"♜","Device connection","Staff deployment and linked-device details",
            ()->needsStaff(()->startActivity(new Intent(this,MobileDeployActivity.class))));
        action(account,"♢","Verification code",
            "Find and copy your TGC or Cult invite code",this::openInviteCode);
        // Inbox is already on Home and Explore. Avoid a third identical link.
        // Avatar collections and private notes are already in Explore. Keep
        // Settings focused on account configuration instead of duplicate links.
        action(account,"☼","Status & presets","Edit your status and saved presets",this::openStatus);

        }
        settingsBody=settingsPanel("notifications","NOTIFICATIONS",
            "Inbox "+(InboxAlertSettings.enabled(this)?"on":"off")+
            " · Live "+(InboxAlertSettings.liveEnabled(this)?"on":"off"));
        if(settingsBody!=null){
        settingsBody.addView(CompanionStyle.section(this,"VRCHAT NOTIFICATIONS"));
        LinearLayout inboxSettings=settingsChild(settingsBody);
        boolean optedIn=InboxAlertSettings.enabled(this);
        boolean allowed=InboxAlertSettings.permissionGranted(this);
        inboxSettings.addView(txt(optedIn?
            allowed?"● Inbox alerts enabled":"○ Inbox alerts need Android permission":
            "○ Inbox alerts are off",15,
            optedIn&&allowed?CompanionStyle.GOOD:CompanionStyle.GOLD,true));
        space(inboxSettings,7);
        inboxSettings.addView(txt(
            "Invitations, friend requests, group notices and events. Live mode "+
            "is faster; Android controls sound and visibility.",
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
        if(optedIn&&allowed){
            boolean live=InboxAlertSettings.liveEnabled(this);
            space(inboxSettings,5);
            action(inboxSettings,"◉",
                live?"Turn off live VRChat alerts":"Enable LIVE VRChat alerts",
                live?"Stop the persistent WebSocket service; periodic checks remain":
                    "Much faster group, invite and friend alerts with a visible ongoing notification",
                this::toggleLiveInbox);
            action(inboxSettings,"◎","Send a notification test",
                "Check whether this phone shows and sounds a Group alert",
                ()->{
                    InboxAlertWorker.sendTest(this);
                    Toast.makeText(this,"Test sent. Check Android notification settings if silent.",
                        Toast.LENGTH_LONG).show();
                });
            action(inboxSettings,"⚙","Android notification sounds & pop-ups",
                "Adjust each VRChat notification category's sound, vibration and visibility",
                this::openAndroidNotificationSettings);
            android.app.NotificationManager notificationManager=
                (android.app.NotificationManager)getSystemService(NOTIFICATION_SERVICE);
            if(notificationManager!=null&&!notificationManager.areNotificationsEnabled())
                inboxSettings.addView(txt(
                    "Android has blocked app notifications. Open system settings above.",
                    12,CompanionStyle.GOLD,true));
        }

        }
        settingsBody=settingsPanel("friends","FRIEND AUTOMATION",
            "Invite requests and pinned friend alerts");
        if(settingsBody!=null){
        settingsBody.addView(CompanionStyle.section(this,"AUTO-ACCEPT INVITE REQUESTS"));
        LinearLayout autoInvites=settingsChild(settingsBody);
        boolean autoInviteEnabled=AutoInviteRequests.enabled(this);
        autoInvites.addView(txt(autoInviteEnabled?
            "● App Favorite Friend invite requests: ON":
            "○ App Favorite Friend invite requests: OFF",
            15,autoInviteEnabled?CompanionStyle.GOOD:CompanionStyle.MUTED,true));
        autoInvites.addView(txt(
            "Optionally invite pinned friends to your current VRChat instance "+
            "when they request it. Never affects TGC group invites.",
            12,CompanionStyle.MUTED,false));
        action(autoInvites,"✦",autoInviteEnabled?
            "Disable automatic invite responses":"Enable automatic invite responses",
            "Only Companion App Favorite Friends (pinned friends)",
            this::toggleAutoInviteRequests);
        settingsBody.addView(CompanionStyle.section(this,"PINNED FRIEND ONLINE ALERTS"));
        LinearLayout friendAlertSettings=settingsChild(settingsBody);
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
            "Get notified when a pinned friend comes online. Background "+
            "checks may be delayed by Android.",
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

        }
        settingsBody=settingsPanel("tools","FLOATING / QUEST & APPEARANCE",
            "Overlay controls, personal themes and profile status");
        if(settingsBody!=null){

        settingsBody.addView(CompanionStyle.section(this,
            questSystem?"QUEST TOOLS":"FLOATING TOOLS"));
        LinearLayout floating=settingsChild(settingsBody);
        if(questSystem){
            floating.addView(txt("Quest Tools replaces floating tools",16,
                CompanionStyle.ACCENT,true));
            space(floating,7);
            floating.addView(txt(
                "Open Quest Tools from Home for the full Quest-native interface. "+
                "Phone-style application overlays remain disabled on Quest. "+
                "A separately verified owner-only 2D window preview is available "+
                "inside Quest Tools; pinning and visibility in VRChat depend on Horizon OS. "+
                "Player tracking runs independently.",
                12,CompanionStyle.MUTED,false));
        }else{
        // Dev-only UI preview: visible exclusively to the exact Discord
        // identity currently verified by the TGC Relay. QuestToolsActivity
        // still requires a LIVE match of the separate VRChat account before
        // rendering anything. Do not replace the phone floater with this.
        if(OwnerQuestToolsPreview.isOwnerStaff(this)){
            action(floating,"⌘","Quest Tools · owner phone preview",
                "Test the real Quest Tools UI on this phone (VRChat identity check required)",
                ()->needsStaff(()->startActivity(new Intent(this,QuestToolsActivity.class))));
            space(floating,4);
        }
        action(floating,"◉","Floating tools",
            "Keep your tools available while playing VRChat",this::toggleOverlay);

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

        }
        // Keep the former low-level Shizuku test lab available, but away
        // from the one-page everyday Tracking UI.
        if(!questSystem){
            action(floating,"⌘","Advanced Shizuku diagnostic lab",
                "Optional low-level log-reader troubleshooting",
                ()->needsStaff(()->startActivity(new Intent(this,MainActivity.class))));
        }
        settingsBody.addView(CompanionStyle.section(this,"APPEARANCE"));
        LinearLayout themes=settingsChild(settingsBody);
        action(themes,"✦","Color theme",
            "Current: "+ThemePreferences.selectedPresetName(this)+
                (OwnerFeaturePreviewAccess.eligible(this)?
                    " · 12 shared + 1 owner-test exclusive theme":
                    ThemePreferences.devinityPresetEligible(this)?
                        " · 12 shared + 1 exclusive palette":
                        " · 12 built-in palettes for everyone"),
            this::chooseBuiltInTheme);
        if(ThemePreferences.allowed(this)){
            action(themes,"◈","Advanced Theme Studio",
                "Your saved themes, VRChat codes and custom hex / RGB mixer",
                ()->needsStaff(()->startActivity(new Intent(this,ThemeEditorActivity.class))));
        }
        if(StaffGate.rankTier(this)>=7){
            LinearLayout badge=settingsChild(settingsBody);
            badge.addView(CompanionStyle.chip(this,"♛ THE ROYALTY",
                ThemePreferences.royalGold(),0xFF46371C));
            badge.addView(txt("Gold accents mark your Royalty status. "+
                "Other staff see your verified Royalty identity on your VRChat profile.",
                12,CompanionStyle.MUTED,false));
        }

        }
        settingsBody=settingsPanel("app","APP, BATTERY & LEGAL",
            "Updates, background health, release notes and agreement");
        if(settingsBody!=null){
        settingsBody.addView(CompanionStyle.section(this,"BACKGROUND & BATTERY"));
        LinearLayout background=settingsChild(settingsBody);
        action(background,"◉","Background health",
            questSystem?"Tracking and inbox alerts at a glance":
                "Tracking, floating tools and inbox alerts at a glance",
            this::showBackgroundHealth);

        settingsBody.addView(CompanionStyle.section(this,"APP UPDATES"));
        LinearLayout updateAlerts=settingsChild(settingsBody);
        boolean updateEnabled=CompanionUpdateAlerts.enabled(this);
        boolean updateAllowed=CompanionUpdateAlerts.notificationsAllowed(this);
        updateAlerts.addView(txt(
            updateEnabled?(updateAllowed?
                "● Automatic update alerts enabled":"○ Allow Android notifications for update alerts"):
                "○ Automatic update alerts disabled",
            15,updateEnabled&&updateAllowed?
                CompanionStyle.GOOD:CompanionStyle.GOLD,true));
        updateAlerts.addView(txt(
            "Checks for signed updates and alerts once per new version.",
            12,CompanionStyle.MUTED,false));
        action(updateAlerts,"◉",
            updateEnabled?"Disable app update alerts":"Enable app update alerts",
            "Independent of VRChat inbox alert settings",
            this::toggleCompanionUpdateAlerts);
        if(updateEnabled&&!updateAllowed)
            action(updateAlerts,"↗","Fix Android update notifications",
                "Check app permission and the Companion updates notification channel",
                this::openCompanionUpdateNotificationSettings);
        action(updateAlerts,"↻","Check for update alerts now",
            "Run a fresh background update check",
            ()->{CompanionUpdateAlerts.checkNow(this);
                Toast.makeText(this,"Checking for official updates…",Toast.LENGTH_SHORT).show();});
        settingsBody.addView(CompanionStyle.section(this,"APP"));
        LinearLayout app=settingsChild(settingsBody);
        action(app,"↓","Check for updates","Keep your Companion up to date",
            ()->startActivity(new Intent(this,MobileUpdaterActivity.class)));
        action(app,"✦","What's New · release highlights",
            "Major staff-facing changes, including older releases",
            ()->startActivity(new Intent(this,WhatsNewActivity.class)));
        action(app,"ⓘ","About","Version "+BuildConfig.VERSION_NAME,()->{
            new android.app.AlertDialog.Builder(this)
                .setTitle("TGC Staff Companion")
                .setMessage("A private companion for The Gamer Club staff.\nVersion "+
                    BuildConfig.VERSION_NAME+"\n\nYour personal account and staff access stay separate.")
                .setPositiveButton("Close",null).show();
        });
        // Full expanded legal text remains readable at any time after
        // acceptance; before acceptance the first-launch screen shows it.
        settingsBody.addView(CompanionStyle.section(this,"LEGAL & AGREEMENT"));
        LinearLayout legal=settingsChild(settingsBody);
        action(legal,"§","Read full license & agreement",
            "Version "+CompanionLicense.VERSION+" · 43 detailed sections",
            ()->startActivity(new Intent(this,CompanionLicenseActivity.class)));


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
    private void toggleAutoInviteRequests(){
        if(!StaffGate.unlocked(this)){revalidate();return;}
        if(AutoInviteRequests.enabled(this)){
            AutoInviteRequests.setEnabled(this,false);
            Toast.makeText(this,"Auto invite responses disabled",
                Toast.LENGTH_SHORT).show();
            renderUnlocked();return;
        }
        new android.app.AlertDialog.Builder(this)
            .setTitle("Automatically invite App Favorite Friends?")
            .setMessage("After you enable this, the Companion may automatically "+
                "invite your pinned App Favorite Friends to your current VRChat "+
                "instance when they send a NEW invite request. This performs "+
                "real VRChat actions without asking each time. It does not "+
                "accept friend requests, cannot invite while you are offline, "+
                "and may be delayed by Android background restrictions. "+
                "This setting is off by default and only applies to your "+
                "currently signed-in personal VRChat account.")
            .setNegativeButton("Cancel",null)
            .setPositiveButton("Enable",(d,which)->{
                try{
                    AutoInviteRequests.setEnabled(this,true);
                    Toast.makeText(this,"Auto invite responses enabled",
                        Toast.LENGTH_SHORT).show();
                    renderUnlocked();
                }catch(Exception e){
                    Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show();
                }
            }).show();
    }
    private void toggleLiveInbox(){
        if(!StaffGate.unlocked(this)){revalidate();return;}
        if(InboxAlertSettings.liveEnabled(this)){
            InboxAlertSettings.setLiveEnabled(this,false);
            Toast.makeText(this,"Live inbox stopped; scheduled checks remain on",
                Toast.LENGTH_SHORT).show();
            renderUnlocked();
            return;
        }
        new android.app.AlertDialog.Builder(this)
            .setTitle("Enable low-latency VRChat inbox alerts?")
            .setMessage("The Companion will maintain a visible Android foreground "+
                "service and connect directly to the VRChat WebSocket pipeline "+
                "using your personal login. It refreshes actual inbox data "+
                "when new events arrive, including group announcements and "+
                "events. This uses battery and requires a persistent ongoing "+
                "notification. Android, network outages or VRChat may still "+
                "interrupt real-time delivery; scheduled catch-up remains active.")
            .setNegativeButton("Not now",null)
            .setPositiveButton("Enable live alerts",(dialog,button)->{
                try{
                    InboxAlertSettings.setLiveEnabled(this,true);
                    renderUnlocked();
                }catch(Exception e){
                    Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show();
                }
            }).show();
    }
    private void openAndroidNotificationSettings(){
        try{
            Intent settings=new Intent(
                android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE,getPackageName());
            startActivity(settings);
        }catch(Exception e){
            Toast.makeText(this,"Open Settings → Apps → Companion → Notifications",
                Toast.LENGTH_LONG).show();
        }
    }
    private void toggleCompanionUpdateAlerts(){
        if(!StaffGate.unlocked(this)){revalidate();return;}
        if(CompanionUpdateAlerts.enabled(this)){
            CompanionUpdateAlerts.setEnabled(this,false);
            Toast.makeText(this,"Companion update alerts disabled",
                Toast.LENGTH_SHORT).show();
        }else{
            CompanionUpdateAlerts.setEnabled(this,true);
            if(!CompanionUpdateAlerts.permission(this))
                requestCompanionUpdatePermission();
            else CompanionUpdateAlerts.checkOnOpen(this);
        }
        renderUnlocked();
    }
    private void openCompanionUpdateNotificationSettings(){
        if(android.os.Build.VERSION.SDK_INT>=33 &&
            !CompanionUpdateAlerts.permission(this)){
            requestCompanionUpdatePermission();return;
        }
        try{
            Intent settings=new Intent(android.provider.Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS);
            settings.putExtra(android.provider.Settings.EXTRA_APP_PACKAGE,getPackageName());
            settings.putExtra(android.provider.Settings.EXTRA_CHANNEL_ID,"tgc_companion_updates_v1");
            startActivity(settings);
        }catch(Exception e){openAndroidNotificationSettings();}
    }
    private void requestCompanionUpdatePermission(){
        if(android.os.Build.VERSION.SDK_INT>=33 &&
            !CompanionUpdateAlerts.permission(this)){
            requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS},
                UPDATE_ALERT_PERMISSION_REQUEST);
        }else{
            CompanionUpdateAlerts.restore(this);
            CompanionUpdateAlerts.checkOnOpen(this);
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
        if(requestCode==UPDATE_ALERT_PERMISSION_REQUEST){
            if(CompanionUpdateAlerts.permission(this)){
                CompanionUpdateAlerts.restore(this);
                CompanionUpdateAlerts.checkOnOpen(this);
            }else Toast.makeText(this,
                "Notification permission was not granted",Toast.LENGTH_LONG).show();
            if(StaffGate.unlocked(this))renderUnlocked();
            return;
        }
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
    /** Presets are for all currently verified users; custom RGB stays gated. */
    private void chooseBuiltInTheme(){
        if(!ThemePreferences.presetAllowed(this)){revalidate();return;}
        // UI choices use stable underlying indices; private presets are never
        // listed for other users, and the repository also guards direct calls.
        final int[] indices=ThemePreferences.availablePresetIndices(this);
        final String[] names=new String[indices.length];
        String selected=ThemePreferences.selectedPresetName(this);
        int current=0;
        for(int i=0;i<indices.length;i++){
            names[i]=ThemePreferences.NAMES[indices[i]];
            if(names[i].equals(selected))current=i;
        }
        new android.app.AlertDialog.Builder(this)
            .setTitle(OwnerFeaturePreviewAccess.eligible(this)?
                "Choose App Color Theme · Owner Preview":
                "Choose App Color Theme")
            .setSingleChoiceItems(names,current,(dialog,which)->{
                if(!ThemePreferences.presetAllowed(this)){dialog.dismiss();revalidate();return;}
                if(which<0||which>=indices.length)return;
                ThemePreferences.preset(this,indices[which]);
                dialog.dismiss();
                renderUnlocked();
            }).setNegativeButton("Cancel",null).show();
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
