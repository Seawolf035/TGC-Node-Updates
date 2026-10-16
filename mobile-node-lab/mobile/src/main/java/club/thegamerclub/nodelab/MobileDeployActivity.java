package club.thegamerclub.nodelab;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.Build;
import android.net.Uri;
import android.provider.DocumentsContract;
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

/** One-time enrollment only. Main Companion Tracking tab owns capture and permissions. */
public class MobileDeployActivity extends Activity {
  private EditText setup;
  private TextView status,technicalStatus,feedback,questFolderStatus;
  private Button startControl,stopControl,questFolderControl,questFolderModeControl;
  private LinearLayout enrollment,advancedPane,questFallbackContainer,questFallbackDetails;
  private View unlinkControl;
  private boolean advanced=false;
  private boolean helpExpanded=false,questFallbackExpanded=false;
  private final Handler handler=new Handler(Looper.getMainLooper());
  private final Runnable autoRefresh=new Runnable(){
    @Override public void run(){refresh();handler.postDelayed(this,5000);}
  };
  private static final int SHIZUKU_PERMISSION=7005, QUEST_FOLDER_REQUEST=7152;
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
    questFallbackExpanded=state!=null && state.getBoolean("quest_fallback_expanded",false);
    TrackingSetupGuide.initialize(this);
    // This Activity is now enrollment-only. Existing linked, authenticated
    // devices always use the one Tracking tab for capture and permissions.
    boolean linked=false;
    try{linked=MobileCredentials.load(this)!=null;}catch(Exception ignored){}
    if(linked && StaffGate.unlocked(this)){
      Intent tracking=new Intent(this,CompanionActivity.class)
          .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP)
          .putExtra("open_tracking_tab",true);
      startActivity(tracking);finish();return;
    }
    ScrollView scroll=new ScrollView(this);
    scroll.setBackgroundColor(CompanionStyle.BG);
    LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
    root.setPadding(dp(18),dp(20),dp(18),dp(27));
    scroll.addView(root);setContentView(scroll);
    CompanionStyle.safeArea(this,scroll);
    action(root,"← Back",this::finish,false);
    spacer(root,12);
    root.addView(CompanionStyle.section(this,"DEVICE ENROLLMENT"));
    root.addView(title("Link this device",28));
    spacer(root,7);
    TextView intro=title(QuestDevice.isQuestDevice()?
        "Enter a one-time Companion link for this headset. Tracking controls and Quest log permissions are on the Tracking tab.":
        "Enter a one-time Companion link for this phone. Tracking controls and Shizuku access are on the Tracking tab.",14);
    intro.setTextColor(CompanionStyle.MUTED);
    root.addView(intro);spacer(root,14);
    enrollment=CompanionStyle.card(this);
    root.addView(enrollment,CompanionStyle.cardParams(this));
    enrollment.addView(title("Link your device",20));
    spacer(enrollment,8);
    TextView guide=title(QuestDevice.isQuestDevice()?
        "Paste your staff setup link to connect this headset.":
        "Paste your staff setup link to connect this phone.",13);
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
    enrollment.addView(title(
        "After linking, all tracking controls, log permissions and diagnostics "+
        "are on the single Tracking tab. This screen is only for device enrollment.",
        13));
  }
  private void setAdvanced(){
    if(technicalStatus==null || advancedPane==null)return;
    technicalStatus.setVisibility(advanced?View.VISIBLE:View.GONE);
    if(unlinkControl!=null)unlinkControl.setVisibility(advanced?View.VISIBLE:View.GONE);
    if(questFallbackContainer!=null)
      questFallbackContainer.setVisibility(advanced?View.VISIBLE:View.GONE);
    if(questFallbackDetails!=null)
      questFallbackDetails.setVisibility(advanced&&questFallbackExpanded?View.VISIBLE:View.GONE);
    Button toggle=(Button)advancedPane.getChildAt(0);
    toggle.setText(advanced?"Hide advanced diagnostics":"Advanced tools");
    if(advanced)refresh();
  }
  private void showStatus(String message){
    runOnUiThread(()->{if(feedback!=null)feedback.setText(message);});
  }
  private void redeem(){
    if(!CompanionLicense.accepted(this)){
      showStatus("Accept the User Agreement before linking this device.");
      startActivity(new Intent(this,CompanionLicenseActivity.class));
      return;
    }
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
        // Enrollment is complete, but device capture setup is not. Return
        // through the main Companion gate so live staff validation, explicit
        // agreement acceptance, and the guided setup happen in that order.
        runOnUiThread(()->{
          if(isFinishing()||isDestroyed())return;
          setup.setText("");
          Intent home=new Intent(this,CompanionActivity.class)
              .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP)
              .putExtra("open_setup_after_link",true);
          startActivity(home);
          finish();
        });
      }catch(Exception e){
        showStatus("Couldn't link your device: "+
          (e.getMessage()==null?"Please try again":e.getMessage()));
      }
    });
  }
  private boolean trackingActive(){
    android.content.SharedPreferences s=getSharedPreferences("tgc_mobile_node_status",0);
    long last=s.getLong("service_heartbeat_at",0);
    return s.getBoolean("running",false)&&last>0&&
        System.currentTimeMillis()-last<90000;
  }
  private void pickQuestFolder(){
    if(!QuestDevice.isQuestDevice())return;
    if(trackingActive()){
      showStatus("Stop tracking before switching log sources.");return;
    }
    Intent picker=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
    picker.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION |
        Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
    try{
      startActivityForResult(picker,QUEST_FOLDER_REQUEST);
    }catch(Exception e){
      showStatus("Quest could not open the folder picker. Advanced tools has the Shizuku fallback.");
    }
  }
  @Override protected void onActivityResult(int request,int result,Intent data){
    super.onActivityResult(request,result,data);
    if(request!=QUEST_FOLDER_REQUEST)return;
    if(result!=RESULT_OK||data==null||data.getData()==null){
      showStatus("Folder selection canceled. Tracking method was not changed.");return;
    }
    try{
      QuestSafLogWatcher.selectFolder(this,data.getData());
      showStatus("Documents/Logs access granted. Folder tracking is selected. Start tracking before joining VRChat.");
      refresh();
    }catch(Exception e){
      showStatus("Folder access not granted: "+e.getMessage()+
          ". Select Documents/Logs exactly, or use Shizuku.");
    }
  }
  private void chooseFolderTracking(){
    if(trackingActive()){
      showStatus("Stop tracking before switching log sources.");return;
    }
    if(!QuestSafLogWatcher.hasGrant(this)){
      showStatus("Select Documents/Logs to enable recommended Quest tracking.");
      pickQuestFolder();return;
    }
    if(QuestSafLogWatcher.selectFolderMode(this))
      showStatus("Folder tracking selected. Shizuku is not needed.");
    refresh();
  }
  private void chooseShizukuTracking(){
    if(trackingActive()){
      showStatus("Stop tracking before switching log sources.");return;
    }
    QuestSafLogWatcher.selectShizuku(this);
    showStatus("Shizuku fallback selected for Quest. Start tracking after Shizuku is running.");
    refresh();
  }
  /** Quick-start for the main Tracking tab. Preserve the full permission flow
   * in this Activity whenever additional setup or authorization is required. */
  public static boolean startFromDashboard(Activity activity){
    boolean deviceLinked=false;
    try{deviceLinked=MobileCredentials.load(activity)!=null;}
    catch(Exception e){deviceLinked=false;}
    if(!deviceLinked ||
        !TrackingSetupGuide.acknowledged(activity) ||
        (QuestSafLogWatcher.isSelected(activity) &&
            !QuestSafLogWatcher.hasGrant(activity)) ||
        (!QuestSafLogWatcher.isSelected(activity) &&
            (!Shizuku.pingBinder() ||
             Shizuku.checkSelfPermission()!=PackageManager.PERMISSION_GRANTED))){
      activity.startActivity(new Intent(activity,MobileDeployActivity.class));
      return false;
    }
    try{
      if(Build.VERSION.SDK_INT>=33 &&
          activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=
              PackageManager.PERMISSION_GRANTED)
        activity.requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},451);
      Intent intent=new Intent(activity,MobileNodeService.class)
          .setAction(MobileNodeService.START);
      if(Build.VERSION.SDK_INT>=26)activity.startForegroundService(intent);
      else activity.startService(intent);
      android.widget.Toast.makeText(activity,"Starting tracking…",
          android.widget.Toast.LENGTH_SHORT).show();
      return true;
    }catch(Exception e){
      android.widget.Toast.makeText(activity,"Couldn't start tracking: "+e.getMessage(),
          android.widget.Toast.LENGTH_LONG).show();
      return false;
    }
  }
  private void startNode(){
    try{
      if(MobileCredentials.load(this)==null){
        showStatus("Link this device before starting tracking.");return;
      }
      if(!TrackingSetupGuide.acknowledged(this)){
        // First-run setup is part of the main Tracking screen, not a dialog
        // or an Advanced menu inside tracking controls.
        showStatus("Complete the setup guide on the Tracking tab first.");
        startActivity(new Intent(this,CompanionActivity.class)
            .putExtra("open_tracking_setup",true));
        finish();
        return;
      }
      if(QuestSafLogWatcher.isSelected(this)){
        if(!QuestSafLogWatcher.hasGrant(this)){
          showStatus("Grant Documents/Logs folder access to start Quest tracking.");
          pickQuestFolder();return;
        }
      }else{
        if(!Shizuku.pingBinder()){
          showStatus("The Shizuku tracking service is unavailable. Open Advanced tools for help.");return;
        }
        if(Shizuku.checkSelfPermission()!=PackageManager.PERMISSION_GRANTED){
          Shizuku.requestPermission(SHIZUKU_PERMISSION);
          showStatus("Allow the tracking permission when prompted.");return;
        }
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
        boolean quest=QuestDevice.isQuestDevice();
        boolean folderMode=QuestSafLogWatcher.isSelected(this);
        boolean folderGrant=quest && QuestSafLogWatcher.hasGrant(this);
        String selectedMethod=folderMode?"folder":"shizuku";
        String activeMethod=online?state.getString("tracking_mode",selectedMethod):selectedMethod;
        boolean activeFolder="folder".equals(activeMethod);
        String general=r==null?"This device isn't linked yet.":
          "Device linked\n\n"+
          (online?"● Tracking service is running":
            quest&&folderMode&&!folderGrant?"○ VRChat logs folder access required":
            "○ Tracking is paused or reconnecting")+
          "\n\nPlayers recorded: "+state.getInt("captured_total",0)+
          "\nSent: "+state.getInt("uploaded_total",0)+
          "\nNot sent yet: "+pending;
        String technical="Device: "+(r==null?"Not linked":r.nodeName)+
          "\nTracking mode: "+(activeFolder?"Quest folder (read-only)":"Shizuku")+
          "\nService heartbeat: "+(heartbeat==0?"Never":(now-heartbeat)/1000+" seconds ago")+
          "\nShizuku: "+(activeFolder?"Not required in folder mode":
            Shizuku.pingBinder()?"Connected":"Disconnected")+
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
            startControl.setText(online?"Tracking is running":
                quest&&folderMode&&!folderGrant?"Grant logs folder access":"Start tracking");
          }
          if(stopControl!=null)stopControl.setEnabled(r!=null&&online);
          if(advanced)technicalStatus.setText(technical);
          if(questFolderStatus!=null){
            questFolderStatus.setText("Selected Quest mode: "+
                (folderMode?"Folder access (recommended)":"Shizuku fallback")+
                "\nFolder permission: "+QuestSafLogWatcher.grantedPath(this)+
                (online?"\nActive mode: "+(activeFolder?"Folder":"Shizuku")+
                    "\nStop tracking before switching methods.":""));
            if(questFolderControl!=null){
              questFolderControl.setText(folderGrant?
                  "Change Documents/Logs access":"Grant Documents/Logs permission");
              questFolderControl.setEnabled(!online);
            }
            if(questFolderModeControl!=null){
              questFolderModeControl.setVisibility(folderMode?View.GONE:View.VISIBLE);
              questFolderModeControl.setEnabled(!online);
            }
          }
        });
      }catch(Exception e){showStatus("Status temporarily unavailable. Try refreshing.");}
    });
  }
  private void unlinkConfirm(){
    new AlertDialog.Builder(this).setTitle("Unlink this device?")
      .setMessage("This stops tracking and clears this device's Node setup, unsent encounters and saved local player/log history. Your personal VRChat sign-in is separate.")
      .setNegativeButton("Cancel",null)
      .setPositiveButton("Unlink",(d,w)->{
        stopNode();
        MobileCredentials.clear(this);
        try(MobileOutbox outbox=new MobileOutbox(this)){outbox.wipe();}
        StaffGate.lock();
        showStatus("This device has been unlinked. Contact a group owner if you need a new link.");
        enrollment.setVisibility(View.VISIBLE);
      }).show();
  }
  @Override protected void onResume(){
    super.onResume();
    handler.removeCallbacks(autoRefresh);
    if(status!=null)handler.postDelayed(autoRefresh,2000);
  }
  @Override protected void onSaveInstanceState(Bundle state){
    state.putBoolean("shizuku_help_expanded",helpExpanded);
    state.putBoolean("quest_fallback_expanded",questFallbackExpanded);
    super.onSaveInstanceState(state);
  }
  @Override protected void onPause(){handler.removeCallbacks(autoRefresh);super.onPause();}
  @Override protected void onDestroy(){
    handler.removeCallbacks(autoRefresh);
    Shizuku.removeRequestPermissionResultListener(shizukuResult);
    worker.shutdown();super.onDestroy();
  }
}
