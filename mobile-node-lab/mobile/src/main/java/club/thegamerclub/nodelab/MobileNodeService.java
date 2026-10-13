package club.thegamerclub.nodelab;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;
import rikka.shizuku.Shizuku;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Visible user-started VRChat monitoring service.
 * Shizuku watcher and relay uploader have INDEPENDENT work loops: slow relay
 * calls never block saving join events. No account auth tokens in logs/status.
 */
public final class MobileNodeService extends Service {
  public static final String START="club.thegamerclub.nodelab.START_NODE";
  public static final String STOP="club.thegamerclub.nodelab.STOP_NODE";
  private static final String CHANNEL="tgc_mobile_node";
  private static final int NOTIFICATION_ID=221, SHIZUKU_VERSION=5;
  private final ExecutorService collection=Executors.newSingleThreadExecutor();
  private final ExecutorService upload=Executors.newSingleThreadExecutor();
  private final Handler handler=new Handler(Looper.getMainLooper());
  private volatile boolean running=false, bound=false, shuttingDown=false, trackingStarted=false;
  private volatile IPrivilegedProbe watcher;
  private MobileOutbox outbox;
  private volatile long lastAuth=0;
  private volatile String watcherState="Waiting for Shizuku";
  private volatile String relayState="Not checked";
  private volatile long lastEventTime=0;

  private final Shizuku.OnBinderReceivedListener onBinderReceived=()->{
    if(running && watcher==null){bound=false;connectWatcher();}
  };
  private final Shizuku.OnBinderDeadListener onBinderDead=()->{
    watcher=null;bound=false;trackingStarted=false;
    watcherState="Shizuku stopped / disconnected";
    persist();
  };
  private final ServiceConnection connection=new ServiceConnection(){
    @Override public void onServiceConnected(ComponentName name,IBinder service){
      watcher=IPrivilegedProbe.Stub.asInterface(service);
      bound=true;
      trackingStarted=false;
      watcherState="Shizuku connected; starting live watcher";
      persist();
    }
    @Override public void onServiceDisconnected(ComponentName name){
      watcher=null;bound=false;trackingStarted=false;
      watcherState="Shizuku watcher disconnected; retrying";
      persist();
    }
  };

  @Override public void onCreate(){
    super.onCreate();
    outbox=new MobileOutbox(this);
    getSystemService(NotificationManager.class).createNotificationChannel(
      new NotificationChannel(CHANNEL,"TGC Mobile Node",NotificationManager.IMPORTANCE_LOW));
    Shizuku.addBinderReceivedListenerSticky(onBinderReceived);
    Shizuku.addBinderDeadListener(onBinderDead);
  }
  private Shizuku.UserServiceArgs serviceArgs(){
    return new Shizuku.UserServiceArgs(new ComponentName(this,PrivilegedProbe.class))
      .daemon(true).processNameSuffix("tgc_mobile_watch").version(SHIZUKU_VERSION).debuggable(false);
  }
  private Notification notification(){
    PendingIntent open=PendingIntent.getActivity(this,1,
      new Intent(this,MobileDeployActivity.class),
      PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    PendingIntent stop=PendingIntent.getService(this,2,
      new Intent(this,MobileNodeService.class).setAction(STOP),
      PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    return new Notification.Builder(this,CHANNEL)
      .setSmallIcon(android.R.drawable.stat_notify_sync)
      .setContentTitle("TGC Companion • Player tracking")
      .setContentText("Tracking is on · tap to view your activity")
      .setContentIntent(open).setOngoing(true)
      .addAction(new Notification.Action.Builder(null,"Stop",stop).build()).build();
  }
  @Override public int onStartCommand(Intent intent,int flags,int startId){
    if(intent!=null && STOP.equals(intent.getAction())){
      stopCollection("Stopped by user");
      return START_NOT_STICKY;
    }
    try{
      if(Build.VERSION.SDK_INT>=34){
        startForeground(NOTIFICATION_ID,notification(),
          android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
      }else{startForeground(NOTIFICATION_ID,notification());}
    }catch(Exception e){
      watcherState="Android denied foreground service: "+e.getClass().getSimpleName();
      running=false;persist();stopSelf();return START_NOT_STICKY;
    }
    if(!running){
      running=true;shuttingDown=false;
      watcherState="Foreground service started, waiting for Shizuku";
      relayState="Waiting to verify deployment";
      persist();
      collection.execute(this::collectorLoop);
      upload.execute(this::relayLoop);
    }
    return START_STICKY;
  }
  private void connectWatcher(){
    if(!running || shuttingDown || bound || watcher!=null)return;
    try{
      if(!Shizuku.pingBinder()){
        watcherState="Shizuku connection not ready. Open Shizuku and confirm Running.";
      }else if(Shizuku.checkSelfPermission()!=PackageManager.PERMISSION_GRANTED){
        watcherState="Shizuku is running but TGC Companion is not authorized. Open Node Lab to grant permission.";
      }else{
        // Mark bound before sending asynchronous bind to avoid duplicate requests.
        bound=true;
        watcherState="Binding Shizuku live watcher...";
        Shizuku.bindUserService(serviceArgs(),connection);
      }
    }catch(Exception e){
      bound=false;
      watcherState="Shizuku binding failed: "+e.getClass().getSimpleName();
    }
    persist();
  }

  private void collectorLoop(){
    while(running&&!shuttingDown){
      try{
        if(watcher==null)connectWatcher();
        IPrivilegedProbe active=watcher;
        if(active!=null){
          if(!trackingStarted){
            // Preserve the local observed roster if we simply reconnect to an
            // already-running watcher. Clear it only when starting a new log session.
            boolean alreadyWatching=new JSONObject(active.getWatchStats())
                .optBoolean("watching",false);
            active.startWatching();
            if(!alreadyWatching)outbox.resetCurrentPlayers();
            trackingStarted=true;
          }
          JSONObject stats=new JSONObject(active.getWatchStats());
          watcherState=stats.optBoolean("watching")?"Watching VRChat output logs":
            "Shizuku connected but watcher not running";
          getSharedPreferences("tgc_mobile_node_status",0).edit()
            .putInt("watch_polls",stats.optInt("polls"))
            .putInt("watch_joins",stats.optInt("joins"))
            .putInt("watch_leaves",stats.optInt("leaves"))
            .putInt("watch_unrecognized",stats.optInt("unrecognized"))
            .putInt("watch_errors",stats.optInt("read_errors"))
            .putInt("watch_rotations",stats.optInt("rotations"))
            .putInt("watch_pending",stats.optInt("pending"))
            .putLong("watch_bytes",stats.optLong("bytes"))
            .putLong("last_poll_at",stats.optLong("last_check"))
            .putLong("last_append_at",stats.optLong("last_append"))
            .putBoolean("watch_source",stats.optBoolean("source_found"))
            .apply();
          captureFromWatcher(active);
          captureLocalActivity(active);
        }
      }catch(Exception e){
        watcherState="Watcher error: "+e.getClass().getSimpleName();
        // The Shizuku binder might have restarted; force clean reconnection.
        watcher=null;bound=false;trackingStarted=false;
      }
      persist();
      sleep(5000);
    }
  }
  private void captureFromWatcher(IPrivilegedProbe active)throws Exception{
    JSONArray events=new JSONArray(active.pendingSightings(150));
    if(events.length()==0)return;
    // Only ACK events after durable SQLite commit. Invalid entries are not counted.
    int stored=outbox.insert(events);
    JSONArray ack=new JSONArray();
    for(int i=0;i<events.length();i++){
      JSONObject item=events.optJSONObject(i);
      if(item!=null){
        String id=item.optString("event_id","");
        if(id.matches("mobile_[0-9a-fA-F-]{36}"))ack.put(id);
      }
    }
    active.ackSightings(ack.toString());
    if(stored>0){
      getSharedPreferences("tgc_mobile_node_status",0).edit()
        .putInt("captured_total",getSharedPreferences("tgc_mobile_node_status",0).getInt("captured_total",0)+stored)
        .putLong("last_capture_at",System.currentTimeMillis()).apply();
    }
  }
  /** Local-only VRCX activity: never included in the Node's Relay payload. */
  private void captureLocalActivity(IPrivilegedProbe active)throws Exception{
    JSONArray observed=new JSONArray(active.pendingActivity(150));
    if(observed.length()==0)return;
    int saved=outbox.insertLocalActivity(observed);
    JSONArray ack=new JSONArray();
    for(int i=0;i<observed.length();i++){
      JSONObject item=observed.optJSONObject(i);
      if(item==null)continue;
      String id=item.optString("event_id","");
      String kind=item.optString("kind","");
      String uid=item.optString("user_id","");
      if(id.matches("local_[0-9a-fA-F-]{36}") &&
        ("instance".equals(kind) ||
        (("join".equals(kind)||"leave".equals(kind)) &&
          uid.matches("usr_[0-9a-fA-F-]{36}"))))ack.put(id);
    }
    // ACK only after durable SQLite commit; retries deduplicate by event_id.
    active.ackActivity(ack.toString());
    if(saved>0)getSharedPreferences("tgc_mobile_node_status",0).edit()
      .putLong("last_local_activity_at",System.currentTimeMillis()).apply();
  }
  private void relayLoop(){
    while(running&&!shuttingDown){
      try{
        MobileCredentials.Record record=MobileCredentials.load(this);
        if(record==null){
          relayState="Not deployed: redeem your owner-issued Mobile Companion link";
          lastAuth=0;
        }else{
          if(System.currentTimeMillis()-lastAuth>90000){
            JSONObject reply=MobileRelay.post(record.url,MobileRelay.authorized(record,"node_status"));
            if(!reply.optBoolean("verified") ||
               !"mobile_companion".equals(reply.optString("client_type")) ||
               !record.discordId.equals(reply.optString("discord_id"))){
              StaffGate.lock();
              relayState="Relay did not verify mobile staff deployment";
              lastAuth=0;
              persist();
              sleep(20000);
              continue;
            }
            // Reuse this independent live staff approval to keep the Companion
            // unlocked while VRChat Mobile occupies the foreground.
            StaffGate.confirmRelayApproval(record,reply);
            lastAuth=System.currentTimeMillis();
          }
          if(System.currentTimeMillis()-lastAuth<180000){
            JSONArray batch=outbox.batch(100);
            if(batch.length()>0){
              JSONObject request=MobileRelay.authorized(record,"submit_sightings");
              request.put("sightings",batch);
              JSONObject result=MobileRelay.post(record.url,request);
              JSONArray accepted=result.optJSONArray("accepted_event_ids");
              JSONArray rejected=result.optJSONArray("rejected_event_ids");
              if(accepted==null && rejected==null){
                throw new IllegalStateException("Relay did not return event acknowledgments");
              }
              JSONArray ack=new JSONArray();
              int a=0,b=0;
              if(accepted!=null)for(int i=0;i<accepted.length();i++){ack.put(accepted.optString(i));a++;}
              if(rejected!=null)for(int i=0;i<rejected.length();i++){ack.put(rejected.optString(i));b++;}
              outbox.remove(ack);
              android.content.SharedPreferences p=getSharedPreferences("tgc_mobile_node_status",0);
              p.edit().putInt("uploaded_total",p.getInt("uploaded_total",0)+a)
                .putInt("rejected_total",p.getInt("rejected_total",0)+b)
                .putLong("last_upload_at",System.currentTimeMillis()).apply();
              relayState="Relay accepted "+a+" events, rejected "+b;
            }else relayState="Relay authenticated; queue empty";
          }
        }
      }catch(Exception e){
        String message=e.getMessage()==null?e.getClass().getSimpleName():e.getMessage();
        // Never log URLs or credentials from exceptions.
        if(message.length()>110)message=message.substring(0,110);
        relayState="Relay/connection issue: "+message;
        lastAuth=0;
      }
      persist();
      sleep(20000);
    }
  }
  private static void sleep(long duration){
    try{Thread.sleep(duration);}catch(InterruptedException e){Thread.currentThread().interrupt();}
  }
  private void persist(){
    try{
      getSharedPreferences("tgc_mobile_node_status",0).edit()
        .putBoolean("running",running)
        .putString("watcher",watcherState)
        .putString("relay",relayState)
        .putInt("queue",outbox==null?0:outbox.count())
        .putLong("service_heartbeat_at",System.currentTimeMillis())
        .apply();
    }catch(RuntimeException ignored){}
  }
  private void stopCollection(String reason){
    running=false;
    watcherState=reason;
    relayState="Stopped";
    IPrivilegedProbe active=watcher;
    if(active!=null){try{active.stopWatching();}catch(Exception ignored){}}
    persist();
    stopForeground(STOP_FOREGROUND_REMOVE);
    stopSelf();
  }
  @Override public void onDestroy(){
    shuttingDown=true;running=false;
    collection.shutdownNow();upload.shutdownNow();
    Shizuku.removeBinderReceivedListener(onBinderReceived);
    Shizuku.removeBinderDeadListener(onBinderDead);
    if(bound){
      try{Shizuku.unbindUserService(serviceArgs(),connection,false);}catch(Exception ignored){}
    }
    watcherState="Background Node service stopped";
    persist();
    if(outbox!=null)outbox.close();
    super.onDestroy();
  }
  @Override public IBinder onBind(Intent intent){return null;}
}
