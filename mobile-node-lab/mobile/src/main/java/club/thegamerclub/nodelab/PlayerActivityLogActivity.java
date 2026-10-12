package club.thegamerclub.nodelab;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.DateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Local, VRCX-style player information from the existing Mobile Node watcher.
 * No second log reader and no VRChat API credentials are needed to view stored log data.
 */
public final class PlayerActivityLogActivity extends StaffProtectedActivity {
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final Handler handler=new Handler(Looper.getMainLooper());
    private JSONArray active=new JSONArray(),events=new JSONArray(),encounters=new JSONArray();
    private boolean busy=false,visible=false,nodeRunning=false;
    private int limit=80;
    private String mode="current";
    private LinearLayout entries,tabs;
    private TextView state;
    private EditText search;
    private final Runnable update=new Runnable(){
        @Override public void run(){
            if(!visible||isFinishing()||isDestroyed())return;
            reload();handler.postDelayed(this,10_000L);
        }
    };
    private int dp(float p){return CompanionStyle.dp(this,p);}
    private TextView text(String value,int size,int color,boolean strong){
        return CompanionStyle.text(this,value,size,color,strong);
    }
    @Override public void onCreate(Bundle stateBundle){
        super.onCreate(stateBundle);
        if(!StaffGate.unlocked(this)){finish();return;}
        String incoming=getIntent().getStringExtra("tab");
        if("timeline".equals(incoming)||"encounters".equals(incoming))mode=incoming;
        build();
        reload();
    }
    @Override protected void onResume(){
        super.onResume();
        visible=true;
        handler.removeCallbacks(update);
        handler.postDelayed(update,10_000L);
    }
    @Override protected void onPause(){
        visible=false;handler.removeCallbacks(update);super.onPause();
    }
    @Override protected void onDestroy(){
        handler.removeCallbacks(update);worker.shutdownNow();super.onDestroy();
    }
    private void build(){
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(CompanionStyle.BG);
        root.setPadding(dp(14),dp(8),dp(14),dp(8));
        setContentView(root);
        CompanionStyle.safeArea(this,root);
        LinearLayout bar=new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);root.addView(bar);
        Button back=CompanionStyle.button(this,"‹ Back",false);
        bar.addView(back,new LinearLayout.LayoutParams(dp(90),dp(44)));
        back.setOnClickListener(v->finish());
        TextView heading=text("Player activity",21,CompanionStyle.TEXT,true);
        heading.setPadding(dp(12),0,0,0);
        bar.addView(heading,new LinearLayout.LayoutParams(0,-2,1));

        state=text("Reading saved events…",12,CompanionStyle.MUTED,false);
        state.setPadding(0,dp(10),0,dp(8));root.addView(state);
        tabs=new LinearLayout(this);
        root.addView(tabs);
        search=CompanionStyle.input(this,"Search players, event types and worlds");
        LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,dp(46));
        sp.topMargin=dp(7);sp.bottomMargin=dp(8);
        root.addView(search,sp);
        search.addTextChangedListener(new TextWatcher(){
            public void beforeTextChanged(CharSequence s,int a,int c,int d){}
            public void onTextChanged(CharSequence s,int a,int b,int c){limit=80;redraw();}
            public void afterTextChanged(Editable x){}
        });
        ScrollView scroll=new ScrollView(this);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        entries=new LinearLayout(this);entries.setOrientation(LinearLayout.VERTICAL);
        entries.setPadding(0,dp(5),0,dp(12));
        scroll.addView(entries);
        Button refresh=CompanionStyle.button(this,"Refresh",false);
        root.addView(refresh,CompanionStyle.buttonParams(this));
        refresh.setOnClickListener(v->reload());
        redraw();
    }
    private void tab(String label,String key){
        Button b=CompanionStyle.button(this,label,mode.equals(key));
        b.setTextSize(11);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(42),1f);
        p.setMargins(dp(2),0,dp(2),0);
        tabs.addView(b,p);
        b.setOnClickListener(v->{mode=key;limit=80;redraw();});
    }
    private void reload(){
        if(busy||!StaffGate.unlocked(this))return;
        busy=true;
        worker.execute(()->{
            JSONArray current=new JSONArray(),history=new JSONArray(),seen=new JSONArray();
            String error="";
            try(MobileOutbox store=new MobileOutbox(this)){
                current=store.currentPlayers(250);
                history=store.recentLocalActivity(300);
                seen=store.recentEncounters(100);
            }catch(Exception e){error=e.getClass().getSimpleName();}
            android.content.SharedPreferences status=getSharedPreferences("tgc_mobile_node_status",0);
            long pulse=status.getLong("service_heartbeat_at",0L);
            boolean running=status.getBoolean("running",false)&&pulse>0L &&
                System.currentTimeMillis()-pulse<90_000L;
            final JSONArray players=current,logs=history,enc=seen;
            final String problem=error;
            runOnUiThread(()->{
                busy=false;
                if(isFinishing()||isDestroyed()||!StaffGate.unlocked(this))return;
                active=players;events=logs;encounters=enc;nodeRunning=running;
                state.setText(problem.isEmpty()?
                    (running?"● Monitoring logs · ":"○ Monitoring is off · ")+
                    active.length()+" observed players · "+events.length()+" recent log events":
                    "Saved log history is unavailable: "+problem);
                state.setTextColor(running?CompanionStyle.GOOD:CompanionStyle.GOLD);
                redraw();
            });
        });
    }
    private boolean match(String value){
        return value.toLowerCase(Locale.US).contains(search==null?"":search.getText().toString().trim().toLowerCase(Locale.US));
    }
    private void info(String title,String details,Runnable action){
        LinearLayout c=CompanionStyle.card(this);
        entries.addView(c,CompanionStyle.cardParams(this));
        c.addView(text(title,15,CompanionStyle.TEXT,true));
        TextView sub=text(details,12,CompanionStyle.MUTED,false);
        sub.setPadding(0,dp(5),0,0);c.addView(sub);
        if(action!=null){c.setOnClickListener(v->action.run());c.addView(text("View profile  ›",12,CompanionStyle.ACCENT,false));}
    }
    private void profile(String id){
        if(!StaffGate.unlocked(this))return;
        if(id==null||!id.matches("usr_[0-9a-fA-F-]{36}"))return;
        startActivity(new Intent(this,VRChatDetailActivity.class)
            .putExtra("kind","user").putExtra("id",id));
    }
    private String time(long at){
        return at<=0?"Time unknown":DateFormat.getDateTimeInstance(
            DateFormat.SHORT,DateFormat.SHORT).format(new Date(at));
    }
    private void redraw(){
        if(entries==null||tabs==null)return;
        tabs.removeAllViews();
        tab("Players","current");
        tab("Join / Leave","timeline");
        tab("Encounters","encounters");
        entries.removeAllViews();
        if("current".equals(mode)){
            info("Players observed in this instance",
                (nodeRunning?"Tracking is active. ":"Tracking is paused; this list isn't live. ")+
                "This is not necessarily everyone in the instance. Players who joined before tracking began may be missing.",null);
            int shown=0;
            for(int i=0;i<active.length()&&shown<limit;i++){
                JSONObject p=active.optJSONObject(i);if(p==null)continue;
                String name=p.optString("display_name",""),id=p.optString("user_id","");
                if(name.isEmpty())name=id;
                if(!match(name+" "+id+" "+p.optString("location","")))continue;
                String title=name;
                info(title,"Joined (observed) "+time(p.optLong("time"))+
                    (p.optString("location","").isEmpty()?"":"\n"+p.optString("location")),()->profile(id));
                shown++;
            }
            if(shown==0)info("No observed players yet",
                "Start tracking while playing VRChat Mobile to collect joins. Earlier players aren't automatically discovered.",null);
        }else if("timeline".equals(mode)){
            int shown=0;
            for(int i=0;i<events.length()&&shown<limit;i++){
                JSONObject e=events.optJSONObject(i);if(e==null)continue;
                String kind=e.optString("kind","");
                String name=e.optString("display_name",""),id=e.optString("user_id","");
                String location=e.optString("location","");
                if(!match(kind+" "+name+" "+id+" "+location))continue;
                String label="join".equals(kind)?"Joined":("leave".equals(kind)?"Left":"Entered a world");
                String title=kind.equals("instance")?"World / instance changed":
                    label+" · "+(name.isEmpty()?id:name);
                info(title,time(e.optLong("time"))+
                    (location.isEmpty()?"":"\n"+location),
                    id.matches("usr_[0-9a-fA-F-]{36}")?()->profile(id):null);
                shown++;
            }
            if(shown==0)info("No join or leave events recorded",
                "New events appear here after tracking begins. Past sessions cannot be reconstructed.",null);
            if(shown>=limit)addMore();
        }else{
            int shown=0;
            for(int i=0;i<encounters.length()&&shown<limit;i++){
                JSONObject e=encounters.optJSONObject(i);if(e==null)continue;
                String name=e.optString("display_name",""),id=e.optString("user_id","");
                if(name.isEmpty())name=id;
                if(!match(name+" "+id))continue;
                info(name,"Seen "+e.optInt("count",0)+" times · Last seen "+e.optString("last_seen",""),
                    ()->profile(id));
                shown++;
            }
            if(shown==0)info("No encounters yet","Your saved player encounters will appear here.",null);
        }
    }
    private void addMore(){
        Button more=CompanionStyle.button(this,"Show more",false);
        entries.addView(more,CompanionStyle.buttonParams(this));
        more.setOnClickListener(v->{limit+=80;redraw();});
    }
}
