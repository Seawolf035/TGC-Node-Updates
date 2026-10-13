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
 * Quest-only, normal Android Activity reproducing the floating tools WITHOUT
 * an overlay. Reuses all 30+ OverlayMiniVrcx pages and existing data stores.
 * Phone Companion and FloatingCompanionService are left unchanged.
 */
public final class QuestToolsActivity extends StaffProtectedActivity {
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private final ArrayDeque<String> back=new ArrayDeque<>();
    private String page="home";
    private LinearLayout body,root;
    private ScrollView scroll;
    private TextView oscSummary;
    private Button micButton,liveButton,pauseButton;
    private boolean restoreNativePageAfterPause=false;
    private OverlayMiniVrcx mini;
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
        // This new Activity is not exported and not shown on ordinary phones.
        if(!QuestDevice.isQuestDevice()){finish();return;}
        if(!StaffGate.unlocked(this)){finish();return;}
        getWindow().setSoftInputMode(
            android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
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
                if(isFinishing()||!StaffGate.unlocked(QuestToolsActivity.this))return;
                new AlertDialog.Builder(QuestToolsActivity.this)
                    .setTitle("Confirm VRChat action")
                    .setMessage(prompt)
                    .setNegativeButton("Cancel",null)
                    .setPositiveButton("Confirm",(d,w)->{
                        if(StaffGate.unlocked(QuestToolsActivity.this))task.run();
                    }).show();
            }
            @Override public void feedback(String message){toast(message);}
            @Override public boolean isOscLiveActive(){return QuestOscService.live();}
            @Override public String oscLiveDescription(){return QuestOscService.summary();}
            @Override public void oscStartLive(int intervalSeconds){
                startOsc(QuestOscService.START_LIVE,intervalSeconds);
            }
            @Override public void oscStopLive(){startOsc(QuestOscService.STOP_LIVE,0);}
        });
        render();
    }
    @Override protected void onResume(){
        super.onResume();
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
        if(!QuestDevice.isQuestDevice()||!StaffGate.unlocked(this)){
            toast("Quest staff access required.");return;
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
                if(StaffGate.unlocked(this))task.run();
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
    private void go(String destination){
        if(!StaffGate.unlocked(this)){finish();return;}
        if(page.equals(destination))return;
        back.push(page);page=destination;render();
    }
    private void switchTab(String destination){
        if(page.equals(destination))return;
        back.clear();page=destination;render();
    }
    private void goBack(){
        if(back.isEmpty()){
            if("home".equals(page)){finish();return;}
            page="home";
        }else page=back.pop();
        render();
    }
    @Override public void onBackPressed(){goBack();}
    private boolean isHub(){
        return "home".equals(page)||"social".equals(page)||
            "osc".equals(page)||"tools".equals(page)||
            "shortcuts".equals(page);
    }
    private void render(){
        if(isFinishing()||!StaffGate.unlocked(this))return;
        oscSummary=null;micButton=null;liveButton=null;pauseButton=null;
        if(mini!=null)mini.close();
        root=vertical();
        root.setBackgroundColor(CompanionStyle.BG);
        setContentView(root);
        getWindow().setStatusBarColor(CompanionStyle.BG);
        getWindow().setNavigationBarColor(CompanionStyle.BG);
        LinearLayout heading=new LinearLayout(this);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        heading.setPadding(dp(12),dp(8),dp(12),dp(8));
        root.addView(heading);
        Button backButton=CompanionStyle.button(this,"‹",false);
        heading.addView(backButton,new LinearLayout.LayoutParams(dp(44),dp(40)));
        backButton.setOnClickListener(v->goBack());
        LinearLayout titles=vertical();
        titles.setPadding(dp(9),0,0,0);
        heading.addView(titles,new LinearLayout.LayoutParams(0,-2,1));
        titles.addView(text("TGC STAFF COMPANION",11,CompanionStyle.ACCENT,true));
        titles.addView(text(isHub()?"Quest Tools":
            "Quest Tools · "+page.replace("mini_","").replace('_',' '),
            19,CompanionStyle.TEXT,true));
        LinearLayout tabs=new LinearLayout(this);
        root.addView(tabs);
        String[][] values={{"Home","home"},{"Social","social"},
            {"OSC","osc"},{"Tools","tools"}};
        for(String[] v:values){
            Button tab=CompanionStyle.button(this,v[0],page.equals(v[1]));
            tab.setTextSize(12);
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(42),1f);
            p.setMargins(dp(2),0,dp(2),dp(2));
            tabs.addView(tab,p);
            tab.setOnClickListener(view->switchTab(v[1]));
        }
        // Pin the configurable three-action bar beneath the main Quest tabs.
        // It must not scroll with page content; the ordinary Android floater is unchanged.
        if(isHub() && !"shortcuts".equals(page)){
            List<String> bar=OverlayPreferences.quickBar(this);
            if(!bar.isEmpty()){
                LinearLayout quick=new LinearLayout(this);
                quick.setPadding(dp(12),dp(2),dp(12),dp(2));
                root.addView(quick,new LinearLayout.LayoutParams(-1,-2));
                for(String key:bar){
                    Button q=CompanionStyle.button(this,
                        OverlayPreferences.name(key),false);
                    q.setTextSize(11);
                    LinearLayout.LayoutParams p=
                        new LinearLayout.LayoutParams(0,dp(37),1f);
                    p.setMargins(dp(2),0,dp(2),dp(4));
                    quick.addView(q,p);
                    q.setOnClickListener(v->openShortcut(key));
                }
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
        LinearLayout info=card("QUEST · WITHOUT FLOATING WINDOWS");
        info.addView(text(
            "Native tools work from the Quest system's Android app view. "+
            "No overlay permission or floating bubble is required. "+
            "VRChat OSC requires Quest VRChat to accept local OSC packets; "+
            "system multitasking may suspend VRChat or other apps.",
            12,CompanionStyle.MUTED,false));
        LinearLayout links=card("YOUR QUICK TOOLS");
        List<String> shortcuts=OverlayPreferences.shortcuts(this);
        for(int i=0;i<shortcuts.size();i+=2){
            if(i+1<shortcuts.size())pair(links,
                OverlayPreferences.name(shortcuts.get(i)),OverlayPreferences.destination(shortcuts.get(i)),
                OverlayPreferences.name(shortcuts.get(i+1)),OverlayPreferences.destination(shortcuts.get(i+1)));
            else{
                String key=shortcuts.get(i);
                button(links,OverlayPreferences.name(key),false,()->openShortcut(key));
            }
        }
        button(links,"Customize shortcuts",false,()->go("shortcuts"));
        LinearLayout social=card("VRCHAT");
        pair(social,"Friends","mini_friends","Inbox","mini_inbox");
        pair(social,"Current instance","mini_now","Worlds","mini_worlds");
        pair(social,"Groups","mini_groups","Avatar favorites","mini_avatars");
        pair(social,"Player history","mini_players","Verification","mini_verify");
        LinearLayout osc=card("OSC");
        osc.addView(text(QuestOscService.summary(),13,CompanionStyle.ACCENT,false));
        button(osc,"Open native OSC controls",true,()->switchTab("osc"));
        button(osc,"Return to Companion",false,()->finish());
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
        LinearLayout friends=card("FRIENDS & NOTIFICATIONS");
        pair(friends,"Friends & locations","mini_friends","Friend log","mini_log");
        pair(friends,"All notifications","mini_inbox","Activity feed","mini_activity");
        pair(friends,"Recent players","mini_players","Find anyone","mini_find");
        pair(friends,"Groups","mini_groups","My status","mini_presence");
    }
    private void runOscSend(String value){
        if(!StaffGate.unlocked(this))return;
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
        LinearLayout live=card("LIVE CHATBOX · QUEST NATIVE");
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
    private void showTools(){
        LinearLayout account=card("ACCOUNT & SOCIAL");
        pair(account,"Status & presets","mini_presence","Verification","mini_verify");
        pair(account,"Favorite avatars","mini_avatars","Worlds & bookmarks","mini_worlds");
        pair(account,"Recent worlds","mini_recent","Current instance","mini_now");
        pair(account,"Groups","mini_groups","Search everything","mini_find");
        pair(account,"Friend notes","mini_friends","Inbox","mini_inbox");
        LinearLayout logs=card("LOCAL ACTIVITY");
        pair(logs,"Observed players","mini_current","Join & leave log","mini_joins");
        pair(logs,"Player history","mini_players","Friend changes","mini_log");
        logs.addView(text(
            "REQUIRED: In VRChat, open Settings → Debug → Logging and select FULL. "+
            "Quest tracking reads Documents/Logs with permission. "+
            "Start tracking before joining an instance; new joins appear after they happen.",
            12,CompanionStyle.GOLD,false));
        Button tracking=button(logs,"Open tracking controls",false,()->
            startActivity(new Intent(this,MobileDeployActivity.class)));
        LinearLayout app=card("MAIN APP");
        button(app,"Open full Companion",true,()->{
            startActivity(new Intent(this,CompanionActivity.class));finish();
        });
        button(app,"Customize native shortcuts",false,()->go("shortcuts"));
    }
    private void showShortcuts(){
        LinearLayout bar=card("QUICK BAR · UP TO THREE");
        bar.addView(text("These are the same saved shortcuts used by the phone "+
            "floater. Changes here only apply on this Quest device.",
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
