package club.thegamerclub.nodelab;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads *only* received VRChat invitations and their associated instance names.
 * No invite sends, code generation, backend verification or Discord writes.
 */
public final class VRChatInviteCode {
    // Exactly matches the one-time code produced by Gamer Music 3's launcher.
    private static final Pattern CODE=Pattern.compile(
        "(?i)\\bTGC-[A-HJ-NP-Z2-9]{4}-[A-HJ-NP-Z2-9]{4}\\b");
    // Gamer Music 2 / Cult uses its own distinct verification-code namespace.
    private static final Pattern CULT_CODE=Pattern.compile(
        "(?i)\\bCULT-[A-Z0-9]{4}-[A-Z0-9]{4}\\b");
    private static final long CULT_RECENT_WINDOW=60L*60_000L;
    private static final Pattern LOCATION=Pattern.compile(
        "(?i)(wrld_[0-9a-f-]{36}):([^\\s\"'<>]+)");
    private static final String WORLD="wrld_a7a3e90e-ba93-4bb3-88fa-25747dcea477";
    private static final long TTL=10L*60*1000;
    private VRChatInviteCode(){}

    public static final class Result{
        public final String code;
        public final String message;
        public final int matchingInvites;
        public final boolean inboxRead;
        public final long createdAt;
        /** "gm3", "cult", or "none"; set by the recognized code, never by a UI tab. */
        public final String provider;
        Result(String code,String message,int matchingInvites,boolean inboxRead,long createdAt){
            this(code,message,matchingInvites,inboxRead,createdAt,
                code!=null&&code.toUpperCase(Locale.US).startsWith("CULT-")?"cult":
                code!=null&&code.toUpperCase(Locale.US).startsWith("TGC-")?"gm3":"none");
        }
        Result(String code,String message,int matchingInvites,boolean inboxRead,long createdAt,String provider){
            this.code=code;this.message=message;this.matchingInvites=matchingInvites;
            this.inboxRead=inboxRead;this.createdAt=createdAt;this.provider=provider;
        }
    }
    private static final class Invite{
        JSONObject raw,details;
        long created;
        boolean matched;
        String code="",location="";
    }
    private static long date(JSONObject obj){
        String value=obj.optString("created_at",obj.optString("createdAt",""));
        try{return Instant.parse(value).toEpochMilli();}
        catch(Exception ignored){
            try{return java.time.OffsetDateTime.parse(value).toInstant().toEpochMilli();}
            catch(Exception ignored2){return 0L;}
        }
    }
    private static JSONObject details(JSONObject notification){
        Object raw=notification.opt("details");
        if(raw instanceof JSONObject)return (JSONObject)raw;
        if(raw instanceof String){
            try{return new JSONObject((String)raw);}catch(Exception ignored){}
        }
        JSONObject data=notification.optJSONObject("data");
        if(data!=null){
            Object nested=data.opt("details");
            if(nested instanceof JSONObject)return (JSONObject)nested;
            if(nested instanceof String){
                try{return new JSONObject((String)nested);}catch(Exception ignored){}
            }
        }
        return new JSONObject();
    }
    private static boolean containsWorld(JSONObject notification,JSONObject details){
        return containsWorldText(notification.optString("message","")) ||
            containsWorldText(notification.optString("link","")) ||
            recursiveWorld(details,0) ||
            recursiveWorld(notification.optJSONObject("data"),0);
    }
    private static boolean containsWorldText(String s){
        return s!=null&&s.toLowerCase(Locale.US).contains(WORLD.toLowerCase(Locale.US));
    }
    private static boolean recursiveWorld(JSONObject o,int depth){
        if(o==null||depth>3)return false;
        java.util.Iterator<String> keys=o.keys();
        while(keys.hasNext()){
            Object v=o.opt(keys.next());
            if(v instanceof String && containsWorldText((String)v))return true;
            if(v instanceof JSONObject && recursiveWorld((JSONObject)v,depth+1))return true;
        }
        return false;
    }
    private static String code(String candidate){
        if(candidate==null)return "";
        Matcher m=CODE.matcher(candidate);
        return m.find()?m.group().toUpperCase(Locale.US):"";
    }
    private static String matchCode(JSONObject o,int depth){
        if(o==null||depth>3)return "";
        for(String key:new String[]{"displayName","instanceName","name","message","inviteMessage","title","worldName"}){
            String found=code(o.optString(key,""));
            if(!found.isEmpty())return found;
        }
        java.util.Iterator<String> keys=o.keys();
        while(keys.hasNext()){
            Object value=o.opt(keys.next());
            if(value instanceof JSONObject){
                String found=matchCode((JSONObject)value,depth+1);
                if(!found.isEmpty())return found;
            }
        }
        return "";
    }
    private static String instanceLocation(String s){
        if(s==null)return "";
        Matcher matcher=LOCATION.matcher(s);
        if(matcher.find() && matcher.group(1).equalsIgnoreCase(WORLD))
            return matcher.group(2).replaceAll("[,.;]*$","");
        // Some notification payloads include instanceId without a world prefix.
        if(s.length()>4 && s.length()<512 &&
           s.matches("[0-9]{1,9}(?:~[A-Za-z0-9_-]+\\([^)]*\\))*"))
            return s;
        return "";
    }
    private static String findLocation(JSONObject o,int depth){
        if(o==null||depth>4)return "";
        // VRCX reads the *complete location* from ref.details.worldId.
        // It is typically wrld_<uuid>:<instance>~private(...)~nonce(...),
        // not a bare world UUID plus a separate instanceId.
        for(String field:new String[]{"worldId","location","instanceId","instanceLocation",
                "targetLocation","worldInstance","joinLocation","link"}){
            String result=instanceLocation(o.optString(field,""));
            if(!result.isEmpty())return result;
        }
        java.util.Iterator<String> keys=o.keys();
        while(keys.hasNext()){
            Object value=o.opt(keys.next());
            if(value instanceof JSONObject){
                String found=findLocation((JSONObject)value,depth+1);
                if(!found.isEmpty())return found;
            }else if(value instanceof String){
                String str=(String)value;
                if(str.startsWith("{")){
                    try{
                        String found=findLocation(new JSONObject(str),depth+1);
                        if(!found.isEmpty())return found;
                    }catch(Exception ignored){}
                }else if(str.toLowerCase(Locale.US).contains(WORLD.toLowerCase(Locale.US)+":")){
                    String found=instanceLocation(str);
                    if(!found.isEmpty())return found;
                }
            }
        }
        return "";
    }

    private static String cultCode(String text){
        if(text==null)return "";
        Matcher matcher=CULT_CODE.matcher(text);
        return matcher.find()?matcher.group().toUpperCase(Locale.US):"";
    }
    private static String findCultCode(JSONObject data,int depth){
        if(data==null||depth>4)return "";
        for(String key:new String[]{
                "displayName","instanceName","name","message",
                "inviteMessage","title","worldName"}){
            String value=cultCode(data.optString(key,""));
            if(!value.isEmpty())return value;
        }
        java.util.Iterator<String> iterator=data.keys();
        while(iterator.hasNext()){
            Object value=data.opt(iterator.next());
            if(value instanceof JSONObject){
                String found=findCultCode((JSONObject)value,depth+1);
                if(!found.isEmpty())return found;
            }else if(value instanceof String && ((String)value).startsWith("{")){
                try{
                    String found=findCultCode(new JSONObject((String)value),depth+1);
                    if(!found.isEmpty())return found;
                }catch(Exception ignored){}
            }
        }
        return "";
    }
    private static String cultInstanceLocation(String text){
        if(text==null)return "";
        Matcher matcher=LOCATION.matcher(text);
        if(matcher.find()){
            String instance=matcher.group(2).replaceAll("[,.;]*$","");
            // An opaque private instance ID; never synthesize one.
            if(instance.length()<=512 && instance.matches("[A-Za-z0-9_~().,-]+"))
                return matcher.group(1)+":"+instance;
        }
        return "";
    }
    private static String findCultLocation(JSONObject data,int depth){
        if(data==null||depth>4)return "";
        for(String key:new String[]{
                "worldId","location","instanceLocation","targetLocation",
                "worldInstance","joinLocation","link"}){
            String found=cultInstanceLocation(data.optString(key,""));
            if(!found.isEmpty())return found;
        }
        java.util.Iterator<String> iterator=data.keys();
        while(iterator.hasNext()){
            Object value=data.opt(iterator.next());
            if(value instanceof JSONObject){
                String found=findCultLocation((JSONObject)value,depth+1);
                if(!found.isEmpty())return found;
            }else if(value instanceof String){
                String raw=(String)value;
                if(raw.startsWith("{")){
                    try{
                        String found=findCultLocation(new JSONObject(raw),depth+1);
                        if(!found.isEmpty())return found;
                    }catch(Exception ignored){}
                }else{
                    String found=cultInstanceLocation(raw);
                    if(!found.isEmpty())return found;
                }
            }
        }
        return "";
    }
    private static void acceptCult(JSONArray input,ArrayList<Invite> matches){
        if(input==null)return;
        long now=System.currentTimeMillis();
        for(int i=0;i<input.length();i++){
            JSONObject notification=input.optJSONObject(i);
            if(notification==null)continue;
            String type=notification.optString("type","").toLowerCase(Locale.US);
            // An arbitrary message in the inbox must never become a verification code.
            if(!type.equals("invite") && !type.endsWith(".invite") &&
                !type.equals("invitev2"))continue;
            long at=date(notification);
            if(at>0 && (at>now+60_000L || now-at>CULT_RECENT_WINDOW))continue;
            JSONObject detail=details(notification);
            Invite invite=new Invite();
            invite.raw=notification;
            invite.details=detail;
            invite.created=at;
            invite.code=findCultCode(detail,0);
            if(invite.code.isEmpty())invite.code=findCultCode(notification,0);
            invite.location=findCultLocation(detail,0);
            if(invite.location.isEmpty())invite.location=findCultLocation(notification,0);
            // Ignore regular invitations that have neither a Cult code nor an
            // instance location. An ordinary invite is not a verification request.
            if(!invite.code.isEmpty()||!invite.location.isEmpty())
                matches.add(invite);
        }
    }
    /**
     * Cult verification is separate from Gamer Music 3. The code lives in a
     * VRChat private-invite instance name; a Cult-formatted code embedded in
     * the invite itself is also accepted. We do not know or infer a Cult world
     * UUID, expiry duration, or any backend verification authority.
     *
     * Reads only: this never starts or completes Discord verification.
     */
    public static Result scanCult(Context context)throws Exception{
        ArrayList<Invite> matches=new ArrayList<>();
        boolean read=false;
        Exception lastError=null;
        try{
            acceptCult(VRChatApi.inviteNotifications(context),matches);
            read=true;
        }catch(Exception ex){lastError=ex;}
        try{
            acceptCult(VRChatApi.inviteNotificationsV2(context),matches);
            read=true;
        }catch(Exception ex){if(lastError==null)lastError=ex;}
        if(!read){
            if(lastError!=null)throw lastError;
            throw new IllegalStateException("VRChat invite inbox unavailable");
        }
        Collections.sort(matches,(a,b)->Long.compare(b.created,a.created));
        java.util.HashSet<String> checkedLocations=new java.util.HashSet<>();
        int instanceRequests=0;
        for(Invite invite:matches){
            if(!invite.code.isEmpty()){
                return new Result(invite.code,
                    "Cult verification code found in your VRChat invite",
                    matches.size(),true,invite.created);
            }
            String location=invite.location;
            int colon=location.indexOf(':');
            if(colon<=0 || !checkedLocations.add(location))continue;
            // Avoid scanning every personal invite / hitting the VRChat rate limit.
            if(++instanceRequests>12)break;
            try{
                JSONObject instance=VRChatApi.invitedInstance(context,
                    location.substring(0,colon),location.substring(colon+1));
                // The *custom instance name* is authoritative, not a world title.
                String found=cultCode(instance.optString("displayName",""));
                if(!found.isEmpty())
                    return new Result(found,
                        "Cult verification code found in your invited instance",
                        matches.size(),true,invite.created);
            }catch(Exception ignored){
                // VRChat may restrict access to a private instance.
            }
        }
        if(matches.isEmpty())
            return new Result("",
                "No recent Cult verification invite found. Request an invite through Gamer Music 2's existing verification flow, then return here.",
                0,true,0L);
        return new Result("",
            "Invites found, but a Cult verification code was not available in their accessible details. You can also read the instance name in VRChat.",
            matches.size(),true,0L);
    }
    private static final class UnifiedInvite {
        final String key;
        final long created;
        final String code,location,tgcInstance;
        UnifiedInvite(String key,long created,String code,String location,String tgcInstance){
            this.key=key;this.created=created;this.code=code;
            this.location=location;this.tgcInstance=tgcInstance;
        }
    }
    private static void collectUnified(JSONArray notifications,
                                       ArrayList<UnifiedInvite> candidates){
        if(notifications==null)return;
        long now=System.currentTimeMillis();
        for(int i=0;i<notifications.length();i++){
            JSONObject notification=notifications.optJSONObject(i);
            if(notification==null)continue;
            String type=notification.optString("type","").toLowerCase(Locale.US);
            if(!type.equals("invite")&&!type.endsWith(".invite")&&
                !type.equals("invitev2"))continue;
            long created=date(notification);
            if(created>now+60_000L)continue;
            JSONObject detail=details(notification);
            boolean gm3World=containsWorld(notification,detail);
            String gm3Direct=gm3World?matchCode(detail,0):"";
            if(gm3World&&gm3Direct.isEmpty())gm3Direct=matchCode(notification,0);
            String cultDirect=findCultCode(detail,0);
            if(cultDirect.isEmpty())cultDirect=findCultCode(notification,0);
            boolean gm3Fresh=gm3World&&(created==0||now-created<TTL);
            boolean cultFresh=created==0||now-created<=CULT_RECENT_WINDOW;
            String tgcInstance=gm3Fresh?findLocation(notification,0):"";
            String cultLocation=cultFresh?findCultLocation(detail,0):"";
            if(cultLocation.isEmpty()&&cultFresh)
                cultLocation=findCultLocation(notification,0);
            String code="";
            if(gm3Fresh&&!gm3Direct.isEmpty())code=gm3Direct;
            else if(cultFresh&&!cultDirect.isEmpty())code=cultDirect;
            // A TGC world is authoritative for TGC codes; otherwise an invite
            // with a known location can expose a Cult custom instance name.
            if(code.isEmpty()&&!gm3Fresh&&!cultFresh)continue;
            if(code.isEmpty()&&tgcInstance.isEmpty()&&cultLocation.isEmpty())continue;
            String id=notification.optString("id","");
            String key=!id.isEmpty()?id:
                (created+":"+tgcInstance+":"+cultLocation+":"+code);
            candidates.add(new UnifiedInvite(key,created,code,cultLocation,tgcInstance));
        }
    }
    /**
     * Auto-recognizes whichever of Gamer Music 3 (TGC) or Gamer Music 2 (CULT)
     * verification codes arrived most recently.
     *
     * Both verification flows begin and end in Discord. This only reads the
     * personal VRChat inbox / the invited instance's displayName; never
     * creates codes, sends Discord messages, or uploads VRChat credentials.
     */
    public static Result scanLatest(Context context)throws Exception{
        ArrayList<UnifiedInvite> candidates=new ArrayList<>();
        boolean read=false;
        Exception error=null;
        try{
            collectUnified(VRChatApi.inviteNotifications(context),candidates);
            read=true;
        }catch(Exception e){error=e;}
        try{
            collectUnified(VRChatApi.inviteNotificationsV2(context),candidates);
            read=true;
        }catch(Exception e){if(error==null)error=e;}
        if(!read){
            if(error!=null)throw error;
            throw new IllegalStateException("Unable to read VRChat invitations");
        }
        // Resolve newest first, deduplicating v1/v2 versions of the same invite.
        Collections.sort(candidates,(a,b)->{
            int age=Long.compare(b.created,a.created);
            if(age!=0)return age;
            // The v1 and v2 versions of the same invite may have different
            // available fields. Prefer a ready code over an unresolved lookup.
            return Boolean.compare(!b.code.isEmpty(),!a.code.isEmpty());
        });
        java.util.HashSet<String> seen=new java.util.HashSet<>();
        java.util.HashSet<String> lookedUp=new java.util.HashSet<>();
        int requests=0;
        for(UnifiedInvite invite:candidates){
            if(!seen.add(invite.key))continue;
            if(!invite.code.isEmpty()){
                return new Result(invite.code,
                    "New "+(invite.code.startsWith("CULT-")?"Cult":"Gamer Music 3")+
                        " verification code found",
                    candidates.size(),true,invite.created);
            }
            // Use the known TGC verification world only for TGC requests.
            // Cult uses the complete instance location from the actual invite.
            String world="",instance="";
            if(!invite.tgcInstance.isEmpty()){
                world=WORLD;instance=invite.tgcInstance;
            }else if(!invite.location.isEmpty()){
                int colon=invite.location.indexOf(':');
                if(colon>0){
                    world=invite.location.substring(0,colon);
                    instance=invite.location.substring(colon+1);
                }
            }
            if(world.isEmpty()||instance.isEmpty())continue;
            if(!lookedUp.add(world+":"+instance))continue;
            if(++requests>12)break; // Bound metadata lookups across both services.
            try{
                JSONObject metadata=VRChatApi.invitedInstance(context,world,instance);
                String display=metadata.optString("displayName","");
                String detected=world.equalsIgnoreCase(WORLD)?code(display):"";
                if(detected.isEmpty())detected=cultCode(display);
                if(!detected.isEmpty()){
                    return new Result(detected,
                        "Verification code found in the private VRChat instance name",
                        candidates.size(),true,invite.created);
                }
            }catch(Exception ignored){
                // A private world may not expose its instance name to this account.
            }
        }
        return new Result("",
            candidates.isEmpty()?
            "No recent TGC or Cult verification invite found. Start verification in Discord, then check again.":
            "A recent invite was found, but VRChat did not expose a TGC or Cult code. You can also check its instance name in VRChat.",
            candidates.size(),true,0L);
    }

    private static void accept(JSONArray notifications,ArrayList<Invite> matches){
        if(notifications==null)return;
        long now=System.currentTimeMillis();
        for(int i=0;i<notifications.length();i++){
            JSONObject n=notifications.optJSONObject(i);
            if(n==null)continue;
            String type=n.optString("type","").toLowerCase(Locale.US);
            // Only inbound VRChat world invites; never friend requests or
            // unrelated group notices.
            if(!type.equals("invite") &&
               !type.endsWith(".invite") &&
               !type.equals("invitev2"))continue;
            long at=date(n);
            if(at>0 && (at>now+60_000 || now-at>TTL+20_000))continue;
            JSONObject d=details(n);
            if(!containsWorld(n,d))continue;
            Invite invite=new Invite();
            invite.raw=n;invite.details=d;invite.created=at;invite.matched=true;
            invite.code=matchCode(d,0);
            if(invite.code.isEmpty())invite.code=matchCode(n,0);
            invite.location=findLocation(n,0);
            matches.add(invite);
        }
    }
    /**
     * Read two compatible inbox formats. A code is returned only if it is
     * present in the invite itself or confirmed by the invited instance API.
     */
    public static Result scan(Context context)throws Exception{
        ArrayList<Invite> matches=new ArrayList<>();
        boolean read=false;
        Exception lastError=null;
        try{
            accept(VRChatApi.inviteNotifications(context),matches);
            read=true;
        }catch(Exception ex){lastError=ex;}
        try{
            accept(VRChatApi.inviteNotificationsV2(context),matches);
            read=true;
        }catch(Exception ex){if(lastError==null)lastError=ex;}
        if(!read){
            if(lastError!=null)throw lastError;
            throw new IllegalStateException("VRChat invite inbox unavailable");
        }
        // Notifications v1/v2 can contain the same invite. The newest is best.
        Collections.sort(matches,(a,b)->Long.compare(b.created,a.created));
        String newest="";
        for(Invite invite:matches){
            if(!invite.code.isEmpty()){
                return new Result(invite.code,"Code found in your VRChat invite",
                    matches.size(),true,invite.created);
            }
            if(!invite.location.isEmpty()){
                try{
                    JSONObject world=VRChatApi.invitedInstance(context,WORLD,invite.location);
                    String displayName=world.optString("displayName","");
                    // Require the *instance custom display name*, never use
                    // an arbitrary world or instance number as a code.
                    String found=code(displayName);
                    if(!found.isEmpty())
                        return new Result(found,"Code found in the invited instance name",
                            matches.size(),true,invite.created);
                }catch(Exception ignored){
                    // VRChat can hide private-instance data from some clients.
                }
            }
        }
        if(!matches.isEmpty()){
            return new Result("","Invite found, but VRChat did not include its private instance name in the API response. You can still read the code from the invite inside VRChat.",
                matches.size(),true,0);
        }
        return new Result("","No recent TGC verification invite found. Request an invite using the existing verification flow, then return here.",
            0,true,0);
    }
}
