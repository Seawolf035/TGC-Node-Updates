package club.thegamerclub.nodelab;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Local, account-separated OSC settings. Never stores chat history, login
 * credentials, VRChat API data, or outgoing background broadcasts.
 *
 * Saved phrases and avatar parameter names are only written on explicit user
 * action. A different personal VRChat account sees a separate collection.
 */
public final class OscControlStore {
    private static final String PREF="tgc_osc_controls";
    private static final int MAX_PHRASES=24;
    private static final int MAX_PARAMETERS=16;
    private static final int MAX_CHATBOXES=8;
    private OscControlStore(){}

    private static android.content.SharedPreferences prefs(Context ctx){
        return ctx.getSharedPreferences(PREF,Context.MODE_PRIVATE);
    }
    private static String account(Context ctx){
        String user;
        try{user=VRChatSession.load(ctx).optString("userId","");}
        catch(Exception e){
            throw new SecurityException(
                "Sign in to your personal VRChat account first.",e);
        }
        if(!user.matches("usr_[0-9a-fA-F-]{36}"))
            throw new SecurityException("Sign in to your personal VRChat account first.");
        return user;
    }
    private static String key(Context ctx,String suffix){
        return account(ctx)+"_"+suffix;
    }
    private static JSONArray array(Context ctx,String suffix){
        try{return new JSONArray(prefs(ctx).getString(key(ctx,suffix),"[]"));}
        catch(Exception e){return new JSONArray();}
    }
    private static void saveArray(Context ctx,String suffix,JSONArray data){
        prefs(ctx).edit().putString(key(ctx,suffix),data.toString()).apply();
    }
    public static JSONArray phrases(Context ctx){return array(ctx,"phrases");}
    public static JSONArray parameters(Context ctx){return array(ctx,"params");}
    public static JSONArray chatboxes(Context ctx){return array(ctx,"rotating_chatboxes");}
    private static String validLabel(String value){
        if(value==null||value.trim().isEmpty()||value.trim().length()>40)
            throw new IllegalArgumentException("Use a name between 1 and 40 characters.");
        return value.trim();
    }
    public static void savePhrase(Context ctx,String name,String message)throws Exception{
        String label=validLabel(name);
        String text=VRChatOscChat.validate(message);
        JSONArray existing=phrases(ctx),updated=new JSONArray();
        for(int i=0;i<existing.length();i++){
            JSONObject row=existing.optJSONObject(i);
            if(row==null||label.equalsIgnoreCase(row.optString("name","")))continue;
            updated.put(row);
        }
        if(updated.length()>=MAX_PHRASES)
            throw new IllegalArgumentException("Maximum 24 saved phrases.");
        updated.put(new JSONObject().put("name",label).put("text",text));
        saveArray(ctx,"phrases",updated);
    }
    public static void deletePhrase(Context ctx,String name)throws Exception{
        JSONArray previous=phrases(ctx),next=new JSONArray();
        for(int i=0;i<previous.length();i++){
            JSONObject row=previous.optJSONObject(i);
            if(row!=null&&!name.equals(row.optString("name","")))next.put(row);
        }
        saveArray(ctx,"phrases",next);
    }
    public static void saveParameter(Context ctx,String name,String type)throws Exception{
        String label=name==null?"":name.trim();
        if(!label.matches("[A-Za-z_][A-Za-z0-9_]{0,63}"))
            throw new IllegalArgumentException(
                "Parameter names must use letters, digits and underscores.");
        if(!"bool".equals(type)&&!"int".equals(type)&&!"float".equals(type))
            throw new IllegalArgumentException("Choose Bool, Int or Float.");
        JSONArray existing=parameters(ctx),updated=new JSONArray();
        for(int i=0;i<existing.length();i++){
            JSONObject row=existing.optJSONObject(i);
            if(row==null||label.equals(row.optString("name","")))continue;
            updated.put(row);
        }
        if(updated.length()>=MAX_PARAMETERS)
            throw new IllegalArgumentException("Maximum 16 saved parameters.");
        updated.put(new JSONObject().put("name",label).put("type",type));
        saveArray(ctx,"params",updated);
    }
    public static void deleteParameter(Context ctx,String name)throws Exception{
        JSONArray previous=parameters(ctx),next=new JSONArray();
        for(int i=0;i<previous.length();i++){
            JSONObject row=previous.optJSONObject(i);
            if(row!=null&&!name.equals(row.optString("name","")))next.put(row);
        }
        saveArray(ctx,"params",next);
    }
    public static String prefix(Context ctx){
        return prefs(ctx).getString(key(ctx,"prefix"),"");
    }
    public static void setPrefix(Context ctx,String text){
        String value=text==null?"":text.trim();
        if(value.length()>90)
            throw new IllegalArgumentException("Status prefix is limited to 90 characters.");
        prefs(ctx).edit().putString(key(ctx,"prefix"),value).apply();
    }
    public static boolean includeTime(Context ctx){
        return prefs(ctx).getBoolean(key(ctx,"include_time"),true);
    }
    public static void setIncludeTime(Context ctx,boolean enabled){
        prefs(ctx).edit().putBoolean(key(ctx,"include_time"),enabled).apply();
    }
    public static boolean includeBattery(Context ctx){
        return prefs(ctx).getBoolean(key(ctx,"include_battery"),false);
    }
    public static void setIncludeBattery(Context ctx,boolean enabled){
        prefs(ctx).edit().putBoolean(key(ctx,"include_battery"),enabled).apply();
    }
    public static boolean typingAuto(Context ctx){
        return prefs(ctx).getBoolean(key(ctx,"typing_auto"),false);
    }
    public static void setTypingAuto(Context ctx,boolean enabled){
        prefs(ctx).edit().putBoolean(key(ctx,"typing_auto"),enabled).apply();
    }
    public static int liveInterval(Context ctx){
        int seconds=prefs(ctx).getInt(key(ctx,"live_interval_seconds"),5);
        return seconds==5||seconds==10||seconds==15||seconds==30?
            seconds:5;
    }
    public static void setLiveInterval(Context ctx,int seconds){
        if(seconds!=5&&seconds!=10&&seconds!=15&&seconds!=30)
            throw new IllegalArgumentException("Choose a 5, 10, 15 or 30 second interval.");
        prefs(ctx).edit().putInt(key(ctx,"live_interval_seconds"),seconds).apply();
    }
    /** Additional named rotating chatboxes; the legacy status remains slot zero. */
    public static void saveChatbox(Context ctx,String previousName,String newName,
                                   String message,boolean clock,boolean battery)throws Exception{
        String label=validLabel(newName);
        String content=message==null?"":message.trim();
        if(content.length()>90)
            throw new IllegalArgumentException("Chatbox text is limited to 90 characters.");
        // Validate the full outgoing OSC message before changing saved settings.
        buildStatusLine(ctx,content,clock,battery);
        JSONArray old=chatboxes(ctx),next=new JSONArray();
        String original=previousName==null?"":previousName.trim();
        boolean replaced=false;
        for(int n=0;n<old.length();n++){
            JSONObject item=old.optJSONObject(n);
            if(item==null)continue;
            String name=item.optString("name","");
            if(label.equalsIgnoreCase(name)&&!original.equalsIgnoreCase(name))
                throw new IllegalArgumentException("A chatbox with that name already exists.");
            if(!original.isEmpty()&&original.equalsIgnoreCase(name)){
                next.put(new JSONObject().put("name",label).put("text",content)
                    .put("time",clock).put("battery",battery));
                replaced=true;
            }else next.put(item);
        }
        if(!replaced){
            if(next.length()>=MAX_CHATBOXES)
                throw new IllegalArgumentException("Maximum 8 additional chatboxes.");
            next.put(new JSONObject().put("name",label).put("text",content)
                .put("time",clock).put("battery",battery));
        }
        saveArray(ctx,"rotating_chatboxes",next);
    }
    public static void deleteChatbox(Context ctx,String name){
        JSONArray old=chatboxes(ctx),next=new JSONArray();
        for(int n=0;n<old.length();n++){
            JSONObject item=old.optJSONObject(n);
            if(item!=null&&!name.equals(item.optString("name","")))next.put(item);
        }
        saveArray(ctx,"rotating_chatboxes",next);
        if(next.length()==0 &&
            TgcOfficialChatboxes.selectedEligible(ctx).isEmpty())
            setRotationEnabled(ctx,false);
    }
    public static boolean rotationEnabled(Context ctx){
        return prefs(ctx).getBoolean(key(ctx,"chatbox_rotation_enabled"),false)
            &&(chatboxes(ctx).length()>0||
               !TgcOfficialChatboxes.selectedEligible(ctx).isEmpty());
    }
    public static void setRotationEnabled(Context ctx,boolean enabled){
        if(enabled&&chatboxes(ctx).length()==0&&
           TgcOfficialChatboxes.selectedEligible(ctx).isEmpty())
            throw new IllegalArgumentException(
                "Save a personal chatbox or select an unlocked official TGC chatbox first.");
        prefs(ctx).edit().putBoolean(key(ctx,"chatbox_rotation_enabled"),enabled).apply();
    }
    public static int rotationInterval(Context ctx){
        int seconds=prefs(ctx).getInt(key(ctx,"chatbox_rotation_interval"),30);
        return seconds==15||seconds==30||seconds==60||seconds==120?seconds:30;
    }
    public static void setRotationInterval(Context ctx,int seconds){
        if(seconds!=15&&seconds!=30&&seconds!=60&&seconds!=120)
            throw new IllegalArgumentException("Choose a 15, 30, 60 or 120 second switch.");
        prefs(ctx).edit().putInt(key(ctx,"chatbox_rotation_interval"),seconds).apply();
    }
    public static int liveSlotCount(Context ctx){
        return rotationEnabled(ctx)?
            1+chatboxes(ctx).length()+TgcOfficialChatboxes.selectedEligible(ctx).size():1;
    }
    public static String liveChatboxAt(Context ctx,int slot){
        if(slot<=0||!rotationEnabled(ctx))return statusLine(ctx);
        JSONArray personal=chatboxes(ctx);
        if(slot<=personal.length()){
            JSONObject row=personal.optJSONObject(slot-1);
            if(row==null)return statusLine(ctx);
            return buildStatusLine(ctx,row.optString("text",""),
                row.optBoolean("time",false),row.optBoolean("battery",false));
        }
        java.util.List<TgcOfficialChatboxes.Template> selected=
            TgcOfficialChatboxes.selectedEligible(ctx);
        int officialSlot=slot-personal.length()-1;
        if(officialSlot<0||officialSlot>=selected.size())return statusLine(ctx);
        // Official messages are fixed, no SFX and no implicit broadcast.
        return VRChatOscChat.validate(selected.get(officialSlot).text);
    }
    public static String previewChatbox(Context ctx,String message,
                                        boolean clock,boolean battery){
        return buildStatusLine(ctx,message,clock,battery);
    }
    public static String statusLine(Context ctx){
        return buildStatusLine(ctx,prefix(ctx),includeTime(ctx),includeBattery(ctx));
    }
    private static String buildStatusLine(Context ctx,String prefix,
                                          boolean showTime,boolean showBattery){
        StringBuilder result=new StringBuilder();
        String message=prefix==null?"":prefix.trim();
        if(message.length()>90)
            throw new IllegalArgumentException("Status text is limited to 90 characters.");
        if(!message.isEmpty())result.append(message);
        if(showTime){
            if(result.length()>0)result.append("  ·  ");
            result.append(new SimpleDateFormat("h:mm a",Locale.getDefault())
                .format(new Date()));
        }
        if(showBattery){
            if(result.length()>0)result.append("  ·  ");
            Intent battery=ctx.registerReceiver(null,
                new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if(battery==null){
                result.append("Battery unavailable");
            }else{
                int level=battery.getIntExtra(BatteryManager.EXTRA_LEVEL,-1);
                int scale=battery.getIntExtra(BatteryManager.EXTRA_SCALE,100);
                int status=battery.getIntExtra(BatteryManager.EXTRA_STATUS,-1);
                if(level<0||scale<=0)result.append("Battery unavailable");
                else result.append("Battery ").append((level*100)/scale).append('%');
                if(status==BatteryManager.BATTERY_STATUS_CHARGING||
                   status==BatteryManager.BATTERY_STATUS_FULL)
                    result.append(" (charging)");
            }
        }
        return VRChatOscChat.validate(result.toString());
    }
}
