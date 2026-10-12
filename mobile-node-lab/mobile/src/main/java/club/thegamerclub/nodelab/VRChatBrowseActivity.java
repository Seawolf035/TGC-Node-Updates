package club.thegamerclub.nodelab;

import android.content.Intent;
import android.graphics.Typeface;
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

/** Read-only native lists for the signed-in VRChat account. */
public final class VRChatBrowseActivity extends StaffProtectedActivity {
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private String mode="friends_online";
    private LinearLayout page,items;
    private TextView state;
    private EditText searchField;
    private long lastFetch=0L;
    private int dp(int n){return CompanionStyle.dp(this,n);}
    private TextView label(String text,int size,int color,boolean bold){
        return CompanionStyle.text(this,text,size,color,bold);
    }
    private void gap(LinearLayout l,int n){
        l.addView(new View(this),new LinearLayout.LayoutParams(1,dp(n)));
    }
    @Override public void onCreate(Bundle bundle){
        super.onCreate(bundle);
        if(!StaffGate.unlocked(this)){finish();return;}
        mode=getIntent().getStringExtra("mode");
        if(mode==null)mode="friends_online";
        render();
    }
    @Override protected void onResume(){
        super.onResume();if(!StaffGate.unlocked(this))finish();
    }
    private String pageTitle(){
        switch(mode){
            case "friends_online":return "Online friends";
            case "friends_offline":return "Offline friends";
            case "worlds":return "Favorite worlds";
            case "recent":return "Recent worlds";
            case "groups":return "My groups";
            case "encounters":return "Recent players";
            case "search_users":return "Find people";
            case "search_worlds":return "Find worlds";
            default:return "Explore";
        }
    }
    private boolean searching(){return "search_users".equals(mode)||"search_worlds".equals(mode);}
    private void render(){
        getWindow().setStatusBarColor(CompanionStyle.BG);
        getWindow().setNavigationBarColor(CompanionStyle.BG);
        ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(CompanionStyle.BG);
        page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(18),dp(20),dp(18),dp(27));
        scroll.addView(page);setContentView(scroll);
        CompanionStyle.safeArea(this,scroll);
        Button back=CompanionStyle.button(this,"← Explore",false);
        page.addView(back,new LinearLayout.LayoutParams(dp(122),dp(44)));
        back.setOnClickListener(v->finish());
        gap(page,18);
        page.addView(CompanionStyle.section(this,"DISCOVER"));
        page.addView(label(pageTitle(),27,CompanionStyle.TEXT,true));
        gap(page,6);
        page.addView(label(searching()?"Search your next connection or destination.":
            "Tap an item to see more, all inside the Companion.",13,CompanionStyle.MUTED,false));
        gap(page,19);
        LinearLayout tools=CompanionStyle.card(this);
        page.addView(tools,CompanionStyle.cardParams(this));
        if(searching()){
            tools.addView(CompanionStyle.section(this,"SEARCH"));
            searchField=CompanionStyle.input(this,
                "search_users".equals(mode)?"Search VRChat players":"Search VRChat worlds");
            tools.addView(searchField,new LinearLayout.LayoutParams(-1,dp(56)));
            gap(tools,13);
        }
        Button refresh=CompanionStyle.button(this,searching()?"Search":"Refresh list",true);
        tools.addView(refresh,CompanionStyle.buttonParams(this));
        refresh.setOnClickListener(v->load());
        state=label(searching()?"Enter a name to search.":"Loading your list...",
            13,CompanionStyle.MUTED,false);
        page.addView(state);gap(page,11);
        items=new LinearLayout(this);items.setOrientation(LinearLayout.VERTICAL);
        page.addView(items);
        if(!searching())load();
    }
    private void load(){
        if(!StaffGate.unlocked(this)){finish();return;}
        if(System.currentTimeMillis()-lastFetch<30_000L){
            state.setText("Please wait a moment before refreshing.");return;
        }
        final String search=searching()?searchField.getText().toString().trim():"";
        if(searching()&&search.length()<2){
            state.setText("Enter at least two characters to search.");return;
        }
        lastFetch=System.currentTimeMillis();
        state.setText("Loading...");
        io.execute(()->{
            try{
                JSONArray data;
                switch(mode){
                    case "friends_online":data=VRChatApi.friends(this,false);break;
                    case "friends_offline":data=VRChatApi.friends(this,true);break;
                    case "worlds":data=VRChatApi.worldFavorites(this);break;
                    case "recent":data=VRChatApi.recentWorlds(this);break;
                    case "groups":data=VRChatApi.groups(this);break;
                    case "search_users":data=VRChatApi.searchUsers(this,search);break;
                    case "search_worlds":data=VRChatApi.searchWorlds(this,search);break;
                    case "encounters":
                        try(MobileOutbox outbox=new MobileOutbox(this)){data=outbox.recentEncounters(80);}
                        break;
                    default:throw new IllegalArgumentException("Invalid view");
                }
                runOnUiThread(()->showItems(data));
            }catch(Exception e){
                String m=e.getMessage();if(m==null)m="Please try again.";
                final String safe=m.substring(0,Math.min(135,m.length()));
                runOnUiThread(()->state.setText("Couldn't load. "+safe));
            }
        });
    }
    private void showItems(JSONArray results){
        if(!StaffGate.unlocked(this)){finish();return;}
        items.removeAllViews();
        state.setText(results.length()==0?"Nothing here yet.":results.length()+" results");
        if(results.length()==0){
            LinearLayout empty=CompanionStyle.card(this);
            items.addView(empty,CompanionStyle.cardParams(this));
            empty.addView(label("No results yet",19,CompanionStyle.TEXT,true));
            gap(empty,7);
            empty.addView(label(searching()?"Try a different search.":"Your list is empty right now.",
                13,CompanionStyle.MUTED,false));return;
        }
        for(int i=0;i<Math.min(100,results.length());i++){
            JSONObject item=results.optJSONObject(i);
            if(item==null)continue;
            String title=item.optString("displayName",item.optString("name","Unknown"));
            String note="",kind=null,id=null,glyph="☺";
            if(mode.equals("friends_online")||mode.equals("friends_offline")||mode.equals("search_users")){
                note=item.optString("statusDescription",item.optString("status",""));
                String where=item.optString("location","");
                if(!where.isEmpty()&&!where.startsWith("wrld_"))note+=note.isEmpty()?where:" · "+where;
                String candidate=item.optString("id","");
                if(candidate.matches("usr_[0-9a-fA-F-]{36}")){kind="user";id=candidate;}
            }else if(mode.equals("worlds")||mode.equals("recent")||mode.equals("search_worlds")){
                glyph="◇";
                note="Up to "+item.optInt("capacity",0)+" players";
                String candidate=item.optString("id","");
                if(candidate.matches("wrld_[0-9a-fA-F-]{36}")){kind="world";id=candidate;}
            }else if(mode.equals("groups")){
                glyph="◈";
                note=item.optInt("memberCount",0)+" members";
                String candidate=item.optString("groupId",item.optString("id",""));
                if(candidate.matches("grp_[0-9a-fA-F-]{36}")){kind="group";id=candidate;}
            }else if(mode.equals("encounters")){
                title=item.optString("display_name","Unknown player");
                note="Seen "+item.optInt("count",0)+" times · "+item.optString("last_seen","Recently");
                String candidate=item.optString("user_id","");
                if(candidate.matches("usr_[0-9a-fA-F-]{36}")){kind="user";id=candidate;}
            }
            LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(14),dp(14),dp(12),dp(14));
            row.setBackground(CompanionStyle.shape(this,CompanionStyle.SURFACE,16,CompanionStyle.BORDER));
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
            lp.bottomMargin=dp(10);items.addView(row,lp);
            TextView avatar=label(glyph,24,CompanionStyle.ACCENT,true);
            avatar.setGravity(Gravity.CENTER);
            avatar.setBackground(CompanionStyle.shape(this,CompanionStyle.RAISED,13,0));
            row.addView(avatar,new LinearLayout.LayoutParams(dp(48),dp(48)));
            LinearLayout middle=new LinearLayout(this);middle.setOrientation(LinearLayout.VERTICAL);
            middle.setPadding(dp(13),0,dp(7),0);row.addView(middle,new LinearLayout.LayoutParams(0,-2,1f));
            TextView display=label(title,16,CompanionStyle.TEXT,true);
            display.setMaxLines(2);middle.addView(display);
            if(!note.trim().isEmpty()){
                gap(middle,5);
                TextView desc=label(note,12,CompanionStyle.MUTED,false);
                desc.setMaxLines(2);middle.addView(desc);
            }
            row.addView(label("›",27,CompanionStyle.ACCENT,false));
            if(kind!=null && id!=null){
                final String targetKind=kind,targetId=id;
                row.setOnClickListener(v->{
                    if(!StaffGate.unlocked(this)){finish();return;}
                    startActivity(new Intent(this,VRChatDetailActivity.class)
                        .putExtra("kind",targetKind).putExtra("id",targetId));
                });
            }
        }
    }
    @Override protected void onDestroy(){io.shutdownNow();super.onDestroy();}
}
