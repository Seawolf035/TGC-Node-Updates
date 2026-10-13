package club.thegamerclub.nodelab;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Quest-native, NO OVERLAY service. Lives only after explicit user action.
 * Separate from FloatingCompanionService so phone behavior is unchanged.
 * A sent local OSC packet is not confirmation of VRChat mic/chatbox state.
 */
public final class QuestOscService extends Service {
    public static final String START_LIVE="club.thegamerclub.nodelab.QUEST_OSC_LIVE_START";
    public static final String STOP_LIVE="club.thegamerclub.nodelab.QUEST_OSC_LIVE_STOP";
    public static final String PAUSE_LIVE="club.thegamerclub.nodelab.QUEST_OSC_LIVE_PAUSE";
    public static final String VOICE_HOLD="club.thegamerclub.nodelab.QUEST_OSC_PTT_HOLD";
    public static final String VOICE_RELEASE="club.thegamerclub.nodelab.QUEST_OSC_PTT_RELEASE";
    public static final String STOP_ALL="club.thegamerclub.nodelab.QUEST_OSC_STOP";
    private static final String CHANNEL="tgc_quest_native_osc";
    private static final int NOTICE=328;
    private static volatile boolean live=false,paused=false,held=false;
    private static volatile String lastError="",lastText="";
    private static volatile long lastSentAt=0;
    public static boolean live(){return live;}
    public static boolean paused(){return paused;}
    public static boolean holding(){return held;}
    public static String summary(){
        String text=live?(paused?"Live Chatbox PAUSED":"Live Chatbox ON"):"Live Chatbox OFF";
        text+=" · "+(held?"PTT HOLD requested":"PTT released");
        if(!lastError.isEmpty())text+=" · "+lastError;
        return text;
    }
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final ExecutorService sends=Executors.newSingleThreadExecutor();
    private final ExecutorService voice=Executors.newSingleThreadExecutor();
    private int liveEpoch=0,voiceEpoch=0,slot=0,interval=10;
    private long slotStarted=0;
    private String owner="";
    private final Runnable safety=()->{
        if(held)releaseVoice();
    };
    private final Runnable liveTick=new Runnable(){
        @Override public void run(){
            if(!live)return;
            if(!StaffGate.unlocked(QuestOscService.this)){
                stopAll();return;
            }
            if(paused){handler.postDelayed(this,2000);return;}
            if(VRChatOscChat.sinceManualChatMs()<20_000L){
                handler.postDelayed(this,5000);return;
            }
            final int epoch=liveEpoch;
            sends.execute(()->{
                String error=null,output=null;
                boolean fatal=false;
                try{
                    String current=VRChatSession.load(QuestOscService.this)
                        .optString("userId","");
                    if(!owner.equals(current))throw new SecurityException("VRChat account changed");
                    int count=OscControlStore.liveSlotCount(QuestOscService.this);
                    if(count<1)throw new SecurityException("No eligible chatboxes remain");
                    long now=SystemClock.elapsedRealtime();
                    if(slot>=count)slot=0;
                    if(count>1&&now-slotStarted>=
                        1000L*OscControlStore.rotationInterval(QuestOscService.this)){
                        slot=(slot+1)%count;
                        slotStarted=now;
                    }
                    output=OscControlStore.liveChatboxAt(QuestOscService.this,slot);
                    if(epoch==liveEpoch&&live)
                        VRChatOscChat.sendLive(output);
                }catch(SecurityException x){
                    fatal=true;error=x.getMessage();
                }catch(Exception x){error=x.getMessage();}
                final boolean stop=fatal;final String problem=error,value=output;
                handler.post(()->{
                    if(epoch!=liveEpoch)return;
                    if(stop){
                        lastError=problem==null?"Access or account changed":problem;
                        stopLive();maybeStop();
                        return;
                    }
                    lastError=problem==null?"":problem;
                    if(problem==null&&value!=null){
                        lastText=value;lastSentAt=System.currentTimeMillis();
                    }
                    refreshNotification();
                    if(live)handler.postDelayed(liveTick,interval*1000L);
                });
            });
        }
    };
    @Override public void onCreate(){
        super.onCreate();
        NotificationManager nm=getSystemService(NotificationManager.class);
        if(nm!=null){
            NotificationChannel ch=new NotificationChannel(CHANNEL,
                "Quest OSC controls",NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("User-started VRChat Live Chatbox and PTT controls");
            nm.createNotificationChannel(ch);
        }
    }
    private PendingIntent command(String action,int id){
        return PendingIntent.getService(this,id,
            new Intent(this,QuestOscService.class).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }
    private Notification notice(){
        PendingIntent open=PendingIntent.getActivity(this,329,
            new Intent(this,QuestToolsActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this,CHANNEL)
            .setSmallIcon(R.drawable.tgc_logo)
            .setContentTitle("TGC Quest OSC")
            .setContentText(summary())
            .setContentIntent(open)
            .setOngoing(true)
            .addAction(new Notification.Action.Builder(null,
                "RELEASE PTT",command(VOICE_RELEASE,330)).build())
            .addAction(new Notification.Action.Builder(null,
                "Stop OSC",command(STOP_ALL,331)).build())
            .build();
    }
    private void refreshNotification(){
        try{
            NotificationManager nm=getSystemService(NotificationManager.class);
            if(nm!=null)nm.notify(NOTICE,notice());
        }catch(Exception ignored){}
    }
    @Override public int onStartCommand(Intent intent,int flags,int id){
        String action=intent==null?"":intent.getAction();
        if(STOP_ALL.equals(action)){
            stopAll();stopSelf();return START_NOT_STICKY;
        }
        if(VOICE_RELEASE.equals(action)){
            releaseVoice();maybeStop();
            return START_NOT_STICKY;
        }
        if(STOP_LIVE.equals(action)){
            stopLive();maybeStop();return START_NOT_STICKY;
        }
        if(!QuestDevice.isQuestDevice()||!StaffGate.unlocked(this)){
            stopAll();stopSelf();return START_NOT_STICKY;
        }
        try{startForeground(NOTICE,notice());}
        catch(Exception e){stopAll();stopSelf();return START_NOT_STICKY;}
        if(START_LIVE.equals(action)){
            int requested=intent.getIntExtra("seconds",10);
            if(requested!=5&&requested!=10&&requested!=15&&requested!=30)
                requested=10;
            try{
                String account=VRChatSession.load(this).optString("userId","");
                if(!account.matches("usr_[0-9a-fA-F-]{36}"))
                    throw new SecurityException("Personal VRChat sign-in required");
                if(OscControlStore.liveSlotCount(this)<1)
                    throw new IllegalStateException("Select a chatbox first");
                OscControlStore.liveChatboxAt(this,0);
                stopLive();
                owner=account;
                interval=requested;
                slot=0;slotStarted=SystemClock.elapsedRealtime();
                lastError="";
                live=true;paused=false;
                handler.post(liveTick);
            }catch(Exception e){
                lastError=e.getMessage()==null?"Cannot start Live Chatbox":e.getMessage();
                maybeStop();
            }
        }else if(PAUSE_LIVE.equals(action)){
            if(live){
                paused=!paused;
                if(!paused){
                    handler.removeCallbacks(liveTick);
                    handler.post(liveTick);
                }
            }
        }else if(VOICE_HOLD.equals(action)){
            holdVoice();
        }else{
            maybeStop();
        }
        refreshNotification();
        return START_NOT_STICKY; // Never resume held mic automatically after process death.
    }
    private void holdVoice(){
        try{
            String account=VRChatSession.load(this).optString("userId","");
            if(!account.matches("usr_[0-9a-fA-F-]{36}"))
                throw new SecurityException("Personal VRChat sign-in required");
            if(held)return;
            held=true;
            final int epoch=++voiceEpoch;
            handler.removeCallbacks(safety);
            voice.execute(()->{
                if(epoch!=voiceEpoch||!held||!StaffGate.unlocked(this))return;
                try{VRChatOscChat.sendVoiceInput(true);}
                catch(Exception e){
                    handler.post(()->{if(epoch==voiceEpoch)releaseVoice();});
                }
            });
            handler.postDelayed(safety,300_000L);
        }catch(Exception e){lastError="PTT unavailable: "+e.getMessage();}
    }
    private void releaseVoice(){
        held=false;
        ++voiceEpoch;
        handler.removeCallbacks(safety);
        voice.execute(()->{
            try{VRChatOscChat.sendVoiceInput(false);}catch(Exception ignored){}
            try{Thread.sleep(125);}catch(InterruptedException ignored){}
            try{VRChatOscChat.sendVoiceInput(false);}catch(Exception ignored){}
        });
        refreshNotification();
    }
    private void stopLive(){
        live=false;paused=false;++liveEpoch;
        handler.removeCallbacks(liveTick);
        refreshNotification();
    }
    private void maybeStop(){
        if(!live&&!held)stopSelf();
    }
    private void stopAll(){
        stopLive();
        releaseVoice();
    }
    @Override public void onDestroy(){
        stopAll();
        handler.removeCallbacks(liveTick);
        handler.removeCallbacks(safety);
        sends.shutdownNow();
        voice.shutdown();
        live=false;paused=false;held=false;
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent){return null;}
}
