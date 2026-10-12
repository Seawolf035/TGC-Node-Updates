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
import android.widget.ImageView;
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
        void onPresenceChanged(String status);
    }
    private final Service ctx;
    private final Host host;
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private LinearLayout root;
    private String screen="",target="",kind="";
    private String selectedGroupId="";
    private String avatarGroup="all";
    private boolean avatarGroupsExpanded=false;
    private String selectedAvatarId="";
    private JSONObject selectedAvatarDetails;
    private boolean selectedAvatarInfoLoading=false;
    private int selectedAvatarInfoToken=0;
    private AvatarFavoritesRepository.Snapshot favoriteAvatarLists;
    private JSONObject avatarOwnUser=new JSONObject();
    private String avatarAccountId="";
    private static final class AvatarLoad{
        final AvatarFavoritesRepository.Snapshot favorites;
        final JSONObject ownUser;
        final String accountId;
        AvatarLoad(AvatarFavoritesRepository.Snapshot data,JSONObject user,String account){
            favorites=data;ownUser=user;accountId=account;
        }
    }
    private String searchKind="people";
    private String submittedSearch="";
    private String searchLoadingKind="";
    private String editingStatus="active";
    private int memberOffset=0,banOffset=0;
    private int revision=0;
    private String lastError="";
    private boolean busy=false,writing=false;
    private JSONArray loaded=new JSONArray();
    private JSONObject profile=new JSONObject();
    private LinearLayout list;
    private EditText search;
    private int limit=65;
    private String inboxFilter="all";
    private long inboxLastChecked=0L;
    private boolean inboxPartial=false;
    private String inboxError="";
    private final Runnable inboxTick=new Runnable(){
        @Override public void run(){
            if(root==null||!"mini_inbox".equals(screen))return;
            if(!busy&&!writing)refreshInbox(false);
            handler.postDelayed(this,90_000L);
        }
    };
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
        handler.removeCallbacks(inboxTick);
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
    public void findAnything(){searchKind="people";submittedSearch="";host.navigate("mini_find");}
    public void verification(){host.navigate("mini_verify");}
    public void note(String userId){
        if(validUser(userId)){target=userId;kind="note";host.navigate("mini_note");}
    }
    public void inbox(String type){
        inboxFilter=type;
        if("mini_inbox".equals(screen)&&root!=null)drawList();
        else host.navigate("mini_inbox");
    }
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
        if("mini_inbox".equals(page))
            handler.postDelayed(inboxTick,90_000L);
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
            if("mini_search".equals(screen)||"mini_members".equals(screen)||
                "mini_find".equals(screen)){
                search.setHint("mini_members".equals(screen)?
                    "Search member name (3+ letters)":
                    "mini_find".equals(screen)?"Search people, worlds or local players":
                    "Search user (2+ letters)");
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
                public void onTextChanged(CharSequence x,int start,int before,int count){
                    drawList();
                }
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
            screen.equals("mini_search")||screen.equals("mini_members")||
            screen.equals("mini_find");
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
            case "mini_find":return "Find anything";
            case "mini_recent":return "Recent worlds";
            case "mini_now":return "Your instance";
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
            case "mini_presence":return "Choose a status, edit your message, then apply once";
            case "mini_find":return "Search people, worlds or players seen on this phone";
            case "mini_recent":return "Worlds your VRChat account has visited";
            case "mini_now":return "Instance detected from local VRChat Mobile logs";
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
                refreshInbox(profile.optJSONArray("items")==null);break;
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
                execute(()->{
                    String account=VRChatSession.load(ctx).optString("userId","");
                    AvatarFavoritesRepository.Snapshot data=
                        AvatarFavoritesRepository.fetch(ctx);
                    JSONObject own=new JSONObject();
                    try{own=VRChatApi.currentUser(ctx);}
                    catch(Exception ignored){
                        // Favorite lists should still work if current-avatar
                        // metadata is temporarily inaccessible.
                    }
                    return new AvatarLoad(data,own,account);
                },result->{
                    AvatarLoad data=(AvatarLoad)result;
                    if(!data.accountId.equals(avatarAccountId)){
                        selectedAvatarId="";
                        selectedAvatarDetails=null;
                        avatarGroup="all";
                    }
                    avatarAccountId=data.accountId;
                    avatarOwnUser=data.ownUser;
                    favoriteAvatarLists=data.favorites;
                    if(!"all".equals(avatarGroup)){
                        boolean found=false;
                        for(int i=0;i<favoriteAvatarLists.groups.length();i++){
                            JSONObject group=favoriteAvatarLists.groups.optJSONObject(i);
                            if(group!=null&&avatarGroup.equals(group.optString("name",""))){
                                found=true;break;
                            }
                        }
                        if(!found)avatarGroup="all";
                    }
                    if(!selectedAvatarId.isEmpty() &&
                       findFavoriteAvatar(selectedAvatarId)==null){
                        selectedAvatarId="";selectedAvatarDetails=null;
                    }
                    drawList();
                });break;
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
            case "mini_find":
                loadGlobalSearch();break;
            case "mini_recent":
                execute(()->VRChatApi.recentWorlds(ctx),
                    result->{loaded=(JSONArray)result;drawList();});break;
            case "mini_now":
                execute(()->{
                    JSONObject result=new JSONObject();
                    try(MobileOutbox db=new MobileOutbox(ctx)){
                        result.put("events",db.recentLocalActivity(300));
                        result.put("players",db.currentPlayers(250));
                    }
                    return result;
                },result->{profile=(JSONObject)result;drawList();});break;
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
            case "mini_find":showGlobalSearch();break;
            case "mini_recent":showRecentWorlds();break;
            case "mini_now":showCurrentInstance();break;
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
        JSONArray filtered=new JSONArray();
        JSONArray items=profile.optJSONArray("items");
        if(items==null)return filtered;
        for(int i=0;i<items.length();i++){
            JSONObject item=items.optJSONObject(i);
            if(InboxRepository.include(item,inboxFilter))filtered.put(item);
        }
        return filtered;
    }
    /** Keep the previous inbox visible if one refresh fails or VRChat is offline. */
    private void refreshInbox(boolean first){
        if(root==null||!"mini_inbox".equals(screen)||busy||writing||
           !StaffGate.unlocked(ctx))return;
        busy=true;
        final int token=revision;
        if(first)status("Checking your VRChat inbox…");
        io.execute(()->{
            InboxRepository.Snapshot snapshot=null;String issue="";
            try{snapshot=InboxRepository.refresh(ctx);}
            catch(Exception e){issue=e instanceof SecurityException?
                "Sign in to your personal VRChat account in the Companion first.":
                "Couldn't refresh. Check your VRChat connection, then try again.";}
            final InboxRepository.Snapshot found=snapshot;
            final String problem=issue;
            handler.post(()->{
                if(token!=revision||root==null||!"mini_inbox".equals(screen)||
                    !StaffGate.unlocked(ctx))return;
                busy=false;
                if(found!=null){
                    try{profile.put("items",found.items);}catch(Exception ignored){}
                    inboxLastChecked=found.checkedAt;
                    inboxPartial=found.partial;
                    inboxError=found.warning;
                    drawList();
                }else if(profile.optJSONArray("items")!=null){
                    inboxError=problem+" Showing the last loaded notifications.";
                    drawList();
                }else status(problem);
            });
        });
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
            "Start verification in The Gamer Club's Discord or Cult of Gamer Music's Discord. "
            +"The latest available TGC-XXXX-XXXX or CULT-XXXX-XXXX code will appear here automatically.",
            12,CompanionStyle.MUTED,false));
        VRChatInviteCode.Result found=verificationResult;
        if(found==null){
            heading.addView(text("Checking recent invites…",13,CompanionStyle.MUTED,false));
        }else if(!found.code.isEmpty()){
            String service="cult".equals(found.provider)?"Cult of Gamer Music":"The Gamer Club";
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
        button(list,"unread".equals(inboxFilter)?"✓ Showing unread only":"Show unread only",
            false,()->inbox("unread".equals(inboxFilter)?"all":"unread"));
        JSONArray entries=inboxEntries();
        JSONArray all=profile.optJSONArray("items");
        int unread=all==null?0:InboxRepository.unread(all);
        list.addView(text(entries.length()+" shown · "+unread+" unread"+
            (inboxLastChecked>0?" · Checked "+date(inboxLastChecked):""),
            12,CompanionStyle.ACCENT,false));
        if(!inboxError.isEmpty())line(list,
            inboxPartial?"Partial inbox":"Refresh unavailable",
            inboxError,null);
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
        AvatarFavoritesRepository.Snapshot snapshot=favoriteAvatarLists;
        if(snapshot==null){
            line(list,"Loading favorite lists",
                "Retrieving your VRChat avatar groups…",null);
            return;
        }
        JSONArray groups=snapshot.groups;
        LinearLayout picker=card(list);
        picker.addView(text(groups.length()+" VRChat favorite list"+
            (groups.length()==1?"":"s")+" · "+snapshot.uniqueAvatars+
            " unique avatars",13,CompanionStyle.ACCENT,true));
        if(!snapshot.warning.isEmpty())
            picker.addView(text(snapshot.warning,12,CompanionStyle.GOLD,false));
        button(picker,("all".equals(avatarGroup)?"✓ ":"")+"All avatar lists",false,()->{
            avatarGroup="all";limit=65;drawList();
        });
        // Display every group returned by VRChat, including VRC+ slots and
        // renamed groups. The labels are obtained from VRChat, not guessed.
        for(int i=0;i<groups.length();i++){
            JSONObject group=groups.optJSONObject(i);
            if(group==null)continue;
            String key=group.optString("name","");
            String display=group.optString("displayName",key);
            JSONArray entries=group.optJSONArray("avatars");
            String count=group.optString("error","").isEmpty()?
                (entries==null?0:entries.length())+" loaded":"Unavailable";
            button(picker,(avatarGroup.equals(key)?"✓ ":"")+
                shortValue(display,36)+" · "+count,false,()->{
                avatarGroup=key;limit=65;drawList();
            });
        }
        if(groups.length()==0)
            line(list,"No avatar favorite lists",
                "VRChat did not return any favorite avatar groups for your account.",null);
        java.util.HashSet<String> seen=new java.util.HashSet<>();
        int total=0,shown=0;
        for(int i=0;i<groups.length();i++){
            JSONObject group=groups.optJSONObject(i);
            if(group==null)continue;
            if(!"all".equals(avatarGroup)&&
                !avatarGroup.equals(group.optString("name","")))continue;
            JSONArray avatars=group.optJSONArray("avatars");
            if(avatars==null)continue;
            for(int j=0;j<avatars.length();j++){
                JSONObject a=avatars.optJSONObject(j);if(a==null)continue;
                String name=a.optString("name","Avatar"),id=a.optString("id","");
                if(!id.matches("avtr_[0-9a-fA-F-]{36}"))continue;
                if(!seen.add(id)||!matches(name+" "+id+" "+a.optString("authorName","")))
                    continue;
                total++;
                if(shown>=limit)continue;
                LinearLayout card=card(list);
                card.addView(text(shortValue(name,55),15,CompanionStyle.TEXT,true));
                card.addView(text("By "+shortValue(a.optString("authorName","Unknown creator"),55),
                    12,CompanionStyle.MUTED,false));
                if("all".equals(avatarGroup)){
                    card.addView(text(shortValue(group.optString("displayName",""),50),
                        11,CompanionStyle.ACCENT,false));
                }
                String info=a.optString("description","");
                if(!info.isEmpty())card.addView(text(shortValue(info,105),
                    12,CompanionStyle.MUTED,false));
                button(card,"Select avatar",false,()->confirm(
                    "Switch your VRChat avatar to "+name+"?",
                    ()->write(()->VRChatApi.selectAvatar(ctx,id),"Avatar selected")));
                button(card,"Copy avatar ID",false,()->copy("avatar ID",id));
                shown++;
            }
        }
        if(total==0){
            JSONObject group=null;
            for(int i=0;i<groups.length();i++){
                JSONObject candidate=groups.optJSONObject(i);
                if(candidate!=null&&avatarGroup.equals(candidate.optString("name",""))){
                    group=candidate;break;
                }
            }
            line(list,"No avatars in this view",
                group!=null&&!group.optString("error","").isEmpty()?
                    "VRChat couldn't load this list. Try Refresh.":
                    "Choose another list or change your search.",null);
        }else if(total>shown){
            button(list,"Show more avatars ("+(total-shown)+" remaining)",false,
                ()->{limit+=65;drawList();});
        }
        button(list,"Refresh all favorite lists",false,this::load);
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
    private void loadGlobalSearch(){
        if("local".equals(searchKind)){
            submittedSearch="";
            execute(()->{
                try(MobileOutbox db=new MobileOutbox(ctx)){
                    return db.recentEncounters(100);
                }
            },result->{loaded=(JSONArray)result;drawList();});
            return;
        }
        String query=search==null?"":search.getText().toString().trim();
        if(query.length()<2){
            submittedSearch="";loaded=new JSONArray();
            drawList();
            return;
        }
        final String requestedKind=searchKind,requestedQuery=query;
        submittedSearch=requestedQuery;
        execute(()->"worlds".equals(requestedKind)?
            VRChatApi.searchWorlds(ctx,requestedQuery):
            VRChatApi.searchUsers(ctx,requestedQuery),result->{
            if(!requestedKind.equals(searchKind))return;
            loaded=(JSONArray)result;drawList();
        });
    }
    private void showGlobalSearch(){
        chips(list,"People","Worlds","Seen here",
            ()->switchGlobalSearch("people"),
            ()->switchGlobalSearch("worlds"),
            ()->switchGlobalSearch("local"));
        list.addView(text("Searching: "+
            ("worlds".equals(searchKind)?"Worlds":
             "local".equals(searchKind)?"Local encounters":"People"),
            12,CompanionStyle.ACCENT,true));
        if(!"local".equals(searchKind)){
            if(term().length()<2){
                line(list,"Enter a name",
                    "Enter two or more characters above to search VRChat.",null);
                return;
            }
            if(!submittedSearch.equals(search.getText().toString().trim())){
                line(list,"Ready to search",
                    "Tap Search below or the keyboard Search key.",null);
                button(list,"Search "+("worlds".equals(searchKind)?"worlds":"people"),
                    true,this::load);
                return;
            }
        }
        int shown=0;
        for(int i=0;i<loaded.length()&&shown<65;i++){
            JSONObject item=loaded.optJSONObject(i);if(item==null)continue;
            String name,id,detail;
            if("worlds".equals(searchKind)){
                name=item.optString("name","World");
                id=item.optString("id","");
                if(!id.matches("wrld_[0-9a-fA-F-]{36}"))continue;
                detail=shortValue(item.optString("description",""),110);
                final String worldId=id;
                line(list,name,detail,()->world(worldId));
            }else{
                id=item.optString("user_id",item.optString("id",""));
                name=item.optString("display_name",item.optString("displayName",id));
                if(!validUser(id))continue;
                if("local".equals(searchKind)&&!matches(name+" "+id))continue;
                detail="local".equals(searchKind)?
                    ("Seen "+item.optInt("count",0)+" time(s) · "+
                    item.optString("last_seen","")):
                    item.optString("statusDescription",item.optString("status",""));
                final String userId=id;
                line(list,name,detail,()->profile(userId));
            }
            shown++;
        }
        if(shown==0)line(list,"No matches",
            "Try a different search, or check another category.",null);
    }
    private void switchGlobalSearch(String kind){
        if(kind.equals(searchKind))return;
        searchKind=kind;
        submittedSearch="";
        loaded=new JSONArray();
        if("local".equals(kind))loadGlobalSearch();
        else drawList();
    }
    private void showRecentWorlds(){
        int count=0;
        for(int i=0;i<loaded.length()&&count<65;i++){
            JSONObject e=loaded.optJSONObject(i);
            if(e==null)continue;
            String id=e.optString("id",""),name=e.optString("name","World");
            if(!id.matches("wrld_[0-9a-fA-F-]{36}"))continue;
            final String worldId=id;
            line(list,name,shortValue(e.optString("description",""),100),
                ()->world(worldId));
            count++;
        }
        if(count==0)line(list,"No recent worlds",
            "Recent worlds appear here if VRChat makes them available to your account.",null);
        button(list,"Find another world",false,()->{
            searchKind="worlds";submittedSearch="";host.navigate("mini_find");
        });
    }
    private void showCurrentInstance(){
        JSONArray logs=profile.optJSONArray("events");
        JSONArray present=profile.optJSONArray("players");
        if(logs==null)logs=new JSONArray();
        if(present==null)present=new JSONArray();
        JSONObject recent=null;
        for(int i=0;i<logs.length();i++){
            JSONObject e=logs.optJSONObject(i);
            if(e!=null&&"instance".equals(e.optString("kind",""))){
                recent=e;break;
            }
        }
        android.content.SharedPreferences state=
            ctx.getSharedPreferences("tgc_mobile_node_status",0);
        boolean active=state.getBoolean("running",false)&&
            System.currentTimeMillis()-state.getLong("service_heartbeat_at",0L)<90_000L;
        LinearLayout c=card(list);
        String location=recent==null?"":recent.optString("location","");
        long at=recent==null?0:recent.optLong("time",0);
        c.addView(text("Last detected instance",16,CompanionStyle.TEXT,true));
        if(location.isEmpty()){
            c.addView(text("No world or instance detected in recent VRChat Mobile logs.",
                12,CompanionStyle.MUTED,false));
        }else{
            c.addView(text(shortValue(location,180),12,CompanionStyle.ACCENT,false));
            c.addView(text("Observed "+date(at),12,CompanionStyle.MUTED,false));
            button(c,"Copy location",false,()->copy("instance",location));
            if(location.startsWith("wrld_")&&location.contains(":")){
                String worldId=location.split(":",2)[0];
                button(c,"World details",false,()->world(worldId));
                button(c,"Request an invite to this instance",false,
                    ()->confirm("Request an invite to the last detected instance?",
                        ()->write(()->VRChatApi.selfInvite(ctx,location),"Invite requested")));
            }
        }
        c.addView(text(active?
            "Tracking active · "+present.length()+" players observed since monitoring started":
            "Tracking paused · player list may be out of date",
            12,active?CompanionStyle.GOOD:CompanionStyle.GOLD,false));
        c.addView(text("This is based on the latest local logs, not a guaranteed live or complete VRChat instance roster.",
            11,CompanionStyle.MUTED,false));
        button(c,"Observed player list",true,()->host.navigate("mini_current"));
        button(c,"Join & leave history",false,()->host.navigate("mini_joins"));
    }
    private void showPresence(){
        final String accountId=profile.optString("id","");
        final String currentStatus=profile.optString("status","");
        final String description=profile.optString("statusDescription","");
        final String[] states={"active","join me","ask me","busy","offline"};
        final String[] chosen={currentStatus};
        if(!java.util.Arrays.asList(states).contains(chosen[0]))chosen[0]="active";
        LinearLayout main=card(list);
        LinearLayout activeLine=new LinearLayout(ctx);
        activeLine.setGravity(Gravity.CENTER_VERTICAL);
        main.addView(activeLine);
        TextView current=text("●",22,VRChatPresenceIndicator.color(currentStatus),true);
        activeLine.addView(current,new LinearLayout.LayoutParams(dp(28),-2));
        LinearLayout details=new LinearLayout(ctx);
        details.setOrientation(LinearLayout.VERTICAL);
        activeLine.addView(details,new LinearLayout.LayoutParams(0,-2,1f));
        details.addView(text("Currently "+VRChatPresenceIndicator.label(currentStatus),
            17,CompanionStyle.TEXT,true));
        details.addView(text(description.isEmpty()?"No status message":
            shortValue(description,55),12,CompanionStyle.MUTED,false));
        section(main,"CHOOSE HOW YOU APPEAR");
        final java.util.ArrayList<Button> choices=new java.util.ArrayList<>();
        for(int i=0;i<states.length;i++){
            final String state=states[i];
            if(i%2==0){
                LinearLayout row=new LinearLayout(ctx);
                row.setOrientation(LinearLayout.HORIZONTAL);
                main.addView(row);
            }
            LinearLayout row=(LinearLayout)main.getChildAt(main.getChildCount()-1);
            Button option=CompanionStyle.button(ctx,
                "● "+VRChatPresenceIndicator.label(state),false);
            option.setTextSize(12);
            LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(0,dp(44),1f);
            params.setMargins(dp(2),dp(3),dp(2),dp(3));
            row.addView(option,params);
            choices.add(option);
        }
        TextView preview=text("",13,CompanionStyle.ACCENT,true);
        preview.setPadding(0,dp(7),0,dp(4));
        main.addView(preview);
        Runnable paint=()->{
            for(int i=0;i<states.length;i++){
                Button b=choices.get(i);
                boolean active=states[i].equals(chosen[0]);
                int color=VRChatPresenceIndicator.color(states[i]);
                b.setBackground(CompanionStyle.shape(ctx,
                    active?color:CompanionStyle.RAISED,11,
                    active?CompanionStyle.ACCENT:CompanionStyle.BORDER));
                b.setTextColor(active?0xFF091423:color);
                b.setContentDescription(VRChatPresenceIndicator.label(states[i])+
                    (active?" selected":""));
            }
            preview.setText("Selected: "+VRChatPresenceIndicator.label(chosen[0]));
        };
        for(int i=0;i<states.length;i++){
            final String state=states[i];
            choices.get(i).setOnClickListener(v->{chosen[0]=state;paint.run();});
        }
        paint.run();
        section(main,"STATUS MESSAGE");
        EditText input=CompanionStyle.input(ctx,"Your message · max 32 characters");
        input.setSingleLine(false);
        input.setMaxLines(2);
        input.setMinLines(1);
        input.setFilters(new android.text.InputFilter[]{
            new android.text.InputFilter.LengthFilter(32)});
        input.setText(description);
        main.addView(input,new LinearLayout.LayoutParams(-1,dp(72)));
        input.setOnFocusChangeListener((v,focused)->host.keyboard(focused));
        input.setOnTouchListener((v,event)->{
            if(event.getActionMasked()==android.view.MotionEvent.ACTION_DOWN)
                host.keyboard(true);
            return false;
        });
        TextView count=text(input.length()+"/32",11,CompanionStyle.MUTED,false);
        count.setGravity(Gravity.END);
        main.addView(count);
        input.addTextChangedListener(new android.text.TextWatcher(){
            public void beforeTextChanged(CharSequence a,int s,int c,int d){}
            public void onTextChanged(CharSequence a,int start,int before,int n){
                count.setText(a.length()+"/32");
            }
            public void afterTextChanged(android.text.Editable value){}
        });
        LinearLayout confirmBox=new LinearLayout(ctx);
        confirmBox.setOrientation(LinearLayout.VERTICAL);
        main.addView(confirmBox);
        confirmBox.setVisibility(View.GONE);
        Button apply=CompanionStyle.button(ctx,"Apply status",true);
        main.addView(apply,CompanionStyle.buttonParams(ctx));
        apply.setOnClickListener(v->{
            if(!StaffGate.unlocked(ctx))return;
            host.keyboard(false);input.clearFocus();
            if(confirmBox.getVisibility()==View.VISIBLE){
                confirmBox.setVisibility(View.GONE);return;
            }
            confirmBox.removeAllViews();
            String state=chosen[0],newMessage=input.getText().toString().trim();
            confirmBox.addView(text(
                "Update VRChat to "+VRChatPresenceIndicator.label(state)+
                (newMessage.isEmpty()?"":" · "+newMessage)+"?",
                12,CompanionStyle.GOLD,true));
            button(confirmBox,"Confirm change",true,()->{
                confirmBox.setVisibility(View.GONE);
                // Verify the authenticated account before changing its status.
                write(()->{
                    JSONObject me=VRChatApi.currentUser(ctx);
                    if(!accountId.equals(me.optString("id","")))
                        throw new SecurityException("VRChat account changed. Refresh this page.");
                    VRChatApi.updatePresence(ctx,state,newMessage);
                },"Status updated",
                  ()->host.onPresenceChanged(state));
            });
            button(confirmBox,"Cancel",false,()->confirmBox.setVisibility(View.GONE));
            confirmBox.setVisibility(View.VISIBLE);
        });
        section(list,"SAVED PRESETS");
        LinearLayout presetBox=card(list);
        presetBox.addView(text("Presets saved in the full Companion also work here.",
            12,CompanionStyle.MUTED,false));
        LinearLayout savedRows=new LinearLayout(ctx);
        savedRows.setOrientation(LinearLayout.VERTICAL);
        presetBox.addView(savedRows);
        Runnable showSaved=()->{
            savedRows.removeAllViews();
            JSONArray presets=readStatusPresets(accountId);
            if(presets.length()==0){
                savedRows.addView(text("No presets saved yet.",
                    12,CompanionStyle.MUTED,false));
            }
            for(int i=0;i<Math.min(30,presets.length());i++){
                JSONObject row=presets.optJSONObject(i);if(row==null)continue;
                String name=row.optString("title","Preset");
                String state=row.optString("status","active");
                String savedMessage=row.optString("description","");
                if(!java.util.Arrays.asList(states).contains(state))continue;
                button(savedRows,shortValue(name,26)+" · "+
                    VRChatPresenceIndicator.label(state),false,()->{
                        chosen[0]=state;
                        paint.run();
                        input.setText(savedMessage);
                        confirmBox.setVisibility(View.GONE);
                    });
            }
        };
        showSaved.run();
        section(presetBox,"SAVE THIS COMBINATION");
        EditText presetName=CompanionStyle.input(ctx,"Preset name");
        presetName.setFilters(new android.text.InputFilter[]{
            new android.text.InputFilter.LengthFilter(45)});
        presetName.setSingleLine(true);
        presetBox.addView(presetName,new LinearLayout.LayoutParams(-1,dp(43)));
        presetName.setOnFocusChangeListener((v,focused)->host.keyboard(focused));
        presetName.setOnTouchListener((v,event)->{
            if(event.getActionMasked()==android.view.MotionEvent.ACTION_DOWN)
                host.keyboard(true);
            return false;
        });
        button(presetBox,"Save preset on this phone",false,()->{
            if(!StaffGate.unlocked(ctx))return;
            String label=presetName.getText().toString().trim();
            if(label.isEmpty()){presetName.setError("Enter a name");return;}
            if(!validUser(accountId)){message("Sign in to VRChat to save presets");return;}
            JSONArray existing=readStatusPresets(accountId),updated=new JSONArray();
            for(int i=0;i<existing.length();i++){
                JSONObject item=existing.optJSONObject(i);
                if(item!=null&&!label.equalsIgnoreCase(item.optString("title","")))
                    updated.put(item);
            }
            if(updated.length()>=30){message("You can save up to 30 presets");return;}
            try{
                updated.put(new JSONObject().put("title",label)
                    .put("status",chosen[0])
                    .put("description",input.getText().toString().trim()));
                ctx.getSharedPreferences("tgc_vrcx_presets",0).edit()
                    .putString("status_presets_"+accountId,updated.toString()).apply();
                presetName.setText("");presetName.clearFocus();host.keyboard(false);
                showSaved.run();
                message("Preset saved on this phone.");
            }catch(Exception e){message("Unable to save preset");}
        });
    }
    private JSONArray readStatusPresets(String accountId){
        if(!validUser(accountId))return new JSONArray();
        String raw=ctx.getSharedPreferences("tgc_vrcx_presets",0)
            .getString("status_presets_"+accountId,"[]");
        try{return new JSONArray(raw);}catch(Exception ignored){return new JSONArray();}
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
        write(action,success,null);
    }
    private void write(Action action,String success,Runnable onSuccess){
        if(!StaffGate.unlocked(ctx)||writing)return;
        writing=true;
        final int token=revision;
        message("Working…");
        io.execute(()->{
            String error=null;
            try{action.run();}
            catch(Exception e){error=e.getMessage()==null?"Action unavailable":e.getMessage();}
            final String problem=error;
            handler.post(()->{
                writing=false;
                if(token!=revision||root==null||!StaffGate.unlocked(ctx))return;
                if(problem==null){
                    if(onSuccess!=null)onSuccess.run();
                    message(success);load();
                }else message("Action failed: "+shortValue(problem,110));
            });
        });
    }
}
