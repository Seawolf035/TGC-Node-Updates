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
    // Voice press/release must never sit behind API/log/phrase jobs.
    private final ExecutorService oscVoiceWorker=Executors.newSingleThreadExecutor();
    private final ExecutorService presenceWorker=Executors.newSingleThreadExecutor();
    private WindowManager wm;
    private WindowManager.LayoutParams panelParams;
    private OverlayMiniVrcx mini;
    private final ArrayDeque<String> backStack=new ArrayDeque<>();
    private FrameLayout bubble;
    private View statusDot;
    private TextView micSidecar;
    private WindowManager.LayoutParams micSidecarParams;
    private LinearLayout quickBarRow;
    private TextView oscVoiceBubbleBadge,oscVoiceInfo;
    private Button oscVoiceToggle;
    private LinearLayout oscVoiceQuickRelease;
    // Experimental OSC PTT hold is process-local and is never persisted.
    // These flags reflect what was SENT, not the actual VRChat microphone state.
    private volatile boolean oscVoiceHoldRequested=false;
    private boolean oscVoiceSending=false;
    private volatile int oscVoiceEpoch=0;
    private static final long OSC_VOICE_SAFETY_MS=300_000L;
    private final Runnable oscVoiceSafety=()->{
        if(oscVoiceHoldRequested){
            sendOscVoiceHold(false);
            Toast.makeText(this,"OSC mic hold auto-released after 5 minutes.",
                Toast.LENGTH_LONG).show();
        }
    };
    private FrameLayout panel, panelModalLayer;
    private LinearLayout panelContent;
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
    private boolean verifying=false, friendBusy=false, eventBusy=false;
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
    private volatile boolean oscLivePaused=false;
    private TextView oscQuickStatus;
    private Button oscQuickStartStop,oscQuickPause;
    private boolean oscLiveBusy=false;
    private int oscLiveIntervalSeconds=5;
    private volatile int oscLiveGeneration=0;
    private String oscLiveAccountId="",oscLiveLastError="",oscLiveLastText="";
    private long oscLiveLastAt=0L;
    private volatile long oscLiveSwitchElapsed=0L;
    private volatile int oscLiveCurrentSlot=0;
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
            if(oscLivePaused){
                handler.postDelayed(this,2000L);
                return;
            }
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
                boolean accountChanged=false, noChatboxes=false;
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
                        if(count<1){
                            noChatboxes=true;
                            throw new IllegalStateException(
                                "No authorized chatbox remains. Live Chatbox stopped.");
                        }
                        long now=android.os.SystemClock.elapsedRealtime();
                        if(count>1){
                            int interval=OscControlStore.rotationInterval(FloatingCompanionService.this);
                            // Advance one slot per actual send at most. When the
                            // refresh interval is slower than the rotation period,
                            // no profiles are silently skipped.
                            if(oscLiveCurrentSlot>=count)oscLiveCurrentSlot=0;
                            if(now-oscLiveSwitchElapsed>=1000L*interval){
                                oscLiveCurrentSlot=(oscLiveCurrentSlot+1)%count;
                                oscLiveSwitchElapsed=now;
                            }
                        }else{
                            oscLiveCurrentSlot=0;
                            oscLiveSwitchElapsed=now;
                        }
                        value=OscControlStore.liveChatboxAt(FloatingCompanionService.this,
                            oscLiveCurrentSlot);
                        if(!oscLiveActive||epoch!=oscLiveGeneration)return;
                        VRChatOscChat.sendLive(value);
                    }
                }catch(Exception e){
                    error=e.getMessage()==null?"Unable to send OSC status":e.getMessage();
                }
                final String problem=error,output=value;
                final boolean invalidAccount=accountChanged;
                final boolean emptyRotation=noChatboxes;
                handler.post(()->{
                    if(epoch!=oscLiveGeneration)return;
                    oscLiveBusy=false;
                    if(invalidAccount){
                        oscLiveLastError="VRChat account changed. Live Chatbox stopped.";
                        stopOscLive();
                        return;
                    }
                    if(emptyRotation){
                        oscLiveLastError="No authorized chatboxes remain. Live Chatbox stopped.";
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
            // Validate the ACTUAL first outgoing slot, which may be an
            // official TGC role tag with no personal status enabled.
            if(OscControlStore.liveSlotCount(this)<1)
                throw new IllegalStateException(
                    "Select an unlocked official tag or enable your personal status.");
            OscControlStore.liveChatboxAt(this,0);
            stopOscLive();
            oscLiveAccountId=account;
            oscLiveCurrentSlot=0;
            oscLiveSwitchElapsed=android.os.SystemClock.elapsedRealtime();
            oscLiveIntervalSeconds=seconds;
            oscLiveActive=true;
            oscLivePaused=false;
            oscLiveLastError="";
            handler.post(oscLiveTick);
            refreshOscQuick();
            updateForegroundText("Live OSC Chatbox · every "+seconds+" seconds");
        }catch(Exception e){
            Toast.makeText(this,"Live Chatbox unavailable: "+e.getMessage(),
                Toast.LENGTH_LONG).show();
        }
    }
    private void stopOscLive(){
        oscLiveActive=false;
        oscLivePaused=false;
        oscLiveGeneration++;
        handler.removeCallbacks(oscLiveTick);
        oscLiveBusy=false;
        refreshOscQuick();
        if(!stoppingExplicitly&&bubble!=null)
            updateForegroundText("Floating tools ready");
    }
    private String oscLiveSummary(){
        if(!oscLiveActive)return "OFF · Only sends when you press Send";
        String label=(oscLivePaused?"PAUSED":"ON")+
            " · Refresh every "+oscLiveIntervalSeconds+" seconds";
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
                        JSONArray full=VRChatApi.friendSnapshot(FloatingCompanionService.this);
                        FriendLogStore.observe(FloatingCompanionService.this,session,full);
                        if(PinnedFriendAlerts.active(FloatingCompanionService.this)){
                            PinnedFriendAlertWorker.notifyOnline(FloatingCompanionService.this,
                                PinnedFriendAlerts.observe(FloatingCompanionService.this,
                                    session,full));
                        }
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
            @Override public void confirm(String prompt,Runnable action){
                showPinnedConfirmation(prompt,action);
            }
            @Override public void feedback(String message){
                showPinnedFeedback(message);
            }
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
        releaseOscVoiceOnExit();
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
        TextView micBadge=text("PTT",9,0xFFFFFFFF,true);
        micBadge.setGravity(Gravity.CENTER);
        micBadge.setBackground(CompanionStyle.shape(this,0xFFB72C32,8,0));
        FrameLayout.LayoutParams badgeBounds=new FrameLayout.LayoutParams(
            dp(30),dp(19),Gravity.TOP|Gravity.START);
        badgeBounds.setMargins(dp(1),dp(1),0,0);
        circle.addView(micBadge,badgeBounds);
        micBadge.setVisibility(View.GONE);
        oscVoiceBubbleBadge=micBadge;
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
                            updateMicSidecarPosition();
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
        // Separate small, always-reachable action next to the draggable bubble.
        // The main bubble remains 60dp and keeps its original tap/drag gestures.
        try{
            micSidecar=text("PTT\nREL",10,CompanionStyle.TEXT,true);
            micSidecar.setGravity(Gravity.CENTER);
            micSidecar.setBackground(CompanionStyle.shape(this,
                CompanionStyle.SURFACE,15,CompanionStyle.BORDER));
            micSidecar.setElevation(dp(8));
            micSidecar.setContentDescription("Toggle OSC push-to-talk");
            micSidecar.setOnClickListener(v->sendOscVoiceHold(!oscVoiceHoldRequested));
            micSidecarParams=params(dp(45),dp(49));
            placeMicSidecar();
            wm.addView(micSidecar,micSidecarParams);
            updateOscVoiceUi();
        }catch(Exception e){
            // Failure to show the shortcut must not remove the working bubble.
            micSidecar=null;micSidecarParams=null;
        }
    }
    private void placeMicSidecar(){
        if(bubbleParams==null||micSidecarParams==null)return;
        int left=bubbleParams.x;
        int rightSpace=screenWidth()-left-dp(60);
        int proposed=rightSpace>=dp(52)?left+dp(63):left-dp(48);
        micSidecarParams.x=Math.max(0,
            Math.min(screenWidth()-dp(45),proposed));
        micSidecarParams.y=Math.max(dp(5),
            Math.min(screenHeight()-dp(55),bubbleParams.y+dp(5)));
    }
    private void updateMicSidecarPosition(){
        if(micSidecar==null||micSidecarParams==null)return;
        placeMicSidecar();
        try{wm.updateViewLayout(micSidecar,micSidecarParams);}
        catch(Exception ignored){}
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
        updateMicSidecarPosition();
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
    private void drawQuickBar(){
        if(quickBarRow==null)return;
        quickBarRow.removeAllViews();
        List<String> keys=OverlayPreferences.quickBar(this);
        quickBarRow.setVisibility(keys.isEmpty()?View.GONE:View.VISIBLE);
        for(String key:keys){
            String shortName;
            switch(key){
                case "inbox":shortName="Inbox";break;
                case "friends":shortName="Friends";break;
                case "status":shortName="Status";break;
                case "instance":shortName="Instance";break;
                case "chat":shortName="Chat";break;
                case "verify":shortName="Code";break;
                case "activity":shortName="Activity";break;
                case "players":shortName="Players";break;
                case "recent":shortName="Recent";break;
                case "search":shortName="Search";break;
                case "joins":shortName="Joins";break;
                case "groups":shortName="Groups";break;
                case "worlds":shortName="Worlds";break;
                default:shortName=OverlayPreferences.name(key);break;
            }
            Button control=CompanionStyle.button(this,shortName,false);
            control.setTextSize(11);
            control.setAllCaps(false);
            control.setContentDescription("Quick action: "+OverlayPreferences.name(key));
            LinearLayout.LayoutParams param=new LinearLayout.LayoutParams(0,dp(38),1f);
            param.setMargins(dp(2),0,dp(2),0);
            quickBarRow.addView(control,param);
            control.setOnClickListener(v->openShortcut(key));
        }
    }
    private void changeQuickAction(String key){
        List<String> keys=new ArrayList<>(OverlayPreferences.quickBar(this));
        if(keys.contains(key))keys.remove(key);
        else{
            if(keys.size()>=3){
                Toast.makeText(this,"Choose at most three quick actions",
                    Toast.LENGTH_SHORT).show();return;
            }
            keys.add(key);
        }
        OverlayPreferences.setQuickBar(this,keys);
        drawQuickBar();
        buildTab();
    }
    private void moveQuickAction(String key,int distance){
        List<String> keys=new ArrayList<>(OverlayPreferences.quickBar(this));
        int first=keys.indexOf(key),next=first+distance;
        if(first<0||next<0||next>=keys.size())return;
        java.util.Collections.swap(keys,first,next);
        OverlayPreferences.setQuickBar(this,keys);
        drawQuickBar();
        buildTab();
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
        section(panelContent,"PERSISTENT QUICK BAR");
        LinearLayout quickSettings=panelCard(panelContent);
        quickSettings.addView(text(
            "Choose up to three buttons that stay visible under the tabs on "+
            "every floating tool page. This is separate from Home shortcuts.",
            12,CompanionStyle.MUTED,false));
        List<String> activeQuick=OverlayPreferences.quickBar(this);
        for(String key:OverlayPreferences.SHORTCUTS){
            boolean picked=activeQuick.contains(key);
            action(quickSettings,(picked?"✓ ":"＋ ")+OverlayPreferences.name(key),
                picked,()->changeQuickAction(key));
            if(picked)
                paired(quickSettings,"↑ Earlier","↓ Later",
                    ()->moveQuickAction(key,-1),()->moveQuickAction(key,1));
        }
        action(quickSettings,"Hide persistent quick bar",false,()->{
            OverlayPreferences.setQuickBar(this,new ArrayList<>());
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
        // FrameLayout permits true modal confirmations ABOVE the existing buttons.
        // The user's scroll position and screen remain unchanged underneath.
        FrameLayout overlayRoot=new FrameLayout(this);
        overlayRoot.setElevation(dp(14));
        overlayRoot.setBackground(CompanionStyle.shape(this,CompanionStyle.BG,19,
            CompanionStyle.BORDER));
        LinearLayout container=vertical();
        overlayRoot.addView(container,new FrameLayout.LayoutParams(-1,-1));
        panel=overlayRoot;panelVisible=true;

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

        // User-selected shortcuts never scroll away on long Mini-VRCX pages.
        quickBarRow=new LinearLayout(this);
        quickBarRow.setGravity(Gravity.CENTER_VERTICAL);
        quickBarRow.setPadding(dp(9),0,dp(9),dp(3));
        container.addView(quickBarRow);
        drawQuickBar();

        // Fast release stays outside the scrolling content on EVERY tab.
        oscVoiceQuickRelease=vertical();
        oscVoiceQuickRelease.setPadding(dp(12),dp(3),dp(12),dp(3));
        container.addView(oscVoiceQuickRelease);
        updateOscVoiceUi();

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

        // In-panel scrim is drawn last, over all tool controls. It intercepts
        // background taps while Confirm/Cancel are visible, without any scroll.
        panelModalLayer=new FrameLayout(this);
        panelModalLayer.setVisibility(View.GONE);
        panelModalLayer.setBackgroundColor(0xAA06121C);
        panelModalLayer.setClickable(true);
        panelModalLayer.setFocusable(true);
        overlayRoot.addView(panelModalLayer,
            new FrameLayout.LayoutParams(-1,-1));

        int[] dimensions=panelDimensions();
        int width=dimensions[0],height=dimensions[1];
        WindowManager.LayoutParams p=params(width,height);
        p.x=Math.max(dp(8),Math.min(screenWidth()-width-dp(8),bubbleParams.x));
        int possibleTop=bubbleParams.y+dp(65);
        if(possibleTop+height>screenHeight()-dp(20))possibleTop=bubbleParams.y-height-dp(7);
        p.y=Math.max(dp(25),Math.min(screenHeight()-height-dp(25),possibleTop));
        try{
            panelParams=p;
            wm.addView(overlayRoot,p);
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
            selectedTab=key;backStack.clear();
            dismissPanel();togglePanel();
        });
    }
    private void dismissModal(){
        if(panelModalLayer==null)return;
        panelModalLayer.removeAllViews();
        panelModalLayer.setVisibility(View.GONE);
    }
    private void showPinnedConfirmation(String prompt,Runnable action){
        if(panelModalLayer==null || !StaffGate.unlocked(this) || action==null)return;
        dismissModal();
        // Dismiss the keyboard; the modal appears centrally over the same
        // buttons, rather than being appended to a scrolled list.
        setOverlayKeyboard(false);
        final FrameLayout owner=panelModalLayer;
        LinearLayout card=CompanionStyle.card(this);
        FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(
            -1,-2,Gravity.CENTER);
        p.setMargins(dp(17),dp(8),dp(17),dp(8));
        owner.addView(card,p);
        card.addView(text(prompt==null?"Confirm this action?":prompt,
            14,CompanionStyle.TEXT,true));
        LinearLayout buttons=vertical();
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        card.addView(buttons);
        Button cancel=CompanionStyle.button(this,"Cancel",false);
        Button approve=CompanionStyle.button(this,"Confirm",true);
        LinearLayout.LayoutParams left=new LinearLayout.LayoutParams(0,dp(48),1f);
        left.setMargins(0,dp(12),dp(5),0);
        LinearLayout.LayoutParams right=new LinearLayout.LayoutParams(0,dp(48),1f);
        right.setMargins(dp(5),dp(12),0,0);
        buttons.addView(cancel,left);
        buttons.addView(approve,right);
        owner.setVisibility(View.VISIBLE);
        owner.bringToFront();
        cancel.setOnClickListener(v->dismissModal());
        approve.setOnClickListener(v->{
            if(owner!=panelModalLayer)return;
            dismissModal();
            if(!StaffGate.unlocked(this)){stopSelf();return;}
            action.run();
        });
    }
    private void showPinnedFeedback(String info){
        if(panel==null||info==null||info.trim().isEmpty())return;
        // Messages don't get injected into the document or move the scroll.
        Toast.makeText(this,info,Toast.LENGTH_SHORT).show();
    }
    private void buildTab(){
        if(panelContent==null)return;
        drawQuickBar();
        dismissModal();
        // Stop in-overlay periodic lookups when navigating away from a detail page.
        if(mini!=null)mini.close();
        panelContent.removeAllViews();
        presenceActionLabel=null;
        nodeStatus=null;nodeStats=null;trackingToggle=null;recentStatus=null;
        oscVoiceInfo=null;oscVoiceToggle=null;
        oscQuickStatus=null;oscQuickStartStop=null;oscQuickPause=null;
        switch(selectedTab){
            case "social":showSocial();break;
            case "osc":showOscDashboard();break;
            case "tools":showTools();break;
            case "home":showHome();break;
            case "customize":showOverlaySettings();break;
            default:mini.show(panelContent,selectedTab);break;
        }
        updateOscVoiceUi();
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
        buildTab();
    }
    private void goBack(){
        selectedTab=backStack.isEmpty()?"home":backStack.pop();
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
    private void renderPinnedRows(LinearLayout rows){
        if(rows==null)return;
        rows.removeAllViews();
        try{
            JSONArray pinned=FriendPinStore.pins(this);
            JSONObject current=new JSONObject();
            long at=0L;
            try{
                JSONObject session=VRChatSession.load(this);
                current=FriendLogStore.snapshot(this,session);
                at=FriendLogStore.lastPoll(this,session);
            }catch(Exception ignored){}
            if(pinned.length()==0){
                rows.addView(text(
                    "No friends pinned yet. Add ★ from Friends or a profile.",
                    12,CompanionStyle.MUTED,false));
                return;
            }
            boolean recent=at>0 && System.currentTimeMillis()-at<8*60_000L;
            for(int i=0;i<pinned.length();i++){
                JSONObject row=pinned.optJSONObject(i);
                if(row==null)continue;
                final String id=row.optString("id","");
                if(!id.matches("usr_[0-9a-fA-F-]{36}"))continue;
                LinearLayout line=new LinearLayout(this);
                line.setGravity(Gravity.CENTER_VERTICAL);
                rows.addView(line,new LinearLayout.LayoutParams(-1,dp(43)));
                String name=row.optString("name","Friend");
                JSONObject snapshot=current.optJSONObject(id);
                String state=snapshot==null?" · ?":
                    !recent?" · last seen":
                    snapshot.optBoolean("online",false)?" · ● online":" · offline";
                TextView label=text((i+1)+". "+name+state,11,
                    recent&&snapshot!=null&&snapshot.optBoolean("online",false)?
                        CompanionStyle.GOOD:CompanionStyle.TEXT,false);
                label.setSingleLine(true);
                label.setEllipsize(android.text.TextUtils.TruncateAt.END);
                label.setPadding(dp(2),0,dp(4),0);
                label.setContentDescription("Open pinned friend "+name);
                line.addView(label,new LinearLayout.LayoutParams(0,dp(39),1f));
                label.setGravity(Gravity.CENTER_VERTICAL);
                label.setOnClickListener(v->mini.profile(id));
                Button up=CompanionStyle.button(this,"↑",false);
                Button down=CompanionStyle.button(this,"↓",false);
                up.setTextSize(14);down.setTextSize(14);
                up.setContentDescription("Move "+name+" earlier");
                down.setContentDescription("Move "+name+" later");
                line.addView(up,new LinearLayout.LayoutParams(dp(39),dp(39)));
                line.addView(down,new LinearLayout.LayoutParams(dp(39),dp(39)));
                up.setEnabled(i>0);down.setEnabled(i<pinned.length()-1);
                up.setOnClickListener(v->{
                    try{FriendPinStore.move(this,id,-1);renderPinnedRows(rows);}
                    catch(Exception e){showPinnedFeedback(e.getMessage());}
                });
                down.setOnClickListener(v->{
                    try{FriendPinStore.move(this,id,1);renderPinnedRows(rows);}
                    catch(Exception e){showPinnedFeedback(e.getMessage());}
                });
            }
        }catch(Exception e){
            rows.addView(text("Sign in to view pinned friend shortcuts.",
                12,CompanionStyle.MUTED,false));
        }
    }
    private void showSocial(){
        section(panelContent,"PINNED FRIENDS");
        LinearLayout pinned=panelCard(panelContent);
        pinned.addView(text(
            "Compact shortcuts · use ↑ / ↓ to choose your own order.",
            11,CompanionStyle.MUTED,false));
        LinearLayout pinnedRows=vertical();
        pinned.addView(pinnedRows);
        renderPinnedRows(pinnedRows);

        Button alerts=CompanionStyle.button(this,
            PinnedFriendAlerts.enabled(this)?
                "Online alerts: ON":"Online alerts: OFF",false);
        pinned.addView(alerts,CompanionStyle.buttonParams(this));
        alerts.setOnClickListener(v->{
            if(!PinnedFriendAlerts.enabled(this)&&!PinnedFriendAlerts.permission(this)){
                showPinnedFeedback("Allow Android notifications in the full Companion first.");
                open(CompanionActivity.class,null,null);
                return;
            }
            try{
                boolean next=!PinnedFriendAlerts.enabled(this);
                PinnedFriendAlerts.setEnabled(this,next);
                alerts.setText(next?"Online alerts: ON":"Online alerts: OFF");
                showPinnedFeedback(next?
                    "Pinned friend alerts enabled. First check sets a baseline.":
                    "Pinned friend alerts disabled.");
            }catch(Exception ex){showPinnedFeedback("Friend alerts: "+ex.getMessage());}
        });
        pinned.addView(text(
            "When enabled, alerts appear only for pinned friends changing "+
            "from offline to online. Floating checks usually take 2.5–5 min; "+
            "background checks are about every 15 min and may be delayed.",
            10,CompanionStyle.MUTED,false));
        paired(pinned,"Manage friends","Invitations",
            ()->go("mini_friends"),()->mini.inbox("invite"));
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
    private void refreshOscQuick(){
        if(oscQuickStatus!=null)oscQuickStatus.setText(oscLiveSummary());
        if(oscQuickStartStop!=null)
            oscQuickStartStop.setText(oscLiveActive?
                "Stop Live Chatbox":"Start Live Chatbox");
        if(oscQuickPause!=null){
            oscQuickPause.setVisibility(oscLiveActive?View.VISIBLE:View.GONE);
            oscQuickPause.setText(oscLivePaused?"Resume Live Chatbox":"Pause Live Chatbox");
        }
    }
    private void toggleOscPause(){
        if(!oscLiveActive)return;
        oscLivePaused=!oscLivePaused;
        if(!oscLivePaused){
            handler.removeCallbacks(oscLiveTick);
            handler.post(oscLiveTick);
        }
        refreshOscQuick();
        showPinnedFeedback(oscLivePaused?
            "Live Chatbox paused; no OSC status packets until resumed.":
            "Live Chatbox resumed.");
    }
    private void quickOscStartStop(){
        if(oscLiveActive){
            stopOscLive();
            showPinnedFeedback("Live Chatbox stopped.");
            return;
        }
        try{
            if(OscControlStore.liveSlotCount(this)<1)
                throw new IllegalStateException("Select an eligible chatbox first.");
            OscControlStore.liveChatboxAt(this,0);
        }catch(Exception e){
            showPinnedFeedback("Cannot start: "+e.getMessage());return;
        }
        final int speed=OscControlStore.liveInterval(this);
        showPinnedConfirmation("Start Live Chatbox every "+speed+
            " seconds? Sends the selected rotating chatboxes until stopped.",()->
            startOscLive(speed));
    }
    private void sendQuickPhrase(String line){
        if(!StaffGate.unlocked(this))return;
        eventsWorker.execute(()->{
            String error=null;
            try{VRChatOscChat.send(line,false);}
            catch(Exception e){error=e.getMessage();}
            final String result=error;
            handler.post(()->showPinnedFeedback(result==null?
                "OSC chat sent locally; verify it appeared in VRChat.":
                "OSC chat: "+result));
        });
    }

    private void showOscDashboard(){
        section(panelContent,"OSC · VRCHAT MOBILE");
        LinearLayout top=panelCard(panelContent);
        top.addView(text("Send directly to VRChat on this phone",
            14,CompanionStyle.TEXT,true));
        top.addView(text("Enable OSC in VRChat's Action Menu. OSC uses 127.0.0.1:9000 and does not need Shizuku, a PC or the TGC Relay.",
            12,CompanionStyle.MUTED,false));

        section(panelContent,"FAST CHATBOX CONTROLS");
        LinearLayout fast=panelCard(panelContent);
        oscQuickStatus=text(oscLiveSummary(),12,
            oscLiveActive?CompanionStyle.GOOD:CompanionStyle.MUTED,false);
        fast.addView(oscQuickStatus);
        oscQuickStartStop=action(fast,
            oscLiveActive?"Stop Live Chatbox":"Start Live Chatbox",true,
            this::quickOscStartStop);
        oscQuickPause=action(fast,
            oscLivePaused?"Resume Live Chatbox":"Pause Live Chatbox",false,
            this::toggleOscPause);
        refreshOscQuick();
        try{
            boolean exclusive=OscControlStore.officialOnly(this);
            Button only=action(fast,exclusive?
                "✓ Official TGC tags only":"Use official TGC tags only",
                false,()->{});
            only.setOnClickListener(v->{
                try{
                    boolean next=!OscControlStore.officialOnly(this);
                    OscControlStore.setOfficialOnly(this,next);
                    only.setText(next?"✓ Official TGC tags only":
                        "Use official TGC tags only");
                    refreshOscQuick();
                    showPinnedFeedback(next?"Only authorized official tags will rotate.":
                        "Personal chatboxes are included again where enabled.");
                }catch(Exception ex){showPinnedFeedback(ex.getMessage());}
            });
        }catch(Exception ignored){}
        paired(fast,"Edit status","Official tags",
            ()->go("mini_osc_status"),()->go("mini_osc_tags"));

        section(panelContent,"SEND SAVED PHRASE");
        LinearLayout quickPhrases=panelCard(panelContent);
        int phraseCount=0;
        try{
            JSONArray phrases=OscControlStore.phrases(this);
            for(int i=0;i<phrases.length()&&phraseCount<4;i++){
                JSONObject entry=phrases.optJSONObject(i);
                if(entry==null)continue;
                String value=entry.optString("text","");
                if(value.isEmpty())continue;
                final String line=value;
                String name=entry.optString("name","Saved phrase");
                action(quickPhrases,"Send · "+name,false,()->
                    sendQuickPhrase(line));
                phraseCount++;
            }
        }catch(Exception ignored){}
        if(phraseCount==0)quickPhrases.addView(text(
            "Save a quick phrase to send it with one tap here.",
            12,CompanionStyle.MUTED,false));
        action(quickPhrases,"All saved phrases",false,
            ()->go("mini_osc_presets"));

        section(panelContent,"EXPERIMENTAL MICROPHONE INPUT");
        LinearLayout mic=panelCard(panelContent);
        mic.addView(text("OSC Push-to-Talk latch (Android test)",
            14,CompanionStyle.TEXT,true));
        oscVoiceInfo=text("",12,CompanionStyle.GOLD,false);
        mic.addView(oscVoiceInfo);
        oscVoiceToggle=action(mic,"Hold PTT via OSC (1)",true,()->
            sendOscVoiceHold(!oscVoiceHoldRequested));
        action(mic,"Send emergency PTT RELEASE (0)",false,()->
            sendOscVoiceHold(false));
        mic.addView(text(
            "Experimental: VRChat Android may ignore /input/Voice. "+
            "Check the VRChat microphone icon yourself; a sent OSC packet "+
            "is NOT proof your microphone is broadcasting. Hold auto-releases "+
            "after 5 minutes or when the floating service stops. "+
            "Collapse the panel to keep holding; hide the bubble to release.",
            11,CompanionStyle.MUTED,false));
        updateOscVoiceUi();

        section(panelContent,"CHATBOX");
        LinearLayout chat=panelCard(panelContent);
        action(chat,"Compose & send chat text",true,()->go("mini_chat"));
        action(chat,"Saved quick phrases",false,()->go("mini_osc_presets"));

        section(panelContent,"LIVE CHATBOX");
        LinearLayout line=panelCard(panelContent);
        line.addView(text(oscLiveSummary(),12,
            oscLiveActive?CompanionStyle.GOOD:CompanionStyle.MUTED,false));
        action(line,"Configure / start Live Chatbox",true,()->go("mini_osc_status"));
        paired(line,"Official TGC tags","Rotation settings",
            ()->go("mini_osc_tags"),()->go("mini_osc_rotation"));
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
    /** A local OSC input request only; VRChat does not acknowledge the mic state. */
    private void sendOscVoiceHold(boolean hold){
        if(hold && (!StaffGate.unlocked(this)||!QuestDevice.canRunOverlay(this)
                ||!OverlayPreferences.desired(this)))return;
        if(hold && oscVoiceSending){
            Toast.makeText(this,"Please wait for the previous OSC mic command.",
                Toast.LENGTH_SHORT).show();return;
        }
        // Release takes priority even while a press is queued, by invalidating
        // its epoch. The single worker serializes all OSC press/release packets.
        final int epoch=++oscVoiceEpoch;
        oscVoiceHoldRequested=hold;
        oscVoiceSending=true;
        handler.removeCallbacks(oscVoiceSafety);
        updateOscVoiceUi();
        oscVoiceWorker.execute(()->{
            if(hold && (epoch!=oscVoiceEpoch || !StaffGate.unlocked(this)))return;
            String error=null;
            try{VRChatOscChat.sendVoiceInput(hold);}
            catch(Exception e){
                error=e.getMessage()==null?"Local OSC send failed":e.getMessage();
            }
            final String problem=error;
            handler.post(()->{
                if(epoch!=oscVoiceEpoch)return;
                oscVoiceSending=false;
                if(problem!=null){
                    if(hold)oscVoiceHoldRequested=false;
                    showPinnedFeedback("OSC voice command failed: "+problem);
                }else if(hold){
                    handler.postDelayed(oscVoiceSafety,OSC_VOICE_SAFETY_MS);
                    showPinnedFeedback("OSC PTT press sent; check VRChat's mic icon.");
                }else{
                    showPinnedFeedback("OSC PTT release sent (client not verified).");
                }
                updateOscVoiceUi();
            });
        });
    }
    private void updateOscVoiceUi(){
        if(oscVoiceBubbleBadge!=null)
            oscVoiceBubbleBadge.setVisibility(oscVoiceHoldRequested?View.VISIBLE:View.GONE);
        if(micSidecar!=null){
            micSidecar.setText(oscVoiceHoldRequested?"PTT\nHOLD":
                oscVoiceSending?"PTT\nWAIT":"PTT\nREL");
            micSidecar.setTextColor(oscVoiceHoldRequested?
                0xFFFFFFFF:CompanionStyle.TEXT);
            micSidecar.setBackground(CompanionStyle.shape(this,
                oscVoiceHoldRequested?0xFFB72C32:CompanionStyle.SURFACE,
                15,CompanionStyle.BORDER));
            micSidecar.setContentDescription(oscVoiceHoldRequested?
                "Release OSC push-to-talk":"Hold OSC push-to-talk");
        }
        if(oscVoiceInfo!=null)oscVoiceInfo.setText(
            oscVoiceHoldRequested?
                (oscVoiceSending?"Sending PTT press/release…":
                "OSC PTT HOLD SENT · actual mic state unknown"):
                (oscVoiceSending?"Sending OSC release…":
                "OSC PTT released / inactive · mic state unknown"));
        if(oscVoiceToggle!=null){
            oscVoiceToggle.setText(oscVoiceHoldRequested?
                "RELEASE PTT via OSC (0)":
                (oscVoiceSending?"Wait for release…":"Hold PTT via OSC (1)"));
            oscVoiceToggle.setEnabled(!oscVoiceSending||oscVoiceHoldRequested);
        }
        if(oscVoiceQuickRelease!=null){
            oscVoiceQuickRelease.removeAllViews();
            if(oscVoiceHoldRequested){
                oscVoiceQuickRelease.setVisibility(View.VISIBLE);
                Button release=CompanionStyle.button(this,
                    "RELEASE OSC PTT (mic state unverified)",true);
                oscVoiceQuickRelease.addView(release,CompanionStyle.buttonParams(this));
                release.setOnClickListener(v->sendOscVoiceHold(false));
            }else oscVoiceQuickRelease.setVisibility(View.GONE);
        }
        refreshPresenceDot();
    }
    private void releaseOscVoiceOnExit(){
        // Android can kill a process without onDestroy. Never suggest this is
        // guaranteed. This is a best-effort redundant OFF packet on normal exit.
        ++oscVoiceEpoch;
        boolean wasActive=oscVoiceHoldRequested||oscVoiceSending;
        oscVoiceHoldRequested=false;oscVoiceSending=false;
        handler.removeCallbacks(oscVoiceSafety);
        if(!wasActive)return;
        Thread release=new Thread(()->{
            try{VRChatOscChat.sendVoiceInput(false);}
            catch(Exception ignored){}
            try{Thread.sleep(120L);}catch(InterruptedException ignored){}
            try{VRChatOscChat.sendVoiceInput(false);}
            catch(Exception ignored){}
        },"TGC-OSC-PTT-release");
        release.start();
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
        paired(personal,"Favorite avatars","World bookmarks & favorites",
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
            showPinnedConfirmation("Stop VRChat player tracking on this phone?",()->{
                try{
                    startService(new Intent(this,MobileNodeService.class)
                        .setAction(MobileNodeService.STOP));
                    statusNote="Stopping tracking…";
                }catch(Exception error){
                    statusNote="Could not stop tracking. Check tracking settings.";
                }
                refreshStatus();
                handler.postDelayed(this::refreshStatus,2200L);
            });
            return;
        }else{
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
                (oscVoiceHoldRequested?". Experimental OSC PTT hold requested.":"")+
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
            trackingToggle.setText(live?"Stop tracking":"Start tracking");
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
        dismissModal();
        panel=null;panelContent=null;panelModalLayer=null;panelScroller=null;
        quickBarRow=null;
        oscVoiceQuickRelease=null;oscVoiceToggle=null;oscVoiceInfo=null;
        oscQuickStatus=null;oscQuickStartStop=null;oscQuickPause=null;
        recentRows=null;recentStatus=null;
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
        releaseOscVoiceOnExit();
        stopOscLive();
        if(restoreRunnable[0]!=null)handler.removeCallbacks(restoreRunnable[0]);
        handler.removeCallbacks(validate);
        handler.removeCallbacks(friendTick);
        handler.removeCallbacks(refreshTick);
        handler.removeCallbacks(presenceTick);
        verifier.shutdownNow();friendWorker.shutdownNow();eventsWorker.shutdownNow();
        oscVoiceWorker.shutdownNow();presenceWorker.shutdownNow();
        dismissPanel();if(mini!=null)mini.shutdown();
        if(micSidecar!=null){
            try{wm.removeView(micSidecar);}catch(Exception ignored){}
            micSidecar=null;micSidecarParams=null;
        }
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
