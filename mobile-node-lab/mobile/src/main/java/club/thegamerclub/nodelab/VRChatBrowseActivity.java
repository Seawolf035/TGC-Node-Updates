package club.thegamerclub.nodelab;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
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
    private String lastFetchKey="";
    private int offset=0;
    private boolean busy=false,hasMore=false;
    private String activeQuery="";
    private Button moreButton,refreshButton;
    private java.util.HashSet<String> favoriteIds=new java.util.HashSet<>();
    private boolean friendsMode(){
        return mode.equals("friends_online")||mode.equals("friends_offline");
    }
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
            case "bookmarks":return "Bookmarked worlds";
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
    @Override protected void onResume(){
        super.onResume();
        if(!StaffGate.unlocked(this)){finish();return;}
        if("bookmarks".equals(mode)&&adapter!=null)load(true);
    }
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

        {
            searchField=CompanionStyle.input(this,search()?"Search VRChat by name":
                "Filter loaded "+name().toLowerCase(java.util.Locale.US));
            LinearLayout.LayoutParams inputP=new LinearLayout.LayoutParams(-1,dp(52));
            inputP.setMargins(dp(15),dp(14),dp(15),dp(4));
            root.addView(searchField,inputP);
            searchField.setSingleLine(true);
            if(!search()){
                searchField.addTextChangedListener(new android.text.TextWatcher(){
                    public void beforeTextChanged(CharSequence t,int start,int count,int after){}
                    public void afterTextChanged(android.text.Editable e){}
                    public void onTextChanged(CharSequence t,int start,int before,int count){
                        if(adapter!=null){
                            adapter.applyFilter();
                            showCount();
                        }
                    }
                });
            }
            searchField.setImeOptions(search()?EditorInfo.IME_ACTION_SEARCH:EditorInfo.IME_ACTION_DONE);
            searchField.setOnEditorActionListener((view,actionId,event)->{
                if(actionId==EditorInfo.IME_ACTION_SEARCH||actionId==EditorInfo.IME_ACTION_DONE||
                  (event!=null&&event.getKeyCode()==android.view.KeyEvent.KEYCODE_ENTER)){
                    InputMethodManager imm=(InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
                    if(imm!=null)imm.hideSoftInputFromWindow(searchField.getWindowToken(),0);
                    searchField.clearFocus();
                    if(search())load(true);
                    else{
                        adapter.applyFilter();
                        showCount();
                    }
                    return true;
                }
                return false;
            });
        }

        LinearLayout controls=new LinearLayout(this);controls.setGravity(Gravity.CENTER_VERTICAL);
        controls.setPadding(dp(16),dp(10),dp(16),dp(10));root.addView(controls);
        count=text(search()?"Enter a name to search":"Loading...",13,CompanionStyle.MUTED,false);
        controls.addView(count,new LinearLayout.LayoutParams(0,-2,1f));
        refreshButton=CompanionStyle.button(this,search()?"Search":"Refresh",false);
        controls.addView(refreshButton,new LinearLayout.LayoutParams(dp(105),dp(42)));
        refreshButton.setOnClickListener(v->load(true));

        RecyclerView list=new RecyclerView(this);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setClipToPadding(false);
        list.setPadding(dp(14),dp(2),dp(14),dp(12));
        adapter=new ResultsAdapter();
        list.setAdapter(adapter);
        root.addView(list,new LinearLayout.LayoutParams(-1,0,1f));
        moreButton=CompanionStyle.button(this,"Load more",false);
        LinearLayout.LayoutParams moreP=new LinearLayout.LayoutParams(-1,dp(46));
        moreP.setMargins(dp(15),dp(4),dp(15),dp(12));
        root.addView(moreButton,moreP);
        moreButton.setOnClickListener(v->load(false));
        updatePaging();
        if(!search())restoreInitial();
    }
    /** Start with encrypted account-scoped first page, then fetch only if stale. */
    private void restoreInitial(){
        if(!friendsMode()&&!mode.equals("groups")&&!mode.equals("worlds")&&
           !mode.equals("recent")){load(true);return;}
        io.execute(()->{
            favoriteIds=VRChatFavoriteIndex.savedIds(this);
            CompanionCache.Record local=CompanionCache.read(this,"browse_"+mode);
            // The full friendship snapshot, collected by other Companion
            // components, is preferable to re-fetching another paginated view.
            boolean completeFriends=false;
            if(friendsMode()){
                CompanionCache.Record shared=CompanionCache.read(this,"friends");
                if(shared!=null&&(local==null||shared.updatedAt>local.updatedAt)){
                    local=shared;completeFriends=true;
                }
            }
            final boolean fullFriendSnapshot=completeFriends;
            JSONArray items=local==null?null:local.body.optJSONArray("items");
            ArrayList<JSONObject> rows=new ArrayList<>();
            if(items!=null)for(int i=0;i<items.length();i++){
                JSONObject item=items.optJSONObject(i);
                if(item==null)continue;
                if(fullFriendSnapshot){
                    boolean online=item.optBoolean("isOnline",false);
                    if(mode.equals("friends_online")&&!online)continue;
                    if(mode.equals("friends_offline")&&online)continue;
                }
                rows.add(item);
            }
            boolean fresh=local!=null&&local.fresh(friendsMode()?300_000L:600_000L);
            runOnUiThread(()->{
                if(isFinishing()||isDestroyed()||!StaffGate.unlocked(this))return;
                if(!rows.isEmpty()){
                    adapter.replace(rows);
                    hasMore=!fullFriendSnapshot&&paged()&&
                        rows.size()>=VRChatApi.LIST_PAGE_SIZE;
                    offset=hasMore?VRChatApi.LIST_PAGE_SIZE:0;
                    showCount();updatePaging();
                }
                if(!fresh)load(true);
            });
        });
    }
    private void showCount(){
        if(count==null||adapter==null||busy)return;
        int shown=adapter.getItemCount(),loaded=adapter.loadedCount();
        count.setText(loaded==0?"No results yet":
            (shown==loaded?loaded+" results":shown+" shown of "+loaded+" loaded")+
            (hasMore?" · More available":""));
    }
    private boolean paged(){
        return !"groups".equals(mode)&&!"encounters".equals(mode)&&
            !"bookmarks".equals(mode);
    }
    private void updatePaging(){
        if(moreButton==null)return;
        moreButton.setVisibility(paged()&&hasMore?View.VISIBLE:View.GONE);
        moreButton.setEnabled(!busy);
        refreshButton.setEnabled(!busy);
    }
    /** Manual pagination avoids bursts of VRChat requests and keeps long lists usable. */
    private void load(boolean reset){
        if(!StaffGate.unlocked(this)){finish();return;}
        if(busy)return;
        final String term=search()?searchField.getText().toString().trim():"";
        if(search()&&term.length()<2){
            count.setText("Enter at least two characters.");return;
        }
        if(!reset && (!hasMore || !paged()))return;
        int requestedOffset=reset||!term.equals(activeQuery)?0:offset;
        String key=mode+"|"+term+"|"+requestedOffset;
        if(!"bookmarks".equals(mode)&&key.equals(lastFetchKey)&&
            System.currentTimeMillis()-lastFetch<10_000L){
            count.setText("Up to date. You can refresh again in a few seconds.");return;
        }
        if(reset||!term.equals(activeQuery)){
            // Preserve the current list until replacement data arrives.
            offset=0;activeQuery=term;
        }
        final int requested=offset;
        final boolean append=requested>0;
        final String requestedTerm=term;
        lastFetchKey=key;
        lastFetch=System.currentTimeMillis();
        busy=true;updatePaging();
        count.setText(append?"Loading more results…":"Loading…");
        io.execute(()->{
            try{
                JSONArray data;
                if(mode.equals("groups"))data=VRChatApi.groups(this);
                else if(mode.equals("bookmarks"))data=WorldBookmarkStore.bookmarks(this);
                else if(mode.equals("encounters")){
                    try(MobileOutbox outbox=new MobileOutbox(this)){
                        data=outbox.recentEncounters(100);
                    }
                }else data=VRChatApi.browsePage(this,mode,requestedTerm,requested);
                if(friendsMode()&&requested==0){
                    try{VRChatFavoriteIndex.refreshIfStale(this);}
                    catch(Exception ignored){/* Sort from cached favorite index. */}
                    favoriteIds=VRChatFavoriteIndex.savedIds(this);
                }
                if(!append&&!search()&&(friendsMode()||mode.equals("groups")||
                    mode.equals("worlds")||mode.equals("recent")))
                    try{CompanionCache.saveArray(this,"browse_"+mode,data);}
                    catch(Exception ignored){}
                ArrayList<JSONObject> rows=new ArrayList<>();
                for(int i=0;i<data.length();i++){
                    JSONObject item=data.optJSONObject(i);if(item==null)continue;
                    if(mode.equals("friends_online")||mode.equals("friends_offline")){
                        String location=item.optString("location","");
                        String presence=item.optString("status","");
                        boolean offline=location.equalsIgnoreCase("offline")||
                            presence.equalsIgnoreCase("offline");
                        if(mode.equals("friends_online")&&offline)continue;
                        if(mode.equals("friends_offline")&&!offline)continue;
                    }
                    rows.add(item);
                }
                final boolean next=paged()&&data.length()==VRChatApi.LIST_PAGE_SIZE &&
                    requested+VRChatApi.LIST_PAGE_SIZE<=10000;
                runOnUiThread(()->{
                    if(isFinishing()||isDestroyed()||!StaffGate.unlocked(this))return;
                    busy=false;
                    if(append)adapter.append(rows);
                    else adapter.replace(rows);
                    offset=paged()?requested+VRChatApi.LIST_PAGE_SIZE:0;
                    hasMore=next;
                    showCount();
                    updatePaging();
                });
            }catch(Exception e){
                String message=e.getMessage();if(message==null)message="Please try again.";
                final String safe=message.substring(0,Math.min(130,message.length()));
                runOnUiThread(()->{
                    busy=false;
                    count.setText("Couldn't load results: "+safe);
                    updatePaging();
                });
            }
        });
    }
    private static String imageFor(JSONObject item,String kind){
        if(kind.equals("user"))return CompanionImages.preferred(item,
            "iconUrl","userIcon","currentAvatarThumbnailImageUrl","currentAvatarImageUrl");
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
            if(friendsMode()&&favoriteIds.contains(e.id))e.title="★ "+e.title;
        }else if(mode.equals("groups")){
            e.kind="group";e.glyph="◈";e.id=item.optString("groupId",item.optString("id",""));
            try{
                if(GroupToolkitStore.contains(this,e.id))e.title="★ "+e.title;
            }catch(Exception ignored){}
            String code=item.optString("shortCode","");
            e.description=(code.isEmpty()?"":code+"  ·  ")+item.optInt("memberCount",0)+" members";
        }else if(mode.equals("worlds")||mode.equals("recent")||
                 mode.equals("search_worlds")||mode.equals("bookmarks")){
            e.kind="world";e.glyph="◇";
            boolean bookmarked=false;
            try{bookmarked=WorldBookmarkStore.contains(this,e.id);}
            catch(Exception ignored){}
            if(bookmarked)e.title="★ "+e.title;
            e.description=mode.equals("bookmarks")?"Local world bookmark":
                item.optInt("capacity",0)+" capacity";
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
        private final ArrayList<JSONObject> shown=new ArrayList<>();
        int loadedCount(){return data.size();}
        void applyFilter(){
            if(friendsMode()){
                java.util.Map<String,Integer> counts=
                    FriendOrdering.counts(data,favoriteIds);
                data.sort((a,b)->FriendOrdering.compareFriends(a,b,
                    favoriteIds,counts));
            }
            shown.clear();
            String phrase=(searchField==null||search())?"":searchField.getText().toString()
                .trim().toLowerCase(java.util.Locale.US);
            for(JSONObject item:data){
                Entry result=entry(item);
                if(phrase.isEmpty()||result.title.toLowerCase(java.util.Locale.US).contains(phrase)||
                    result.description.toLowerCase(java.util.Locale.US).contains(phrase))
                    shown.add(item);
            }
            notifyDataSetChanged();
        }
        void replace(ArrayList<JSONObject> newItems){
            data.clear();data.addAll(newItems);applyFilter();
        }
        void append(ArrayList<JSONObject> newItems){
            java.util.HashSet<String> known=new java.util.HashSet<>();
            for(JSONObject obj:data)known.add(key(obj));
            int added=0;
            for(JSONObject obj:newItems){
                String id=key(obj);
                if(known.add(id)){data.add(obj);added++;}
            }
            if(added>0)applyFilter();
        }
        private String key(JSONObject item){
            if(mode.equals("groups"))return item.optString("groupId",item.optString("id",""));
            if(mode.equals("encounters"))return item.optString("user_id","");
            String id=item.optString("id","");
            return id.isEmpty()?item.toString():id;
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
            Entry e=entry(shown.get(position));
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
        @Override public int getItemCount(){return shown.size();}
    }
    @Override protected void onDestroy(){io.shutdownNow();super.onDestroy();}
}
