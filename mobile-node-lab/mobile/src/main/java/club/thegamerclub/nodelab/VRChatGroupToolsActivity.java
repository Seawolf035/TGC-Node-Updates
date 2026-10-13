package club.thegamerclub.nodelab;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Native Mini-VRCX group tools using the signed-in personal VRChat session.
 * Group members, live instances and visible bans are read-only. The only
 * mutation is an explicitly confirmed invite to the user's own account.
 */
public final class VRChatGroupToolsActivity extends StaffProtectedActivity {
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private String groupId,mode;
    private int offset=0;
    private String query="";
    private boolean busy=false,populatedOnly=false,sortByPopulation=true;
    private JSONArray items=new JSONArray(),groupRoles=new JSONArray();
    private LinearLayout page;
    private TextView notice;
    private int dp(float value){return CompanionStyle.dp(this,value);}
    private TextView text(String value,int size,int color,boolean bold){
        return CompanionStyle.text(this,value,size,color,bold);
    }
    private void button(LinearLayout parent,String label,boolean primary,Runnable run){
        Button control=CompanionStyle.button(this,label,primary);
        parent.addView(control,CompanionStyle.buttonParams(this));
        control.setOnClickListener(v->run.run());
    }
    private LinearLayout card(){
        LinearLayout view=CompanionStyle.card(this);
        page.addView(view,CompanionStyle.cardParams(this));
        return view;
    }
    private String title(){
        return "members".equals(mode)?"Group member directory":
            "instances".equals(mode)?"Group instances":"Visible group bans";
    }
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        if(!StaffGate.unlocked(this)){finish();return;}
        groupId=getIntent().getStringExtra("groupId");
        mode=getIntent().getStringExtra("mode");
        if(groupId==null||!groupId.matches("grp_[0-9a-fA-F-]{36}")||
            (!"members".equals(mode)&&!"instances".equals(mode)&&!"bans".equals(mode))){
            finish();return;
        }
        render();
        load();
    }
    @Override protected void onResume(){super.onResume();if(!StaffGate.unlocked(this))finish();}
    private void render(){
        getWindow().setStatusBarColor(CompanionStyle.BG);
        getWindow().setNavigationBarColor(CompanionStyle.BG);
        ScrollView scroll=new ScrollView(this);
        scroll.setBackgroundColor(CompanionStyle.BG);
        page=new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(16),dp(14),dp(16),dp(30));
        scroll.addView(page);
        setContentView(scroll);
        CompanionStyle.safeArea(this,scroll);
        button(page,"‹  Back to group",false,this::finish);
        page.addView(CompanionStyle.section(this,"MINI-VRCX · GROUP TOOLS"));
        page.addView(text(title(),23,CompanionStyle.TEXT,true));
        page.addView(text("Only information permitted for your signed-in VRChat account is shown.",
            12,CompanionStyle.MUTED,false));
        notice=text(busy?"Loading from VRChat...":"",12,CompanionStyle.ACCENT,false);
        page.addView(notice);

        if("members".equals(mode)){
            LinearLayout searchCard=card();
            searchCard.addView(text("Find a group member",14,CompanionStyle.TEXT,true));
            EditText search=CompanionStyle.input(this,"Search 3+ characters across this group");
            search.setSingleLine(true);
            search.setText(query);
            search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
            searchCard.addView(search,new LinearLayout.LayoutParams(-1,dp(52)));
            Runnable submit=()->{
                String value=search.getText().toString().trim();
                if(!value.isEmpty()&&(value.length()<3||value.length()>80)){
                    notice.setText("Group member search requires 3–80 characters.");
                    return;
                }
                InputMethodManager input=(InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);
                if(input!=null)input.hideSoftInputFromWindow(search.getWindowToken(),0);
                query=value;offset=0;load();
            };
            search.setOnEditorActionListener((v,action,event)->{
                if(action==EditorInfo.IME_ACTION_SEARCH||
                    (event!=null&&event.getKeyCode()==android.view.KeyEvent.KEYCODE_ENTER)){
                    submit.run();return true;
                }
                return false;
            });
            button(searchCard,"Search / refresh",true,submit);
            if(!query.isEmpty())button(searchCard,"Clear search",false,()->{
                query="";offset=0;load();
            });
        }else if("instances".equals(mode)){
            LinearLayout options=card();
            button(options,populatedOnly?"Show all visible instances":"Only populated instances",
                false,()->{populatedOnly=!populatedOnly;render();});
            button(options,sortByPopulation?"Sort: Most populated":"Sort: VRChat order",
                false,()->{sortByPopulation=!sortByPopulation;render();});
            button(options,"Refresh from VRChat",false,this::load);
        }else{
            LinearLayout warning=card();
            warning.addView(text("Read-only group ban list",15,CompanionStyle.GOLD,true));
            warning.addView(text(
                "Your account must have permission to view this list. "+
                "This screen cannot add or remove bans.",12,CompanionStyle.MUTED,false));
            button(warning,"Refresh bans",false,this::load);
        }
        displayItems();
    }
    private void load(){
        if(!StaffGate.unlocked(this)){finish();return;}
        if(busy)return;
        busy=true;
        if(notice!=null)notice.setText("Loading from VRChat...");
        final String group=groupId,section=mode,term=query;
        final int pageOffset=offset;
        worker.execute(()->{
            JSONArray rows=null,roles=new JSONArray();
            String error=null;
            try{
                if("members".equals(section)){
                    if(!term.isEmpty())rows=VRChatApi.searchGroupMembers(this,group,term);
                    else rows=VRChatApi.groupMembers(this,group,pageOffset);
                    try{
                        JSONObject details=VRChatApi.getGroup(this,group);
                        JSONArray available=details.optJSONArray("roles");
                        if(available!=null)roles=available;
                    }catch(Exception ignored){}
                }else if("instances".equals(section)){
                    rows=VRChatApi.groupInstances(this,group);
                }else rows=VRChatApi.groupBans(this,group,pageOffset);
            }catch(Exception e){error=e.getMessage();}
            final JSONArray result=rows,roleData=roles;
            final String issue=error;
            runOnUiThread(()->{
                if(isFinishing()||isDestroyed()||!StaffGate.unlocked(this))return;
                busy=false;
                if(issue!=null){
                    if(notice!=null)notice.setText("VRChat could not load this view: "+issue);
                    return;
                }
                items=result==null?new JSONArray():result;
                groupRoles=roleData;
                render();
            });
        });
    }
    private String memberId(JSONObject item){
        String id=item.optString("userId","");
        JSONObject user=item.optJSONObject("user");
        if(!id.matches("usr_[0-9a-fA-F-]{36}")&&user!=null)
            id=user.optString("id","");
        return id;
    }
    private String memberName(JSONObject item){
        JSONObject user=item.optJSONObject("user");
        return user==null?item.optString("displayName","Group member"):
            user.optString("displayName",item.optString("displayName","Group member"));
    }
    private String memberRoles(JSONObject item){
        JSONArray ids=item.optJSONArray("roleIds");
        if(ids==null||ids.length()==0)return "";
        StringBuilder result=new StringBuilder();
        for(int i=0;i<ids.length();i++){
            String id=ids.optString(i,"");
            for(int j=0;j<groupRoles.length();j++){
                JSONObject role=groupRoles.optJSONObject(j);
                if(role!=null&&id.equals(role.optString("id",""))){
                    String name=role.optString("name","");
                    if(!name.isEmpty()){
                        if(result.length()>0)result.append(", ");
                        result.append(name);
                    }
                    break;
                }
            }
        }
        return result.length()>0?result.toString():ids.length()+" role(s)";
    }
    private int occupants(JSONObject item){
        int count=item.optInt("n_users",-1);
        if(count<0)count=item.optInt("userCount",-1);
        return count;
    }
    private void copy(String value){
        ClipboardManager clipboard=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
        if(clipboard!=null)clipboard.setPrimaryClip(ClipData.newPlainText("VRChat",value));
        notice.setText("Copied.");
    }
    private void displayItems(){
        if(busy)return;
        if(items.length()==0){
            LinearLayout empty=card();
            empty.addView(text("No records returned",14,CompanionStyle.MUTED,false));
        }
        if("instances".equals(mode)){
            ArrayList<JSONObject> visible=new ArrayList<>();
            for(int i=0;i<items.length();i++){
                JSONObject row=items.optJSONObject(i);
                if(row!=null&&(!populatedOnly||occupants(row)!=0))visible.add(row);
            }
            if(sortByPopulation)Collections.sort(visible,(a,b)->
                Integer.compare(occupants(b),occupants(a)));
            for(JSONObject row:visible){
                String location=row.optString("location","");
                JSONObject world=row.optJSONObject("world");
                String name=row.optString("name","");
                if(name.isEmpty()&&world!=null)name=world.optString("name","");
                if(name.isEmpty())name="Group instance";
                LinearLayout card=card();
                card.addView(text(name,16,CompanionStyle.TEXT,true));
                int count=occupants(row);
                card.addView(text((count<0?"Population unknown":count+" players")+
                    " · "+row.optString("region",""),12,CompanionStyle.MUTED,false));
                if(!location.isEmpty()){
                    button(card,"Copy instance location",false,()->copy(location));
                    if(location.startsWith("wrld_")&&location.contains(":")){
                        final String safe=location;
                        button(card,"Instance details",true,()->startActivity(
                            new Intent(this,VRChatInstanceActivity.class)
                                .putExtra("location",safe)));
                        button(card,"Invite myself",false,()->new AlertDialog.Builder(this)
                            .setTitle("Send a self-invite?")
                            .setMessage("Send a VRChat invitation to this instance "+
                                "using your personal VRChat account?")
                            .setNegativeButton("Cancel",null)
                            .setPositiveButton("Send",(d,w)->sendSelfInvite(safe))
                            .show());
                    }
                }
            }
            if(visible.isEmpty())card().addView(text("No matching visible instances.",
                12,CompanionStyle.MUTED,false));
        }else{
            for(int i=0;i<items.length();i++){
                JSONObject row=items.optJSONObject(i);
                if(row==null)continue;
                LinearLayout card=card();
                if("members".equals(mode)){
                    String id=memberId(row);
                    card.addView(text(memberName(row),16,CompanionStyle.TEXT,true));
                    String roles=memberRoles(row);
                    if(!roles.isEmpty())card.addView(text(roles,12,CompanionStyle.ACCENT,false));
                    String joined=row.optString("joinedAt","");
                    if(!joined.isEmpty())card.addView(text("Joined "+joined,
                        12,CompanionStyle.MUTED,false));
                    if(id.matches("usr_[0-9a-fA-F-]{36}"))
                        button(card,"View player profile",false,()->startActivity(
                            new Intent(this,VRChatDetailActivity.class)
                                .putExtra("kind","user").putExtra("id",id)));
                }else{
                    String id=row.optString("userId",row.optString("id",""));
                    card.addView(text(memberName(row),15,CompanionStyle.TEXT,true));
                    card.addView(text("Ban record · Read-only",12,CompanionStyle.GOLD,false));
                    if(id.matches("usr_[0-9a-fA-F-]{36}"))
                        button(card,"View user profile",false,()->startActivity(
                            new Intent(this,VRChatDetailActivity.class)
                                .putExtra("kind","user").putExtra("id",id)));
                }
            }
            if(query.isEmpty()&&items.length()>=60){
                LinearLayout navigation=card();
                if(offset>=60)button(navigation,"Previous 60",false,()->{
                    offset-=60;load();
                });
                button(navigation,"Next 60",false,()->{offset+=60;load();});
            }else if(query.isEmpty()&&offset>=60){
                button(card(),"Previous 60",false,()->{offset-=60;load();});
            }
        }
    }
    private void sendSelfInvite(String location){
        if(busy||!StaffGate.unlocked(this))return;
        busy=true;notice.setText("Sending personal self-invite...");
        worker.execute(()->{
            String response;
            try{
                VRChatApi.selfInvite(this,location);
                response="Self-invite requested through VRChat.";
            }catch(Exception ex){response="Invite failed: "+ex.getMessage();}
            final String result=response;
            runOnUiThread(()->{
                busy=false;
                if(!isFinishing()&&!isDestroyed()&&notice!=null)notice.setText(result);
            });
        });
    }
    @Override protected void onDestroy(){worker.shutdownNow();super.onDestroy();}
}
