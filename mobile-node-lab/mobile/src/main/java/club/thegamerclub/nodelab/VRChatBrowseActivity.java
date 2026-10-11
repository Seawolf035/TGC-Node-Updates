package club.thegamerclub.nodelab;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Button;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** VRCX-style read-only personal account views; live VRChat API data, not fake samples. */
public final class VRChatBrowseActivity extends Activity {
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private String mode="friends_online";
    private LinearLayout page;
    private TextView state;
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private TextView label(String value,int size,int color){
        TextView t=new TextView(this);t.setText(value);t.setTextColor(color);t.setTextSize(size);
        t.setPadding(0,dp(7),0,dp(7));return t;
    }
    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        mode=getIntent().getStringExtra("mode");
        if(mode==null)mode="friends_online";
        render();
    }
    @Override protected void onResume(){
        super.onResume();
        if(!StaffGate.unlocked(this)){finish();return;}
    }
    private void render(){
        ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(0xFF0B1423);
        page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(18),dp(25),dp(18),dp(25));
        scroll.addView(page);setContentView(scroll);
        page.addView(label(labelForMode(),23,0xFFE6F4FA));
        page.addView(label("Showing your own VRChat account data, not GM1 or any shared account. Refreshes are manual to respect VRChat's API limits.",12,0xFFB6CDE0));
        Button refresh=new Button(this);refresh.setText("Refresh");refresh.setAllCaps(false);
        page.addView(refresh);refresh.setOnClickListener(v->load());
        state=label("Loading...",13,0xFF67D1EC);
        page.addView(state);
        load();
    }
    private String labelForMode(){
        switch(mode){
            case "friends_online": return "Online friends";
            case "friends_offline": return "Offline friends";
            case "worlds": return "Favorite worlds";
            case "recent": return "Recent worlds";
            case "groups": return "My groups";
            case "encounters": return "Recently encountered players";
            default:return "VRChat";
        }
    }
    private long lastFetch=0;
    private void load(){
        if(!StaffGate.unlocked(this)){finish();return;}
        if(System.currentTimeMillis()-lastFetch<7500){
            state.setText("Please wait a few seconds before refreshing.");return;
        }
        lastFetch=System.currentTimeMillis();
        state.setText("Loading "+labelForMode().toLowerCase()+"...");
        io.execute(()->{
            try{
                JSONArray data;
                switch(mode){
                    case "friends_online":data=VRChatApi.friends(this,false);break;
                    case "friends_offline":data=VRChatApi.friends(this,true);break;
                    case "worlds":data=VRChatApi.worldFavorites(this);break;
                    case "recent":data=VRChatApi.recentWorlds(this);break;
                    case "groups":data=VRChatApi.groups(this);break;
                    case "encounters":
                        try(MobileOutbox outbox=new MobileOutbox(this)){data=outbox.recentEncounters(80);}
                        break;
                    default:throw new IllegalArgumentException("Invalid view");
                }
                runOnUiThread(()->showItems(data));
            }catch(Exception e){
                String m=e.getMessage();
                if(m==null)m=e.getClass().getSimpleName();
                final String safe=m.substring(0,Math.min(m.length(),190));
                runOnUiThread(()->state.setText("Couldn't load: "+safe));
            }
        });
    }
    private void showItems(JSONArray result){
        if(!StaffGate.unlocked(this)){finish();return;}
        // remove previously drawn rows but retain the header/refresh/status.
        while(page.getChildCount()>4)page.removeViewAt(4);
        state.setText(result.length()+" results");
        if(result.length()==0){
            page.addView(label("Nothing to show yet.",13,0xFFB6CDE0));
            return;
        }
        for(int i=0;i<Math.min(result.length(),100);i++){
            JSONObject item=result.optJSONObject(i);
            if(item==null)continue;
            String title=item.optString("displayName",item.optString("name","Unknown"));
            String subtitle="",link=null;
            if("friends_online".equals(mode)||"friends_offline".equals(mode)){
                subtitle=item.optString("statusDescription",item.optString("status",""))+
                  " · "+item.optString("location","unknown location");
                String id=item.optString("id");
                if(id.matches("usr_[0-9a-fA-F-]{36}"))link="https://vrchat.com/home/user/"+id;
            }else if("worlds".equals(mode)||"recent".equals(mode)){
                subtitle="Players: "+item.optInt("occupants",0)+" · Capacity: "+item.optInt("capacity",0);
                String id=item.optString("id");
                if(id.matches("wrld_[0-9a-fA-F-]{36}"))link="https://vrchat.com/home/world/"+id;
            }else if("groups".equals(mode)){
                subtitle=item.optString("shortCode","")+
                  " · "+item.optInt("memberCount",0)+" members";
                String id=item.optString("groupId",item.optString("id",""));
                if(id.matches("grp_[0-9a-fA-F-]{36}"))link="https://vrchat.com/home/group/"+id;
            }else if("encounters".equals(mode)){
                title=item.optString("display_name","Unknown player");
                subtitle="Seen "+item.optInt("count",0)+" time(s) · "+item.optString("last_seen","?");
                String id=item.optString("user_id");
                if(id.matches("usr_[0-9a-fA-F-]{36}"))link="https://vrchat.com/home/user/"+id;
            }
            LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(dp(14),dp(12),dp(14),dp(12));
            row.setBackgroundColor((i%2==0)?0xFF17283A:0xFF21344A);
            page.addView(row);
            row.addView(label(title,16,0xFFE6F4FA));
            if(subtitle!=null&&!subtitle.trim().isEmpty())row.addView(label(subtitle,12,0xFFAEC5DA));
            if(link!=null){
                final String dest=link;
                row.setOnClickListener(v->{
                    try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(dest)));}
                    catch(ActivityNotFoundException ignored){}
                });
            }
        }
    }
    @Override protected void onDestroy(){io.shutdownNow();super.onDestroy();}
}
