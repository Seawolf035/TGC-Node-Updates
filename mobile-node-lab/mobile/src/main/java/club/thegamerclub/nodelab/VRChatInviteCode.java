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
        Result(String code,String message,int matchingInvites,boolean inboxRead,long createdAt){
            this.code=code;this.message=message;this.matchingInvites=matchingInvites;
            this.inboxRead=inboxRead;this.createdAt=createdAt;
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
        for(String field:new String[]{"location","instanceId","instanceLocation",
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
                }else if(str.contains(WORLD+":")){
                    String found=instanceLocation(str);
                    if(!found.isEmpty())return found;
                }
            }
        }
        return "";
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
