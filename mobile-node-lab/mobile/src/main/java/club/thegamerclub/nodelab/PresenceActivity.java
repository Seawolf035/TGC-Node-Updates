package club.thegamerclub.nodelab;

import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** VRCX-inspired status presets; local presets, live VRChat account presence. */
public final class PresenceActivity extends StaffProtectedActivity {
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private final String[] states={"active","join me","ask me","busy","offline"};
    private final String[] labels={"Online","Join Me","Ask Me","Do Not Disturb","Offline"};
    private String accountId="",chosen="active";
    private boolean busy=false;
    private EditText description;
    private TextView feedback,current,chosenLabel;
    private LinearLayout saved;
    private Button apply,savePreset;
    private final java.util.ArrayList<Button> stateButtons=new java.util.ArrayList<>();
    private int dp(float size){return CompanionStyle.dp(this,size);}
    private TextView text(String val,int sp,int color,boolean strong){
        return CompanionStyle.text(this,val,sp,color,strong);
    }
    private LinearLayout card(LinearLayout root){
        LinearLayout c=CompanionStyle.card(this);
        root.addView(c,CompanionStyle.cardParams(this));return c;
    }
    private Button button(LinearLayout root,String name,boolean primary,Runnable action){
        Button b=CompanionStyle.button(this,name,primary);
        root.addView(b,CompanionStyle.buttonParams(this));
        b.setOnClickListener(v->action.run());return b;
    }
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        if(!StaffGate.unlocked(this)){finish();return;}
        render();load();
    }
    @Override protected void onResume(){
        super.onResume();if(!StaffGate.unlocked(this))finish();
    }
    private void render(){
        getWindow().setStatusBarColor(CompanionStyle.BG);
        getWindow().setNavigationBarColor(CompanionStyle.BG);
        ScrollView scroll=new ScrollView(this);
        scroll.setBackgroundColor(CompanionStyle.BG);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16),dp(14),dp(16),dp(26));
        scroll.addView(root);setContentView(scroll);CompanionStyle.safeArea(this,scroll);
        Button back=CompanionStyle.button(this,"‹ Back",false);
        root.addView(back,new LinearLayout.LayoutParams(dp(117),dp(45)));
        back.setOnClickListener(v->finish());
        root.addView(CompanionStyle.section(this,"YOUR VRCHAT PRESENCE"));
        root.addView(text("Status & presets",27,CompanionStyle.TEXT,true));
        current=text("Checking current status...",13,CompanionStyle.MUTED,false);
        current.setPadding(0,dp(8),0,dp(13));root.addView(current);
        LinearLayout edit=card(root);
        chosenLabel=text("Selected: Online",16,CompanionStyle.TEXT,true);
        edit.addView(chosenLabel);
        edit.addView(CompanionStyle.section(this,"CHOOSE PRESENCE"));
        LinearLayout first=new LinearLayout(this);
        first.setGravity(Gravity.CENTER_VERTICAL);
        edit.addView(first);
        LinearLayout second=new LinearLayout(this);
        second.setGravity(Gravity.CENTER_VERTICAL);
        edit.addView(second);
        for(int i=0;i<states.length;i++){
            final String value=states[i];
            LinearLayout container=i<3?first:second;
            Button option=CompanionStyle.button(this,labels[i],false);
            option.setTextSize(12);
            LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(0,dp(47),1f);
            params.setMargins(dp(3),dp(3),dp(3),dp(3));
            container.addView(option,params);
            stateButtons.add(option);
            option.setOnClickListener(v->{
                chosen=value;
                chosenLabel.setText("Selected: "+labels[indexOf(chosen)]);
                paintStatuses();
            });
        }
        paintStatuses();
        edit.addView(CompanionStyle.section(this,"STATUS MESSAGE"));
        description=CompanionStyle.input(this,"Your status message (up to 32 characters)");
        description.setSingleLine(false);description.setMaxLines(3);
        description.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(32)});
        edit.addView(description,new LinearLayout.LayoutParams(-1,dp(80)));
        View space=new View(this);edit.addView(space,new LinearLayout.LayoutParams(1,dp(14)));
        apply=button(edit,"Apply to VRChat",true,this::confirmApply);
        savePreset=button(edit,"Save as a preset",false,this::promptSavePreset);

        feedback=text("",13,CompanionStyle.MUTED,false);
        root.addView(feedback);
        root.addView(CompanionStyle.section(this,"YOUR SAVED PRESETS"));
        saved=card(root);
        saved.addView(text("Loading presets...",13,CompanionStyle.MUTED,false));
        root.addView(text("Presets are private to this phone and VRChat account. Only Apply changes your live VRChat status.",
            12,CompanionStyle.MUTED,false));
        controls();
    }
    private void paintStatuses(){
        for(int i=0;i<stateButtons.size();i++){
            boolean chosenNow=states[i].equals(chosen);
            Button option=stateButtons.get(i);
            option.setBackground(CompanionStyle.shape(this,
                chosenNow?CompanionStyle.ACCENT:CompanionStyle.RAISED,10,0));
            option.setTextColor(chosenNow?CompanionStyle.BG:CompanionStyle.TEXT);
            option.setContentDescription(labels[i]+(chosenNow?" — selected":""));
        }
    }
    private void controls(){
        boolean available=!accountId.isEmpty()&&!busy;
        apply.setEnabled(available);savePreset.setEnabled(available);
    }
    private void choose(){
        if(busy||accountId.isEmpty())return;
        new AlertDialog.Builder(this).setTitle("Choose status")
            .setSingleChoiceItems(labels,indexOf(chosen),(dialog,n)->{
                chosen=states[n];chosenLabel.setText("Selected: "+labels[n]);dialog.dismiss();
            })
            .setNegativeButton("Cancel",null).show();
    }
    private int indexOf(String value){
        for(int i=0;i<states.length;i++)if(states[i].equals(value))return i;
        return 0;
    }
    private void load(){
        if(busy||!StaffGate.unlocked(this))return;
        busy=true;controls();
        io.execute(()->{
            JSONObject user=null;String issue=null;
            try{user=VRChatApi.currentUser(this);}
            catch(Exception e){issue=e.getMessage();}
            final JSONObject info=user;final String error=issue;
            runOnUiThread(()->{
                busy=false;
                if(isFinishing()||isDestroyed()||!StaffGate.unlocked(this))return;
                if(info==null){
                    feedback.setText("Couldn't check your VRChat account: "+error);
                    controls();return;
                }
                accountId=info.optString("id","");
                String status=info.optString("status","active");
                chosen=indexOf(status)<states.length?states[indexOf(status)]:"active";
                chosenLabel.setText("Selected: "+labels[indexOf(chosen)]);
                paintStatuses();
                description.setText(info.optString("statusDescription",""));
                current.setText("Current status: "+status);
                feedback.setText("");
                controls();showPresets();
            });
        });
    }
    private String prefKey(){return "status_presets_"+accountId;}
    private JSONArray readPresets(){
        if(accountId.isEmpty())return new JSONArray();
        String raw=getSharedPreferences("tgc_vrcx_presets",0).getString(prefKey(),"[]");
        try{return new JSONArray(raw);}catch(Exception e){return new JSONArray();}
    }
    private void writePresets(JSONArray arr){
        getSharedPreferences("tgc_vrcx_presets",0).edit().putString(prefKey(),arr.toString()).apply();
    }
    private void showPresets(){
        saved.removeAllViews();
        JSONArray data=readPresets();
        if(data.length()==0){
            saved.addView(text("No presets saved yet. Choose a status and tap Save as a preset.",
                13,CompanionStyle.MUTED,false));return;
        }
        for(int i=0;i<data.length();i++){
            JSONObject p=data.optJSONObject(i);if(p==null)continue;
            String title=p.optString("title","Preset");
            String status=p.optString("status","active");
            String desc=p.optString("description","");
            TextView name=text(title,16,CompanionStyle.TEXT,true);saved.addView(name);
            TextView detail=text(status+(desc.isEmpty()?"":" · "+desc),12,CompanionStyle.MUTED,false);
            saved.addView(detail);
            LinearLayout buttons=new LinearLayout(this);
            buttons.setGravity(Gravity.CENTER_VERTICAL);
            saved.addView(buttons);
            Button use=CompanionStyle.button(this,"Load preset",false);
            buttons.addView(use,new LinearLayout.LayoutParams(0,dp(43),1f));
            use.setOnClickListener(v->{
                chosen=status;chosenLabel.setText("Selected: "+labels[indexOf(chosen)]);
                paintStatuses();
                description.setText(desc);
                feedback.setText("Preset loaded. Tap Apply to update your VRChat status.");
            });
            Button delete=CompanionStyle.button(this,"Delete",false);
            LinearLayout.LayoutParams dp=new LinearLayout.LayoutParams(dp(100),dp(43));
            dp.leftMargin=dp(8);buttons.addView(delete,dp);
            delete.setOnClickListener(v->confirmDelete(title));
            saved.addView(new View(this),new LinearLayout.LayoutParams(1,dp(12)));
        }
    }
    private void promptSavePreset(){
        if(busy||accountId.isEmpty())return;
        EditText title=CompanionStyle.input(this,"Preset name");
        title.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(45)});
        LinearLayout wrapper=new LinearLayout(this);wrapper.setPadding(dp(15),dp(14),dp(15),0);
        wrapper.addView(title);
        AlertDialog alert=new AlertDialog.Builder(this).setTitle("Save status preset")
            .setView(wrapper).setNegativeButton("Cancel",null).setPositiveButton("Save",null).create();
        alert.setOnShowListener(d->alert.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            String name=title.getText().toString().trim();
            if(name.isEmpty()){title.setError("Enter a preset name");return;}
            JSONArray old=readPresets(),out=new JSONArray();
            for(int i=0;i<old.length();i++){
                JSONObject row=old.optJSONObject(i);
                if(row!=null&&!name.equalsIgnoreCase(row.optString("title","")))out.put(row);
            }
            if(out.length()>=30){title.setError("30-preset limit reached");return;}
            JSONObject row=new JSONObject();
            try{row.put("title",name).put("status",chosen)
                .put("description",description.getText().toString().trim());}
            catch(Exception e){return;}
            out.put(row);writePresets(out);alert.dismiss();showPresets();
            feedback.setText("Preset saved on this device.");
        }));
        alert.show();
    }
    private void confirmDelete(String name){
        new AlertDialog.Builder(this).setTitle("Delete preset?")
            .setMessage("Remove “"+name+"” from this phone?")
            .setNegativeButton("Cancel",null)
            .setPositiveButton("Delete",(d,w)->{
                JSONArray old=readPresets(),out=new JSONArray();
                for(int i=0;i<old.length();i++){
                    JSONObject p=old.optJSONObject(i);
                    if(p!=null&&!name.equalsIgnoreCase(p.optString("title","")))out.put(p);
                }
                writePresets(out);showPresets();
            }).show();
    }
    private void confirmApply(){
        if(busy||accountId.isEmpty()||!StaffGate.unlocked(this))return;
        String status=chosen,desc=description.getText().toString().trim();
        new AlertDialog.Builder(this).setTitle("Update your VRChat status?")
            .setMessage("Set your status to "+labels[indexOf(status)]+
                (desc.isEmpty()?"":("\n\n"+desc))+"?")
            .setNegativeButton("Cancel",null)
            .setPositiveButton("Update",(d,w)->apply(status,desc)).show();
    }
    private void apply(String status,String desc){
        if(busy)return;
        busy=true;controls();feedback.setText("Updating your status…");
        String original=accountId;
        io.execute(()->{
            String error=null;
            try{
                JSONObject check=VRChatApi.currentUser(this);
                if(!original.equals(check.optString("id","")))
                    throw new SecurityException("VRChat account changed. Reopen this screen.");
                VRChatApi.updatePresence(this,status,desc);
            }catch(Exception e){error=e.getMessage();}
            final String issue=error;
            runOnUiThread(()->{
                busy=false;
                if(isFinishing()||isDestroyed())return;
                feedback.setText(issue==null?"Your VRChat status was updated.":
                    "Couldn't update status: "+issue);
                if(issue==null)current.setText("Current status: "+status);
                controls();
            });
        });
    }
    @Override protected void onDestroy(){io.shutdownNow();super.onDestroy();}
}
