package club.thegamerclub.nodelab;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Per-verified-Discord-ID, local, USER-SAVED color palettes.
 * Completely separate from ThemePreferences built-in presets and selected
 * theme settings. Selecting a built-in theme, importing a VRChat UI Code or
 * resetting active colors never erases saved personal presets.
 *
 * Access is available only while a live Relay-backed Admin+ grant is valid.
 * No palette or account credentials are uploaded; this is local device data.
 */
public final class UserThemePresets {
    private static final String STORE="tgc_companion_saved_themes_v1";
    private static final String KEY_PREFIX="palette_discord_";
    public static final int MAX_SAVED=25;
    private UserThemePresets(){}

    public static final class Preset {
        public final String name;
        public final int[] colors;
        Preset(String name,int[] colors){
            this.name=name;
            this.colors=colors.clone();
        }
    }
    private static String accountKey(Context context){
        if(!ThemePreferences.allowed(context))
            throw new SecurityException("Admin+ staff access is required");
        try{
            MobileCredentials.Record creds=MobileCredentials.load(context);
            if(creds==null||creds.discordId==null ||
                !creds.discordId.matches("[0-9]{16,21}"))
                throw new SecurityException("Verified Discord identity unavailable");
            return KEY_PREFIX+creds.discordId;
        }catch(SecurityException e){
            throw e;
        }catch(Exception e){
            throw new SecurityException("Unable to read verified staff identity",e);
        }
    }
    private static SharedPreferences storage(Context c){
        return c.getSharedPreferences(STORE,0);
    }
    private static JSONArray read(Context c,String key){
        String raw=storage(c).getString(key,"");
        if(raw==null||raw.isEmpty())return new JSONArray();
        if(raw.length()>100000)
            throw new IllegalStateException("Saved presets exceed the expected size");
        try{return new JSONArray(raw);}
        catch(JSONException e){
            throw new IllegalStateException("Saved presets could not be parsed",e);
        }
    }
    private static String cleanedName(String value){
        String name=value==null?"":value.trim();
        if(name.length()<1||name.length()>40 ||
            name.matches("(?s).*[\\x00-\\x1F\\x7F].*"))
            throw new IllegalArgumentException("Choose a preset name of 1–40 printable characters.");
        return name;
    }
    private static int[] readColors(JSONObject obj){
        JSONArray values=obj.optJSONArray("colors");
        if(values==null||values.length()!=ThemePreferences.COLOR_NAMES.length)
            throw new IllegalStateException("Saved color palette is incomplete");
        int[] colors=new int[values.length()];
        for(int i=0;i<colors.length;i++){
            if(!(values.opt(i) instanceof Number))
                throw new IllegalStateException("Saved color palette has an invalid channel");
            colors[i]=0xFF000000|(values.optInt(i)&0x00FFFFFF);
        }
        return colors;
    }
    private static JSONObject record(String name,int[] colors)throws JSONException{
        JSONObject item=new JSONObject();
        item.put("name",name);
        JSONArray arr=new JSONArray();
        for(int color:colors)arr.put(0xFF000000|(color&0x00FFFFFF));
        item.put("colors",arr);
        return item;
    }
    private static void persist(Context c,String key,JSONArray items){
        if(!storage(c).edit().putString(key,items.toString()).commit())
            throw new IllegalStateException("Could not save personal presets on this device");
    }
    /** Saved data is independent per verified staff Discord ID on this device. */
    public static synchronized List<Preset> list(Context context){
        String key=accountKey(context);
        JSONArray data=read(context,key);
        List<Preset> items=new ArrayList<>();
        for(int i=0;i<data.length();i++){
            JSONObject obj=data.optJSONObject(i);
            if(obj==null)continue;
            String name=obj.optString("name","");
            if(name.isEmpty())continue;
            items.add(new Preset(name,readColors(obj)));
        }
        return Collections.unmodifiableList(items);
    }
    public static synchronized boolean exists(Context context,String requestedName){
        String name=cleanedName(requestedName);
        for(Preset p:list(context))if(p.name.equalsIgnoreCase(name))return true;
        return false;
    }
    /**
     * Save the EXACT current ten color channels under a personal name.
     * Refuses duplicates unless an explicit overwrite is approved in the UI.
     */
    public static synchronized void save(Context context,String requestedName,
                                         boolean overwrite){
        String key=accountKey(context),name=cleanedName(requestedName);
        JSONArray old=read(context,key),next=new JSONArray();
        boolean found=false;
        try{
            for(int i=0;i<old.length();i++){
                JSONObject row=old.optJSONObject(i);
                if(row==null)continue;
                if(row.optString("name","").equalsIgnoreCase(name)){
                    if(!overwrite)
                        throw new IllegalArgumentException(
                            "A preset called "+name+" already exists");
                    next.put(record(name,ThemePreferences.colors(context)));
                    found=true;
                }else next.put(row);
            }
            if(!found){
                if(next.length()>=MAX_SAVED)
                    throw new IllegalArgumentException(
                        "You can save up to "+MAX_SAVED+" personal presets. Delete one first.");
                next.put(record(name,ThemePreferences.colors(context)));
            }
        }catch(JSONException e){
            throw new IllegalStateException("Could not encode personal colors",e);
        }
        persist(context,key,next);
    }
    public static synchronized void load(Context context,String requestedName){
        String name=cleanedName(requestedName);
        for(Preset saved:list(context)){
            if(saved.name.equalsIgnoreCase(name)){
                ThemePreferences.applySavedColors(context,saved.colors);
                return;
            }
        }
        throw new IllegalArgumentException("The saved preset no longer exists");
    }
    public static synchronized void delete(Context context,String requestedName){
        String key=accountKey(context),name=cleanedName(requestedName);
        JSONArray current=read(context,key),next=new JSONArray();
        boolean found=false;
        for(int i=0;i<current.length();i++){
            JSONObject row=current.optJSONObject(i);
            if(row==null)continue;
            if(row.optString("name","").equalsIgnoreCase(name))found=true;
            else next.put(row);
        }
        if(!found)throw new IllegalArgumentException("The saved preset no longer exists");
        persist(context,key,next);
    }
}
