package club.thegamerclub.nodelab;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONException;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * TGC Staff Directory source of truth = official TGC Google Apps Script roster
 * JSON, the exact URL already used by TGC's Main Launcher. NOT VRChat groups.
 *
 * Roster fields: role, discordId, username, vrchatUrl, vrchatUserId,
 * avatarUrl, displayRole, isRoyalty. Royalty is cosmetic: operational
 * permissions remain Relay-verified and do not depend on this public feed.
 *
 * VRChat API is used ONLY for the viewer's authenticated friends list and for
 * per-user friend requests, never to decide who is staff or assign a rank.
 */
public final class StaffDirectoryRepository {
    // Same public read-only endpoint configured as STAFF_ROSTER_URL in GM1.
    private static final String ROSTER_URL=
        "https://script.google.com/macros/s/AKfycbx0MRdKsYmz1B82M3KYZ28_fcyTrdavMTsTxNkLm2-ijyXAaR-1hbpa2fhDB_zXZAmrCg/exec?format=json";
    private static final long CACHE_MS=15L*60*1000;
    private static volatile Snapshot cache;
    private static volatile String cacheDiscord="";
    private static final String DEX_CACHE_PREF="tgc_staffdex_saved_snapshot_v1";
    private static final String DEX_CACHE_KEY="last_complete";
    private static final int DEX_CACHE_SCHEMA=1;
    private static volatile Map<String,String> rankCache=Collections.emptyMap();
    private static volatile long rankCacheAt=0L;
    private StaffDirectoryRepository(){}

    public static final class Entry {
        public final String id,discordId,name,avatarUrl;
        public String rank;
        public boolean friend=false;
        Entry(String id,String discordId,String name,String avatarUrl,String rank){
            this.id=id;this.discordId=discordId;this.name=name;
            this.avatarUrl=avatarUrl;this.rank=rank;
        }
        public boolean royalty(){return "The Royalty".equals(rank);}
        public boolean canFriend(){return validUser(id);}
    }
    public static final class Snapshot {
        public final String accountId;
        public final ArrayList<Entry> members;
        public final boolean rosterComplete,friendsComplete;
        public final Set<String> friendIds;
        public final long capturedAt;
        Snapshot(String accountId,ArrayList<Entry> members,
                 boolean rosterComplete,boolean friendsComplete,Set<String> friendIds){
            this(accountId,members,rosterComplete,friendsComplete,friendIds,
                System.currentTimeMillis());
        }
        Snapshot(String accountId,ArrayList<Entry> members,
                 boolean rosterComplete,boolean friendsComplete,Set<String> friendIds,
                 long capturedAt){
            this.accountId=accountId;this.members=members;
            this.rosterComplete=rosterComplete;this.friendsComplete=friendsComplete;
            this.friendIds=Collections.unmodifiableSet(new HashSet<>(friendIds));
            this.capturedAt=capturedAt;
        }
        /** A user's own VRChat account satisfies an account collection item
         * without requiring an impossible self-friend request. */
        public boolean collectedId(String vrchatId){
            return vrchatId!=null&&
                (vrchatId.equals(accountId)||friendIds.contains(vrchatId));
        }
        public int collected(){
            int count=0;
            for(Entry e:members)if(!e.id.equals(accountId)&&e.canFriend()&&e.friend)count++;
            return count;
        }
    }
    private static boolean validUser(String s){
        return s!=null && s.matches("usr_[0-9a-fA-F-]{36}");
    }
    private static boolean validDiscord(String s){
        return s!=null && s.matches("[0-9]{16,21}");
    }
    private static String idFromProfile(String link){
        if(link==null)return "";
        java.util.regex.Matcher m=java.util.regex.Pattern
            .compile("usr_[0-9a-fA-F-]{36}").matcher(link);
        return m.find()?m.group():"";
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
    private static String standardRank(String raw){
        if(raw==null)return "Staff";
        switch(raw.trim().toLowerCase(Locale.US)){
            case "the royalty":case "royalty":return "The Royalty";
            case "owner":case "co-owner":return "Owner";
            case "head admin":case "head administrator":return "Head Admin";
            case "admin":case "administrator":return "Admin";
            case "head moderator":case "head mod":return "Head Moderator";
            case "moderator":case "mod":return "Moderator";
            case "trial moderator":case "trial mod":return "Trial Moderator";
            default:return "Staff";
        }
    }
    private static JSONArray readRoster() throws Exception {
        // No staff secrets, cookies, tokens, device IDs or VRChat session data
        // are sent to the public roster. This is the existing GM1 feed.
        HttpURLConnection con=(HttpURLConnection)new URL(ROSTER_URL).openConnection();
        con.setConnectTimeout(12000);con.setReadTimeout(12000);
        con.setRequestProperty("Accept","application/json");
        con.setRequestProperty("User-Agent","TGC-Staff-Companion/Roster");
        con.setInstanceFollowRedirects(true);
        try {
            int code=con.getResponseCode();
            if(code!=200)throw new IllegalStateException("Staff roster returned HTTP "+code);
            try(InputStream stream=con.getInputStream();
                ByteArrayOutputStream out=new ByteArrayOutputStream()){
                byte[] buffer=new byte[8192];
                int size,total=0;
                while((size=stream.read(buffer))>=0){
                    total+=size;
                    if(total>1000000)throw new IllegalStateException("Staff roster is unexpectedly large");
                    out.write(buffer,0,size);
                }
                JSONObject json=new JSONObject(new String(out.toByteArray(),StandardCharsets.UTF_8));
                if(json.has("ok")&&!json.optBoolean("ok",false))
                    throw new IllegalStateException("TGC roster reported an error");
                JSONArray members=json.optJSONArray("members");
                if(members==null)members=json.optJSONArray("staff");
                if(members==null)
                    throw new IllegalStateException("TGC roster JSON is missing members/staff");
                return members;
            }
        }finally {con.disconnect();}
    }
    private static ArrayList<Entry> parseRoster(JSONArray rows){
        // The roster's Discord ID is the stable, primary staff identity.
        LinkedHashMap<String,Entry> entries=new LinkedHashMap<>();
        for(int i=0;i<rows.length();i++){
            JSONObject row=rows.optJSONObject(i);
            if(row==null)continue;
            String discord=row.optString("discordId","").trim();
            if(!validDiscord(discord))continue;
            String vrchat=row.optString("vrchatUserId","");
            if(!validUser(vrchat))vrchat=idFromProfile(row.optString("vrchatUrl",""));
            String role=standardRank(row.optString("role",""));
            // Cosmetic Royalty does not change the staff's underlying role.
            boolean royalty=row.optBoolean("isRoyalty",false);
            String display=row.optString("displayRole","");
            if(royalty || "The Royalty".equalsIgnoreCase(display))role="The Royalty";
            else if("Staff".equals(role))role=standardRank(display);
            if(tier(role)==0)continue; // No staff eligibility for unknown roles.
            String name=row.optString("username","").trim();
            if(name.isEmpty())name="Staff "+discord;
            String avatar=row.optString("avatarUrl","");
            entries.put(discord,new Entry(vrchat,discord,name,avatar,role));
        }
        ArrayList<Entry> results=new ArrayList<>(entries.values());
        Collections.sort(results,(a,b)->{
            int role=Integer.compare(tier(b.rank),tier(a.rank));
            return role!=0?role:a.name.compareToIgnoreCase(b.name);
        });
        return results;
    }
    private static boolean loadFriends(Context c,Set<String> friends){
        boolean complete=true;
        for(boolean offline:new boolean[]{false,true}){
            boolean done=false;
            for(int offset=0;offset<4000;offset+=80){
                JSONArray page;
                try{page=VRChatApi.friendsPage(c,offline,offset);}
                catch(Exception e){complete=false;break;}
                for(int i=0;i<page.length();i++){
                    JSONObject friend=page.optJSONObject(i);
                    if(friend!=null&&validUser(friend.optString("id","")))
                        friends.add(friend.optString("id"));
                }
                if(page.length()<80){done=true;break;}
            }
            if(!done)complete=false;
        }
        return complete;
    }
    private static synchronized void refreshRankCache(ArrayList<Entry> members){
        Map<String,String> names=new HashMap<>();
        for(Entry e:members)if(validUser(e.id))names.put(e.id,e.rank);
        rankCache=Collections.unmodifiableMap(names);
        rankCacheAt=System.currentTimeMillis();
    }
    /**
     * StaffDex uses stale-while-revalidate: a last successfully completed scan
     * remains readable even after 15 minutes or process restart. Its timestamp
     * is always shown in UI; it is NOT evidence of live membership.
     * Cache is scoped to BOTH signed-in VRChat and verified Discord identity.
     */
    private static String[] viewerScope(Context c){
        if(!StaffGate.unlocked(c))return null;
        try{
            MobileCredentials.Record creds=MobileCredentials.load(c);
            JSONObject session=VRChatSession.load(c);
            String user=session.optString("userId","");
            if(creds==null||!validDiscord(creds.discordId)||
               !validUser(user)||session.optString("auth","").isEmpty()||
               session.optBoolean("needs2fa",true))return null;
            StaffGate.assertPersonalVrchatAccount(c,user);
            return new String[]{creds.discordId,user};
        }catch(Exception ignored){return null;}
    }
    private static JSONArray compactFriends(Snapshot snapshot){
        Set<String> eligible=new HashSet<>();
        for(Entry e:snapshot.members)if(validUser(e.id))eligible.add(e.id);
        eligible.addAll(SpecialStaffDexAchievements.gamerMusic());
        eligible.addAll(SpecialStaffDexAchievements.royalIds(snapshot));
        eligible.addAll(SpecialStaffDexAchievements.ownerIds(snapshot));
        for(String id:new String[]{
            SpecialStaffDexAchievements.LILY,SpecialStaffDexAchievements.SEAWOLF,
            SpecialStaffDexAchievements.DARKFLAMES,
            SpecialStaffDexAchievements.DEVINITY,
            SpecialStaffDexAchievements.SOUP,
            SpecialStaffDexAchievements.KLASHY})eligible.add(id);
        JSONArray ids=new JSONArray();
        for(String id:snapshot.friendIds)if(eligible.contains(id))ids.put(id);
        return ids;
    }
    /** Called on the background refresh thread only; never saves partial scans. */
    private static void storeComplete(Context c,Snapshot s,String expectedDiscord){
        if(!s.rosterComplete||!s.friendsComplete)return;
        String[] scope=viewerScope(c);
        if(scope==null||!scope[0].equals(expectedDiscord)||
           !scope[1].equals(s.accountId))return;
        try{
            JSONObject saved=new JSONObject();
            saved.put("schema",DEX_CACHE_SCHEMA);
            saved.put("discord",scope[0]);saved.put("account",scope[1]);
            saved.put("captured_at",s.capturedAt);
            JSONArray members=new JSONArray();
            for(Entry e:s.members){
                JSONObject m=new JSONObject();
                m.put("id",e.id);m.put("discord",e.discordId);
                m.put("name",e.name);m.put("avatar",e.avatarUrl);
                m.put("rank",e.rank);
                members.put(m);
            }
            saved.put("members",members);
            saved.put("friend_ids",compactFriends(s));
            c.getSharedPreferences(DEX_CACHE_PREF,Context.MODE_PRIVATE).edit()
                .putString(DEX_CACHE_KEY,saved.toString()).commit();
        }catch(Exception ignored){
            // Disk cache is an optimization; a write failure never breaks live data.
        }
    }
    public static synchronized Snapshot cached(Context c){
        String[] scope=viewerScope(c);
        if(scope==null)return null;
        Snapshot s=cache;
        if(s!=null&&scope[1].equals(s.accountId)&&
           scope[0].equals(cacheDiscord))return s;
        try{
            SharedPreferences preferences=c.getSharedPreferences(
                DEX_CACHE_PREF,Context.MODE_PRIVATE);
            String json=preferences.getString(DEX_CACHE_KEY,"");
            if(json.isEmpty()||json.length()>300000)return null;
            JSONObject saved=new JSONObject(json);
            long at=saved.optLong("captured_at",0L);
            long now=System.currentTimeMillis();
            if(saved.optInt("schema",0)!=DEX_CACHE_SCHEMA ||
               !scope[0].equals(saved.optString("discord","")) ||
               !scope[1].equals(saved.optString("account","")) ||
               at<=0 || at>now+60000L)return null;
            JSONArray rows=saved.optJSONArray("members");
            JSONArray friends=saved.optJSONArray("friend_ids");
            if(rows==null||friends==null||rows.length()<1||rows.length()>500||
               friends.length()>1000)return null;
            ArrayList<Entry> members=new ArrayList<>();
            Set<String> ids=new HashSet<>();
            Set<String> seenDiscord=new HashSet<>();
            for(int i=0;i<rows.length();i++){
                JSONObject m=rows.optJSONObject(i);
                if(m==null)return null;
                String id=m.optString("id","");
                String discord=m.optString("discord","");
                String rank=standardRank(m.optString("rank",""));
                if(!validDiscord(discord)||!seenDiscord.add(discord)||
                   tier(rank)==0)return null;
                // Missing VRChat IDs are valid for real roster entries.
                if(!id.isEmpty()&&!validUser(id))return null;
                members.add(new Entry(id,discord,m.optString("name","Staff"),
                    m.optString("avatar",""),rank));
            }
            for(int i=0;i<friends.length();i++){
                String id=friends.optString(i,"");
                if(!validUser(id))return null;
                ids.add(id);
            }
            for(Entry e:members)e.friend=validUser(e.id)&&ids.contains(e.id);
            Snapshot loaded=new Snapshot(scope[1],members,true,true,ids,at);
            cache=loaded;cacheDiscord=scope[0];
            return loaded;
        }catch(Exception ignored){return null;}
    }
    public static synchronized void clear(){
        cache=null;cacheDiscord="";
        rankCache=Collections.emptyMap();rankCacheAt=0L;
    }
    public static Snapshot load(Context c)throws Exception{
        if(!StaffGate.unlocked(c))throw new SecurityException("Staff verification required");
        String self=VRChatApi.currentUser(c).optString("id","");
        if(!validUser(self))throw new SecurityException("Sign into VRChat first");
        ArrayList<Entry> results=parseRoster(readRoster());
        if(results.isEmpty())throw new IllegalStateException("The official TGC staff roster is empty");
        refreshRankCache(results);
        Set<String> friends=new HashSet<>();
        boolean friendCoverage=loadFriends(c,friends);
        for(Entry e:results)e.friend=validUser(e.id)&&friends.contains(e.id);
        Snapshot snapshot=new Snapshot(self,results,true,friendCoverage,friends);
        String[] scope=viewerScope(c);
        if(scope!=null&&self.equals(scope[1])&&friendCoverage){
            storeComplete(c,snapshot,scope[0]);
            synchronized(StaffDirectoryRepository.class){
                cache=snapshot;cacheDiscord=scope[0];
            }
        }
        return snapshot;
    }
    /** Read-only roster lookup, never VRChat group membership or displayed bio. */
    public static String rankLive(Context c,String userId){
        if(!StaffGate.unlocked(c)||!validUser(userId))return "";
        if(System.currentTimeMillis()-rankCacheAt<CACHE_MS)
            return rankCache.getOrDefault(userId,"");
        try{
            ArrayList<Entry> results=parseRoster(readRoster());
            if(!results.isEmpty())refreshRankCache(results);
            return rankCache.getOrDefault(userId,"");
        }catch(Exception ignored){return "";}
    }
    public static String rank(Context c,String userId){
        if(!StaffGate.unlocked(c)||!validUser(userId))return "";
        if(System.currentTimeMillis()-rankCacheAt<CACHE_MS)
            return rankCache.getOrDefault(userId,"");
        return "";
    }
    public static int rankColor(String rank){
        if("The Royalty".equals(rank))return ThemePreferences.royalGold();
        if("Owner".equals(rank))return 0xFFF05D62;
        if("Head Admin".equals(rank))return 0xFFF6A04B;
        if("Admin".equals(rank))return 0xFFF2D15C;
        if("Head Moderator".equals(rank))return 0xFF69CC80;
        if("Moderator".equals(rank))return 0xFF76AFFF;
        if("Trial Moderator".equals(rank))return 0xFFBA8BFF;
        return CompanionStyle.MUTED;
    }
}
