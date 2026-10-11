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
import android.os.IBinder;
import android.os.RemoteException;

import org.json.JSONArray;
import org.json.JSONObject;
import rikka.shizuku.Shizuku;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * User-started visible foreground collector.
 * Shizuku reads VRChat data. This app securely stores/retries HTTPS relay submissions.
 * Actual invites and eligibility filters remain exclusively in Main Launcher (GM4/GM1).
 */
public class MobileNodeService extends Service {
  public static final String START="club.thegamerclub.nodelab.START_NODE";
  public static final String STOP="club.thegamerclub.nodelab.STOP_NODE";
  private static final String CHANNEL="tgc_mobile_node";
  private final ExecutorService jobs=Executors.newSingleThreadExecutor();
  private volatile boolean running=false,bound=false,shutdown=false;
  private volatile IPrivilegedProbe watcher;
  private MobileOutbox outbox;
  private long lastAuth=0;
  private String lastMessage="Starting...";
  private final ServiceConnection connection=new ServiceConnection(){
    @Override public void onServiceConnected(ComponentName name,IBinder service){
      watcher=IPrivilegedProbe.Stub.asInterface(service);
      try{watcher.startWatching();update("Watcher connected; collecting new joins");}
      catch(Exception e){update("Cannot start watcher: "+e.getClass().getSimpleName());}
    }
    @Override public void onServiceDisconnected(ComponentName name){
      watcher=null;bound=false;update("Shizuku watcher disconnected");
    }
  };
  @Override public void onCreate(){
    super.onCreate();
    outbox=new MobileOutbox(this);
    NotificationManager manager=getSystemService(NotificationManager.class);
    manager.createNotificationChannel(new NotificationChannel(CHANNEL,"TGC Mobile Invite Node",
      NotificationManager.IMPORTANCE_LOW));
  }
  private Notification notification(){
    PendingIntent open=PendingIntent.getActivity(this,1,
      new Intent(this,CompanionActivity.class),
      PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    PendingIntent stop=PendingIntent.getService(this,2,
      new Intent(this,MobileNodeService.class).setAction(STOP),
      PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    return new Notification.Builder(this,CHANNEL)
      .setSmallIcon(android.R.drawable.stat_notify_sync)
      .setContentTitle("TGC Mobile Invite Node")
      .setContentText("Tracking VRChat player joins • staff-only relay")
      .setContentIntent(open).setOngoing(true)
      .addAction(new Notification.Action.Builder(null,"Stop",stop).build()).build();
  }
  @Override public int onStartCommand(Intent intent,int flags,int startId){
    if(intent!=null && STOP.equals(intent.getAction())){stopCollection();return START_NOT_STICKY;}
    try{
      if(Build.VERSION.SDK_INT>=29){
        startForeground(221,notification(),
          android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
      }else{startForeground(221,notification());}
    }catch(RuntimeException e){
      update("Foreground service denied: "+e.getClass().getSimpleName());
      stopSelf();return START_NOT_STICKY;
    }
    if(!running){
      running=true;
      jobs.execute(()->runLoop());
    }
    return START_STICKY;
  }
  private void connectWatcher(){
    if(bound||watcher!=null)return;
    try{
      if(!Shizuku.pingBinder() || Shizuku.checkSelfPermission()!=PackageManager.PERMISSION_GRANTED){
        update("Shizuku needs to be running and authorized; use Node Lab");
        return;
      }
      Shizuku.UserServiceArgs args=new Shizuku.UserServiceArgs(
        new ComponentName(this,PrivilegedProbe.class))
        .daemon(true).processNameSuffix("tgc_mobile_watch").version(3).debuggable(false);
      Shizuku.bindUserService(args,connection);
      bound=true;
    }catch(Exception e){bound=false;update("Shizuku connect: "+e.getClass().getSimpleName());}
  }
  private void runLoop(){
    while(running&&!shutdown){
      try{
        if(watcher==null)connectWatcher();
        IPrivilegedProbe active=watcher;
        if(active!=null)saveCapturedEvents(active);
        MobileCredentials.Record creds=MobileCredentials.load(this);
        if(creds==null){update("Node not deployed. Paste your staff link first.");}
        else{
          if(System.currentTimeMillis()-lastAuth>90000){
            JSONObject check=MobileRelay.post(creds.url,MobileRelay.authorized(creds,"node_status"));
            if(!check.optBoolean("verified",false) ||
                !"mobile_companion".equals(check.optString("client_type"))){
              throw new SecurityException("Mobile Node not verified");
            }
            if(!creds.discordId.equals(check.optString("discord_id"))){
              throw new SecurityException("Deployment owner mismatch");
            }
            lastAuth=System.currentTimeMillis();
          }
          if(System.currentTimeMillis()-lastAuth<=240000)sendQueued(creds);
        }
      }catch(Exception e){
        String message=e.getMessage()!=null?e.getMessage():e.getClass().getSimpleName();
        if(message.length()>130)message=message.substring(0,130);
        update("Relay: "+message);
        if(message.matches("(?i).*(revoked|staff ID not|authorization expired|Unknown node|invalid node key|not verified|owner mismatch).*")){
          update("ACCESS REVOKED / EXPIRED: "+message);
          running=false;
          break;
        }
      }
      try{Thread.sleep(10000);}catch(InterruptedException e){Thread.currentThread().interrupt();break;}
    }
    if(!shutdown)stopSelf();
  }
  private void saveCapturedEvents(IPrivilegedProbe active) throws Exception{
    String text=active.pendingSightings(150);
    JSONArray pending=new JSONArray(text);
    if(pending.length()==0)return;
    outbox.insert(pending);
    JSONArray ids=new JSONArray();
    for(int i=0;i<pending.length();i++){
      JSONObject e=pending.optJSONObject(i);
      if(e!=null)ids.put(e.optString("event_id"));
    }
    active.ackSightings(ids.toString()); // ACK only after SQLite transaction succeeded.
    update("Captured "+pending.length()+" new events; queued "+outbox.count());
  }
  private void sendQueued(MobileCredentials.Record record) throws Exception{
    JSONArray batch=outbox.batch(100);
    if(batch.length()==0){
      update("Connected • queue empty • monitoring");
      return;
    }
    JSONObject payload=MobileRelay.authorized(record,"submit_sightings");
    payload.put("sightings",batch);
    JSONObject received=MobileRelay.post(record.url,payload);
    JSONArray ack=received.optJSONArray("accepted_event_ids");
    JSONArray rejected=received.optJSONArray("rejected_event_ids");
    JSONArray removal=new JSONArray();
    if(ack!=null)for(int i=0;i<ack.length();i++)removal.put(ack.optString(i));
    if(rejected!=null)for(int i=0;i<rejected.length();i++)removal.put(rejected.optString(i));
    outbox.remove(removal);
    update("Relay accepted "+(ack==null?0:ack.length())+" • queued "+outbox.count());
  }
  private void update(String value){
    lastMessage=value;
    getSharedPreferences("tgc_mobile_node_status",0).edit()
      .putString("message",value).putLong("at",System.currentTimeMillis())
      .putBoolean("running",running).apply();
  }
  private void stopCollection(){
    running=false;
    try{if(watcher!=null)watcher.stopWatching();}catch(Exception ignored){}
    update("Stopped by user • queued "+(outbox==null?0:outbox.count()));
    stopForeground(STOP_FOREGROUND_REMOVE);
    stopSelf();
  }
  @Override public void onDestroy(){
    shutdown=true;running=false;
    jobs.shutdownNow();
    if(bound){
      try{
        Shizuku.UserServiceArgs args=new Shizuku.UserServiceArgs(
          new ComponentName(this,PrivilegedProbe.class))
          .daemon(true).processNameSuffix("tgc_mobile_watch").version(3).debuggable(false);
        Shizuku.unbindUserService(args,connection,false);
      }catch(Exception ignored){}
    }
    if(outbox!=null)outbox.close();
    super.onDestroy();
  }
  @Override public IBinder onBind(Intent intent){return null;}
}
