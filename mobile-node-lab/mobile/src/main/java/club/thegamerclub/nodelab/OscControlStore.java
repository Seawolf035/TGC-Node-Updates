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
        // Persist one canonical relative form for both full OSC addresses
        // and shorter names; existing saved simple parameter names stay valid.
        String label=AvatarOscParameterPath.relative(name);
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
    // Shared interval catalog and validation for Quest native tools, Android
    // phone floating tools and each foreground OSC sender.
    // 5 seconds is the minimum: UDP chatbox writes are locally rate-limited
    // and excessively fast re-broadcasts can interfere with manual chat.
    public static int[] liveIntervals(){
        return new int[]{5,10,15,20,30,45,60,90,120};
    }
    public static boolean supportsLiveInterval(int seconds){
        for(int allowed:liveIntervals())if(seconds==allowed)return true;
        return false;
    }
    public static int liveInterval(Context ctx){
        int seconds=prefs(ctx).getInt(key(ctx,"live_interval_seconds"),5);
        return supportsLiveInterval(seconds)?seconds:5;
    }
    public static void setLiveInterval(Context ctx,int seconds){
        if(!supportsLiveInterval(seconds))
            throw new IllegalArgumentException("Choose a supported Live Chatbox timing.");
        prefs(ctx).edit().putInt(key(ctx,"live_interval_seconds"),seconds).apply();
    }
    /** Additional named rotating chatboxes; the legacy status is optional. */
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
            // Revoked official tags must fail closed, even if the default
            // is disabled. Never throw while deleting a local chatbox.
            prefs(ctx).edit().putBoolean(
                key(ctx,"chatbox_rotation_enabled"),false).apply();
    }
    /** Keep existing installs identical by default. Preferences are per VRChat account. */
    public static boolean includeDefaultChatbox(Context ctx){
        return prefs(ctx).getBoolean(key(ctx,"include_default_chatbox"),true);
    }
    /** When enabled, excludes BOTH default status and custom personal chatboxes. */
    public static boolean officialOnly(Context ctx){
        return prefs(ctx).getBoolean(key(ctx,"official_only_chatboxes"),false);
    }
    public static void setOfficialOnly(Context ctx,boolean on){
        if(!StaffGate.unlocked(ctx))
            throw new SecurityException("Verified staff access required.");
        if(on&&TgcOfficialChatboxes.selectedEligible(ctx).isEmpty())
            throw new IllegalArgumentException(
                "Select an unlocked official TGC chatbox tag first.");
        android.content.SharedPreferences.Editor edit=prefs(ctx).edit()
            .putBoolean(key(ctx,"official_only_chatboxes"),on);
        if(on)edit.putBoolean(key(ctx,"chatbox_rotation_enabled"),true);
        edit.apply();
    }
    public static void setIncludeDefaultChatbox(Context ctx,boolean included){
        if(!StaffGate.unlocked(ctx))
            throw new SecurityException("Verified staff access required.");
        if(!included&&!officialOnly(ctx)&&chatboxes(ctx).length()==0&&
           TgcOfficialChatboxes.selectedEligible(ctx).isEmpty())
            throw new IllegalArgumentException(
                "Save a personal chatbox or select an unlocked official tag first.");
        android.content.SharedPreferences.Editor edit=prefs(ctx).edit()
            .putBoolean(key(ctx,"include_default_chatbox"),included);
        if(!included)edit.putBoolean(key(ctx,"chatbox_rotation_enabled"),true);
        edit.apply();
    }
    /** Clear only the three personal default fields, never any official selection. */
    public static void clearPersonalStatus(Context ctx){
        if(!StaffGate.unlocked(ctx))
            throw new SecurityException("Verified staff access required.");
        prefs(ctx).edit()
            .putString(key(ctx,"prefix"),"")
            .putBoolean(key(ctx,"include_time"),false)
            .putBoolean(key(ctx,"include_battery"),false).apply();
    }
    public static boolean rotationEnabled(Context ctx){
        // Official-only must never silently fall back to the legacy personal line.
        if(officialOnly(ctx))
            return prefs(ctx).getBoolean(key(ctx,"chatbox_rotation_enabled"),false)
                &&!TgcOfficialChatboxes.selectedEligible(ctx).isEmpty();
        return prefs(ctx).getBoolean(key(ctx,"chatbox_rotation_enabled"),false)
            &&(chatboxes(ctx).length()>0||
               !TgcOfficialChatboxes.selectedEligible(ctx).isEmpty());
    }
    public static void setRotationEnabled(Context ctx,boolean enabled){
        if(!StaffGate.unlocked(ctx))
            throw new SecurityException("Verified staff access required.");
        if(!enabled&&(officialOnly(ctx)||!includeDefaultChatbox(ctx)))
            throw new IllegalArgumentException(
                "Turn on the default personal status before disabling rotation.");
        if(enabled&&effectiveExtras(ctx)==0)
            throw new IllegalArgumentException(
                "Save a personal chatbox or select an unlocked official TGC tag first.");
        prefs(ctx).edit().putBoolean(key(ctx,"chatbox_rotation_enabled"),enabled).apply();
    }
    private static int effectiveExtras(Context ctx){
        int official=TgcOfficialChatboxes.selectedEligible(ctx).size();
        return official+(officialOnly(ctx)?0:chatboxes(ctx).length());
    }
    public static int[] rotationIntervals(){
        return new int[]{10,15,20,30,45,60,90,120,180,300};
    }
    public static boolean supportsRotationInterval(int seconds){
        for(int allowed:rotationIntervals())if(seconds==allowed)return true;
        return false;
    }
    public static int rotationInterval(Context ctx){
        int seconds=prefs(ctx).getInt(key(ctx,"chatbox_rotation_interval"),30);
        return supportsRotationInterval(seconds)?seconds:30;
    }
    public static void setRotationInterval(Context ctx,int seconds){
        if(!supportsRotationInterval(seconds))
            throw new IllegalArgumentException("Choose a supported rotation timing.");
        prefs(ctx).edit().putInt(key(ctx,"chatbox_rotation_interval"),seconds).apply();
    }
    public static int liveSlotCount(Context ctx){
        boolean official=officialOnly(ctx);
        if(rotationEnabled(ctx)){
            return (official||!includeDefaultChatbox(ctx)?0:1)
                +(official?0:chatboxes(ctx).length())
                +TgcOfficialChatboxes.selectedEligible(ctx).size();
        }
        // No eligible official tags after rank loss => fail closed, not default.
        return official||!includeDefaultChatbox(ctx)?0:1;
    }
    public static String liveChatboxAt(Context ctx,int slot){
        int count=liveSlotCount(ctx);
        if(count<1)
            throw new IllegalStateException(
                "No authorized chatboxes to broadcast. Select an unlocked tag.");
        if(slot<0||slot>=count)
            throw new IllegalArgumentException("Chatbox slot is no longer available.");
        if(!rotationEnabled(ctx))return statusLine(ctx);
        int offset=slot;
        if(!officialOnly(ctx)&&includeDefaultChatbox(ctx)){
            if(offset==0)return statusLine(ctx);
            offset--;
        }
        if(!officialOnly(ctx)){
            JSONArray personal=chatboxes(ctx);
            if(offset<personal.length()){
                JSONObject row=personal.optJSONObject(offset);
                if(row==null)throw new IllegalStateException("Saved chatbox is unavailable.");
                return buildStatusLine(ctx,row.optString("text",""),
                    row.optBoolean("time",false),row.optBoolean("battery",false));
            }
            offset-=personal.length();
        }
        java.util.List<TgcOfficialChatboxes.Template> selected=
            TgcOfficialChatboxes.selectedEligible(ctx);
        if(offset<0||offset>=selected.size())
            throw new IllegalStateException("Official chatbox authorization changed.");
        return VRChatOscChat.validate(selected.get(offset).text);
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
