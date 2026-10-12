package club.thegamerclub.nodelab;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
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
import android.view.View;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONObject;

/** Staff-facing setup and tracking. Diagnostic details are hidden behind Advanced. */
public class MobileDeployActivity extends Activity {
  private EditText setup;
  private TextView status,technicalStatus,feedback;
  private Button startControl,stopControl;
  private LinearLayout enrollment,advancedPane;
  private boolean advanced=false;
  private boolean helpExpanded=false;
  private final Handler handler=new Handler(Looper.getMainLooper());
  private final Runnable autoRefresh=new Runnable(){
    @Override public void run(){refresh();handler.postDelayed(this,5000);}
  };
  private static final int SHIZUKU_PERMISSION=7005;
  private final Shizuku.OnRequestPermissionResultListener shizukuResult=(request,grant)->{
    if(request!=SHIZUKU_PERMISSION)return;
    runOnUiThread(()->{
      if(grant==PackageManager.PERMISSION_GRANTED)startNode();
      else showStatus("Permission not granted. Open Advanced tools if you need help.");
    });
  };
  private final ExecutorService worker=Executors.newSingleThreadExecutor();
  private int dp(int n){return CompanionStyle.dp(this,n);}
  private TextView title(String value,int size){
    return CompanionStyle.text(this,value,size,CompanionStyle.TEXT,size>=19);
  }
  private void spacer(LinearLayout l,int n){
    l.addView(new View(this),new LinearLayout.LayoutParams(1,dp(n)));
  }
  private Button action(LinearLayout root,String name,Runnable callback,boolean primary){
    Button b=CompanionStyle.button(this,name,primary);
    root.addView(b,CompanionStyle.buttonParams(this));
    b.setOnClickListener(v->callback.run());
    return b;
  }
  private void openShizukuLink(String url){
    try{
      startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url))
          .addCategory(Intent.CATEGORY_BROWSABLE));
    }catch(Exception e){
      new AlertDialog.Builder(this)
          .setTitle("Open Shizuku website")
          .setMessage("Open this address in a browser: "+url)
          .setPositiveButton("OK",null).show();
    }
  }
  @Override public void onCreate(Bundle state){
    super.onCreate(state);
    getWindow().setStatusBarColor(CompanionStyle.BG);
    getWindow().setNavigationBarColor(CompanionStyle.BG);
    Shizuku.addRequestPermissionResultListener(shizukuResult);
    advanced=getIntent().getBooleanExtra("advanced",false);
    helpExpanded=state!=null && state.getBoolean("shizuku_help_expanded",false);
    ScrollView scroll=new ScrollView(this);
    scroll.setBackgroundColor(CompanionStyle.BG);
    LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
    root.setPadding(dp(18),dp(20),dp(18),dp(27));
    scroll.addView(root);setContentView(scroll);
    CompanionStyle.safeArea(this,scroll);
    action(root,"← Back",this::finish,false);
    spacer(root,12);
    root.addView(CompanionStyle.section(this,"DEVICE"));
    root.addView(title("Player tracking",28));
    spacer(root,7);
    TextView intro=title("Track who joins and leaves your VRChat Mobile instances and keep useful player history.",14);
    intro.setTextColor(CompanionStyle.MUTED);
    root.addView(intro);spacer(root,14);
    enrollment=CompanionStyle.card(this);
    root.addView(enrollment,CompanionStyle.cardParams(this));
    enrollment.addView(title("Link your device",20));
    spacer(enrollment,8);
    TextView guide=title("Paste your staff setup link to connect this phone.",13);
    guide.setTextColor(CompanionStyle.MUTED);enrollment.addView(guide);
    spacer(enrollment,12);
    setup=CompanionStyle.input(this,"Paste your setup link");
    setup.setSingleLine(false);setup.setMaxLines(4);setup.setMinLines(2);
    enrollment.addView(setup);
    spacer(enrollment,13);
    action(enrollment,"Paste from clipboard",()->{
      ClipboardManager clip=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
      ClipData data=clip.getPrimaryClip();
      if(data!=null && data.getItemCount()>0)
        setup.setText(data.getItemAt(0).coerceToText(this));
    },false);
    action(enrollment,"Link this device",this::redeem,true);

    LinearLayout dashboard=CompanionStyle.card(this);
    root.addView(dashboard,CompanionStyle.cardParams(this));
    status=title("Checking tracking status...",14);
    status.setTextColor(CompanionStyle.TEXT);
    dashboard.addView(status);
    spacer(dashboard,9);
    feedback=title("",13);
    feedback.setTextColor(CompanionStyle.ACCENT);
    dashboard.addView(feedback);
    spacer(dashboard,14);
    startControl=action(dashboard,"Start tracking",this::startNode,true);
    stopControl=action(dashboard,"Stop tracking",this::stopNode,false);
    action(dashboard,"Refresh status",this::refresh,false);

    action(dashboard,"View player list and log",()->startActivity(
        new Intent(this,PlayerActivityLogActivity.class)),false);

    // Keep setup and tracking controls accessible without scrolling through help text.
    // Expand this explanation only when the user asks for it.
    LinearLayout info=CompanionStyle.card(this);
    root.addView(info,CompanionStyle.cardParams(this));
    Button infoToggle=CompanionStyle.button(this,
        helpExpanded?"▴ Hide Shizuku explanation":"ⓘ What is Shizuku? (optional)  ▾",false);
    info.addView(infoToggle,CompanionStyle.buttonParams(this));
    action(info,"Download Shizuku • official website",()->openShizukuLink(
        "https://shizuku.rikka.app/download/"),false);

    LinearLayout infoDetails=new LinearLayout(this);
    infoDetails.setOrientation(LinearLayout.VERTICAL);
    info.addView(infoDetails);
    spacer(infoDetails,7);
    infoDetails.addView(title("Do I need Shizuku?",18));
    spacer(infoDetails,7);
    TextView notNeeded=title(
        "No. You can use your friends list, profiles, notifications, worlds, avatars and other Companion features without Shizuku or playing VRChat on this phone.",13);
    notNeeded.setTextColor(CompanionStyle.MUTED);
    infoDetails.addView(notNeeded);
    spacer(infoDetails,10);
    infoDetails.addView(title("What does Shizuku do?",16));
    spacer(infoDetails,6);
    TextView purpose=title(
        "Shizuku is an Android utility that, with your permission, lets apps use certain restricted system features. Here it is only used to let the Mobile Node read VRChat Mobile's log files. The same logs power your player list, join/leave history and instance changes, like desktop VRCX.",13);
    purpose.setTextColor(CompanionStyle.MUTED);
    infoDetails.addView(purpose);
    spacer(infoDetails,9);
    TextView sharing=title(
        "Player join sightings may also be sent to the TGC network if tracking is enabled. Your complete raw log files are not uploaded. Quest support is planned for investigation, not available yet.",12);
    sharing.setTextColor(CompanionStyle.MUTED);
    infoDetails.addView(sharing);
    action(infoDetails,"How to set up Shizuku",()->openShizukuLink(
        "https://shizuku.rikka.app/guide/setup/"),false);
    action(infoDetails,"Hide explanation",()->{
      helpExpanded=false;
      infoDetails.setVisibility(View.GONE);
      infoToggle.setText("ⓘ What is Shizuku? (optional)  ▾");
    },false);
    infoDetails.setVisibility(helpExpanded?View.VISIBLE:View.GONE);
    infoToggle.setOnClickListener(v->{
      helpExpanded=!helpExpanded;
      infoDetails.setVisibility(helpExpanded?View.VISIBLE:View.GONE);
      infoToggle.setText(helpExpanded?
          "▴ Hide Shizuku explanation":"ⓘ What is Shizuku? (optional)  ▾");
    });

    advancedPane=CompanionStyle.card(this);
    root.addView(advancedPane,CompanionStyle.cardParams(this));
    action(advancedPane,advanced?"Hide advanced diagnostics":"Advanced tools",
       ()->{advanced=!advanced;setAdvanced();},false);
    technicalStatus=title("",12);
    technicalStatus.setTextColor(CompanionStyle.MUTED);
    advancedPane.addView(technicalStatus);
    action(advancedPane,"Unlink this device",this::unlinkConfirm,false);
    setAdvanced();
    refresh();
  }
  private void setAdvanced(){
    if(technicalStatus==null || advancedPane==null)return;
    technicalStatus.setVisibility(advanced?View.VISIBLE:View.GONE);
    // The last child is the dangerous Unlink action, only shown in Advanced.
    advancedPane.getChildAt(advancedPane.getChildCount()-1)
      .setVisibility(advanced?View.VISIBLE:View.GONE);
    Button toggle=(Button)advancedPane.getChildAt(0);
    toggle.setText(advanced?"Hide advanced diagnostics":"Advanced tools");
    if(advanced)refresh();
  }
  private void showStatus(String message){
    runOnUiThread(()->{if(feedback!=null)feedback.setText(message);});
  }
  private void redeem(){
    final String raw=setup.getText().toString().trim();
    if(raw.isEmpty()){showStatus("Paste your setup link first.");return;}
    showStatus("Connecting your device...");
    worker.execute(()->{
      try{
        Uri uri=Uri.parse(raw);
        if(!"https".equals(uri.getScheme()) ||
          !"script.google.com".equals(uri.getHost()) ||
          !uri.getPath().matches("/macros/s/[^/]+/exec"))
          throw new SecurityException("This setup link is not valid");
        String code=uri.getQueryParameter("deploy");
        if(code==null || !code.matches("(?i)TGC-DEPLOY(-[A-F0-9]{4}){5}"))
          throw new IllegalArgumentException("This setup link is not valid");
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
          throw new SecurityException("Your staff access couldn't be verified");
        MobileCredentials.save(this,relay,response);
        showStatus("Connected! You're ready to start player tracking.");
        runOnUiThread(()->{setup.setText("");enrollment.setVisibility(View.GONE);});
      }catch(Exception e){
        showStatus("Couldn't link your device: "+
          (e.getMessage()==null?"Please try again":e.getMessage()));
      }
    });
  }
  private void startNode(){
    try{
      if(MobileCredentials.load(this)==null){
        showStatus("Link this device before starting tracking.");return;
      }
      if(!Shizuku.pingBinder()){
        showStatus("The tracking service isn't available. Open Advanced tools for help.");return;
      }
      if(Shizuku.checkSelfPermission()!=PackageManager.PERMISSION_GRANTED){
        Shizuku.requestPermission(SHIZUKU_PERMISSION);
        showStatus("Allow the tracking permission when prompted.");return;
      }
      if(Build.VERSION.SDK_INT>=33 &&
         checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
        requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},451);
      Intent intent=new Intent(this,MobileNodeService.class).setAction(MobileNodeService.START);
      if(Build.VERSION.SDK_INT>=26)startForegroundService(intent);
      else startService(intent);
      showStatus("Starting tracking. Your status will update shortly.");
    }catch(Exception e){showStatus("Couldn't start tracking: "+e.getMessage());}
  }
  private void stopNode(){
    startService(new Intent(this,MobileNodeService.class).setAction(MobileNodeService.STOP));
    showStatus("Stopping tracking...");
  }
  private void refresh(){
    worker.execute(()->{
      try{
        MobileCredentials.Record r=MobileCredentials.load(this);
        android.content.SharedPreferences state=getSharedPreferences("tgc_mobile_node_status",0);
        int pending;
        try(MobileOutbox db=new MobileOutbox(this)){pending=db.count();}
        long now=System.currentTimeMillis(),heartbeat=state.getLong("service_heartbeat_at",0);
        boolean online=state.getBoolean("running",false)&&heartbeat>0&&
            now-heartbeat<90_000;
        String general=r==null?"This device isn't linked yet.":
          "Device linked\n\n"+
          (online?"● Tracking is active":"○ Tracking is paused or reconnecting")+
          "\n\nPlayers recorded: "+state.getInt("captured_total",0)+
          "\nSent: "+state.getInt("uploaded_total",0)+
          "\nNot sent yet: "+pending;
        String technical="Device: "+(r==null?"Not linked":r.nodeName)+
          "\nService heartbeat: "+(heartbeat==0?"Never":(now-heartbeat)/1000+" seconds ago")+
          "\nShizuku: "+(Shizuku.pingBinder()?"Connected":"Disconnected")+
          "\nWatcher: "+state.getString("watcher","Not started")+
          "\nSource found: "+state.getBoolean("watch_source",false)+
          "\nWatcher polls: "+state.getInt("watch_polls",0)+
          "\nNew joins: "+state.getInt("watch_joins",0)+
          "\nRead errors: "+state.getInt("watch_errors",0)+
          "\nBytes processed: "+state.getLong("watch_bytes",0)+
          "\nRelay status: "+state.getString("relay","Not checked")+
          "\nRejected records: "+state.getInt("rejected_total",0);
        runOnUiThread(()->{
          if(isFinishing()||isDestroyed())return;
          enrollment.setVisibility(r==null?View.VISIBLE:View.GONE);
          status.setText(general);
          if(startControl!=null){
            startControl.setEnabled(r!=null&&!online);
            startControl.setText(online?"Tracking is running":"Start tracking");
          }
          if(stopControl!=null)stopControl.setEnabled(r!=null&&online);
          if(advanced)technicalStatus.setText(technical);
        });
      }catch(Exception e){showStatus("Status temporarily unavailable. Try refreshing.");}
    });
  }
  private void unlinkConfirm(){
    new AlertDialog.Builder(this).setTitle("Unlink this phone?")
      .setMessage("This stops tracking and clears this device's Node setup, unsent encounters and saved local player/log history. Your personal VRChat sign-in is separate.")
      .setNegativeButton("Cancel",null)
      .setPositiveButton("Unlink",(d,w)->{
        stopNode();
        MobileCredentials.clear(this);
        try(MobileOutbox outbox=new MobileOutbox(this)){outbox.wipe();}
        StaffGate.lock();
        showStatus("This phone has been unlinked. Contact a group owner if you need a new link.");
        enrollment.setVisibility(View.VISIBLE);
      }).show();
  }
  @Override protected void onResume(){
    super.onResume();
    handler.removeCallbacks(autoRefresh);
    handler.postDelayed(autoRefresh,2000);
  }
  @Override protected void onSaveInstanceState(Bundle state){
    state.putBoolean("shizuku_help_expanded",helpExpanded);
    super.onSaveInstanceState(state);
  }
  @Override protected void onPause(){handler.removeCallbacks(autoRefresh);super.onPause();}
  @Override protected void onDestroy(){
    handler.removeCallbacks(autoRefresh);
    Shizuku.removeRequestPermissionResultListener(shizukuResult);
    worker.shutdown();super.onDestroy();
  }
}
