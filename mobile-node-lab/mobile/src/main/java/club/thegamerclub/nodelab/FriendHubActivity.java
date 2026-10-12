package club.thegamerclub.nodelab;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Mini VRCX social hub: friends by location, chronological friend log and local activity. */
public final class FriendHubActivity extends StaffProtectedActivity {
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private final Handler handler=new Handler(Looper.getMainLooper());
    private JSONObject snapshot=new JSONObject();
    private JSONArray events=new JSONArray(),encounters=new JSONArray();
    private String view="locations",filter="all";
    private int shown=75;
    private boolean fetching=false,visible=false;
    private TextView status;
    private LinearLayout content,tabs,filters;
    private EditText query;
    private final Runnable periodic=new Runnable(){
        @Override public void run(){
            if(!visible||isFinishing()||isDestroyed())return;
            refresh(false);
            handler.postDelayed(this,120_000L);
        }
    };
    private int dp(float x){return CompanionStyle.dp(this,x);}
    private TextView text(String s,int size,int color,boolean strong){
        return CompanionStyle.text(this,s,size,color,strong);
    }
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        if(!StaffGate.unlocked(this)){finish();return;}
        String initial=getIntent().getStringExtra("tab");
        if("log".equals(initial)||"activity".equals(initial))view=initial;
        render();
        refresh(true);
    }
    @Override protected void onResume(){
        super.onResume();
        if(!StaffGate.unlocked(this)){finish();return;}
        visible=true;
        handler.removeCallbacks(periodic);
        handler.postDelayed(periodic,120_000L);
    }
    @Override protected void onPause(){
        visible=false;handler.removeCallbacks(periodic);
        super.onPause();
    }
    @Override protected void onDestroy(){
        handler.removeCallbacks(periodic);
        io.shutdownNow();super.onDestroy();
    }
    private void button(LinearLayout parent,String label,boolean primary,Runnable onClick){
        Button b=CompanionStyle.button(this,label,primary);
        parent.addView(b,CompanionStyle.buttonParams(this));
        b.setOnClickListener(v->onClick.run());
    }
    private void render(){
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(CompanionStyle.BG);
        root.setPadding(dp(14),dp(8),dp(14),dp(12));
        setContentView(root);CompanionStyle.safeArea(this,root);
        LinearLayout head=new LinearLayout(this);head.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(head);
        Button back=CompanionStyle.button(this,"‹ Back",false);
        head.addView(back,new LinearLayout.LayoutParams(dp(95),dp(44)));
        back.setOnClickListener(v->finish());
        TextView title=text("Friends & activity",22,CompanionStyle.TEXT,true);
        title.setPadding(dp(12),0,0,0);
        head.addView(title);
        tabs=new LinearLayout(this);tabs.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(tabs);
        query=CompanionStyle.input(this,"Filter friends, events or worlds");
        LinearLayout.LayoutParams qp=new LinearLayout.LayoutParams(-1,dp(48));
        qp.setMargins(0,dp(9),0,dp(8));
        root.addView(query,qp);
        query.addTextChangedListener(new android.text.TextWatcher(){
            public void beforeTextChanged(CharSequence x,int a,int c,int d){}
            public void onTextChanged(CharSequence x,int a,int b,int c){shown=75;draw();}
            public void afterTextChanged(android.text.Editable x){}
        });
        status=text("Loading your friend data…",12,CompanionStyle.MUTED,false);
        root.addView(status);
        filters=new LinearLayout(this);filters.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(filters);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(false);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1f));
        content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0,dp(8),0,dp(20));scroll.addView(content);
        button(root,"Refresh friends",false,()->refresh(true));
        draw();
    }
    private void rowTab(LinearLayout host,String label,String key){
        Button b=CompanionStyle.button(this,label,view.equals(key));
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(0,dp(44),1f);
        params.setMargins(dp(2),dp(4),dp(2),dp(2));
        host.addView(b,params);
        b.setTextSize(12);
        b.setOnClickListener(v->{view=key;shown=75;draw();});
    }
    private void chipFilter(String title,String type){
        Button b=CompanionStyle.button(this,title,filter.equals(type));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(39),1f);
        p.setMargins(dp(2),dp(6),dp(2),dp(4));
        filters.addView(b,p);b.setTextSize(11);
        b.setOnClickListener(v->{filter=type;shown=75;draw();});
    }
    private void refresh(boolean force){
        if(fetching||!StaffGate.unlocked(this))return;
        fetching=true;
        status.setText("Updating online and offline friends…");
        io.execute(()->{
            String error=null;
            int changes=0;
            JSONObject stored=new JSONObject();JSONArray log=new JSONArray(),recent=new JSONArray();
            try{
                JSONObject session=VRChatSession.load(this);
                FriendLogStore.account(session);
                // No partial pagination may be committed to the history.
                if(force || System.currentTimeMillis()-FriendLogStore.lastPoll(this,session)>=110_000L){
                    JSONArray friends=VRChatApi.friendSnapshot(this);
                    changes=FriendLogStore.observe(this,session,friends);
                }
                stored=FriendLogStore.snapshot(this,session);
                log=FriendLogStore.events(this,session);
            }catch(Exception e){
                error=e.getMessage();
                // Retain previously observed data during a transient API outage.
                try{
                    JSONObject session=VRChatSession.load(this);
                    stored=FriendLogStore.snapshot(this,session);
                    log=FriendLogStore.events(this,session);
                }catch(Exception ignored){}
            }
            try(MobileOutbox outbox=new MobileOutbox(this)){
                recent=outbox.recentEncounters(100);
            }catch(Exception ignored){}
            final JSONObject result=stored;final JSONArray logResult=log,encounterResult=recent;
            final String problem=error;final int recorded=changes;
            runOnUiThread(()->{
                fetching=false;
                if(isFinishing()||isDestroyed()||!StaffGate.unlocked(this))return;
                snapshot=result;events=logResult;encounters=encounterResult;
                status.setText(problem!=null?"Showing saved history; update failed: "+problem:
                    "Updated · "+snapshot.length()+" friends · "+events.length()+" saved events"+
                        (recorded>0?" · "+recorded+" new changes":""));
                draw();
            });
        });
    }
    private boolean match(String value){
        String term=query==null?"":query.getText().toString().trim().toLowerCase(Locale.US);
        return term.isEmpty()||value.toLowerCase(Locale.US).contains(term);
    }
    private LinearLayout card(){
        LinearLayout c=CompanionStyle.card(this);
        content.addView(c,CompanionStyle.cardParams(this));
        return c;
    }
    private void profile(String id){
        if(!StaffGate.unlocked(this)){finish();return;}
        if(!id.matches("usr_[0-9a-fA-F-]{36}"))return;
        startActivity(new Intent(this,VRChatDetailActivity.class)
            .putExtra("kind","user").putExtra("id",id));
    }
    private String date(long time){
        return DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(new Date(time));
    }
    private void draw(){
        if(content==null||tabs==null)return;
        tabs.removeAllViews();
        rowTab(tabs,"Locations","locations");
        rowTab(tabs,"Activity","activity");
        rowTab(tabs,"Friend log","log");
        filters.removeAllViews();
        if(view.equals("log")){
            chipFilter("All","all");chipFilter("Presence","presence");
            chipFilter("Worlds","worlds");chipFilter("Friends","friends");
        }
        content.removeAllViews();
        switch(view){
            case "locations":drawLocations();break;
            case "activity":drawActivity();break;
            default:drawLog();break;
        }
    }
    private void empty(String explanation){
        LinearLayout c=card();
        c.addView(text(explanation,14,CompanionStyle.MUTED,false));
    }
    private void drawLocations(){
        Map<String,ArrayList<JSONObject>> places=new LinkedHashMap<>();
        java.util.Iterator<String> ids=snapshot.keys();
        while(ids.hasNext()){
            String id=ids.next();JSONObject friend=snapshot.optJSONObject(id);
            if(friend==null||!friend.optBoolean("online",false))continue;
            String place=friend.optString("location","unknown");
            if(place.equals("private")||place.equals("unknown")||place.isEmpty())
                place="Private / hidden location";
            try{
                JSONObject entry=new JSONObject(friend.toString()).put("userId",id);
                if(!places.containsKey(place))places.put(place,new ArrayList<>());
                places.get(place).add(entry);
            }catch(Exception ignored){}
        }
        if(places.isEmpty()){
            empty("No online friends observed yet. Refresh while signed in to your personal VRChat account.");
            return;
        }
        ArrayList<String> locations=new ArrayList<>(places.keySet());
        Collections.sort(locations,(a,b)->Integer.compare(places.get(b).size(),places.get(a).size()));
        int displayed=0;
        for(String location:locations){
            ArrayList<JSONObject> group=places.get(location);
            ArrayList<JSONObject> matching=new ArrayList<>();
            for(JSONObject f:group){
                if(match(location+" "+f.optString("name")+" "+f.optString("status")))matching.add(f);
            }
            if(matching.isEmpty())continue;
            if(displayed++>=shown)break;
            LinearLayout box=card();
            String label=location.startsWith("wrld_")?"World instance":"Location";
            box.addView(CompanionStyle.section(this,label+" · "+group.size()+" friends"));
            TextView heading=text(location,13,CompanionStyle.ACCENT,true);
            heading.setTextIsSelectable(true);box.addView(heading);
            if(location.startsWith("wrld_")&&location.contains(":")){
                final String target=location;
                button(box,"Instance details",false,()->{
                    if(!StaffGate.unlocked(this))return;
                    try{VRChatApi.normalizeLocation(target);}
                    catch(Exception e){return;}
                    startActivity(new Intent(this,VRChatInstanceActivity.class)
                        .putExtra("location",target));
                });
            }
            for(JSONObject f:matching){
                String id=f.optString("userId","");
                TextView name=text("› "+f.optString("name",id)+"  ·  "+f.optString("status",""),
                    15,CompanionStyle.TEXT,false);
                name.setPadding(0,dp(9),0,dp(9));
                box.addView(name);
                name.setOnClickListener(v->profile(id));
            }
        }
        if(displayed==0)empty("No friends or locations match the search.");
        else if(locations.size()>shown)button(content,"Show more locations",false,()->{shown+=75;draw();});
    }
    private ArrayList<JSONObject> matchingEvents(boolean includeEncounters){
        ArrayList<JSONObject> result=new ArrayList<>();
        for(int i=0;i<events.length();i++){
            JSONObject e=events.optJSONObject(i);
            if(e==null)continue;
            String type=e.optString("type","");
            if(!includeEncounters){
                if(filter.equals("presence")&&! (type.equals("online")||type.equals("offline")))continue;
                if(filter.equals("worlds")&&!type.equals("location"))continue;
                if(filter.equals("friends")&&! (type.equals("friend_added")||type.equals("friend_removed")||type.equals("name")))continue;
            }
            if(match(e.optString("name")+" "+type+" "+e.optString("location")+" "+e.optString("status")))
                result.add(e);
        }
        if(includeEncounters){
            for(int i=0;i<encounters.length();i++){
                JSONObject e=encounters.optJSONObject(i);
                if(e==null)continue;
                JSONObject item=new JSONObject();
                try{
                    item.put("name",e.optString("display_name","Player"))
                        .put("userId",e.optString("user_id",""))
                        .put("time",e.optLong("seen_at"))
                        .put("type","encounter")
                        .put("visits",e.optInt("visits",1));
                    if(match(item.optString("name")+" encounter"))result.add(item);
                }catch(Exception ignored){}
            }
        }
        Collections.sort(result,(a,b)->Long.compare(b.optLong("time",0),a.optLong("time",0)));
        return result;
    }
    private String summary(JSONObject item){
        String type=item.optString("type","");
        switch(type){
            case "online":return "Came online";
            case "offline":return "Went offline";
            case "location":return "Location changed to "+item.optString("location","unknown");
            case "status":return "Changed status to "+item.optString("status","unknown");
            case "statusDescription":return "Updated status message";
            case "name":return "Display name changed";
            case "friend_added":return "Added to friends";
            case "friend_removed":return "No longer on friends list";
            case "encounter":return "Recently encountered · "+item.optInt("visits",1)+" recorded visit(s)";
            default:return type;
        }
    }
    private void drawEvents(boolean activity){
        ArrayList<JSONObject> list=matchingEvents(activity);
        if(list.isEmpty()){
            empty("No recorded events match this view. The Friend Log begins collecting after its first successful refresh; it cannot recover earlier activity.");
            return;
        }
        for(int i=0;i<Math.min(shown,list.size());i++){
            JSONObject event=list.get(i);
            LinearLayout box=card();
            String id=event.optString("userId","");
            TextView who=text(event.optString("name",id),16,CompanionStyle.TEXT,true);
            box.addView(who);
            who.setOnClickListener(v->profile(id));
            box.addView(text(summary(event),13,CompanionStyle.MUTED,false));
            long at=event.optLong("time",0);
            if(at>0)box.addView(text(date(at),12,CompanionStyle.ACCENT,false));
        }
        if(list.size()>shown)button(content,"Load more events",false,()->{shown+=75;draw();});
    }
    private void drawLog(){drawEvents(false);}
    private void drawActivity(){drawEvents(true);}
}
