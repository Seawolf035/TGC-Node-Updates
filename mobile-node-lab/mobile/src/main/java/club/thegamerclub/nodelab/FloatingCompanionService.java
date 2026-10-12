package club.thegamerclub.nodelab;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.res.Configuration;
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
import java.util.ArrayList;
import java.util.List;
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
    public static final String ACTION_RESTORE="club.thegamerclub.nodelab.OVERLAY_RESTORE";
    private static final String CHANNEL="tgc_companion_overlay";
    private static final int NOTICE=223;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final ExecutorService verifier=Executors.newSingleThreadExecutor();
    private final ExecutorService friendWorker=Executors.newSingleThreadExecutor();
    private final ExecutorService eventsWorker=Executors.newSingleThreadExecutor();
    private final ExecutorService presenceWorker=Executors.newSingleThreadExecutor();
    private WindowManager wm;
    private WindowManager.LayoutParams panelParams;
    private OverlayMiniVrcx mini;
    private final ArrayDeque<String> backStack=new ArrayDeque<>();
    private FrameLayout bubble;
    private View statusDot;
    private LinearLayout panel, panelContent;
    private ScrollView panelScroller;
    private WindowManager.LayoutParams bubbleParams;
    private TextView nodeStatus, nodeStats, recentStatus, presenceLabel,presenceActionLabel;
    private String presenceStatus="";
    private String presenceDescription="VRChat: checking status…";
    private boolean presenceBusy=false;
    private long presenceChangedAt=0L;
    private long lastPresenceFetch=0L,lastFriendFetch=0L;
    private Button trackingToggle;
    private String selectedTab="home", statusNote="";
    private boolean verifying=false, friendBusy=false, eventBusy=false, pendingStop=false;
    private boolean panelVisible=false;
    private boolean restoring=false,stoppingExplicitly=false;
    private int restoreAttempts=0;
    private static final int MAX_RESTORE_ATTEMPTS=3;
    private final Runnable[] restoreRunnable=new Runnable[1];
    private long lastPanelRefresh=0L;
    // Live Chatbox is OFF by default and deliberately not persisted as "on".
    // It uses this already-authorized floating foreground service while its
    // bubble exists, even when the panel is collapsed.
    private volatile boolean oscLiveActive=false;
    private boolean oscLiveBusy=false;
    private int oscLiveIntervalSeconds=5;
    private volatile int oscLiveGeneration=0;
    private String oscLiveAccountId="",oscLiveLastError="",oscLiveLastText="";
    private long oscLiveLastAt=0L;
    private volatile long oscLiveStartedElapsed=0L;
    private final Runnable oscLiveTick=new Runnable(){
        @Override public void run(){
            if(!oscLiveActive)return;
            if(!StaffGate.unlocked(FloatingCompanionService.this)||
                !OverlayPreferences.desired(FloatingCompanionService.this)||
                !QuestDevice.canRunOverlay(FloatingCompanionService.this)){
                stopOscLive();
                return;
            }
            // A manual chat message wins; allow it to stay visible long enough
            // to be read before resuming the periodic status line.
            if(VRChatOscChat.sinceManualChatMs()<20_000L){
                handler.postDelayed(this,5_000L);
                return;
            }
            if(oscLiveBusy){
                handler.postDelayed(this,2_500L);
                return;
            }
            oscLiveBusy=true;
            final int epoch=oscLiveGeneration;
            final String owner=oscLiveAccountId;
            eventsWorker.execute(()->{
                String error=null,value=null;
                boolean accountChanged=false;
                try{
                    String account=VRChatSession.load(FloatingCompanionService.this)
                        .optString("userId","");
                    if(!owner.equals(account)||
                        !account.matches("usr_[0-9a-fA-F-]{36}")){
                        accountChanged=true;
                    }else if(!oscLiveActive||epoch!=oscLiveGeneration){
                        return;
                    }else{
                        // Refresh interval and rotation interval are independent.
                        int count=OscControlStore.liveSlotCount(FloatingCompanionService.this);
                        int slot=0;
                        if(count>1){
                            long elapsed=Math.max(0L,
                                android.os.SystemClock.elapsedRealtime()-oscLiveStartedElapsed);
                            int interval=OscControlStore.rotationInterval(FloatingCompanionService.this);
                            slot=(int)((elapsed/(1000L*interval))%count);
                        }
                        value=OscControlStore.liveChatboxAt(FloatingCompanionService.this,slot);
                        if(!oscLiveActive||epoch!=oscLiveGeneration)return;
                        VRChatOscChat.sendLive(value);
                    }
                }catch(Exception e){
                    error=e.getMessage()==null?"Unable to send OSC status":e.getMessage();
                }
                final String problem=error,output=value;
                final boolean invalidAccount=accountChanged;
                handler.post(()->{
                    if(epoch!=oscLiveGeneration)return;
                    oscLiveBusy=false;
                    if(invalidAccount){
                        oscLiveLastError="VRChat account changed. Live Chatbox stopped.";
                        stopOscLive();
                        return;
                    }
                    if(problem!=null)oscLiveLastError=problem;
                    else if(output!=null){
                        oscLiveLastError="";
                        oscLiveLastText=output;
                        oscLiveLastAt=System.currentTimeMillis();
                    }
                    if(oscLiveActive)
                        handler.postDelayed(oscLiveTick,
                            oscLiveIntervalSeconds*1000L);
                });
            });
        }
    };
    private void startOscLive(int seconds){
        if(!StaffGate.unlocked(this)||!OverlayPreferences.desired(this)||
           !QuestDevice.canRunOverlay(this)){
            Toast.makeText(this,"Unlock staff access before starting OSC",
                Toast.LENGTH_LONG).show();
            return;
        }
        if(seconds!=5&&seconds!=10&&seconds!=15&&seconds!=30)
            seconds=10;
        try{
            String account=VRChatSession.load(this).optString("userId","");
            if(!account.matches("usr_[0-9a-fA-F-]{36}"))
                throw new SecurityException("Sign in to personal VRChat first.");
            OscControlStore.statusLine(this); // Preserve legacy default chatbox.
            // Named profiles are validated before they are saved.
            stopOscLive();
            oscLiveAccountId=account;
            oscLiveStartedElapsed=android.os.SystemClock.elapsedRealtime();
            oscLiveIntervalSeconds=seconds;
            oscLiveActive=true;
            oscLiveLastError="";
            handler.post(oscLiveTick);
            updateForegroundText("Live OSC Chatbox · every "+seconds+" seconds");
        }catch(Exception e){
            Toast.makeText(this,"Live Chatbox unavailable: "+e.getMessage(),
                Toast.LENGTH_LONG).show();
        }
    }
    private void stopOscLive(){
        oscLiveActive=false;
        oscLiveGeneration++;
        handler.removeCallbacks(oscLiveTick);
        oscLiveBusy=false;
        if(!stoppingExplicitly&&bubble!=null)
            updateForegroundText("Floating tools ready");
    }
    private String oscLiveSummary(){
        if(!oscLiveActive)return "OFF · Only sends when you press Send";
        String label="ON · Refresh every "+oscLiveIntervalSeconds+" seconds";
        try{
            int slots=OscControlStore.liveSlotCount(this);
            if(slots>1)
                label+=" · Rotating "+slots+" chatboxes every "+
                    OscControlStore.rotationInterval(this)+"s";
        }catch(Exception ignored){}
        if(oscLiveLastAt>0)
            label+=" · Last sent "+DateFormat.getTimeInstance(
                DateFormat.SHORT).format(new Date(oscLiveLastAt));
        if(!oscLiveLastError.isEmpty())label+=" · Last error: "+oscLiveLastError;
        return label;
    }


    private final Runnable validate=new Runnable(){
        @Override public void run(){
            if(!QuestDevice.canRunOverlay(FloatingCompanionService.this)){
                stopRequestedByUser();return;
            }
            if(!StaffGate.unlocked(FloatingCompanionService.this)){
                stopSelf();return;
            }
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
                lastFriendFetch=System.currentTimeMillis();
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
            handler.postDelayed(this,panelVisible?150_000L:300_000L);
        }
    };

    /** VRChat account polling is independent of Shizuku and the Mobile Node. */
    private final Runnable presenceTick=new Runnable(){
        @Override public void run(){
            if(!StaffGate.unlocked(FloatingCompanionService.this)){stopSelf();return;}
            if(!presenceBusy){
                presenceBusy=true;
                lastPresenceFetch=System.currentTimeMillis();
                final long requestStarted=System.currentTimeMillis();
                presenceWorker.execute(()->{
                    String value="",description="VRChat: unavailable";
                    try{
                        JSONObject session=VRChatSession.load(FloatingCompanionService.this);
                        if(session.optString("auth","").isEmpty()||
                            session.optBoolean("needs2fa",false)){
                            description="VRChat: not signed in";
                        }else{
                            JSONObject user=VRChatApi.currentUser(FloatingCompanionService.this);
                            value=user.optString("status","");
                            description="VRChat: "+VRChatPresenceIndicator.label(value);
                        }
                    }catch(Exception ignored){
                        description="VRChat: unavailable";
                    }
                    final String updated=value,display=description;
                    handler.post(()->{
                        presenceBusy=false;
                        if(!StaffGate.unlocked(FloatingCompanionService.this)||
                            requestStarted<presenceChangedAt)return;
                        presenceStatus=updated;
                        presenceDescription=display;
                        refreshPresenceDot();
                    });
                });
            }
            // Avoid excessive VRChat account API calls while a game is running.
            handler.postDelayed(this,panelVisible?75_000L:180_000L);
        }
    };
    private final Runnable refreshTick=new Runnable(){
        @Override public void run(){
            if(!QuestDevice.canRunOverlay(FloatingCompanionService.this)){
                stopRequestedByUser();return;
            }
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
            @Override public void onPresenceChanged(String updated){
                presenceChangedAt=System.currentTimeMillis();
                presenceStatus=updated;
                presenceDescription="VRChat: "+VRChatPresenceIndicator.label(updated);
                refreshPresenceDot();
            }
            @Override public boolean isOscLiveActive(){return oscLiveActive;}
            @Override public String oscLiveDescription(){return oscLiveSummary();}
            @Override public void oscStartLive(int seconds){startOscLive(seconds);}
            @Override public void oscStopLive(){stopOscLive();}
            @Override public void scrollToTop(){
                if(panelScroller!=null){
                    final ScrollView target=panelScroller;
                    target.post(()->{
                        if(panelScroller==target)target.smoothScrollTo(0,0);
                    });
                }
            }
        });
        NotificationManager manager=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        manager.createNotificationChannel(new NotificationChannel(CHANNEL,
            "TGC floating tools",NotificationManager.IMPORTANCE_LOW));
    }
    private Notification notice(){return notice("Floating quick tools are enabled");}
    private Notification notice(String detail){
        PendingIntent open=PendingIntent.getActivity(this,11,
            new Intent(this,CompanionActivity.class),
            PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        PendingIntent close=PendingIntent.getService(this,12,
            new Intent(this,FloatingCompanionService.class).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this,CHANNEL)
            .setSmallIcon(R.drawable.tgc_logo)
            .setContentTitle("TGC floating tools")
            .setContentText(detail)
            .setContentIntent(open).setOngoing(true)
            .addAction(new Notification.Action.Builder(null,"Hide bubble",close).build())
            .build();
    }
    private boolean startVisible(){
        if(!QuestDevice.canRunOverlay(this)||
           !StaffGate.unlocked(this)||!Settings.canDrawOverlays(this))return false;
        try{
            if(bubble==null)showBubble();
        }catch(Exception e){return false;}
        getSharedPreferences("tgc_overlay",0).edit()
            .putBoolean("running",true)
            .putLong("service_heartbeat_at",System.currentTimeMillis()).apply();
        restoring=false;restoreAttempts=0;
        scheduleTasks();
        updateForegroundText("Floating tools ready");
        return true;
    }
    private void scheduleTasks(){
        handler.removeCallbacks(validate);
        handler.postDelayed(validate,1500L);
        handler.removeCallbacks(friendTick);
        handler.postDelayed(friendTick,2500L);
        handler.removeCallbacks(refreshTick);
        handler.postDelayed(refreshTick,600L);
        handler.removeCallbacks(presenceTick);
        handler.post(presenceTick);
    }
    private void updateForegroundText(String description){
        try{
            NotificationManager m=getSystemService(NotificationManager.class);
            if(m!=null)m.notify(NOTICE,notice(description));
        }catch(Exception ignored){}
    }
    private void stopRequestedByUser(){
        stoppingExplicitly=true;
        stopOscLive();
        OverlayPreferences.setDesired(this,false);
        handler.removeCallbacks(validate);
        handler.removeCallbacks(friendTick);
        handler.removeCallbacks(refreshTick);
        handler.removeCallbacks(presenceTick);
        if(restoreRunnable[0]!=null)handler.removeCallbacks(restoreRunnable[0]);
        stopSelf();
    }
    /** A system-led recovery must verify access online before showing the bubble. */
    private void recoverAfterInterruption(){
        if(restoring)return;
        if(!QuestDevice.canRunOverlay(this)||
           !OverlayPreferences.desired(this)||!OverlayPreferences.recover(this)||
           !Settings.canDrawOverlays(this)){
            stopSelf();return;
        }
        restoring=true;
        updateForegroundText("Checking staff access before restoring tools");
        restoreRunnable[0]=new Runnable(){
            @Override public void run(){
                if(stoppingExplicitly||
                   !QuestDevice.canRunOverlay(FloatingCompanionService.this)||
                   !OverlayPreferences.desired(FloatingCompanionService.this)||
                   !OverlayPreferences.recover(FloatingCompanionService.this)){
                    restoring=false;stopSelf();return;
                }
                if(StaffGate.unlocked(FloatingCompanionService.this)){
                    restoring=false;
                    if(!startVisible())stopSelf();
                    return;
                }
                if(++restoreAttempts>MAX_RESTORE_ATTEMPTS){
                    restoring=false;
                    // Leave the user's preference alone: they can re-open the
                    // Companion when service startup is allowed and online.
                    stopSelf();return;
                }
                verifier.execute(()->{
                    boolean denied=false;
                    try{StaffGate.verifyOnline(FloatingCompanionService.this);}
                    catch(SecurityException e){denied=true;}
                    catch(Exception ignored){}
                    final boolean rejected=denied;
                    handler.post(()->{
                        if(stoppingExplicitly)return;
                        if(rejected){
                            // Never resurrect an overlay after definite revocation.
                            OverlayPreferences.setDesired(FloatingCompanionService.this,false);
                            restoring=false;stopSelf();return;
                        }
                        if(StaffGate.unlocked(FloatingCompanionService.this)){
                            restoring=false;
                            if(!startVisible())stopSelf();
                        }else{
                            updateForegroundText("Waiting for connection to restore floating tools");
                            handler.postDelayed(this,30_000L);
                        }
                    });
                });
            }
        };
        handler.post(restoreRunnable[0]);
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        String action=intent==null?null:intent.getAction();
        if(ACTION_STOP.equals(action)){
            stopRequestedByUser();
            return START_NOT_STICKY;
        }
        // Enforce Quest consent in the service itself. This covers direct
        // starts, sticky Android restarts and recovery calls alike.
        if(!QuestDevice.canRunOverlay(this)){
            stopRequestedByUser();
            return START_NOT_STICKY;
        }
        final boolean manual=ACTION_START.equals(action);
        if(manual){
            // Only an explicit user action can initially opt into the overlay.
            if(!StaffGate.unlocked(this)||!Settings.canDrawOverlays(this)){
                stopSelf();return START_NOT_STICKY;
            }
            OverlayPreferences.setDesired(this,true);
        }else if(!OverlayPreferences.desired(this)||!OverlayPreferences.recover(this)){
            // Never resurrect a deliberately hidden bubble.
            stopSelf();return START_NOT_STICKY;
        }
        try{
            if(Build.VERSION.SDK_INT>=34)
                startForeground(NOTICE,notice(manual?"Floating tools ready":"Restoring floating tools"),
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            else startForeground(NOTICE,notice("Floating tools ready"));
        }catch(Exception e){
            stopSelf();return START_NOT_STICKY;
        }
        if(manual){
            if(!startVisible())stopSelf();
        }else recoverAfterInterruption();
        // Recovery is available ONLY with the opt-in setting enabled. Android
        // still decides when and whether a killed foreground service restarts.
        return OverlayPreferences.recover(this)?START_STICKY:START_NOT_STICKY;
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
        led.setBackground(CompanionStyle.shape(this,
            VRChatPresenceIndicator.GRAY,10,CompanionStyle.SURFACE));
        FrameLayout.LayoutParams indicator=new FrameLayout.LayoutParams(dp(13),dp(13),
            Gravity.BOTTOM|Gravity.END);
        indicator.setMargins(0,0,dp(2),dp(2));
        circle.addView(led,indicator);
        bubble=circle;statusDot=led;
        refreshPresenceDot();
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
                            snapBubbleToEdge();
                            dismissPanel();
                        }else togglePanel();
                        return true;
                    case MotionEvent.ACTION_CANCEL:
                        if(moved){snapBubbleToEdge();dismissPanel();}
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
    /** Restrict panel bounds to the available device display on every open. */
    private int[] panelDimensions(){
        String mode=OverlayPreferences.size(this);
        int availableWidth=Math.max(1,screenWidth()-dp(16));
        int availableHeight=Math.max(1,screenHeight()-dp(80));
        int w="compact".equals(mode)?dp(294):
            "expanded".equals(mode)?dp(420):dp(352);
        int h="compact".equals(mode)?dp(410):
            "expanded".equals(mode)?dp(690):dp(555);
        return new int[]{Math.min(w,availableWidth),Math.min(h,availableHeight)};
    }
    private void resizePanel(){
        if(panel==null||panelParams==null)return;
        int[] dimensions=panelDimensions();
        panelParams.width=dimensions[0];panelParams.height=dimensions[1];
        panelParams.x=Math.max(0,Math.min(screenWidth()-panelParams.width,
            panelParams.x));
        panelParams.y=Math.max(dp(10),Math.min(
            Math.max(dp(10),screenHeight()-panelParams.height-dp(12)),
            panelParams.y));
        try{wm.updateViewLayout(panel,panelParams);}
        catch(Exception ignored){}
    }
    private void snapBubbleToEdge(){
        if(bubbleParams==null)return;
        if(OverlayPreferences.snap(this)){
            int max=Math.max(0,screenWidth()-dp(60));
            bubbleParams.x=bubbleParams.x<max/2?0:max;
        }
        bubbleParams.x=Math.max(0,Math.min(screenWidth()-dp(60),bubbleParams.x));
        bubbleParams.y=Math.max(dp(10),Math.min(screenHeight()-dp(70),
            bubbleParams.y));
        try{wm.updateViewLayout(bubble,bubbleParams);}
        catch(Exception ignored){}
        saveBubblePosition();
    }
    private void saveBubblePosition(){
        if(bubbleParams==null)return;
        getSharedPreferences("tgc_overlay",0).edit()
            .putInt("x",bubbleParams.x).putInt("y",bubbleParams.y).apply();
    }
    private void openShortcut(String key){
        switch(key){
            case "inbox":mini.inbox("all");break;
            case "search":mini.findAnything();break;
            case "verify":mini.verification();break;
            default:go(OverlayPreferences.destination(key));break;
        }
    }
    private void pinnedShortcuts(LinearLayout parent){
        List<String> pinned=OverlayPreferences.shortcuts(this);
        for(int i=0;i<pinned.size();i+=2){
            final String a=pinned.get(i);
            if(i+1<pinned.size()){
                final String b=pinned.get(i+1);
                paired(parent,OverlayPreferences.name(a),OverlayPreferences.name(b),
                    ()->openShortcut(a),()->openShortcut(b));
            }else{
                action(parent,OverlayPreferences.name(a),false,()->openShortcut(a));
            }
        }
    }
    private void showOverlaySettings(){
        action(panelContent,"‹ Back to tools",false,()->{
            selectedTab="tools";buildTab();
        });
        section(panelContent,"FLOATING PANEL SIZE");
        LinearLayout sizes=panelCard(panelContent);
        String selected=OverlayPreferences.size(this);
        sizes.addView(text("Current: "+(
            "compact".equals(selected)?"Compact":
            "expanded".equals(selected)?"Expanded":"Standard"),
            12,CompanionStyle.ACCENT,true));
        action(sizes,"Compact • less screen space",false,
            ()->changeSize("compact"));
        action(sizes,"Standard • balanced",false,
            ()->changeSize("standard"));
        action(sizes,"Expanded • more information",false,
            ()->changeSize("expanded"));
        section(panelContent,"BUBBLE POSITION");
        LinearLayout position=panelCard(panelContent);
        action(position,
            OverlayPreferences.snap(this)?
                "Snap to nearest edge: ON":"Snap to nearest edge: OFF",false,()->{
                OverlayPreferences.setSnap(this,!OverlayPreferences.snap(this));
                if(OverlayPreferences.snap(this))snapBubbleToEdge();
                buildTab();
            });
        action(position,"Reset bubble position",false,()->{
            if(bubbleParams==null)return;
            bubbleParams.x=Math.max(0,screenWidth()-dp(80));
            bubbleParams.y=dp(165);
            snapBubbleToEdge();
            buildTab();
        });
        section(panelContent,"PINNED HOME SHORTCUTS");
        LinearLayout pins=panelCard(panelContent);
        pins.addView(text(
            "Choose up to six shortcuts; tap ↑ or ↓ to change their order. "
            +"Your choices stay saved on this phone.",
            12,CompanionStyle.MUTED,false));
        List<String> selectedPins=OverlayPreferences.shortcuts(this);
        for(String key:OverlayPreferences.SHORTCUTS){
            final String value=key;
            boolean pinned=selectedPins.contains(value);
            action(pins,(pinned?"✓ ":"＋ ")+OverlayPreferences.name(value),
                pinned,()->{
                List<String> list=new ArrayList<>(OverlayPreferences.shortcuts(this));
                if(list.contains(value)){
                    if(list.size()<=1){
                        Toast.makeText(this,"Keep at least one shortcut",
                            Toast.LENGTH_SHORT).show();return;
                    }
                    list.remove(value);
                }else{
                    if(list.size()>=6){
                        Toast.makeText(this,"Maximum six shortcuts",
                            Toast.LENGTH_SHORT).show();return;
                    }
                    list.add(value);
                }
                OverlayPreferences.setShortcuts(this,list);
                buildTab();
            });
            if(pinned){
                paired(pins,"↑ Move earlier","↓ Move later",
                    ()->moveShortcut(value,-1),()->moveShortcut(value,1));
            }
        }
        section(panelContent,"BACKGROUND RECOVERY");
        LinearLayout background=panelCard(panelContent);
        background.addView(text(
            "Tracking runs independently. Android can interrupt the floating "
            +"panel; optional recovery asks Android to restart it and checks "
            +"staff access online before showing it again. This is best-effort, "
            +"not a way around battery restrictions or force-stop.",
            12,CompanionStyle.MUTED,false));
        action(background,OverlayPreferences.recover(this)?
            "Recover floating tools: ON":"Recover floating tools: OFF",false,()->{
            boolean enable=!OverlayPreferences.recover(this);
            OverlayPreferences.setRecover(this,enable);
            // Refresh the foreground service's START_STICKY return mode.
            try{
                startService(new Intent(this,FloatingCompanionService.class)
                    .setAction(ACTION_START));
            }catch(Exception ignored){}
            buildTab();
        });
    }
    private void changeSize(String size){
        OverlayPreferences.setSize(this,size);
        resizePanel();
        buildTab();
    }
    private void moveShortcut(String key,int direction){
        List<String> list=new ArrayList<>(OverlayPreferences.shortcuts(this));
        int index=list.indexOf(key),next=index+direction;
        if(index<0||next<0||next>=list.size())return;
        java.util.Collections.swap(list,index,next);
        OverlayPreferences.setShortcuts(this,list);
        buildTab();
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
        presenceLabel=text(presenceDescription+"  ·  Edit ›",11,CompanionStyle.ACCENT,false);
        presenceLabel.setPadding(0,dp(4),0,dp(4));
        labels.addView(presenceLabel);
        presenceLabel.setOnClickListener(v->go("mini_presence"));
        Button collapse=CompanionStyle.button(this,"−",false);
        collapse.setContentDescription("Collapse overlay");
        bar.addView(collapse,new LinearLayout.LayoutParams(dp(43),dp(39)));
        collapse.setOnClickListener(v->dismissPanel());

        LinearLayout tabs=new LinearLayout(this);
        tabs.setPadding(dp(12),dp(7),dp(12),dp(3));
        container.addView(tabs);
        tab(tabs,"Home","home");
        tab(tabs,"Social","social");
        tab(tabs,"OSC","osc");
        tab(tabs,"Tools","tools");

        ScrollView scroll=new ScrollView(this);
        panelScroller=scroll;
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
        smallAction(bottom,"Hide bubble",this::stopRequestedByUser);

        int[] dimensions=panelDimensions();
        int width=dimensions[0],height=dimensions[1];
        WindowManager.LayoutParams p=params(width,height);
        p.x=Math.max(dp(8),Math.min(screenWidth()-width-dp(8),bubbleParams.x));
        int possibleTop=bubbleParams.y+dp(65);
        if(possibleTop+height>screenHeight()-dp(20))possibleTop=bubbleParams.y-height-dp(7);
        p.y=Math.max(dp(25),Math.min(screenHeight()-height-dp(25),possibleTop));
        try{
            panelParams=p;
            wm.addView(container,p);
            // Refresh stale account data on return from a long collapsed session,
            // but don't spam VRChat when users quickly open/close the bubble.
            long now=System.currentTimeMillis();
            if(now-lastPresenceFetch>75_000L){
                handler.removeCallbacks(presenceTick);
                handler.post(presenceTick);
            }
            if(now-lastFriendFetch>150_000L){
                handler.removeCallbacks(friendTick);
                handler.post(friendTick);
            }
            refreshStatus();loadRecentEvents();
        }catch(Exception e){
            dismissPanel();
            Toast.makeText(this,"Unable to display overlay panel",Toast.LENGTH_SHORT).show();
        }
    }
    private void tab(LinearLayout container,String title,String key){
        Button b=CompanionStyle.button(this,title,key.equals(selectedTab));
        b.setTextSize(11);
        b.setAllCaps(false);
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
        // Stop in-overlay periodic lookups when navigating away from a detail page.
        if(mini!=null)mini.close();
        panelContent.removeAllViews();
        presenceActionLabel=null;
        nodeStatus=null;nodeStats=null;trackingToggle=null;recentStatus=null;
        switch(selectedTab){
            case "social":showSocial();break;
            case "osc":showOscDashboard();break;
            case "tools":showTools();break;
            case "home":showHome();break;
            case "customize":showOverlaySettings();break;
            default:mini.show(panelContent,selectedTab);break;
        }
        // Each in-overlay page should start at its header, not inherit the
        // previous page's scroll offset (especially on a small phone).
        if(panelScroller!=null){
            ScrollView current=panelScroller;
            current.post(()->{
                if(panelScroller==current)current.scrollTo(0,0);
            });
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
        paired(recent,"Player list","Join / leave log",
            ()->go("mini_current"),()->go("mini_joins"));
        action(recent,"Past encounters",false,()->go("mini_players"));

        section(panelContent,"YOUR PINNED TOOLS");
        LinearLayout quick=panelCard(panelContent);
        pinnedShortcuts(quick);
        action(quick,"Customize shortcuts",false,()->go("customize"));
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
        paired(communication,"Friend requests","Find anything",
            ()->mini.inbox("friend"),()->mini.findAnything());
        section(panelContent,"PLAYER TOOLS");
        LinearLayout players=panelCard(panelContent);
        paired(players,"Player list","Join / leave log",
            ()->go("mini_current"),()->go("mini_joins"));
        action(players,"Past encounters & profiles",false,
            ()->go("mini_players"));
        action(players,"My VRChat groups",false,()->go("mini_groups"));
    }
    /** Dedicated fourth tab: all local VRChat Mobile OSC controls live here. */
    private void showOscDashboard(){
        section(panelContent,"OSC · VRCHAT MOBILE");
        LinearLayout top=panelCard(panelContent);
        top.addView(text("Send directly to VRChat on this phone",
            14,CompanionStyle.TEXT,true));
        top.addView(text("Enable OSC in VRChat's Action Menu. OSC uses 127.0.0.1:9000 and does not need Shizuku, a PC or the TGC Relay.",
            12,CompanionStyle.MUTED,false));

        section(panelContent,"CHATBOX");
        LinearLayout chat=panelCard(panelContent);
        action(chat,"Compose & send chat text",true,()->go("mini_chat"));
        action(chat,"Saved quick phrases",false,()->go("mini_osc_presets"));

        section(panelContent,"LIVE CHATBOX");
        LinearLayout line=panelCard(panelContent);
        line.addView(text(oscLiveSummary(),12,
            oscLiveActive?CompanionStyle.GOOD:CompanionStyle.MUTED,false));
        action(line,"Configure / start Live Chatbox",true,()->go("mini_osc_status"));
        line.addView(text("Build an optional chatbox line using a custom message, current time and phone battery level. Sending is user-controlled.",
            12,CompanionStyle.MUTED,false));

        section(panelContent,"TYPING INDICATOR");
        LinearLayout typing=panelCard(panelContent);
        paired(typing,"Typing ON","Typing OFF",
            ()->sendOscTyping(true),()->sendOscTyping(false));
        boolean automatic=false;
        try{automatic=OscControlStore.typingAuto(this);}
        catch(Exception ignored){}
        action(typing,"Follow chat keyboard: "+(automatic?"ON":"OFF"),false,()->{
            try{
                boolean next=!OscControlStore.typingAuto(this);
                OscControlStore.setTypingAuto(this,next);
                if(!next)sendOscTyping(false);
                buildTab();
            }catch(Exception ex){
                Toast.makeText(this,"Sign in to VRChat first to configure OSC.",
                    Toast.LENGTH_LONG).show();
            }
        });

        section(panelContent,"AVATAR OSC PARAMETERS");
        LinearLayout controls=panelCard(panelContent);
        action(controls,"My avatar parameter controls",true,
            ()->go("mini_osc_parameters"));
        controls.addView(text("Add your avatar's exact parameter name; send Bool, Int or Float values. Only compatible avatars react.",
            12,CompanionStyle.MUTED,false));

        LinearLayout help=panelCard(panelContent);
        help.addView(text("All broadcasts and parameter writes are OFF until you deliberately send them. Local OSC packets are not delivery confirmations.",
            11,CompanionStyle.GOLD,false));
    }
    private void sendOscTyping(boolean typing){
        if(!StaffGate.unlocked(this))return;
        eventsWorker.execute(()->{
            String issue=null;
            try{VRChatOscChat.sendTyping(typing);}
            catch(Exception ex){issue=ex.getMessage();}
            final String error=issue;
            handler.post(()->Toast.makeText(this,
                error==null?
                    "Typing "+(typing?"ON":"OFF")+" packet sent locally":
                    "OSC: "+error,Toast.LENGTH_SHORT).show());
        });
    }
    private void showTools(){
        section(panelContent,"YOUR VRCHAT STATUS");
        LinearLayout statusCard=panelCard(panelContent);
        presenceActionLabel=text(presenceDescription,16,CompanionStyle.ACCENT,true);
        statusCard.addView(presenceActionLabel);
        action(statusCard,"Change status or message",true,()->go("mini_presence"));
        statusCard.addView(text("Pick a color, edit your message, apply once. Your saved presets work here too.",
            12,CompanionStyle.MUTED,false));
        section(panelContent,"YOUR ACCOUNT");
        LinearLayout personal=panelCard(panelContent);
        paired(personal,"Favorite avatars","Saved worlds",
            ()->go("mini_avatars"),()->go("mini_worlds"));
        paired(personal,"Recent worlds","Current instance",
            ()->go("mini_recent"),()->go("mini_now"));
        paired(personal,"Find anything","My groups",
            ()->mini.findAnything(),()->go("mini_groups"));
        section(panelContent,"VERIFICATION");
        LinearLayout codes=panelCard(panelContent);
        action(codes,"Verification code",false,()->mini.verification());
        codes.addView(text("Automatically detects the latest TGC or Cult code from your VRChat invites.",
            12,CompanionStyle.MUTED,false));
        section(panelContent,"STATUS COLORS");
        LinearLayout presenceInfo=panelCard(panelContent);
        presenceInfo.addView(text(
            "● Green Online  ·  ● Blue Join Me  ·  ● Yellow Ask Me  ·  ● Red Busy  ·  ● Gray Offline/unknown",
            12,CompanionStyle.MUTED,false));
        section(panelContent,"PLAYER INFORMATION");
        LinearLayout player=panelCard(panelContent);
        paired(player,"Player list","Join / leave log",
            ()->go("mini_current"),()->go("mini_joins"));
        paired(player,"Past encounters","Friend log",
            ()->go("mini_players"),()->go("mini_log"));
        action(panelContent,"Customize floating panel",false,
            ()->{selectedTab="customize";buildTab();});
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
    private void refreshPresenceDot(){
        if(statusDot!=null){
            statusDot.setBackground(CompanionStyle.shape(this,
                VRChatPresenceIndicator.color(presenceStatus),12,CompanionStyle.SURFACE));
            statusDot.setContentDescription(presenceDescription);
        }
        if(bubble!=null){
            bubble.setContentDescription("TGC floating tools. "+presenceDescription+
                ". Tap to expand; drag to move.");
        }
        if(presenceLabel!=null)presenceLabel.setText(presenceDescription+"  ·  Edit ›");
        if(presenceActionLabel!=null){
            presenceActionLabel.setText(presenceDescription);
            presenceActionLabel.setTextColor(VRChatPresenceIndicator.color(presenceStatus));
        }
    }
    private void refreshStatus(){
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
        panel=null;panelContent=null;panelScroller=null;recentRows=null;recentStatus=null;
        presenceLabel=null;presenceActionLabel=null;
        panelParams=null;
        nodeStatus=null;nodeStats=null;trackingToggle=null;
    }
    @Override public void onConfigurationChanged(Configuration newConfig){
        super.onConfigurationChanged(newConfig);
        // Orientation, split screen and resolution changes can otherwise leave
        // an overlay outside the touchable display area.
        handler.post(()->{
            if(bubble!=null)snapBubbleToEdge();
            if(panel!=null)resizePanel();
        });
    }
    @Override public void onDestroy(){
        stoppingExplicitly=true;
        stopOscLive();
        if(restoreRunnable[0]!=null)handler.removeCallbacks(restoreRunnable[0]);
        handler.removeCallbacks(validate);
        handler.removeCallbacks(friendTick);
        handler.removeCallbacks(refreshTick);
        handler.removeCallbacks(presenceTick);
        verifier.shutdownNow();friendWorker.shutdownNow();eventsWorker.shutdownNow();
        presenceWorker.shutdownNow();
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
