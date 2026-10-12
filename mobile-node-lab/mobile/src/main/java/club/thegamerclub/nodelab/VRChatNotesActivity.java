package club.thegamerclub.nodelab;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Private VRChat user notes. Reads/writes ONLY through the signed-in personal
 * VRChat account, never through the TGC relay, staff Node or Discord.
 * On target-profile launch, checks existing notes page-by-page before allowing
 * a new note, so an older note can't be silently overwritten.
 */
public final class VRChatNotesActivity extends StaffProtectedActivity {
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final ArrayList<JSONObject> notes=new ArrayList<>();
    private NotesAdapter adapter;
    private TextView status,heading;
    private Button more,refresh;
    private LinearLayout editorHost;
    private String targetId="",targetName="";
    private int offset=0;
    private boolean busy=false,hasMore=true,editorReady=false;
    private int dp(float n){return CompanionStyle.dp(this,n);}
    private TextView text(String value,int size,int color,boolean bold){
        return CompanionStyle.text(this,value,size,color,bold);
    }
    private static boolean validUser(String value){
        return value!=null && value.matches("usr_[0-9a-fA-F-]{36}");
    }
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);
        if(!StaffGate.unlocked(this)){finish();return;}
        String requested=getIntent().getStringExtra("target_id");
        if(validUser(requested)){
            targetId=requested;
            targetName=getIntent().getStringExtra("target_name");
            if(targetName==null||targetName.trim().isEmpty())targetName="this player";
        }
        render();
        load(true);
    }
    @Override protected void onResume(){
        super.onResume();
        if(!StaffGate.unlocked(this))finish();
    }
    private void render(){
        getWindow().setStatusBarColor(CompanionStyle.BG);
        getWindow().setNavigationBarColor(CompanionStyle.BG);
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(CompanionStyle.BG);
        setContentView(root);
        CompanionStyle.safeArea(this,root);

        LinearLayout top=new LinearLayout(this);
        top.setPadding(dp(14),dp(12),dp(15),dp(8));
        top.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(top);
        Button back=CompanionStyle.button(this,"‹",false);
        back.setTextSize(26);
        top.addView(back,new LinearLayout.LayoutParams(dp(52),dp(46)));
        back.setOnClickListener(v->finish());
        heading=text(targetId.isEmpty()?"Your VRChat notes":"Note for "+targetName,
            21,CompanionStyle.TEXT,true);
        heading.setMaxLines(2);
        LinearLayout.LayoutParams h=new LinearLayout.LayoutParams(0,-2,1f);
        h.leftMargin=dp(13);top.addView(heading,h);

        TextView info=text("Private to your VRChat account. Notes sync through VRChat and are never sent to TGC.",
            13,CompanionStyle.MUTED,false);
        info.setPadding(dp(17),dp(2),dp(17),dp(10));
        root.addView(info);

        LinearLayout tools=new LinearLayout(this);
        tools.setPadding(dp(16),dp(7),dp(16),dp(9));
        tools.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(tools);
        status=text("Loading saved notes...",13,CompanionStyle.MUTED,false);
        tools.addView(status,new LinearLayout.LayoutParams(0,-2,1f));
        refresh=CompanionStyle.button(this,"Refresh",false);
        LinearLayout.LayoutParams b=new LinearLayout.LayoutParams(dp(102),dp(42));
        b.leftMargin=dp(7);tools.addView(refresh,b);
        refresh.setOnClickListener(v->load(true));

        RecyclerView list=new RecyclerView(this);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setClipToPadding(false);
        list.setPadding(dp(14),dp(7),dp(14),dp(7));
        adapter=new NotesAdapter();
        list.setAdapter(adapter);
        root.addView(list,new LinearLayout.LayoutParams(-1,0,1f));
        more=CompanionStyle.button(this,"Load more notes",false);
        LinearLayout.LayoutParams next=new LinearLayout.LayoutParams(-1,dp(47));
        next.setMargins(dp(16),dp(5),dp(16),dp(14));
        root.addView(more,next);
        more.setOnClickListener(v->load(false));

        editorHost=new LinearLayout(this);
        editorHost.setOrientation(LinearLayout.VERTICAL);
        editorHost.setPadding(dp(15),dp(9),dp(15),dp(10));
        root.addView(editorHost);
        editorHost.setVisibility(View.GONE);
        if(!targetId.isEmpty()){
            // A targeted profile must never allow a blank overwrite while
            // unsearched pages might still contain an existing note.
            list.setVisibility(View.GONE);
            editorHost.setVisibility(View.VISIBLE);
            showTargetWaiting();
        }
        updateButtons();
    }
    private void updateButtons(){
        refresh.setEnabled(!busy);
        more.setEnabled(!busy);
        more.setVisibility(hasMore?View.VISIBLE:View.GONE);
        if(!targetId.isEmpty()&&hasMore){
            more.setText("Check older notes");
        }else more.setText("Load more notes");
    }
    private void showTargetWaiting(){
        editorHost.removeAllViews();
        editorHost.addView(text("Checking VRChat for an existing note before allowing edits…",
            14,CompanionStyle.MUTED,false));
    }
    private void load(boolean reset){
        if(busy||!StaffGate.unlocked(this))return;
        if(reset){
            offset=0;hasMore=true;editorReady=false;notes.clear();
            adapter.notifyDataSetChanged();
            if(!targetId.isEmpty())showTargetWaiting();
        }
        if(!hasMore)return;
        final int requested=offset;
        busy=true;updateButtons();
        status.setText("Loading private notes…");
        worker.execute(()->{
            try{
                JSONArray data=VRChatApi.userNotes(this,requested);
                ArrayList<JSONObject> batch=new ArrayList<>();
                for(int i=0;i<data.length();i++){
                    JSONObject item=data.optJSONObject(i);
                    if(item!=null)batch.add(item);
                }
                runOnUiThread(()->{
                    if(isFinishing()||isDestroyed()||!StaffGate.unlocked(this))return;
                    busy=false;
                    for(JSONObject item:batch){
                        String noteId=item.optString("id","");
                        boolean duplicate=false;
                        for(JSONObject old:notes){
                            if(!noteId.isEmpty()&&old.optString("id","").equals(noteId)){
                                duplicate=true;break;
                            }
                        }
                        if(!duplicate)notes.add(item);
                    }
                    adapter.notifyDataSetChanged();
                    offset=requested+100;
                    hasMore=batch.size()>=100 && offset<=10000;
                    status.setText(notes.size()+" notes loaded"+(hasMore?" · More available":""));
                    if(!targetId.isEmpty()){
                        JSONObject match=findTarget();
                        if(match!=null){hasMore=false;showEditor(match);}
                        else if(!hasMore){showEditor(null);}
                        else showTargetWaiting();
                    }
                    updateButtons();
                });
            }catch(Exception ex){
                final String error=ex.getMessage()==null?"Please retry.":ex.getMessage();
                runOnUiThread(()->{
                    busy=false;
                    status.setText("Couldn't load notes: "+error.substring(0,Math.min(100,error.length())));
                    updateButtons();
                });
            }
        });
    }
    private JSONObject findTarget(){
        for(JSONObject item:notes){
            if(targetId.equals(item.optString("targetUserId","")))return item;
        }
        return null;
    }
    private void showEditor(JSONObject existing){
        if(!StaffGate.unlocked(this)){finish();return;}
        editorReady=true;
        editorHost.removeAllViews();
        editorHost.addView(CompanionStyle.section(this,
            existing==null?"NEW PRIVATE NOTE":"EDIT PRIVATE NOTE"));
        EditText input=CompanionStyle.input(this,"Write a private VRChat note");
        input.setSingleLine(false);
        input.setMinLines(4);
        input.setMaxLines(8);
        input.setText(existing==null?"":existing.optString("note",""));
        input.setSelection(input.length());
        editorHost.addView(input,new LinearLayout.LayoutParams(-1,dp(160)));
        TextView help=text("This note is saved to your personal VRChat account, not to the TGC relay.",
            12,CompanionStyle.MUTED,false);
        help.setPadding(0,dp(9),0,dp(10));editorHost.addView(help);
        Button save=CompanionStyle.button(this,"Save note to VRChat",true);
        editorHost.addView(save,CompanionStyle.buttonParams(this));
        save.setOnClickListener(v->{
            if(!editorReady||busy)return;
            String body=input.getText().toString().trim();
            if(body.isEmpty()||body.length()>2000){
                status.setText("Please enter 1–2000 characters.");return;
            }
            new AlertDialog.Builder(this)
                .setTitle("Save private VRChat note?")
                .setMessage("This updates your private note for "+targetName+
                    " on your VRChat account. It will be available on other devices signed into that account.")
                .setNegativeButton("Cancel",null)
                .setPositiveButton("Save",(dialog,which)->save(body,save)).show();
        });
    }
    private void save(String body,Button action){
        if(busy||!StaffGate.unlocked(this))return;
        busy=true;action.setEnabled(false);updateButtons();status.setText("Saving note…");
        worker.execute(()->{
            try{
                VRChatApi.saveUserNote(this,targetId,body);
                runOnUiThread(()->{
                    if(isFinishing()||isDestroyed())return;
                    busy=false;
                    status.setText("Note saved to your VRChat account.");
                    action.setEnabled(true);updateButtons();
                    // Refresh on demand only to avoid making unnecessary API calls.
                });
            }catch(Exception e){
                String reason=e.getMessage()==null?"Please retry.":e.getMessage();
                final String safe=reason.substring(0,Math.min(100,reason.length()));
                runOnUiThread(()->{
                    busy=false;action.setEnabled(true);updateButtons();
                    status.setText("Couldn't save: "+safe+" (your text is still here)");
                });
            }
        });
    }
    private final class Holder extends RecyclerView.ViewHolder{
        final TextView title,summary;
        Holder(LinearLayout root,TextView name,TextView preview){
            super(root);title=name;summary=preview;
        }
    }
    private final class NotesAdapter extends RecyclerView.Adapter<Holder>{
        @Override public Holder onCreateViewHolder(ViewGroup parent,int type){
            LinearLayout card=CompanionStyle.card(VRChatNotesActivity.this);
            RecyclerView.LayoutParams p=new RecyclerView.LayoutParams(-1,-2);
            p.bottomMargin=dp(9);card.setLayoutParams(p);
            TextView name=text("",16,CompanionStyle.TEXT,true);
            TextView preview=text("",13,CompanionStyle.MUTED,false);
            preview.setMaxLines(3);
            card.addView(name);card.addView(preview);
            return new Holder(card,name,preview);
        }
        @Override public void onBindViewHolder(Holder holder,int position){
            JSONObject note=notes.get(position);
            JSONObject target=note.optJSONObject("targetUser");
            String name=target==null?"VRChat player":target.optString("displayName","VRChat player");
            String user=note.optString("targetUserId","");
            holder.title.setText(name);
            holder.summary.setText(note.optString("note",""));
            holder.itemView.setOnClickListener(validUser(user)?v->{
                if(!StaffGate.unlocked(VRChatNotesActivity.this)){finish();return;}
                startActivity(new Intent(VRChatNotesActivity.this,VRChatNotesActivity.class)
                    .putExtra("target_id",user).putExtra("target_name",name));
            }:null);
        }
        @Override public int getItemCount(){return targetId.isEmpty()?notes.size():0;}
    }
    @Override protected void onDestroy(){worker.shutdownNow();super.onDestroy();}
}
