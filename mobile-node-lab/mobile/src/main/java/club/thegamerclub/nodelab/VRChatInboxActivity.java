package club.thegamerclub.nodelab;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Native personal VRChat notifications and invites; combines VRCX-compatible
 * legacy and modern inboxes. Mutations are explicit, confirmed user actions.
 */
public final class VRChatInboxActivity extends StaffProtectedActivity {
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final ArrayList<Entry> entries=new ArrayList<>();
    private final InboxAdapter adapter=new InboxAdapter();
    private TextView status;
    private Button allTab,inviteTab,friendTab,more,refreshButton;
    private String filter="all";
    private boolean busy=false;
    private int legacyOffset=0,modernOffset=0;
    private int dp(float n){return CompanionStyle.dp(this,n);}
    private TextView txt(String value,int size,int color,boolean bold){
        return CompanionStyle.text(this,value,size,color,bold);
    }
    private static final class Entry{
        JSONObject raw,details;
        String id,type,name,message,date,location,sender;
        boolean modern,seen;
        long sort;
    }
    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        if(!StaffGate.unlocked(this)){finish();return;}
        filter=getIntent().getStringExtra("filter");
        if(!"invite".equals(filter)&&!"friend".equals(filter))filter="all";
        render();
        load(false);
    }
    @Override protected void onResume(){super.onResume();if(!StaffGate.unlocked(this))finish();}
    private void render(){
        getWindow().setStatusBarColor(CompanionStyle.BG);
        getWindow().setNavigationBarColor(CompanionStyle.BG);
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(CompanionStyle.BG);
        setContentView(root);CompanionStyle.safeArea(this,root);
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(14),dp(11),dp(14),dp(9));root.addView(header);
        Button back=CompanionStyle.button(this,"‹",false);
        back.setTextSize(26);header.addView(back,new LinearLayout.LayoutParams(dp(53),dp(48)));
        back.setOnClickListener(v->finish());
        TextView title=txt("Notifications & invites",21,CompanionStyle.TEXT,true);
        title.setPadding(dp(12),0,0,0);
        header.addView(title,new LinearLayout.LayoutParams(0,-2,1f));
        LinearLayout tools=new LinearLayout(this);
        tools.setPadding(dp(14),dp(8),dp(14),dp(6));
        tools.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(tools);
        allTab=filterButton(tools,"All","all");
        inviteTab=filterButton(tools,"Invites","invite");
        friendTab=filterButton(tools,"Friends","friend");
        refreshButton=CompanionStyle.button(this,"↻",false);
        LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(dp(48),dp(46));
        rp.leftMargin=dp(7);tools.addView(refreshButton,rp);
        refreshButton.setContentDescription("Refresh notifications");
        refreshButton.setOnClickListener(v->load(false));
        status=txt("Loading your notifications...",12,CompanionStyle.MUTED,false);
        status.setPadding(dp(17),dp(9),dp(17),dp(10));root.addView(status);
        RecyclerView list=new RecyclerView(this);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(adapter);
        list.setClipToPadding(false);
        list.setPadding(dp(12),dp(0),dp(12),dp(7));
        root.addView(list,new LinearLayout.LayoutParams(-1,0,1f));
        more=CompanionStyle.button(this,"Load more",false);
        LinearLayout.LayoutParams moreP=new LinearLayout.LayoutParams(-1,dp(48));
        moreP.setMargins(dp(15),dp(0),dp(15),dp(10));
        root.addView(more,moreP);
        more.setOnClickListener(v->load(true));
        updateFilter();
    }
    private Button filterButton(LinearLayout tools,String label,String value){
        Button button=CompanionStyle.button(this,label,false);
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(0,dp(46),1f);
        params.rightMargin=dp(4);
        tools.addView(button,params);
        button.setOnClickListener(v->{filter=value;updateFilter();});
        return button;
    }
    private void updateFilter(){
        for(Button button:new Button[]{allTab,inviteTab,friendTab}){
            if(button==null)continue;
            boolean selected=(button==allTab&&filter.equals("all"))||
                (button==inviteTab&&filter.equals("invite"))||
                (button==friendTab&&filter.equals("friend"));
            button.setBackground(CompanionStyle.shape(this,
                selected?CompanionStyle.ACCENT:CompanionStyle.RAISED,11,
                selected?CompanionStyle.ACCENT:0));
            button.setTextColor(selected?CompanionStyle.BG:CompanionStyle.TEXT);
            button.setContentDescription(button.getText()+(selected?" — selected":""));
        }
        adapter.rebuild();
        updateStatus();
    }
    private void updateStatus(){
        int count=0;
        for(Entry e:entries)if(include(e))count++;
        status.setText(count+" notifications"+(busy?" · loading...":""));
    }
    private boolean include(Entry e){
        if(filter.equals("all"))return true;
        String type=e.type.toLowerCase(Locale.US);
        return filter.equals("invite")?type.contains("invite"):type.contains("friend");
    }
    private static JSONObject parseDetails(JSONObject src){
        JSONObject val=src.optJSONObject("details");
        if(val!=null)return val;
        String raw=src.optString("details","");
        if(!raw.isEmpty()&&raw.startsWith("{")){
            try{return new JSONObject(raw);}catch(Exception ignored){}
        }
        JSONObject data=src.optJSONObject("data");
        if(data!=null){
            JSONObject d=data.optJSONObject("details");
            if(d!=null)return d;
            String inner=data.optString("details","");
            if(inner.startsWith("{")){
                try{return new JSONObject(inner);}catch(Exception ignored){}
            }
            return data;
        }
        return new JSONObject();
    }
    private static String location(JSONObject entry,JSONObject details){
        for(String name:new String[]{"worldId","location","targetLocation","instanceId"}){
            String v=details.optString(name,"");
            if(v.startsWith("wrld_")&&v.contains(":"))return v;
        }
        for(String name:new String[]{"worldId","location"}){
            String v=entry.optString(name,"");
            if(v.startsWith("wrld_")&&v.contains(":"))return v;
        }
        return "";
    }
    private static long timestamp(String value){
        try{return java.time.Instant.parse(value).toEpochMilli();}
        catch(Exception ignored){
            try{return java.time.OffsetDateTime.parse(value).toInstant().toEpochMilli();}
            catch(Exception ignored2){return 0;}
        }
    }
    private static Entry decode(JSONObject n,boolean modern){
        Entry e=new Entry();e.raw=n;e.modern=modern;
        e.details=parseDetails(n);
        e.id=n.optString("id","");
        e.type=n.optString("type","Notice");
        e.name=n.optString("senderUsername",n.optString("senderDisplayName",
            n.optString("title","VRChat")));
        e.message=n.optString("message",n.optString("description",""));
        e.date=n.optString("created_at",n.optString("createdAt",""));
        e.sort=timestamp(e.date);
        e.sender=n.optString("senderUserId",e.details.optString("senderUserId",""));
        e.location=location(n,e.details);
        e.seen=n.optBoolean("seen",false);
        return e;
    }
    private void load(boolean append){
        if(busy||!StaffGate.unlocked(this))return;
        busy=true;
        if(refreshButton!=null)refreshButton.setEnabled(false);
        if(more!=null)more.setEnabled(false);
        if(!append){legacyOffset=0;modernOffset=0;}
        status.setText("Checking your VRChat inbox...");
        final int old=legacyOffset,now=modernOffset;
        worker.execute(()->{
            JSONArray legacy=null,modern=null;
            String failures="";
            try{legacy=VRChatApi.notifications(this,false,old);}
            catch(Exception ex){failures+="Older notifications unavailable. ";}
            try{modern=VRChatApi.notifications(this,true,now);}
            catch(Exception ex){failures+="Some notifications unavailable. ";}
            final JSONArray a=legacy,b=modern;
            final String fail=failures;
            runOnUiThread(()->{
                busy=false;
                if(refreshButton!=null)refreshButton.setEnabled(true);
                if(more!=null)more.setEnabled(true);
                if(isFinishing()||isDestroyed()||!StaffGate.unlocked(this))return;
                if(a==null&&b==null){
                    status.setText(fail.isEmpty()?"Could not load notifications.":fail);
                    return;
                }
                LinkedHashMap<String,Entry> merged=new LinkedHashMap<>();
                if(append){
                    for(Entry previous:entries)
                        merged.put((previous.modern?"2:":"1:")+previous.id,previous);
                }
                if(a!=null){
                    for(int i=0;i<a.length();i++){
                        JSONObject item=a.optJSONObject(i);if(item==null)continue;
                        Entry e=decode(item,false);
                        merged.put("1:"+e.id,e);
                    }
                    legacyOffset+=a.length();
                }
                if(b!=null){
                    for(int i=0;i<b.length();i++){
                        JSONObject item=b.optJSONObject(i);if(item==null)continue;
                        Entry e=decode(item,true);
                        merged.put("2:"+e.id,e);
                    }
                    modernOffset+=b.length();
                }
                entries.clear();entries.addAll(merged.values());
                entries.sort((x,y)->Long.compare(y.sort,x.sort));
                adapter.rebuild();
                updateStatus();
                if(!fail.isEmpty())status.setText(status.getText()+" · Partial inbox");
                more.setVisibility((a!=null&&a.length()==60)||(b!=null&&b.length()==60)
                    ?View.VISIBLE:View.GONE);
            });
        });
    }
    private void action(Entry e,String label,Runnable confirmed){
        new AlertDialog.Builder(this).setTitle(label+"?")
            .setMessage("This changes the notification on your personal VRChat account.")
            .setNegativeButton("Cancel",null)
            .setPositiveButton(label,(d,w)->confirmed.run()).show();
    }
    private void perform(Entry e,String kind){
        if(!StaffGate.unlocked(this)){finish();return;}
        status.setText("Updating your VRChat inbox...");
        worker.execute(()->{
            String error=null;
            try{
                if(kind.equals("seen"))VRChatApi.markNotificationSeen(this,e.id,e.modern);
                else if(kind.equals("hide"))VRChatApi.dismissNotification(this,e.id,e.modern);
                else if(kind.equals("accept")&&!e.modern)VRChatApi.acceptFriendRequest(this,e.id);
                else throw new IllegalArgumentException("Unsupported notification action");
            }catch(Exception ex){
                error=ex.getMessage();if(error==null)error="Action unavailable";
            }
            final String fail=error;
            runOnUiThread(()->{
                if(fail!=null){
                    status.setText("Could not update: "+fail);return;
                }
                status.setText("Updated successfully");
                if(kind.equals("seen"))e.seen=true;
                else entries.remove(e);
                adapter.rebuild();
                updateStatus();
            });
        });
    }
    private void open(Entry e){
        ArrayList<String> options=new ArrayList<>();
        ArrayList<Runnable> actions=new ArrayList<>();
        if(e.location.length()>0){
            options.add("View invited instance");
            actions.add(()->startActivity(new Intent(this,VRChatInstanceActivity.class)
                .putExtra("location",e.location)));
        }
        if(e.sender.matches("usr_[0-9a-fA-F-]{36}")){
            options.add("View sender profile");
            actions.add(()->startActivity(new Intent(this,VRChatDetailActivity.class)
                .putExtra("kind","user").putExtra("id",e.sender)));
        }
        if(!e.seen){
            options.add("Mark as read");
            actions.add(()->perform(e,"seen"));
        }
        if(!e.modern && e.type.equalsIgnoreCase("friendRequest")){
            options.add("Accept friend request");
            actions.add(()->action(e,"Accept friend request",()->perform(e,"accept")));
        }
        options.add("Dismiss notification");
        actions.add(()->action(e,"Dismiss notification",()->perform(e,"hide")));
        // A custom action sheet displays the message AND all action controls.
        // AlertDialog.setMessage() together with setItems() can hide the item list.
        String details=e.message.isEmpty()?e.type:e.message;
        LinearLayout sheet=new LinearLayout(this);
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setPadding(dp(18),dp(8),dp(18),dp(7));
        TextView body=txt(details,14,CompanionStyle.TEXT,false);
        body.setTextIsSelectable(true);
        sheet.addView(body);
        View divider=new View(this);
        sheet.addView(divider,new LinearLayout.LayoutParams(1,dp(13)));
        AlertDialog dialog=new AlertDialog.Builder(this)
            .setTitle(e.name)
            .setView(sheet)
            .setNegativeButton("Close",null).create();
        for(int i=0;i<options.size();i++){
            final Runnable action=actions.get(i);
            Button button=CompanionStyle.button(this,options.get(i),false);
            sheet.addView(button,CompanionStyle.buttonParams(this));
            button.setOnClickListener(v->{dialog.dismiss();action.run();});
        }
        dialog.show();
    }
    private final class Holder extends RecyclerView.ViewHolder{
        TextView icon,name,message,when;
        Holder(LinearLayout row,TextView icon,TextView name,TextView message,TextView when){
            super(row);this.icon=icon;this.name=name;this.message=message;this.when=when;
        }
    }
    private final class InboxAdapter extends RecyclerView.Adapter<Holder>{
        private final ArrayList<Entry> shown=new ArrayList<>();
        void rebuild(){
            shown.clear();
            for(Entry e:entries)if(include(e))shown.add(e);
            notifyDataSetChanged();
        }
        @Override public Holder onCreateViewHolder(ViewGroup parent,int type){
            LinearLayout row=CompanionStyle.card(VRChatInboxActivity.this);
            RecyclerView.LayoutParams lp=new RecyclerView.LayoutParams(-1,-2);
            lp.bottomMargin=dp(9);row.setLayoutParams(lp);
            LinearLayout layout=new LinearLayout(VRChatInboxActivity.this);
            layout.setGravity(Gravity.CENTER_VERTICAL);
            row.addView(layout);
            TextView symbol=txt("✉",23,CompanionStyle.ACCENT,true);
            symbol.setGravity(Gravity.CENTER);
            layout.addView(symbol,new LinearLayout.LayoutParams(dp(39),dp(42)));
            LinearLayout body=new LinearLayout(VRChatInboxActivity.this);
            body.setOrientation(LinearLayout.VERTICAL);
            body.setPadding(dp(7),0,0,0);
            layout.addView(body,new LinearLayout.LayoutParams(0,-2,1f));
            TextView name=txt("",15,CompanionStyle.TEXT,true);body.addView(name);
            TextView description=txt("",12,CompanionStyle.MUTED,false);
            description.setMaxLines(3);body.addView(description);
            TextView when=txt("",11,CompanionStyle.ACCENT,false);body.addView(when);
            return new Holder(row,symbol,name,description,when);
        }
        @Override public void onBindViewHolder(Holder h,int position){
            Entry e=shown.get(position);
            h.icon.setText(e.type.toLowerCase(Locale.US).contains("invite")?"➜":
                e.type.toLowerCase(Locale.US).contains("friend")?"☺":"✉");
            h.name.setText(e.name);
            h.message.setText(e.message.isEmpty()?e.type:e.message);
            h.when.setText((e.seen?"":"●  ")+e.date.replace('T',' ').replace('Z',' '));
            h.itemView.setOnClickListener(v->open(e));
        }
        @Override public int getItemCount(){return shown.size();}
    }
    @Override protected void onDestroy(){worker.shutdownNow();super.onDestroy();}
}
