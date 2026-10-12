package club.thegamerclub.nodelab;

import android.app.AlertDialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Native picker: only sends invites after choosing a friend and confirming. */
public final class VRChatPeoplePickerActivity extends StaffProtectedActivity {
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final ArrayList<JSONObject> friends=new ArrayList<>();
    private final ArrayList<JSONObject> shown=new ArrayList<>();
    private String location;
    private TextView status;
    private EditText search;
    private RecyclerView list;
    private final PickerAdapter adapter=new PickerAdapter();
    private int dp(float n){return CompanionStyle.dp(this,n);}
    private TextView text(String s,int size,int color,boolean bold){
        return CompanionStyle.text(this,s,size,color,bold);
    }
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);
        if(!StaffGate.unlocked(this)){finish();return;}
        location=getIntent().getStringExtra("location");
        try{VRChatApi.normalizeLocation(location);}
        catch(Exception ex){finish();return;}
        render();load();
    }
    @Override protected void onResume(){super.onResume();if(!StaffGate.unlocked(this))finish();}
    private void render(){
        getWindow().setStatusBarColor(CompanionStyle.BG);
        getWindow().setNavigationBarColor(CompanionStyle.BG);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(CompanionStyle.BG);setContentView(root);
        CompanionStyle.safeArea(this,root);
        LinearLayout bar=new LinearLayout(this);bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(15),dp(10),dp(15),dp(10));root.addView(bar);
        Button back=CompanionStyle.button(this,"‹",false);
        bar.addView(back,new LinearLayout.LayoutParams(dp(52),dp(46)));
        back.setOnClickListener(v->finish());
        TextView title=text("Invite a friend",23,CompanionStyle.TEXT,true);
        title.setPadding(dp(12),0,0,0);bar.addView(title);
        search=CompanionStyle.input(this,"Find a friend");
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(54));
        p.setMargins(dp(16),dp(8),dp(16),dp(8));root.addView(search,p);
        search.addTextChangedListener(new TextWatcher(){
            @Override public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            @Override public void onTextChanged(CharSequence s,int start,int before,int count){applyFilter();}
            @Override public void afterTextChanged(Editable s){}
        });
        status=text("Loading your friends...",13,CompanionStyle.MUTED,false);
        status.setPadding(dp(17),dp(9),dp(17),dp(9));root.addView(status);
        list=new RecyclerView(this);list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(adapter);list.setPadding(dp(13),0,dp(13),dp(9));
        root.addView(list,new LinearLayout.LayoutParams(-1,0,1f));
    }
    private void load(){
        worker.execute(()->{
            final ArrayList<JSONObject> results=new ArrayList<>();
            int successful=0;
            String error="";
            for(boolean offline:new boolean[]{false,true}){
                try{
                    JSONArray json=VRChatApi.friends(this,offline);
                    successful++;
                    for(int i=0;i<json.length();i++){
                        JSONObject item=json.optJSONObject(i);
                        if(item==null)continue;
                        String id=item.optString("id","");
                        boolean exists=false;
                        for(JSONObject other:results)
                            if(id.equals(other.optString("id")))exists=true;
                        if(!exists)results.add(item);
                    }
                }catch(Exception e){error=e.getMessage();}
            }
            final int read=successful;
            final String fail=error;
            runOnUiThread(()->{
                if(isFinishing()||isDestroyed()||!StaffGate.unlocked(this))return;
                friends.clear();friends.addAll(results);
                friends.sort((a,b)->a.optString("displayName","").compareToIgnoreCase(
                    b.optString("displayName","")));
                applyFilter();
                if(read==0)status.setText("Couldn't load friends: "+fail);
                else status.setText(results.size()+" friends available to invite"+
                    (read<2?" (partial list)":""));
            });
        });
    }
    private void applyFilter(){
        String query=search==null?"":search.getText().toString().toLowerCase(Locale.US).trim();
        shown.clear();
        for(JSONObject friend:friends){
            if(friend.optString("displayName","").toLowerCase(Locale.US).contains(query))
                shown.add(friend);
        }
        adapter.notifyDataSetChanged();
    }
    private void confirm(JSONObject friend){
        String name=friend.optString("displayName","Friend");
        String id=friend.optString("id","");
        if(!id.matches("usr_[0-9a-fA-F-]{36}"))return;
        new AlertDialog.Builder(this)
            .setTitle("Invite "+name+"?")
            .setMessage("Send this friend an invitation to your selected VRChat instance?")
            .setNegativeButton("Cancel",null)
            .setPositiveButton("Send invite",(d,w)->{
                if(!StaffGate.unlocked(this)){finish();return;}
                status.setText("Sending invitation...");
                worker.execute(()->{
                    String msg;
                    try{
                        VRChatApi.sendInvite(this,id,location);
                        msg="Invite sent to "+name+".";
                    }catch(Exception e){msg="Invite failed: "+e.getMessage();}
                    String text=msg;
                    runOnUiThread(()->status.setText(text));
                });
            }).show();
    }
    private final class Holder extends RecyclerView.ViewHolder{
        final TextView name;
        final ImageView image;
        Holder(LinearLayout root,TextView name,ImageView image){
            super(root);this.name=name;this.image=image;
        }
    }
    private final class PickerAdapter extends RecyclerView.Adapter<Holder>{
        @Override public Holder onCreateViewHolder(ViewGroup parent,int viewType){
            LinearLayout row=CompanionStyle.card(VRChatPeoplePickerActivity.this);
            row.setGravity(Gravity.CENTER_VERTICAL);row.setOrientation(LinearLayout.HORIZONTAL);
            RecyclerView.LayoutParams lp=new RecyclerView.LayoutParams(-1,-2);
            lp.bottomMargin=dp(8);row.setLayoutParams(lp);
            ImageView img=new ImageView(VRChatPeoplePickerActivity.this);
            img.setScaleType(ImageView.ScaleType.CENTER_CROP);
            img.setBackground(CompanionStyle.shape(VRChatPeoplePickerActivity.this,
                CompanionStyle.RAISED,11,0));
            img.setClipToOutline(true);
            row.addView(img,new LinearLayout.LayoutParams(dp(48),dp(48)));
            TextView title=text("",16,CompanionStyle.TEXT,true);
            title.setPadding(dp(15),0,0,0);
            row.addView(title,new LinearLayout.LayoutParams(0,-2,1f));
            row.addView(text("›",25,CompanionStyle.ACCENT,false));
            return new Holder(row,title,img);
        }
        @Override public void onBindViewHolder(Holder holder,int position){
            JSONObject user=shown.get(position);
            holder.name.setText(user.optString("displayName","VRChat user"));
            CompanionImages.load(holder.image,CompanionImages.preferred(user,
                "iconUrl","userIcon","currentAvatarThumbnailImageUrl"));
            holder.itemView.setOnClickListener(v->confirm(user));
        }
        @Override public int getItemCount(){return shown.size();}
    }
    @Override protected void onDestroy(){worker.shutdownNow();super.onDestroy();}
}
