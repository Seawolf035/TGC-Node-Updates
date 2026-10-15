package club.thegamerclub.nodelab;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Personal-account VRChat instance creator, following VRCX's official
 * POST /instances flow. After creation it automatically sends a personal
 * self-invite, never a TGC group invitation. Never constructs fake instance IDs.
 */
public final class VRChatInstanceCreateActivity extends StaffProtectedActivity {
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private final ArrayList<JSONObject> groups=new ArrayList<>();
    private final ArrayList<String> groupLabels=new ArrayList<>();
    private final ArrayList<JSONObject> groupRoles=new ArrayList<>();
    private final HashSet<String> chosenRoleIds=new HashSet<>();
    private final ArrayList<String> groupModes=new ArrayList<>();
    private JSONObject permissions=new JSONObject();
    private String groupId="",createdLocation="",worldName="";
    private boolean busy=false,permissionLoaded=false,invited=false;
    private EditText worldInput,displayName;
    private Spinner access,region,groupAccess,minimumAvatar;
    private CheckBox queue,ageGate;
    private LinearLayout page,groupPanel;
    private TextView status,groupLabel,rolesLabel,worldLabel;
    private Button create,retryInvite;
    private final String[] accessOptions={"Public","Friends+","Friends","Invite+","Invite","Group"};
    private final String[] accessKeys={"public","friends+","friends","invite+","invite","group"};
    private final String[] regions={"US West","US East","Europe","Japan"};
    private final String[] regionKeys={"us","use","eu","jp"};
    private final String[] performance={"None","Poor","Medium","Good"};
    private int dp(int size){return CompanionStyle.dp(this,size);}
    private TextView text(String value,int size,int color,boolean strong){
        return CompanionStyle.text(this,value,size,color,strong);
    }
    private LinearLayout card(){
        LinearLayout result=CompanionStyle.card(this);
        page.addView(result,CompanionStyle.cardParams(this));
        return result;
    }
    private void space(LinearLayout root,int amount){
        root.addView(new View(this),new LinearLayout.LayoutParams(1,dp(amount)));
    }
    private Button button(LinearLayout root,String label,boolean primary,Runnable run){
        Button b=CompanionStyle.button(this,label,primary);
        root.addView(b,CompanionStyle.buttonParams(this));
        b.setOnClickListener(v->run.run());
        return b;
    }
    private void heading(LinearLayout root,String label){
        root.addView(CompanionStyle.section(this,label));
    }
    private Spinner spinner(LinearLayout root,String[] values){
        Spinner input=new Spinner(this);
        ArrayAdapter<String> adapter=new ArrayAdapter<>(this,
            android.R.layout.simple_spinner_dropdown_item,values);
        input.setAdapter(adapter);
        input.setBackground(CompanionStyle.shape(this,CompanionStyle.FIELD,12,CompanionStyle.BORDER));
        input.setPadding(dp(8),dp(7),dp(8),dp(7));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(51));
        p.bottomMargin=dp(9);
        root.addView(input,p);
        return input;
    }
    private EditText input(LinearLayout root,String hint){
        EditText e=CompanionStyle.input(this,hint);
        root.addView(e,new LinearLayout.LayoutParams(-1,dp(53)));
        space(root,9);
        return e;
    }
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);
        if(!StaffGate.unlocked(this)){finish();return;}
        if(saved!=null){
            createdLocation=saved.getString("location","");
            invited=saved.getBoolean("invited",false);
            worldName=saved.getString("worldName","");
        }
        Intent launch=getIntent();
        groupId=launch.getStringExtra("groupId");
        if(groupId==null||!groupId.matches("grp_[0-9a-fA-F-]{36}"))groupId="";
        build();
        if(saved!=null){
            worldInput.setText(saved.getString("worldId",""));
            displayName.setText(saved.getString("displayName",""));
            access.setSelection(saved.getInt("access",0));
            region.setSelection(saved.getInt("region",0));
        }else{
            String world=launch.getStringExtra("worldId");
            if(world!=null&&world.matches("wrld_[0-9a-fA-F-]{36}"))
                worldInput.setText(world);
            if(!groupId.isEmpty())access.setSelection(5);
        }
        updateGroupVisibility();
        if(createdLocation.isEmpty())loadGroups();
        else{
            status.setText(invited?
                "Created successfully and sent a VRChat invite to yourself.":
                "Instance created. Self-invite was not confirmed. Retry the invite below.");
            create.setVisibility(View.GONE);
            retryInvite.setVisibility(invited?View.GONE:View.VISIBLE);
        }
    }
    @Override protected void onSaveInstanceState(Bundle out){
        super.onSaveInstanceState(out);
        if(worldInput==null)return;
        out.putString("location",createdLocation);
        out.putBoolean("invited",invited);
        out.putString("worldName",worldName);
        out.putString("worldId",worldInput.getText().toString().trim());
        out.putString("displayName",displayName.getText().toString());
        out.putInt("access",access.getSelectedItemPosition());
        out.putInt("region",region.getSelectedItemPosition());
    }
    private void build(){
        getWindow().setStatusBarColor(CompanionStyle.BG);
        getWindow().setNavigationBarColor(CompanionStyle.BG);
        ScrollView scroll=new ScrollView(this);
        scroll.setBackgroundColor(CompanionStyle.BG);
        page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(16),dp(13),dp(16),dp(30));
        scroll.addView(page);setContentView(scroll);
        CompanionStyle.safeArea(this,scroll);
        button(page,"‹  Back",false,this::finish);
        heading(page,"VRCHAT INSTANCE CREATOR");
        page.addView(text("Create a world instance",24,CompanionStyle.TEXT,true));
        page.addView(text("Uses your signed-in VRChat account. After creation, "+
            "you'll automatically receive an invite to join.",13,CompanionStyle.MUTED,false));
        space(page,13);
        LinearLayout world=card();
        heading(world,"WORLD");
        worldInput=input(world,"wrld_…");
        worldLabel=text(worldName,13,CompanionStyle.ACCENT,false);
        world.addView(worldLabel);
        button(world,"Search VRChat worlds",false,this::searchWorld);
        LinearLayout settings=card();
        heading(settings,"ACCESS");
        access=spinner(settings,accessOptions);
        access.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
            @Override public void onItemSelected(android.widget.AdapterView<?> parent,View view,
                int position,long id){updateGroupVisibility();}
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent){}
        });
        heading(settings,"SERVER REGION");
        region=spinner(settings,regions);
        heading(settings,"OPTIONAL INSTANCE NAME · VRC+");
        displayName=input(settings,"Leave empty to use the normal world name");
        groupPanel=card();
        heading(groupPanel,"GROUP INSTANCE OPTIONS");
        groupLabel=text("Loading your eligible VRChat groups…",
            13,CompanionStyle.MUTED,false);
        groupPanel.addView(groupLabel);
        button(groupPanel,"Choose group",false,this::chooseGroup);
        heading(groupPanel,"GROUP ACCESS");
        groupAccess=spinner(groupPanel,new String[]{"Select a permitted group"});
        queue=new CheckBox(this);queue.setText("Enable join queue");
        queue.setTextColor(CompanionStyle.TEXT);queue.setChecked(true);
        groupPanel.addView(queue);
        ageGate=new CheckBox(this);ageGate.setText("Age-gated instance (permission required)");
        ageGate.setTextColor(CompanionStyle.TEXT);
        groupPanel.addView(ageGate);
        heading(groupPanel,"MINIMUM AVATAR PERFORMANCE");
        minimumAvatar=spinner(groupPanel,performance);
        rolesLabel=text("Role restrictions: none",13,CompanionStyle.MUTED,false);
        groupPanel.addView(rolesLabel);
        button(groupPanel,"Select permitted member roles",false,this::pickRoles);
        updateGroupVisibility();
        status=text("Choose a world and instance access, then create.",
            13,CompanionStyle.ACCENT,false);
        page.addView(status);
        space(page,12);
        create=button(page,"Create instance and invite me",true,this::confirmCreate);
        retryInvite=button(page,"Retry self-invite",true,this::sendSelfInvite);
        retryInvite.setVisibility(View.GONE);
        page.addView(text("VRChat determines which group permissions you have. "+
            "This does not create TGC group invites or change your account location.",
            12,CompanionStyle.MUTED,false));
    }
    private void updateGroupVisibility(){
        if(groupPanel==null||access==null)return;
        boolean group=access.getSelectedItemPosition()==5;
        groupPanel.setVisibility(group?View.VISIBLE:View.GONE);
    }
    private boolean hasPermission(String group,String permission){
        JSONArray list=permissions.optJSONArray(group);
        JSONObject record=permissions.optJSONObject(group);
        if(list==null&&record!=null)list=record.optJSONArray("permissions");
        if(list==null)return false;
        for(int i=0;i<list.length();i++)if(permission.equals(list.optString(i,"")))return true;
        return false;
    }
    private boolean allowedGroup(String id){
        return hasPermission(id,"group-instance-open-create")||
            hasPermission(id,"group-instance-plus-create")||
            hasPermission(id,"group-instance-public-create");
    }
    private void loadGroups(){
        io.execute(()->{
            ArrayList<JSONObject> loaded=new ArrayList<>();
            JSONObject permitted=new JSONObject();
            String failure=null;
            try{
                JSONArray memberships=VRChatApi.groups(this);
                permitted=VRChatApi.myGroupPermissions(this);
                for(int i=0;i<memberships.length();i++){
                    JSONObject m=memberships.optJSONObject(i);
                    if(m==null)continue;
                    JSONObject sub=m.optJSONObject("group");
                    String id=m.optString("groupId","");
                    if(id.isEmpty())id=m.optString("id","");
                    if(id.isEmpty()&&sub!=null)id=sub.optString("id","");
                    if(id.matches("grp_[0-9a-fA-F-]{36}"))loaded.add(m);
                }
            }catch(Exception ex){failure=ex.getMessage();}
            final JSONObject allowed=permitted;
            final String error=failure;
            runOnUiThread(()->{
                if(isFinishing()||isDestroyed())return;
                permissions=allowed;permissionLoaded=error==null;
                groups.clear();groupLabels.clear();
                for(JSONObject m:loaded){
                    JSONObject nested=m.optJSONObject("group");
                    String id=m.optString("groupId",m.optString("id",""));
                    if(id.isEmpty()&&nested!=null)id=nested.optString("id","");
                    if(!allowedGroup(id))continue;
                    groups.add(m);
                    String label=m.optString("name",m.optString("groupName",""));
                    if(label.isEmpty()&&nested!=null)label=nested.optString("name","");
                    groupLabels.add(label.isEmpty()?id:label);
                }
                if(error!=null)
                    groupLabel.setText("Group permissions unavailable: "+error);
                else if(groups.isEmpty())
                    groupLabel.setText("No groups with instance-creation permissions.");
                else if(!groupId.isEmpty()&&allowedGroup(groupId))
                    updateSelectedGroup(groupId);
                else
                    groupLabel.setText("Select a group ("+groups.size()+" eligible)");
            });
        });
    }
    private void chooseGroup(){
        if(!permissionLoaded){status.setText("Group permissions are still loading.");return;}
        if(groupLabels.isEmpty()){status.setText("No permitted groups available.");return;}
        new AlertDialog.Builder(this).setTitle("Create for which group?")
            .setItems(groupLabels.toArray(new String[0]),(d,which)->{
                JSONObject m=groups.get(which);
                JSONObject sub=m.optJSONObject("group");
                String id=m.optString("groupId",m.optString("id",""));
                if(id.isEmpty()&&sub!=null)id=sub.optString("id","");
                updateSelectedGroup(id);
            }).setNegativeButton("Cancel",null).show();
    }
    private void updateSelectedGroup(String id){
        if(!id.matches("grp_[0-9a-fA-F-]{36}")||!allowedGroup(id)){
            groupId="";groupLabel.setText("This group does not permit instance creation.");
            return;
        }
        groupId=id;
        String title=id;
        for(int i=0;i<groups.size();i++){
            JSONObject member=groups.get(i);
            JSONObject sub=member.optJSONObject("group");
            String current=member.optString("groupId",member.optString("id",""));
            if(current.isEmpty()&&sub!=null)current=sub.optString("id","");
            if(id.equals(current)){title=groupLabels.get(i);break;}
        }
        groupLabel.setText(title);
        groupModes.clear();
        if(hasPermission(id,"group-instance-open-create"))groupModes.add("Members");
        if(hasPermission(id,"group-instance-plus-create"))groupModes.add("Group+");
        if(hasPermission(id,"group-instance-public-create"))groupModes.add("Public");
        ArrayAdapter<String> modes=new ArrayAdapter<>(this,
            android.R.layout.simple_spinner_dropdown_item,groupModes);
        groupAccess.setAdapter(modes);
        ageGate.setEnabled(hasPermission(id,"group-instance-age-gated-create"));
        if(!ageGate.isEnabled())ageGate.setChecked(false);
        chosenRoleIds.clear();groupRoles.clear();
        rolesLabel.setText("Role restrictions: none");
        io.execute(()->{
            JSONArray roles=null;String failure=null;
            try{roles=VRChatApi.groupRoles(this,id);}
            catch(Exception e){failure=e.getMessage();}
            final JSONArray result=roles;final String error=failure;
            runOnUiThread(()->{
                if(!id.equals(groupId)||isFinishing()||isDestroyed())return;
                groupRoles.clear();
                if(result!=null)for(int i=0;i<result.length();i++){
                    JSONObject role=result.optJSONObject(i);
                    if(role!=null)groupRoles.add(role);
                }
                if(error!=null)rolesLabel.setText("Group role choices unavailable: "+error);
                else rolesLabel.setText("Role restrictions: none");
            });
        });
    }
    private void pickRoles(){
        if(groupRoles.isEmpty()){
            status.setText("Select a group with accessible roles first.");return;
        }
        String[] names=new String[groupRoles.size()];
        boolean[] checked=new boolean[names.length];
        for(int i=0;i<names.length;i++){
            JSONObject role=groupRoles.get(i);
            names[i]=role.optString("name","Group role");
            checked[i]=chosenRoleIds.contains(role.optString("id",""));
        }
        new AlertDialog.Builder(this).setTitle("Members allowed to join")
            .setMultiChoiceItems(names,checked,(d,index,value)->checked[index]=value)
            .setNegativeButton("Cancel",null)
            .setPositiveButton("Use selected roles",(d,w)->{
                chosenRoleIds.clear();
                for(int i=0;i<checked.length;i++)if(checked[i])
                    chosenRoleIds.add(groupRoles.get(i).optString("id",""));
                rolesLabel.setText(chosenRoleIds.isEmpty()?
                    "Role restrictions: none":
                    chosenRoleIds.size()+" group roles selected");
            }).show();
    }
    private void searchWorld(){
        EditText search=CompanionStyle.input(this,"Search VRChat world name");
        LinearLayout container=new LinearLayout(this);
        container.setPadding(dp(18),dp(12),dp(18),0);
        container.addView(search,new LinearLayout.LayoutParams(-1,dp(54)));
        new AlertDialog.Builder(this).setTitle("Find a world")
            .setView(container)
            .setNegativeButton("Cancel",null)
            .setPositiveButton("Search",(d,which)->{
                String term=search.getText().toString().trim();
                if(term.length()<2){status.setText("Search needs at least 2 characters.");return;}
                status.setText("Searching VRChat worlds…");
                io.execute(()->{
                    JSONArray result=null;String failure=null;
                    try{result=VRChatApi.searchWorlds(this,term);}
                    catch(Exception ex){failure=ex.getMessage();}
                    final JSONArray worlds=result;final String error=failure;
                    runOnUiThread(()->{
                        if(isFinishing()||isDestroyed())return;
                        if(error!=null){status.setText("World search failed: "+error);return;}
                        ArrayList<JSONObject> options=new ArrayList<>();
                        ArrayList<String> names=new ArrayList<>();
                        if(worlds!=null)for(int i=0;i<worlds.length();i++){
                            JSONObject w=worlds.optJSONObject(i);
                            if(w==null)continue;
                            String id=w.optString("id","");
                            if(!id.matches("wrld_[0-9a-fA-F-]{36}"))continue;
                            options.add(w);names.add(w.optString("name",id));
                        }
                        if(options.isEmpty()){status.setText("No worlds found.");return;}
                        new AlertDialog.Builder(this).setTitle("Choose a world")
                            .setItems(names.toArray(new String[0]),(dialog,index)->{
                                JSONObject selected=options.get(index);
                                worldInput.setText(selected.optString("id",""));
                                worldName=selected.optString("name","");
                                worldLabel.setText(worldName);
                                status.setText("World selected. Ready to configure.");
                            }).setNegativeButton("Cancel",null).show();
                    });
                });
            }).show();
    }
    private void confirmCreate(){
        if(busy||!createdLocation.isEmpty()||!StaffGate.unlocked(this))return;
        String world=worldInput.getText().toString().trim();
        if(!world.matches("wrld_[0-9a-fA-F-]{36}")){
            status.setText("Select a valid VRChat world first.");return;
        }
        String type=accessKeys[access.getSelectedItemPosition()];
        if("group".equals(type)){
            if(!permissionLoaded||!allowedGroup(groupId)||groupModes.isEmpty()){
                status.setText("Choose a group with instance creation permissions.");
                return;
            }
        }
        String summary=type.equals("group")?
            "Create a "+groupAccess.getSelectedItem()+" group instance in "+regions[region.getSelectedItemPosition()]+"?":
            "Create a "+accessOptions[access.getSelectedItemPosition()]+" instance in "+
                regions[region.getSelectedItemPosition()]+"?";
        new AlertDialog.Builder(this).setTitle("Create VRChat instance?")
            .setMessage(summary+"\n\nOnce VRChat creates it, the Companion will send you a personal invite automatically.")
            .setNegativeButton("Cancel",null)
            .setPositiveButton("Create and invite me",(d,w)->createInstance())
            .show();
    }
    private void createInstance(){
        if(busy||!createdLocation.isEmpty()||!StaffGate.unlocked(this))return;
        final String world=worldInput.getText().toString().trim();
        final String type=accessKeys[access.getSelectedItemPosition()];
        final String server=regionKeys[region.getSelectedItemPosition()];
        final String group=groupId;
        final String mode;
        if(groupModes.isEmpty()||groupAccess.getSelectedItem()==null)mode="plus";
        else{
            String label=String.valueOf(groupAccess.getSelectedItem());
            mode=label.equals("Members")?"members":label.equals("Public")?"public":"plus";
        }
        final String performanceChoice=String.valueOf(minimumAvatar.getSelectedItem());
        final JSONArray roles=new JSONArray();
        if("group".equals(type)&&"members".equals(mode))
            for(String id:chosenRoleIds)roles.put(id);
        final boolean queued=queue.isChecked(),gate=ageGate.isChecked();
        final String name=displayName.getText().toString().trim();
        busy=true;create.setEnabled(false);status.setText("Creating with VRChat…");
        io.execute(()->{
            String result="",location="";
            try{
                if(!name.isEmpty())VRChatApi.requireActiveVRCPlus(this);
                if("group".equals(type)&&gate&&
                   !hasPermission(group,"group-instance-age-gated-create"))
                    throw new SecurityException("Age-gated group instance permission missing");
                JSONObject instance=VRChatApi.createInstance(this,world,type,server,
                    group,mode,roles,queued,gate,name,performanceChoice);
                location=instance.optString("location","");
                try{
                    VRChatApi.selfInvite(this,location);
                    result="Instance created and self-invite sent! Check VRChat's notifications.";
                }catch(Exception inviteError){
                    result="Instance created, but the self-invite failed: "+
                        inviteError.getMessage()+". Retry Invite without creating again.";
                }
            }catch(Exception creationError){
                result="Couldn't confirm creation: "+creationError.getMessage();
            }
            final String finalMessage=result,finalLocation=location;
            runOnUiThread(()->{
                if(isFinishing()||isDestroyed())return;
                busy=false;
                if(!finalLocation.isEmpty()){
                    createdLocation=finalLocation;
                    invited=finalMessage.startsWith("Instance created and self-invite sent!");
                    create.setVisibility(View.GONE);
                    retryInvite.setVisibility(invited?View.GONE:View.VISIBLE);
                }else create.setEnabled(true);
                status.setText(finalMessage);
            });
        });
    }
    private void sendSelfInvite(){
        if(busy||createdLocation.isEmpty()||invited||!StaffGate.unlocked(this))return;
        busy=true;retryInvite.setEnabled(false);
        status.setText("Retrying invite to your own VRChat account…");
        io.execute(()->{
            String value;
            boolean success=false;
            try{
                VRChatApi.selfInvite(this,createdLocation);
                value="Self-invite sent! Check VRChat notifications.";
                success=true;
            }catch(Exception error){value="Self-invite failed: "+error.getMessage();}
            final String response=value;final boolean delivered=success;
            runOnUiThread(()->{
                if(isFinishing()||isDestroyed())return;
                busy=false;invited=delivered;
                status.setText(response);
                retryInvite.setVisibility(invited?View.GONE:View.VISIBLE);
                retryInvite.setEnabled(true);
            });
        });
    }
    @Override protected void onResume(){super.onResume();if(!StaffGate.unlocked(this))finish();}
    @Override protected void onDestroy(){io.shutdownNow();super.onDestroy();}
}
