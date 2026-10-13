package club.thegamerclub.nodelab;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Local account-scoped group shortcuts. Never caches role privileges, access
 * tokens, member records or moderation authority. Remote access is always
 * checked by VRChatApi on every request.
 */
public final class GroupToolkitStore {
    private static final String PREF="tgc_mini_vrcx_groups";
    private static final int MAX_PINNED=16;

    private GroupToolkitStore(){}

    private static String key(Context context){
        String user;
        try{user=VRChatSession.load(context).optString("userId","");}
        catch(Exception e){throw new SecurityException("Sign in to your personal VRChat account first.",e);}
        if(!user.matches("usr_[0-9a-fA-F-]{36}"))
            throw new SecurityException("Sign in to a personal VRChat account first.");
        return user+"_pinned_groups";
    }
    public static JSONArray pinned(Context context){
        String data=context.getSharedPreferences(PREF,Context.MODE_PRIVATE)
            .getString(key(context),"[]");
        try{return new JSONArray(data);}catch(Exception ignored){return new JSONArray();}
    }
    private static void store(Context context,JSONArray value){
        SharedPreferences prefs=context.getSharedPreferences(PREF,Context.MODE_PRIVATE);
        prefs.edit().putString(key(context),value.toString()).apply();
    }
    public static boolean contains(Context context,String groupId){
        JSONArray rows=pinned(context);
        for(int i=0;i<rows.length();i++){
            JSONObject row=rows.optJSONObject(i);
            if(row!=null&&groupId.equals(row.optString("id","")))return true;
        }
        return false;
    }
    public static void setPinned(Context context,String groupId,String name,boolean pin)throws Exception{
        if(groupId==null||!groupId.matches("grp_[0-9a-fA-F-]{36}"))
            throw new IllegalArgumentException("Invalid VRChat group ID.");
        JSONArray old=pinned(context),updated=new JSONArray();
        for(int i=0;i<old.length();i++){
            JSONObject row=old.optJSONObject(i);
            if(row!=null&&!groupId.equals(row.optString("id","")))updated.put(row);
        }
        if(pin){
            if(updated.length()>=MAX_PINNED)
                throw new IllegalArgumentException("Maximum 16 pinned groups.");
            String label=name==null?"Group":name.trim();
            if(label.length()>90)label=label.substring(0,90);
            if(label.isEmpty())label="Group";
            updated.put(new JSONObject().put("id",groupId).put("name",label));
        }
        store(context,updated);
    }
}
