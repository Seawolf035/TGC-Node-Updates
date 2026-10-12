package club.thegamerclub.nodelab;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Native read-only instance details with explicit opt-in self/friend invitation actions. */
public final class VRChatInstanceActivity extends StaffProtectedActivity {
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private LinearLayout page;
    private TextView notice;
    private String location,worldId,instanceId;
    private int dp(float v){return CompanionStyle.dp(this,v);}
    private TextView text(String s,int n,int color,boolean strong){
        return CompanionStyle.text(this,s,n,color,strong);
    }
    private void space(int h){page.addView(new View(this),new LinearLayout.LayoutParams(1,dp(h)));}
    private void detail(LinearLayout parent,String key,String val){
        if(val==null || val.trim().isEmpty() || val.equalsIgnoreCase("null"))return;
        parent.addView(CompanionStyle.section(this,key));
        TextView t=text(val,14,CompanionStyle.TEXT,false);
        t.setTextIsSelectable(true);
        parent.addView(t);
    }
    private void button(LinearLayout parent,String name,boolean primary,Runnable click){
        Button b=CompanionStyle.button(this,name,primary);
        parent.addView(b,CompanionStyle.buttonParams(this));
        b.setOnClickListener(v->click.run());
    }
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        if(!StaffGate.unlocked(this)){finish();return;}
        location=getIntent().getStringExtra("location");
        try{VRChatApi.normalizeLocation(location);}
        catch(Exception ex){finish();return;}
        int sep=location.indexOf(':');
        worldId=location.substring(0,sep);
        instanceId=location.substring(sep+1);
        render("Loading instance information...");
        reload();
    }
    @Override protected void onResume(){super.onResume();if(!StaffGate.unlocked(this))finish();}
    private void render(String msg){
        getWindow().setStatusBarColor(CompanionStyle.BG);
        getWindow().setNavigationBarColor(CompanionStyle.BG);
        ScrollView scroll=new ScrollView(this);
        scroll.setBackgroundColor(CompanionStyle.BG);
        page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(17),dp(16),dp(17),dp(32));
        scroll.addView(page);setContentView(scroll);
        CompanionStyle.safeArea(this,scroll);
        Button back=CompanionStyle.button(this,"‹  Back",false);
        page.addView(back,new LinearLayout.LayoutParams(dp(128),dp(46)));
        back.setOnClickListener(v->finish());
        space(14);page.addView(CompanionStyle.section(this,"WORLD INSTANCE"));
        page.addView(text("Instance details",26,CompanionStyle.TEXT,true));
        space(14);
        notice=text(msg,13,CompanionStyle.MUTED,false);page.addView(notice);
    }
    private void reload(){
        if(!StaffGate.unlocked(this)){finish();return;}
        notice.setText("Loading from VRChat...");
        worker.execute(()->{
            JSONObject instance=null,world=null;
            String error=null;
            try{instance=VRChatApi.getInstance(this,location);}
            catch(Exception e){error=e.getMessage();}
            try{world=VRChatApi.getWorld(this,worldId);}
            catch(Exception ignored){}
            final JSONObject i=instance,w=world;
            final String issue=error;
            runOnUiThread(()->{
                if(isFinishing()||isDestroyed()||!StaffGate.unlocked(this))return;
                render(issue==null?"Information from your VRChat account":
                    "VRChat restricted some instance details. You can still view the world.");
                show(i,w);
            });
        });
    }
    private void show(JSONObject instance,JSONObject world){
        String title=world==null?"VRChat world":world.optString("name","VRChat world");
        LinearLayout hero=CompanionStyle.card(this);
        page.addView(hero,CompanionStyle.cardParams(this));
        hero.addView(text(title,22,CompanionStyle.TEXT,true));
        if(instance!=null){
            String name=instance.optString("displayName","");
            if(!name.isEmpty()){
                TextView instanceName=text(name,19,CompanionStyle.ACCENT,true);
                hero.addView(instanceName);
            }
            detail(hero,"Access",instance.optString("type",""));
            detail(hero,"Region",instance.optString("region",""));
            detail(hero,"Owner",instance.optString("ownerId",""));
            detail(hero,"Players",String.valueOf(instance.optInt("n_users",instance.optInt("occupants",0))));
            detail(hero,"Capacity",String.valueOf(instance.optInt("capacity",world!=null?world.optInt("capacity",0):0)));
            JSONArray users=instance.optJSONArray("users");
            if(users!=null && users.length()>0){
                detail(hero,"Visible players",String.valueOf(users.length()));
            }
        }
        if(world!=null){
            LinearLayout about=CompanionStyle.card(this);
            page.addView(about,CompanionStyle.cardParams(this));
            about.addView(text("About this world",18,CompanionStyle.TEXT,true));
            detail(about,"Description",world.optString("description",""));
            detail(about,"Author",world.optString("authorName",""));
        }
        LinearLayout actions=CompanionStyle.card(this);
        page.addView(actions,CompanionStyle.cardParams(this));
        actions.addView(text("Actions",19,CompanionStyle.TEXT,true));
        button(actions,"View world profile",false,()->startActivity(
            new Intent(this,VRChatDetailActivity.class).putExtra("kind","world").putExtra("id",worldId)));
        button(actions,"Copy instance location",false,()->{
            ClipboardManager clipboard=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
            if(clipboard!=null)clipboard.setPrimaryClip(ClipData.newPlainText("VRChat instance",location));
            notice.setText("Instance location copied.");
        });
        button(actions,"Send an invite to myself",true,()->confirm(
            "Send an invite to yourself?",
            "VRChat will send an invitation to this instance on your personal account.",
            ()->mutate("Sending invitation...",()->VRChatApi.selfInvite(this,location))));
        button(actions,"Invite a friend",false,()->startActivity(new Intent(this,VRChatPeoplePickerActivity.class)
            .putExtra("location",location)));
        button(actions,"Refresh information",false,this::reload);
    }
    private void confirm(String title,String message,Runnable click){
        new AlertDialog.Builder(this).setTitle(title).setMessage(message)
            .setNegativeButton("Cancel",null).setPositiveButton("Continue",(d,w)->click.run()).show();
    }
    private interface Action{void run()throws Exception;}
    private void mutate(String label,Action action){
        if(!StaffGate.unlocked(this)){finish();return;}
        notice.setText(label);
        worker.execute(()->{
            String result;
            try{action.run();result="Invitation sent successfully.";}
            catch(Exception ex){result="Couldn't send invitation: "+ex.getMessage();}
            final String value=result;
            runOnUiThread(()->{if(notice!=null)notice.setText(value);});
        });
    }
    @Override protected void onDestroy(){worker.shutdownNow();super.onDestroy();}
}
