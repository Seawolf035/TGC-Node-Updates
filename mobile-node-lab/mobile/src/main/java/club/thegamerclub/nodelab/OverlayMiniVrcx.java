package club.thegamerclub.nodelab;

import android.app.Service;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Native, fully in-overlay Mini VRCX pages. No Activity launches.
 * All network/file operations stay off the main thread; private VRChat credentials
 * are only used by VRChatApi on this device, never the TGC Relay.
 */
public final class OverlayMiniVrcx {
    public interface Host {
        void navigate(String page);
        void back();
        void keyboard(boolean focus);
    }
    private final Service ctx;
    private final Host host;
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private LinearLayout root;
    private String screen="",target="",kind="";
    private String selectedGroupId="";
    private int memberOffset=0,banOffset=0;
    private int revision=0;
    private String lastError="";
    private boolean busy=false;
    private JSONArray loaded=new JSONArray();
    private JSONObject profile=new JSONObject();
    private LinearLayout list;
    private EditText search;
    private int limit=65;
    private String inboxFilter="all";
    private VRChatInviteCode.Result verificationResult;
    private final Runnable verificationTick=new Runnable(){
        @Override public void run(){
            if(root==null||!"mini_verify".equals(screen))return;
            refreshVerification();
            handler.postDelayed(this,45_000L);
        }
    };
    private boolean canType=false;
    private String pending="";
    private Runnable pendingAction;
    private final android.os.Handler handler=new android.os.Handler(android.os.Looper.getMainLooper());
    public OverlayMiniVrcx(Service service,Host host){ctx=service;this.host=host;}
    private int dp(float n){return CompanionStyle.dp(ctx,n);}
    private TextView text(String s,int n,int color,boolean bold){
        return CompanionStyle.text(ctx,s,n,color,bold);
    }
    public void close(){
        handler.removeCallbacks(verificationTick);
        revision++;root=null;list=null;search=null;pendingAction=null;
        host.keyboard(false);
    }
    public void shutdown(){close();io.shutdownNow();}
    public void profile(String userId){
        if(validUser(userId)){target=userId;kind="user";host.navigate("mini_profile");}
    }
    public void world(String id){
        if(id!=null&&id.matches("wrld_[0-9a-fA-F-]{36}")){
            target=id;kind="world";host.navigate("mini_world");
        }
    }
    public void findUser(){host.navigate("mini_search");}
    public void verification(){host.navigate("mini_verify");}
    public void note(String userId){
        if(validUser(userId)){target=userId;kind="note";host.navigate("mini_note");}
    }
    public void inbox(String type){inboxFilter=type;host.navigate("mini_inbox");}
    public void location(String value){
        if(value!=null&&value.startsWith("wrld_")&&value.contains(":")){
            target=value;kind="location";host.navigate("mini_instance");
        }
    }
    public static boolean validUser(String value){
        return value!=null&&value.matches("usr_[0-9a-fA-F-]{36}");
    }
    private void copy(String label,String value){
        ClipboardManager clipboard=(ClipboardManager)ctx.getSystemService(Context.CLIPBOARD_SERVICE);
        if(clipboard!=null)clipboard.setPrimaryClip(ClipData.newPlainText(label,value));
        message("Copied "+label);
    }
    private void message(String text){
        if(root==null)return;
        TextView n=text(text,12,CompanionStyle.GOLD,false);
        root.addView(n,1);
    }
    public void show(LinearLayout parent,String page){
        close();
        root=parent;screen=page;lastError="";busy=false;loaded=new JSONArray();profile=new JSONObject();
        limit=65;pending="";revision++;
        if("mini_verify".equals(page))verificationResult=null;
        drawFrame();
        load();
        if("mini_verify".equals(page))
            handler.postDelayed(verificationTick,45_000L);
    }
    private void drawFrame(){
        if(root==null)return;
        root.removeAllViews();
        LinearLayout bar=new LinearLayout(ctx);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(bar);
        Button back=CompanionStyle.button(ctx,"‹ Back",false);
        bar.addView(back,new LinearLayout.LayoutParams(dp(88),dp(40)));
        back.setOnClickListener(v->host.back());
        TextView heading=text(title(),18,CompanionStyle.TEXT,true);
        heading.setPadding(dp(9),0,0,0);
        bar.addView(heading,new LinearLayout.LayoutParams(0,-2,1));
        if(!"mini_profile".equals(screen)&&!"mini_note".equals(screen)){
            Button refresh=CompanionStyle.button(ctx,"↻",false);
            bar.addView(refresh,new LinearLayout.LayoutParams(dp(40),dp(40)));
            refresh.setOnClickListener(v->{if(search!=null)search.clearFocus();host.keyboard(false);load();});
        }
        TextView explanation=text(subtitle(),12,CompanionStyle.MUTED,false);
        explanation.setPadding(0,dp(6),0,dp(8));
        root.addView(explanation);
        if(isSearchable()){
            search=CompanionStyle.input(ctx,"Filter by name, ID, or details");
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(43));
            p.bottomMargin=dp(9);root.addView(search,p);
            search.setOnFocusChangeListener((v,focus)->host.keyboard(focus));
            search.setOnTouchListener((v,event)->{
                if(event.getActionMasked()==android.view.MotionEvent.ACTION_DOWN)
                    host.keyboard(true);
                return false;
            });
            search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
            if("mini_search".equals(screen)||"mini_members".equals(screen)){
                search.setHint("mini_members".equals(screen)?
                    "Search member name (3+ letters)":"Search user (2+ letters)");
                search.setSingleLine(true);
                search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
                search.setOnEditorActionListener((v,action,event)->{
                    if(action==EditorInfo.IME_ACTION_SEARCH){
                        search.clearFocus();host.keyboard(false);load();return true;
                    }
                    return false;
                });
            }
            search.addTextChangedListener(new android.text.TextWatcher(){
                public void beforeTextChanged(CharSequence x,int a,int c,int d){}
                public void onTextChanged(CharSequence x,int start,int before,int count){drawList();}
                public void afterTextChanged(android.text.Editable x){}
            });
        }else search=null;
        list=new LinearLayout(ctx);
        list.setOrientation(LinearLayout.VERTICAL);
        root.addView(list);
        status("Loading...");
    }
    private boolean isSearchable(){
        return screen.equals("mini_players")||screen.equals("mini_current")||
            screen.equals("mini_joins")||screen.equals("mini_friends")||
            screen.equals("mini_log")||screen.equals("mini_activity")||
            screen.equals("mini_worlds")||
            screen.equals("mini_avatars")||screen.equals("mini_groups")||
            screen.equals("mini_search")||screen.equals("mini_members");
    }
    private String title(){
        switch(screen){
            case "mini_players":return "Player log";
            case "mini_current":return "Current players";
            case "mini_joins":return "Join & leave history";
            case "mini_friends":return "Friends & locations";
            case "mini_log":return "Friend log";
            case "mini_activity":return "Activity feed";
            case "mini_inbox":return "Notifications";
            case "mini_verify":return "Verification code";
            case "mini_profile":return "Player profile";
            case "mini_worlds":return "Saved worlds";
            case "mini_avatars":return "Favorite avatars";
            case "mini_groups":return "Your groups";
            case "mini_group":return "Group details";
            case "mini_members":return "Group members";
            case "mini_instances":return "Group instances";
            case "mini_bans":return "Group bans";
            case "mini_search":return "Search users";
            case "mini_world":return "World details";
            case "mini_instance":return "Instance details";
            case "mini_presence":return "Your status";
            case "mini_note":return "Private VRChat note";
            default:return "Floating tools";
        }
    }
    private String subtitle(){
        switch(screen){
            case "mini_players":return "People you have met · saved on this phone";
            case "mini_current":return "Observed since monitoring started · may be incomplete";
            case "mini_joins":return "Locally recorded joins, leaves and instance changes";
            case "mini_friends":return "Your personal VRChat friends · only visible locations";
            case "mini_log":return "Observed friend changes since logging began";
            case "mini_activity":return "Locally recorded encounters and friend activity";
            case "mini_inbox":return "Use your personal VRChat account without switching apps";
            case "mini_verify":return "Read and copy your private invite code without leaving VRChat";
            case "mini_profile":return "Personal VRChat account profile details";
            case "mini_note":return "Notes are saved to your personal VRChat account";
            case "mini_presence":return "Change your personal status without leaving VRChat";
            default:return "Use these tools without leaving VRChat";
        }
    }
    private void status(String message){
        if(list==null)return;
        list.removeAllViews();
        TextView status=text(message,13,CompanionStyle.MUTED,false);
        status.setPadding(dp(5),dp(11),dp(5),dp(10));list.addView(status);
    }
    private LinearLayout card(LinearLayout destination){
        LinearLayout item=CompanionStyle.card(ctx);
        destination.addView(item,CompanionStyle.cardParams(ctx));
        return item;
    }
    private void line(LinearLayout parent,String title,String details,Runnable action){
        LinearLayout item=card(parent);
        TextView primary=text(title,14,CompanionStyle.TEXT,true);
        item.addView(primary);
        if(details!=null&&!details.trim().isEmpty()){
            TextView secondary=text(details,12,CompanionStyle.MUTED,false);
            secondary.setPadding(0,dp(5),0,0);item.addView(secondary);
        }
        if(action!=null){
            TextView hint=text("Tap for details  ›",11,CompanionStyle.ACCENT,false);
            hint.setPadding(0,dp(7),0,0);item.addView(hint);
            item.setOnClickListener(v->action.run());
        }
    }
    private void button(LinearLayout parent,String label,boolean primary,Runnable run){
        Button b=CompanionStyle.button(ctx,label,primary);
        parent.addView(b,CompanionStyle.buttonParams(ctx));
        b.setOnClickListener(v->run.run());
    }
    private void chips(LinearLayout parent,String a,String b,String c,
                       Runnable one,Runnable two,Runnable three){
        LinearLayout row=new LinearLayout(ctx);row.setOrientation(LinearLayout.HORIZONTAL);
        parent.addView(row);
        String[] labels={a,b,c};
        Runnable[] actions={one,two,three};
        for(int i=0;i<3;i++){
            final Runnable fn=actions[i];
            Button btn=CompanionStyle.button(ctx,labels[i],false);
            btn.setTextSize(11);
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(43),1);
            p.setMargins(dp(1),0,dp(1),dp(7));
            row.addView(btn,p);
            btn.setOnClickListener(v->fn.run());
        }
    }
    private String term(){
        return search==null?"":search.getText().toString().trim().toLowerCase(Locale.US);
    }
    private boolean matches(String value){return value.toLowerCase(Locale.US).contains(term());}
    private String date(long millis){
        return millis<=0?"Time not available":DateFormat.getDateTimeInstance(
            DateFormat.SHORT,DateFormat.SHORT).format(new Date(millis));
    }
    private String shortValue(String text,int max){
        if(text==null)return "";
        return text.length()>max?text.substring(0,max-1)+"…":text;
    }
    private void execute(Work work,Done done){
        if(!StaffGate.unlocked(ctx)){host.back();return;}
        if(busy)return;
        busy=true;
        final int token=revision;
        status("Loading "+title().toLowerCase(Locale.US)+"...");
        io.execute(()->{
            Object result=null;String err=null;
            try{result=work.run();}
            catch(Exception exception){err=exception.getMessage();if(err==null)err="Unable to load data";}
            final Object data=result;final String error=err;
            handler.post(()->{
                if(token!=revision||root==null||!StaffGate.unlocked(ctx))return;
                busy=false;
                if(error!=null){
                    lastError=error;status("Couldn't load: "+shortValue(error,160));
                }else{
                    lastError="";done.accept(data);
                }
            });
        });
    }
    private interface Work{Object run()throws Exception;}
    private interface Done{void accept(Object result);}
    private void load(){
        switch(screen){
            case "mini_current":
                execute(()->{
                    try(MobileOutbox db=new MobileOutbox(ctx)){
                        return db.currentPlayers(250);
                    }
                },result->{loaded=(JSONArray)result;drawList();});break;
            case "mini_joins":
                execute(()->{
                    try(MobileOutbox db=new MobileOutbox(ctx)){
                        return db.recentLocalActivity(300);
                    }
                },result->{loaded=(JSONArray)result;drawList();});break;
            case "mini_players":
                execute(()->{
                    JSONArray data=new JSONArray();
                    try(MobileOutbox db=new MobileOutbox(ctx)){
                        JSONArray seen=db.recentEncounters(100),recent=db.recentTrackedEvents(150);
                        return new JSONObject().put("seen",seen).put("recent",recent).put("pending",db.count());
                    }
                },result->{profile=(JSONObject)result;drawList();});break;
            case "mini_friends":
                execute(()->{
                    JSONObject session=VRChatSession.load(ctx);
                    JSONArray fresh=VRChatApi.friendSnapshot(ctx);
                    FriendLogStore.observe(ctx,session,fresh);
                    return fresh;
                },result->{loaded=(JSONArray)result;drawList();});break;
            case "mini_log":
                execute(()->FriendLogStore.events(ctx,VRChatSession.load(ctx)),
                    result->{loaded=(JSONArray)result;drawList();});break;
            case "mini_activity":
                execute(()->{
                    JSONObject result=new JSONObject();
                    result.put("friends",FriendLogStore.events(ctx,VRChatSession.load(ctx)));
                    try(MobileOutbox db=new MobileOutbox(ctx)){
                        JSONArray local=db.recentLocalActivity(150);
                        result.put("players",local.length()>0?local:db.recentTrackedEvents(150));
                    }
                    return result;
                },result->{profile=(JSONObject)result;drawList();});break;
            case "mini_verify":
                execute(()->VRChatInviteCode.scanLatest(ctx),result->{
                    verificationResult=(VRChatInviteCode.Result)result;
                    drawList();
                });
                break;
            case "mini_inbox":
                execute(()->{
                    JSONObject result=new JSONObject();
                    try{result.put("legacy",VRChatApi.notifications(ctx,false,0));}
                    catch(Exception e){result.put("legacyError",shortValue(e.getMessage(),100));}
                    try{result.put("modern",VRChatApi.notifications(ctx,true,0));}
                    catch(Exception e){result.put("modernError",shortValue(e.getMessage(),100));}
                    if(!result.has("legacy")&&!result.has("modern"))
                        throw new IllegalStateException("VRChat notifications unavailable");
                    return result;
                },result->{profile=(JSONObject)result;drawList();});break;
            case "mini_profile":
                execute(()->VRChatApi.getUserProfile(ctx,target),
                    result->{profile=(JSONObject)result;drawList();});break;
            case "mini_world":
                execute(()->VRChatApi.getWorld(ctx,target),
                    result->{profile=(JSONObject)result;drawList();});break;
            case "mini_instance":
                execute(()->VRChatApi.getInstance(ctx,target),
                    result->{profile=(JSONObject)result;drawList();});break;
            case "mini_avatars":
                execute(()->VRChatApi.favoriteAvatars(ctx,0),
                    result->{loaded=(JSONArray)result;drawList();});break;
            case "mini_groups":
                execute(()->VRChatApi.groups(ctx),
                    result->{loaded=(JSONArray)result;drawList();});break;
            case "mini_group":
                execute(()->VRChatApi.getGroup(ctx,selectedGroupId),
                    result->{profile=(JSONObject)result;drawList();});break;
            case "mini_members":
                execute(()->{
                    String term=term();
                    if(term.length()>=3)return VRChatApi.searchGroupMembers(ctx,selectedGroupId,term);
                    return VRChatApi.groupMembers(ctx,selectedGroupId,memberOffset);
                },result->{loaded=(JSONArray)result;drawList();});break;
            case "mini_instances":
                execute(()->VRChatApi.groupInstances(ctx,selectedGroupId),
                    result->{loaded=(JSONArray)result;drawList();});break;
            case "mini_bans":
                execute(()->VRChatApi.groupBans(ctx,selectedGroupId,banOffset),
                    result->{loaded=(JSONArray)result;drawList();});break;
            case "mini_search":
                if(term().length()<2){status("Enter at least two characters, then tap Search.");break;}
                execute(()->VRChatApi.searchUsers(ctx,search.getText().toString().trim()),
                    result->{loaded=(JSONArray)result;drawList();});break;
            case "mini_worlds":
                execute(()->VRChatApi.browsePage(ctx,"worlds","",0),
                    result->{loaded=(JSONArray)result;drawList();});break;
            case "mini_presence":
                execute(()->VRChatApi.currentUser(ctx),
                    result->{profile=(JSONObject)result;drawList();});break;
            case "mini_note":
                execute(()->{
                    JSONArray all=new JSONArray();
                    for(int offset=0;offset<=1000;offset+=100){
                        JSONArray page=VRChatApi.userNotes(ctx,offset);
                        for(int i=0;i<page.length();i++){
                            JSONObject note=page.optJSONObject(i);
                            if(note!=null&&target.equals(note.optString("targetUserId","")))
                                return note;
                        }
                        if(page.length()<100)break;
                    }
                    return new JSONObject();
                },result->{profile=(JSONObject)result;drawList();});break;
            default:status("Choose a tool.");
        }
    }
    private void drawList(){
        if(list==null||busy)return;
        list.removeAllViews();
        switch(screen){
            case "mini_current":showCurrentPlayers();break;
            case "mini_joins":showJoinsAndLeaves();break;
            case "mini_players":showPlayers();break;
            case "mini_friends":showFriends();break;
            case "mini_log":showLog();break;
            case "mini_activity":showActivity();break;
            case "mini_inbox":showInbox();break;
            case "mini_verify":showVerification();break;
            case "mini_profile":showProfile();break;
            case "mini_world":showWorld();break;
            case "mini_instance":showInstance();break;
            case "mini_worlds":showWorlds();break;
            case "mini_avatars":showAvatars();break;
            case "mini_groups":showGroups();break;
            case "mini_group":showGroup();break;
            case "mini_members":showMembers();break;
            case "mini_instances":showGroupInstances();break;
            case "mini_bans":showBans();break;
            case "mini_search":showSearch();break;
            case "mini_presence":showPresence();break;
            case "mini_note":showNote();break;
            default:status("Choose a tool.");
        }
    }
    private void showCurrentPlayers(){
        android.content.SharedPreferences status=ctx.getSharedPreferences("tgc_mobile_node_status",0);
        boolean live=status.getBoolean("running",false)&&
            System.currentTimeMillis()-status.getLong("service_heartbeat_at",0L)<90_000L;
        line(list,live?"● Tracking is active":"○ Tracking is paused",
            "Only players whose joins were detected during this monitoring session are shown. This may not be the complete VRChat instance.",null);
        int shown=0;
        for(int i=0;i<loaded.length()&&shown<limit;i++){
            JSONObject item=loaded.optJSONObject(i);if(item==null)continue;
            String uid=item.optString("user_id","");
            String name=item.optString("display_name","");
            if(name.isEmpty())name=uid;
            String location=item.optString("location","");
            if(!matches(name+" "+uid+" "+location))continue;
            line(list,name,"Joined "+date(item.optLong("time"))+
                (location.isEmpty()?"":" · "+shortValue(location,65)),
                validUser(uid)?()->profile(uid):null);
            shown++;
        }
        if(shown==0)line(list,"No observed players",
            "Start tracking while playing VRChat Mobile to see new joins. Players already there when tracking started may not appear.",null);
    }
    private void showJoinsAndLeaves(){
        int shown=0;
        for(int i=0;i<loaded.length()&&shown<limit;i++){
            JSONObject e=loaded.optJSONObject(i);if(e==null)continue;
            String kind=e.optString("kind","");
            String id=e.optString("user_id","");
            String name=e.optString("display_name","");
            String location=e.optString("location","");
            if(name.isEmpty())name=id;
            if(!matches(kind+" "+name+" "+id+" "+location))continue;
            String label="join".equals(kind)?"Joined":
                "leave".equals(kind)?"Left":"Changed world";
            String title="instance".equals(kind)?"World / instance changed":label+" · "+name;
            line(list,title,date(e.optLong("time"))+
                (location.isEmpty()?"":" · "+shortValue(location,75)),
                validUser(id)?()->profile(id):null);
            shown++;
        }
        if(shown==0)line(list,"No recorded join/leave history",
            "This list starts when tracking is enabled; earlier sessions cannot be reconstructed.",null);
        if(shown>=limit)button(list,"Show more",false,()->{limit+=65;drawList();});
    }
    private void showPlayers(){
        JSONArray seen=profile.optJSONArray("seen"),recent=profile.optJSONArray("recent");
        if(seen==null)seen=new JSONArray();
        if(recent==null)recent=new JSONArray();
        TextView counts=text(seen.length()+" known players · "+
            recent.length()+" captured log entries · "+profile.optInt("pending",0)+
            " waiting to send",12,CompanionStyle.ACCENT,false);
        list.addView(counts);
        int items=0;
        for(int i=0;i<seen.length()&&items<limit;i++){
            JSONObject e=seen.optJSONObject(i);
            if(e==null)continue;
            String id=e.optString("user_id","");
            String name=e.optString("display_name",id);
            if(!matches(name+" "+id))continue;
            line(list,shortValue(name,56),"Encounters: "+e.optInt("count",0)+
                " · Last seen: "+e.optString("last_seen","unknown"),()->profile(id));
            items++;
        }
        if(items==0)line(list,"No matching players",
            "Start Mobile Node tracking to collect encounters.",null);
        button(list,"Observed current players",false,()->host.navigate("mini_current"));
        button(list,"Join & leave history",false,()->host.navigate("mini_joins"));
        button(list,"Activity feed",false,()->host.navigate("mini_activity"));
    }
    private void showFriends(){
        LinkedHashMap<String,ArrayList<JSONObject>> grouped=new LinkedHashMap<>();
        int online=0;
        for(int i=0;i<loaded.length();i++){
            JSONObject f=loaded.optJSONObject(i);
            if(f==null)continue;
            boolean active=f.optBoolean("isOnline",false);
            if(active)online++;
            String loc=active?f.optString("location","unknown"):"Offline";
            if(loc.isEmpty()||loc.equals("private")||loc.equals("unknown"))loc="Private / hidden";
            if(!grouped.containsKey(loc))grouped.put(loc,new ArrayList<>());
            grouped.get(loc).add(f);
        }
        list.addView(text(online+" online · "+loaded.length()+" total friends",
            12,CompanionStyle.ACCENT,false));
        ArrayList<String> keys=new ArrayList<>(grouped.keySet());
        Collections.sort(keys,(a,b)->{
            if("Offline".equals(a))return 1;if("Offline".equals(b))return -1;
            return Integer.compare(grouped.get(b).size(),grouped.get(a).size());
        });
        int shown=0;
        for(String key:keys){
            ArrayList<JSONObject> group=grouped.get(key);
            int matching=0;
            for(JSONObject f:group)
                if(matches(f.optString("displayName","")+" "+key))matching++;
            if(matching==0)continue;
            if(shown>=limit)break;
            LinearLayout c=card(list);
            String heading=key.startsWith("wrld_")?"Instance: "+shortValue(key,42):key;
            c.addView(text(heading+" · "+matching,13,CompanionStyle.ACCENT,true));
            if(key.startsWith("wrld_")&&key.contains(":"))
                button(c,"Instance info",false,()->location(key));
            for(JSONObject f:group){
                String id=f.optString("id","");
                String name=f.optString("displayName",id);
                if(!matches(name+" "+key))continue;
                TextView row=text("› "+shortValue(name,48)+"  •  "+
                    f.optString("status",""),13,CompanionStyle.TEXT,false);
                row.setPadding(0,dp(7),0,dp(7));c.addView(row);
                row.setOnClickListener(v->profile(id));
            }
            shown++;
        }
        if(shown==0)line(list,"No matching friends","Try another filter.",null);
    }
    private void showLog(){
        int seen=0;
        for(int i=loaded.length()-1;i>=0&&seen<limit;i--){
            JSONObject e=loaded.optJSONObject(i);
            if(e==null)continue;
            String name=e.optString("name","Friend"),type=e.optString("type","");
            String location=e.optString("location","");
            if(!matches(name+" "+type+" "+location))continue;
            String id=e.optString("userId","");
            String detail=type.replace('_',' ')+
                (type.equals("location")?" · "+shortValue(location,50):"");
            line(list,name,detail+" · "+date(e.optLong("time")),()->profile(id));
            seen++;
        }
        if(seen==0)line(list,"No recorded changes",
            "Events begin after the first successful friend snapshot.",null);
        if(seen>=limit)button(list,"Show more",false,()->{limit+=65;drawList();});
    }
    private void showActivity(){
        ArrayList<JSONObject> results=new ArrayList<>();
        JSONArray events=profile.optJSONArray("players");
        if(events!=null)for(int i=0;i<events.length();i++){
            JSONObject item=events.optJSONObject(i);
            if(item==null)continue;
            try{
                String kind=item.optString("kind","");
                String label="join".equals(kind)?"Joined":
                    "leave".equals(kind)?"Left":
                    "instance".equals(kind)?"Changed instance":"Player encountered";
                String person="instance".equals(kind)?"World / instance":
                    item.optString("display_name","Player");
                if(person.isEmpty())person=item.optString("user_id","Player");
                results.add(new JSONObject().put("time",item.optLong("time",
                        item.optLong("local_seen_at")))
                    .put("name",person)
                    .put("type",label+
                        (item.optString("location","").isEmpty()?"":" · "+shortValue(item.optString("location",""),75)))
                    .put("user",item.optString("user_id","")));
            }catch(Exception ignored){}
        }
        JSONArray friends=profile.optJSONArray("friends");
        if(friends!=null)for(int i=0;i<friends.length();i++){
            JSONObject e=friends.optJSONObject(i);
            if(e==null)continue;
            try{
                results.add(new JSONObject().put("time",e.optLong("time"))
                    .put("name",e.optString("name","Friend"))
                    .put("type","Friend: "+e.optString("type","event"))
                    .put("user",e.optString("userId","")));
            }catch(Exception ignored){}
        }
        Collections.sort(results,(a,b)->Long.compare(b.optLong("time"),a.optLong("time")));
        int shown=0;
        for(JSONObject row:results){
            if(shown++>=limit)break;
            String id=row.optString("user","");
            line(list,row.optString("name"),row.optString("type")+
                " · "+date(row.optLong("time")),()->profile(id));
        }
        if(results.isEmpty())line(list,"No recent recorded events",
            "The activity feed fills as monitoring collects data.",null);
        if(results.size()>limit)button(list,"Show more",false,()->{limit+=65;drawList();});
    }
    private JSONArray inboxEntries(){
        JSONArray entries=new JSONArray();
        HashMap<String,Boolean> seen=new HashMap<>();
        for(boolean modern:new boolean[]{false,true}){
            JSONArray source=profile.optJSONArray(modern?"modern":"legacy");
            if(source==null)continue;
            for(int i=0;i<source.length();i++){
                JSONObject n=source.optJSONObject(i);if(n==null)continue;
                String type=n.optString("type","").toLowerCase(Locale.US);
                boolean invite=type.contains("invite"),friend=type.contains("friend");
                if(inboxFilter.equals("invite")&&!invite)continue;
                if(inboxFilter.equals("friend")&&!friend)continue;
                String id=n.optString("id","");
                if(id.isEmpty()||seen.put((modern?"2:":"1:")+id,true)!=null)continue;
                try{entries.put(new JSONObject(n.toString()).put("_modern",modern));}
                catch(Exception ignored){}
            }
        }
        return entries;
    }
    private JSONObject details(JSONObject n){
        JSONObject d=n.optJSONObject("details");
        if(d!=null)return d;
        String raw=n.optString("details","");
        if(raw.startsWith("{"))try{return new JSONObject(raw);}catch(Exception ignored){}
        JSONObject data=n.optJSONObject("data");
        if(data==null)return new JSONObject();
        JSONObject nested=data.optJSONObject("details");
        if(nested!=null)return nested;
        raw=data.optString("details","");
        if(raw.startsWith("{"))try{return new JSONObject(raw);}catch(Exception ignored){}
        return data;
    }
    private String inviteLocation(JSONObject n){
        JSONObject d=details(n);
        for(String key:new String[]{"location","targetLocation","worldId","instanceId"}){
            String value=d.optString(key,"");
            if(value.startsWith("wrld_")&&value.contains(":"))return value;
        }
        for(String key:new String[]{"location","worldId"}){
            String value=n.optString(key,"");
            if(value.startsWith("wrld_")&&value.contains(":"))return value;
        }
        return "";
    }
    private void showVerification(){
        LinearLayout heading=card(list);
        heading.addView(text("Automatic invite code",16,CompanionStyle.TEXT,true));
        heading.addView(text(
            "Start verification in Gamer Music 3 or Gamer Music 2's Discord. "
            +"The latest available TGC-XXXX-XXXX or CULT-XXXX-XXXX code will appear here automatically.",
            12,CompanionStyle.MUTED,false));
        VRChatInviteCode.Result found=verificationResult;
        if(found==null){
            heading.addView(text("Checking recent invites…",13,CompanionStyle.MUTED,false));
        }else if(!found.code.isEmpty()){
            String service="cult".equals(found.provider)?"Gamer Music 2 · Cult":"Gamer Music 3 · TGC";
            heading.addView(text(service,12,CompanionStyle.ACCENT,true));
            TextView label=text(found.code,21,CompanionStyle.GOOD,true);
            label.setGravity(Gravity.CENTER);
            label.setPadding(0,dp(12),0,dp(12));
            label.setBackground(CompanionStyle.shape(ctx,CompanionStyle.RAISED,12,0));
            heading.addView(label);
            String value=found.code;
            button(heading,"Copy code",true,()->copy("verification code",value));
            heading.addView(text(
                "Paste this code into the same "+("cult".equals(found.provider)?"Cult":"TGC")+
                " verification prompt you started in Discord.",
                12,CompanionStyle.MUTED,false));
        }else{
            heading.addView(text(found.message,13,CompanionStyle.GOLD,false));
        }
        button(heading,"Check again",false,this::refreshVerification);
        heading.addView(text(
            "Code detection is automatic. Verification starts and finishes in Discord; "
            +"this tool only reads and copies VRChat invite codes.",
            11,CompanionStyle.MUTED,false));
    }
    /** Scan in the background without hiding the previously displayed code. */
    private void refreshVerification(){
        if(root==null||!"mini_verify".equals(screen)||busy||
            !StaffGate.unlocked(ctx))return;
        busy=true;
        final int token=revision;
        io.execute(()->{
            VRChatInviteCode.Result result=null;
            String error=null;
            try{result=VRChatInviteCode.scanLatest(ctx);}
            catch(Exception e){
                error=e instanceof SecurityException?
                    "Sign in to your personal VRChat account in the Companion first.":
                    "Couldn't refresh VRChat invites. Please try again.";
            }
            final VRChatInviteCode.Result found=result;
            final String problem=error;
            handler.post(()->{
                if(token!=revision||root==null||!"mini_verify".equals(screen)||
                    !StaffGate.unlocked(ctx))return;
                busy=false;
                if(problem!=null){
                    if(verificationResult==null||verificationResult.code.isEmpty())
                        verificationResult=new VRChatInviteCode.Result("",problem,0,false,0L);
                    // Preserve existing codes on a transient network failure.
                }else if(found!=null){
                    verificationResult=found;
                }
                drawList();
            });
        });
    }
    private void showInbox(){
        chips(list,"All","Invites","Friends",
            ()->inbox("all"),()->inbox("invite"),()->inbox("friend"));
        JSONArray entries=inboxEntries();
        list.addView(text(entries.length()+" notifications on this page",
            12,CompanionStyle.ACCENT,false));
        for(int i=0;i<Math.min(entries.length(),65);i++){
            JSONObject n=entries.optJSONObject(i);if(n==null)continue;
            final boolean modern=n.optBoolean("_modern",false);
            final String id=n.optString("id",""),type=n.optString("type","");
            String sender=n.optString("senderUsername",n.optString("senderDisplayName","VRChat"));
            String message=n.optString("message",n.optString("description",""));
            LinearLayout c=card(list);
            c.addView(text(shortValue(sender,55),15,CompanionStyle.TEXT,true));
            c.addView(text(type+" · "+shortValue(message,140),12,CompanionStyle.MUTED,false));
            String uid=n.optString("senderUserId",details(n).optString("senderUserId",""));
            if(validUser(uid))button(c,"Sender profile",false,()->profile(uid));
            String location=inviteLocation(n);
            if(!location.isEmpty()){
                button(c,"Copy instance location",false,()->copy("instance",location));
                button(c,"Send myself an invite",false,()->confirm(
                    "Request a personal invite to this instance?",
                    ()->write(()->VRChatApi.selfInvite(ctx,location),"Invite requested")));
            }
            if(!n.optBoolean("seen",false)){
                button(c,"Mark seen",false,()->write(
                    ()->VRChatApi.markNotificationSeen(ctx,id,modern),"Marked as seen"));
            }
            if(!modern&&type.toLowerCase(Locale.US).contains("friend")&&
                type.toLowerCase(Locale.US).contains("request")){
                button(c,"Accept friend request",true,()->confirm(
                    "Accept this VRChat friend request?",
                    ()->write(()->VRChatApi.acceptFriendRequest(ctx,id),"Friend request accepted")));
            }
            button(c,"Dismiss",false,()->confirm(
                "Dismiss this notification?",
                ()->write(()->VRChatApi.dismissNotification(ctx,id,modern),"Notification dismissed")));
        }
        if(entries.length()==0)line(list,"Inbox is empty",
            "Your personal account has no matching notifications.",null);
    }
    private void showProfile(){
        String id=target;
        LinearLayout info=card(list);
        String name=profile.optString("displayName","VRChat user");
        info.addView(text(name,18,CompanionStyle.TEXT,true));
        info.addView(text("Status: "+profile.optString("status","Unknown")+
            "\n"+profile.optString("statusDescription",""),
            13,CompanionStyle.MUTED,false));
        String bio=profile.optString("bio","");
        if(!bio.isEmpty())info.addView(text(shortValue(bio,1000),
            13,CompanionStyle.TEXT,false));
        String loc=profile.optString("location","");
        if(!loc.isEmpty())info.addView(text("Location: "+shortValue(loc,150),
            12,CompanionStyle.MUTED,false));
        button(info,"Copy player ID",false,()->copy("player ID",id));
        button(info,"Copy VRChat profile link",false,
            ()->copy("profile link","https://vrchat.com/home/user/"+id));
        if(loc.startsWith("wrld_")&&loc.contains(":"))
            button(info,"Location details",false,()->location(loc));
        button(info,"View / write personal note",false,()->note(id));
        button(info,"Send friend request",false,()->confirm(
            "Send a friend request to "+name+"?",
            ()->write(()->VRChatApi.sendFriendRequest(ctx,id),"Friend request sent")));
        button(info,"Request an invite",false,()->confirm(
            "Request an invite from "+name+"?",
            ()->write(()->VRChatApi.requestInvite(ctx,id),"Invite requested")));
    }
    private void showWorlds(){
        int shown=0;
        for(int i=0;i<loaded.length()&&shown<limit;i++){
            JSONObject w=loaded.optJSONObject(i);if(w==null)continue;
            String name=w.optString("name","World"),id=w.optString("id","");
            if(!matches(name+" "+id))continue;
            line(list,name,shortValue(w.optString("description",""),100),()->world(id));
            shown++;
        }
        if(shown==0)line(list,"No favorite worlds",
            "Worlds saved in your personal VRChat account appear here.",null);
        if(shown>=limit)button(list,"Show more",false,()->{limit+=65;drawList();});
    }
    private void showWorld(){
        LinearLayout c=card(list);
        c.addView(text(profile.optString("name","World"),18,CompanionStyle.TEXT,true));
        c.addView(text(shortValue(profile.optString("description",""),1200),
            13,CompanionStyle.MUTED,false));
        c.addView(text("Capacity: "+profile.optInt("capacity",0)+
            " · Visits: "+profile.optInt("visits",0),12,CompanionStyle.ACCENT,false));
        String id=target;
        button(c,"Copy world ID",false,()->copy("world ID",id));
        button(c,"Copy world link",false,
            ()->copy("world link","https://vrchat.com/home/world/"+id));
    }
    private void showInstance(){
        LinearLayout c=card(list);
        c.addView(text("VRChat instance",17,CompanionStyle.TEXT,true));
        c.addView(text(shortValue(target,180),12,CompanionStyle.MUTED,false));
        String loc=target;
        button(c,"Copy location",false,()->copy("location",loc));
        button(c,"Send myself an invite",true,()->confirm(
            "Request an invite to this instance?",
            ()->write(()->VRChatApi.selfInvite(ctx,loc),"Invite requested")));
        String worldId=loc.split(":",2)[0];
        button(c,"View world details",false,()->world(worldId));
        String name=profile.optString("name",profile.optString("instanceName",""));
        if(!name.isEmpty())c.addView(text(name,13,CompanionStyle.ACCENT,false));
    }
    private void showAvatars(){
        int count=0;
        for(int i=0;i<loaded.length()&&count<limit;i++){
            JSONObject a=loaded.optJSONObject(i);if(a==null)continue;
            String name=a.optString("name","Avatar"),id=a.optString("id","");
            if(!matches(name+" "+id))continue;
            LinearLayout c=card(list);
            c.addView(text(shortValue(name,55),15,CompanionStyle.TEXT,true));
            c.addView(text(shortValue(a.optString("description",""),110),12,
                CompanionStyle.MUTED,false));
            if(id.matches("avtr_[0-9a-fA-F-]{36}")){
                button(c,"Select avatar",false,()->confirm("Switch your VRChat avatar to "+name+"?",
                    ()->write(()->VRChatApi.selectAvatar(ctx,id),"Avatar selected")));
                button(c,"Copy avatar ID",false,()->copy("avatar ID",id));
            }
            count++;
        }
        if(count==0)line(list,"No favorite avatars","Save avatars in VRChat to see them here.",null);
        if(count>=limit)button(list,"Show more",false,()->{limit+=65;drawList();});
    }
    private void showGroups(){
        int count=0;
        for(int i=0;i<loaded.length()&&count<limit;i++){
            JSONObject group=loaded.optJSONObject(i);if(group==null)continue;
            String name=group.optString("name",group.optString("groupName","Group"));
            String id=group.optString("id",group.optString("groupId",""));
            if(!id.matches("grp_[0-9a-fA-F-]{36}")||!matches(name+" "+id))continue;
            line(list,name,group.optString("shortCode",id),()->{
                selectedGroupId=id;target=id;memberOffset=0;banOffset=0;
                host.navigate("mini_group");
            });count++;
        }
        if(count==0)line(list,"No groups found","Your joined VRChat groups appear here.",null);
    }
    private void showGroup(){
        String id=selectedGroupId;
        LinearLayout c=card(list);
        c.addView(text(profile.optString("name","VRChat group"),18,CompanionStyle.TEXT,true));
        c.addView(text(shortValue(profile.optString("description",""),1300),
            13,CompanionStyle.MUTED,false));
        c.addView(text("Members: "+profile.optInt("memberCount",0),12,
            CompanionStyle.ACCENT,false));
        button(c,"Copy group ID",false,()->copy("group ID",id));
        button(c,"Copy group link",false,
            ()->copy("group link","https://vrchat.com/home/group/"+id));
        button(c,"Browse group members",true,()->{memberOffset=0;host.navigate("mini_members");});
        button(c,"View active group instances",false,()->host.navigate("mini_instances"));
        button(c,"Review group bans (read-only)",false,()->{banOffset=0;host.navigate("mini_bans");});
    }
    private String memberName(JSONObject member){
        JSONObject user=member.optJSONObject("user");
        if(user!=null){
            String name=user.optString("displayName","");
            if(!name.isEmpty())return name;
        }
        return member.optString("displayName",member.optString("userId","Member"));
    }
    private String memberId(JSONObject member){
        String uid=member.optString("userId","");
        if(!validUser(uid)){
            JSONObject user=member.optJSONObject("user");
            if(user!=null)uid=user.optString("id","");
        }
        return uid;
    }
    private void showMembers(){
        section(list,"GROUP MEMBER DIRECTORY");
        list.addView(text("Page "+(memberOffset/60+1)+" · "+loaded.length()+
            " results · search 3+ characters for server-wide matches",
            12,CompanionStyle.MUTED,false));
        int count=0;
        for(int i=0;i<loaded.length();i++){
            JSONObject e=loaded.optJSONObject(i);
            if(e==null)continue;
            String name=memberName(e),id=memberId(e);
            if(!matches(name+" "+id))continue;
            String role="";
            JSONArray roles=e.optJSONArray("roleIds");
            if(roles!=null&&roles.length()>0)role=" · "+roles.length()+" group role(s)";
            final String uid=id;
            line(list,name,"Member"+role+
                (e.optString("joinedAt","").isEmpty()?"":" · Joined "+e.optString("joinedAt")),
                validUser(uid)?()->profile(uid):null);
            count++;
        }
        if(count==0)line(list,"No matching members",
            "Try another search, or refresh without a search filter.",null);
        if(term().length()<3){
            LinearLayout navigation=card(list);
            button(navigation,"Previous 60",false,()->{
                if(memberOffset>=60){memberOffset-=60;load();}
            });
            if(loaded.length()>=60){
                button(navigation,"Next 60",false,()->{memberOffset+=60;load();});
            }
        }
    }
    private void showGroupInstances(){
        section(list,"ACTIVE GROUP INSTANCES");
        list.addView(text("Only instances this personal account is allowed to see",
            12,CompanionStyle.MUTED,false));
        for(int i=0;i<loaded.length();i++){
            JSONObject item=loaded.optJSONObject(i);if(item==null)continue;
            String loc=item.optString("location","");
            String name=item.optString("name",item.optString("instanceId","Group instance"));
            String number=item.optString("n_users",
                item.optString("userCount",item.optString("capacity","")));
            LinearLayout c=card(list);
            c.addView(text(shortValue(name,60),14,CompanionStyle.TEXT,true));
            c.addView(text(shortValue(loc,150)+
                (number.isEmpty()?"":" · "+number+" players/capacity"),12,
                CompanionStyle.MUTED,false));
            if(!loc.isEmpty()){
                button(c,"Copy location",false,()->copy("group instance location",loc));
                if(loc.startsWith("wrld_")&&loc.contains(":")){
                    button(c,"Instance details",false,()->location(loc));
                    button(c,"Request self-invite",false,()->confirm(
                        "Send yourself an invite to this group instance?",
                        ()->write(()->VRChatApi.selfInvite(ctx,loc),"Invite requested")));
                }
            }
        }
        if(loaded.length()==0)line(list,"No visible group instances",
            "There may be no active instances, or your account cannot see them.",null);
    }
    private void showBans(){
        section(list,"READ-ONLY GROUP BAN LIST");
        list.addView(text("Visible only where your VRChat group permissions allow it.",
            12,CompanionStyle.MUTED,false));
        for(int i=0;i<loaded.length();i++){
            JSONObject item=loaded.optJSONObject(i);if(item==null)continue;
            String id=memberId(item);
            String name=memberName(item);
            if(name.trim().isEmpty())name=id.isEmpty()?"Group ban":id;
            line(list,name,"Banned member · "+shortValue(id,60),
                validUser(id)?()->profile(id):null);
        }
        if(loaded.length()==0)line(list,"No visible bans",
            "This group may have no bans, or your account may have limited access.",null);
        if(banOffset>=60)button(list,"Previous 60",false,()->{banOffset-=60;load();});
        if(loaded.length()>=60)button(list,"Next 60",false,()->{banOffset+=60;load();});
    }
    private void showSearch(){
        String query=term();
        if(query.length()<2){
            line(list,"Search VRChat users","Type two or more characters above, then tap Search.",null);
            return;
        }
        int count=0;
        for(int i=0;i<loaded.length()&&count<60;i++){
            JSONObject user=loaded.optJSONObject(i);if(user==null)continue;
            String id=user.optString("id",""),name=user.optString("displayName",id);
            if(!validUser(id))continue;
            line(list,name,user.optString("status","")+" · "+id,()->profile(id));
            count++;
        }
        if(count==0)line(list,"No matching users","Try another search.",null);
    }
    private void showPresence(){
        LinearLayout c=card(list);
        c.addView(text("Current status: "+profile.optString("status","Unknown"),
            15,CompanionStyle.TEXT,true));
        final String[] states={"active","join me","ask me","busy","offline"};
        final String[] labels={"Online","Join Me","Ask Me","Busy","Offline"};
        final EditText message=CompanionStyle.input(ctx,"Status message (32 characters max)");
        message.setText(profile.optString("statusDescription",""));
        message.setFilters(new android.text.InputFilter[]{
            new android.text.InputFilter.LengthFilter(32)});
        c.addView(message,new LinearLayout.LayoutParams(-1,dp(47)));
        message.setOnFocusChangeListener((v,focused)->host.keyboard(focused));
        message.setOnTouchListener((v,event)->{
            if(event.getActionMasked()==android.view.MotionEvent.ACTION_DOWN)
                host.keyboard(true);
            return false;
        });
        section(c,"Choose a status");
        for(int i=0;i<states.length;i++){
            final String state=states[i];
            button(c,labels[i],false,()->{
                host.keyboard(false);
                message.clearFocus();
                confirm("Set status to "+state+"?",
                    ()->write(()->VRChatApi.updatePresence(ctx,state,
                        message.getText().toString()),"Presence updated"));
            });
        }
    }
    private void section(LinearLayout parent,String title){
        parent.addView(CompanionStyle.section(ctx,title));
    }
    private void showNote(){
        LinearLayout c=card(list);
        String previous=profile.optString("note","");
        EditText input=CompanionStyle.input(ctx,"Your private user note");
        input.setSingleLine(false);input.setMinLines(3);input.setMaxLines(7);
        input.setText(previous);
        input.setFilters(new android.text.InputFilter[]{
            new android.text.InputFilter.LengthFilter(2000)});
        input.setOnFocusChangeListener((v,focus)->host.keyboard(focus));
        input.setOnTouchListener((v,event)->{
            if(event.getActionMasked()==android.view.MotionEvent.ACTION_DOWN)
                host.keyboard(true);
            return false;
        });
        c.addView(input);
        button(c,"Save private VRChat note",true,()->{
            String value=input.getText().toString().trim();
            if(value.isEmpty()){message("Enter a note before saving");return;}
            host.keyboard(false);input.clearFocus();
            String id=target;
            confirm("Save this note to your VRChat account?",
                ()->write(()->VRChatApi.saveUserNote(ctx,id,value),"Note saved"));
        });
        button(c,"Copy user ID",false,()->copy("user ID",target));
    }
    private interface Action{void run()throws Exception;}
    private void confirm(String prompt,Runnable action){
        if(root==null)return;
        if(pendingAction!=null){pendingAction=null;pending="";}
        LinearLayout approval=card(list);
        approval.addView(text(prompt,13,CompanionStyle.GOLD,true));
        button(approval,"Confirm",true,()->{pendingAction=null;action.run();});
        button(approval,"Cancel",false,()->{list.removeView(approval);});
        pendingAction=action;pending=prompt;
    }
    private void write(Action action,String success){
        if(!StaffGate.unlocked(ctx))return;
        final int token=revision;
        message("Working…");
        io.execute(()->{
            String error=null;
            try{action.run();}
            catch(Exception e){error=e.getMessage()==null?"Action unavailable":e.getMessage();}
            final String problem=error;
            handler.post(()->{
                if(token!=revision||root==null||!StaffGate.unlocked(ctx))return;
                if(problem==null){message(success);load();}
                else message("Action failed: "+shortValue(problem,110));
            });
        });
    }
}
