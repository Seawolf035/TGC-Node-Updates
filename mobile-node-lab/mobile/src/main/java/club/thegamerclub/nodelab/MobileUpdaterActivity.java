package club.thegamerclub.nodelab;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.core.content.FileProvider;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MobileUpdaterActivity extends Activity {
  // Preview feed; production release feed will reside on the default branch.
  static final String FEED="https://raw.githubusercontent.com/Seawolf035/TGC-Node-Updates/experiment/mobile-node-lab/mobile-node-lab/mobile-update.json";
  private static final long MAX_APK_BYTES=120L*1024*1024;
  private final ExecutorService io=Executors.newSingleThreadExecutor();
  private TextView status;
  private ProgressBar progress;
  private volatile boolean downloading=false;
  private File readyApk;
  private int readyVersion;

  @Override public void onCreate(Bundle state){
    super.onCreate(state);
    getWindow().setStatusBarColor(CompanionStyle.BG);
    getWindow().setNavigationBarColor(CompanionStyle.BG);
    ScrollView scroll=new ScrollView(this);
    scroll.setBackgroundColor(CompanionStyle.BG);
    LinearLayout root=new LinearLayout(this);
    root.setOrientation(LinearLayout.VERTICAL);
    root.setPadding(dp(18),dp(20),dp(18),dp(30));
    scroll.addView(root);setContentView(scroll);
    CompanionStyle.safeArea(this,scroll);
    Button back=CompanionStyle.button(this,"← Back",false);
    root.addView(back,new LinearLayout.LayoutParams(dp(115),dp(44)));
    back.setOnClickListener(v->finish());
    TextView kicker=CompanionStyle.section(this,"COMPANION");
    root.addView(kicker);
    root.addView(text("Updates",28,CompanionStyle.TEXT));
    root.addView(text("Keep your Companion up to date. Your account, tracking, and settings stay in place when you update.",14,CompanionStyle.MUTED));
    LinearLayout body=CompanionStyle.card(this);
    root.addView(body,CompanionStyle.cardParams(this));
    status=text("Checking for updates...",14,CompanionStyle.ACCENT);
    body.addView(status);
    progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
    progress.setMax(100);progress.setVisibility(View.GONE);
    body.addView(progress);
    button(body,"Check for updates",this::check);
    button(body,"Install downloaded update",this::install);
    check();
  }
  private int dp(int x){return CompanionStyle.dp(this,x);}
  private TextView text(String value,int sp,int color){
    TextView t=CompanionStyle.text(this,value,sp,color,sp>=25);
    t.setPadding(0,dp(9),0,dp(10));return t;
  }
  private void button(LinearLayout root,String title,Runnable action){
    Button b=CompanionStyle.button(this,title,true);
    root.addView(b,CompanionStyle.buttonParams(this));
    b.setOnClickListener(v->action.run());
  }
  private void show(String value){runOnUiThread(()->status.setText(value));}
  private void check(){
    if(downloading)return;
    downloading=true;
    progress.setVisibility(View.GONE);
    readyApk=null;
    show("Checking for a newer release...");
    io.execute(()->{
      try{
        JSONObject release=fetchManifest();
        int latest=release.getInt("version_code");
        int current=localVersion();
        if(latest<=current){
          show("You're up to date!\nVersion "+release.optString("version"));
          return;
        }
        show("A new version is available: "+release.optString("version")+
          "\nDownloading the update...");
        runOnUiThread(()->{progress.setProgress(0);progress.setVisibility(View.VISIBLE);});
        File candidate=download(release);
        verifySameSigner(candidate);
        readyApk=candidate;readyVersion=latest;
        show("Version "+release.optString("version")+
          " is ready. Tap Install downloaded update. Android will ask for approval.");
      }catch(Exception e){
        readyApk=null;
        String m=e.getMessage();
        if(m==null)m=e.getClass().getSimpleName();
        if(m.length()>320)m=m.substring(0,320);
        show("Couldn't check for updates:\n"+m);
      }finally{downloading=false;}
    });
  }
  private JSONObject fetchManifest() throws Exception{
    HttpURLConnection connection=connection(FEED);
    try(InputStream stream=connection.getInputStream()){
      byte[] b=readCapped(stream,65536);
      JSONObject j=new JSONObject(new String(b,StandardCharsets.UTF_8));
      if(j.optInt("schema")!=1 || !"mobile_companion".equals(j.optString("channel")))
        throw new SecurityException("Invalid mobile release feed");
      if(j.optInt("version_code",0)<1)throw new SecurityException("Release version missing");
      if(!j.optString("sha256").matches("(?i)[0-9a-f]{64}"))
        throw new SecurityException("Missing APK SHA-256");
      approvedUrl(j.getString("apk_url"));
      return j;
    }finally{connection.disconnect();}
  }
  private static void approvedUrl(String raw) throws Exception{
    URL u=new URL(raw);
    if(!"https".equalsIgnoreCase(u.getProtocol()) ||
       !"raw.githubusercontent.com".equalsIgnoreCase(u.getHost()) ||
       !u.getPath().startsWith("/Seawolf035/TGC-Node-Updates/"))
      throw new SecurityException("Update must come from TGC's official GitHub repository");
  }
  private static HttpURLConnection connection(String raw) throws Exception{
    approvedUrl(raw);
    HttpURLConnection c=(HttpURLConnection)new URL(raw).openConnection();
    c.setConnectTimeout(15000);c.setReadTimeout(20000);
    c.setRequestProperty("Accept","application/octet-stream");
    c.setInstanceFollowRedirects(false);
    if(c.getResponseCode()!=200){int http=c.getResponseCode();c.disconnect();
      throw new IllegalStateException("GitHub update request returned HTTP "+http);}
    return c;
  }
  private static byte[] readCapped(InputStream in,int limit) throws Exception{
    java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();
    byte[] buf=new byte[4096];int n;
    while((n=in.read(buf))>=0){if(out.size()+n>limit)throw new SecurityException("Update metadata too large");out.write(buf,0,n);}
    return out.toByteArray();
  }
  private File download(JSONObject release) throws Exception{
    File dir=new File(getCacheDir(),"updates");
    if(!dir.isDirectory()&&!dir.mkdirs())throw new IllegalStateException("Cannot create cache");
    File target=new File(dir,"companion-"+release.getInt("version_code")+".apk");
    File temporary=new File(dir,"pending.part");
    HttpURLConnection c=connection(release.getString("apk_url"));
    MessageDigest sha=MessageDigest.getInstance("SHA-256");
    long bytes=0;
    try(InputStream input=new BufferedInputStream(c.getInputStream());
        FileOutputStream output=new FileOutputStream(temporary)){
      byte[] buffer=new byte[16384];int n;
      while((n=input.read(buffer))!=-1){
        bytes+=n;if(bytes>MAX_APK_BYTES)throw new SecurityException("Update is too large");
        sha.update(buffer,0,n);output.write(buffer,0,n);
        final int pct=(int)Math.min(95,bytes*100L/Math.max(1,c.getContentLengthLong()));
        runOnUiThread(()->progress.setProgress(pct));
      }
      output.getFD().sync();
    }finally{c.disconnect();}
    if(bytes<10000)throw new SecurityException("APK download unexpectedly small");
    StringBuilder found=new StringBuilder();
    for(byte b:sha.digest())found.append(String.format(java.util.Locale.US,"%02x",b&0xff));
    if(!MessageDigest.isEqual(found.toString().getBytes(StandardCharsets.US_ASCII),
        release.getString("sha256").toLowerCase(java.util.Locale.US).getBytes(StandardCharsets.US_ASCII))){
      temporary.delete();throw new SecurityException("APK SHA-256 mismatch; refusing installation");
    }
    if(target.exists()&&!target.delete())throw new IllegalStateException("Cannot replace old download");
    if(!temporary.renameTo(target))throw new IllegalStateException("Cannot finalize APK");
    runOnUiThread(()->progress.setProgress(100));
    return target;
  }
  @SuppressWarnings("deprecation")
  private Signature[] installedSignatures() throws Exception{
    if(Build.VERSION.SDK_INT>=28){
      PackageInfo p=getPackageManager().getPackageInfo(getPackageName(),PackageManager.GET_SIGNING_CERTIFICATES);
      return p.signingInfo.getApkContentsSigners();
    }
    return getPackageManager().getPackageInfo(getPackageName(),PackageManager.GET_SIGNATURES).signatures;
  }
  @SuppressWarnings("deprecation")
  private Signature[] downloadedSignatures(File file) throws Exception{
    PackageInfo p=getPackageManager().getPackageArchiveInfo(file.getAbsolutePath(),
      Build.VERSION.SDK_INT>=28?PackageManager.GET_SIGNING_CERTIFICATES:PackageManager.GET_SIGNATURES);
    if(p==null||!getPackageName().equals(p.packageName))
      throw new SecurityException("APK has the wrong Android package ID");
    Signature[] sigs=Build.VERSION.SDK_INT>=28?p.signingInfo.getApkContentsSigners():p.signatures;
    if(sigs==null||sigs.length==0)throw new SecurityException("APK is not signed");
    return sigs;
  }
  private void verifySameSigner(File file) throws Exception{
    Signature[] current=installedSignatures(),candidate=downloadedSignatures(file);
    if(current==null||current.length==0||!Arrays.equals(current,candidate))
      throw new SecurityException("Different APK signing certificate. Cannot install this over your current version. A permanent signing key is required for seamless updates.");
  }
  private int localVersion() throws Exception{
    PackageInfo info=getPackageManager().getPackageInfo(getPackageName(),0);
    return Build.VERSION.SDK_INT>=28?(int)info.getLongVersionCode():info.versionCode;
  }
  private void install(){
    File f=readyApk;
    if(f==null||!f.isFile()){show("No update is ready yet. Tap Check for updates first.");return;}
    if(Build.VERSION.SDK_INT>=26&&!getPackageManager().canRequestPackageInstalls()){
      Intent allow=new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
        Uri.parse("package:"+getPackageName()));
      show("Allow installations from TGC Staff Companion, return here, then tap Install again.");
      startActivity(allow);return;
    }
    try{
      Uri uri=FileProvider.getUriForFile(this,getPackageName()+".updates",f);
      Intent intent=new Intent(Intent.ACTION_VIEW);
      intent.setDataAndType(uri,"application/vnd.android.package-archive");
      intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
      startActivity(intent);
    }catch(ActivityNotFoundException e){show("No Android APK installer available.");}
    catch(Exception e){show("Could not launch installer: "+e.getClass().getSimpleName());}
  }
  @Override public void onDestroy(){io.shutdownNow();super.onDestroy();}
}
