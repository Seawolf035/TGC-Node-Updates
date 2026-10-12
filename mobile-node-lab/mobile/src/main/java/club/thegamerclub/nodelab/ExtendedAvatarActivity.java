package club.thegamerclub.nodelab;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.LinearLayoutManager;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** VRCX-like on-device avatar collections. Requires confirmed active VRC+ subscription. */
public final class ExtendedAvatarActivity extends StaffProtectedActivity {
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final ArrayList<JSONObject> avatars=new ArrayList<>();
    private final Adapter adapter=new Adapter();
    private String accountId="",category="All",incomingAvatar="";
    private long verifiedAt=0L;
    private boolean busy=false;
    private TextView message,total;
    private Button add,collection,refresh;
    private EditText search;
    private int dp(float n){return CompanionStyle.dp(this,n);}
    private TextView text(String label,int size,int color,boolean strong){
        return CompanionStyle.text(this,label,size,color,strong);
    }
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        if(!StaffGate.unlocked(this)){finish();return;}
        String raw=getIntent().getStringExtra("avatar_id");
        if(ExtendedAvatarStore.validAvatar(raw))incomingAvatar=raw;
        render();
    }
    @Override protected void onResume(){
        super.onResume();
        if(!StaffGate.unlocked(this)){finish();return;}
        if(System.currentTimeMillis()-verifiedAt>5*60_000L)verify();
    }
    private void render(){
        getWindow().setStatusBarColor(CompanionStyle.BG);
        getWindow().setNavigationBarColor(CompanionStyle.BG);
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(CompanionStyle.BG);
        setContentView(root);
        CompanionStyle.safeArea(this,root);

        LinearLayout header=new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(13),dp(9),dp(14),dp(8));root.addView(header);
        Button back=CompanionStyle.button(this,"‹",false);back.setTextSize(25);
        header.addView(back,new LinearLayout.LayoutParams(dp(50),dp(45)));
        back.setOnClickListener(v->finish());
        TextView heading=text("Extended avatar favorites",21,CompanionStyle.TEXT,true);
        heading.setPadding(dp(12),0,0,0);
        heading.setMaxLines(2);
        header.addView(heading,new LinearLayout.LayoutParams(0,-2,1f));
        TextView sub=text("Local collections for your personal VRChat account • VRC+ required",
            12,CompanionStyle.MUTED,false);
        sub.setPadding(dp(17),dp(6),dp(17),dp(9));root.addView(sub);

        message=text("Checking VRC+ membership…",13,CompanionStyle.MUTED,false);
        message.setPadding(dp(16),dp(8),dp(16),dp(6));
        root.addView(message);
        LinearLayout filters=new LinearLayout(this);filters.setGravity(Gravity.CENTER_VERTICAL);
        filters.setPadding(dp(14),dp(4),dp(14),dp(8));root.addView(filters);
        collection=CompanionStyle.button(this,"All categories ▾",false);
        filters.addView(collection,new LinearLayout.LayoutParams(0,dp(43),1f));
        collection.setOnClickListener(v->chooseCategory());
        add=CompanionStyle.button(this,"+ Save avatar",true);
        LinearLayout.LayoutParams a=new LinearLayout.LayoutParams(dp(143),dp(43));
        a.leftMargin=dp(7);filters.addView(add,a);
        add.setOnClickListener(v->promptAdd(incomingAvatar));
        search=CompanionStyle.input(this,"Search saved avatars or creators");
        LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,dp(49));
        sp.setMargins(dp(14),dp(4),dp(14),dp(7));root.addView(search,sp);
        search.addTextChangedListener(new android.text.TextWatcher(){
            public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            public void onTextChanged(CharSequence s,int start,int before,int count){reload();}
            public void afterTextChanged(android.text.Editable e){}
        });
        LinearLayout bar=new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);bar.setPadding(dp(15),0,dp(15),dp(7));
        root.addView(bar);
        total=text("Checking collection...",12,CompanionStyle.MUTED,false);
        bar.addView(total,new LinearLayout.LayoutParams(0,-2,1f));
        refresh=CompanionStyle.button(this,"Refresh",false);
        bar.addView(refresh,new LinearLayout.LayoutParams(dp(100),dp(40)));
        refresh.setOnClickListener(v->verify());
        RecyclerView list=new RecyclerView(this);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setClipToPadding(false);
        list.setPadding(dp(12),0,dp(12),dp(13));
        list.setAdapter(adapter);
        root.addView(list,new LinearLayout.LayoutParams(-1,0,1f));
        controls();
    }
    private void controls(){
        boolean allowed=!accountId.isEmpty()&&!busy&&StaffGate.unlocked(this);
        add.setEnabled(allowed);
        collection.setEnabled(allowed);
        search.setEnabled(allowed);
        refresh.setEnabled(!busy);
    }
    private void verify(){
        if(busy||!StaffGate.unlocked(this))return;
        busy=true;
        accountId="";
        verifiedAt=0;
        avatars.clear();adapter.notifyDataSetChanged();
        message.setText("Checking your VRC+ subscription with VRChat…");
        controls();
        worker.execute(()->{
            String id="",error=null;
            try{id=VRChatApi.requireActiveVRCPlus(this);}
            catch(Exception e){error=e.getMessage();}
            final String actual=id,issue=error;
            runOnUiThread(()->{
                if(isFinishing()||isDestroyed()||!StaffGate.unlocked(this))return;
                busy=false;
                if(issue!=null){
                    message.setText(issue.startsWith("An active VRC+")?issue:
                        "Could not verify VRC+. Check your connection and try Refresh.");
                    total.setText("Extended favorites are locked");
                    controls();return;
                }
                accountId=actual;verifiedAt=System.currentTimeMillis();
                message.setText("✓ VRC+ active • Your local collection is unlocked");
                controls();
                reload();
                if(!incomingAvatar.isEmpty()){
                    String idToAdd=incomingAvatar;
                    incomingAvatar="";
                    promptAdd(idToAdd);
                }
            });
        });
    }
    private void reload(){
        if(accountId.isEmpty())return;
        try(ExtendedAvatarStore db=new ExtendedAvatarStore(this)){
            JSONArray list=db.list(accountId,category,search.getText().toString());
            avatars.clear();
            for(int i=0;i<list.length();i++){
                JSONObject record=list.optJSONObject(i);
                if(record!=null)avatars.add(record);
            }
            adapter.notifyDataSetChanged();
            total.setText(db.total(accountId)+" saved avatars • "+avatars.size()+" shown");
            collection.setText(category+" ▾");
        }catch(Exception e){
            message.setText("Couldn't load your local collection.");
        }
    }
    private void chooseCategory(){
        if(accountId.isEmpty()||busy)return;
        try(ExtendedAvatarStore db=new ExtendedAvatarStore(this)){
            List<String> names=db.categories(accountId);
            names.add(0,"All");
            new AlertDialog.Builder(this).setTitle("Filter by category")
                .setItems(names.toArray(new String[0]),(d,w)->{
                    category=names.get(w);reload();
                }).setNegativeButton("Cancel",null).show();
        }catch(Exception e){message.setText("Couldn't load categories.");}
    }
    private void promptAdd(String preset){
        if(busy||accountId.isEmpty()||!StaffGate.unlocked(this))return;
        LinearLayout form=new LinearLayout(this);form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(16),dp(8),dp(16),0);
        TextView hint=text("Save an avatar by ID or copy it from normal VRChat favorites. This does not use a VRChat favorite slot.",
            13,CompanionStyle.MUTED,false);
        form.addView(hint);
        EditText avatar=CompanionStyle.input(this,"avtr_...");
        avatar.setText(preset==null?"":preset);
        LinearLayout.LayoutParams field=new LinearLayout.LayoutParams(-1,dp(55));
        field.topMargin=dp(11);form.addView(avatar,field);
        EditText group=CompanionStyle.input(this,"Category (e.g. Sharks)");
        group.setText(category.equals("All")?"General":category);
        LinearLayout.LayoutParams gp=new LinearLayout.LayoutParams(-1,dp(55));
        gp.topMargin=dp(11);form.addView(group,gp);
        AlertDialog dialog=new AlertDialog.Builder(this)
            .setTitle("Save to Extended Favorites")
            .setView(form).setNegativeButton("Cancel",null)
            .setPositiveButton("Save",null).create();
        dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            String id=avatar.getText().toString().trim();
            String cat=group.getText().toString().trim();
            if(!ExtendedAvatarStore.validAvatar(id)){avatar.setError("Enter a valid VRChat avatar ID");return;}
            try{ExtendedAvatarStore.category(cat);}
            catch(Exception ex){group.setError(ex.getMessage());return;}
            dialog.dismiss();
            addAvatar(id,cat);
        }));
        dialog.show();
    }
    private void addAvatar(String id,String cat){
        if(busy||accountId.isEmpty())return;
        busy=true;controls();
        message.setText("Checking avatar and VRC+ membership…");
        final String original=accountId;
        worker.execute(()->{
            String error=null;
            try{
                String verified=VRChatApi.requireActiveVRCPlus(this);
                if(!original.equals(verified))throw new SecurityException("VRChat account changed. Refresh this screen.");
                JSONObject detail=VRChatApi.avatar(this,id);
                if(!id.equals(detail.optString("id","")))
                    throw new SecurityException("VRChat returned a different avatar.");
                try(ExtendedAvatarStore db=new ExtendedAvatarStore(this)){
                    db.add(original,detail,cat);
                }
            }catch(Exception ex){error=ex.getMessage();}
            final String issue=error;
            runOnUiThread(()->{
                busy=false;if(isFinishing()||isDestroyed())return;
                message.setText(issue==null?"Avatar saved in Extended Favorites.":
                    "Couldn't save avatar: "+issue);
                controls();
                if(issue==null)reload();
            });
        });
    }
    private void inspect(JSONObject item){
        if(accountId.isEmpty()||busy||!StaffGate.unlocked(this))return;
        String id=item.optString("id","");
        if(!ExtendedAvatarStore.validAvatar(id))return;
        busy=true;controls();message.setText("Loading avatar details…");
        worker.execute(()->{
            JSONObject detail=null;String error=null;
            try{detail=VRChatApi.avatar(this,id);}
            catch(Exception ex){error=ex.getMessage();}
            final JSONObject profile=detail;final String issue=error;
            runOnUiThread(()->{
                busy=false;controls();
                if(isFinishing()||isDestroyed())return;
                if(profile==null){
                    message.setText("Avatar is unavailable on VRChat: "+issue+
                        ". It remains in your local library until you remove it.");
                    showUnavailable(item);return;
                }
                showActions(profile);
            });
        });
    }
    private void showUnavailable(JSONObject item){
        new AlertDialog.Builder(this).setTitle(item.optString("name","Unavailable avatar"))
            .setMessage("VRChat doesn't currently allow this avatar's details to be retrieved. You can remove the local bookmark.")
            .setNegativeButton("Keep",null)
            .setPositiveButton("Remove bookmark",(d,w)->confirmRemove(item)).show();
    }
    private void showActions(JSONObject avatar){
        String id=avatar.optString("id","");
        String name=avatar.optString("name","Avatar");
        String[] actions={"Use this avatar","Move to a category","Remove from Extended Favorites"};
        new AlertDialog.Builder(this).setTitle(name)
            .setMessage("By "+avatar.optString("authorName","Unknown")+
                "\n\n"+avatar.optString("description","")+
                "\n\nOnly your local extended bookmark is managed here.")
            .setItems(actions,(d,selected)->{
                if(selected==0)confirmUse(id,name);
                else if(selected==1)promptMove(id);
                else confirmRemove(avatar);
            })
            .setNegativeButton("Close",null).show();
    }
    private void confirmUse(String id,String name){
        new AlertDialog.Builder(this)
            .setTitle("Use "+name+"?")
            .setMessage("Change your personal VRChat account to this avatar?")
            .setNegativeButton("Cancel",null)
            .setPositiveButton("Change avatar",(d,w)->runAction("Updating your avatar…",true,
                id,null)).show();
    }
    private void promptMove(String id){
        EditText field=CompanionStyle.input(this,"Category");
        try(ExtendedAvatarStore db=new ExtendedAvatarStore(this)){
            JSONArray list=db.list(accountId,"All","");
            for(int i=0;i<list.length();i++){
                JSONObject obj=list.optJSONObject(i);
                if(obj!=null&&id.equals(obj.optString("id",""))){
                    field.setText(obj.optString("category","General"));break;
                }
            }
        }catch(Exception ignored){}
        LinearLayout root=new LinearLayout(this);
        root.setPadding(dp(18),dp(15),dp(18),dp(2));root.addView(field);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Move to category")
            .setView(root).setNegativeButton("Cancel",null).setPositiveButton("Move",null).create();
        dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            String name=field.getText().toString();
            try{ExtendedAvatarStore.category(name);}catch(Exception e){field.setError(e.getMessage());return;}
            dialog.dismiss();runAction("Moving avatar…",false,id,name);
        }));
        dialog.show();
    }
    private void confirmRemove(JSONObject avatar){
        String id=avatar.optString("id","");
        new AlertDialog.Builder(this).setTitle("Remove local bookmark?")
            .setMessage("Remove "+avatar.optString("name","this avatar")+
                " from your extended collection? Your normal VRChat favorites are unaffected.")
            .setNegativeButton("Cancel",null)
            .setPositiveButton("Remove",(d,w)->runAction("Removing bookmark…",false,id,"__REMOVE__"))
            .show();
    }
    private void runAction(String heading,boolean select,String avatarId,String group){
        if(busy||accountId.isEmpty())return;
        busy=true;message.setText(heading);controls();
        final String current=accountId;
        worker.execute(()->{
            String failure=null;
            try{
                String verified=VRChatApi.requireActiveVRCPlus(this);
                if(!verified.equals(current))throw new SecurityException("VRChat account changed. Refresh.");
                if(select)VRChatApi.selectAvatar(this,avatarId);
                else try(ExtendedAvatarStore db=new ExtendedAvatarStore(this)){
                    if("__REMOVE__".equals(group))db.remove(current,avatarId);
                    else db.move(current,avatarId,group);
                }
            }catch(Exception e){failure=e.getMessage();}
            final String error=failure;
            runOnUiThread(()->{
                busy=false;if(isFinishing()||isDestroyed())return;
                message.setText(error==null?(select?"Avatar changed.":"Extended favorites updated."):
                    "Couldn't complete action: "+error);
                controls();if(error==null)reload();
            });
        });
    }
    private final class Holder extends RecyclerView.ViewHolder{
        final ImageView image;final TextView name,description;
        Holder(LinearLayout card,ImageView image,TextView name,TextView description){
            super(card);this.image=image;this.name=name;this.description=description;
        }
    }
    private final class Adapter extends RecyclerView.Adapter<Holder>{
        @Override public Holder onCreateViewHolder(ViewGroup parent,int type){
            LinearLayout row=CompanionStyle.card(ExtendedAvatarActivity.this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            RecyclerView.LayoutParams p=new RecyclerView.LayoutParams(-1,-2);
            p.bottomMargin=dp(8);row.setLayoutParams(p);
            ImageView image=new ImageView(ExtendedAvatarActivity.this);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            image.setClipToOutline(true);
            image.setBackground(CompanionStyle.shape(ExtendedAvatarActivity.this,CompanionStyle.RAISED,11,0));
            row.addView(image,new LinearLayout.LayoutParams(dp(65),dp(65)));
            LinearLayout labels=new LinearLayout(ExtendedAvatarActivity.this);
            labels.setOrientation(LinearLayout.VERTICAL);
            labels.setPadding(dp(12),0,dp(6),0);
            row.addView(labels,new LinearLayout.LayoutParams(0,-2,1f));
            TextView name=text("",15,CompanionStyle.TEXT,true);name.setMaxLines(2);
            name.setEllipsize(TextUtils.TruncateAt.END);labels.addView(name);
            TextView desc=text("",12,CompanionStyle.MUTED,false);
            desc.setMaxLines(2);labels.addView(desc);
            row.addView(text("›",25,CompanionStyle.ACCENT,false));
            return new Holder(row,image,name,desc);
        }
        @Override public void onBindViewHolder(Holder h,int pos){
            JSONObject avatar=avatars.get(pos);
            h.name.setText(avatar.optString("name","Avatar"));
            h.description.setText(avatar.optString("category","General")+
                " · "+avatar.optString("authorName","Unknown creator"));
            CompanionImages.load(h.image,CompanionImages.preferred(avatar,"thumbnailImageUrl","imageUrl"));
            h.itemView.setOnClickListener(v->inspect(avatar));
        }
        @Override public int getItemCount(){return avatars.size();}
    }
    @Override protected void onDestroy(){worker.shutdownNow();super.onDestroy();}
}
