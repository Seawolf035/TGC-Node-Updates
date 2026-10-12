package club.thegamerclub.nodelab;

import android.app.AlertDialog;
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
    private Button more;
    private int offset=0;
    private boolean loading=false;
    private int dp(float n){return CompanionStyle.dp(this,n);}
    private TextView text(String val,int sp,int color,boolean strong){
        return CompanionStyle.text(this,val,sp,color,strong);
    }
    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        if(!StaffGate.unlocked(this)){finish();return;}
        render();fetch(false);
    }
    @Override protected void onResume(){super.onResume();if(!StaffGate.unlocked(this))finish();}
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
        TextView heading=text("Favorite avatars",22,CompanionStyle.TEXT,true);
        heading.setPadding(dp(13),0,0,0);head.addView(heading);
        Button extended=CompanionStyle.button(this,"Extended favorites • VRC+",true);
        LinearLayout.LayoutParams extendedP=CompanionStyle.buttonParams(this);
        extendedP.setMargins(dp(15),dp(4),dp(15),dp(3));
        root.addView(extended,extendedP);
        extended.setOnClickListener(v->{
            if(!StaffGate.unlocked(this)){finish();return;}
            startActivity(new Intent(this,ExtendedAvatarActivity.class));
        });
        status=text("Loading saved avatars...",13,CompanionStyle.MUTED,false);
        status.setPadding(dp(17),dp(11),dp(17),dp(12));root.addView(status);
        RecyclerView list=new RecyclerView(this);
        list.setLayoutManager(new LinearLayoutManager(this));list.setAdapter(adapter);
        list.setPadding(dp(13),0,dp(13),dp(10));
        root.addView(list,new LinearLayout.LayoutParams(-1,0,1f));
        more=CompanionStyle.button(this,"Load more avatars",false);
        LinearLayout.LayoutParams p=CompanionStyle.buttonParams(this);
        p.setMargins(dp(15),0,dp(15),dp(13));root.addView(more,p);
        more.setOnClickListener(v->fetch(true));
        Button refresh=CompanionStyle.button(this,"Refresh",true);
        LinearLayout.LayoutParams rp=CompanionStyle.buttonParams(this);
        rp.setMargins(dp(15),0,dp(15),dp(13));root.addView(refresh,rp);
        refresh.setOnClickListener(v->fetch(false));
    }
    private void fetch(boolean append){
        if(loading||!StaffGate.unlocked(this))return;
        loading=true;
        int next=append?offset:0;
        status.setText("Loading saved avatars...");
        worker.execute(()->{
            JSONArray data=null;
            String error=null;
            try{data=VRChatApi.favoriteAvatars(this,next);}
            catch(Exception e){error=e.getMessage();}
            JSONArray found=data;String issue=error;
            runOnUiThread(()->{
                loading=false;
                if(isFinishing()||isDestroyed()||!StaffGate.unlocked(this))return;
                if(found==null){
                    status.setText("Couldn't load avatars: "+issue);return;
                }
                if(!append){avatars.clear();offset=0;}
                for(int i=0;i<found.length();i++){
                    JSONObject avatar=found.optJSONObject(i);
                    if(avatar!=null)avatars.add(avatar);
                }
                offset+=found.length();
                adapter.notifyDataSetChanged();
                status.setText(avatars.size()+" favorite avatars"+
                    (avatars.size()==0?" — nothing saved yet":""));
                more.setVisibility(found.length()==60?View.VISIBLE:View.GONE);
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
                    .setNeutralButton("Save to Extended",(d,w)->{
                        if(!StaffGate.unlocked(this)){finish();return;}
                        startActivity(new Intent(this,ExtendedAvatarActivity.class)
                            .putExtra("avatar_id",id));
                    })
                    .setPositiveButton("Use this avatar",(d,w)->confirmSelection(id,name))
                    .show();
                status.setText(avatars.size()+" favorite avatars");
            });
        });
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
        Holder(LinearLayout row,TextView title,TextView author,ImageView image){
            super(row);this.title=title;this.author=author;this.image=image;
        }
    }
    private final class AvatarAdapter extends RecyclerView.Adapter<Holder>{
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
            row.addView(text("›",28,CompanionStyle.ACCENT,false));
            return new Holder(row,title,author,image);
        }
        @Override public void onBindViewHolder(Holder h,int position){
            JSONObject item=avatars.get(position);
            h.title.setText(item.optString("name","Avatar"));
            h.author.setText("By "+item.optString("authorName","Unknown creator"));
            CompanionImages.load(h.image,CompanionImages.preferred(item,
                "thumbnailImageUrl","imageUrl"));
            h.itemView.setOnClickListener(v->inspect(item));
        }
        @Override public int getItemCount(){return avatars.size();}
    }
    @Override protected void onDestroy(){worker.shutdownNow();super.onDestroy();}
}
