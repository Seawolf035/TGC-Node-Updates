package club.thegamerclub.nodelab;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Locale;

/**
 * Runs in Shizuku's shell-UID user service, with explicit permission from the phone owner.
 * All access is read-only. The returned report never contains player names, user IDs or raw logs.
 */
public final class PrivilegedProbe extends IPrivilegedProbe.Stub {
  private static final String[] FOLDERS={
    "/storage/emulated/0/Android/data/com.vrchat.mobile.playstore/files",
    "/storage/emulated/0/Android/data/com.vrchat.mobile/files",
    "/storage/emulated/0/Documents/Logs"
  };

  @Override public String probe(){
    StringBuilder result=new StringBuilder("Shizuku privileged probe\nRead-only; no credentials or relay access\n\n");
    File candidate=null;
    for(String path:FOLDERS){
      File folder=new File(path);
      result.append("Log source: ").append(path.contains("Documents")?"Shared Documents":path.contains("playstore")?"Play Store VRChat":"Other VRChat package").append("\n");
      result.append("Readable: ").append(folder.canRead()).append("\n");
      try{
        File[] files=folder.listFiles();
        if(files==null){result.append("Directory listing denied or unavailable\n\n");continue;}
        result.append("Directory listing: allowed\n");
        int found=0;
        File latest=null;
        for(File file:files){
          File log=findLog(file,0);
          if(log!=null){
            found++;
            if(latest==null || log.lastModified()>latest.lastModified())latest=log;
          }
        }
        result.append("Log-containing entries: ").append(found).append("\n");
        if(latest!=null && (candidate==null || latest.lastModified()>candidate.lastModified()))candidate=latest;
      }catch(SecurityException ex){result.append("Directory listing denied\n");}
      result.append("\n");
    }
    if(candidate!=null){
      result.append("Live log discovered: yes\n");
      result.append("Bytes: ").append(candidate.length()).append("\n");
      try(FileInputStream input=new FileInputStream(candidate)){
        LogParser.Summary count=LogParser.parse(new InputStreamReader(input,StandardCharsets.UTF_8));
        result.append("File reading: SUCCESS\n").append(count.asText()).append("\n");
        result.append("File recency (seconds): ").append(Math.max(0,(System.currentTimeMillis()-candidate.lastModified())/1000L)).append("\n");
      }catch(IOException|SecurityException ex){
        result.append("File reading: FAILED (").append(ex.getClass().getSimpleName()).append(")\n");
      }
    }else{result.append("Live file reading: NO ACCESSIBLE LOG FOUND\n");}
    result.append("\nSystem logcat test (last 3000 lines):\n");
    Process process=null;
    try{
      process=new ProcessBuilder("/system/bin/logcat","-d","-v","brief","-t","3000").redirectErrorStream(true).start();
      try(BufferedReader reader=new BufferedReader(new InputStreamReader(process.getInputStream(),StandardCharsets.UTF_8))){
        // Parse in memory and only return aggregate counts, never raw device logs.
        LogParser.Summary count=LogParser.parse(reader);
        result.append("System log read: SUCCESS\n").append(count.asText()).append("\n");
      }
      if(!process.waitFor(6,java.util.concurrent.TimeUnit.SECONDS)){
        process.destroyForcibly();
        result.append("System log probe timed out\n");
      }else{
        result.append("System log command exit: ").append(process.exitValue()).append("\n");
      }
    }catch(IOException|InterruptedException|SecurityException ex){
      result.append("System log read: FAILED (").append(ex.getClass().getSimpleName()).append(")\n");
      if(ex instanceof InterruptedException)Thread.currentThread().interrupt();
    }finally{if(process!=null)process.destroy();}
    result.append("\nAutomatic source available: ")
      .append(candidate!=null?"CHECK FILE RECENCY AND COUNTS ABOVE":"CHECK SYSTEM LOG COUNTS ABOVE")
      .append("\nA successful single read does not yet verify monitoring while VRChat is foreground.");
    return result.toString();
  }

  private static File findLog(File item,int depth){
    if(depth>3)return null;
    if(item.isFile() && item.getName().startsWith("output_log_") && item.getName().endsWith(".txt"))return item;
    if(!item.isDirectory())return null;
    File[] children;
    try{children=item.listFiles();}catch(SecurityException e){return null;}
    if(children==null)return null;
    File candidate=null;
    for(File child:children){
      File log=findLog(child,depth+1);
      if(log!=null && (candidate==null || log.lastModified()>candidate.lastModified()))candidate=log;
    }
    return candidate;
  }

  public void destroy(){System.exit(0);}
}
