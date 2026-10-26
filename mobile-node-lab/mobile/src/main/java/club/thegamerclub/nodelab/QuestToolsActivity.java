package club.thegamerclub.nodelab;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.core.content.ContextCompat;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Quest-native tools in an ordinary Android Activity (never a Quest overlay).
 * The exact owner may also preview this same UI on a phone after live Relay
 * AND VRChat identity verification. Everyone else's phone UI and floater stay
 * unchanged. This is not a new network/staff authority or invite sender.
 */
public final class QuestToolsActivity extends StaffProtectedActivity implements QuestBackHost {
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private final ArrayDeque<String> back=new ArrayDeque<>();
    private String page="home";
    // Home, Social, OSC, Tracking and Tools are navigation roots.
    // Shortcuts is an editor, not a root: it needs Back.
    @Override public boolean showQuestBack(){return !isPrimaryTab();}
    private LinearLayout body,root;
    private ScrollView scroll;
    private TextView oscSummary;
    private Button micButton,liveButton,pauseButton;
    private boolean restoreNativePageAfterPause=false;
    private OverlayMiniVrcx mini;
    private boolean phonePreviewAuthorized=false,phoneVerificationRunning=false;
    // Recheck the VERIFIED owner pair automatically after leaving VRChat Login.
    private boolean accountScreenOpened=false;
    private final Runnable statusTick=new Runnable(){
        @Override public void run(){
            if(oscSummary!=null)oscSummary.setText(QuestOscService.summary());
            if(micButton!=null)micButton.setText(
                QuestOscService.holding()?"RELEASE PTT":"Hold PTT");
            if(liveButton!=null)liveButton.setText(
                QuestOscService.live()?"Stop Live Chatbox":"Start Live Chatbox");
            if(pauseButton!=null){
                pauseButton.setVisibility(QuestOscService.live()?View.VISIBLE:View.GONE);
                pauseButton.setText(QuestOscService.paused()?"Resume Live":"Pause Live");
            }
            main.postDelayed(this,1000L);
        }
    };
    private int dp(float v){return CompanionStyle.dp(this,v);}
    private TextView text(String v,int size,int color,boolean bold){
        return CompanionStyle.text(this,v,size,color,bold);
    }
    private LinearLayout vertical(){
        LinearLayout out=new LinearLayout(this);
        out.setOrientation(LinearLayout.VERTICAL);
        return out;
    }
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        // Headsets remain unchanged. Phone entry requires the exact approved
        // Relay Discord identity, then a LIVE check of the VRChat identity
        // before any Quest Tools data or actions are displayed.
        if(!StaffGate.unlocked(this)){finish();return;}
        if(!QuestDevice.isQuestDevice() &&
            !OwnerQuestToolsPreview.isOwnerStaff(this)){finish();return;}
        getWindow().setSoftInputMode(
            android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        if(!QuestDevice.isQuestDevice()){
            verifyPhonePreview();
            return;
        }
        initializeTools();
    }
    private void verifyPhonePreview(){
        if(phoneVerificationRunning||isFinishing()||isDestroyed())return;
        if(!OwnerQuestToolsPreview.isOwnerStaff(this)){finish();return;}
        phoneVerificationRunning=true;
        LinearLayout panel=vertical();
        panel.setPadding(dp(18),dp(28),dp(18),dp(24));
        panel.setBackgroundColor(CompanionStyle.BG);
        setContentView(panel);
        panel.addView(text("OWNER · QUEST TOOLS PHONE PREVIEW",17,CompanionStyle.ACCENT,true));
        TextView state=text("Verifying your VRChat account online…",14,CompanionStyle.TEXT,false);
        state.setPadding(0,dp(14),0,dp(14));
        panel.addView(state);
        Button retry=button(panel,"Retry verification",false,this::verifyPhonePreview);
        retry.setEnabled(false);
        button(panel,"Open VRChat account",false,()->{
            accountScreenOpened=true;
            startActivity(new Intent(this,VRChatAccountActivity.class));
        });
        button(panel,"← Main Menu",false,this::returnToMainMenu);
        io.execute(()->{
            String failure=null;
            try{OwnerQuestToolsPreview.verifyLive(this);}
            catch(Exception e){failure=e.getMessage();}
            final String message=failure;
            runOnUiThread(()->{
                phoneVerificationRunning=false;
                if(isFinishing()||isDestroyed())return;
                if(message!=null){
                    state.setText("Preview locked: "+
                        (message.length()>190?message.substring(0,190):message)+
                        "\nSign in with the authorized VRChat account and retry.");
                    retry.setEnabled(true);
                    return;
                }
                if(!OwnerQuestToolsPreview.hasOwnerPair(this)){
                    state.setText("Preview locked: owner identities no longer match.");
                    retry.setEnabled(true);
                    return;
                }
                phonePreviewAuthorized=true;
                initializeTools();
            });
        });
    }
    private void initializeTools(){
        mini=new OverlayMiniVrcx(this,new OverlayMiniVrcx.Host(){
            @Override public void navigate(String target){go(target);}
            @Override public void back(){goBack();}
            @Override public void keyboard(boolean focused){
                if(!focused){
                    android.view.View current=getCurrentFocus();
                    if(current!=null){
                        InputMethodManager ime=(InputMethodManager)
                            getSystemService(INPUT_METHOD_SERVICE);
                        if(ime!=null)ime.hideSoftInputFromWindow(
                            current.getWindowToken(),0);
                    }
                }
            }
            @Override public void onPresenceChanged(String status){}
            @Override public void scrollToTop(){
                if(scroll!=null)scroll.post(()->scroll.smoothScrollTo(0,0));
            }
            @Override public void confirm(String prompt,Runnable task){
                if(isFinishing()||!canUseTools())return;
                new AlertDialog.Builder(QuestToolsActivity.this)
                    .setTitle("Confirm VRChat action")
                    .setMessage(prompt)
                    .setNegativeButton("Cancel",null)
                    .setPositiveButton("Confirm",(d,w)->{
                        if(canUseTools())task.run();
                    }).show();
            }
            @Override public void feedback(String message){toast(message);}
            @Override public boolean isOscLiveActive(){return QuestOscService.live();}
            @Override public String oscLiveDescription(){return QuestOscService.summary();}
            @Override public void oscStartLive(int intervalSeconds){
                startOsc(QuestOscService.START_LIVE,intervalSeconds);
            }
            @Override public void oscStopLive(){startOsc(QuestOscService.STOP_LIVE,0);}
        },false); // Quest and owner phone preview use one Back control.
        render();
    }
    @Override protected void onResume(){
        super.onResume();
        if(!QuestDevice.isQuestDevice()){
            if(!OwnerQuestToolsPreview.isOwnerStaff(this) ||
                (phonePreviewAuthorized &&
                 !OwnerQuestToolsPreview.hasOwnerPair(this))){
                finish();return;
            }
            if(accountScreenOpened && !phonePreviewAuthorized){
                accountScreenOpened=false;
                verifyPhonePreview();
            }
        }
        if(restoreNativePageAfterPause && mini!=null &&
           !isFinishing() && StaffGate.unlocked(this)){
            restoreNativePageAfterPause=false;
            render(); // Mini-VRCX screens cancel IO on pause; load a fresh view.
        }
        main.removeCallbacks(statusTick);
        main.post(statusTick);
    }
    @Override protected void onPause(){
        main.removeCallbacks(statusTick);
        restoreNativePageAfterPause=mini!=null && !isHub();
        if(mini!=null)mini.close();
        super.onPause();
    }
    @Override protected void onDestroy(){
        main.removeCallbacks(statusTick);
        if(mini!=null)mini.shutdown();
        io.shutdownNow();
        super.onDestroy();
    }
    private void toast(String value){
        if(value!=null&&!value.trim().isEmpty())
            Toast.makeText(this,value,Toast.LENGTH_SHORT).show();
    }
    private void startOsc(String action,int seconds){
        if(!QuestDevice.isQuestDevice()||!canUseTools()){
            toast("Quest OSC service requires a real Quest headset.");return;
        }
        Intent cmd=new Intent(this,QuestOscService.class).setAction(action);
        if(QuestOscService.START_LIVE.equals(action))
            cmd.putExtra("seconds",seconds);
        try{
            if(QuestOscService.START_LIVE.equals(action)||
               QuestOscService.VOICE_HOLD.equals(action)||
               QuestOscService.PAUSE_LIVE.equals(action))
                ContextCompat.startForegroundService(this,cmd);
            else startService(cmd);
        }catch(Exception ex){toast("OSC service: "+ex.getMessage());}
        main.postDelayed(statusTick,220L);
    }
    private void confirm(String title,String message,Runnable task){
        new AlertDialog.Builder(this).setTitle(title).setMessage(message)
            .setNegativeButton("Cancel",null)
            .setPositiveButton("Confirm",(d,w)->{
                if(canUseTools())task.run();
            }).show();
    }
    private Button button(LinearLayout column,String label,boolean primary,
                          Runnable task){
        Button b=CompanionStyle.button(this,label,primary);
        column.addView(b,CompanionStyle.buttonParams(this));
        b.setOnClickListener(v->task.run());
        return b;
    }
    private void pair(LinearLayout column,String a,String x,String b,String y){
        LinearLayout row=new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        column.addView(row,new LinearLayout.LayoutParams(-1,-2));
        pairButton(row,a,x);
        pairButton(row,b,y);
    }
    private void pairButton(LinearLayout row,String label,String pageKey){
        Button b=CompanionStyle.button(this,label,false);
        b.setTextSize(12);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(43),1f);
        p.setMargins(dp(2),dp(1),dp(2),dp(5));
        row.addView(b,p);
        b.setOnClickListener(v->go(pageKey));
    }
    private LinearLayout card(String title){
        body.addView(CompanionStyle.section(this,title));
        LinearLayout c=CompanionStyle.card(this);
        body.addView(c,CompanionStyle.cardParams(this));
        return c;
    }
    private boolean canUseTools(){
        return StaffGate.unlocked(this) &&
            (QuestDevice.isQuestDevice() ||
             (phonePreviewAuthorized && OwnerQuestToolsPreview.hasOwnerPair(this)));
    }
    private void go(String destination){
        if(!canUseTools()){finish();return;}
        if(page.equals(destination))return;
        back.push(page);page=destination;render();
    }
    private void switchTab(String destination){
        if(!canUseTools()){finish();return;}
        if(page.equals(destination))return;
        back.clear();page=destination;render();
    }
    private void goBack(){
        if(!canUseTools()){finish();return;}
        if(back.isEmpty()){
            if(isPrimaryTab()){returnToMainMenu();return;}
            page="home";
        }else page=back.pop();
        render();
    }
    @Override public void onBackPressed(){goBack();}
    private boolean isPrimaryTab(){
        return "home".equals(page)||"social".equals(page)||
            "osc".equals(page)||"tracking".equals(page)||
            "tools".equals(page);
    }
    private boolean isHub(){
        // The shortcuts editor has its own native layout, not a MiniVRCX page.
        return isPrimaryTab()||"shortcuts".equals(page);
    }
    /** Explicit exit to the COMPANION HOME, never to a stale Settings page. */
    private void returnToMainMenu(){
        // Leaving this page must never be blocked by an expired VRChat cookie.
        // CompanionActivity independently verifies staff access on return.
        Intent mainMenu=new Intent(this,CompanionActivity.class);
        mainMenu.putExtra("open_main_menu",true);
        mainMenu.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|
            Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(mainMenu);
        finish();
    }
    private void render(){
        if(isFinishing()||!canUseTools())return;
        oscSummary=null;micButton=null;liveButton=null;pauseButton=null;
        if(mini!=null)mini.close();
        root=vertical();
        root.setBackgroundColor(CompanionStyle.BG);
        setContentView(root);
        CompanionStyle.safeArea(this,root);
        QuestBackApplication.refresh(this);
        getWindow().setStatusBarColor(CompanionStyle.BG);
        getWindow().setNavigationBarColor(CompanionStyle.BG);
        LinearLayout heading=new LinearLayout(this);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        heading.setPadding(dp(14),dp(9),dp(14),dp(9));
        heading.setBackground(CompanionStyle.gradient(this,
            CompanionStyle.SURFACE,CompanionStyle.BG,14));
        root.addView(heading);
        // Root pages have a clear "Main Menu" exit on BOTH Quest and phone.
        // Nested pages: Quest uses its universal Back; the private phone
        // preview uses ONE wide, readable Back here instead.
        if(isPrimaryTab() || !QuestDevice.isQuestDevice()){
            final boolean topLevel=isPrimaryTab();
            Button navigation=CompanionStyle.button(this,
                topLevel?"← Main Menu":"← Back",false);
            navigation.setTextSize(12);
            navigation.setSingleLine(true);
            navigation.setAllCaps(false);
            navigation.setMinWidth(0);
            navigation.setMinimumWidth(0);
            navigation.setPadding(dp(9),0,dp(9),0);
            navigation.setContentDescription(topLevel?
                "Back to Staff Companion main menu":
                "Back to previous Quest Tools screen");
            LinearLayout.LayoutParams navSize=new LinearLayout.LayoutParams(
                dp(topLevel?120:96),dp(43));
            navSize.rightMargin=dp(4);
            heading.addView(navigation,navSize);
            navigation.setOnClickListener(v->{
                if(topLevel)returnToMainMenu();
                else goBack();
            });
        }
        LinearLayout titles=vertical();
        titles.setPadding(dp(6),0,0,0);
        heading.addView(titles,new LinearLayout.LayoutParams(0,-2,1));
        titles.addView(text("TGC STAFF COMPANION",10,CompanionStyle.ACCENT,true));
        // The active MiniVRCX screen has its own readable title below.
        // Repeating a long page name here steals width from the Back button.
        TextView screenTitle=text("Quest Tools",19,CompanionStyle.TEXT,true);
        screenTitle.setSingleLine(true);
        screenTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        titles.addView(screenTitle);
        LinearLayout tabs=new LinearLayout(this);
        tabs.setGravity(Gravity.CENTER_VERTICAL);
        tabs.setPadding(dp(6),dp(3),dp(6),dp(5));
        tabs.setBackgroundColor(CompanionStyle.SURFACE);
        root.addView(tabs);
        String[][] values={{"Home","home"},{"Social","social"},
            {"OSC","osc"},{"Tracking","tracking"},{"Tools","tools"}};
        for(String[] v:values){
            Button tab=CompanionStyle.button(this,v[0],page.equals(v[1]));
            tab.setTextSize(10);
            tab.setSingleLine(true);
            tab.setEllipsize(android.text.TextUtils.TruncateAt.END);
            tab.setPadding(dp(2),0,dp(2),0);
            tab.setMinWidth(0);tab.setMinimumWidth(0);
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(42),1f);
            p.setMargins(dp(2),0,dp(2),dp(2));
            tabs.addView(tab,p);
            tab.setContentDescription(v[0]+" Quest Tools tab"+
                (page.equals(v[1])?", selected":""));
            tab.setOnClickListener(view->switchTab(v[1]));
        }
        // Keep the user-configured three-button bar as a fixed sibling of
        // the ScrollView on EVERY Quest Tools page, including nested mini
        // pages and the shortcuts editor. Previously it disappeared as soon
        // as isHub() was false, which felt like the buttons moved with pages.
        // Rebuild it from saved preferences after navigation so button edits
        // are reflected without moving the bar into scrolling content.
        List<String> bar=OverlayPreferences.quickBar(this);
        if(!bar.isEmpty()){
            LinearLayout quick=new LinearLayout(this);
            quick.setGravity(Gravity.CENTER_VERTICAL);
            quick.setBackgroundColor(CompanionStyle.SURFACE);
            quick.setPadding(dp(12),dp(5),dp(12),dp(5));
            root.addView(quick,new LinearLayout.LayoutParams(-1,dp(51)));
            for(String key:bar){
                Button q=CompanionStyle.button(this,
                    OverlayPreferences.name(key),false);
                q.setTextSize(11);
                q.setSingleLine(true);
                q.setEllipsize(android.text.TextUtils.TruncateAt.END);
                LinearLayout.LayoutParams p=
                    new LinearLayout.LayoutParams(0,dp(39),1f);
                p.setMargins(dp(2),0,dp(2),0);
                quick.addView(q,p);
                q.setOnClickListener(v->openShortcut(key));
            }
        }
        scroll=new ScrollView(this);
        scroll.setFillViewport(true);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1f));
        body=vertical();
        body.setPadding(dp(12),dp(10),dp(12),dp(22));
        scroll.addView(body);
        if(!isHub()){
            mini.show(body,page);
            return;
        }
        switch(page){
            case "home":showHome();break;
            case "social":showSocial();break;
            case "osc":showOsc();break;
            case "tracking":showTracking();break;
            case "shortcuts":showShortcuts();break;
            default:showTools();break;
        }
    }
    private void openShortcut(String key){
        if("inbox".equals(key)){mini.inbox("all");return;}
        if("search".equals(key)){mini.findAnything();return;}
        if("verify".equals(key)){mini.verification();return;}
        go(OverlayPreferences.destination(key));
    }
    private void showHome(){
        // The Home hub is a root: no Back. Keep high-value routes visible.
        LinearLayout primary=card("QUICK ACTIONS");
        List<String> shortcuts=OverlayPreferences.shortcuts(this);
        for(int i=0;i<shortcuts.size();i+=2){
            if(i+1<shortcuts.size()){
                final String first=shortcuts.get(i),second=shortcuts.get(i+1);
                pair(primary,OverlayPreferences.name(first),
                    OverlayPreferences.destination(first),
                    OverlayPreferences.name(second),
                    OverlayPreferences.destination(second));
            }else{
                String key=shortcuts.get(i);
                button(primary,OverlayPreferences.name(key),false,
                    ()->openShortcut(key));
            }
        }
        button(primary,"Edit my shortcuts",false,()->go("shortcuts"));
        if(OwnerQuestFloatingPreview.eligible(this)){
            button(primary,"Open owner floating 2D panel (experimental)",true,()->
                startActivity(new Intent(this,QuestFloatingToolsActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)));
            primary.addView(text("Uses Horizon OS window/pin controls. Visibility "+
                "over VRChat is controlled by the headset and is not guaranteed.",
                12,CompanionStyle.MUTED,false));
        }

        LinearLayout social=card("VRCHAT · DISCOVER & CONNECT");
        pair(social,"Friends","mini_friends","Inbox","mini_inbox");
        pair(social,"Favorite friend groups","mini_friend_favorites",
            "Current instance","mini_now");
        pair(social,"Worlds","mini_worlds","Groups","mini_groups");
        button(social,"Create VRChat instance and invite myself",false,()->
            startActivity(new android.content.Intent(this,VRChatInstanceCreateActivity.class)));
        pair(social,"Avatars","mini_avatars","Player history","mini_players");
        pair(social,"Verification","mini_verify","Search","mini_find");

        LinearLayout osc=card("OSC · FAST CONTROLS");
        osc.addView(text(QuestOscService.summary(),13,CompanionStyle.ACCENT,true));
        osc.addView(text("Live Chatbox, PTT, saved phrases, typing and avatar "+
            "OSC share the same menu structure as Floating Tools.",
            12,CompanionStyle.MUTED,false));
        button(osc,"Open OSC controls",true,()->switchTab("osc"));
        // Main Menu is always available in the fixed top header. No duplicate exit.
    }
    private void showPinned(LinearLayout parent){
        final LinearLayout rows=vertical();
        final boolean[] editing={false};
        LinearLayout header=new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        parent.addView(header);
        int count=0;
        try{count=FriendPinStore.pins(this).length();}catch(Exception ignored){}
        header.addView(text(count+" pinned",12,CompanionStyle.ACCENT,true),
            new LinearLayout.LayoutParams(0,-2,1f));
        Button edit=CompanionStyle.button(this,"Edit order",false);
        edit.setTextSize(11);
        header.addView(edit,new LinearLayout.LayoutParams(dp(110),dp(38)));
        TextView hint=text("Use ↑/↓ to rearrange; saving is automatic",
            10,CompanionStyle.MUTED,false);
        hint.setVisibility(View.GONE);
        parent.addView(hint);
        parent.addView(rows);
        Runnable redraw=()->drawPinnedRows(rows,editing[0]);
        redraw.run();
        edit.setOnClickListener(v->{
            editing[0]=!editing[0];
            edit.setText(editing[0]?"Done":"Edit order");
            hint.setVisibility(editing[0]?View.VISIBLE:View.GONE);
            redraw.run();
        });
    }
    private void drawPinnedRows(LinearLayout rows,boolean editing){
        rows.removeAllViews();
        try{
            JSONArray pins=FriendPinStore.pins(this);
            JSONObject presence=new JSONObject();
            boolean recent=false;
            try{
                JSONObject session=VRChatSession.load(this);
                presence=FriendLogStore.snapshot(this,session);
                long at=FriendLogStore.lastPoll(this,session);
                recent=at>0&&System.currentTimeMillis()-at<8*60_000L;
            }catch(Exception ignored){}
            if(pins.length()==0){
                rows.addView(text("Pin people from the Friends list or profile.",
                    12,CompanionStyle.MUTED,false));
            }
            for(int i=0;i<pins.length();i++){
                JSONObject pin=pins.optJSONObject(i);
                if(pin==null)continue;
                final String id=pin.optString("id","");
                if(!OverlayMiniVrcx.validUser(id))continue;
                String name=pin.optString("name","Friend");
                JSONObject friend=presence.optJSONObject(id);
                boolean online=recent&&friend!=null&&friend.optBoolean("online",false);
                String availability=!recent?" · last seen":
                    online?" · ● online":" · offline";
                final int at=i;
                LinearLayout line=new LinearLayout(this);
                line.setGravity(Gravity.CENTER_VERTICAL);
                rows.addView(line,new LinearLayout.LayoutParams(-1,dp(editing?40:32)));
                TextView title=text((i+1)+". "+name+availability,11,
                    online?CompanionStyle.GOOD:CompanionStyle.TEXT,false);
                title.setSingleLine(true);
                title.setEllipsize(android.text.TextUtils.TruncateAt.END);
                title.setGravity(Gravity.CENTER_VERTICAL);
                line.addView(title,new LinearLayout.LayoutParams(0,-1,1f));
                if(!editing){
                    title.setOnClickListener(v->mini.profile(id));
                }else{
                    for(int direction:new int[]{-1,1}){
                        final int move=direction;
                        Button arrow=CompanionStyle.button(this,move<0?"↑":"↓",false);
                        arrow.setTextSize(12);
                        arrow.setMinHeight(0);arrow.setMinimumHeight(0);
                        arrow.setEnabled(move<0?at>0:at<pins.length()-1);
                        line.addView(arrow,new LinearLayout.LayoutParams(dp(40),dp(36)));
                        arrow.setOnClickListener(v->{
                            try{FriendPinStore.move(this,id,move);}
                            catch(Exception e){toast(e.getMessage());}
                            drawPinnedRows(rows,true);
                        });
                    }
                }
            }
        }catch(Exception ex){
            rows.addView(text("Sign into your personal VRChat account first.",
                12,CompanionStyle.MUTED,false));
        }
    }
    private void showSocial(){
        LinearLayout pins=card("PINNED FRIENDS");
        showPinned(pins);
        Button alert=button(pins,
            PinnedFriendAlerts.enabled(this)?"Online alerts: ON":"Online alerts: OFF",
            false,()->{});
        alert.setOnClickListener(v->{
            if(!PinnedFriendAlerts.permission(this)){
                toast("Allow Android notifications from Companion Settings first.");
                return;
            }
            try{
                boolean next=!PinnedFriendAlerts.enabled(this);
                PinnedFriendAlerts.setEnabled(this,next);
                alert.setText(next?"Online alerts: ON":"Online alerts: OFF");
                toast(next?"Alerts enabled; initial check seeds silently.":"Alerts disabled.");
            }catch(Exception e){toast(e.getMessage());}
        });
        LinearLayout favorites=card("VRCHAT FRIEND FAVORITES");
        favorites.addView(text("Your named VRChat friend categories, separate "+
            "from Companion pinned shortcuts.",12,CompanionStyle.MUTED,false));
        button(favorites,"Browse favorite friend groups",true,
            ()->go("mini_friend_favorites"));
        favorites.addView(text("Mutual friends and groups are available on "+
            "each person's VRChat profile, subject to privacy preferences.",
            11,CompanionStyle.MUTED,false));
        LinearLayout friends=card("FRIENDS & NOTIFICATIONS");
        pair(friends,"Friends & locations","mini_friends","Friend log","mini_log");
        pair(friends,"All notifications","mini_inbox","Activity feed","mini_activity");
        pair(friends,"Recent players","mini_players","Find anyone","mini_find");
        pair(friends,"Groups","mini_groups","My status","mini_presence");
    }
    private void runOscSend(String value){
        if(!canUseTools())return;
        io.execute(()->{
            String result;
            try{VRChatOscChat.send(value,false);
                result="OSC chat packet sent locally; VRChat receipt unverified.";
            }catch(Exception e){result="OSC chat: "+e.getMessage();}
            final String reply=result;
            main.post(()->toast(reply));
        });
    }
    private void showOsc(){
        LinearLayout live=card("LIVE CHATBOX · "+(QuestDevice.isQuestDevice()?
            "QUEST NATIVE":"PHONE PREVIEW"));
        oscSummary=text(QuestOscService.summary(),12,CompanionStyle.ACCENT,true);
        live.addView(oscSummary);
        liveButton=button(live,QuestOscService.live()?
            "Stop Live Chatbox":"Start Live Chatbox",true,()->{
            if(QuestOscService.live()){
                startOsc(QuestOscService.STOP_LIVE,0);return;
            }
            try{
                OscControlStore.liveChatboxAt(this,0);
                int seconds=OscControlStore.liveInterval(this);
                confirm("Start Live Chatbox?",
                    "Broadcast enabled chatboxes every "+seconds+
                    " seconds until stopped, even if you leave this screen.",
                    ()->startOsc(QuestOscService.START_LIVE,seconds));
            }catch(Exception e){toast("Cannot start: "+e.getMessage());}
        });
        pauseButton=button(live,QuestOscService.paused()?
            "Resume Live":"Pause Live",false,()->
                startOsc(QuestOscService.PAUSE_LIVE,0));
        pauseButton.setVisibility(QuestOscService.live()?View.VISIBLE:View.GONE);
        if(!StaffGate.delegated(this)){
            Button exclusive=button(live,"Official TGC tags only: "+(safeOfficial()?"ON":"OFF"),
                false,()->{});
            exclusive.setOnClickListener(v->{
                try{
                    boolean next=!OscControlStore.officialOnly(this);
                    OscControlStore.setOfficialOnly(this,next);
                    exclusive.setText("Official TGC tags only: "+(next?"ON":"OFF"));
                }catch(Exception e){toast(e.getMessage());}
            });
            pair(live,"Configure status","mini_osc_status",
                "Official rank tags","mini_osc_tags");
        }else{
            button(live,"Configure personal status",false,()->
                go("mini_osc_status"));
        }
        pair(live,"Rotation","mini_osc_rotation","Saved phrases","mini_osc_presets");

        LinearLayout mic=card("PUSH-TO-TALK VIA OSC");
        micButton=button(mic,QuestOscService.holding()?
            "RELEASE PTT":"Hold PTT",true,()->
            startOsc(QuestOscService.holding()?
                QuestOscService.VOICE_RELEASE:QuestOscService.VOICE_HOLD,0));
        button(mic,"Emergency mic RELEASE",false,()->
            startOsc(QuestOscService.VOICE_RELEASE,0));
        mic.addView(text("PTT auto-releases after five minutes. The service "+
            "notification also provides an emergency release. VRChat may "+
            "ignore OSC or suspend itself during Quest multitasking.",
            11,CompanionStyle.MUTED,false));

        LinearLayout quick=card("SEND SAVED PHRASES");
        try{
            JSONArray phrases=OscControlStore.phrases(this);
            int added=0;
            for(int i=0;i<phrases.length()&&added<6;i++){
                JSONObject item=phrases.optJSONObject(i);
                if(item==null)continue;
                String line=item.optString("text","");
                if(line.isEmpty())continue;
                added++;
                button(quick,"Send · "+item.optString("name","Saved phrase"),
                    false,()->runOscSend(line));
            }
            if(added==0)quick.addView(text(
                "Save a phrase in OSC Quick Phrases to see it here.",
                12,CompanionStyle.MUTED,false));
        }catch(Exception e){quick.addView(text("Sign in to VRChat for saved phrases.",
            12,CompanionStyle.MUTED,false));}
        pair(quick,"Compose message","mini_chat","Edit phrases","mini_osc_presets");
        LinearLayout advanced=card("AVATAR & TYPING OSC");
        OscHeightTool.attach(this,advanced,io,null,null);
        pair(advanced,"Avatar parameters","mini_osc_parameters",
            "OSC status","mini_osc_status");
        button(advanced,"Typing indicator ON",false,()->sendTyping(true));
        button(advanced,"Typing indicator OFF",false,()->sendTyping(false));
        button(advanced,"STOP ALL Quest OSC",false,()->
            confirm("Stop Quest OSC?","Stop Live Chatbox and release PTT?",
                ()->startOsc(QuestOscService.STOP_ALL,0)));
    }
    private boolean safeOfficial(){
        try{return OscControlStore.officialOnly(this);}
        catch(Exception ignored){return false;}
    }
    private void sendTyping(boolean value){
        io.execute(()->{
            try{VRChatOscChat.sendTyping(value);}
            catch(Exception ignored){}
        });
        toast("OSC typing packet sent locally.");
    }
    /** Tracking hub for both actual Quest and the creator's phone preview.
     * Capture continues in the existing per-device service, NOT this Activity.
     * On Quest, read-only Documents/Logs is the recommended source. */
    private void showTracking(){
        boolean quest=QuestDevice.isQuestDevice();
        android.content.SharedPreferences state=
            getSharedPreferences("tgc_mobile_node_status",0);
        long heartbeat=state.getLong("service_heartbeat_at",0L);
        boolean active=state.getBoolean("running",false)&&heartbeat>0L&&
            System.currentTimeMillis()-heartbeat<90_000L;
        int captured=state.getInt("captured_total",0);
        int uploaded=state.getInt("uploaded_total",0);

        LinearLayout status=card(quest?"QUEST PLAYER TRACKING":
            "PHONE PLAYER TRACKING · PREVIEW");
        status.addView(text(active?"● Tracking active":"○ Tracking paused",
            17,active?CompanionStyle.GOOD:CompanionStyle.GOLD,true));
        status.addView(text("Players captured: "+captured+
            "  ·  Records synced: "+uploaded,13,CompanionStyle.MUTED,false));
        status.addView(text(active?
            "The existing device capture service is recording player activity.":
            "Start capture in Device & Capture Controls. Tracking works "+
            "independently of whether Quest Tools is open.",
            12,CompanionStyle.MUTED,false));
        button(status,active?"Stop tracking":"Start tracking",true,()->{
            if(active){
                startService(new Intent(this,MobileNodeService.class)
                    .setAction(MobileNodeService.STOP));
                toast("Stopping tracking…");
            }else MobileDeployActivity.startFromDashboard(this);
            render();
        });
        button(status,TrackingAutoStart.enabled(this)?"Auto-start: ON · tap to disable":"Auto-start: OFF · tap to enable",false,()->{
            TrackingAutoStart.setEnabled(this,!TrackingAutoStart.enabled(this));
            if(TrackingAutoStart.enabled(this))TrackingAutoStart.maybeStart(this);
            render();
        });
        button(status,"Device & Capture Controls",false,()->
            startActivity(new Intent(this,MobileDeployActivity.class)));
        button(status,"Refresh tracking status",false,this::render);

        LinearLayout logs=card("TRACKED PLAYERS & HISTORY");
        pair(logs,"Observed players","mini_current",
            "Join & leave log","mini_joins");
        pair(logs,"Past encounters","mini_players",
            "Friend changes","mini_log");

        LinearLayout setup=card("TRACKING SETUP");
        if(quest){
            boolean grant=QuestSafLogWatcher.hasGrant(this);
            boolean full=CompanionSetupState.fullLoggingAcknowledged(this);
            setup.addView(text((grant?"✓ ":"○ ")+"Documents/Logs: "+
                (grant?"read access saved":"folder access needed"),
                13,grant?CompanionStyle.GOOD:CompanionStyle.GOLD,false));
            setup.addView(text((full?"✓ ":"○ ")+"VRChat FULL logging: "+
                (full?"user-confirmed":"not confirmed"),
                13,full?CompanionStyle.GOOD:CompanionStyle.GOLD,false));
            setup.addView(text("FULL logging is confirmed by the user; "+
                "the Companion cannot read VRChat's Debug setting.",
                12,CompanionStyle.MUTED,false));
        }else{
            setup.addView(text(
                "This owner preview displays your PHONE's own history. "+
                "Phone tracking is optional and uses Shizuku when enabled; "+
                "your Quest has separate log-folder permissions.",
                12,CompanionStyle.MUTED,false));
        }
        button(setup,"Guided device setup",false,()->
            startActivity(new Intent(this,CompanionSetupActivity.class)));
    }
    private void showTools(){
        LinearLayout ageCheck=card("AGE CHECK · OFFLINE");
        AgeCheckTool.attach(this,ageCheck,null,null);
        LinearLayout account=card("ACCOUNT & SOCIAL");
        pair(account,"Favorite friend groups","mini_friend_favorites",
            "Find mutuals via a friend","mini_friends");
        pair(account,"Status & presets","mini_presence","Verification","mini_verify");
        pair(account,"Favorite avatars","mini_avatars","Worlds & bookmarks","mini_worlds");
        pair(account,"Recent worlds","mini_recent","Current instance","mini_now");
        pair(account,"Groups","mini_groups","Search everything","mini_find");
        pair(account,"Friend notes","mini_friends","Inbox","mini_inbox");
        LinearLayout app=card("MAIN APP");
        button(app,"Open full Companion",true,()->{
            startActivity(new Intent(this,CompanionActivity.class));finish();
        });
        button(app,"Customize native shortcuts",false,()->go("shortcuts"));
    }
    private void showShortcuts(){
        LinearLayout bar=card("QUICK BAR · UP TO THREE");
        bar.addView(text(QuestDevice.isQuestDevice()?
            "These buttons are saved locally on this Quest and do not affect your phone.":
            "In this phone preview, these buttons share your phone's floating-tools "+
            "shortcut settings. The headset has separate local preferences.",
            11,CompanionStyle.MUTED,false));
        List<String> selected=OverlayPreferences.quickBar(this);
        for(String key:OverlayPreferences.SHORTCUTS){
            boolean isSelected=selected.contains(key);
            button(bar,(isSelected?"✓ ":"+ ")+OverlayPreferences.name(key),
                isSelected,()->{
                List<String> next=new ArrayList<>(OverlayPreferences.quickBar(this));
                if(next.contains(key))next.remove(key);
                else{
                    if(next.size()>=3){toast("Choose up to three");return;}
                    next.add(key);
                }
                OverlayPreferences.setQuickBar(this,next);
                render();
            });
        }
        LinearLayout home=card("HOME SHORTCUTS · UP TO SIX");
        List<String> defaults=OverlayPreferences.shortcuts(this);
        for(String key:OverlayPreferences.SHORTCUTS){
            boolean enabled=defaults.contains(key);
            button(home,(enabled?"✓ ":"+ ")+OverlayPreferences.name(key),
                enabled,()->{
                List<String> next=new ArrayList<>(OverlayPreferences.shortcuts(this));
                if(next.contains(key))next.remove(key);
                else{
                    if(next.size()>=6){toast("Choose up to six");return;}
                    next.add(key);
                }
                try{OverlayPreferences.setShortcuts(this,next);render();}
                catch(Exception e){toast(e.getMessage());}
            });
        }
    }
}
