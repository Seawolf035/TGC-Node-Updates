package club.thegamerclub.nodelab;

import android.app.Activity;
import android.content.ComponentName;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.IBinder;
import rikka.shizuku.Shizuku;
import android.content.ClipData;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
  private static final int PICK_LOG=41;
  private static final int BG=0xFF101827, SURFACE=0xFF1E2B3E, FG=0xFFE6EFF9, SUB=0xFFB6C5D8, CYAN=0xFF62D4EB;
  private final Handler main=new Handler(Looper.getMainLooper());
  private final ExecutorService worker=Executors.newSingleThreadExecutor();
  private TextView display,sourceLabel,statusLabel;
  private Uri selected;
  private String report="No test run yet.";
  private boolean watching=false, reading=false;
  private int lastJoins=-1;
  private static final int ACTION_PROBE=0,ACTION_START=1,ACTION_REPORT=2,ACTION_STOP=3;
  private int requestedAction=ACTION_PROBE;
  private static final int SHIZUKU_REQUEST=532;
  private IPrivilegedProbe privilegedProbe;
  private boolean shizukuBound=false;
  private final Shizuku.OnBinderReceivedListener binderReceivedListener=()->{
    runOnUiThread(()->{
      if(statusLabel!=null)statusLabel.setText("Shizuku connection received. Ready to request access.");
    });
  };
  private final Shizuku.OnBinderDeadListener binderDeadListener=()->{
    privilegedProbe=null;
    shizukuBound=false;
    runOnUiThread(()->{
      if(statusLabel!=null)statusLabel.setText("Shizuku connection lost. Restart Shizuku and reopen this app.");
    });
  };
  private final Shizuku.OnRequestPermissionResultListener permissionListener=(requestCode,grantResult)->{
    if(requestCode!=SHIZUKU_REQUEST)return;
    runOnUiThread(()->{
      if(grantResult==PackageManager.PERMISSION_GRANTED)connectShizuku();
      else show("Shizuku permission was denied. No privileged files were accessed.");
    });
  };
  private final ServiceConnection connection=new ServiceConnection(){
    @Override public void onServiceConnected(ComponentName name,IBinder service){
      privilegedProbe=IPrivilegedProbe.Stub.asInterface(service);
      runOnUiThread(()->show("Shizuku connected. Running the selected test..."));
      performShizukuAction();
    }
    @Override public void onServiceDisconnected(ComponentName name){
      privilegedProbe=null;
      shizukuBound=false;
      runOnUiThread(()->show("Shizuku service disconnected. Reopen the lab or retry after restarting Shizuku."));
    }
  };

  private final Runnable poll=new Runnable(){
    @Override public void run(){
      if(!watching)return;
      if(selected!=null && !reading)readLog(selected,true);
      main.postDelayed(this,10000);
    }
  };

  @Override public void onCreate(Bundle state){
    super.onCreate(state);
    getWindow().setStatusBarColor(BG);
    getWindow().setNavigationBarColor(BG);
    createUi();
    Shizuku.addRequestPermissionResultListener(permissionListener);
    Shizuku.addBinderReceivedListenerSticky(binderReceivedListener);
    Shizuku.addBinderDeadListener(binderDeadListener);
    if(Intent.ACTION_SEND.equals(getIntent().getAction()))acceptShare(getIntent());
  }
  private int px(int value){return (int)(value*getResources().getDisplayMetrics().density+.5f);}
  private GradientDrawable background(int color){
    GradientDrawable drawable=new GradientDrawable();
    drawable.setColor(color);
    drawable.setCornerRadius(px(13));
    return drawable;
  }
  private TextView label(String text,int size,int color,boolean bold){
    TextView label=new TextView(this);
    label.setText(text);
    label.setTextSize(size);
    label.setTextColor(color);
    label.setLineSpacing(px(2),1f);
    if(bold)label.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
    return label;
  }
  private void gap(LinearLayout layout,int pixels){
    layout.addView(new View(this),new LinearLayout.LayoutParams(1,px(pixels)));
  }
  private LinearLayout card(LinearLayout root){
    LinearLayout box=new LinearLayout(this);
    box.setOrientation(LinearLayout.VERTICAL);
    box.setPadding(px(16),px(16),px(16),px(16));
    box.setBackground(background(SURFACE));
    LinearLayout.LayoutParams layout=new LinearLayout.LayoutParams(-1,-2);
    layout.bottomMargin=px(15);
    root.addView(box,layout);
    return box;
  }
  private void button(LinearLayout parent,String text,View.OnClickListener action){
    Button button=new Button(this);
    button.setText(text);
    button.setAllCaps(false);
    button.setTextColor(BG);
    button.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
    button.setBackground(background(CYAN));
    LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,px(50));
    lp.bottomMargin=px(10);
    parent.addView(button,lp);
    button.setOnClickListener(action);
  }
  private void createUi(){
    ScrollView scroll=new ScrollView(this);
    scroll.setFillViewport(true);
    scroll.setBackgroundColor(BG);
    LinearLayout root=new LinearLayout(this);
    root.setOrientation(LinearLayout.VERTICAL);
    root.setPadding(px(18),px(22),px(18),px(30));
    scroll.addView(root);
    setContentView(scroll);

    root.addView(label("TGC / MOBILE NODE LAB",14,CYAN,true));
    gap(root,7);
    root.addView(label("VRChat log access",26,FG,true));
    gap(root,6);
    root.addView(label("Experimental test • Android / Quest • v0.5",13,SUB,false));
    gap(root,18);

    LinearLayout about=card(root);
    about.addView(label("What we're testing",18,FG,true));
    gap(about,8);
    about.addView(label("Monitor new VRChat joins and leaves while you switch into VRChat Mobile. The Shizuku watcher runs in a separate authorized process; results are counts only.",14,SUB,false));
    gap(about,10);
    about.addView(label("No internet or relay traffic. Shizuku access is optional and must be authorized.",13,CYAN,true));

    LinearLayout controls=card(root);
    controls.addView(label("Tests",18,FG,true));
    gap(controls,9);
    button(controls,"1   Import an exported log",v->chooseLog());
    button(controls,"2   Probe VRChat folder access",v->probeFolders());
    button(controls,"3   Test automatic access via Shizuku",v->useShizuku(ACTION_PROBE));
    button(controls,"4   START live watcher (new events only)",v->useShizuku(ACTION_START));
    button(controls,"5   VIEW live watcher results",v->useShizuku(ACTION_REPORT));
    button(controls,"6   STOP live watcher",v->useShizuku(ACTION_STOP));
    sourceLabel=label("Selected log: none",13,SUB,false);
    controls.addView(sourceLabel);
    gap(controls,9);
    statusLabel=label("Import reread: foreground only. Shizuku live watcher: 5-second polling in its authorized process.",12,SUB,false);
    controls.addView(statusLabel);

    LinearLayout output=card(root);
    output.addView(label("Diagnostic results",18,FG,true));
    gap(output,9);
    display=label("No tests run yet.",14,FG,false);
    display.setTextIsSelectable(true);
    output.addView(display);

    button(root,"Share sanitized report",v->shareReport());
    root.addView(label("LIVE TEST: Start watcher, enter a populated VRChat world for 2–3 minutes, then switch back and tap VIEW. The Shizuku process may be terminated by Android; check whether polls increased. No log exports or internet access needed. Stop the watcher after testing.",12,SUB,false));
  }
  private void chooseLog(){
    Intent picker=new Intent(Intent.ACTION_OPEN_DOCUMENT);
    picker.addCategory(Intent.CATEGORY_OPENABLE);
    picker.setType("*/*");
    picker.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
    startActivityForResult(picker,PICK_LOG);
  }
  @Override protected void onActivityResult(int request,int result,Intent data){
    super.onActivityResult(request,result,data);
    if(request!=PICK_LOG || result!=RESULT_OK || data==null || data.getData()==null)return;
    Uri uri=data.getData();
    try{
      getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);
    }catch(RuntimeException ignored){}
    useLog(uri,"File picker");
  }
  private void acceptShare(Intent intent){
    Uri uri=null;
    try{
      Object stream=intent.getParcelableExtra(Intent.EXTRA_STREAM);
      if(stream instanceof Uri)uri=(Uri)stream;
      ClipData clip=intent.getClipData();
      if(uri==null && clip!=null && clip.getItemCount()>0)uri=clip.getItemAt(0).getUri();
    }catch(RuntimeException ignored){}
    if(uri!=null)useLog(uri,"Shared log");
  }
  private void useLog(Uri uri,String description){
    selected=uri;
    lastJoins=-1;
    sourceLabel.setText("Selected: "+description);
    show("Reading imported log...");
    readLog(uri,false);
  }
  private void readLog(Uri uri,boolean periodic){
    if(reading)return;
    reading=true;
    worker.execute(()->{
      String result;
      int joins=-1;
      try(InputStream stream=getContentResolver().openInputStream(uri)){
        if(stream==null)throw new IOException("The file provider did not return a stream.");
        LogParser.Summary s=LogParser.parse(new InputStreamReader(stream,StandardCharsets.UTF_8));
        joins=s.joinEvents;
        int newer=lastJoins<0?0:Math.max(0,joins-lastJoins);
        result="Log read: SUCCESS\n\n"+s.asText()+"\n"+
          (periodic?"New joins since previous check: "+newer+"\n":"")+
          "\nImporting a log does not prove live access to VRChat's private folder.";
      }catch(Exception ex){
        result="Log read: FAILED\n"+ex.getClass().getSimpleName()+
          "\nAndroid may be denying permission or the shared URI may have expired.";
      }
      final int count=joins;
      final String finalResult=result;
      runOnUiThread(()->{
        reading=false;
        if(count>=0)lastJoins=count;
        show(finalResult);
      });
    });
  }
  private void useShizuku(int action){
    requestedAction=action;
    requestShizukuProbe();
  }
  private void requestShizukuProbe(){
    try{
      if(!Shizuku.pingBinder()){
        show("Shizuku connection NOT RECEIVED by this app.\n\nIf Shizuku itself shows 'Running', it is working; the Lab is waiting for its Binder. Force-close and reopen this Lab. If it persists, restart the Shizuku service while leaving the Lab installed.\n\nThis is NOT proof Shizuku is stopped.");
        return;
      }
      if(Shizuku.isPreV11()){
        show("Shizuku v11+ is required.");
        return;
      }
      if(Shizuku.checkSelfPermission()!=PackageManager.PERMISSION_GRANTED){
        if(Shizuku.shouldShowRequestPermissionRationale()){
          show("Shizuku permission denied. Allow TGC Mobile Node Lab in Shizuku settings and retry.");
          return;
        }
        Shizuku.requestPermission(SHIZUKU_REQUEST);
        show("Waiting for your Shizuku permission decision...");
        return;
      }
      connectShizuku();
    }catch(RuntimeException exception){
      show("Cannot contact Shizuku: "+exception.getClass().getSimpleName()+". Check that Shizuku is started.");
    }
  }
  private void connectShizuku(){
    if(privilegedProbe!=null){performShizukuAction();return;}
    if(shizukuBound){show("Waiting for Shizuku service connection...");return;}
    try{
      shizukuBound=true;
      Shizuku.UserServiceArgs args=new Shizuku.UserServiceArgs(
          new ComponentName(this,PrivilegedProbe.class))
          .daemon(true).processNameSuffix("tgc_mobile_watch").version(3).debuggable(true);
      Shizuku.bindUserService(args,connection);
      show("Starting authorized read-only Shizuku service...");
    }catch(RuntimeException exception){
      shizukuBound=false;
      show("Shizuku binding failed: "+exception.getClass().getSimpleName());
    }
  }
  private void performShizukuAction(){
    final IPrivilegedProbe service=privilegedProbe;
    if(service==null){show("Shizuku service not connected.");return;}
    final int action=requestedAction;
    worker.execute(()->{
      try{
        String result;
        if(action==ACTION_START)result=service.startWatching();
        else if(action==ACTION_REPORT)result=service.getWatchReport();
        else if(action==ACTION_STOP)result=service.stopWatching();
        else result=service.probe();
        runOnUiThread(()->show(result));
      }catch(Exception e){
        runOnUiThread(()->show("Shizuku operation failed: "+e.getClass().getSimpleName()+
          "\\nReopen the Lab and retry. If Shizuku's process restarted, monitoring may have reset."));
      }
    });
  }
  private void probeFolders(){
    show("Trying read-only access to VRChat log directories...");
    worker.execute(()->{
      String base=Environment.getExternalStorageDirectory().getAbsolutePath();
      String[] locations={
        base+"/Android/data/com.vrchat.mobile.playstore/files",
        base+"/Android/data/com.vrchat.mobile/files",
        base+"/Documents/Logs"
      };
      StringBuilder result=new StringBuilder("Direct log folder access:\n\n");
      for(String location:locations){
        File folder=new File(location);
        result.append(location).append("\n");
        try{
          result.append("Exists: ").append(folder.exists()).append("\n");
          result.append("Readable: ").append(folder.canRead()).append("\n");
          File[] files=folder.listFiles();
          if(files==null)result.append("Listing: blocked or unavailable\n");
          else{
            int count=0;
            for(File file:files)if(file.getName().startsWith("output_log_")&&file.getName().endsWith(".txt"))count++;
            result.append("Listing: allowed; output logs: ").append(count).append("\n");
          }
        }catch(SecurityException ex){result.append("Listing: Android denied access\n");}
        result.append("\n");
      }
      result.append("Android 11+ often blocks other apps' Android/data folders. This test does not try to bypass Android permissions.");
      runOnUiThread(()->show(result.toString()));
    });
  }
  private void show(String value){
    display.setText(value);
    report="TGC Mobile Node Lab v0.5\nOffline, no credentials\n\n"+value;
  }
  private void shareReport(){
    Intent out=new Intent(Intent.ACTION_SEND);
    out.setType("text/plain");
    out.putExtra(Intent.EXTRA_TEXT,report);
    startActivity(Intent.createChooser(out,"Share diagnostic summary"));
  }
  @Override protected void onResume(){
    super.onResume();
    watching=true;
    main.removeCallbacks(poll);
    main.postDelayed(poll,10000);
    if(statusLabel!=null)statusLabel.setText("Import reread: active. Shizuku watcher runs separately if started.");
  }
  @Override protected void onPause(){
    watching=false;
    main.removeCallbacks(poll);
    if(statusLabel!=null)statusLabel.setText("Import reread paused; Shizuku watcher can continue if Android permits.");
    super.onPause();
  }
  @Override protected void onDestroy(){
    Shizuku.removeRequestPermissionResultListener(permissionListener);
    Shizuku.removeBinderReceivedListener(binderReceivedListener);
    Shizuku.removeBinderDeadListener(binderDeadListener);
    main.removeCallbacks(poll);
    worker.shutdown();
    super.onDestroy();
  }
}
