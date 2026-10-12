package club.thegamerclub.nodelab;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Account-scoped, device-local friend observation history. Never stores session cookies. */
public final class FriendLogStore {
    private FriendLogStore(){}
    private static final int MAX_EVENTS=5000;
    private static final long MIN_POLL_MS=45_000L;
    private static String account(JSONObject session) {
        String id=session.optString("userId","");
        if(!id.matches("usr_[A-Za-z0-9-]{8,}"))throw new SecurityException("Missing VRChat account");
        return id;
    }
    private static File file(Context ctx,String id){return new File(ctx.getFilesDir(),"friend_log_"+id+".json");}
    private static JSONObject read(File file)throws Exception{
        if(!file.exists())return new JSONObject().put("events",new JSONArray()).put("snapshot",new JSONObject());
        try(FileInputStream in=new FileInputStream(file)){
            byte[] data=new byte[(int)Math.min(file.length(),4_000_000L)];
            int off=0,n;
            while(off<data.length&&(n=in.read(data,off,data.length-off))>0)off+=n;
            return new JSONObject(new String(data,0,off,StandardCharsets.UTF_8));
        }
    }
    private static void write(Context ctx,File dest,JSONObject data)throws Exception{
        File tmp=new File(dest.getPath()+".tmp");
        try(FileOutputStream out=new FileOutputStream(tmp)){
            out.write(data.toString().getBytes(StandardCharsets.UTF_8));
            out.getFD().sync();
        }
        if(!tmp.renameTo(dest))throw new java.io.IOException("Could not save friend history");
    }
    public static synchronized JSONArray events(Context ctx,JSONObject session)throws Exception{
        return read(file(ctx,account(session))).optJSONArray("events");
    }
    /**
     * Feed this method a complete online+offline friend snapshot. A partial page must
     * never be used: otherwise absent friends would be misreported as removed.
     */
    public static synchronized int observe(Context ctx,JSONObject session,JSONArray completeFriends)throws Exception{
        String id=account(session);
        File path=file(ctx,id);
        JSONObject data=read(path),prior=data.optJSONObject("snapshot");
        if(prior==null)prior=new JSONObject();
        long now=System.currentTimeMillis();
        if(now-data.optLong("lastPoll",0)<MIN_POLL_MS)return 0;
        JSONObject next=new JSONObject();
        for(int i=0;i<completeFriends.length();i++){
            JSONObject f=completeFriends.optJSONObject(i);
            if(f==null)continue;
            String uid=f.optString("id","");
            if(!uid.startsWith("usr_"))continue;
            JSONObject entry=new JSONObject();
            entry.put("name",f.optString("displayName",uid));
            entry.put("status",f.optString("status","unknown"));
            entry.put("location",f.optString("location","unknown"));
            entry.put("online",f.optBoolean("isOnline",false));
            next.put(uid,entry);
        }
        JSONArray history=data.optJSONArray("events");
        if(history==null)history=new JSONArray();
        int changes=0;
        boolean initialized=data.optBoolean("initialized",false);
        if(initialized){
            java.util.Iterator<String> ids=next.keys();
            while(ids.hasNext()){
                String uid=ids.next();
                JSONObject current=next.getJSONObject(uid),old=prior.optJSONObject(uid);
                if(old==null){history.put(event(now,uid,current,"friend_added",""));changes++;continue;}
                String type="";
                if(current.optBoolean("online")!=old.optBoolean("online"))type=current.optBoolean("online")?"online":"offline";
                else if(!current.optString("location").equals(old.optString("location")))type="location";
                else if(!current.optString("status").equals(old.optString("status")))type="status";
                if(!type.isEmpty()){history.put(event(now,uid,current,type,old.optString(type.equals("location")?"location":"status","")));changes++;}
            }
            java.util.Iterator<String> removed=prior.keys();
            while(removed.hasNext()){
                String uid=removed.next();
                if(!next.has(uid)){history.put(event(now,uid,prior.getJSONObject(uid),"friend_removed",""));changes++;}
            }
        }
        JSONArray trimmed=new JSONArray();
        for(int i=Math.max(0,history.length()-MAX_EVENTS);i<history.length();i++)trimmed.put(history.get(i));
        data.put("events",trimmed).put("snapshot",next).put("initialized",true).put("lastPoll",now);
        write(ctx,path,data);
        return changes;
    }
    private static JSONObject event(long time,String id,JSONObject f,String type,String previous)throws Exception{
        return new JSONObject().put("time",time).put("userId",id)
            .put("name",f.optString("name",id)).put("type",type)
            .put("location",f.optString("location","unknown"))
            .put("status",f.optString("status","unknown")).put("previous",previous);
    }
}
