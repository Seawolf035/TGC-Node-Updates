package club.thegamerclub.nodelab;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Local-only personal VRChat world bookmarks, independent of VRChat favorite
 * quotas. Stores no permissions, tokens, instance locations or friend data.
 * Account scope is verified on every operation.
 */
public final class WorldBookmarkStore {
    private static final String PREF="tgc_mini_vrcx_world_bookmarks";
    private static final int MAX_BOOKMARKS=24;

    private WorldBookmarkStore(){}

    private static String key(Context context){
        String id;
        try{id=VRChatSession.load(context).optString("userId","");}
        catch(Exception e){
            throw new SecurityException("Sign in to your personal VRChat account first.",e);
        }
        if(!id.matches("usr_[0-9a-fA-F-]{36}"))
            throw new SecurityException("Sign in to your personal VRChat account first.");
        return id+"_world_bookmarks_v1";
    }
    public static JSONArray bookmarks(Context context){
        String saved=context.getSharedPreferences(PREF,Context.MODE_PRIVATE)
            .getString(key(context),"[]");
        JSONArray result=new JSONArray();
        try{
            JSONArray rows=new JSONArray(saved);
            for(int i=0;i<rows.length()&&result.length()<MAX_BOOKMARKS;i++){
                JSONObject entry=rows.optJSONObject(i);
                if(entry==null)continue;
                String id=entry.optString("id","");
                if(!id.matches("wrld_[0-9a-fA-F-]{36}"))continue;
                String name=entry.optString("name","World").trim();
                if(name.isEmpty())name="World";
                if(name.length()>90)name=name.substring(0,90);
                boolean duplicate=false;
                for(int j=0;j<result.length();j++){
                    if(id.equals(result.optJSONObject(j).optString("id",""))){
                        duplicate=true;break;
                    }
                }
                if(!duplicate)result.put(new JSONObject().put("id",id).put("name",name));
            }
        }catch(Exception ignored){}
        return result;
    }
    public static boolean contains(Context context,String id){
        if(id==null)return false;
        JSONArray saved=bookmarks(context);
        for(int i=0;i<saved.length();i++){
            JSONObject item=saved.optJSONObject(i);
            if(item!=null&&id.equals(item.optString("id","")))return true;
        }
        return false;
    }
    public static void setBookmarked(Context context,String id,String name,boolean add)
        throws Exception {
        if(!StaffGate.unlocked(context))
            throw new SecurityException("Verified TGC staff access required.");
        if(id==null||!id.matches("wrld_[0-9a-fA-F-]{36}"))
            throw new IllegalArgumentException("Invalid VRChat world ID.");
        JSONArray saved=bookmarks(context),next=new JSONArray();
        for(int i=0;i<saved.length();i++){
            JSONObject item=saved.optJSONObject(i);
            if(item!=null&&!id.equals(item.optString("id","")))next.put(item);
        }
        if(add){
            if(next.length()>=MAX_BOOKMARKS)
                throw new IllegalArgumentException("Maximum 24 bookmarked worlds.");
            String label=name==null?"World":name.trim();
            if(label.isEmpty())label="World";
            if(label.length()>90)label=label.substring(0,90);
            // Recently bookmarked worlds appear first.
            JSONArray ordered=new JSONArray();
            ordered.put(new JSONObject().put("id",id).put("name",label));
            for(int i=0;i<next.length();i++)ordered.put(next.getJSONObject(i));
            next=ordered;
        }
        SharedPreferences prefs=context.getSharedPreferences(PREF,Context.MODE_PRIVATE);
        prefs.edit().putString(key(context),next.toString()).apply();
    }
}
