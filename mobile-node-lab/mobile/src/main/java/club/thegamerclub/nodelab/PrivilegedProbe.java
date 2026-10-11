package club.thegamerclub.nodelab;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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


  // The experimental watcher runs inside Shizuku's authorized shell-UID user service.
  // It never writes to VRChat files, holds no credentials, and never sends network traffic.
  private static final Pattern WATCH_JOIN=Pattern.compile("OnPlayerJoined\\s+.*?\\s+\\((usr_[a-fA-F0-9-]{36})\\)");
  private static final Pattern WATCH_LEAVE=Pattern.compile("OnPlayerLeft\\s+.*?\\s+\\((usr_[a-fA-F0-9-]{36})\\)");
  private final Object watchLock=new Object();
  private final Set<String> seenUsers=new HashSet<>();
  private volatile boolean watching=false;
  private int generation=0,joinCount=0,leaveCount=0,unrecognized=0,pollCount=0,errorCount=0,fileSwitches=0;
  private long startedAt=0,lastCheck=0,lastLogChange=0,readBytes=0,lastSeenAppend=0;
  private String trackedPath=null,lastError="none";
  private long offset=0;
  private Thread monitorThread;

  @Override public String startWatching(){
    synchronized(watchLock){
      if(watching)return reportLocked("Already running");
      watching=true;
      generation++;
      startedAt=System.currentTimeMillis();
      lastCheck=0;lastLogChange=0;readBytes=0;lastSeenAppend=0;
      joinCount=0;leaveCount=0;unrecognized=0;pollCount=0;errorCount=0;fileSwitches=0;
      seenUsers.clear();
      lastError="none";
      File existing=latestVrchatFile();
      trackedPath=existing==null?null:existing.getAbsolutePath();
      // Start at EOF of an existing log: count only events occurring after Start.
      offset=existing==null?0:existing.length();
      if(existing!=null)lastLogChange=existing.lastModified();
      final int runGeneration=generation;
      monitorThread=new Thread(()->watchLoop(runGeneration),"tgc-mobile-vrchat-watch");
      monitorThread.setDaemon(true);
      monitorThread.start();
      return reportLocked("Watcher started");
    }
  }

  @Override public String getWatchReport(){
    synchronized(watchLock){return reportLocked(watching?"Watching":"Stopped");}
  }

  @Override public String stopWatching(){
    synchronized(watchLock){
      watching=false;
      generation++;
      return reportLocked("Watcher stopped");
    }
  }

  private String reportLocked(String state){
    long now=System.currentTimeMillis();
    return "TGC Mobile Live Watch v0.3\n"+state+"\n\n"+
      "Watching VRChat Play Store logs: "+(trackedPath!=null?"source found":"waiting for log")+"\n"+
      "Elapsed monitoring seconds: "+(startedAt==0?0:(now-startedAt)/1000)+"\n"+
      "Polling interval: 5 seconds\n"+
      "Polls completed: "+pollCount+"\n"+
      "New join events: "+joinCount+"\n"+
      "Unique new player IDs: "+seenUsers.size()+"\n"+
      "New leave events: "+leaveCount+"\n"+
      "Unrecognized joins: "+unrecognized+"\n"+
      "New log bytes processed: "+readBytes+"\n"+
      "Log rotations/switches: "+fileSwitches+"\n"+
      "Latest log modified seconds ago: "+(lastLogChange==0?"not found":Long.toString(Math.max(0,(now-lastLogChange)/1000)))+"\n"+
      "Last checked seconds ago: "+(lastCheck==0?"not yet":Long.toString(Math.max(0,(now-lastCheck)/1000)))+"\n"+
      "Last appended data seconds ago: "+(lastSeenAppend==0?"not yet":Long.toString(Math.max(0,(now-lastSeenAppend)/1000)))+"\n"+
      "Read errors: "+errorCount+" (latest: "+lastError+")\n"+
      "\nNo player names, IDs, tokens, or log contents included in this report.\n"+
      "No relay traffic. If polls stop increasing while VRChat is open, the Shizuku watcher did not persist.";
  }

  private void watchLoop(int runGeneration){
    while(watching && generation==runGeneration){
      synchronized(watchLock){
        if(!watching||generation!=runGeneration)break;
        try{pollFileLocked();}
        catch(Exception e){errorCount++;lastError=e.getClass().getSimpleName();}
        pollCount++;
        lastCheck=System.currentTimeMillis();
      }
      try{Thread.sleep(5000);}catch(InterruptedException e){Thread.currentThread().interrupt();break;}
    }
  }

  private void pollFileLocked() throws IOException{
    File file=latestVrchatFile();
    if(file==null)return;
    String path=file.getAbsolutePath();
    if(!path.equals(trackedPath)){
      // A freshly-created log starts at zero. Do not import historical files as new sightings.
      boolean genuinelyNew=file.lastModified()>=startedAt-3000;
      trackedPath=path;
      offset=genuinelyNew?0:file.length();
      fileSwitches++;
    }
    lastLogChange=file.lastModified();
    long currentSize=file.length();
    if(currentSize<offset){offset=0;fileSwitches++;}
    if(currentSize==offset)return;
    long consumed=0;
    try(RandomAccessFile reader=new RandomAccessFile(file,"r")){
      reader.seek(offset);
      while(reader.getFilePointer()<currentSize && consumed<2L*1024*1024){
        long position=reader.getFilePointer();
        String line=reader.readLine();
        if(line==null)break;
        long next=reader.getFilePointer();
        if(next>currentSize)break;
        if(next==currentSize && !line.isEmpty()){
          // Avoid parsing an incomplete last line; wait for a newline on the next poll.
          reader.seek(currentSize-1);
          int endByte=reader.read();
          if(endByte!='\n' && endByte!='\r'){reader.seek(position);break;}
        }
        consumed+=next-position;
        offset=next;
        Matcher join=WATCH_JOIN.matcher(line);
        if(join.find()){joinCount++;seenUsers.add(join.group(1));}
        else if(line.contains("OnPlayerJoined"))unrecognized++;
        if(WATCH_LEAVE.matcher(line).find())leaveCount++;
      }
    }
    if(consumed>0){readBytes+=consumed;lastSeenAppend=System.currentTimeMillis();}
  }

  private static File latestVrchatFile(){
    File newest=null;
    for(int i=0;i<2;i++){
      File folder=new File(FOLDERS[i]);
      File candidate=findLog(folder,0);
      if(candidate!=null && (newest==null||candidate.lastModified()>newest.lastModified()))newest=candidate;
    }
    return newest;
  }

  public void destroy(){stopWatching();System.exit(0);}
}
