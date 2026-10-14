package club.thegamerclub.nodelab;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Real VRChat group membership backed TGC Staff Directory.
 * The separate TGC staff group is GAMER.5669; Royalty is GAMER.3166.
 * No spreadsheet, private roster, rank or VRChat group IDs are fabricated.
 * Live group role membership takes precedence; unknowns are labeled Staff.
 * All network work must be called from a background executor.
 */
public final class StaffDirectoryRepository {
    private static final String STAFF_CODE="GAMER.5669";
    private static final String ROYALTY_CODE="GAMER.3166";
    private static volatile Snapshot cache;

    public static final class Entry {
        public final String id,name;
        public String rank="Staff";
        public boolean friend=false;
        Entry(String id,String name){this.id=id;this.name=name;}
        public boolean royalty(){return "The Royalty".equals(rank);}
    }
    public static final class Snapshot {
        public final String accountId;
        public final ArrayList<Entry> members;
        public final boolean rosterComplete,friendsComplete;
        public final long capturedAt;
        Snapshot(String accountId,ArrayList<Entry> members,boolean rosterComplete,
                 boolean friendsComplete){
            this.accountId=accountId;this.members=members;this.rosterComplete=rosterComplete;
            this.friendsComplete=friendsComplete;this.capturedAt=System.currentTimeMillis();
        }
        public int collected(){int c=0;for(Entry e:members)if(e.friend)c++;return c;}
    }
    private StaffDirectoryRepository(){}

    private static boolean validUser(String s){
        return s!=null&&s.matches("usr_[0-9a-fA-F-]{36}");
    }
    private static String normalizedCode(JSONObject group){
        String shortCode=group.optString("shortCode","");
        String disc=group.optString("discriminator","");
        if(shortCode.matches("(?i)[A-Z0-9]{3,6}\\.[0-9]{4}"))
            return shortCode.toUpperCase(Locale.US);
        return (shortCode+"."+disc).toUpperCase(Locale.US);
    }
    private static String findGroup(Context context,String code)throws Exception{
        // Search results can include unrelated communities. Require the exact
        // official discriminator rather than trusting a substring in a name.
        JSONArray groups=VRChatApi.searchGroups(context,code);
        for(int i=0;i<groups.length();i++){
            JSONObject item=groups.optJSONObject(i);
            if(item!=null && code.equalsIgnoreCase(normalizedCode(item))){
                String id=item.optString("id","");
                if(id.matches("grp_[0-9a-fA-F-]{36}"))return id;
            }
        }
        JSONArray joined=VRChatApi.groups(context);
        for(int i=0;i<joined.length();i++){
            JSONObject item=joined.optJSONObject(i);
            if(item!=null&&code.equalsIgnoreCase(normalizedCode(item))){
                String id=item.optString("id","");
                if(id.matches("grp_[0-9a-fA-F-]{36}"))return id;
            }
        }
        throw new IllegalStateException("Official TGC VRChat group "+code+" not available to this account");
    }
    private static JSONArray fullMembers(Context c,String groupId,int limit)throws Exception{
        JSONArray all=new JSONArray();
        for(int offset=0;offset<limit;offset+=60){
            JSONArray page=VRChatApi.groupMembers(c,groupId,offset);
            for(int i=0;i<page.length();i++)all.put(page.get(i));
            if(page.length()<60)break;
        }
        return all;
    }
    private static String memberId(JSONObject o){
        String id=o.optString("userId","");
        JSONObject user=o.optJSONObject("user");
        if(!validUser(id)&&user!=null)id=user.optString("id","");
        return id;
    }
    private static String memberName(JSONObject o){
        JSONObject user=o.optJSONObject("user");
        if(user!=null)return user.optString("displayName",o.optString("displayName","Staff member"));
        return o.optString("displayName","Staff member");
    }
    private static int tier(String rank){
        switch(rank){
            case "The Royalty":return 7;
            case "Owner":return 6;
            case "Head Admin":return 5;
            case "Admin":return 4;
            case "Head Moderator":return 3;
            case "Moderator":return 2;
            case "Trial Moderator":return 1;
            default:return 0;
        }
    }
    private static String standardRank(String name){
        if(name==null)return "Staff";
        String n=name.trim().toLowerCase(Locale.US);
        if(n.equals("the royalty")||n.equals("royalty"))return "The Royalty";
        if(n.equals("owner")||n.equals("co-owner"))return "Owner";
        if(n.equals("head admin")||n.equals("head administrator"))return "Head Admin";
        if(n.equals("admin")||n.equals("administrator"))return "Admin";
        if(n.equals("head moderator")||n.equals("head mod"))return "Head Moderator";
        if(n.equals("moderator")||n.equals("mod"))return "Moderator";
        if(n.equals("trial moderator")||n.equals("trial mod"))return "Trial Moderator";
        return "Staff";
    }
    private static Map<String,String> roleNames(JSONArray roles){
        Map<String,String> map=new HashMap<>();
        for(int i=0;i<roles.length();i++){
            JSONObject role=roles.optJSONObject(i);
            if(role!=null)map.put(role.optString("id",""),role.optString("name",""));
        }
        return map;
    }
    private static String rankFromRoles(JSONObject m,Map<String,String> roles){
        String result="Staff";
        JSONArray ids=m.optJSONArray("roleIds");
        if(ids!=null)for(int i=0;i<ids.length();i++){
            String candidate=standardRank(roles.get(ids.optString(i)));
            if(tier(candidate)>tier(result))result=candidate;
        }
        return result;
    }
    private static boolean loadFriends(Context c,Set<String> friends){
        boolean complete=true;
        for(boolean offline:new boolean[]{false,true}){
            boolean done=false;
            // Capped to avoid unlimited calls; never claim 100% from partial data.
            for(int offset=0;offset<800;offset+=80){
                JSONArray page;
                try{page=VRChatApi.friendsPage(c,offline,offset);}
                catch(Exception e){complete=false;break;}
                for(int i=0;i<page.length();i++){
                    JSONObject f=page.optJSONObject(i);
                    if(f!=null && validUser(f.optString("id","")))
                        friends.add(f.optString("id"));
                }
                if(page.length()<80){done=true;break;}
            }
            if(!done)complete=false;
        }
        return complete;
    }
    public static synchronized Snapshot cached(Context c){
        Snapshot s=cache;
        try{
            String id=VRChatSession.load(c).optString("userId","");
            if(s!=null&&id.equals(s.accountId)&&
               System.currentTimeMillis()-s.capturedAt<20*60*1000L)return s;
        }catch(Exception ignored){}
        return null;
    }
    public static synchronized void clear(){cache=null;}
    public static Snapshot load(Context c)throws Exception{
        if(!StaffGate.unlocked(c))throw new SecurityException("Staff verification required");
        String self=VRChatApi.currentUser(c).optString("id","");
        if(!validUser(self))throw new SecurityException("VRChat login required");
        String staffId=findGroup(c,STAFF_CODE);
        JSONArray roster=fullMembers(c,staffId,600);
        LinkedHashMap<String,Entry> result=new LinkedHashMap<>();
        for(int i=0;i<roster.length();i++){
            JSONObject row=roster.optJSONObject(i);
            if(row==null)continue;
            String id=memberId(row);
            if(validUser(id)&&!id.equals(self))
                result.put(id,new Entry(id,memberName(row)));
        }
        // The API excludes the calling user. Include them only when the server
        // confirms that the account is a member of the official Staff group.
        JSONObject staffGroup=VRChatApi.getGroup(c,staffId);
        if(staffGroup.optJSONObject("myMember")!=null)
            result.put(self,new Entry(self,VRChatSession.load(c).optString("displayName","My profile")));
        boolean rosterComplete=roster.length()<600;
        try{
            Map<String,String> names=roleNames(VRChatApi.groupRoles(c,staffId));
            for(int i=0;i<roster.length();i++){
                JSONObject row=roster.optJSONObject(i);
                if(row==null)continue;
                Entry e=result.get(memberId(row));
                if(e!=null)e.rank=rankFromRoles(row,names);
            }
        }catch(Exception ignored){ /* unknown roles are never invented */ }
        // Royalty is a separate official group. Do not guess from usernames or
        // treat any Owner role as The Royalty.
        try{
            String royaltyId=findGroup(c,ROYALTY_CODE);
            JSONArray royal=fullMembers(c,royaltyId,120);
            for(int i=0;i<royal.length();i++){
                JSONObject row=royal.optJSONObject(i);
                if(row!=null){
                    Entry member=result.get(memberId(row));
                    if(member!=null)member.rank="The Royalty";
                }
            }
            JSONObject rg=VRChatApi.getGroup(c,royaltyId);
            if(rg.optJSONObject("myMember")!=null){
                Entry mine=result.get(self);
                if(mine!=null)mine.rank="The Royalty";
            }
        }catch(Exception ignored){ /* do not assert unverified gold identity */ }
        Set<String> friends=new HashSet<>();
        boolean friendsComplete=loadFriends(c,friends);
        ArrayList<Entry> list=new ArrayList<>(result.values());
        for(Entry e:list)e.friend=friends.contains(e.id);
        Collections.sort(list,(a,b)->{
            int rank=Integer.compare(tier(b.rank),tier(a.rank));
            return rank!=0?rank:a.name.compareToIgnoreCase(b.name);
        });
        Snapshot snapshot=new Snapshot(self,list,rosterComplete,friendsComplete);
        if(StaffGate.unlocked(c))cache=snapshot;
        return snapshot;
    }
    /** Verified membership only; no rank inferred from names or profile bios. */
    public static String rank(Context c,String userId){
        Snapshot s=cached(c);
        if(s==null)return "";
        for(Entry e:s.members)if(e.id.equals(userId))return e.rank;
        return "";
    }
}
