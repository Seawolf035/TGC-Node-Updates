package club.thegamerclub.nodelab;

import android.app.AlertDialog;
import android.widget.EditText;
import android.text.TextWatcher;
import android.text.Editable;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Personal avatar favorites gallery with explicitly confirmed avatar selection. */
public final class VRChatAvatarActivity extends StaffProtectedActivity {
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final ArrayList<JSONObject> avatars=new ArrayList<>();
    private final AvatarAdapter adapter=new AvatarAdapter();
    private TextView status;
    private Button groupPicker,refreshButton;
    private AvatarFavoritesRepository.Snapshot favoriteLists;
    private String selectedGroup="all";
    private String statusWarning="";
    private final java.util.HashSet<String> savedExtended=new java.util.HashSet<>();
    private boolean pickerMode=false;
    private EditText filter;
    private boolean loading=false;
    private int dp(float n){return CompanionStyle.dp(this,n);}
    private TextView text(String val,int sp,int color,boolean strong){
        return CompanionStyle.text(this,val,sp,color,strong);
    }
    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        if(!StaffGate.unlocked(this)){finish();return;}
        pickerMode=getIntent().getBooleanExtra("extended_picker",false);
        render();fetch();
    }
    @Override protected void onResume(){
        super.onResume();
        if(!StaffGate.unlocked(this)){finish();return;}
        refreshExtendedSaved();
    }
    private void render(){
        getWindow().setStatusBarColor(CompanionStyle.BG);
        getWindow().setNavigationBarColor(CompanionStyle.BG);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(CompanionStyle.BG);setContentView(root);
        CompanionStyle.safeArea(this,root);
        LinearLayout head=new LinearLayout(this);head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(dp(15),dp(11),dp(15),dp(11));root.addView(head);
        Button back=CompanionStyle.button(this,"‹",false);back.setTextSize(25);
        head.addView(back,new LinearLayout.LayoutParams(dp(51),dp(47)));
        back.setOnClickListener(v->finish());
        TextView heading=text(pickerMode?"Choose an avatar":"Favorite avatars",
            22,CompanionStyle.TEXT,true);
        heading.setPadding(dp(13),0,0,0);head.addView(heading);
        Button extended=CompanionStyle.button(this,"Local avatar collections • VRC+",true);
        LinearLayout.LayoutParams extendedP=CompanionStyle.buttonParams(this);
        extendedP.setMargins(dp(15),dp(4),dp(15),dp(3));
        root.addView(extended,extendedP);
        extended.setVisibility(pickerMode?View.GONE:View.VISIBLE);
        extended.setOnClickListener(v->{
            if(!StaffGate.unlocked(this)){finish();return;}
            startActivity(new Intent(this,ExtendedAvatarActivity.class));
        });
        status=text("Loading VRChat favorite lists…",13,CompanionStyle.MUTED,false);
        status.setPadding(dp(17),dp(11),dp(17),dp(8));root.addView(status);
        if(pickerMode){
            TextView help=text("Tap ☆ Save beside any avatar to choose a local Extended Favorites category. No avatar ID is needed.",13,CompanionStyle.MUTED,false);
            help.setPadding(dp(17),0,dp(17),dp(8));root.addView(help);
        }
        groupPicker=CompanionStyle.button(this,"All favorite lists ▾",false);
        LinearLayout.LayoutParams gp=CompanionStyle.buttonParams(this);
        gp.setMargins(dp(15),dp(3),dp(15),dp(9));
        root.addView(groupPicker,gp);
        groupPicker.setOnClickListener(v->chooseFavoriteList());
        groupPicker.setEnabled(false);
        filter=CompanionStyle.input(this,"Search avatars across loaded lists");
        filter.setSingleLine(true);
        LinearLayout.LayoutParams fp=new LinearLayout.LayoutParams(-1,dp(52));
        fp.setMargins(dp(14),0,dp(14),dp(10));
        root.addView(filter,fp);
        filter.addTextChangedListener(new TextWatcher(){
            @Override public void beforeTextChanged(CharSequence text,int start,int count,int after){}
            @Override public void onTextChanged(CharSequence text,int start,int before,int count){
                adapter.rebuild();
                updateSummary();
            }
            @Override public void afterTextChanged(Editable text){}
        });
        RecyclerView list=new RecyclerView(this);
        list.setLayoutManager(new LinearLayoutManager(this));list.setAdapter(adapter);
        list.setPadding(dp(13),0,dp(13),dp(10));
        root.addView(list,new LinearLayout.LayoutParams(-1,0,1f));
        refreshButton=CompanionStyle.button(this,"Refresh all favorite lists",true);
        LinearLayout.LayoutParams rp=CompanionStyle.buttonParams(this);
        rp.setMargins(dp(15),0,dp(15),dp(13));root.addView(refreshButton,rp);
        refreshButton.setOnClickListener(v->fetch());
    }
    private void updateSummary(){
        if(status==null)return;
        String term=filter==null?"":filter.getText().toString().trim();
        int matching=adapter.getItemCount();
        int groupCount=favoriteLists==null?0:favoriteLists.groups.length();
        String label=term.isEmpty()?
            avatars.size()+" favorites · "+groupCount+" VRChat list"+
                (groupCount==1?"":"s"):
            matching+" matches in "+avatars.size()+" loaded avatars";
        if(!"all".equals(selectedGroup))label+=" · "+selectedGroupLabel();
        if(!statusWarning.isEmpty())label+="\n"+statusWarning;
        status.setText(label);
        status.setTextColor(statusWarning.isEmpty()?
            CompanionStyle.MUTED:CompanionStyle.GOLD);
    }
    private String selectedGroupLabel(){
        if(favoriteLists==null)return "All lists";
        for(int i=0;i<favoriteLists.groups.length();i++){
            JSONObject g=favoriteLists.groups.optJSONObject(i);
            if(g!=null&&selectedGroup.equals(g.optString("name","")))
                return g.optString("displayName",selectedGroup);
        }
        return "All lists";
    }
    private void chooseFavoriteList(){
        if(favoriteLists==null||loading)return;
        JSONArray groups=favoriteLists.groups;
        String[] names=new String[groups.length()+1];
        names[0]="All lists • "+favoriteLists.uniqueAvatars+" unique avatars";
        int selection=0;
        for(int i=0;i<groups.length();i++){
            JSONObject g=groups.optJSONObject(i);
            if(g==null){names[i+1]="Favorite list";continue;}
            String key=g.optString("name","");
            if(key.equals(selectedGroup))selection=i+1;
            JSONArray a=g.optJSONArray("avatars");
            int size=a==null?0:a.length();
            String suffix=g.optString("error","").isEmpty()?
                size+" loaded":"Could not load";
            names[i+1]=g.optString("displayName",key)+" • "+suffix;
        }
        new AlertDialog.Builder(this)
            .setTitle("VRChat favorite avatar lists")
            .setSingleChoiceItems(names,selection,(dialog,which)->{
                if(which==0)selectedGroup="all";
                else{
                    JSONObject g=groups.optJSONObject(which-1);
                    if(g!=null)selectedGroup=g.optString("name","all");
                }
                dialog.dismiss();
                updateVisibleAvatars();
            })
            .setNegativeButton("Cancel",null).show();
    }
    private void updateVisibleAvatars(){
        avatars.clear();
        if(favoriteLists!=null){
            java.util.HashSet<String> seen=new java.util.HashSet<>();
            for(int i=0;i<favoriteLists.groups.length();i++){
                JSONObject g=favoriteLists.groups.optJSONObject(i);
                if(g==null)continue;
                if(!"all".equals(selectedGroup)&&
                    !selectedGroup.equals(g.optString("name","")))continue;
                JSONArray items=g.optJSONArray("avatars");
                if(items==null)continue;
                for(int j=0;j<items.length();j++){
                    JSONObject avatar=items.optJSONObject(j);
                    if(avatar==null)continue;
                    String id=avatar.optString("id","");
                    if(id.matches("avtr_[0-9a-fA-F-]{36}")&&seen.add(id))
                        avatars.add(avatar);
                }
            }
        }
        adapter.rebuild();
        if(groupPicker!=null)
            groupPicker.setText("all".equals(selectedGroup)?
                "All favorite lists ▾":selectedGroupLabel()+" ▾");
        updateSummary();
    }
    private void fetch(){
        if(loading||!StaffGate.unlocked(this))return;
        loading=true;
        refreshButton.setEnabled(false);
        groupPicker.setEnabled(false);
        status.setText("Loading your VRChat avatar lists…");
        worker.execute(()->{
            AvatarFavoritesRepository.Snapshot data=null;
            String issue="";
            try{data=AvatarFavoritesRepository.fetch(this);}
            catch(Exception e){issue=e.getMessage()==null?
                "Please check your connection and VRChat login.":e.getMessage();}
            final AvatarFavoritesRepository.Snapshot result=data;
            final String error=issue;
            runOnUiThread(()->{
                loading=false;
                if(isFinishing()||isDestroyed()||!StaffGate.unlocked(this))return;
                refreshButton.setEnabled(true);
                if(result==null){
                    statusWarning="Could not refresh lists: "+error;
                    updateSummary();return; // Previous loaded groups remain available.
                }
                favoriteLists=result;
                boolean groupPresent="all".equals(selectedGroup);
                for(int i=0;i<result.groups.length();i++){
                    JSONObject g=result.groups.optJSONObject(i);
                    if(g!=null&&selectedGroup.equals(g.optString("name","")))
                        groupPresent=true;
                }
                if(!groupPresent)selectedGroup="all";
                statusWarning=result.warning;
                groupPicker.setEnabled(result.groups.length()>0);
                updateVisibleAvatars();
            });
        });
    }
    private void inspect(JSONObject avatar){
        String id=avatar.optString("id","");
        if(!id.matches("avtr_[0-9a-fA-F-]{36}"))return;
        status.setText("Loading avatar information...");
        worker.execute(()->{
            JSONObject detail;
            try{detail=VRChatApi.avatar(this,id);}
            catch(Exception e){detail=avatar;}
            JSONObject data=detail;
            runOnUiThread(()->{
                if(!StaffGate.unlocked(this)){finish();return;}
                String name=data.optString("name","Avatar");
                String description=data.optString("description","");
                String author=data.optString("authorName","Unknown creator");
                StringBuilder msg=new StringBuilder("By "+author);
                if(!description.isEmpty())msg.append("\n\n").append(description);
                msg.append("\n\nRelease: ").append(data.optString("releaseStatus","unknown"));
                msg.append("\nPlatforms depend on the avatar's uploaded variants.");
                new AlertDialog.Builder(this)
                    .setTitle(name)
                    .setMessage(msg.toString())
                    .setNegativeButton("Close",null)
                    .setNeutralButton(savedExtended.contains(id)?
                        "Move in Extended":"Save to Extended",(d,w)->{
                        if(!StaffGate.unlocked(this)){finish();return;}
                        saveToExtended(data);
                    })
                    .setPositiveButton("Use this avatar",(d,w)->confirmSelection(id,name))
                    .show();
                updateSummary();
            });
        });
    }
    /** The Saved badges are read only from this account's private local database. */
    private void refreshExtendedSaved(){
        if(!StaffGate.unlocked(this))return;
        worker.execute(()->{
            java.util.HashSet<String> next=new java.util.HashSet<>();
            try{
                JSONObject session=VRChatSession.load(this);
                String user=session==null?"":session.optString("userId","");
                try(ExtendedAvatarStore db=new ExtendedAvatarStore(this)){
                    next=db.savedIds(user);
                }
            }catch(Exception ignored){}
            final java.util.HashSet<String> snapshot=next;
            runOnUiThread(()->{
                if(isFinishing()||isDestroyed()||!StaffGate.unlocked(this))return;
                savedExtended.clear();
                savedExtended.addAll(snapshot);
                adapter.notifyDataSetChanged();
            });
        });
    }
    private void saveToExtended(JSONObject avatar){
        if(!StaffGate.unlocked(this)){finish();return;}
        ExtendedAvatarSaveFlow.open(this,avatar,worker,this::refreshExtendedSaved);
    }
    private void confirmSelection(String id,String name){
        new AlertDialog.Builder(this).setTitle("Change your VRChat avatar?")
            .setMessage("Switch your personal VRChat account to "+name+"? This is an account-changing action.")
            .setNegativeButton("Cancel",null)
            .setPositiveButton("Change avatar",(d,w)->{
                if(!StaffGate.unlocked(this)){finish();return;}
                status.setText("Updating avatar...");
                worker.execute(()->{
                    String response;
                    try{VRChatApi.selectAvatar(this,id);response="Avatar updated successfully.";}
                    catch(Exception ex){response="Couldn't change avatar: "+ex.getMessage();}
                    final String msg=response;
                    runOnUiThread(()->{if(status!=null)status.setText(msg);});
                });
            }).show();
    }
    private final class Holder extends RecyclerView.ViewHolder{
        final TextView title,author;
        final ImageView image;
        final Button save;
        Holder(LinearLayout row,TextView title,TextView author,
            ImageView image,Button save){
            super(row);this.title=title;this.author=author;this.image=image;
            this.save=save;
        }
    }
    private final class AvatarAdapter extends RecyclerView.Adapter<Holder>{
        private final ArrayList<JSONObject> shown=new ArrayList<>();
        void rebuild(){
            shown.clear();
            String search=filter==null?"":filter.getText().toString().trim().toLowerCase(java.util.Locale.ROOT);
            for(JSONObject item:avatars){
                if(search.isEmpty()||
                    item.optString("name","").toLowerCase(java.util.Locale.ROOT).contains(search)||
                    item.optString("authorName","").toLowerCase(java.util.Locale.ROOT).contains(search))
                    shown.add(item);
            }
            notifyDataSetChanged();
        }
        @Override public Holder onCreateViewHolder(ViewGroup parent,int type){
            LinearLayout row=CompanionStyle.card(VRChatAvatarActivity.this);
            row.setOrientation(LinearLayout.HORIZONTAL);row.setGravity(Gravity.CENTER_VERTICAL);
            RecyclerView.LayoutParams p=new RecyclerView.LayoutParams(-1,-2);
            p.bottomMargin=dp(9);row.setLayoutParams(p);
            ImageView image=new ImageView(VRChatAvatarActivity.this);
            image.setBackground(CompanionStyle.shape(VRChatAvatarActivity.this,
                CompanionStyle.RAISED,11,0));
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);image.setClipToOutline(true);
            row.addView(image,new LinearLayout.LayoutParams(dp(65),dp(65)));
            LinearLayout labels=new LinearLayout(VRChatAvatarActivity.this);
            labels.setPadding(dp(13),0,dp(5),0);
            labels.setOrientation(LinearLayout.VERTICAL);
            row.addView(labels,new LinearLayout.LayoutParams(0,-2,1f));
            TextView title=text("",16,CompanionStyle.TEXT,true);
            labels.addView(title);
            TextView author=text("",12,CompanionStyle.MUTED,false);
            labels.addView(author);
            Button quickSave=CompanionStyle.button(VRChatAvatarActivity.this,"☆ Save",false);
            quickSave.setTextSize(12);
            LinearLayout.LayoutParams qp=new LinearLayout.LayoutParams(dp(93),dp(46));
            qp.rightMargin=dp(4);
            row.addView(quickSave,qp);
            return new Holder(row,title,author,image,quickSave);
        }
        @Override public void onBindViewHolder(Holder h,int position){
            JSONObject item=shown.get(position);
            h.title.setText(item.optString("name","Avatar"));
            h.author.setText("By "+item.optString("authorName","Unknown creator"));
            CompanionImages.load(h.image,CompanionImages.preferred(item,
                "thumbnailImageUrl","imageUrl"));
            String id=item.optString("id","");
            boolean saved=savedExtended.contains(id);
            h.save.setText(saved?"★ Saved":"☆ Save");
            h.save.setContentDescription(saved?
                "Move "+item.optString("name","avatar")+" in Extended Favorites":
                "Save "+item.optString("name","avatar")+" to Extended Favorites");
            h.save.setOnClickListener(v->saveToExtended(item));
            h.itemView.setOnClickListener(v->inspect(item));
        }
        @Override public int getItemCount(){return shown.size();}
    }
    @Override protected void onDestroy(){worker.shutdownNow();super.onDestroy();}
}
