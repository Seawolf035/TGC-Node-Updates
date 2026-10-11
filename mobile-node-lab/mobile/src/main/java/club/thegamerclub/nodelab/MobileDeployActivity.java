package club.thegamerclub.nodelab;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Build;
import android.net.Uri;
import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import rikka.shizuku.Shizuku;
import android.content.ClipboardManager;
import android.content.ClipData;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Button;
import android.graphics.Color;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONObject;

public class MobileDeployActivity extends Activity {
  private static final int BG=0xFF0D1829;
  private EditText setup;
  private TextView status;
  private final Handler handler=new Handler(Looper.getMainLooper());
  private final Runnable autoRefresh=new Runnable(){
    @Override public void run(){refresh();handler.postDelayed(this,5000);}
  };
  private static final int SHIZUKU_PERMISSION=7005;
  private final Shizuku.OnRequestPermissionResultListener shizukuResult=(request,grant)->{
    if(request!=SHIZUKU_PERMISSION)return;
    runOnUiThread(()->{
      if(grant==PackageManager.PERMISSION_GRANTED)startNode();
      else showStatus("Shizuku permission denied. Open Shizuku and authorize TGC Companion.");
    });
  };
  private final ExecutorService worker=Executors.newSingleThreadExecutor();

  private int dp(int n){return (int)(getResources().getDisplayMetrics().density*n+.5f);}
  private TextView title(String value,int size){
    TextView tv=new TextView(this);tv.setText(value);tv.setTextColor(Color.WHITE);
    tv.setTextSize(size);tv.setPadding(0,dp(8),0,dp(7));
    if(size>=19)tv.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
    return tv;
  }
  private void action(LinearLayout root,String name,Runnable callback){
    Button b=new Button(this);b.setText(name);b.setAllCaps(false);
    LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(52));
    lp.bottomMargin=dp(9);root.addView(b,lp);b.setOnClickListener(v->callback.run());
  }
  @Override public void onCreate(Bundle state){
    super.onCreate(state);getWindow().setStatusBarColor(BG);
    Shizuku.addRequestPermissionResultListener(shizukuResult);
    ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(BG);
    LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
    root.setPadding(dp(17),dp(22),dp(17),dp(18));
    scroll.addView(root);setContentView(scroll);
    root.addView(title("STAFF NODE DEPLOYMENT",24));
    root.addView(title("Paste your owner-issued, single-use TGC Quick Deployment link. Your staff identity is assigned by the Main Launcher, not by this app.",13));
    setup=new EditText(this);
    setup.setHint("Full setup link (https://script.google.com/.../exec?deploy=...)");
    setup.setTextColor(Color.WHITE);setup.setHintTextColor(0xFF98ADC5);
    setup.setTextSize(13);setup.setMinLines(2);setup.setMaxLines(5);
    root.addView(setup);
    action(root,"Paste setup link",()->{
      ClipboardManager clip=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
      ClipData data=clip.getPrimaryClip();
      if(data!=null&&data.getItemCount()>0)
        setup.setText(data.getItemAt(0).coerceToText(this));
    });
    action(root,"Redeem one-time staff deployment",this::redeem);
    root.addView(title("NODE CONTROLS",20));
    action(root,"Start automatic Node + relay uploads",this::startNode);
    action(root,"Refresh live tracking / relay status",this::refresh);
    action(root,"Stop automatic Node",this::stopNode);
    action(root,"Unlink this device",this::unlinkConfirm);
    status=title("Checking deployment...",14);status.setTextIsSelectable(true);root.addView(status);
    root.addView(title("Node credentials are Android Keystore-encrypted. Offline joins are stored in the app's private SQLite database and uploaded through the relay. The phone never sends group invites: GM4 checks eligibility and GM1 invites.",12));
    refresh();
  }
  private void showStatus(String message){runOnUiThread(()->status.setText(message));}
  private void redeem(){
    final String raw=setup.getText().toString().trim();
    if(raw.isEmpty()){showStatus("Paste the complete one-use setup link first.");return;}
    showStatus("Redeeming with relay...");
    worker.execute(()->{
      try{
        Uri uri=Uri.parse(raw);
        if(!"https".equals(uri.getScheme()) ||
          !"script.google.com".equals(uri.getHost()) ||
          !uri.getPath().matches("/macros/s/[^/]+/exec"))
          throw new SecurityException("Invalid Apps Script setup URL");
        String code=uri.getQueryParameter("deploy");
        if(code==null || !code.matches("(?i)TGC-DEPLOY(-[A-F0-9]{4}){5}"))
          throw new IllegalArgumentException("Invalid deployment code in URL");
        String relay=uri.buildUpon().clearQuery().fragment(null).build().toString();
        JSONObject request=new JSONObject();
        request.put("action","redeem_deployment");
        request.put("code",code.toUpperCase(java.util.Locale.US));
        request.put("client_type","mobile_companion");
        request.put("install_id",MobileCredentials.installId(this));
        request.put("version","0.5.0-mobile");
        JSONObject response=MobileRelay.post(relay,request);
        if(!response.optBoolean("verified",false) ||
          !"mobile_companion".equals(response.optString("client_type")))
          throw new SecurityException("Relay did not verify this Mobile Companion deployment");
        MobileCredentials.save(this,relay,response);
        showStatus("DEPLOYED • "+response.optString("node_name")+
          "\nStaff Discord ID: "+response.optString("discord_id")+
          "\nReady. Press Start to enable the automatic Node.");
        runOnUiThread(()->setup.setText(""));
      }catch(Exception e){
        showStatus("Deployment failed: "+(e.getMessage()==null?e.getClass().getSimpleName():e.getMessage()));
      }
    });
  }
  private void startNode(){
    try{
      if(MobileCredentials.load(this)==null){
        showStatus("Not deployed yet. Redeem the staff link first.");return;
      }
      if(!Shizuku.pingBinder()){
        showStatus("Shizuku is not connected. Open Shizuku, start it, then return here. The relay Node cannot track without Shizuku.");
        return;
      }
      if(Shizuku.checkSelfPermission()!=PackageManager.PERMISSION_GRANTED){
        Shizuku.requestPermission(SHIZUKU_PERMISSION);
        showStatus("Authorize TGC Companion in the Shizuku prompt, then background monitoring will start.");
        return;
      }
      if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
        requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},451);
      Intent intent=new Intent(this,MobileNodeService.class).setAction(MobileNodeService.START);
      if(Build.VERSION.SDK_INT>=26)startForegroundService(intent);
      else startService(intent);
      showStatus("Node service started; verify Shizuku is authorized. Check status after a few seconds.");
    }catch(Exception e){showStatus("Cannot start Node: "+e.getMessage());}
  }
  private void stopNode(){
    startService(new Intent(this,MobileNodeService.class).setAction(MobileNodeService.STOP));
    showStatus("Stopping the Node...");
  }
  private void refresh(){
    worker.execute(()->{
      try{
        MobileCredentials.Record r=MobileCredentials.load(this);
        String node=r==null?"Not linked":"Provisioned: "+r.nodeName+"\nStaff owner: "+r.discordId;
        android.content.SharedPreferences state=getSharedPreferences("tgc_mobile_node_status",0);
        int pending;
        try(MobileOutbox db=new MobileOutbox(this)){pending=db.count();}
        boolean active=state.getBoolean("running",false);
        long now=System.currentTimeMillis();
        long heartbeat=state.getLong("service_heartbeat_at",0);
        long poll=state.getLong("last_poll_at",0);
        long capture=state.getLong("last_capture_at",0);
        long upload=state.getLong("last_upload_at",0);
        String elapsed=heartbeat==0?"never":(Math.max(0,now-heartbeat)/1000)+"s ago";
        String pollAge=poll==0?"never":(Math.max(0,now-poll)/1000)+"s ago";
        String capturedAge=capture==0?"never":(Math.max(0,now-capture)/1000)+"s ago";
        String uploadAge=upload==0?"never":(Math.max(0,now-upload)/1000)+"s ago";
        String report=node+
          "\n\nBACKGROUND SERVICE: "+(active?"RUNNING":"STOPPED")+
          "\nService heartbeat: "+elapsed+
          "\nShizuku: "+(Shizuku.pingBinder()?"connected":"NOT connected")+
          "\nWatcher: "+state.getString("watcher","Not started")+
          "\nLog source found: "+state.getBoolean("watch_source",false)+
          "\nWatcher polls: "+state.getInt("watch_polls",0)+
          "\nLast log poll: "+pollAge+
          "\nNew player joins: "+state.getInt("watch_joins",0)+
          "\nPlayer leaves: "+state.getInt("watch_leaves",0)+
          "\nUnrecognized joins: "+state.getInt("watch_unrecognized",0)+
          "\nRead errors: "+state.getInt("watch_errors",0)+
          "\nBytes processed: "+state.getLong("watch_bytes",0)+
          "\n\nOFFLINE & RELAY"+
          "\nSaved new sightings: "+state.getInt("captured_total",0)+
          "\nLast saved sighting: "+capturedAge+
          "\nQueued, awaiting upload: "+pending+
          "\nRelay: "+state.getString("relay","Not checked")+
          "\nEvents accepted by relay: "+state.getInt("uploaded_total",0)+
          "\nEvents rejected by relay: "+state.getInt("rejected_total",0)+
          "\nLast successful upload: "+uploadAge+
          "\n\nKeep the TGC foreground notification visible while using VRChat. Start here (not Node Lab) for relay uploads.";
        showStatus(report);
      }catch(Exception e){showStatus("Status unavailable: "+e.getClass().getSimpleName());}
    });
  }
  private void unlinkConfirm(){
    new AlertDialog.Builder(this).setTitle("Unlink this staff device?")
      .setMessage("Stop the Node and clear its local key and sighting queue? Use Main Launcher to revoke the relay Node separately.")
      .setNegativeButton("Cancel",null)
      .setPositiveButton("Unlink",(d,w)->{
        stopNode();
        MobileCredentials.clear(this);
        new MobileOutbox(this).wipe();
        showStatus("Local deployment cleared. Also remove the Node in Main Launcher.");
      }).show();
  }
  @Override protected void onResume(){
    super.onResume();
    handler.removeCallbacks(autoRefresh);
    handler.postDelayed(autoRefresh,2000);
  }
  @Override protected void onPause(){handler.removeCallbacks(autoRefresh);super.onPause();}
  @Override protected void onDestroy(){
    handler.removeCallbacks(autoRefresh);
    Shizuku.removeRequestPermissionResultListener(shizukuResult);
    worker.shutdown();super.onDestroy();
  }
}
