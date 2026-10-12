package club.thegamerclub.nodelab;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
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
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import org.json.JSONObject;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Optional draggable Mini VRCX launcher above VRChat Mobile.
 * It NEVER opens Shizuku or parses logs: those belong exclusively to MobileNodeService.
 * A separate visible foreground-service notification lets users stop it at any time.
 */
public final class FloatingCompanionService extends Service {
    public static final String ACTION_START="club.thegamerclub.nodelab.OVERLAY_START";
    public static final String ACTION_STOP="club.thegamerclub.nodelab.OVERLAY_STOP";
    private static final String CHANNEL="tgc_companion_overlay";
    private static final int NOTICE=223;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final ExecutorService verifier=Executors.newSingleThreadExecutor();
    private final ExecutorService friendWorker=Executors.newSingleThreadExecutor();
    private boolean friendBusy=false;
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
                        // VRChat login, offline state or rate limits: preserve the last complete snapshot.
                    }finally{
                        handler.post(()->friendBusy=false);
                    }
                });
            }
            handler.postDelayed(this,150_000L);
        }
    };
    private WindowManager wm;
    private View bubble,panel;
    private WindowManager.LayoutParams bubbleParams;
    private boolean verifying=false;
    private TextView tracking,liveEvents;
    private final ExecutorService feedWorker=Executors.newSingleThreadExecutor();
    private boolean feedBusy=false;
    private final Runnable feedTick=new Runnable(){
        @Override public void run(){
            if(panel!=null&&liveEvents!=null&&StaffGate.unlocked(FloatingCompanionService.this)&&!feedBusy){
                feedBusy=true;
                feedWorker.execute(()->{
                    StringBuilder result=new StringBuilder();
                    try(MobileOutbox outbox=new MobileOutbox(FloatingCompanionService.this)){
                        org.json.JSONArray recent=outbox.recentTrackedEvents(3);
                        for(int i=0;i<recent.length();i++){
                            org.json.JSONObject e=recent.optJSONObject(i);
                            if(e==null)continue;
                            if(result.length()>0)result.append("\\n");
                            result.append("• ").append(e.optString("display_name",e.optString("user_id","Player")));
                        }
                    }catch(Exception ignored){}
                    final String display=result.length()==0?
                        "No captured player events yet. Start Mobile Node tracking to populate this feed.":
                        result.toString();
                    handler.post(()->{
                        feedBusy=false;
                        if(liveEvents!=null)liveEvents.setText(display);
                    });
                });
            }
            handler.postDelayed(this,20_000L);
        }
    };
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
            updateStatus();
            handler.postDelayed(this,90_000L);
        }
    };
    private int dp(float n){return CompanionStyle.dp(this,n);}
    @Override public void onCreate(){
        super.onCreate();
        wm=(WindowManager)getSystemService(WINDOW_SERVICE);
        NotificationManager n=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        n.createNotificationChannel(new NotificationChannel(CHANNEL,"TGC floating Companion",
            NotificationManager.IMPORTANCE_LOW));
    }
    private Notification notice(){
        Intent open=new Intent(this,CompanionActivity.class);
        PendingIntent launch=PendingIntent.getActivity(this,11,open,
            PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Intent stop=new Intent(this,FloatingCompanionService.class).setAction(ACTION_STOP);
        PendingIntent close=PendingIntent.getService(this,12,stop,
            PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this,CHANNEL)
            .setSmallIcon(R.drawable.tgc_logo)
            .setContentTitle("TGC Mini VRCX bubble")
            .setContentText("Tap the floating bubble to open quick tools")
            .setContentIntent(launch)
            .addAction(new Notification.Action.Builder(null,"Hide bubble",close).build())
            .setOngoing(true).build();
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(intent!=null&&ACTION_STOP.equals(intent.getAction())){
            stopSelf();return START_NOT_STICKY;
        }
        if(!StaffGate.unlocked(this)||!Settings.canDrawOverlays(this)){
            stopSelf();return START_NOT_STICKY;
        }
        startForeground(NOTICE,notice());
        if(bubble==null){
            try{showBubble();}
            catch(Exception e){stopSelf();return START_NOT_STICKY;}
        }
        getSharedPreferences("tgc_overlay",0).edit().putBoolean("running",true).apply();
        handler.removeCallbacks(validate);handler.postDelayed(validate,1500L);
        handler.removeCallbacks(friendTick);handler.postDelayed(friendTick,3000L);
        handler.removeCallbacks(feedTick);handler.postDelayed(feedTick,4000L);
        return START_NOT_STICKY;
    }
    private WindowManager.LayoutParams params(int width,int height){
        WindowManager.LayoutParams p=new WindowManager.LayoutParams(
            width,height,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT);
        p.gravity=Gravity.TOP|Gravity.START;
        return p;
    }
    private void showBubble(){
        TextView b=CompanionStyle.text(this,"TGC",15,CompanionStyle.BG,true);
        b.setGravity(Gravity.CENTER);
        b.setContentDescription("Open TGC Mini VRCX overlay");
        b.setBackground(CompanionStyle.shape(this,CompanionStyle.ACCENT,50,CompanionStyle.BORDER));
        bubble=b;
        bubbleParams=params(dp(58),dp(58));
        int width=getResources().getDisplayMetrics().widthPixels;
        int height=getResources().getDisplayMetrics().heightPixels;
        bubbleParams.x=Math.max(0,Math.min(width-dp(60),
            getSharedPreferences("tgc_overlay",0).getInt("x",width-dp(80))));
        bubbleParams.y=Math.max(dp(28),Math.min(height-dp(110),
            getSharedPreferences("tgc_overlay",0).getInt("y",dp(165))));
        b.setOnTouchListener(new View.OnTouchListener(){
            private float downX,downY;private int firstX,firstY;private boolean dragged;
            @Override public boolean onTouch(View v,MotionEvent e){
                switch(e.getActionMasked()){
                    case MotionEvent.ACTION_DOWN:
                        downX=e.getRawX();downY=e.getRawY();
                        firstX=bubbleParams.x;firstY=bubbleParams.y;dragged=false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        int dx=Math.round(e.getRawX()-downX),dy=Math.round(e.getRawY()-downY);
                        if(Math.abs(dx)>dp(7)||Math.abs(dy)>dp(7))dragged=true;
                        if(dragged){
                            bubbleParams.x=Math.max(0,Math.min(width-dp(60),firstX+dx));
                            bubbleParams.y=Math.max(dp(25),Math.min(height-dp(75),firstY+dy));
                            wm.updateViewLayout(bubble,bubbleParams);
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        if(dragged){
                            getSharedPreferences("tgc_overlay",0).edit()
                                .putInt("x",bubbleParams.x).putInt("y",bubbleParams.y).apply();
                            dismissPanel();
                        }else togglePanel();
                        return true;
                    default:return true;
                }
            }
        });
        wm.addView(b,bubbleParams);
    }
    private void togglePanel(){
        if(panel!=null){dismissPanel();return;}
        if(!StaffGate.unlocked(this)){stopSelf();return;}
        LinearLayout frame=CompanionStyle.card(this);
        frame.setElevation(dp(8));
        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.addView(frame);
        panel=scroll;
        TextView title=CompanionStyle.text(this,"MINI VRCX · QUICK TOOLS",14,
            CompanionStyle.ACCENT,true);
        frame.addView(title);
        tracking=CompanionStyle.text(this,"Checking Mobile Node…",13,CompanionStyle.MUTED,false);
        tracking.setPadding(0,dp(9),0,dp(9));frame.addView(tracking);
        frame.addView(CompanionStyle.section(this,"LATEST MOBILE LOG EVENTS"));
        liveEvents=CompanionStyle.text(this,"Loading recent captured players…",
            12,CompanionStyle.MUTED,false);
        frame.addView(liveEvents);
        addButton(frame,"Friends by location",FriendHubActivity.class,"locations");
        addButton(frame,"Friend log",FriendHubActivity.class,"log");
        addButton(frame,"Activity feed",FriendHubActivity.class,"activity");
        addButton(frame,"Notifications",VRChatInboxActivity.class,"");
        addButton(frame,"Recently seen players",VRChatBrowseActivity.class,"encounters");
        addButton(frame,"Full Companion",CompanionActivity.class,"");
        Button close=CompanionStyle.button(this,"Close bubble",false);
        frame.addView(close,CompanionStyle.buttonParams(this));
        close.setOnClickListener(v->stopSelf());
        int width=getResources().getDisplayMetrics().widthPixels;
        int height=getResources().getDisplayMetrics().heightPixels;
        WindowManager.LayoutParams p=params(Math.min(dp(315),width-dp(24)),
            Math.min(dp(485),height-dp(135)));
        p.x=Math.max(dp(8),Math.min(width-Math.min(dp(315),width-dp(24))-dp(8),bubbleParams.x));
        p.y=Math.max(dp(28),Math.min(height-dp(530),bubbleParams.y+dp(66)));
        try{wm.addView(panel,p);updateStatus();handler.post(feedTick);}
        catch(Exception e){panel=null;tracking=null;liveEvents=null;}
    }
    private void addButton(LinearLayout parent,String label,Class<?> destination,String mode){
        Button b=CompanionStyle.button(this,label,false);
        parent.addView(b,CompanionStyle.buttonParams(this));
        b.setOnClickListener(v->{
            if(!StaffGate.unlocked(this)){stopSelf();return;}
            Intent open=new Intent(this,destination).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if(destination==FriendHubActivity.class)open.putExtra("tab",mode);
            if(destination==VRChatBrowseActivity.class)open.putExtra("mode",mode);
            if(destination==VRChatInboxActivity.class)open.putExtra("filter","all");
            try{startActivity(open);dismissPanel();}
            catch(Exception ignored){dismissPanel();}
        });
    }
    private void updateStatus(){
        if(tracking==null)return;
        android.content.SharedPreferences p=getSharedPreferences("tgc_mobile_node_status",0);
        long pulse=p.getLong("service_heartbeat_at",0);
        boolean active=p.getBoolean("running",false)&&pulse>0&&
            System.currentTimeMillis()-pulse<90_000L;
        tracking.setText(active?"● Mobile Node tracking active":"○ Mobile Node tracking paused");
        tracking.setTextColor(active?CompanionStyle.GOOD:CompanionStyle.GOLD);
    }
    private void dismissPanel(){
        if(panel!=null){
            try{wm.removeView(panel);}catch(Exception ignored){}
            panel=null;tracking=null;
        }
    }
    @Override public void onDestroy(){
        handler.removeCallbacks(validate);
        handler.removeCallbacks(friendTick);
        handler.removeCallbacks(feedTick);
        verifier.shutdownNow();friendWorker.shutdownNow();feedWorker.shutdownNow();dismissPanel();
        if(bubble!=null){
            try{wm.removeView(bubble);}catch(Exception ignored){}
            bubble=null;
        }
        getSharedPreferences("tgc_overlay",0).edit().putBoolean("running",false).apply();
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent){return null;}
}
