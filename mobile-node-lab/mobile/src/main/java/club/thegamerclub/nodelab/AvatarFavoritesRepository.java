package club.thegamerclub.nodelab;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;

/**
 * Personal VRChat avatar favorite groups. Unlike ExtendedAvatarStore these are
 * remote favorite lists, including VRC+ avatar slots managed by VRChat itself.
 * Fetch using the existing encrypted personal VRChat session, never Shizuku or Relay.
 */
public final class AvatarFavoritesRepository {
    private AvatarFavoritesRepository(){}

    public static final class Snapshot {
        public final JSONArray groups;
        public final boolean partial, fallback;
        public final String warning;
        public final int uniqueAvatars;
        Snapshot(JSONArray groups,boolean partial,boolean fallback,
                 String warning,int uniqueAvatars){
            this.groups=groups;
            this.partial=partial;
            this.fallback=fallback;
            this.warning=warning;
            this.uniqueAvatars=uniqueAvatars;
        }
    }
    private static String safeName(JSONObject group){
        String name=group.optString("name","");
        if(name.matches("[A-Za-z0-9_-]{1,64}"))return name;
        return "";
    }
    private static String displayName(JSONObject group,String name){
        String label=group.optString("displayName","").trim();
        return label.isEmpty()?name:label;
    }
    private static JSONObject favoriteAvatar(JSONObject entry){
        if(entry==null)return null;
        JSONObject avatar=entry.optJSONObject("avatar");
        if(avatar==null)avatar=entry;
        String id=avatar.optString("id",entry.optString("favoriteId",""));
        if(!id.matches("avtr_[0-9a-fA-F-]{36}"))return null;
        JSONObject result;
        try{
            result=new JSONObject(avatar.toString());
            result.put("id",id);
            if(result.optString("name","").isEmpty())
                result.put("name","Avatar "+id.substring(5,13));
        }catch(Exception e){return null;}
        return result;
    }
    private static JSONArray avatarsFromGroup(JSONObject contents){
        JSONArray records=contents.optJSONArray("favorites");
        // Some VRChat API responses use an array of avatar objects directly.
        if(records==null)records=contents.optJSONArray("avatars");
        JSONArray out=new JSONArray();
        if(records==null)return out;
        HashSet<String> seen=new HashSet<>();
        for(int i=0;i<records.length()&&i<400;i++){
            JSONObject avatar=favoriteAvatar(records.optJSONObject(i));
            if(avatar==null)continue;
            if(seen.add(avatar.optString("id","")))out.put(avatar);
        }
        return out;
    }
    public static Snapshot fetch(Context ctx)throws Exception{
        JSONArray groupDefs=null;
        String discoveryError="";
        try{groupDefs=VRChatApi.avatarFavoriteGroups(ctx);}
        catch(SecurityException ex){throw ex;}
        catch(Exception ex){discoveryError=ex.getMessage()==null?
            "VRChat did not return your avatar groups":ex.getMessage();}
        if(groupDefs==null){
            // Compatibility fallback to the older flat list, clearly marked as
            // incomplete group information; never mislabel this as all lists.
            JSONArray pages=new JSONArray();
            HashSet<String> ids=new HashSet<>();
            int batches=0;
            for(int offset=0;offset<=6000&&batches<40;offset+=60,batches++){
                JSONArray fetched=VRChatApi.favoriteAvatars(ctx,offset);
                for(int i=0;i<fetched.length();i++){
                    JSONObject avatar=favoriteAvatar(fetched.optJSONObject(i));
                    if(avatar!=null&&ids.add(avatar.optString("id","")))pages.put(avatar);
                }
                if(fetched.length()<60)break;
            }
            if(pages.length()==0)throw new IllegalStateException(
                "Unable to load VRChat favorite lists. "+discoveryError);
            JSONObject row=new JSONObject().put("name","legacy")
                .put("displayName","Favorites (compatibility view)")
                .put("avatars",pages).put("numFavorites",pages.length())
                .put("error","");
            return new Snapshot(new JSONArray().put(row),true,true,
                "VRChat's favorite-group list was unavailable. Showing the older "+
                "combined favorites feed instead; group names cannot be shown.",
                ids.size());
        }

        LinkedHashMap<String,JSONObject> uniqueGroups=new LinkedHashMap<>();
        for(int i=0;i<groupDefs.length()&&i<60;i++){
            JSONObject def=groupDefs.optJSONObject(i);
            if(def==null)continue;
            String name=safeName(def);
            String type=def.optString("type","avatar");
            if(name.isEmpty()||!"avatar".equalsIgnoreCase(type)||
               uniqueGroups.containsKey(name))continue;
            uniqueGroups.put(name,def);
        }
        JSONArray result=new JSONArray();
        HashSet<String> uniqueAvatarIds=new HashSet<>();
        ArrayList<String> failed=new ArrayList<>();
        // VRChat currently has a small set of avatar groups (including VRC+
        // lists). Use server-provided names rather than assuming fixed slots.
        int fetched=0;
        for(JSONObject def:uniqueGroups.values()){
            if(fetched++>=30){failed.add("more groups");break;}
            String name=safeName(def);
            String label=displayName(def,name);
            JSONArray avatars=new JSONArray();
            String groupError="";
            try{
                JSONObject response=VRChatApi.avatarFavoriteGroupContents(ctx,name);
                if(!response.has("favorites")&&!response.has("avatars"))
                    throw new IllegalStateException("VRChat omitted group contents");
                avatars=avatarsFromGroup(response);
                for(int j=0;j<avatars.length();j++)
                    uniqueAvatarIds.add(avatars.optJSONObject(j).optString("id",""));
            }catch(SecurityException ex){throw ex;}
            catch(Exception ex){
                groupError="Could not load this list";
                failed.add(label);
            }
            JSONObject row=new JSONObject();
            row.put("name",name);
            row.put("displayName",label);
            row.put("numFavorites",def.optInt("numFavorites",avatars.length()));
            row.put("avatars",avatars);
            row.put("error",groupError);
            result.put(row);
        }
        boolean partial=!failed.isEmpty()||uniqueGroups.size()>30;
        return new Snapshot(result,partial,false,
            partial?"Some favorite lists did not load: "+
                android.text.TextUtils.join(", ",failed):"",
            uniqueAvatarIds.size());
    }
}
