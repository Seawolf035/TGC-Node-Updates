package club.thegamerclub.nodelab;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;

/**
 * A single inbox reader for the full Companion, floating tools and optional
 * notification checks. Network calls must run on a background executor.
 *
 * Never reads Shizuku logs, changes VRChat notifications, or stores inbox
 * messages outside of the current process.
 */
public final class InboxRepository {
    private InboxRepository(){}
    public static final class Snapshot {
        public final JSONArray items;
        public final boolean partial;
        public final String warning;
        public final long checkedAt;
        public final int legacyCount,modernCount;
        Snapshot(JSONArray items,boolean partial,String warning,long checkedAt,
                 int legacyCount,int modernCount){
            this.items=items;this.partial=partial;this.warning=warning;this.checkedAt=checkedAt;
            this.legacyCount=legacyCount;this.modernCount=modernCount;
        }
    }

    private static long time(JSONObject item){
        String value=item.optString("created_at",item.optString("createdAt",""));
        try{return Instant.parse(value).toEpochMilli();}
        catch(Exception ignored){
            try{return OffsetDateTime.parse(value).toInstant().toEpochMilli();}
            catch(Exception alsoIgnored){return 0L;}
        }
    }
    private static String key(JSONObject item){
        String id=item.optString("id","");
        if(!id.isEmpty())return "id:"+id;
        return "fallback:"+item.optString("type","")+":"+
            item.optString("senderUserId","")+":"+
            item.optString("created_at",item.optString("createdAt",""))+":"+
            item.optString("message",item.optString("description",""));
    }
    private static String fingerprint(JSONObject item){
        String created=item.optString("created_at",item.optString("createdAt",""));
        String sender=item.optString("senderUserId","");
        String type=item.optString("type","");
        String msg=item.optString("message",item.optString("description",""));
        // Never collapse two different invitations based on the sender alone.
        return (created.isEmpty()||sender.isEmpty()||type.isEmpty())?"":created+"\u001f"+
            sender+"\u001f"+type.toLowerCase(Locale.US)+"\u001f"+msg;
    }
    private static JSONObject normalize(JSONObject item,boolean modern){
        JSONObject result;
        try{
            result=new JSONObject(item.toString());
            result.put("_modern",modern);
            result.put("_inboxTime",time(item));
        }catch(Exception e){result=new JSONObject();}
        return result;
    }
    private static boolean prefer(JSONObject current,JSONObject incoming){
        String type=incoming.optString("type","");
        if(type.toLowerCase(Locale.US).contains("friend")&&
            type.toLowerCase(Locale.US).contains("request")){
            // Existing friend-request acceptance endpoint requires the legacy ID.
            if(!incoming.optBoolean("_modern",false)&&current.optBoolean("_modern",false))
                return true;
            if(incoming.optBoolean("_modern",false)&&!current.optBoolean("_modern",false))
                return false;
        }
        int a=current.optString("message",current.optString("description","")).length();
        int b=incoming.optString("message",incoming.optString("description","")).length();
        if(b!=a)return b>a;
        return !incoming.optBoolean("_modern",false);
    }
    public static Snapshot combine(JSONArray legacy,JSONArray modern,
                                   String warning)throws Exception{
        if(legacy==null&&modern==null)
            throw new IllegalStateException("VRChat inbox temporarily unavailable.");
        LinkedHashMap<String,JSONObject> merged=new LinkedHashMap<>();
        HashSet<String> fingerprints=new HashSet<>();
        for(int source=0;source<2;source++){
            JSONArray input=source==0?legacy:modern;
            if(input==null)continue;
            for(int i=0;i<Math.min(input.length(),120);i++){
                JSONObject raw=input.optJSONObject(i);if(raw==null)continue;
                JSONObject item=normalize(raw,source==1);
                String id=key(item);
                String fingerprint=fingerprint(item);
                if(merged.containsKey(id)){
                    if(prefer(merged.get(id),item))merged.put(id,item);
                    continue;
                }
                if(!fingerprint.isEmpty()&&fingerprints.contains(fingerprint))continue;
                merged.put(id,item);
                if(!fingerprint.isEmpty())fingerprints.add(fingerprint);
            }
        }
        ArrayList<JSONObject> sorted=new ArrayList<>(merged.values());
        sorted.sort((a,b)->Long.compare(b.optLong("_inboxTime"),a.optLong("_inboxTime")));
        JSONArray result=new JSONArray();
        for(int i=0;i<Math.min(120,sorted.size());i++)result.put(sorted.get(i));
        return new Snapshot(result,legacy==null||modern==null,
            warning==null?"":warning,System.currentTimeMillis(),
            legacy==null?0:legacy.length(),modern==null?0:modern.length());
    }
    /** Return a fresh dual-inbox snapshot. A one-endpoint failure is marked partial. */
    public static Snapshot refresh(Context ctx)throws Exception{
        // Do not fetch personal data using another or incomplete login.
        JSONObject session=VRChatSession.load(ctx);
        if(session.optString("auth","").isEmpty()||session.optBoolean("needs2fa",false)||
            !session.optString("userId","").matches("usr_[0-9a-fA-F-]{36}"))
            throw new SecurityException("Sign in to your personal VRChat account first.");
        JSONArray old=null,modern=null;
        StringBuilder warning=new StringBuilder();
        try{old=VRChatApi.notifications(ctx,false,0);}
        catch(Exception error){warning.append("Older inbox temporarily unavailable. ");}
        try{modern=VRChatApi.notifications(ctx,true,0);}
        catch(Exception error){warning.append("Some notifications temporarily unavailable. ");}
        return combine(old,modern,warning.toString().trim());
    }
    public static boolean include(JSONObject item,String filter){
        if(item==null)return false;
        if("unread".equals(filter))return !item.optBoolean("seen",false);
        String type=item.optString("type","").toLowerCase(Locale.US);
        if("invite".equals(filter))return type.contains("invite");
        if("friend".equals(filter))return type.contains("friend");
        return true;
    }
    public static int unread(JSONArray items){
        int n=0;
        for(int i=0;i<items.length();i++){
            JSONObject item=items.optJSONObject(i);
            if(item!=null&&!item.optBoolean("seen",false))n++;
        }
        return n;
    }
}
