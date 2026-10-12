package club.thegamerclub.nodelab;

import android.content.Context;
import android.util.Base64;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

/** Personal-account VRChat API client: sessions NEVER go through the TGC relay. */
public final class VRChatApi {
    private static final String API="https://api.vrchat.cloud/api/1";
    private static final String AGENT="TGC-Staff-Companion/1.0.0 (Android VRChat companion)";
    private VRChatApi(){}

    private static final class Response {
        final int status; final String text; final JSONObject cookies;
        Response(int status,String text,JSONObject cookies){this.status=status;this.text=text;this.cookies=cookies;}
        JSONObject object()throws Exception{return new JSONObject(text);}
        JSONArray array()throws Exception{return new JSONArray(text);}
    }
    private static Response request(String method,String path,String cookie,String authorization,JSONObject payload)throws Exception{
        if(!path.startsWith("/")||path.contains("..")||path.contains("://"))
            throw new SecurityException("Invalid VRChat API path");
        HttpURLConnection conn=(HttpURLConnection)new URL(API+path).openConnection();
        conn.setConnectTimeout(12000);conn.setReadTimeout(17000);
        conn.setInstanceFollowRedirects(false);
        conn.setRequestMethod(method);
        conn.setRequestProperty("User-Agent",AGENT);
        conn.setRequestProperty("Accept","application/json");
        if(cookie!=null&&!cookie.isEmpty())conn.setRequestProperty("Cookie",cookie);
        if(authorization!=null&&!authorization.isEmpty())conn.setRequestProperty("Authorization",authorization);
        if(payload!=null){
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type","application/json");
            byte[] body=payload.toString().getBytes(StandardCharsets.UTF_8);
            try(OutputStream out=conn.getOutputStream()){out.write(body);}
        }
        try{
            int status=conn.getResponseCode();
            JSONObject cookies=new JSONObject();
            for(String hdr:conn.getHeaderFields().keySet()){
                if(hdr==null||!"Set-Cookie".equalsIgnoreCase(hdr))continue;
                List<String> values=conn.getHeaderFields().get(hdr);
                if(values==null)continue;
                for(String value:values){
                    String first=value.split(";",2)[0];
                    int equal=first.indexOf('=');
                    if(equal<=0)continue;
                    String key=first.substring(0,equal).trim();
                    if(key.equalsIgnoreCase("auth"))cookies.put("auth",first.substring(equal+1));
                    if(key.equalsIgnoreCase("twoFactorAuth"))cookies.put("twoFactorAuth",first.substring(equal+1));
                }
            }
            InputStream input=status>=200&&status<400?conn.getInputStream():conn.getErrorStream();
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();
            if(input!=null){
                try(InputStream stream=input){
                    byte[] buf=new byte[4096];int n;
                    while((n=stream.read(buf))!=-1){
                        if(bytes.size()+n>1024*1024)throw new SecurityException("VRChat response too large");
                        bytes.write(buf,0,n);
                    }
                }
            }
            String text=new String(bytes.toByteArray(),StandardCharsets.UTF_8);
            if(status==429)throw new IllegalStateException("VRChat rate limit reached. Wait before retrying.");
            if(status==401 && (authorization==null||authorization.isEmpty()))
                throw new SecurityException("VRChat session expired. Sign in again.");
            if(status<200||status>=300)
                throw new IllegalStateException("VRChat returned HTTP "+status+" (no changes made).");
            return new Response(status,text,cookies);
        }finally{conn.disconnect();}
    }
    private static String cookie(JSONObject session){
        StringBuilder s=new StringBuilder();
        for(String key:new String[]{"auth","twoFactorAuth"}){
            String value=session.optString(key,"");
            if(!value.isEmpty()){
                if(s.length()>0)s.append("; ");
                s.append(key).append("=").append(value);
            }
        }
        return s.toString();
    }
    private static void mergeCookies(JSONObject session,JSONObject from)throws Exception{
        for(String key:new String[]{"auth","twoFactorAuth"}){
            String value=from.optString(key,"");
            if(!value.isEmpty())session.put(key,value);
        }
    }
    private static void updateProfile(JSONObject session,JSONObject user)throws Exception{
        if(!user.optString("id","").matches("usr_[0-9a-fA-F-]{36}"))
            throw new IllegalStateException("VRChat session did not return a valid user");
        session.put("userId",user.getString("id"));
        session.put("displayName",user.optString("displayName","VRChat user"));
        session.put("needs2fa",false);
    }
    public static JSONObject login(Context context,String username,String password)throws Exception{
        if(username.trim().isEmpty()||password.isEmpty())throw new IllegalArgumentException("Username and password required");
        String basic=Base64.encodeToString((username+":"+password).getBytes(StandardCharsets.UTF_8),Base64.NO_WRAP);
        // Username/password only used for this one HTTPS call; never persisted.
        Response response=request("GET","/auth/user",null,"Basic "+basic,null);
        JSONObject session=new JSONObject();
        mergeCookies(session,response.cookies);
        JSONObject result=response.object();
        JSONArray requires=result.optJSONArray("requiresTwoFactorAuth");
        if(requires!=null && requires.length()>0){
            if(session.optString("auth","").isEmpty())throw new SecurityException("VRChat did not return a session cookie for 2FA");
            session.put("needs2fa",true);
            VRChatSession.save(context,session);
            JSONObject next=new JSONObject();
            next.put("requires2fa",true).put("methods",requires);
            return next;
        }
        updateProfile(session,result);
        if(session.optString("auth","").isEmpty())throw new SecurityException("VRChat returned no auth cookie");
        VRChatSession.save(context,session);
        return new JSONObject().put("requires2fa",false).put("displayName",session.getString("displayName"));
    }
    public static JSONObject verify2fa(Context context,String code,String mode)throws Exception{
        if(!mode.matches("totp|emailotp|otp"))throw new IllegalArgumentException("Unknown verification method");
        if(!code.matches("[A-Za-z0-9-]{4,32}"))throw new IllegalArgumentException("Invalid 2FA code");
        JSONObject session=VRChatSession.load(context);
        if(session.optString("auth","").isEmpty() || !session.optBoolean("needs2fa"))
            throw new SecurityException("Start VRChat login first");
        JSONObject input=new JSONObject().put("code",code.trim());
        Response verified=request("POST","/auth/twofactorauth/"+mode+"/verify",cookie(session),null,input);
        JSONObject result=verified.object();
        if(!result.optBoolean("verified",false))throw new SecurityException("VRChat did not verify the code");
        mergeCookies(session,verified.cookies);
        Response me=request("GET","/auth/user",cookie(session),null,null);
        mergeCookies(session,me.cookies);
        updateProfile(session,me.object());
        VRChatSession.save(context,session);
        return new JSONObject().put("displayName",session.getString("displayName"));
    }
    public static JSONObject currentUser(Context context)throws Exception{
        JSONObject session=validSession(context);
        Response response=request("GET","/auth/user",cookie(session),null,null);
        mergeCookies(session,response.cookies);
        JSONObject result=response.object();
        updateProfile(session,result);
        VRChatSession.save(context,session);
        return result;
    }
    private static JSONObject validSession(Context context)throws Exception{
        JSONObject session=VRChatSession.load(context);
        if(session.optString("auth","").isEmpty()||session.optBoolean("needs2fa"))
            throw new SecurityException("Sign in to your personal VRChat account first");
        return session;
    }
    public static JSONArray friends(Context context,boolean offline)throws Exception{
        JSONObject session=validSession(context);
        return request("GET","/auth/user/friends?n=80&offset=0&offline="+offline,
          cookie(session),null,null).array();
    }
    public static JSONArray worldFavorites(Context context)throws Exception{
        JSONObject session=validSession(context);
        return request("GET","/worlds/favorites?n=60&offset=0",cookie(session),null,null).array();
    }
    public static JSONArray groups(Context context)throws Exception{
        JSONObject session=validSession(context);
        String user=session.optString("userId","");
        if(!user.matches("usr_[0-9a-fA-F-]{36}"))throw new SecurityException("Personal VRChat session needs verification");
        return request("GET","/users/"+user+"/groups",cookie(session),null,null).array();
    }
    public static JSONArray recentWorlds(Context context)throws Exception{
        JSONObject session=validSession(context);
        return request("GET","/worlds/recent?n=40&offset=0",cookie(session),null,null).array();
    }

    /** Online entitlement check. VRCX and VRChat identify active VRC+ by system_supporter. */
    public static String requireActiveVRCPlus(Context context)throws Exception{
        JSONObject profile=currentUser(context);
        JSONArray tags=profile.optJSONArray("tags");
        boolean supporter=false;
        if(tags!=null){
            for(int i=0;i<tags.length();i++){
                if("system_supporter".equals(tags.optString(i,""))){supporter=true;break;}
            }
        }
        if(!supporter)throw new SecurityException(
            "An active VRC+ subscription is required for Extended Avatar Favorites.");
        String id=profile.optString("id","");
        if(!id.matches("usr_[0-9a-fA-F-]{36}"))
            throw new SecurityException("VRChat did not return the signed-in account.");
        return id;
    }
    /** Apply only the signed-in user's status and status message; other profile fields are untouched. */
    public static JSONObject updatePresence(Context context,String status,String description)throws Exception{
        if(!status.matches("active|ask me|busy|join me|offline"))
            throw new IllegalArgumentException("Invalid VRChat status");
        if(description==null||description.length()>32)
            throw new IllegalArgumentException("VRChat status message must be 32 characters or less");
        JSONObject session=validSession(context);
        String userId=session.optString("userId","");
        requireId("user",userId);
        JSONObject body=new JSONObject().put("status",status).put("statusDescription",description);
        return request("PUT","/users/"+userId,cookie(session),null,body).object();
    }

    /** A single bounded VRChat page; caller controls when to request the next page. */
    public static final int LIST_PAGE_SIZE=60;
    public static JSONArray browsePage(Context context,String mode,String query,int offset)throws Exception{
        if(offset<0||offset>10000||offset%LIST_PAGE_SIZE!=0)
            throw new IllegalArgumentException("Invalid page offset");
        String location;
        switch(mode){
            case "friends_online":
                location="/auth/user/friends?n=60&offline=false&offset="+offset;break;
            case "friends_offline":
                location="/auth/user/friends?n=60&offline=true&offset="+offset;break;
            case "worlds":
                location="/worlds/favorites?n=60&offset="+offset;break;
            case "recent":
                location="/worlds/recent?n=60&offset="+offset;break;
            case "search_users":
            case "search_worlds":
                String term=query==null?"":query.trim();
                if(term.length()<2||term.length()>80)
                    throw new IllegalArgumentException("Search must contain 2–80 characters");
                location=(mode.equals("search_users")?"/users?search=":"/worlds?search=")+
                    java.net.URLEncoder.encode(term,"UTF-8")+"&n=60&offset="+offset;break;
            default:throw new IllegalArgumentException("This list is not paginated");
        }
        return request("GET",location,cookie(validSession(context)),null,null).array();
    }
    /**
     * Complete normalized friend snapshot across online and offline paginated feeds.
     * Never return a partial list: the observation store treats missing IDs as removals.
     * Monitoring is opt-in, foreground or overlay-driven; avoid high-frequency API use.
     */
    public static JSONArray friendSnapshot(Context context)throws Exception{
        JSONObject session=validSession(context);
        JSONArray combined=new JSONArray();
        java.util.HashSet<String> seen=new java.util.HashSet<>();
        for(boolean offline:new boolean[]{false,true}){
            boolean complete=false;
            for(int offset=0;offset<=9960;offset+=60){
                JSONArray page=request("GET","/auth/user/friends?n=60&offline="+offline+
                    "&offset="+offset,cookie(session),null,null).array();
                if(page.length()>60)throw new IllegalStateException("Unexpected VRChat friend page size");
                for(int i=0;i<page.length();i++){
                    JSONObject friend=page.optJSONObject(i);
                    if(friend==null)continue;
                    String uid=friend.optString("id","");
                    if(!uid.startsWith("usr_")||!seen.add(uid))continue;
                    JSONObject normalized=new JSONObject(friend.toString());
                    normalized.put("isOnline",!offline);
                    combined.put(normalized);
                }
                if(page.length()<60){complete=true;break;}
            }
            if(!complete)throw new IllegalStateException("Friend list exceeds safe pagination limit");
        }
        return combined;
    }

    /** VRChat's private notes, saved to the authenticated VRChat account (not the TGC relay). */
    public static JSONArray userNotes(Context context,int offset)throws Exception{
        if(offset<0||offset>10000||offset%100!=0)
            throw new IllegalArgumentException("Invalid notes offset");
        return request("GET","/userNotes?n=100&offset="+offset,
            cookie(validSession(context)),null,null).array();
    }
    public static JSONObject saveUserNote(Context context,String targetId,String note)throws Exception{
        requireId("user",targetId);
        if(note==null||note.trim().isEmpty()||note.length()>2000)
            throw new IllegalArgumentException("Please enter a note of 1–2000 characters");
        JSONObject body=new JSONObject();
        body.put("targetUserId",targetId);
        body.put("note",note.trim());
        return request("POST","/userNotes",cookie(validSession(context)),null,body).object();
    }

    private static void requireId(String type,String id){
        String prefix=type.equals("user")?"usr_":type.equals("world")?"wrld_":"grp_";
        if(id==null||!id.matches(prefix+"[0-9a-fA-F-]{36}"))
            throw new IllegalArgumentException("Invalid "+type+" ID");
    }
    /** Detailed records are always loaded directly from the signed-in staff member's session. */
    public static JSONObject getUser(Context context,String id)throws Exception{
        requireId("user",id);
        return request("GET","/users/"+id,cookie(validSession(context)),null,null).object();
    }
    /**
     * VRChat's modern profile endpoint contains the displayed banner, profile
     * image and bio. /users/{id} can return a limited user and must NOT be the
     * only source for the Companion profile page.
     *
     * The authenticated user's own /auth/user is also consulted for safe
     * presence fields. The account response is never logged or persisted.
     */
    public static JSONObject getUserProfile(Context context,String id)throws Exception{
        requireId("user",id);
        JSONObject session=validSession(context);
        boolean own=id.equals(session.optString("userId",""));
        String cookies=cookie(session);
        JSONObject profile=null,base=null;
        Exception profileError=null;
        try{
            String path="/profile/"+id+"?withGroupsAndWorlds=true"+(own?"&asSelf=true":"");
            profile=request("GET",path,cookies,null,null).object();
        }catch(Exception ex){profileError=ex;}
        try{
            if(own){
                // CurrentUser has fields that the ordinary get-user response
                // omits, notably the owner's own full bio and status.
                base=request("GET","/auth/user",cookies,null,null).object();
            }else{
                base=request("GET","/users/"+id,cookies,null,null).object();
            }
        }catch(Exception ex){
            if(profile==null)throw profileError!=null?profileError:ex;
        }
        if(profile==null){
            if(base==null)throw profileError!=null?profileError:
                new IllegalStateException("Profile information unavailable");
            return base;
        }
        // The profile response is authoritative for social presentation. Only
        // supplement fields it lacks, without overwriting a real profile
        // banner, icon or nonempty bio with avatar fallback fields.
        if(base!=null){
            for(String key:new String[]{
                "status","statusDescription","location","date_joined",
                "last_login","last_platform","isFriend","friendRequestStatus",
                "username","dateJoined","lastActivity","userIcon",
                "profilePicOverride","profilePicOverrideThumbnail",
                "currentAvatarImageUrl","currentAvatarThumbnailImageUrl",
                "pronouns","bio","bioLinks","displayName"
            }){
                if(!profile.has(key)||profile.isNull(key)||
                   (profile.optString(key,"").isEmpty() &&
                    !key.equals("isFriend") && !key.equals("friendRequestStatus"))){
                    if(base.has(key)&&!base.isNull(key))profile.put(key,base.get(key));
                }
            }
            // Never let a stale user's 'offline' override the signed-in
            // account's explicitly present status.
        }
        return profile;
    }
    /** Groups publicly visible on another user's profile, subject to VRChat privacy. */
    public static JSONArray visibleUserGroups(Context context,String userId)throws Exception{
        requireId("user",userId);
        return request("GET","/users/"+userId+"/groups",
            cookie(validSession(context)),null,null).array();
    }
    public static JSONObject getWorld(Context context,String id)throws Exception{
        requireId("world",id);
        return request("GET","/worlds/"+id,cookie(validSession(context)),null,null).object();
    }
    public static JSONObject getGroup(Context context,String id)throws Exception{
        requireId("group",id);
        return request("GET","/groups/"+id,cookie(validSession(context)),null,null).object();
    }
    /** Read-only pages of visible group members; user's own membership is under myMember. */
    public static JSONArray groupMembers(Context context,String groupId,int offset)throws Exception{
        requireId("group",groupId);
        if(offset<0||offset>100000||offset%60!=0)
            throw new IllegalArgumentException("Invalid group member page");
        return request("GET","/groups/"+groupId+"/members?n=60&offset="+offset,
            cookie(validSession(context)),null,null).array();
    }
    /** Read-only server-side group member search for large groups. */
    public static JSONArray searchGroupMembers(Context context,String groupId,String name)throws Exception{
        requireId("group",groupId);
        String term=name==null?"":name.trim();
        if(term.length()<3||term.length()>80)
            throw new IllegalArgumentException("Group member search needs 3–80 characters");
        return request("GET","/groups/"+groupId+"/members/search?query="+
            java.net.URLEncoder.encode(term,"UTF-8")+"&n=60&offset=0",
            cookie(validSession(context)),null,null).array();
    }
    /** Returns instances visible to this personal VRChat account. */
    public static JSONArray groupInstances(Context context,String groupId)throws Exception{
        requireId("group",groupId);
        return request("GET","/groups/"+groupId+"/instances",
            cookie(validSession(context)),null,null).array();
    }
    /** Group ban list is read-only and subject to the signed-in account's permissions. */
    public static JSONArray groupBans(Context context,String groupId,int offset)throws Exception{
        requireId("group",groupId);
        if(offset<0||offset>100000||offset%60!=0)
            throw new IllegalArgumentException("Invalid group bans page");
        return request("GET","/groups/"+groupId+"/bans?n=60&offset="+offset,
            cookie(validSession(context)),null,null).array();
    }
    public static JSONArray searchUsers(Context context,String query)throws Exception{
        String value=query.trim();
        if(value.length()<2||value.length()>80)throw new IllegalArgumentException("Search for 2–80 characters");
        return request("GET","/users?search="+java.net.URLEncoder.encode(value,"UTF-8")+"&n=40&offset=0",
            cookie(validSession(context)),null,null).array();
    }
    public static JSONArray searchWorlds(Context context,String query)throws Exception{
        String value=query.trim();
        if(value.length()<2||value.length()>80)throw new IllegalArgumentException("Search for 2–80 characters");
        return request("GET","/worlds?search="+java.net.URLEncoder.encode(value,"UTF-8")+"&n=40&offset=0",
            cookie(validSession(context)),null,null).array();
    }
    /** Read-only VRChat invite inbox. Never acknowledges, accepts or hides notifications. */
    public static JSONArray inviteNotifications(Context context)throws Exception{
        JSONObject session=validSession(context);
        return request("GET","/auth/user/notifications?n=100&offset=0",
            cookie(session),null,null).array();
    }
    /** Some newer VRChat invite metadata exists on the version-two inbox. */
    public static JSONArray inviteNotificationsV2(Context context)throws Exception{
        JSONObject session=validSession(context);
        return request("GET","/notifications?n=100&offset=0",
            cookie(session),null,null).array();
    }
    /** Read the custom displayName from a specific invited instance, if accessible. */
    public static JSONObject invitedInstance(Context context,String worldId,String instanceId)throws Exception{
        requireId("world",worldId);
        if(instanceId==null||instanceId.length()<1||instanceId.length()>512 ||
            !instanceId.matches("[A-Za-z0-9_~().,-]{1,512}")){
            throw new IllegalArgumentException("Invalid instance location");
        }
        // Match VRCX: GET /instances/{worldId}:{instanceId}
        // instanceId was strictly validated above to prevent URL injection.
        // URLEncoder is for query parameters, not URI path segments; it can
        // change the private/group instance-location syntax.
        return request("GET","/instances/"+worldId+":"+instanceId,
            cookie(validSession(context)),null,null).object();
    }

    /** VRCX-compatible account notifications with explicitly chosen pagination. */
    public static JSONArray notifications(Context context,boolean modern,int offset)throws Exception{
        if(offset<0 || offset>1000)throw new IllegalArgumentException("Invalid offset");
        return request("GET",(modern?"/notifications":"/auth/user/notifications")+
            "?n=60&offset="+offset,cookie(validSession(context)),null,null).array();
    }
    private static void notificationId(String id){
        if(id==null || !id.matches("not_[0-9a-zA-Z_-]{4,90}"))
            throw new IllegalArgumentException("Invalid notification ID");
    }
    public static void markNotificationSeen(Context context,String id,boolean modern)throws Exception{
        notificationId(id);
        request(modern?"POST":"PUT",
            modern?"/notifications/"+id+"/see":"/auth/user/notifications/"+id+"/see",
            cookie(validSession(context)),null,null);
    }
    public static void dismissNotification(Context context,String id,boolean modern)throws Exception{
        notificationId(id);
        request(modern?"DELETE":"PUT",
            modern?"/notifications/"+id:"/auth/user/notifications/"+id+"/hide",
            cookie(validSession(context)),null,null);
    }
    public static void acceptFriendRequest(Context context,String notificationId)throws Exception{
        notificationId(notificationId);
        request("PUT","/auth/user/notifications/"+notificationId+"/accept",
            cookie(validSession(context)),null,null);
    }
    public static JSONObject getFriendStatus(Context context,String userId)throws Exception{
        requireId("user",userId);
        return request("GET","/user/"+userId+"/friendStatus",
            cookie(validSession(context)),null,null).object();
    }
    public static void sendFriendRequest(Context context,String userId)throws Exception{
        requireId("user",userId);
        request("POST","/user/"+userId+"/friendRequest",cookie(validSession(context)),null,null);
    }
    public static void requestInvite(Context context,String userId)throws Exception{
        requireId("user",userId);
        request("POST","/requestInvite/"+userId,cookie(validSession(context)),null,new JSONObject());
    }
    public static JSONArray favoriteAvatars(Context context,int offset)throws Exception{
        if(offset<0||offset>1000)throw new IllegalArgumentException("Invalid offset");
        return request("GET","/avatars/favorites?n=60&offset="+offset,
            cookie(validSession(context)),null,null).array();
    }
    public static JSONObject avatar(Context context,String avatarId)throws Exception{
        if(avatarId==null||!avatarId.matches("avtr_[0-9a-fA-F-]{36}"))
            throw new IllegalArgumentException("Invalid avatar");
        return request("GET","/avatars/"+avatarId,cookie(validSession(context)),null,null).object();
    }
    public static void selectAvatar(Context context,String avatarId)throws Exception{
        if(avatarId==null||!avatarId.matches("avtr_[0-9a-fA-F-]{36}"))
            throw new IllegalArgumentException("Invalid avatar");
        request("PUT","/avatars/"+avatarId+"/select",cookie(validSession(context)),null,null);
    }
    public static void sendInvite(Context context,String userId,String location)throws Exception{
        requireId("user",userId);
        String safe=normalizeLocation(location);
        request("POST","/invite/"+userId,cookie(validSession(context)),null,
            new JSONObject().put("instanceId",safe));
    }
    public static String normalizeLocation(String location){
        if(location==null || !location.matches(
            "wrld_[0-9a-fA-F-]{36}:[0-9]{1,12}(?:~[A-Za-z0-9_-]+(?:\\([A-Za-z0-9_-]+\\))?)*"))
            throw new IllegalArgumentException("Invalid VRChat instance");
        if(location.length()>480)throw new IllegalArgumentException("Location is too long");
        return location;
    }
    public static JSONObject getInstance(Context context,String location)throws Exception{
        String safe=normalizeLocation(location);
        return request("GET","/instances/"+safe,cookie(validSession(context)),null,null).object();
    }
    public static void selfInvite(Context context,String location)throws Exception{
        String safe=normalizeLocation(location);
        request("POST","/invite/myself/to/"+safe,cookie(validSession(context)),
            null,new JSONObject());
    }
    public static void logout(Context context){VRChatSession.clear(context);}
}
