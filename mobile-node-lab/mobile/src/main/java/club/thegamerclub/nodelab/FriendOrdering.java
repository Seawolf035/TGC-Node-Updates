package club.thegamerclub.nodelab;

import org.json.JSONObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

/** Consistent favorite, shared-instance, solo-instance, hidden, offline ordering. */
public final class FriendOrdering {
    public static final String FAVORITES="★ VRChat Favorites";
    public static final String UNKNOWN="Private / hidden location";
    public static final String OFFLINE="Offline";
    private FriendOrdering(){}
    public static String id(JSONObject item){
        return item.optString("id",item.optString("userId",""));
    }
    public static String name(JSONObject item){
        return item.optString("displayName",item.optString("name",id(item)));
    }
    public static boolean online(JSONObject item){
        return item.has("isOnline")?item.optBoolean("isOnline",false):
            item.optBoolean("online",false);
    }
    public static boolean known(String location){
        return location!=null&&location.startsWith("wrld_")&&location.contains(":");
    }
    public static String section(JSONObject person,HashSet<String> favoriteIds){
        if(favoriteIds!=null&&favoriteIds.contains(id(person)))return FAVORITES;
        if(!online(person))return OFFLINE;
        String place=person.optString("location","");
        return known(place)?place:UNKNOWN;
    }
    public static Map<String,Integer> counts(Iterable<JSONObject> friends,
        HashSet<String> favorites){
        HashMap<String,Integer> counts=new HashMap<>();
        for(JSONObject friend:friends){
            if(friend==null)continue;
            String section=section(friend,favorites);
            counts.put(section,counts.getOrDefault(section,0)+1);
        }
        return counts;
    }
    public static int bucket(String section,Map<String,Integer> counts){
        if(FAVORITES.equals(section))return 0;
        if(OFFLINE.equals(section))return 4;
        if(UNKNOWN.equals(section)||!known(section))return 3;
        return counts.getOrDefault(section,0)>=2?1:2;
    }
    public static int compareGroups(String a,String b,Map<String,Integer> counts){
        int aa=bucket(a,counts),bb=bucket(b,counts);
        if(aa!=bb)return Integer.compare(aa,bb);
        if(aa==1){
            int delta=Integer.compare(counts.getOrDefault(b,0),counts.getOrDefault(a,0));
            if(delta!=0)return delta;
        }
        return a.compareToIgnoreCase(b);
    }
    public static int compareFriends(JSONObject a,JSONObject b,
        HashSet<String> favorites,Map<String,Integer> counts){
        String first=section(a,favorites),second=section(b,favorites);
        int group=compareGroups(first,second,counts);
        return group!=0?group:name(a).compareToIgnoreCase(name(b));
    }
    public static String label(String section,int count){
        if(FAVORITES.equals(section)||UNKNOWN.equals(section)||OFFLINE.equals(section))
            return section;
        return count>=2?"Shared instance · "+count+" friends":"Single known location";
    }
}
