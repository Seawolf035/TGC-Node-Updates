package club.thegamerclub.nodelab;

import android.content.Context;
import android.util.AtomicFile;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Iterator;

/** Per-personal-account, device-local friend changes. Contains no authentication data. */
public final class FriendLogStore {
    private FriendLogStore(){}
    private static final int MAX_EVENTS=5000;
    private static final long MIN_POLL_MS=60_000L;

    public static String account(JSONObject session){
        String id=session.optString("userId","");
        if(!id.matches("usr_[0-9a-fA-F-]{36}"))
            throw new SecurityException("Sign in to your personal VRChat account to view friend history");
        return id;
    }
    private static File path(Context ctx,String id){
        return new File(ctx.getFilesDir(),"friend_log_"+id+".json");
    }
    private static JSONObject read(File path)throws Exception{
        AtomicFile a=new AtomicFile(path);
        if(!path.exists())return new JSONObject().put("events",new JSONArray()).put("snapshot",new JSONObject());
        try(FileInputStream in=a.openRead()){
            java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();
            byte[] buffer=new byte[8192];int n;
            while((n=in.read(buffer))>=0){
                if(n==0)continue;
                if(out.size()+n>8_000_000)throw new IllegalStateException("Friend history file is unexpectedly large");
                out.write(buffer,0,n);
            }
            return new JSONObject(new String(out.toByteArray(),StandardCharsets.UTF_8));
        }
    }
    private static void write(File path,JSONObject value)throws Exception{
        AtomicFile a=new AtomicFile(path);
        FileOutputStream out=a.startWrite();
        try{
            out.write(value.toString().getBytes(StandardCharsets.UTF_8));
            a.finishWrite(out);
        }catch(Exception e){
            a.failWrite(out);
            throw e;
        }
    }
    public static synchronized JSONArray events(Context ctx,JSONObject session)throws Exception{
        JSONArray list=read(path(ctx,account(session))).optJSONArray("events");
        return list==null?new JSONArray():list;
    }
    public static synchronized JSONObject snapshot(Context ctx,JSONObject session)throws Exception{
        JSONObject object=read(path(ctx,account(session))).optJSONObject("snapshot");
        return object==null?new JSONObject():object;
    }
    public static synchronized long lastPoll(Context ctx,JSONObject session)throws Exception{
        return read(path(ctx,account(session))).optLong("lastPoll",0L);
    }
    /**
     * Input must be an entire normalized online+offline friend snapshot, not a partial page.
     * The first successful snapshot seeds state without generating invented past events.
     */
    public static synchronized int observe(Context ctx,JSONObject session,JSONArray complete)throws Exception{
        File dest=path(ctx,account(session));
        JSONObject data=read(dest);
        long now=System.currentTimeMillis();
        if(now-data.optLong("lastPoll",0L)<MIN_POLL_MS)return 0;
        JSONObject old=data.optJSONObject("snapshot");
        if(old==null)old=new JSONObject();
        JSONObject next=new JSONObject();
        HashSet<String> ids=new HashSet<>();
        for(int i=0;i<complete.length();i++){
            JSONObject f=complete.optJSONObject(i);
            if(f==null)continue;
            String uid=f.optString("id","");
            if(!uid.matches("usr_[0-9a-fA-F-]{36}") || !ids.add(uid))continue;
            next.put(uid,new JSONObject()
                .put("name",f.optString("displayName",uid))
                .put("status",f.optString("status","unknown"))
                .put("statusDescription",f.optString("statusDescription",""))
                .put("location",f.optString("location","unknown"))
                .put("online",f.optBoolean("isOnline",false)));
        }
        JSONArray history=data.optJSONArray("events");
        if(history==null)history=new JSONArray();
        int changes=0;
        if(data.optBoolean("initialized",false)){
            Iterator<String> iterator=next.keys();
            while(iterator.hasNext()){
                String uid=iterator.next();
                JSONObject curr=next.getJSONObject(uid),prior=old.optJSONObject(uid);
                if(prior==null){
                    history.put(event(now,uid,curr,"friend_added",""));changes++;
                    continue;
                }
                if(prior.optBoolean("online")!=curr.optBoolean("online")){
                    history.put(event(now,uid,curr,curr.optBoolean("online")?"online":"offline",""));changes++;
                }
                for(String key:new String[]{"location","status","statusDescription","name"}){
                    String before=prior.optString(key,"");
                    String after=curr.optString(key,"");
                    if(!before.equals(after)){
                        history.put(event(now,uid,curr,key,before));
                        changes++;
                    }
                }
            }
            Iterator<String> gone=old.keys();
            while(gone.hasNext()){
                String uid=gone.next();
                if(!next.has(uid)){
                    history.put(event(now,uid,old.getJSONObject(uid),"friend_removed",""));changes++;
                }
            }
        }
        JSONArray trimmed=new JSONArray();
        for(int i=Math.max(0,history.length()-MAX_EVENTS);i<history.length();i++)trimmed.put(history.get(i));
        data.put("events",trimmed).put("snapshot",next)
            .put("initialized",true).put("lastPoll",now);
        write(dest,data);
        return changes;
    }
    private static JSONObject event(long at,String id,JSONObject user,String type,String previous)throws Exception{
        return new JSONObject().put("time",at).put("userId",id)
            .put("name",user.optString("name",id)).put("type",type)
            .put("location",user.optString("location","unknown"))
            .put("status",user.optString("status","unknown"))
            .put("statusDescription",user.optString("statusDescription",""))
            .put("previous",previous);
    }
}
