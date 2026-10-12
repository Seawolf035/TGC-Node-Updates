package club.thegamerclub.nodelab;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.LinearLayoutManager;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Compact Mini-VRCX browsing. Scroll lists recycle views and render actual VRChat images. */
public final class VRChatBrowseActivity extends StaffProtectedActivity {
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private String mode="friends_online";
    private TextView count;
    private EditText searchField;
    private ResultsAdapter adapter;
    private long lastFetch=0L;
    private int dp(float n){return CompanionStyle.dp(this,n);}
    private TextView text(String value,int size,int color,boolean bold){
        return CompanionStyle.text(this,value,size,color,bold);
    }
    private boolean search(){return mode.equals("search_users")||mode.equals("search_worlds");}
    private String name(){
        switch(mode){
            case "friends_online":return "Online friends";
            case "friends_offline":return "Offline friends";
            case "worlds":return "Favorite worlds";
            case "recent":return "Recently visited";
            case "groups":return "My groups";
            case "encounters":return "Recently encountered";
            case "search_users":return "Find people";
            case "search_worlds":return "Find worlds";
            default:return "Explore";
        }
    }
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);
        if(!StaffGate.unlocked(this)){finish();return;}
        mode=getIntent().getStringExtra("mode");
        if(mode==null)mode="friends_online";
        render();
    }
    @Override protected void onResume(){super.onResume();if(!StaffGate.unlocked(this))finish();}
    private void render(){
        getWindow().setStatusBarColor(CompanionStyle.BG);
        getWindow().setNavigationBarColor(CompanionStyle.BG);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(CompanionStyle.BG);
        setContentView(root);
        CompanionStyle.safeArea(this,root);

        LinearLayout top=new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(14),dp(7),dp(16),dp(5));
        root.addView(top);
        Button back=CompanionStyle.button(this,"‹",false);
        back.setTextSize(26);
        top.addView(back,new LinearLayout.LayoutParams(dp(51),dp(46)));
        back.setOnClickListener(v->finish());
        TextView heading=text(name(),23,CompanionStyle.TEXT,true);
        heading.setPadding(dp(12),0,0,0);
        top.addView(heading,new LinearLayout.LayoutParams(0,-2,1f));
        View rule=new View(this);rule.setBackgroundColor(CompanionStyle.BORDER);
        root.addView(rule,new LinearLayout.LayoutParams(-1,dp(1)));

        if(search()){
            searchField=CompanionStyle.input(this,"Search by name");
            LinearLayout.LayoutParams inputP=new LinearLayout.LayoutParams(-1,dp(52));
            inputP.setMargins(dp(15),dp(14),dp(15),dp(4));
            root.addView(searchField,inputP);
            searchField.setSingleLine(true);
        }

        LinearLayout controls=new LinearLayout(this);controls.setGravity(Gravity.CENTER_VERTICAL);
        controls.setPadding(dp(16),dp(10),dp(16),dp(10));root.addView(controls);
        count=text(search()?"Enter a name to search":"Loading...",13,CompanionStyle.MUTED,false);
        controls.addView(count,new LinearLayout.LayoutParams(0,-2,1f));
        Button refresh=CompanionStyle.button(this,search()?"Search":"Refresh",false);
        controls.addView(refresh,new LinearLayout.LayoutParams(dp(105),dp(42)));
        refresh.setOnClickListener(v->load());

        RecyclerView list=new RecyclerView(this);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setClipToPadding(false);
        list.setPadding(dp(14),dp(2),dp(14),dp(12));
        adapter=new ResultsAdapter();
        list.setAdapter(adapter);
        root.addView(list,new LinearLayout.LayoutParams(-1,0,1f));
        if(!search())load();
    }
    private void load(){
        if(!StaffGate.unlocked(this)){finish();return;}
        if(System.currentTimeMillis()-lastFetch<20_000L){
            count.setText("Please wait before refreshing.");return;
        }
        final String query=search()?searchField.getText().toString().trim():"";
        if(search()&&query.length()<2){
            count.setText("Enter at least two characters.");return;
        }
        lastFetch=System.currentTimeMillis();
        count.setText("Loading...");
        io.execute(()->{
            try{
                JSONArray data;
                switch(mode){
                    case "friends_online":data=VRChatApi.friends(this,false);break;
                    case "friends_offline":data=VRChatApi.friends(this,true);break;
                    case "worlds":data=VRChatApi.worldFavorites(this);break;
                    case "recent":data=VRChatApi.recentWorlds(this);break;
                    case "groups":data=VRChatApi.groups(this);break;
                    case "search_users":data=VRChatApi.searchUsers(this,query);break;
                    case "search_worlds":data=VRChatApi.searchWorlds(this,query);break;
                    case "encounters":
                        try(MobileOutbox outbox=new MobileOutbox(this)){data=outbox.recentEncounters(100);}
                        break;
                    default:throw new IllegalArgumentException("Unsupported view");
                }
                ArrayList<JSONObject> rows=new ArrayList<>();
                for(int i=0;i<data.length();i++){
                    JSONObject item=data.optJSONObject(i);if(item==null)continue;
                    if(mode.equals("friends_online")||mode.equals("friends_offline")){
                        String location=item.optString("location","");
                        String status=item.optString("status","");
                        boolean offline=location.equalsIgnoreCase("offline")||
                            status.equalsIgnoreCase("offline");
                        if(mode.equals("friends_online")&&offline)continue;
                        if(mode.equals("friends_offline")&&!offline)continue;
                    }
                    rows.add(item);
                }
                runOnUiThread(()->{
                    if(isFinishing()||isDestroyed()||!StaffGate.unlocked(this))return;
                    adapter.replace(rows);
                    count.setText(rows.size()==0?"No results yet":rows.size()+" results");
                });
            }catch(Exception e){
                String message=e.getMessage();
                if(message==null)message="Please try again.";
                final String error=message.substring(0,Math.min(110,message.length()));
                runOnUiThread(()->count.setText("Couldn't load: "+error));
            }
        });
    }
    private static String imageFor(JSONObject item,String kind){
        if(kind.equals("user"))return CompanionImages.preferred(item,
            "userIcon","profilePicOverride","currentAvatarThumbnailImageUrl","currentAvatarImageUrl");
        if(kind.equals("group"))return CompanionImages.preferred(item,"iconUrl","bannerUrl");
        if(kind.equals("world"))return CompanionImages.preferred(item,"thumbnailImageUrl","imageUrl");
        return "";
    }
    private static final class Entry{
        String title,description,kind,id,image,glyph;
    }
    private Entry entry(JSONObject item){
        Entry e=new Entry();
        e.title=item.optString("displayName",item.optString("name","Unknown"));
        e.description="";
        e.kind="user";e.id=item.optString("id","");e.glyph="☺";
        if(mode.equals("friends_online")||mode.equals("friends_offline")||mode.equals("search_users")){
            String status=item.optString("status","").trim();
            String description=item.optString("statusDescription","").trim();
            if(description.isEmpty())description=item.optString("bio","").trim();
            e.description=(status.isEmpty()?"":status+"  ·  ")+description;
        }else if(mode.equals("groups")){
            e.kind="group";e.glyph="◈";e.id=item.optString("groupId",item.optString("id",""));
            String code=item.optString("shortCode","");
            e.description=(code.isEmpty()?"":code+"  ·  ")+item.optInt("memberCount",0)+" members";
        }else if(mode.equals("worlds")||mode.equals("recent")||mode.equals("search_worlds")){
            e.kind="world";e.glyph="◇";e.description=item.optInt("capacity",0)+" capacity";
            String description=item.optString("description","").replace('\n',' ').trim();
            if(!description.isEmpty())e.description+="  ·  "+description;
        }else if(mode.equals("encounters")){
            e.title=item.optString("display_name","Unknown player");
            e.id=item.optString("user_id","");
            e.description=item.optInt("count",0)+" encounters  ·  "+item.optString("last_seen","");
        }
        if(e.description.length()>180)e.description=e.description.substring(0,180);
        e.image=imageFor(item,e.kind);
        return e;
    }
    private final class Holder extends RecyclerView.ViewHolder{
        final LinearLayout card;
        final ImageView photo;
        final TextView placeholder,name,description;
        Holder(LinearLayout c,ImageView image,TextView icon,TextView name,TextView description){
            super(c);card=c;photo=image;placeholder=icon;this.name=name;this.description=description;
        }
    }
    private final class ResultsAdapter extends RecyclerView.Adapter<Holder>{
        private final ArrayList<JSONObject> data=new ArrayList<>();
        void replace(ArrayList<JSONObject> newItems){
            data.clear();data.addAll(newItems);notifyDataSetChanged();
        }
        @Override public Holder onCreateViewHolder(ViewGroup parent,int type){
            LinearLayout card=new LinearLayout(VRChatBrowseActivity.this);
            card.setGravity(Gravity.CENTER_VERTICAL);
            card.setPadding(dp(10),dp(10),dp(12),dp(10));
            card.setBackground(CompanionStyle.shape(VRChatBrowseActivity.this,
                CompanionStyle.SURFACE,15,CompanionStyle.BORDER));
            RecyclerView.LayoutParams cardP=new RecyclerView.LayoutParams(-1,-2);
            cardP.bottomMargin=dp(8);card.setLayoutParams(cardP);

            FrameLayout art=new FrameLayout(VRChatBrowseActivity.this);
            art.setBackground(CompanionStyle.shape(VRChatBrowseActivity.this,
                CompanionStyle.RAISED,12,0));
            art.setClipToOutline(true);
            card.addView(art,new LinearLayout.LayoutParams(dp(60),dp(60)));
            TextView placeholder=text("☺",24,CompanionStyle.ACCENT,true);
            placeholder.setGravity(Gravity.CENTER);
            art.addView(placeholder,new FrameLayout.LayoutParams(-1,-1));
            ImageView image=new ImageView(VRChatBrowseActivity.this);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            art.addView(image,new FrameLayout.LayoutParams(-1,-1));
            LinearLayout labels=new LinearLayout(VRChatBrowseActivity.this);
            labels.setOrientation(LinearLayout.VERTICAL);
            labels.setPadding(dp(11),0,dp(7),0);
            card.addView(labels,new LinearLayout.LayoutParams(0,-2,1f));
            TextView name=text("",15,CompanionStyle.TEXT,true);
            name.setMaxLines(2);name.setEllipsize(TextUtils.TruncateAt.END);
            labels.addView(name);
            TextView desc=text("",12,CompanionStyle.MUTED,false);
            desc.setMaxLines(2);desc.setEllipsize(TextUtils.TruncateAt.END);
            labels.addView(desc);
            TextView arrow=text("›",26,CompanionStyle.ACCENT,false);
            card.addView(arrow);
            return new Holder(card,image,placeholder,name,desc);
        }
        @Override public void onBindViewHolder(Holder h,int position){
            Entry e=entry(data.get(position));
            h.name.setText(e.title);
            h.description.setText(e.description);
            h.description.setVisibility(e.description.isEmpty()?View.GONE:View.VISIBLE);
            h.placeholder.setText(e.glyph);
            CompanionImages.load(h.photo,e.image);
            String prefix=e.kind.equals("user")?"usr_":e.kind.equals("world")?"wrld_":"grp_";
            boolean valid=e.id!=null&&e.id.matches(prefix+"[0-9a-fA-F-]{36}");
            h.card.setOnClickListener(valid?v->{
                if(!StaffGate.unlocked(VRChatBrowseActivity.this)){finish();return;}
                startActivity(new Intent(VRChatBrowseActivity.this,VRChatDetailActivity.class)
                    .putExtra("kind",e.kind).putExtra("id",e.id));
            }:null);
        }
        @Override public int getItemCount(){return data.size();}
    }
    @Override protected void onDestroy(){io.shutdownNow();super.onDestroy();}
}
