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
    action(root,"Refresh Node status",this::refresh);
    action(root,"Stop automatic Node",this::stopNode);
    action(root,"Unlink this device",this::unlinkConfirm);
    status=title("Checking deployment...",14);root.addView(status);
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
        String current=state.getString("message","Node not started");
        int pending=new MobileOutbox(this).count();
        String report=node+"\nNode running: "+state.getBoolean("running",false)+
          "\n"+current+"\nOffline queued sightings: "+pending;
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
  @Override protected void onDestroy(){worker.shutdown();super.onDestroy();}
}
