package club.thegamerclub.nodelab;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.graphics.drawable.GradientDrawable;
import android.view.inputmethod.InputMethodManager;
import java.util.ArrayDeque;
import android.widget.Toast;
import androidx.core.content.ContextCompat;
import org.json.JSONArray;
import org.json.JSONObject;
import rikka.shizuku.Shizuku;
import java.text.DateFormat;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Optional Mini VRCX window. Uses the exact Companion application icon.
 * The only VRChat log reader is MobileNodeService: this service reads existing
 * local history and starts/stops THAT service using its existing actions.
 */
public final class FloatingCompanionService extends Service {
    public static final String ACTION_START="club.thegamerclub.nodelab.OVERLAY_START";
    public static final String ACTION_STOP="club.thegamerclub.nodelab.OVERLAY_STOP";
    private static final String CHANNEL="tgc_companion_overlay";
    private static final int NOTICE=223;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final ExecutorService verifier=Executors.newSingleThreadExecutor();
    private final ExecutorService friendWorker=Executors.newSingleThreadExecutor();
    private final ExecutorService eventsWorker=Executors.newSingleThreadExecutor();
    private WindowManager wm;
    private WindowManager.LayoutParams panelParams;
    private OverlayMiniVrcx mini;
    private final ArrayDeque<String> backStack=new ArrayDeque<>();
    private FrameLayout bubble;
    private View statusDot;
    private LinearLayout panel, panelContent;
    private WindowManager.LayoutParams bubbleParams;
    private TextView nodeStatus, nodeStats, recentStatus;
    private Button trackingToggle;
    private String selectedTab="home", statusNote="";
    private boolean verifying=false, friendBusy=false, eventBusy=false, pendingStop=false;
    private boolean panelVisible=false;
    private long lastPanelRefresh=0L;

    private final Runnable validate=new Runnable(){
        @Override public void run(){
            if(!StaffGate.unlocked(FloatingCompanionService.this)){stopSelf();return;}
            if(!verifying){
                verifying=true;
                verifier.execute(()->{
                    try{StaffGate.verifyOnline(FloatingCompanionService.this);}
                    catch(Exception ignored){}
                    handler.post(()->{
                        verifying=false;
                        if(!StaffGate.unlocked(FloatingCompanionService.this))stopSelf();
                    });
                });
            }
            handler.postDelayed(this,90_000L);
        }
    };

    private final Runnable friendTick=new Runnable(){
        @Override public void run(){
            if(!StaffGate.unlocked(FloatingCompanionService.this)){stopSelf();return;}
            if(!friendBusy){
                friendBusy=true;
                friendWorker.execute(()->{
                    try{
                        JSONObject session=VRChatSession.load(FloatingCompanionService.this);
                        FriendLogStore.account(session);
                        FriendLogStore.observe(FloatingCompanionService.this,session,
                            VRChatApi.friendSnapshot(FloatingCompanionService.this));
                    }catch(Exception ignored){
                        // Preserve the previous complete snapshot during auth/network failures.
                    }finally{handler.post(()->friendBusy=false);}
                });
            }
            handler.postDelayed(this,150_000L);
        }
    };

    private final Runnable refreshTick=new Runnable(){
        @Override public void run(){
            if(!StaffGate.unlocked(FloatingCompanionService.this)){stopSelf();return;}
            getSharedPreferences("tgc_overlay",0).edit()
                .putLong("service_heartbeat_at",System.currentTimeMillis()).apply();
            refreshStatus();
            if(panelVisible && "home".equals(selectedTab))loadRecentEvents();
            handler.postDelayed(this,12_000L);
        }
    };

    private int dp(float size){return CompanionStyle.dp(this,size);}
    private TextView text(String value,int size,int color,boolean bold){
        return CompanionStyle.text(this,value,size,color,bold);
    }
    @Override public void onCreate(){
        super.onCreate();
        wm=(WindowManager)getSystemService(WINDOW_SERVICE);
        mini=new OverlayMiniVrcx(this,new OverlayMiniVrcx.Host(){
            @Override public void navigate(String page){go(page);}
            @Override public void back(){goBack();}
            @Override public void keyboard(boolean enabled){setOverlayKeyboard(enabled);}
        });
        NotificationManager manager=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        manager.createNotificationChannel(new NotificationChannel(CHANNEL,
            "TGC floating tools",NotificationManager.IMPORTANCE_LOW));
    }
    private Notification notice(){
        PendingIntent open=PendingIntent.getActivity(this,11,
            new Intent(this,CompanionActivity.class),
            PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        PendingIntent close=PendingIntent.getService(this,12,
            new Intent(this,FloatingCompanionService.class).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this,CHANNEL)
            .setSmallIcon(R.drawable.tgc_logo)
            .setContentTitle("TGC floating tools")
            .setContentText("Floating quick tools are enabled")
            .setContentIntent(open).setOngoing(true)
            .addAction(new Notification.Action.Builder(null,"Hide bubble",close).build())
            .build();
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(intent!=null && ACTION_STOP.equals(intent.getAction())){
            stopSelf();return START_NOT_STICKY;
        }
        if(!StaffGate.unlocked(this)||!Settings.canDrawOverlays(this)){
            stopSelf();return START_NOT_STICKY;
        }
        try{
            if(Build.VERSION.SDK_INT>=34)
                startForeground(NOTICE,notice(),
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            else startForeground(NOTICE,notice());
            if(bubble==null)showBubble();
        }catch(Exception e){
            stopSelf();return START_NOT_STICKY;
        }
        getSharedPreferences("tgc_overlay",0).edit()
            .putBoolean("running",true)
            .putLong("service_heartbeat_at",System.currentTimeMillis()).apply();
        handler.removeCallbacks(validate);
        handler.postDelayed(validate,1500L);
        handler.removeCallbacks(friendTick);
        handler.postDelayed(friendTick,3000L);
        handler.removeCallbacks(refreshTick);
        handler.postDelayed(refreshTick,600L);
        return START_NOT_STICKY;
    }
    private WindowManager.LayoutParams params(int width,int height){
        WindowManager.LayoutParams p=new WindowManager.LayoutParams(width,height,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,PixelFormat.TRANSLUCENT);
        p.gravity=Gravity.TOP|Gravity.START;
        return p;
    }
    private int screenWidth(){return getResources().getDisplayMetrics().widthPixels;}
    private int screenHeight(){return getResources().getDisplayMetrics().heightPixels;}

    private void showBubble(){
        FrameLayout circle=new FrameLayout(this);
        circle.setContentDescription("TGC Companion. Tap to expand, hold and drag to move.");
        GradientDrawable ring=new GradientDrawable();
        ring.setShape(GradientDrawable.OVAL);
        ring.setColor(CompanionStyle.SURFACE);
        ring.setStroke(dp(2),CompanionStyle.ACCENT);
        circle.setBackground(ring);
        circle.setElevation(dp(9));
        ImageView logo=new ImageView(this);
        // The actual app icon, not substitute text or an unrelated image.
        logo.setImageResource(R.drawable.tgc_logo);
        logo.setScaleType(ImageView.ScaleType.CENTER_CROP);
        // Clip the app's square icon to a true circle; avoids the square corners
        // visible in the original overlay screenshot while retaining the logo.
        GradientDrawable disk=new GradientDrawable();
        disk.setShape(GradientDrawable.OVAL);
        disk.setColor(CompanionStyle.SURFACE);
        logo.setBackground(disk);
        logo.setClipToOutline(true);
        FrameLayout.LayoutParams imageParams=new FrameLayout.LayoutParams(dp(48),dp(48),
            Gravity.CENTER);
        circle.addView(logo,imageParams);
        View led=new View(this);
        led.setBackground(CompanionStyle.shape(this,CompanionStyle.GOLD,10,CompanionStyle.SURFACE));
        FrameLayout.LayoutParams indicator=new FrameLayout.LayoutParams(dp(13),dp(13),
            Gravity.BOTTOM|Gravity.END);
        indicator.setMargins(0,0,dp(2),dp(2));
        circle.addView(led,indicator);
        bubble=circle;statusDot=led;
        bubbleParams=params(dp(60),dp(60));
        bubbleParams.x=Math.max(0,Math.min(screenWidth()-dp(60),
            getSharedPreferences("tgc_overlay",0).getInt("x",screenWidth()-dp(82))));
        bubbleParams.y=Math.max(dp(25),Math.min(screenHeight()-dp(82),
            getSharedPreferences("tgc_overlay",0).getInt("y",dp(165))));
        circle.setOnTouchListener(new View.OnTouchListener(){
            private float startX,startY;
            private int initialX,initialY;
            private boolean moved;
            @Override public boolean onTouch(View v,MotionEvent event){
                switch(event.getActionMasked()){
                    case MotionEvent.ACTION_DOWN:
                        startX=event.getRawX();startY=event.getRawY();
                        initialX=bubbleParams.x;initialY=bubbleParams.y;
                        moved=false;return true;
                    case MotionEvent.ACTION_MOVE:
                        int dx=Math.round(event.getRawX()-startX);
                        int dy=Math.round(event.getRawY()-startY);
                        if(Math.abs(dx)>dp(7)||Math.abs(dy)>dp(7))moved=true;
                        if(moved){
                            bubbleParams.x=Math.max(0,Math.min(screenWidth()-dp(60),initialX+dx));
                            bubbleParams.y=Math.max(dp(25),Math.min(screenHeight()-dp(82),initialY+dy));
                            if(bubble!=null)wm.updateViewLayout(bubble,bubbleParams);
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        if(moved){
                            getSharedPreferences("tgc_overlay",0).edit()
                                .putInt("x",bubbleParams.x).putInt("y",bubbleParams.y).apply();
                            dismissPanel();
                        }else togglePanel();
                        return true;
                    default:return true;
                }
            }
        });
        wm.addView(circle,bubbleParams);
    }
    private LinearLayout vertical(){
        LinearLayout out=new LinearLayout(this);
        out.setOrientation(LinearLayout.VERTICAL);
        return out;
    }
    private void inset(LinearLayout root,int amount){
        root.setPadding(dp(amount),dp(amount),dp(amount),dp(amount));
    }
    private LinearLayout panelCard(LinearLayout parent){
        LinearLayout box=vertical();
        inset(box,12);
        box.setBackground(CompanionStyle.shape(this,CompanionStyle.SURFACE,15,
            CompanionStyle.BORDER));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);
        p.setMargins(0,0,0,dp(10));
        parent.addView(box,p);
        return box;
    }
    private void section(LinearLayout parent,String title){
        parent.addView(CompanionStyle.section(this,title));
    }
    private Button action(LinearLayout parent,String label,boolean primary,Runnable runnable){
        Button b=CompanionStyle.button(this,label,primary);
        parent.addView(b,CompanionStyle.buttonParams(this));
        b.setOnClickListener(v->runnable.run());
        return b;
    }
    private void paired(LinearLayout parent,String left,String right,
                        Runnable leftClick,Runnable rightClick){
        LinearLayout line=new LinearLayout(this);
        line.setOrientation(LinearLayout.HORIZONTAL);
        parent.addView(line,new LinearLayout.LayoutParams(-1,-2));
        smallAction(line,left,leftClick);
        smallAction(line,right,rightClick);
    }
    private void smallAction(LinearLayout parent,String label,Runnable click){
        Button b=CompanionStyle.button(this,label,false);
        b.setTextSize(12);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(43),1);
        p.setMargins(dp(2),dp(2),dp(2),dp(8));
        parent.addView(b,p);
        b.setOnClickListener(v->click.run());
    }
    private void togglePanel(){
        if(panel!=null){dismissPanel();return;}
        if(!StaffGate.unlocked(this)){stopSelf();return;}
        LinearLayout container=vertical();
        container.setElevation(dp(14));
        container.setBackground(CompanionStyle.shape(this,CompanionStyle.BG,19,
            CompanionStyle.BORDER));
        panel=container;panelVisible=true;

        LinearLayout header=vertical();
        header.setPadding(dp(14),dp(13),dp(14),dp(10));
        header.setBackground(CompanionStyle.gradient(this,0xFF20425B,CompanionStyle.SURFACE,19));
        container.addView(header);
        LinearLayout bar=new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(bar);
        ImageView mark=new ImageView(this);
        mark.setImageResource(R.drawable.tgc_logo);
        mark.setScaleType(ImageView.ScaleType.FIT_CENTER);
        bar.addView(mark,new LinearLayout.LayoutParams(dp(40),dp(40)));
        LinearLayout labels=vertical();
        labels.setPadding(dp(10),0,0,0);
        bar.addView(labels,new LinearLayout.LayoutParams(0,-2,1));
        labels.addView(text("TGC STAFF COMPANION",10,CompanionStyle.ACCENT,true));
        labels.addView(text("Floating tools",19,CompanionStyle.TEXT,true));
        Button collapse=CompanionStyle.button(this,"−",false);
        collapse.setContentDescription("Collapse overlay");
        bar.addView(collapse,new LinearLayout.LayoutParams(dp(43),dp(39)));
        collapse.setOnClickListener(v->dismissPanel());

        LinearLayout tabs=new LinearLayout(this);
        tabs.setPadding(dp(12),dp(7),dp(12),dp(3));
        container.addView(tabs);
        tab(tabs,"Home","home");
        tab(tabs,"Social","social");
        tab(tabs,"Tools","tools");

        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.setClipToPadding(false);
        container.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        panelContent=vertical();
        panelContent.setPadding(dp(13),dp(4),dp(13),dp(8));
        scroll.addView(panelContent);
        buildTab();

        LinearLayout bottom=new LinearLayout(this);
        bottom.setPadding(dp(11),dp(5),dp(11),dp(9));
        container.addView(bottom);
        smallAction(bottom,"Full Companion",()->open(CompanionActivity.class,null,null));
        smallAction(bottom,"Hide bubble",this::stopSelf);

        int width=Math.min(dp(352),screenWidth()-dp(20));
        int height=Math.min(dp(555),screenHeight()-dp(118));
        WindowManager.LayoutParams p=params(width,height);
        p.x=Math.max(dp(8),Math.min(screenWidth()-width-dp(8),bubbleParams.x));
        int possibleTop=bubbleParams.y+dp(65);
        if(possibleTop+height>screenHeight()-dp(20))possibleTop=bubbleParams.y-height-dp(7);
        p.y=Math.max(dp(25),Math.min(screenHeight()-height-dp(25),possibleTop));
        try{
            panelParams=p;
            wm.addView(container,p);
            refreshStatus();loadRecentEvents();
        }catch(Exception e){
            dismissPanel();
            Toast.makeText(this,"Unable to display overlay panel",Toast.LENGTH_SHORT).show();
        }
    }
    private void tab(LinearLayout container,String title,String key){
        Button b=CompanionStyle.button(this,title,key.equals(selectedTab));
        b.setTextSize(12);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(39),1);
        p.setMargins(dp(2),0,dp(2),0);
        container.addView(b,p);
        b.setOnClickListener(v->{
            if(key.equals(selectedTab))return;
            selectedTab=key;pendingStop=false;backStack.clear();
            dismissPanel();togglePanel();
        });
    }
    private void buildTab(){
        if(panelContent==null)return;
        panelContent.removeAllViews();
        nodeStatus=null;nodeStats=null;trackingToggle=null;recentStatus=null;
        switch(selectedTab){
            case "social":showSocial();break;
            case "tools":showTools();break;
            case "home":showHome();break;
            default:mini.show(panelContent,selectedTab);break;
        }
    }
    private void go(String destination){
        if(!StaffGate.unlocked(this)){stopSelf();return;}
        if(selectedTab.equals(destination))return;
        backStack.push(selectedTab);
        selectedTab=destination;
        pendingStop=false;
        buildTab();
    }
    private void goBack(){
        selectedTab=backStack.isEmpty()?"home":backStack.pop();
        pendingStop=false;
        buildTab();
    }
    private void setOverlayKeyboard(boolean focusable){
        if(panel==null||panelParams==null)return;
        if(focusable){
            panelParams.flags&=~WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
            panelParams.softInputMode=WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE;
        }else{
            panelParams.flags|=WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
            panelParams.softInputMode=WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN;
            InputMethodManager imm=(InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);
            if(imm!=null)imm.hideSoftInputFromWindow(panel.getWindowToken(),0);
        }
        try{wm.updateViewLayout(panel,panelParams);}catch(Exception ignored){}
        if(focusable){
            handler.postDelayed(()->{
                if(panel==null)return;
                View focus=panel.findFocus();
                if(focus!=null){
                    InputMethodManager imm=(InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);
                    if(imm!=null)imm.showSoftInput(focus,InputMethodManager.SHOW_IMPLICIT);
                }
            },110L);
        }
    }
    private void showHome(){
        section(panelContent,"TRACKING");
        LinearLayout node=panelCard(panelContent);
        nodeStatus=text("Checking tracking…",15,CompanionStyle.TEXT,true);
        node.addView(nodeStatus);
        nodeStats=text("Reading local status…",12,CompanionStyle.MUTED,false);
        nodeStats.setPadding(0,dp(8),0,dp(5));
        node.addView(nodeStats);
        trackingToggle=action(node,"Start tracking",true,this::toggleTracking);
        paired(node,"Tracking setup","Refresh",()->
            open(MobileDeployActivity.class,null,null),()->{refreshStatus();loadRecentEvents();});

        section(panelContent,"RECENTLY ENCOUNTERED");
        LinearLayout recent=panelCard(panelContent);
        recentStatus=text("Loading captured player events…",12,CompanionStyle.MUTED,false);
        recent.addView(recentStatus);
        // A small, bounded dynamic region. No second log parser or tracker is created.
        recentRows=vertical();recent.addView(recentRows);
        action(recent,"Open full player log",false,()->go("mini_players"));

        section(panelContent,"QUICK ACCESS");
        LinearLayout quick=panelCard(panelContent);
        paired(quick,"Friends locations","Friend log",
            ()->go("mini_friends"),()->go("mini_log"));
        paired(quick,"Inbox","Activity feed",
            ()->mini.inbox("all"),()->go("mini_activity"));
    }
    private LinearLayout recentRows;
    private void showSocial(){
        section(panelContent,"FRIENDS");
        LinearLayout friends=panelCard(panelContent);
        action(friends,"Friends & locations",true,()->go("mini_friends"));
        paired(friends,"Friend log","Activity feed",
            ()->go("mini_log"),()->go("mini_activity"));
        section(panelContent,"INBOX");
        LinearLayout communication=panelCard(panelContent);
        paired(communication,"All notifications","Invitations",
            ()->mini.inbox("all"),()->mini.inbox("invite"));
        paired(communication,"Friend requests","Search players",
            ()->mini.inbox("friend"),()->mini.findUser());
        section(panelContent,"PLAYER TOOLS");
        LinearLayout players=panelCard(panelContent);
        action(players,"Captured player log & profiles",false,
            ()->go("mini_players"));
        action(players,"My VRChat groups",false,()->go("mini_groups"));
    }
    private void showTools(){
        section(panelContent,"ACCOUNT — NO APP SWITCH");
        LinearLayout personal=panelCard(panelContent);
        paired(personal,"Status & presets","Favorite avatars",
            ()->go("mini_presence"),()->go("mini_avatars"));
        paired(personal,"Saved worlds","My groups",
            ()->go("mini_worlds"),()->go("mini_groups"));
        paired(personal,"Search players","Friends & locations",
            ()->mini.findUser(),()->go("mini_friends"));
        section(panelContent,"PLAYER INFORMATION");
        LinearLayout player=panelCard(panelContent);
        paired(player,"Player log","Friend log",
            ()->go("mini_players"),()->go("mini_log"));
        section(panelContent,"NODE TRACKING");
        LinearLayout node=panelCard(panelContent);
        TextView info=text("Use Home to start or stop tracking. Captures and relay uploads continue independently when this panel closes.",
            12,CompanionStyle.MUTED,false);
        node.addView(info);
        action(node,"Tracking dashboard",false,()->{
            backStack.clear();selectedTab="home";buildTab();refreshStatus();
        });
    }
    private void open(Class<?> destination,String parameter,String value){
        if(!StaffGate.unlocked(this)){stopSelf();return;}
        Intent intent=new Intent(this,destination).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if(parameter!=null)intent.putExtra(parameter,value);
        try{startActivity(intent);dismissPanel();}
        catch(Exception error){
            Toast.makeText(this,"Could not open "+destination.getSimpleName(),
                Toast.LENGTH_SHORT).show();
        }
    }
    private boolean nodeRunning(){
        android.content.SharedPreferences pref=getSharedPreferences("tgc_mobile_node_status",0);
        long heartbeat=pref.getLong("service_heartbeat_at",0);
        return pref.getBoolean("running",false) && heartbeat>0 &&
            System.currentTimeMillis()-heartbeat<90_000L;
    }
    private void toggleTracking(){
        if(!StaffGate.unlocked(this)){stopSelf();return;}
        if(nodeRunning()){
            if(!pendingStop){
                pendingStop=true;
                statusNote="Tap again to confirm stopping tracking.";
                if(trackingToggle!=null)trackingToggle.setText("Confirm stop tracking");
                handler.postDelayed(()->{
                    if(pendingStop){pendingStop=false;refreshStatus();}
                },8000L);
                return;
            }
            pendingStop=false;
            try{
                startService(new Intent(this,MobileNodeService.class).setAction(MobileNodeService.STOP));
                statusNote="Stopping tracking…";
            }catch(Exception error){statusNote="Could not stop tracking. Check tracking settings.";}
        }else{
            pendingStop=false;
            try{
                if(MobileCredentials.load(this)==null){
                    statusNote="Link this phone in Tracking first.";
                    open(MobileDeployActivity.class,null,null);return;
                }
                if(!Shizuku.pingBinder()||
                    Shizuku.checkSelfPermission()!=PackageManager.PERMISSION_GRANTED){
                    statusNote="Tracking needs permission. Open Tracking settings.";
                    if(nodeStatus!=null)nodeStatus.setText(statusNote);
                    // Permission requests must come from the existing Activity, never this service.
                    open(MobileDeployActivity.class,null,null);return;
                }
                if(Build.VERSION.SDK_INT>=33 &&
                    checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                        !=PackageManager.PERMISSION_GRANTED){
                    statusNote="Enable Companion notifications in Android settings for reliable tracking.";
                    open(MobileDeployActivity.class,null,null);return;
                }
                ContextCompat.startForegroundService(this,
                    new Intent(this,MobileNodeService.class).setAction(MobileNodeService.START));
                statusNote="Starting tracking…";
            }catch(Exception error){
                statusNote="Unable to start: "+error.getClass().getSimpleName();
            }
        }
        refreshStatus();
        handler.postDelayed(this::refreshStatus,2200L);
    }
    private void refreshStatus(){
        if(statusDot!=null){
            statusDot.setBackground(CompanionStyle.shape(this,
                nodeRunning()?CompanionStyle.GOOD:CompanionStyle.GOLD,12,CompanionStyle.SURFACE));
        }
        if(nodeStatus==null)return;
        boolean live=nodeRunning();
        android.content.SharedPreferences pref=getSharedPreferences("tgc_mobile_node_status",0);
        String watcher=pref.getString("watcher","");
        String label=live?"● Tracking active":"○ Tracking stopped or reconnecting";
        nodeStatus.setText(label);
        nodeStatus.setTextColor(live?CompanionStyle.GOOD:CompanionStyle.GOLD);
        if(trackingToggle!=null){
            trackingToggle.setText(pendingStop?"Confirm stop tracking":
                live?"Stop tracking":"Start tracking");
        }
        if(nodeStats!=null){
            nodeStats.setText("Players: "+pref.getInt("captured_total",0)+
                "  ·  Sent: "+pref.getInt("uploaded_total",0)+
                "\n"+(watcher.isEmpty()?"Check tracking settings for details":
                    watcher.length()>95?watcher.substring(0,95)+"…":watcher)+
                (statusNote.isEmpty()?"":"\n"+statusNote));
        }
    }
    private void loadRecentEvents(){
        if(!panelVisible||!"home".equals(selectedTab)||eventBusy)return;
        eventBusy=true;
        eventsWorker.execute(()->{
            JSONArray events=new JSONArray();
            int pending=-1;
            try(MobileOutbox db=new MobileOutbox(this)){
                events=db.recentTrackedEvents(5);
                pending=db.count();
            }catch(Exception ignored){}
            final JSONArray entries=events;
            final int queue=pending;
            handler.post(()->{
                eventBusy=false;
                if(!panelVisible||recentRows==null||recentStatus==null||
                    !StaffGate.unlocked(this))return;
                recentRows.removeAllViews();
                recentStatus.setText(queue<0?"Local history unavailable":
                    "Waiting to send: "+queue+
                    (entries.length()==0?" · No recorded encounters yet":""));
                for(int i=0;i<entries.length();i++){
                    JSONObject event=entries.optJSONObject(i);
                    if(event==null)continue;
                    String id=event.optString("user_id","");
                    String name=event.optString("display_name","");
                    if(name.trim().isEmpty())name="VRChat player";
                    if(name.length()>38)name=name.substring(0,38)+"…";
                    final String uid=id;
                    TextView item=text("›  "+name,13,CompanionStyle.TEXT,false);
                    item.setPadding(dp(8),dp(9),dp(8),dp(9));
                    item.setBackground(CompanionStyle.shape(this,CompanionStyle.RAISED,10,0));
                    LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
                    lp.setMargins(0,dp(3),0,dp(3));recentRows.addView(item,lp);
                    item.setOnClickListener(v->{
                        if(uid.matches("usr_[0-9a-fA-F-]{36}"))
                            mini.profile(uid);
                        else go("mini_players");
                    });
                }
                lastPanelRefresh=System.currentTimeMillis();
            });
        });
    }
    private void open(Class<?> destination,String paramA,String valueA,String userId){
        if(!StaffGate.unlocked(this)){stopSelf();return;}
        Intent intent=new Intent(this,destination).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if(paramA!=null)intent.putExtra(paramA,valueA);
        if(userId!=null)intent.putExtra("id",userId);
        try{startActivity(intent);dismissPanel();}
        catch(Exception e){Toast.makeText(this,"Could not open profile",Toast.LENGTH_SHORT).show();}
    }
    private void dismissPanel(){
        panelVisible=false;
        if(panel!=null){
            try{wm.removeView(panel);}catch(Exception ignored){}
        }
        if(mini!=null)mini.close();
        panel=null;panelContent=null;recentRows=null;recentStatus=null;
        panelParams=null;
        nodeStatus=null;nodeStats=null;trackingToggle=null;
    }
    @Override public void onDestroy(){
        handler.removeCallbacks(validate);
        handler.removeCallbacks(friendTick);
        handler.removeCallbacks(refreshTick);
        verifier.shutdownNow();friendWorker.shutdownNow();eventsWorker.shutdownNow();
        dismissPanel();if(mini!=null)mini.shutdown();
        if(bubble!=null){
            try{wm.removeView(bubble);}catch(Exception ignored){}
            bubble=null;statusDot=null;
        }
        getSharedPreferences("tgc_overlay",0).edit()
            .putBoolean("running",false).putLong("service_heartbeat_at",0L).apply();
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent){return null;}
}
