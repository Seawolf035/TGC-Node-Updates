package club.thegamerclub.nodelab;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.HashSet;

/**
 * Read-only index of actual VRChat favorite friend groups, not Companion pins.
 * Refreshed sparingly; cached membership is for display sorting only.
 */
public final class VRChatFavoriteIndex {
    private static final String CACHE="vrchat_favorite_friend_index";
    private static final long TTL=15L*60_000L;
    private VRChatFavoriteIndex(){}

    public static HashSet<String> savedIds(Context ctx){
        HashSet<String> ids=new HashSet<>();
        CompanionCache.Record record=CompanionCache.read(ctx,CACHE);
        if(record==null)return ids;
        JSONArray a=record.body.optJSONArray("ids");
        if(a==null)return ids;
        for(int i=0;i<a.length();i++){
            String value=a.optString(i,"");
            if(value.matches("usr_[0-9a-fA-F-]{36}"))ids.add(value);
        }
        return ids;
    }
    public static boolean stale(Context ctx){
        CompanionCache.Record r=CompanionCache.read(ctx,CACHE);
        return r==null||!r.fresh(TTL);
    }
    /** Must run on the background executor, never UI thread. */
    public static void refreshIfStale(Context ctx)throws Exception{
        if(!stale(ctx))return;
        JSONArray groups=VRChatApi.favoriteFriendGroups(ctx);
        JSONArray ids=new JSONArray();
        HashSet<String> seen=new HashSet<>();
        // All group requests must complete before replacement: never mistake
        // transient VRChat failures for empty favorites.
        for(int i=0;i<groups.length()&&i<20;i++){
            JSONObject group=groups.optJSONObject(i);
            if(group==null)continue;
            String name=group.optString("name","");
            if(!name.matches("[A-Za-z0-9_-]{1,64}"))continue;
            JSONObject contents=VRChatApi.favoriteFriendGroupContents(ctx,name);
            JSONArray entries=contents.optJSONArray("favorites");
            if(entries==null)throw new IllegalStateException("Incomplete VRChat favorite group");
            for(int n=0;n<entries.length();n++){
                JSONObject item=entries.optJSONObject(n);
                if(item==null)continue;
                String uid=item.optString("favoriteId",item.optString("userId",""));
                JSONObject user=item.optJSONObject("user");
                if(user==null)user=item.optJSONObject("friend");
                if(user!=null&&user.optString("id","").startsWith("usr_"))
                    uid=user.optString("id","");
                if(uid.matches("usr_[0-9a-fA-F-]{36}")&&seen.add(uid))
                    ids.put(uid);
            }
        }
        CompanionCache.write(ctx,CACHE,new JSONObject().put("ids",ids));
    }
}
