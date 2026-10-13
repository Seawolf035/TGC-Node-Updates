package club.thegamerclub.nodelab;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;

/** Per-account local shortcuts; never modifies VRChat friendships. */
public final class FriendPinStore {
    private static final String PREF="tgc_pinned_friends";
    private static final int MAX_PINS=12;
    private FriendPinStore(){}

    private static String key(Context ctx){
        String account;
        try{account=VRChatSession.load(ctx).optString("userId","");}
        catch(Exception e){throw new SecurityException("Sign in to VRChat first.",e);}
        if(!account.matches("usr_[0-9a-fA-F-]{36}"))
            throw new SecurityException("Sign in to VRChat first.");
        return account+"_pins";
    }
    public static JSONArray pins(Context ctx){
        String data=ctx.getSharedPreferences(PREF,Context.MODE_PRIVATE)
            .getString(key(ctx),"[]");
        JSONArray next=new JSONArray();
        try{
            JSONArray saved=new JSONArray(data);
            for(int i=0;i<saved.length()&&next.length()<MAX_PINS;i++){
                JSONObject row=saved.optJSONObject(i);
                if(row==null)continue;
                String uid=row.optString("id","");
                if(!uid.matches("usr_[0-9a-fA-F-]{36}"))continue;
                boolean found=false;
                for(int j=0;j<next.length();j++)
                    if(uid.equals(next.optJSONObject(j).optString("id","")))
                        found=true;
                if(found)continue;
                String name=row.optString("name","Friend");
                if(name.length()>64)name=name.substring(0,64);
                next.put(new JSONObject().put("id",uid)
                    .put("name",name.isEmpty()?"Friend":name));
            }
        }catch(Exception ignored){}
        return next;
    }
    public static boolean contains(Context ctx,String uid){
        JSONArray list=pins(ctx);
        for(int i=0;i<list.length();i++)
            if(uid!=null&&uid.equals(list.optJSONObject(i).optString("id","")))
                return true;
        return false;
    }
    public static void setPinned(Context ctx,String uid,String name,boolean on)
        throws Exception{
        if(!StaffGate.unlocked(ctx))
            throw new SecurityException("Verified staff required.");
        if(uid==null||!uid.matches("usr_[0-9a-fA-F-]{36}"))
            throw new IllegalArgumentException("Invalid VRChat user ID.");
        JSONArray old=pins(ctx),next=new JSONArray();
        for(int i=0;i<old.length();i++){
            JSONObject row=old.optJSONObject(i);
            if(row!=null&&!uid.equals(row.optString("id","")))next.put(row);
        }
        if(on){
            if(next.length()>=MAX_PINS)
                throw new IllegalArgumentException("Maximum 12 pinned friends.");
            String display=name==null?"Friend":name.trim();
            if(display.isEmpty())display="Friend";
            if(display.length()>64)display=display.substring(0,64);
            JSONArray ordered=new JSONArray();
            ordered.put(new JSONObject().put("id",uid).put("name",display));
            for(int i=0;i<next.length();i++)ordered.put(next.getJSONObject(i));
            next=ordered;
        }
        SharedPreferences p=ctx.getSharedPreferences(PREF,Context.MODE_PRIVATE);
        p.edit().putString(key(ctx),next.toString()).apply();
    }
}
