package club.thegamerclub.nodelab;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.InputFilter;
import android.text.InputType;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.concurrent.Executor;
import java.util.Locale;

/**
 * Account-scoped manual avatar eye-height controls, shared by phone Floating
 * and native Quest OSC tabs. Never sends OSC automatically on opening/restore.
 * This is a local UDP request and is not a measurement or moderation verdict.
 */
public final class OscHeightTool {
    private static final String PREF="tgc_osc_eye_height_presets_v1";
    private static final int MAX_CUSTOM=12;
    private OscHeightTool(){}

    private static String account(Context c){
        if(!StaffGate.unlocked(c))throw new SecurityException("Staff access is required.");
        String id;
        try{id=VRChatSession.load(c).optString("userId","");}
        catch(Exception e){throw new SecurityException("Sign in to VRChat first.");}
        if(!id.matches("usr_[0-9a-fA-F-]{36}"))
            throw new SecurityException("Sign in to your personal VRChat account first.");
        return id;
    }
    private static String key(Context c){return account(c)+"_heights";}
    private static SharedPreferences prefs(Context c){
        return c.getSharedPreferences(PREF,Context.MODE_PRIVATE);
    }
    private static JSONArray saved(Context c){
        try{return new JSONArray(prefs(c).getString(key(c),"[]"));}
        catch(Exception e){return new JSONArray();}
    }
    private static void write(Context c,JSONArray values){
        prefs(c).edit().putString(key(c),values.toString()).apply();
    }
    static String validName(String value){
        String label=value==null?"":value.trim();
        if(label.isEmpty()||label.length()>32)
            throw new IllegalArgumentException("Preset name must be 1 to 32 characters.");
        for(int i=0;i<label.length();i++){
            char ch=label.charAt(i);
            if(Character.isISOControl(ch))
                throw new IllegalArgumentException("Preset name contains unsupported characters.");
        }
        return label;
    }
    private static void save(Context c,String name,String value)throws Exception{
        final String label=validName(name);
        final float height=AvatarEyeHeightOsc.parse(value);
        JSONArray old=saved(c),next=new JSONArray();
        for(int i=0;i<old.length();i++){
            JSONObject entry=old.optJSONObject(i);
            if(entry!=null&&!label.equalsIgnoreCase(entry.optString("name","")))
                next.put(entry);
        }
        if(next.length()>=MAX_CUSTOM)
            throw new IllegalArgumentException("Maximum 12 saved custom heights.");
        next.put(new JSONObject().put("name",label).put("meters",(double)height));
        write(c,next);
    }
    private static void delete(Context c,String name){
        JSONArray old=saved(c),next=new JSONArray();
        for(int i=0;i<old.length();i++){
            JSONObject row=old.optJSONObject(i);
            if(row!=null&&!name.equals(row.optString("name","")))next.put(row);
        }
        write(c,next);
    }
    private static Button button(Context c,LinearLayout parent,String label,boolean primary){
        Button b=CompanionStyle.button(c,label,primary);
        b.setTextSize(12);
        b.setMinHeight(0);b.setMinimumHeight(0);
        parent.addView(b,CompanionStyle.buttonParams(c));
        return b;
    }

    /** Optional keyboard callbacks make an Android TYPE_APPLICATION_OVERLAY focusable only while editing. */
    public static void attach(Context c,LinearLayout parent,Executor worker,
                              Runnable enableKeyboard,Runnable dismissKeyboard){
        parent.addView(CompanionStyle.text(c,"AVATAR EYE HEIGHT",15,
            CompanionStyle.ACCENT,true));
        parent.addView(CompanionStyle.text(c,
            "Rule boundary demonstrations: 0.80 m minimum / 2.00 m maximum. "+
            "Sets VRChat avatar EYE height, not necessarily total model height.",
            12,CompanionStyle.MUTED,false));
        LinearLayout standard=new LinearLayout(c);
        standard.setOrientation(LinearLayout.HORIZONTAL);
        parent.addView(standard);
        TextView status=CompanionStyle.text(c,
            "Manual send only. VRChat OSC must be enabled; world scaling limits may override it.",
            11,CompanionStyle.MUTED,false);
        for(float height:new float[]{0.8f,2.0f}){
            Button preset=CompanionStyle.button(c,
                height==0.8f?"0.80 m · Minimum":"2.00 m · Maximum",true);
            preset.setAllCaps(false);preset.setTextSize(12);
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);
            standard.addView(preset,lp);
            preset.setOnClickListener(v->send(c,parent,worker,status,
                Float.toString(height)));
        }
        parent.addView(status);
        parent.addView(CompanionStyle.text(c,"CUSTOM HEIGHTS",12,
            CompanionStyle.ACCENT,true));
        EditText name=CompanionStyle.input(c,"Name (e.g. My normal height)");
        name.setFilters(new InputFilter[]{new InputFilter.LengthFilter(32)});
        EditText meters=CompanionStyle.input(c,"Eye height in metres (e.g. 1.60)");
        meters.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);
        meters.setFilters(new InputFilter[]{new InputFilter.LengthFilter(10)});
        for(EditText edit:new EditText[]{name,meters}){
            edit.setTextSize(14);
            parent.addView(edit,new LinearLayout.LayoutParams(-1,-2));
            if(enableKeyboard!=null){
                edit.setOnFocusChangeListener((v,focused)->{
                    if(focused)enableKeyboard.run();
                });
                edit.setOnClickListener(v->{enableKeyboard.run();edit.requestFocus();});
            }
        }
        button(c,parent,"Apply entered height",true).setOnClickListener(v->{
            if(dismissKeyboard!=null)dismissKeyboard.run();
            send(c,parent,worker,status,meters.getText().toString());
        });
        LinearLayout list=new LinearLayout(c);
        list.setOrientation(LinearLayout.VERTICAL);
        button(c,parent,"Save / update named height",false).setOnClickListener(v->{
            if(dismissKeyboard!=null)dismissKeyboard.run();
            try{
                save(c,name.getText().toString(),meters.getText().toString());
                status.setText("Saved for this personal VRChat account.");
                renderSaved(c,parent,worker,list,status,meters);
            }catch(Exception e){status.setText(e.getMessage());}
        });
        parent.addView(list);
        renderSaved(c,parent,worker,list,status,meters);
        parent.addView(CompanionStyle.text(c,
            "Presets are stored on this device per personal VRChat account. "+
            "OSC sends to VRChat on this same device (127.0.0.1:9000). "+
            "Sending a packet does not confirm that VRChat applied the height.",
            11,CompanionStyle.MUTED,false));
    }

    private static void renderSaved(Context c,LinearLayout parent,Executor worker,
        LinearLayout list,TextView status,EditText input){
        list.removeAllViews();
        try{
            JSONArray rows=saved(c);
            if(rows.length()==0){
                list.addView(CompanionStyle.text(c,"No custom heights saved yet.",
                    11,CompanionStyle.MUTED,false));
            }
            for(int i=0;i<rows.length();i++){
                JSONObject row=rows.optJSONObject(i);
                if(row==null)continue;
                String label=row.optString("name","");
                String raw=row.optString("meters","");
                final float height;
                try{height=AvatarEyeHeightOsc.parse(raw);}
                catch(Exception ignored){continue;}
                LinearLayout line=new LinearLayout(c);
                line.setGravity(Gravity.CENTER_VERTICAL);
                list.addView(line,new LinearLayout.LayoutParams(-1,-2));
                TextView name=CompanionStyle.text(c,
                    label+" · "+AvatarEyeHeightOsc.display(height),
                    12,CompanionStyle.TEXT,false);
                line.addView(name,new LinearLayout.LayoutParams(0,-2,1));
                Button apply=CompanionStyle.button(c,"Apply",false);
                apply.setTextSize(11);
                line.addView(apply,new LinearLayout.LayoutParams(
                    CompanionStyle.dp(c,66),-2));
                apply.setOnClickListener(v->{
                    input.setText(String.format(Locale.US,"%.3f",height));
                    send(c,parent,worker,status,Float.toString(height));
                });
                Button remove=CompanionStyle.button(c,"Delete",false);
                remove.setTextSize(11);
                line.addView(remove,new LinearLayout.LayoutParams(
                    CompanionStyle.dp(c,72),-2));
                remove.setOnClickListener(v->{
                    try{
                        delete(c,label);
                        renderSaved(c,parent,worker,list,status,input);
                        status.setText("Deleted custom height.");
                    }catch(Exception e){status.setText(e.getMessage());}
                });
            }
        }catch(Exception e){status.setText(e.getMessage());}
    }

    private static void send(Context c,LinearLayout parent,Executor worker,
                             TextView status,String raw){
        final float metres;
        final String expected;
        try{metres=AvatarEyeHeightOsc.parse(raw);expected=account(c);}
        catch(Exception e){status.setText(e.getMessage());return;}
        status.setText("Sending "+AvatarEyeHeightOsc.display(metres)+"…");
        worker.execute(()->{
            String result;
            try{
                if(!expected.equals(account(c)))
                    throw new SecurityException("VRChat account changed; height not sent.");
                VRChatOscChat.sendEyeHeight(metres);
                result="Sent "+AvatarEyeHeightOsc.display(metres)+
                    " to local OSC. Verify avatar height in VRChat.";
            }catch(Exception e){
                result="OSC height: "+(e.getMessage()==null?"Send failed.":e.getMessage());
            }
            final String message=result;
            parent.post(()->status.setText(message));
        });
    }
}
