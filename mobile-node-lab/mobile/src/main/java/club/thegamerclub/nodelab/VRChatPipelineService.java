package club.thegamerclub.nodelab;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import org.json.JSONObject;
import java.net.URLEncoder;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** Explicitly opt-in visible VRChat pipeline; no automatic invite actions. */
public final class VRChatPipelineService extends Service {
    private static final int FOREGROUND_ID=729;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private final OkHttpClient client=new OkHttpClient.Builder()
        .pingInterval(30,TimeUnit.SECONDS).build();
    private WebSocket socket;
    private boolean connecting=false,stopping=false,refreshPending=false;
    private int attempts=0;
    private String identity="";
    private final Runnable retry=this::connect;
    public static void start(Context ctx){
        if(!InboxAlertSettings.liveEnabled(ctx)||!InboxAlertSettings.active(ctx))return;
        try{
            ContextCompat.startForegroundService(ctx,
                new Intent(ctx,VRChatPipelineService.class));
        }catch(RuntimeException ignored){
            // OS background start restriction; periodic catch-up stays enabled.
        }
    }
    public static void stop(Context ctx){
        ctx.stopService(new Intent(ctx,VRChatPipelineService.class));
    }
    @Override public int onStartCommand(Intent intent,int flags,int id){
        if(!InboxAlertSettings.liveEnabled(this)||!InboxAlertSettings.active(this)){
            stopSelf();return START_NOT_STICKY;
        }
        InboxAlertWorker.ensureChannels(this);
        // A user may switch personal VRChat accounts without stopping the
        // Companion. Never leave a socket authorized to the previous user.
        if(socket!=null&&!identity.equals(account())){
            socket.close(1000,"");
            socket=null;identity="";
            connecting=false;
        }
        startForeground(FOREGROUND_ID,new NotificationCompat.Builder(this,
            InboxAlertWorker.liveChannel())
            .setSmallIcon(R.drawable.tgc_logo)
            .setContentTitle("Companion · Live VRChat inbox")
            .setContentText("Connected or reconnecting · tap Companion to manage")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).build());
        connect();
        return START_STICKY;
    }
    private String account(){
        try{
            JSONObject s=VRChatSession.load(this);
            if(s.optString("auth","").isEmpty()||s.optBoolean("needs2fa",false))return "";
            String id=s.optString("userId","");
            return id.matches("usr_[0-9a-fA-F-]{36}")?id:"";
        }catch(Exception e){return "";}
    }
    private void connect(){
        if(stopping||connecting||socket!=null)return;
        if(!InboxAlertSettings.liveEnabled(this)){stopSelf();return;}
        connecting=true;
        io.execute(()->{
            try{
                StaffGate.verifyOnline(this);
                if(!StaffGate.unlocked(this))
                    throw new SecurityException("Staff authorization unavailable");
                String user=account();
                if(user.isEmpty())throw new SecurityException("VRChat login required");
                String token=VRChatApi.pipelineToken(this);
                if(!user.equals(account())||stopping)
                    throw new SecurityException("Account changed");
                String url="wss://pipeline.vrchat.cloud/?auth="+
                    URLEncoder.encode(token,"UTF-8");
                client.newWebSocket(new Request.Builder().url(url).build(),
                    new WebSocketListener(){
                    @Override public void onOpen(WebSocket ws,Response response){
                        main.post(()->{
                            if(stopping||!user.equals(account())){ws.close(1000,"");return;}
                            socket=ws;identity=user;connecting=false;attempts=0;
                            reconcile();
                        });
                    }
                    @Override public void onMessage(WebSocket ws,String message){
                        if(stopping)return;
                        if(!user.equals(account())){
                            ws.close(1000,"");
                            return;
                        }
                        try{
                            String t=new JSONObject(message).optString("type","");
                            if(t.equals("notification")||t.equals("notification-v2")||
                               t.equals("notification-v2-update")||
                               t.equals("notification-v2-delete")||
                               t.equals("see-notification")||
                               t.equals("hide-notification"))
                                main.post(()->reconcile());
                        }catch(Exception ignored){}
                    }
                    @Override public void onFailure(WebSocket ws,Throwable err,
                        Response response){main.post(()->disconnected(ws));}
                    @Override public void onClosed(WebSocket ws,int code,String reason){
                        main.post(()->disconnected(ws));
                    }
                });
            }catch(SecurityException denied){
                main.post(()->{connecting=false;stopSelf();});
            }catch(Exception network){
                main.post(()->{connecting=false;scheduleRetry();});
            }
        });
    }
    private void disconnected(WebSocket ws){
        if(socket==ws){socket=null;identity="";}
        connecting=false;
        scheduleRetry();
    }
    private void scheduleRetry(){
        if(stopping||!InboxAlertSettings.liveEnabled(this))return;
        long delay=Math.min(300_000L,15_000L*(1L<<Math.min(attempts++,4)));
        main.removeCallbacks(retry);main.postDelayed(retry,delay);
    }
    private void reconcile(){
        if(stopping||refreshPending||socket==null)return;
        refreshPending=true;
        main.postDelayed(()->io.execute(()->{
            try{
                if(!stopping&&identity.equals(account())&&
                   InboxAlertSettings.liveEnabled(this)){
                    if(!StaffGate.unlocked(this))StaffGate.verifyOnline(this);
                    InboxAlertWorker.refreshAndNotify(this);
                }
            }catch(Exception ignored){}
            finally{main.post(()->refreshPending=false);}
        }),1200);
    }
    @Override public IBinder onBind(Intent intent){return null;}
    @Override public void onDestroy(){
        stopping=true;main.removeCallbacks(retry);
        if(socket!=null){socket.close(1000,"");socket=null;}
        io.shutdownNow();
        client.dispatcher().executorService().shutdown();
        super.onDestroy();
    }
}
