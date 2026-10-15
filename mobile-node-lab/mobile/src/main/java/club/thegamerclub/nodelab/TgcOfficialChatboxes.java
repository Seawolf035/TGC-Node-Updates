package club.thegamerclub.nodelab;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

/**
 * Fixed official templates imported from the owner's Magic Chatbox export.
 * Rank-based visibility is a verified entitlement, never a role stored on
 * the phone. Editing personal OSC chatbox text is deliberately unrestricted.
 */
public final class TgcOfficialChatboxes {
    public static final class Template {
        public final String id,rank,text;
        public final int minTier;
        public final boolean suggested;
        private Template(String id,String rank,String text,int tier,boolean suggested){
            this.id=id;this.rank=rank;this.text=text;
            this.minTier=tier;this.suggested=suggested;
        }
    }
    private static final String PREF="tgc_official_chatbox_selections";
    private static final Template[] ALL={
        new Template("common_discord","All Staff","『Discord.gg/TheGamerClub』",0,true),
        new Template("common_staff","All Staff","『Gamer Club Staff』",0,false),
        new Template("common_welcome","All Staff","『Welcome to The Gamer Club』",0,true),
        new Template("common_instance","All Staff","『Instance Staff』",0,true),
        new Template("common_do_not_mute","All Staff","『Do not mute/block me』",0,true),
        new Template("trial_mod","Trial Moderator","『Gamer Club Trial Mod』",1,false),
        new Template("moderator","Moderator","『Gamer Club Moderator』",2,false),
        new Template("head_mod","Head Moderator","『Gamer Club Head Mod』",3,false),
        new Template("admin","Admin","『Gamer Club Admin』",4,false),
        new Template("head_admin","Head Admin","『Gamer Club Head Admin』",5,false),
        new Template("owner","Owner","『Gamer Club Owner』",6,false),
        new Template("royalty","The Royalty","『Gamer Club Royalty』",7,false)
    };
    private TgcOfficialChatboxes(){}

    private static String ownerKey(Context context){
        String id;
        try{id=VRChatSession.load(context).optString("userId","");}
        catch(Exception e){
            throw new SecurityException("Sign in to your personal VRChat account first.",e);
        }
        if(!id.matches("usr_[0-9a-fA-F-]{36}"))
            throw new SecurityException("Sign in to your personal VRChat account first.");
        return id;
    }
    private static SharedPreferences prefs(Context context){
        return context.getSharedPreferences(PREF,Context.MODE_PRIVATE);
    }
    public static Template[] templates(){return ALL.clone();}
    public static boolean eligible(Context context,Template template){
        return template!=null && StaffGate.officialOscTagsAllowed(context) &&
            StaffGate.rankTier(context)>=template.minTier;
    }
    private static HashSet<String> selectionIds(Context context){
        String data=prefs(context).getString(ownerKey(context),"[]");
        HashSet<String> ids=new HashSet<>();
        try{
            JSONArray stored=new JSONArray(data);
            for(int i=0;i<stored.length();i++){
                String id=stored.optString(i,"");
                if(!id.isEmpty())ids.add(id);
            }
        }catch(Exception ignored){}
        return ids;
    }
    public static boolean isSelected(Context context,String templateId){
        return selectionIds(context).contains(templateId);
    }
    public static void setSelected(Context context,String templateId,boolean on){
        if(!StaffGate.officialOscTagsAllowed(context))
            throw new SecurityException("Official TGC staff and rank OSC tags are not included in delegated access.");
        Template target=null;
        for(Template template:ALL)
            if(template.id.equals(templateId)){target=template;break;}
        if(target==null)throw new IllegalArgumentException("Unknown TGC template.");
        if(on&&!eligible(context,target))
            throw new SecurityException("This official chatbox requires a higher verified staff rank.");
        HashSet<String> ids=selectionIds(context);
        if(on)ids.add(templateId);else ids.remove(templateId);
        // Persist only IDs; the server-verifiable rank is never saved locally.
        JSONArray saved=new JSONArray();
        for(Template item:ALL)if(ids.contains(item.id))saved.put(item.id);
        prefs(context).edit().putString(ownerKey(context),saved.toString()).apply();
    }
    public static List<Template> selectedEligible(Context context){
        if(!StaffGate.officialOscTagsAllowed(context))return Collections.emptyList();
        HashSet<String> ids;
        try{ids=selectionIds(context);}
        catch(Exception ex){return Collections.emptyList();}
        ArrayList<Template> result=new ArrayList<>();
        final int tier=StaffGate.rankTier(context);
        for(Template item:ALL)
            if(ids.contains(item.id)&&tier>=item.minTier)result.add(item);
        return result;
    }
}
