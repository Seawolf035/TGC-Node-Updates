package club.thegamerclub.nodelab;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.UriPermission;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Experimental, read-only Quest log watcher using the app UID's user-granted
 * Documents/Logs directory. No Shizuku process, unrestricted storage permission,
 * direct Relay connection, VRChat credentials, or access outside the chosen tree.
 *
 * Implements the existing local AIDL interface so MobileNodeService continues
 * using its durable SQLite queues, staff checks, and unchanged Relay uploader.
 * Calls are in-process; pollOnce() runs on the service's collection thread.
 */
public final class QuestSafLogWatcher extends IPrivilegedProbe.Stub {
    private static final String PREF="tgc_quest_log_folder";
    private static final String MODE="mode", TREE="tree";
    private static final String FOLDER="folder", SHIZUKU="shizuku";
    private static final long MAX_POLL=2L*1024L*1024L;
    private static final int MAX_PENDING=4000;
    private static final Pattern JOIN=Pattern.compile(
        "OnPlayerJoined\\s+.*?\\s+\\((usr_[a-fA-F0-9-]{36})\\)");
    private static final Pattern LEAVE=Pattern.compile(
        "OnPlayerLeft\\s+.*?\\s+\\((usr_[a-fA-F0-9-]{36})\\)");
    private static final Pattern INSTANCE=Pattern.compile(
        "\\bJoining\\s+(wrld_[a-fA-F0-9-]{36}:[^\\s]+)");
    private final Context context;
    private final LinkedHashMap<String,JSONObject> sightings=new LinkedHashMap<>();
    private final LinkedHashMap<String,JSONObject> activity=new LinkedHashMap<>();
    private final Set<String> users=new HashSet<>();
    private boolean watching=false;
    private String trackedUri=null,lastInstance="",lastError="none";
    private long started=0,offset=0,lastCheck=0,lastModified=0,lastAppend=0,readBytes=0;
    private int polls=0,joins=0,leaves=0,unrecognized=0,errors=0,rotations=0,dropped=0;

    public QuestSafLogWatcher(Context context){
        this.context=context.getApplicationContext();
    }
    public static boolean isSelected(Context ctx){
        return QuestDevice.isQuestDevice() && FOLDER.equals(
            ctx.getSharedPreferences(PREF,Context.MODE_PRIVATE).getString(MODE,SHIZUKU));
    }
    public static void selectShizuku(Context ctx){
        ctx.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit()
            .putString(MODE,SHIZUKU).apply();
    }
    public static boolean hasGrant(Context ctx){
        String stored=ctx.getSharedPreferences(PREF,Context.MODE_PRIVATE)
            .getString(TREE,"");
        if(stored.isEmpty())return false;
        for(UriPermission permission:ctx.getContentResolver().getPersistedUriPermissions()){
            if(permission.isReadPermission() &&
               stored.equals(permission.getUri().toString()))return true;
        }
        return false;
    }
    public static String grantedPath(Context ctx){
        return hasGrant(ctx)?"Documents/Logs (read-only)":"No folder permission";
    }
    public static void selectFolder(Context ctx,Uri uri){
        if(!QuestDevice.isQuestDevice())throw new SecurityException(
            "Folder experiment is only available on Quest");
        if(uri==null||!"content".equals(uri.getScheme())||!isLogsTree(uri))
            throw new IllegalArgumentException("Select the Documents/Logs folder, not Documents or device root");
        ContentResolver resolver=ctx.getContentResolver();
        resolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);
        ctx.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit()
            .putString(TREE,uri.toString()).putString(MODE,FOLDER).apply();
        if(!hasGrant(ctx))throw new SecurityException(
            "Android did not retain read permission for this folder");
    }
    private static boolean isLogsTree(Uri uri){
        try{
            String id=DocumentsContract.getTreeDocumentId(uri).replace('\\','/');
            return id.equals("Documents/Logs") || id.endsWith(":Documents/Logs");
        }catch(RuntimeException error){return false;}
    }
    private Uri treeUri() throws IOException {
        if(!hasGrant(context))throw new IOException(
            "Documents/Logs folder permission missing or revoked");
        String value=context.getSharedPreferences(PREF,Context.MODE_PRIVATE)
            .getString(TREE,"");
        Uri uri=Uri.parse(value);
        if(!isLogsTree(uri))throw new IOException("Selected folder is not Documents/Logs");
        return uri;
    }
    private static final class LogDocument {
        final Uri uri;
        final String name;
        final long modified,size;
        LogDocument(Uri uri,String name,long modified,long size){
            this.uri=uri;this.name=name;this.modified=modified;this.size=size;
        }
    }
    /** The system provider returns child IDs; never construct raw filesystem paths. */
    private LogDocument latest() throws IOException{
        Uri tree=treeUri();
        Uri children=DocumentsContract.buildChildDocumentsUriUsingTree(
            tree,DocumentsContract.getTreeDocumentId(tree));
        String[] columns={
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            DocumentsContract.Document.COLUMN_SIZE
        };
        LogDocument chosen=null;
        try(Cursor c=context.getContentResolver().query(children,columns,null,null,null)){
            if(c==null)throw new IOException("Quest file provider did not return folder contents");
            while(c.moveToNext()){
                String name=c.getString(1);
                if(name==null||!name.startsWith("output_log_")||!name.endsWith(".txt"))
                    continue;
                String id=c.getString(0);
                if(id==null)continue;
                long time=c.isNull(2)?0:c.getLong(2);
                long size=c.isNull(3)?-1:c.getLong(3);
                LogDocument current=new LogDocument(
                    DocumentsContract.buildDocumentUriUsingTree(tree,id),name,time,size);
                if(chosen==null||time>chosen.modified ||
                    (time==chosen.modified&&name.compareTo(chosen.name)>0))
                    chosen=current;
            }
        }catch(SecurityException e){
            throw new IOException("Quest folder read permission denied",e);
        }catch(IllegalArgumentException e){
            throw new IOException("Quest file provider does not support folder listing",e);
        }
        return chosen;
    }
    private long fileSize(LogDocument file) throws IOException{
        if(file.size>=0)return file.size;
        try(ParcelFileDescriptor fd=context.getContentResolver().openFileDescriptor(file.uri,"r")){
            if(fd==null||fd.getStatSize()<0)throw new IOException(
                "Quest file provider cannot report output log size");
            return fd.getStatSize();
        }
    }
    @Override public synchronized String probe(){
        try{
            LogDocument file=latest();
            return "Quest SAF Documents/Logs permission: granted\n"+
                "Readable log: "+(file==null?"not yet found":"yes")+
                "\nNo Shizuku required; no raw logs or player data returned.";
        }catch(Exception e){return "Quest SAF folder access unavailable: "+e.getClass().getSimpleName();}
    }
    @Override public synchronized String startWatching(){
        if(watching)return getWatchReport();
        watching=true;
        started=System.currentTimeMillis();lastCheck=0;lastModified=0;lastAppend=0;
        readBytes=0;polls=0;joins=0;leaves=0;errors=0;unrecognized=0;rotations=0;
        users.clear();lastInstance="";lastError="none";trackedUri=null;offset=0;
        // Preserve unsent/unsaved events across a watcher restart, just as Shizuku does.
        try{
            LogDocument doc=latest();
            if(doc!=null){
                trackedUri=doc.uri.toString();
                offset=fileSize(doc); // Baseline at EOF; never replay prior sightings.
                lastModified=doc.modified;
            }
        }catch(Exception e){
            errors++;
            lastError=reason(e);
        }
        return getWatchReport();
    }
    @Override public synchronized String stopWatching(){
        watching=false;return getWatchReport();
    }
    @Override public synchronized String getWatchReport(){
        return "Quest folder watcher "+(watching?"running":"stopped")+
            "\nFolder: Documents/Logs\nSource: "+(trackedUri==null?"waiting":"found")+
            "\nPolls: "+polls+"\nJoins: "+joins+"\nLeaves: "+leaves+
            "\nBytes processed: "+readBytes+"\nErrors: "+errors+" ("+lastError+")";
    }
    @Override public synchronized String getWatchStats(){
        JSONObject s=new JSONObject();
        try{
            s.put("watching",watching);s.put("polls",polls);s.put("joins",joins);
            s.put("unique_users",users.size());s.put("leaves",leaves);
            s.put("unrecognized",unrecognized);s.put("read_errors",errors);
            s.put("last_error",lastError);s.put("bytes",readBytes);
            s.put("pending",sightings.size());s.put("rotations",rotations);
            s.put("last_check",lastCheck);s.put("last_append",lastAppend);
            s.put("last_modified",lastModified);s.put("source_found",trackedUri!=null);
        }catch(Exception ignored){}
        return s.toString();
    }
    /** Called by the ordinary five-second MobileNodeService collection loop. */
    public synchronized void pollOnce(){
        if(!watching)return;
        try{
            LogDocument file=latest();
            if(file!=null){
                long size=fileSize(file);
                String uri=file.uri.toString();
                if(!uri.equals(trackedUri)){
                    boolean fresh=file.modified>=started-3000L;
                    trackedUri=uri;
                    offset=fresh?0:size;
                    rotations++;
                }
                lastModified=file.modified;
                if(size<offset){
                    // Truncation in the same file, not an old file being re-imported.
                    offset=0;rotations++;
                }
                if(size>offset)readDelta(file,size);
                lastError="none";
            }else{
                trackedUri=null;
                lastError="Waiting for output_log_*.txt in Documents/Logs";
            }
        }catch(Exception e){
            errors++;lastError=reason(e);
        }
        polls++;lastCheck=System.currentTimeMillis();
    }
    private static String reason(Exception e){
        String message=e.getMessage();
        if(message==null||message.isEmpty())return e.getClass().getSimpleName();
        // Never include provider URIs, local paths, or sensitive data in status.
        if(message.contains("permission")||message.contains("Permission"))
            return "Documents/Logs read permission unavailable";
        if(message.contains("provider"))return "Quest file provider cannot read logs";
        if(message.contains("size"))return "Quest file provider cannot report log size";
        return e.getClass().getSimpleName();
    }
    private static void skipFully(FileInputStream in,long bytes) throws IOException{
        while(bytes>0){
            long n=in.skip(bytes);
            if(n<=0){
                if(in.read()<0)throw new IOException("Log changed during read");
                n=1;
            }
            bytes-=n;
        }
    }
    private void readDelta(LogDocument file,long size) throws IOException{
        int amount=(int)Math.min(MAX_POLL,size-offset);
        ByteArrayOutputStream data=new ByteArrayOutputStream(Math.min(amount,65536));
        try(ParcelFileDescriptor fd=context.getContentResolver().openFileDescriptor(file.uri,"r")){
            if(fd==null)throw new IOException("Quest file provider cannot open output log");
            try(FileInputStream input=new FileInputStream(fd.getFileDescriptor())){
                skipFully(input,offset);
                byte[] buffer=new byte[8192];
                int remaining=amount;
                while(remaining>0){
                    int n=input.read(buffer,0,Math.min(buffer.length,remaining));
                    if(n<0)break;
                    if(n==0)break;
                    data.write(buffer,0,n);
                    remaining-=n;
                }
            }
        }catch(SecurityException e){throw new IOException("Folder permission denied",e);}
        byte[] bytes=data.toByteArray();
        int complete=-1;
        for(int i=bytes.length-1;i>=0;i--){
            if(bytes[i]=='\n'){complete=i;break;}
        }
        if(complete<0)return; // Incomplete log line: retry it during the next poll.
        int lineStart=0;
        for(int i=0;i<=complete;i++){
            if(bytes[i]=='\n'){
                int end=i;
                if(end>lineStart&&bytes[end-1]=='\r')end--;
                processLine(new String(bytes,lineStart,end-lineStart,StandardCharsets.UTF_8));
                lineStart=i+1;
            }
        }
        long consumed=complete+1L;
        offset+=consumed;
        readBytes+=consumed;
        lastAppend=System.currentTimeMillis();
    }
    private void processLine(String line){
        Matcher entering=INSTANCE.matcher(line);
        if(entering.find()){
            String location=entering.group(1);
            if(location.length()>220)location=location.substring(0,220);
            if(!location.equals(lastInstance)){
                lastInstance=location;
                addActivity("instance","","",location);
            }
        }
        Matcher join=JOIN.matcher(line);
        if(join.find()){
            String uid=join.group(1);
            joins++;users.add(uid);
            String name=extractName(line,"OnPlayerJoined");
            addSighting(uid,name);
            addActivity("join",uid,name,lastInstance);
        }else if(line.contains("OnPlayerJoined"))unrecognized++;
        Matcher leave=LEAVE.matcher(line);
        if(leave.find()){
            leaves++;addActivity("leave",leave.group(1),
                extractName(line,"OnPlayerLeft"),lastInstance);
        }
    }
    private static String extractName(String line,String event){
        int at=line.indexOf(event);
        if(at<0)return "";
        String rest=line.substring(at+event.length()).trim();
        int end=rest.lastIndexOf(" (usr_");
        String name=end>=0?rest.substring(0,end).trim():"";
        return name.length()>180?name.substring(0,180):name;
    }
    private void addActivity(String kind,String uid,String name,String location){
        if(activity.size()>=MAX_PENDING){dropped++;return;}
        JSONObject obj=new JSONObject();
        try{
            obj.put("event_id","local_"+UUID.randomUUID().toString());
            obj.put("kind",kind);obj.put("user_id",uid);
            obj.put("display_name",name);obj.put("location",location);
            obj.put("time",System.currentTimeMillis());
            activity.put(obj.getString("event_id"),obj);
        }catch(Exception ignored){}
    }
    private void addSighting(String uid,String name){
        if(sightings.size()>=MAX_PENDING){dropped++;return;}
        SimpleDateFormat iso=new SimpleDateFormat(
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",Locale.US);
        iso.setTimeZone(TimeZone.getTimeZone("UTC"));
        JSONObject obj=new JSONObject();
        try{
            obj.put("event_id","mobile_"+UUID.randomUUID().toString());
            obj.put("user_id",uid);obj.put("display_name",name);
            obj.put("seen_at",iso.format(new Date()));
            sightings.put(obj.getString("event_id"),obj);
        }catch(Exception ignored){}
    }
    private static String snapshot(LinkedHashMap<String,JSONObject> source,int limit){
        JSONArray arr=new JSONArray();int cap=Math.max(1,Math.min(200,limit));
        for(JSONObject item:source.values()){
            if(arr.length()>=cap)break;
            arr.put(item);
        }
        return arr.toString();
    }
    private static void ack(LinkedHashMap<String,JSONObject> source,String json){
        try{
            JSONArray array=new JSONArray(json);
            for(int i=0;i<Math.min(250,array.length());i++)
                source.remove(array.optString(i,""));
        }catch(Exception ignored){}
    }
    @Override public synchronized String pendingSightings(int limit){
        return snapshot(sightings,limit);
    }
    @Override public synchronized void ackSightings(String ids){ack(sightings,ids);}
    @Override public synchronized String pendingActivity(int limit){
        return snapshot(activity,limit);
    }
    @Override public synchronized void ackActivity(String ids){ack(activity,ids);}
}
