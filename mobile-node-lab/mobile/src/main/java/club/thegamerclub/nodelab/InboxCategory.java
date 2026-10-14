package club.thegamerclub.nodelab;

import org.json.JSONObject;
import java.util.Locale;

/** VRCX-compatible VRChat personal, group and event notification categories. */
public final class InboxCategory {
    private InboxCategory(){}
    public static String of(JSONObject n){
        if(n==null)return "other";
        String type=n.optString("type","").toLowerCase(Locale.US);
        String category=n.optString("category","").toLowerCase(Locale.US);
        if(type.startsWith("group.")||type.startsWith("moderation.")||
           type.equals("groupchange")||type.equals("event.announcement")||
           category.startsWith("social.group")||
           category.equals("group")||category.startsWith("moderation."))
            return "groups";
        if(type.startsWith("event.")||category.equals("event")||
           category.startsWith("event."))
            return "events";
        if(type.equals("requestinvite"))return "requests";
        if(type.equals("invite")||type.equals("inviteresponse")||
           type.equals("requestinviteresponse"))return "invite";
        if(type.equals("friendrequest")||type.equals("ignoredfriendrequest")||
           type.equals("boop")||type.startsWith("friend"))return "friend";
        return "other";
    }
    public static boolean include(JSONObject n,String filter){
        if(n==null)return false;
        if("all".equals(filter)||filter==null)return true;
        if("unread".equals(filter))return !n.optBoolean("seen",false);
        String category=of(n);
        if("invite".equals(filter))return category.equals("invite")||
            category.equals("requests");
        return category.equals(filter);
    }
    public static String groupId(JSONObject n){
        if(n==null)return "";
        JSONObject detail=n.optJSONObject("details");
        if(detail==null){
            String raw=n.optString("details","");
            if(raw.startsWith("{"))try{detail=new JSONObject(raw);}
                catch(Exception ignored){}
        }
        JSONObject data=n.optJSONObject("data");
        for(JSONObject from:new JSONObject[]{n,detail,data,
            data==null?null:data.optJSONObject("details")}){
            if(from==null)continue;
            String id=from.optString("groupId",from.optString("groupID",""));
            if(id.matches("grp_[0-9a-fA-F-]{36}"))return id;
            JSONObject nested=from.optJSONObject("group");
            if(nested!=null){
                id=nested.optString("id","");
                if(id.matches("grp_[0-9a-fA-F-]{36}"))return id;
            }
        }
        return "";
    }
    public static String title(JSONObject n){
        String type=n.optString("type","Notification");
        String label=n.optString("title","");
        if(label.isEmpty()){
            JSONObject data=n.optJSONObject("data");
            if(data!=null)label=data.optString("title",data.optString("name",""));
        }
        if(label.isEmpty())label=n.optString("senderUsername",
            n.optString("senderDisplayName",""));
        if(!label.isEmpty())return label;
        switch(of(n)){
            case "groups":return "Group notification";
            case "events":return "Group event";
            case "invite":return "VRChat invitation";
            case "requests":return "Invite request";
            case "friend":return "Friend update";
            default:return type;
        }
    }
    public static String summary(JSONObject n){
        String message=n.optString("message",n.optString("description",""));
        JSONObject data=n.optJSONObject("data");
        if(message.isEmpty()&&data!=null)
            message=data.optString("message",data.optString("description",""));
        if(message.isEmpty())message=n.optString("type","Notification");
        return message;
    }
}
