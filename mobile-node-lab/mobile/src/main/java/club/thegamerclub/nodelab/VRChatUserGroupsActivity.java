package club.thegamerclub.nodelab;

import android.content.Intent;
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
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Browse groups visible for any VRChat user under the signed-in account's
 * permissions. This is strictly read-only; group moderation is not available.
 */
public final class VRChatUserGroupsActivity extends StaffProtectedActivity {
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final ArrayList<JSONObject> groups=new ArrayList<>();
    private final ArrayList<JSONObject> shown=new ArrayList<>();
    private final GroupAdapter adapter=new GroupAdapter();
    private String userId="",userName="Player";
    private TextView summary;
    private EditText search;
    private Button refresh;
    private boolean busy=false;
    private int dp(float value){return CompanionStyle.dp(this,value);}
    private TextView text(String value,int size,int color,boolean bold){
        return CompanionStyle.text(this,value,size,color,bold);
    }
    private static boolean validUser(String id){
        return id!=null&&id.matches("usr_[0-9a-fA-F-]{36}");
    }
    private static boolean validGroup(String id){
        return id!=null&&id.matches("grp_[0-9a-fA-F-]{36}");
    }
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        if(!StaffGate.unlocked(this)){finish();return;}
        userId=getIntent().getStringExtra("user_id");
        if(!validUser(userId)){finish();return;}
        String label=getIntent().getStringExtra("user_name");
        if(label!=null&&!label.trim().isEmpty())
            userName=label.trim().substring(0,Math.min(100,label.trim().length()));
        render();load();
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
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(14),dp(8),dp(14),dp(8));
        root.addView(top);
        Button back=CompanionStyle.button(this,"‹",false);
        back.setTextSize(26);
        top.addView(back,new LinearLayout.LayoutParams(dp(49),dp(46)));
        back.setOnClickListener(v->finish());
        LinearLayout heading=new LinearLayout(this);
        heading.setOrientation(LinearLayout.VERTICAL);
        heading.setPadding(dp(12),0,0,0);
        top.addView(heading,new LinearLayout.LayoutParams(0,-2,1f));
        heading.addView(text("Visible groups",22,CompanionStyle.TEXT,true));
        TextView who=text(userName,13,CompanionStyle.MUTED,false);
        who.setMaxLines(1);who.setEllipsize(android.text.TextUtils.TruncateAt.END);
        heading.addView(who);

        TextView privacy=text(
            "Only groups this VRChat account is permitted to see are shown. Hidden memberships cannot be revealed.",
            12,CompanionStyle.MUTED,false);
        privacy.setPadding(dp(16),dp(5),dp(16),dp(10));
        root.addView(privacy);

        LinearLayout filters=new LinearLayout(this);
        filters.setPadding(dp(13),0,dp(13),dp(7));
        filters.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(filters);
        search=CompanionStyle.input(this,"Search visible groups");
        search.setSingleLine(true);
        filters.addView(search,new LinearLayout.LayoutParams(0,dp(50),1f));
        LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(dp(93),dp(46));
        rp.leftMargin=dp(7);
        refresh=CompanionStyle.button(this,"Refresh",false);
        filters.addView(refresh,rp);
        refresh.setOnClickListener(v->load());
        search.addTextChangedListener(new TextWatcher(){
            public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            public void afterTextChanged(Editable s){}
            public void onTextChanged(CharSequence s,int start,int before,int count){
                filter();
            }
        });
        summary=text("Loading visible VRChat groups…",13,CompanionStyle.MUTED,false);
        summary.setPadding(dp(16),dp(7),dp(16),dp(9));
        root.addView(summary);

        RecyclerView list=new RecyclerView(this);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(adapter);
        list.setClipToPadding(false);
        list.setPadding(dp(12),0,dp(12),dp(14));
        root.addView(list,new LinearLayout.LayoutParams(-1,0,1f));
    }
    /** Group membership responses can be flat or contain a nested group object. */
    static JSONObject visibleGroup(JSONObject membership){
        if(membership==null)return null;
        JSONObject nested=membership.optJSONObject("group");
        if(nested==null)nested=membership.optJSONObject("groupDetails");
        String groupId=membership.optString("groupId","");
        if(!validGroup(groupId) && nested!=null)
            groupId=nested.optString("id",nested.optString("groupId",""));
        if(!validGroup(groupId))groupId=membership.optString("id","");
        if(!validGroup(groupId))return null;
        JSONObject out=new JSONObject();
        String name=nested==null?"":nested.optString("name","");
        if(name.isEmpty())name=membership.optString("name",
            membership.optString("groupName",""));
        if(name.isEmpty())name="Group "+groupId.substring(4,12);
        String code=nested==null?"":nested.optString("shortCode","");
        if(code.isEmpty())code=membership.optString("shortCode","");
        String icon=nested==null?"":CompanionImages.preferred(nested,"iconUrl","bannerUrl");
        if(icon.isEmpty())icon=CompanionImages.preferred(membership,"iconUrl","bannerUrl");
        try{
            out.put("id",groupId);
            out.put("name",name);
            out.put("shortCode",code);
            out.put("iconUrl",icon);
        }catch(Exception ignored){}
        return out;
    }
    private void load(){
        if(busy||!StaffGate.unlocked(this))return;
        busy=true;
        refresh.setEnabled(false);
        summary.setText("Loading visible groups from VRChat…");
        final String target=userId;
        worker.execute(()->{
            ArrayList<JSONObject> results=new ArrayList<>();
            String error=null;
            try{
                JSONArray memberships=VRChatApi.visibleUserGroups(this,target);
                HashSet<String> seen=new HashSet<>();
                for(int i=0;i<memberships.length()&&i<5000;i++){
                    JSONObject group=visibleGroup(memberships.optJSONObject(i));
                    if(group==null)continue;
                    if(seen.add(group.optString("id","")))results.add(group);
                }
                Collections.sort(results,Comparator.comparing(
                    g->g.optString("name","").toLowerCase(Locale.ROOT)));
            }catch(Exception e){
                error=e.getMessage()==null?"Unable to load VRChat groups":e.getMessage();
            }
            final String problem=error;
            runOnUiThread(()->{
                if(isFinishing()||isDestroyed()||!StaffGate.unlocked(this))return;
                busy=false;refresh.setEnabled(true);
                if(problem!=null){
                    summary.setText("Couldn't load visible groups: "+
                        problem.substring(0,Math.min(problem.length(),140))+
                        (groups.isEmpty()?"":" · Showing previous results."));
                    return;
                }
                groups.clear();groups.addAll(results);
                filter();
            });
        });
    }
    private void filter(){
        if(summary==null||search==null)return;
        String term=search.getText().toString().trim().toLowerCase(Locale.ROOT);
        shown.clear();
        for(JSONObject item:groups){
            String title=item.optString("name","").toLowerCase(Locale.ROOT);
            String code=item.optString("shortCode","").toLowerCase(Locale.ROOT);
            if(term.isEmpty()||title.contains(term)||code.contains(term))
                shown.add(item);
        }
        adapter.notifyDataSetChanged();
        summary.setText(groups.isEmpty()?
            (busy?"Loading visible groups from VRChat…":
                "No groups are visible to your VRChat account for this player."):
            shown.size()+" shown · "+groups.size()+" visible group"+
                (groups.size()==1?"":"s"));
    }
    private final class Holder extends RecyclerView.ViewHolder {
        final ImageView image;
        final TextView name,code;
        Holder(View item,ImageView image,TextView name,TextView code){
            super(item);this.image=image;this.name=name;this.code=code;
        }
    }
    private final class GroupAdapter extends RecyclerView.Adapter<Holder>{
        @Override public Holder onCreateViewHolder(ViewGroup parent,int type){
            LinearLayout row=CompanionStyle.card(VRChatUserGroupsActivity.this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            RecyclerView.LayoutParams lp=new RecyclerView.LayoutParams(-1,-2);
            lp.bottomMargin=dp(9);
            row.setLayoutParams(lp);
            ImageView image=new ImageView(VRChatUserGroupsActivity.this);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            image.setBackground(CompanionStyle.shape(
                VRChatUserGroupsActivity.this,CompanionStyle.RAISED,11,0));
            image.setClipToOutline(true);
            row.addView(image,new LinearLayout.LayoutParams(dp(60),dp(60)));
            LinearLayout labels=new LinearLayout(VRChatUserGroupsActivity.this);
            labels.setPadding(dp(12),0,dp(4),0);
            labels.setOrientation(LinearLayout.VERTICAL);
            row.addView(labels,new LinearLayout.LayoutParams(0,-2,1f));
            TextView name=text("",16,CompanionStyle.TEXT,true);
            name.setMaxLines(2);labels.addView(name);
            TextView code=text("",12,CompanionStyle.MUTED,false);
            labels.addView(code);
            row.addView(text("›",27,CompanionStyle.ACCENT,false));
            return new Holder(row,image,name,code);
        }
        @Override public void onBindViewHolder(Holder h,int position){
            JSONObject group=shown.get(position);
            h.name.setText(group.optString("name","Group"));
            h.code.setText(group.optString("shortCode",""));
            CompanionImages.load(h.image,group.optString("iconUrl",""));
            h.itemView.setOnClickListener(v->{
                if(!StaffGate.unlocked(VRChatUserGroupsActivity.this)){finish();return;}
                String id=group.optString("id","");
                if(!validGroup(id))return;
                startActivity(new Intent(VRChatUserGroupsActivity.this,VRChatDetailActivity.class)
                    .putExtra("kind","group").putExtra("id",id));
            });
        }
        @Override public int getItemCount(){return shown.size();}
    }
    @Override protected void onDestroy(){
        worker.shutdownNow();
        super.onDestroy();
    }
}
