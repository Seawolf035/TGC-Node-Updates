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
import android.text.InputType;
import android.text.InputFilter;
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
        void scrollToTop();
        boolean isOscLiveActive();
        String oscLiveDescription();
        void oscStartLive(int intervalSeconds);
        void oscStopLive();
    }
    private final Service ctx;
    private final Host host;
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private LinearLayout root;
    private String screen="",target="",kind="";
    private String selectedGroupId="";
    private boolean onlyPopulatedGroupInstances=false;
    private boolean sortGroupInstancesByOccupancy=true;
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
    private String friendFilter="all";
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
    private String chatDraft="";
    private String chatNotice="";
    private boolean chatSound=true;
    private boolean chatSending=false;
    private boolean oscParameterSending=false;
    private String oscPresetType="bool";
    private String oscStatusNote="";
    private boolean oscStatusSending=false;
    private String oscEditingChatbox="";
    private boolean typingActive=false;
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
         if(typingActive){
            typingActive=false;
            io.execute(()->{
                try{VRChatOscChat.sendTyping(false);}
                catch(Exception ignored){}
            });
        }
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
        limit="mini_avatars".equals(page)?18:65;pending="";revision++;
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
        if(!"mini_profile".equals(screen)&&!"mini_note".equals(screen)&&
           !"mini_chat".equals(screen)&&!"mini_osc_status".equals(screen)&&
           !"mini_osc_rotation".equals(screen)&&
           !"mini_osc_presets".equals(screen)&&
           !"mini_osc_parameters".equals(screen)){
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
            case "mini_chat":return "In-game text chat";
            case "mini_osc_presets":return "OSC quick phrases";
            case "mini_osc_status":return "OSC status line";
            case "mini_osc_rotation":return "Rotating chatboxes";
            case "mini_osc_parameters":return "Avatar OSC controls";
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
            case "mini_chat":return "Send directly to VRChat Mobile through local OSC";
            case "mini_osc_presets":return "Reusable messages · nothing sends until you tap Send";
            case "mini_osc_status":return "Custom text, clock and battery via chatbox OSC";
            case "mini_osc_rotation":return "Saved chatboxes · automatic timed switching";
            case "mini_osc_parameters":return "Typed OSC writes to your current avatar";
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
            case "mini_chat":
            case "mini_osc_presets":
            case "mini_osc_status":
            case "mini_osc_rotation":
            case "mini_osc_parameters":
                drawList();break;
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
            case "mini_chat":showChat();break;
            case "mini_osc_presets":showOscPresets();break;
            case "mini_osc_status":showOscStatus();break;
            case "mini_osc_rotation":showOscRotation();break;
            case "mini_osc_parameters":showOscParameters();break;
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
        chips(list,"All","Online","Offline",
            ()->{friendFilter="all";limit=65;drawList();},
            ()->{friendFilter="online";limit=65;drawList();},
            ()->{friendFilter="offline";limit=65;drawList();});
        LinkedHashMap<String,ArrayList<JSONObject>> grouped=new LinkedHashMap<>();
        int online=0,offline=0,matchingTotal=0;
        for(int i=0;i<loaded.length();i++){
            JSONObject f=loaded.optJSONObject(i);
            if(f==null)continue;
            boolean active=f.optBoolean("isOnline",false);
            if(active)online++;else offline++;
            String name=f.optString("displayName",f.optString("id",""));
            String loc=active?f.optString("location","unknown"):"Offline";
            if(loc.isEmpty()||loc.equals("private")||loc.equals("unknown"))
                loc="Private / hidden";
            if(!matches(name+" "+loc))continue;
            if("online".equals(friendFilter)&&!active)continue;
            if("offline".equals(friendFilter)&&active)continue;
            matchingTotal++;
            if(!grouped.containsKey(loc))grouped.put(loc,new ArrayList<>());
            grouped.get(loc).add(f);
        }
        list.addView(text(online+" online · "+offline+" offline · "+
            matchingTotal+" matching",12,CompanionStyle.ACCENT,false));
        ArrayList<String> keys=new ArrayList<>(grouped.keySet());
        Collections.sort(keys,(a,b)->{
            if(a.equals(b))return 0;
            if("Offline".equals(a))return 1;
            if("Offline".equals(b))return -1;
            return Integer.compare(grouped.get(b).size(),grouped.get(a).size());
        });
        int shown=0;
        for(String key:keys){
            if(shown>=limit)break;
            ArrayList<JSONObject> group=grouped.get(key);
            if(group==null||group.isEmpty())continue;
            LinearLayout card=card(list);
            String heading=key.startsWith("wrld_")?
                "Instance: "+shortValue(key,42):key;
            card.addView(text(heading+" · "+group.size(),13,
                CompanionStyle.ACCENT,true));
            if(key.startsWith("wrld_")&&key.contains(":")){
                button(card,"Instance details",false,()->location(key));
                button(card,"Request invite to this instance",false,()->confirm(
                    "Send yourself an invite to this visible instance?",
                    ()->write(()->VRChatApi.selfInvite(ctx,key),
                        "Invite requested")));
            }
            for(JSONObject f:group){
                if(shown>=limit)break;
                final String uid=f.optString("id","");
                String name=f.optString("displayName",uid);
                String message=f.optString("statusDescription","");
                TextView entry=text("› "+shortValue(name,48)+
                    "  •  "+VRChatPresenceIndicator.label(f.optString("status",""))+
                    (message.isEmpty()?"":" · "+shortValue(message,55)),
                    12,CompanionStyle.TEXT,false);
                entry.setPadding(0,dp(8),0,dp(8));
                card.addView(entry);
                if(validUser(uid)){
                    entry.setOnClickListener(v->profile(uid));
                    entry.setContentDescription("View "+name+"'s VRChat profile");
                }
                shown++;
            }
        }
        if(matchingTotal==0)line(list,"No matching friends",
            "Try another filter or check your VRChat account.",null);
        if(matchingTotal>shown)
            button(list,"Show more friends ("+(matchingTotal-shown)+" remaining)",
                false,()->{limit+=65;drawList();});
        button(list,"Refresh friends",false,this::load);
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
        for(int i=0;i<Math.min(entries.length(),limit);i++){
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
        if(entries.length()>limit)
            button(list,"Show more notifications ("+(entries.length()-limit)+
                " remaining)",false,()->{limit+=40;drawList();});
    }
    private void showProfile(){
        String id=target;
        LinearLayout info=card(list);
        String name=profile.optString("displayName","VRChat user");
        LinearLayout header=new LinearLayout(ctx);
        header.setGravity(Gravity.CENTER_VERTICAL);
        info.addView(header);
        ImageView portrait=new ImageView(ctx);
        portrait.setScaleType(ImageView.ScaleType.CENTER_CROP);
        portrait.setClipToOutline(true);
        portrait.setBackground(CompanionStyle.shape(
            ctx,CompanionStyle.RAISED,11,CompanionStyle.BORDER));
        CompanionImages.load(portrait,CompanionImages.preferred(profile,
            "profilePicOverrideThumbnail","userIcon",
            "currentAvatarThumbnailImageUrl","currentAvatarImageUrl"));
        header.addView(portrait,new LinearLayout.LayoutParams(dp(72),dp(72)));
        LinearLayout details=new LinearLayout(ctx);
        details.setPadding(dp(11),0,0,0);
        details.setOrientation(LinearLayout.VERTICAL);
        header.addView(details,new LinearLayout.LayoutParams(0,-2,1f));
        details.addView(text(shortValue(name,60),17,CompanionStyle.TEXT,true));
        String presence=profile.optString("status","");
        details.addView(text(VRChatPresenceIndicator.label(presence),12,
            VRChatPresenceIndicator.color(presence),true));
        String statusText=profile.optString("statusDescription","");
        if(!statusText.isEmpty())details.addView(
            text(shortValue(statusText,150),12,CompanionStyle.MUTED,false));
        boolean friend=profile.optBoolean("isFriend",false);
        String requestState=profile.optString("friendRequestStatus","")
            .toLowerCase(Locale.US);
        info.addView(text(friend?"✓ Already friends":
            requestState.contains("outgoing")?"Friend request pending":
            requestState.contains("incoming")?"Incoming friend request":
            "Not currently friends",12,
            friend?CompanionStyle.GOOD:CompanionStyle.MUTED,false));
        String bio=profile.optString("bio","");
        if(!bio.isEmpty()){
            section(info,"BIO");
            info.addView(text(shortValue(bio,1000),
                13,CompanionStyle.TEXT,false));
        }
        String loc=profile.optString("location","");
        if(!loc.isEmpty()){
            section(info,"LOCATION");
            info.addView(text(shortValue(loc,150),
                12,CompanionStyle.MUTED,false));
            if(loc.startsWith("wrld_")&&loc.contains(":")){
                button(info,"View visible instance",false,()->location(loc));
                button(info,"Copy visible instance",false,
                    ()->copy("instance location",loc));
            }
        }
        section(info,"ACTIONS");
        button(info,"View / write personal note",false,()->note(id));
        if(!friend&&!requestState.contains("outgoing")&&
           !requestState.contains("incoming")){
            button(info,"Send friend request",false,()->confirm(
                "Send a friend request to "+name+"?",
                ()->write(()->VRChatApi.sendFriendRequest(ctx,id),
                    "Friend request sent")));
        }
        if(friend){
            button(info,"Request an invite from this friend",true,()->confirm(
                "Ask "+name+" for a VRChat invite?",
                ()->write(()->VRChatApi.requestInvite(ctx,id),"Invite requested")));
        }
        button(info,"Copy player ID",false,()->copy("player ID",id));
        button(info,"Copy VRChat profile link",false,
            ()->copy("profile link","https://vrchat.com/home/user/"+id));
        button(info,"Refresh profile",false,this::load);
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
    private JSONObject findFavoriteAvatar(String id){
        if(id==null||id.isEmpty()||favoriteAvatarLists==null)return null;
        JSONArray groups=favoriteAvatarLists.groups;
        for(int i=0;i<groups.length();i++){
            JSONObject group=groups.optJSONObject(i);
            if(group==null)continue;
            JSONArray entries=group.optJSONArray("avatars");
            if(entries==null)continue;
            for(int j=0;j<entries.length();j++){
                JSONObject avatar=entries.optJSONObject(j);
                if(avatar!=null&&id.equals(avatar.optString("id","")))
                    return avatar;
            }
        }
        return null;
    }
    private String avatarGroupTitle(){
        if("all".equals(avatarGroup))return "All favorite lists";
        if(favoriteAvatarLists==null)return avatarGroup;
        JSONArray groups=favoriteAvatarLists.groups;
        for(int i=0;i<groups.length();i++){
            JSONObject group=groups.optJSONObject(i);
            if(group!=null&&avatarGroup.equals(group.optString("name","")))
                return group.optString("displayName",avatarGroup);
        }
        return "All favorite lists";
    }
    private void chooseAvatarGroup(String key){
        if(key.equals(avatarGroup)){
            avatarGroupsExpanded=false;
            drawList();return;
        }
        avatarGroup=key;
        avatarGroupsExpanded=false;
        limit=18;
        selectedAvatarId="";
        selectedAvatarDetails=null;
        selectedAvatarInfoToken++;
        drawList();
        host.scrollToTop();
    }
    private void chooseAvatar(String id){
        if(!id.matches("avtr_[0-9a-fA-F-]{36}"))return;
        if(!selectedAvatarId.equals(id)){
            selectedAvatarId=id;
            selectedAvatarDetails=null;
            selectedAvatarInfoToken++;
        }
        drawList();
        host.scrollToTop();
    }
    private ImageView avatarPicture(JSONObject avatar,int size){
        ImageView picture=new ImageView(ctx);
        picture.setScaleType(ImageView.ScaleType.CENTER_CROP);
        picture.setBackground(CompanionStyle.shape(
            ctx,CompanionStyle.RAISED,10,CompanionStyle.BORDER));
        picture.setClipToOutline(true);
        if(avatar!=null)CompanionImages.load(picture,CompanionImages.preferred(
            avatar,"thumbnailImageUrl","imageUrl","currentAvatarThumbnailImageUrl",
            "currentAvatarImageUrl"));
        return picture;
    }
    private void loadSelectedAvatarInfo(String avatarId){
        if(selectedAvatarInfoLoading||!avatarId.equals(selectedAvatarId))return;
        selectedAvatarInfoLoading=true;
        final int sequence=++selectedAvatarInfoToken,token=revision;
        message("Loading avatar details…");
        io.execute(()->{
            JSONObject details=null;
            String error="";
            try{details=VRChatApi.avatar(ctx,avatarId);}
            catch(Exception ex){error="VRChat couldn't load additional avatar details.";}
            final JSONObject info=details;
            final String issue=error;
            handler.post(()->{
                selectedAvatarInfoLoading=false;
                if(token!=revision||root==null||!"mini_avatars".equals(screen)||
                   !selectedAvatarId.equals(avatarId)||
                   sequence!=selectedAvatarInfoToken)return;
                if(info!=null){
                    selectedAvatarDetails=info;
                    drawList();
                    host.scrollToTop();
                }else message(issue);
            });
        });
    }
    private void showAvatars(){
        AvatarFavoritesRepository.Snapshot snapshot=favoriteAvatarLists;
        if(snapshot==null){
            line(list,"Loading favorite lists",
                "Retrieving your VRChat avatar groups…",null);
            return;
        }
        JSONArray groups=snapshot.groups;
        LinearLayout current=card(list);
        section(current,"CURRENT VRCHAT AVATAR");
        String active=avatarOwnUser.optString("currentAvatar","");
        JSONObject activeFavorite=findFavoriteAvatar(active);
        String currentName=activeFavorite==null?
            (active.isEmpty()?"Current avatar not reported by VRChat":
                "Current avatar isn't in the loaded favorites"):
            activeFavorite.optString("name","Avatar");
        LinearLayout currentRow=new LinearLayout(ctx);
        currentRow.setGravity(Gravity.CENTER_VERTICAL);
        current.addView(currentRow);
        ImageView currentImage=avatarPicture(activeFavorite!=null?
            activeFavorite:avatarOwnUser,58);
        currentRow.addView(currentImage,
            new LinearLayout.LayoutParams(dp(58),dp(58)));
        LinearLayout currentText=new LinearLayout(ctx);
        currentText.setOrientation(LinearLayout.VERTICAL);
        currentText.setPadding(dp(10),0,0,0);
        currentRow.addView(currentText,new LinearLayout.LayoutParams(0,-2,1f));
        currentText.addView(text(shortValue(currentName,62),14,
            CompanionStyle.TEXT,true));
        if(!active.isEmpty())currentText.addView(text(
            shortValue(active,46),11,CompanionStyle.MUTED,false));
        else currentText.addView(text(
            "You can still browse and choose a favorite.",11,CompanionStyle.MUTED,false));

        LinearLayout picker=card(list);
        picker.addView(text(groups.length()+" VRChat favorite list"+
            (groups.length()==1?"":"s")+" · "+snapshot.uniqueAvatars+
            " unique avatars",13,CompanionStyle.ACCENT,true));
        if(!snapshot.warning.isEmpty())
            picker.addView(text(snapshot.warning,12,CompanionStyle.GOLD,false));
        button(picker,"List: "+shortValue(avatarGroupTitle(),36)+
            (avatarGroupsExpanded?"  ▴":"  ▾"),false,()->{
                avatarGroupsExpanded=!avatarGroupsExpanded;
                drawList();
            });
        if(avatarGroupsExpanded){
            button(picker,("all".equals(avatarGroup)?"✓ ":"")+
                "All favorite lists",false,()->chooseAvatarGroup("all"));
            // The group names are the exact labels VRChat returned.
            for(int i=0;i<groups.length();i++){
                JSONObject group=groups.optJSONObject(i);
                if(group==null)continue;
                String key=group.optString("name","");
                String display=group.optString("displayName",key);
                JSONArray items=group.optJSONArray("avatars");
                String count=group.optString("error","").isEmpty()?
                    (items==null?0:items.length())+" avatars":"Unavailable";
                button(picker,(avatarGroup.equals(key)?"✓ ":"")+
                    shortValue(display,33)+" · "+count,false,
                    ()->chooseAvatarGroup(key));
            }
        }
        if(!selectedAvatarId.isEmpty()){
            JSONObject chosen=selectedAvatarDetails!=null?
                selectedAvatarDetails:findFavoriteAvatar(selectedAvatarId);
            if(chosen==null){
                selectedAvatarId="";
                selectedAvatarDetails=null;
            }else{
                final String avatarId=selectedAvatarId;
                String name=chosen.optString("name","Avatar");
                LinearLayout selection=card(list);
                selection.setBackground(CompanionStyle.shape(
                    ctx,CompanionStyle.RAISED,13,CompanionStyle.ACCENT));
                selection.addView(text("SELECTED AVATAR",12,
                    CompanionStyle.ACCENT,true));
                LinearLayout preview=new LinearLayout(ctx);
                preview.setGravity(Gravity.CENTER_VERTICAL);
                selection.addView(preview);
                preview.addView(avatarPicture(chosen,104),
                    new LinearLayout.LayoutParams(dp(104),dp(104)));
                LinearLayout details=new LinearLayout(ctx);
                details.setPadding(dp(12),0,0,0);
                details.setOrientation(LinearLayout.VERTICAL);
                preview.addView(details,new LinearLayout.LayoutParams(0,-2,1f));
                details.addView(text(shortValue(name,70),17,
                    CompanionStyle.TEXT,true));
                details.addView(text("By "+
                    shortValue(chosen.optString("authorName","Unknown creator"),60),
                    12,CompanionStyle.MUTED,false));
                if(avatarId.equals(active)){
                    details.addView(text("● Currently in use",12,
                        CompanionStyle.GOOD,true));
                }else{
                    details.addView(text("Not applied yet",12,
                        CompanionStyle.GOLD,true));
                }
                String description=chosen.optString("description","");
                if(!description.isEmpty()){
                    selection.addView(text(shortValue(description,240),
                        12,CompanionStyle.MUTED,false));
                }
                if(selectedAvatarDetails!=null){
                    selection.addView(text(
                        "Release: "+selectedAvatarDetails.optString(
                            "releaseStatus","Unknown")+
                        " · Version: "+selectedAvatarDetails.optInt("version",0),
                        11,CompanionStyle.MUTED,false));
                }
                LinearLayout controls=new LinearLayout(ctx);
                controls.setOrientation(LinearLayout.VERTICAL);
                selection.addView(controls);
                if(!avatarId.equals(active)){
                    Button apply=CompanionStyle.button(ctx,"Apply selected avatar",true);
                    controls.addView(apply,CompanionStyle.buttonParams(ctx));
                    LinearLayout approval=new LinearLayout(ctx);
                    approval.setOrientation(LinearLayout.VERTICAL);
                    controls.addView(approval);
                    approval.setVisibility(View.GONE);
                    apply.setOnClickListener(v->{
                        host.keyboard(false);
                        if(approval.getVisibility()==View.VISIBLE){
                            approval.setVisibility(View.GONE);return;
                        }
                        approval.removeAllViews();
                        approval.addView(text(
                            "Switch your personal VRChat account to "+name+"?",
                            12,CompanionStyle.GOLD,true));
                        button(approval,"Confirm avatar change",true,()->{
                            approval.setVisibility(View.GONE);
                            write(()->{
                                String now=VRChatSession.load(ctx)
                                    .optString("userId","");
                                if(!now.equals(avatarAccountId)||
                                   !now.matches("usr_[0-9a-fA-F-]{36}"))
                                    throw new SecurityException(
                                        "VRChat account changed. Refresh favorites first.");
                                VRChatApi.selectAvatar(ctx,avatarId);
                            },"Avatar changed",()->{
                                try{avatarOwnUser.put("currentAvatar",avatarId);}
                                catch(Exception ignored){}
                            },false);
                        });
                        button(approval,"Cancel",false,
                            ()->approval.setVisibility(View.GONE));
                        approval.setVisibility(View.VISIBLE);
                    });
                }else{
                    selection.addView(text(
                        "This is already your current VRChat avatar.",
                        12,CompanionStyle.GOOD,false));
                }
                button(controls,
                    selectedAvatarDetails==null?
                        "View full avatar details":"Refresh full details",
                    false,()->loadSelectedAvatarInfo(avatarId));
                button(controls,"Copy avatar ID",false,
                    ()->copy("avatar ID",avatarId));
                button(controls,"Clear selection",false,()->{
                    selectedAvatarId="";
                    selectedAvatarDetails=null;
                    selectedAvatarInfoToken++;
                    drawList();
                });
            }
        }else{
            LinearLayout hint=card(list);
            hint.addView(text("Tap an avatar to preview it",
                14,CompanionStyle.TEXT,true));
            hint.addView(text(
                "Browsing won't switch your avatar. Choose one below, then Apply and Confirm.",
                12,CompanionStyle.MUTED,false));
        }

        if(groups.length()==0){
            line(list,"No avatar favorite lists",
                "VRChat did not return any favorite avatar groups for your account.",null);
        }
        section(list,"FAVORITES · "+shortValue(avatarGroupTitle(),48));
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
                JSONObject avatar=avatars.optJSONObject(j);
                if(avatar==null)continue;
                String name=avatar.optString("name","Avatar");
                String author=avatar.optString("authorName","");
                String id=avatar.optString("id","");
                if(!id.matches("avtr_[0-9a-fA-F-]{36}"))continue;
                if(!seen.add(id)||!matches(name+" "+author+" "+id))continue;
                total++;
                if(shown>=limit)continue;
                boolean picked=id.equals(selectedAvatarId);
                boolean isCurrent=id.equals(active);
                LinearLayout tile=card(list);
                if(picked)tile.setBackground(CompanionStyle.shape(ctx,
                    CompanionStyle.RAISED,12,CompanionStyle.ACCENT));
                LinearLayout tileRow=new LinearLayout(ctx);
                tileRow.setGravity(Gravity.CENTER_VERTICAL);
                tile.addView(tileRow);
                tileRow.addView(avatarPicture(avatar,60),
                    new LinearLayout.LayoutParams(dp(60),dp(60)));
                LinearLayout info=new LinearLayout(ctx);
                info.setPadding(dp(10),0,0,0);
                info.setOrientation(LinearLayout.VERTICAL);
                tileRow.addView(info,new LinearLayout.LayoutParams(0,-2,1f));
                info.addView(text(shortValue(name,65),14,CompanionStyle.TEXT,true));
                info.addView(text(shortValue(
                    (isCurrent?"● In use · ":"")+
                    (picked?"✓ Selected · ":"")+
                    "By "+(author.isEmpty()?"Unknown creator":author),75),
                    11,isCurrent?CompanionStyle.GOOD:CompanionStyle.MUTED,false));
                if("all".equals(avatarGroup)){
                    info.addView(text(shortValue(
                        group.optString("displayName",""),45),10,
                        CompanionStyle.ACCENT,false));
                }
                tile.addView(text(picked?"✓ Selected · Tap to preview":"Tap to preview ›",
                    11,CompanionStyle.ACCENT,false));
                tile.setOnClickListener(v->chooseAvatar(id));
                shown++;
            }
        }
        list.addView(text(total+" matching favorites"+
            (total>shown?" · "+shown+" shown":""),12,
            CompanionStyle.ACCENT,false));
        if(total==0){
            JSONObject group=null;
            for(int i=0;i<groups.length();i++){
                JSONObject candidate=groups.optJSONObject(i);
                if(candidate!=null&&avatarGroup.equals(
                   candidate.optString("name",""))){group=candidate;break;}
            }
            line(list,"No avatars in this view",
                group!=null&&!group.optString("error","").isEmpty()?
                    "VRChat couldn't load this list. Try Refresh.":
                    "Choose another list or change your search.",null);
        }
        if(total>shown)button(list,
            "Show more avatars ("+(total-shown)+" remaining)",false,
            ()->{limit+=18;drawList();});
        button(list,"Refresh all favorite lists",false,this::load);
    }
    private void showGroups(){
        section(list,"PINNED GROUPS");
        int pinnedCount=0;
        try{
            JSONArray saved=GroupToolkitStore.pinned(ctx);
            pinnedCount=saved.length();
            for(int i=0;i<saved.length();i++){
                JSONObject row=saved.optJSONObject(i);
                if(row==null)continue;
                String id=row.optString("id","");
                if(!id.matches("grp_[0-9a-fA-F-]{36}"))continue;
                String label=row.optString("name","Group");
                line(list,"★ "+label,"Open group dashboard",()->openGroupDashboard(id));
            }
        }catch(Exception e){
            line(list,"Sign in for pinned groups",
                "Local shortcuts are separated by VRChat account.",null);
        }
        if(pinnedCount==0)line(list,"No pinned groups",
            "Open a group below and tap Pin for quick access.",null);
        section(list,"YOUR VRCHAT GROUPS");
        int count=0;
        for(int i=0;i<loaded.length()&&count<limit;i++){
            JSONObject group=loaded.optJSONObject(i);if(group==null)continue;
            String name=group.optString("name",group.optString("groupName","Group"));
            String id=group.optString("id",group.optString("groupId",""));
            if(!id.matches("grp_[0-9a-fA-F-]{36}")||!matches(name+" "+id))continue;
            boolean pinned=false;
            try{pinned=GroupToolkitStore.contains(ctx,id);}catch(Exception ignored){}
            line(list,(pinned?"★ ":"")+name,
                group.optString("shortCode",id)+" · "+
                group.optInt("memberCount",0)+" members",
                ()->openGroupDashboard(id));
            count++;
        }
        if(count==0)line(list,"No groups found",
            "Your joined VRChat groups appear here.",null);
    }
    private void openGroupDashboard(String groupId){
        if(!groupId.matches("grp_[0-9a-fA-F-]{36}"))return;
        selectedGroupId=groupId;target=groupId;memberOffset=0;banOffset=0;
        onlyPopulatedGroupInstances=false;
        host.navigate("mini_group");
    }
    private void showGroup(){
        String id=selectedGroupId;
        LinearLayout c=card(list);
        String name=profile.optString("name","VRChat group");
        c.addView(text(name,18,CompanionStyle.TEXT,true));
        String shortCode=profile.optString("shortCode","");
        if(!shortCode.isEmpty())
            c.addView(text(shortCode,12,CompanionStyle.ACCENT,true));
        c.addView(text(shortValue(profile.optString("description",""),1300),
            13,CompanionStyle.MUTED,false));
        c.addView(text("Members: "+profile.optInt("memberCount",0)+
            " · Online: "+profile.optInt("onlineMemberCount",0),
            12,CompanionStyle.ACCENT,false));
        c.addView(text("Joining: "+profile.optString("joinState","Unknown")+
            " · Visibility: "+profile.optString("privacy","Unknown"),
            12,CompanionStyle.MUTED,false));
        JSONObject myMember=profile.optJSONObject("myMember");
        if(myMember!=null){
            String status=myMember.optString("membershipStatus","");
            c.addView(text("Your membership: "+
                (status.isEmpty()?"Member":status),
                12,CompanionStyle.GOOD,true));
            JSONArray assigned=myMember.optJSONArray("roleIds");
            JSONArray available=profile.optJSONArray("roles");
            if(assigned!=null&&assigned.length()>0){
                StringBuilder labels=new StringBuilder();
                for(int i=0;i<assigned.length();i++){
                    String roleId=assigned.optString(i,"");
                    String label=roleId;
                    if(available!=null)for(int j=0;j<available.length();j++){
                        JSONObject role=available.optJSONObject(j);
                        if(role!=null&&roleId.equals(role.optString("id",""))){
                            label=role.optString("name",roleId);break;
                        }
                    }
                    if(!label.isEmpty()){
                        if(labels.length()>0)labels.append(", ");
                        labels.append(label);
                    }
                }
                if(labels.length()>0)c.addView(text(
                    "Your VRChat group roles: "+shortValue(labels.toString(),240),
                    12,CompanionStyle.ACCENT,false));
            }
        }
        boolean pinned=false;
        try{pinned=GroupToolkitStore.contains(ctx,id);}catch(Exception ignored){}
        final boolean wasPinned=pinned;
        button(c,pinned?"★ Unpin this group":"☆ Pin this group",false,()->{
            try{
                GroupToolkitStore.setPinned(ctx,id,name,!wasPinned);
                drawList();
            }catch(Exception e){message("Unable to save shortcut: "+e.getMessage());}
        });
        button(c,"Copy group ID",false,()->copy("group ID",id));
        button(c,"Copy group link",false,
            ()->copy("group link","https://vrchat.com/home/group/"+id));
        section(list,"GROUP TOOLS");
        LinearLayout browse=card(list);
        button(browse,"Browse and search group members",true,()->{
            memberOffset=0;host.navigate("mini_members");
        });
        button(browse,"Active group instances",false,
            ()->host.navigate("mini_instances"));
        button(browse,"Group bans (read-only)",false,()->{
            banOffset=0;host.navigate("mini_bans");
        });
        browse.addView(text(
            "These tools use your personal VRChat account's existing permissions. "+
            "Pinning a group does not grant administrative access.",
            12,CompanionStyle.MUTED,false));
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
    private static int groupInstanceOccupancy(JSONObject instance){
        int players=instance.optInt("n_users",-1);
        if(players<0)players=instance.optInt("userCount",-1);
        if(players<0)players=instance.optInt("memberCount",-1);
        return Math.max(0,players);
    }
    private void showGroupInstances(){
        section(list,"ACTIVE GROUP INSTANCES");
        LinearLayout controls=card(list);
        controls.addView(text("Only instances your personal VRChat account can see",
            12,CompanionStyle.MUTED,false));
        button(controls,onlyPopulatedGroupInstances?"Show all visible instances":
            "Show populated instances only",false,()->{
                onlyPopulatedGroupInstances=!onlyPopulatedGroupInstances;drawList();
        });
        button(controls,sortGroupInstancesByOccupancy?
            "Sort: Most populated first":"Sort: VRChat order",false,()->{
                sortGroupInstancesByOccupancy=!sortGroupInstancesByOccupancy;drawList();
        });
        button(controls,"Refresh group instances",false,this::load);

        java.util.ArrayList<JSONObject> instances=new java.util.ArrayList<>();
        for(int i=0;i<loaded.length();i++){
            JSONObject item=loaded.optJSONObject(i);
            if(item==null)continue;
            if(onlyPopulatedGroupInstances&&groupInstanceOccupancy(item)==0)continue;
            instances.add(item);
        }
        if(sortGroupInstancesByOccupancy){
            java.util.Collections.sort(instances,(a,b)->
                Integer.compare(groupInstanceOccupancy(b),groupInstanceOccupancy(a)));
        }
        controls.addView(text(instances.size()+" visible matching instances",
            12,CompanionStyle.ACCENT,true));
        for(JSONObject item:instances){
            String loc=item.optString("location","");
            String name=item.optString("name",item.optString("instanceId","Group instance"));
            JSONObject world=item.optJSONObject("world");
            if(name.isEmpty()&&world!=null)name=world.optString("name","Group instance");
            String region=item.optString("region","");
            int occupants=groupInstanceOccupancy(item);
            int capacity=item.optInt("capacity",world==null?0:world.optInt("capacity",0));
            LinearLayout c=card(list);
            c.addView(text(shortValue(name,75),14,CompanionStyle.TEXT,true));
            c.addView(text(occupants+" player"+(occupants==1?"":"s")+
                (capacity>0?" / "+capacity+" capacity":"")+
                (region.isEmpty()?"":" · "+region),
                12,CompanionStyle.ACCENT,true));
            if(!loc.isEmpty()){
                c.addView(text(shortValue(loc,115),11,CompanionStyle.MUTED,false));
                button(c,"Copy location",false,()->copy("group instance location",loc));
                if(loc.startsWith("wrld_")&&loc.contains(":")){
                    button(c,"View instance details",false,()->location(loc));
                    button(c,"Request self-invite",false,()->confirm(
                        "Send yourself an invite to this group instance?",
                        ()->write(()->VRChatApi.selfInvite(ctx,loc),"Invite requested")));
                }
            }
        }
        if(instances.isEmpty())line(list,"No matching visible instances",
            "The group may have no active instances, or your account cannot see them. "+
            "Disable the populated-only filter if needed.",null);
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
    private void oscTextEntry(EditText input){
        input.setOnFocusChangeListener((v,focus)->host.keyboard(focus));
        input.setOnTouchListener((v,event)->{
            if(event.getActionMasked()==android.view.MotionEvent.ACTION_DOWN)
                host.keyboard(true);
            return false;
        });
    }
    /** Saved phrases are private, account-scoped and never sent automatically. */
    private void showOscPresets(){
        LinearLayout intro=card(list);
        intro.addView(text("Quick phrases",17,CompanionStyle.TEXT,true));
        intro.addView(text(
            "Save up to 24 messages. Use a phrase to load it into the chat "+
            "composer; you still have to tap Send to transmit anything.",
            12,CompanionStyle.MUTED,false));
        JSONArray phrases;
        try{phrases=OscControlStore.phrases(ctx);}
        catch(Exception error){
            line(list,"Personal VRChat login required",
                "Sign in to your account in the Companion first.",null);
            return;
        }
        section(list,"YOUR SAVED PHRASES");
        if(phrases.length()==0)
            line(list,"No saved phrases",
                "Use the form below to create your first quick phrase.",null);
        for(int i=0;i<phrases.length();i++){
            JSONObject item=phrases.optJSONObject(i);if(item==null)continue;
            String name=item.optString("name","");
            String value=item.optString("text","");
            if(name.isEmpty()||value.isEmpty())continue;
            LinearLayout c=card(list);
            c.addView(text(shortValue(name,70),15,CompanionStyle.TEXT,true));
            c.addView(text(shortValue(value,200),12,CompanionStyle.MUTED,false));
            button(c,"Use in chat composer",true,()->{
                chatDraft=value;
                host.navigate("mini_chat");
            });
            button(c,"Delete phrase",false,()->confirm(
                "Delete saved phrase “"+shortValue(name,50)+"”?",
                ()->{
                    try{
                        OscControlStore.deletePhrase(ctx,name);
                        drawList();
                    }catch(Exception e){message("Couldn't remove phrase: "+e.getMessage());}
                }));
        }
        section(list,"SAVE A NEW PHRASE");
        LinearLayout add=card(list);
        EditText label=CompanionStyle.input(ctx,"Phrase name (e.g. Hello)");
        label.setSingleLine(true);
        label.setFilters(new InputFilter[]{new InputFilter.LengthFilter(40)});
        oscTextEntry(label);
        add.addView(label,new LinearLayout.LayoutParams(-1,dp(43)));
        EditText phrase=CompanionStyle.input(ctx,"Chatbox message (144 characters max)");
        phrase.setSingleLine(false);
        phrase.setMinLines(2);
        phrase.setMaxLines(4);
        phrase.setFilters(new InputFilter[]{
            new InputFilter.LengthFilter(VRChatOscChat.MAX_CHARACTERS)});
        phrase.setText(chatDraft);
        oscTextEntry(phrase);
        add.addView(phrase,new LinearLayout.LayoutParams(-1,-2));
        button(add,"Save this phrase",true,()->{
            if(!StaffGate.unlocked(ctx))return;
            try{
                OscControlStore.savePhrase(ctx,label.getText().toString(),
                    phrase.getText().toString());
                host.keyboard(false);
                phrase.clearFocus();
                drawList();
                message("Phrase saved on this device.");
            }catch(Exception ex){label.setError(ex.getMessage());}
        });
        button(add,"Open chat composer",false,()->host.navigate("mini_chat"));
    }
    private void sendOscParameter(String name,String type,String value,
                                  TextView feedback){
        if(!StaffGate.unlocked(ctx))return;
        if(oscParameterSending){
            feedback.setText("Another OSC parameter is being sent.");return;
        }
        oscParameterSending=true;
        feedback.setText("Sending local OSC parameter…");
        final int token=revision;
        io.execute(()->{
            String error=null;
            try{VRChatOscChat.sendParameter(name,type,value);}
            catch(Exception e){error=e.getMessage()==null?
                "Could not send OSC parameter":e.getMessage();}
            final String problem=error;
            handler.post(()->{
                oscParameterSending=false;
                if(token!=revision||root==null||
                   !"mini_osc_parameters".equals(screen))return;
                feedback.setText(problem==null?
                    "Parameter packet sent locally. Avatar response cannot be confirmed.":
                    "Couldn't send: "+problem);
            });
        });
    }
    /** This configures live sending in FloatingCompanionService, not in this page. */
    private void showOscStatus(){
        LinearLayout intro=card(list);
        intro.addView(text("Live Chatbox",18,CompanionStyle.TEXT,true));
        intro.addView(text("Like MagicChatbox: keep a short status line visible "+
            "above your avatar while you play VRChat Mobile. You control "+
            "what goes into it and when it starts.",
            12,CompanionStyle.MUTED,false));
        intro.addView(text(
            "Requires OSC enabled in VRChat. Uses local UDP on this phone; "+
            "there is no server receipt or guaranteed in-game display.",
            12,CompanionStyle.GOLD,false));
        boolean signedIn=true;
        String prefix="";
        int interval=10;
        boolean time=true,battery=false;
        try{
            prefix=OscControlStore.prefix(ctx);
            interval=OscControlStore.liveInterval(ctx);
            time=OscControlStore.includeTime(ctx);
            battery=OscControlStore.includeBattery(ctx);
        }catch(Exception ex){signedIn=false;}
        if(!signedIn){
            line(list,"Sign in to your personal VRChat account",
                "Live Chatbox preferences belong to that account.",null);
            return;
        }
        section(list,"LIVE BROADCAST");
        LinearLayout live=card(list);
        boolean running=host.isOscLiveActive();
        live.addView(text(running?"● BROADCASTING":"○ NOT BROADCASTING",
            16,running?CompanionStyle.GOOD:CompanionStyle.MUTED,true));
        live.addView(text(host.oscLiveDescription(),12,
            running?CompanionStyle.ACCENT:CompanionStyle.MUTED,false));
        live.addView(text(
            "When on, this continues while you collapse the panel or switch "+
            "to another floating tab. It stops when you tap Stop, hide the "+
            "floating bubble, or the floating service ends.",
            12,CompanionStyle.MUTED,false));
        if(running)button(live,"Stop Live Chatbox",true,()->{
            host.oscStopLive();
            drawList();
        });
        section(list,"CONTENT");
        LinearLayout content=card(list);
        content.addView(text("Custom text",13,CompanionStyle.ACCENT,true));
        EditText message=CompanionStyle.input(ctx,"Optional status text (up to 90 characters)");
        message.setSingleLine(false);
        message.setMinLines(2);
        message.setMaxLines(3);
        message.setFilters(new InputFilter[]{new InputFilter.LengthFilter(90)});
        message.setText(prefix);
        oscTextEntry(message);
        content.addView(message,new LinearLayout.LayoutParams(-1,-2));
        TextView note=text("Change the content, then Save or Start to apply.",
            11,CompanionStyle.MUTED,false);
        content.addView(note);
        final boolean[] flags={time,battery};
        Button clock=CompanionStyle.button(ctx,
            "Time: "+(flags[0]?"ON":"OFF"),false);
        Button charge=CompanionStyle.button(ctx,
            "Phone battery: "+(flags[1]?"ON":"OFF"),false);
        content.addView(clock,CompanionStyle.buttonParams(ctx));
        content.addView(charge,CompanionStyle.buttonParams(ctx));
        TextView preview=text("",13,CompanionStyle.TEXT,true);
        preview.setPadding(0,dp(8),0,dp(6));
        content.addView(preview);
        Runnable updatePreview=()->{
            try{
                OscControlStore.setPrefix(ctx,message.getText().toString());
                OscControlStore.setIncludeTime(ctx,flags[0]);
                OscControlStore.setIncludeBattery(ctx,flags[1]);
                String line=OscControlStore.statusLine(ctx);
                preview.setText("Preview: "+line);
                note.setText("Preferences saved locally. Live sends use the latest values.");
                return;
            }catch(Exception ex){
                preview.setText("Preview unavailable: "+ex.getMessage());
            }
        };
        // Do not save on every keystroke. Only these explicit buttons commit it.
        clock.setOnClickListener(v->{
            flags[0]=!flags[0];
            clock.setText("Time: "+(flags[0]?"ON":"OFF"));
            note.setText("Tap Save / Preview or Start to apply.");
        });
        charge.setOnClickListener(v->{
            flags[1]=!flags[1];
            charge.setText("Phone battery: "+(flags[1]?"ON":"OFF"));
            note.setText("Tap Save / Preview or Start to apply.");
        });
        try{preview.setText("Preview: "+OscControlStore.statusLine(ctx));}
        catch(Exception ex){preview.setText("Enter text or enable an information field.");}
        button(content,"Save settings & preview",false,()->{
            host.keyboard(false);message.clearFocus();updatePreview.run();
        });

        section(list,"ROTATING CHATBOXES");
        LinearLayout rotating=card(list);
        try{
            int extra=OscControlStore.chatboxes(ctx).length();
            boolean on=OscControlStore.rotationEnabled(ctx);
            rotating.addView(text((on?"● Rotating ":"○ Rotation OFF · ")+
                extra+" saved additional chatbox"+(extra==1?"":"es"),
                13,on?CompanionStyle.GOOD:CompanionStyle.MUTED,true));
            rotating.addView(text(
                "Keep this default status, and add named messages to switch between. "+
                "Switching speed is independent of the refresh speed below.",
                12,CompanionStyle.MUTED,false));
        }catch(Exception ignored){}
        button(rotating,"Manage rotating chatboxes",false,
            ()->host.navigate("mini_osc_rotation"));

        section(list,"UPDATE SPEED");
        LinearLayout intervalCard=card(list);
        intervalCard.addView(text(
            "Repeat the line periodically to keep the chatbox visible. "+
            "Slower updates save battery and reduce chatbox interruptions.",
            12,CompanionStyle.MUTED,false));
        final int[] selected={interval};
        LinearLayout speeds=new LinearLayout(ctx);
        speeds.setOrientation(LinearLayout.HORIZONTAL);
        intervalCard.addView(speeds);
        int[] seconds={5,10,15,30};
        ArrayList<Button> options=new ArrayList<>();
        for(int n:seconds){
            Button choice=CompanionStyle.button(ctx,n+"s",false);
            choice.setTextSize(12);
            speeds.addView(choice,new LinearLayout.LayoutParams(0,dp(45),1f));
            options.add(choice);
        }
        Runnable paint=()->{
            for(int i=0;i<seconds.length;i++)
                options.get(i).setText((seconds[i]==selected[0]?"✓ ":"")+
                    seconds[i]+" sec");
        };
        for(int i=0;i<seconds.length;i++){
            final int value=seconds[i];
            options.get(i).setOnClickListener(v->{
                selected[0]=value;paint.run();
                try{OscControlStore.setLiveInterval(ctx,value);}
                catch(Exception ex){note.setText(ex.getMessage());}
            });
        }
        paint.run();
        TextView feedback=text("",12,CompanionStyle.GOLD,false);
        intervalCard.addView(feedback);
        Button startLive=CompanionStyle.button(ctx,
            running?"Update Live Chatbox":"Start Live Chatbox",true);
        intervalCard.addView(startLive,CompanionStyle.buttonParams(ctx));
        LinearLayout approval=new LinearLayout(ctx);
        approval.setOrientation(LinearLayout.VERTICAL);
        approval.setVisibility(View.GONE);
        intervalCard.addView(approval);
        startLive.setOnClickListener(v->{
            if(!StaffGate.unlocked(ctx))return;
            host.keyboard(false);message.clearFocus();
            try{
                OscControlStore.setPrefix(ctx,message.getText().toString());
                OscControlStore.setIncludeTime(ctx,flags[0]);
                OscControlStore.setIncludeBattery(ctx,flags[1]);
                OscControlStore.setLiveInterval(ctx,selected[0]);
                OscControlStore.statusLine(ctx);
            }catch(Exception ex){
                feedback.setText("Cannot start: "+ex.getMessage());return;
            }
            approval.removeAllViews();
            approval.addView(text(
                "Confirm continuous OSC Chatbox updates every "+
                selected[0]+" seconds? The line will keep updating while "+
                "the floating service runs, even if the panel is collapsed.",
                12,CompanionStyle.GOLD,true));
            button(approval,"Confirm Live Chatbox",true,()->{
                approval.setVisibility(View.GONE);
                host.oscStartLive(selected[0]);
                drawList();
            });
            button(approval,"Cancel",false,()->approval.setVisibility(View.GONE));
            approval.setVisibility(View.VISIBLE);
        });
        button(intervalCard,"Send this status once",false,()->{
            if(!StaffGate.unlocked(ctx)||oscStatusSending)return;
            host.keyboard(false);message.clearFocus();
            String line;
            try{
                OscControlStore.setPrefix(ctx,message.getText().toString());
                OscControlStore.setIncludeTime(ctx,flags[0]);
                OscControlStore.setIncludeBattery(ctx,flags[1]);
                line=OscControlStore.statusLine(ctx);
            }catch(Exception ex){feedback.setText(ex.getMessage());return;}
            oscStatusSending=true;
            final int token=revision;
            feedback.setText("Transmitting one OSC chatbox packet…");
            io.execute(()->{
                String error=null;
                try{VRChatOscChat.send(line,false);}
                catch(Exception ex){error=ex.getMessage();}
                final String issue=error;
                handler.post(()->{
                    oscStatusSending=false;
                    if(token!=revision||root==null||
                       !"mini_osc_status".equals(screen))return;
                    feedback.setText(issue==null?
                        "Packet sent locally. Verify it appears in VRChat.":
                        "Couldn't send: "+issue);
                });
            });
        });
        section(list,"SAFETY");
        line(list,"Manual chat takes priority",
            "After you manually send a regular chatbox message, Live Chatbox "+
            "pauses for about 20 seconds before resuming. Live updates are "+
            "sent silently without notification sounds.",
            null);
        line(list,"No background launch or hidden startup",
            "Live Chatbox always starts OFF. It does not start itself after "+
            "reboots or service restarts, and cannot override Quest overlay "+
            "restrictions, staff access or Android battery controls.",
            null);
    }
    /** Rotation settings are local to the signed-in personal VRChat account.
     * All broadcast starts and staff/Quest checks stay in FloatingCompanionService.
     */
    private void showOscRotation(){
        JSONArray saved;
        boolean rotating;
        int seconds;
        try{
            saved=OscControlStore.chatboxes(ctx);
            rotating=OscControlStore.rotationEnabled(ctx);
            seconds=OscControlStore.rotationInterval(ctx);
        }catch(Exception e){
            line(list,"Personal VRChat sign-in required",
                "Saved chatboxes are separated by your personal account.",null);
            return;
        }
        LinearLayout intro=card(list);
        intro.addView(text("Rotating Live Chatboxes",18,CompanionStyle.TEXT,true));
        intro.addView(text(
            "Your existing Live Chatbox is always the first slot. Add up to "+
            "8 more named messages and optionally switch between them. "+
            "Nothing is transmitted by editing or saving.",
            12,CompanionStyle.MUTED,false));
        intro.addView(text(
            "Each message can have its own clock and battery fields. "+
            "Your normal 5/10/15/30-second refresh stays unchanged.",
            12,CompanionStyle.MUTED,false));

        section(list,"ROTATION");
        LinearLayout settings=card(list);
        settings.addView(text(rotating?"● AUTO-SWITCH ON":"○ AUTO-SWITCH OFF",
            15,rotating?CompanionStyle.GOOD:CompanionStyle.MUTED,true));
        settings.addView(text(
            "Saved per VRChat account, but actual broadcasting always starts OFF "+
            "and still requires Start Live Chatbox and confirmation.",
            12,CompanionStyle.MUTED,false));
        button(settings,rotating?"Disable automatic switching":
            "Enable automatic switching",true,()->{
            try{
                OscControlStore.setRotationEnabled(ctx,!OscControlStore.rotationEnabled(ctx));
                drawList();
            }catch(Exception e){message(e.getMessage());}
        });
        settings.addView(text("Switch to next chatbox after:",13,
            CompanionStyle.ACCENT,true));
        LinearLayout switchTimes=new LinearLayout(ctx);
        switchTimes.setOrientation(LinearLayout.HORIZONTAL);
        settings.addView(switchTimes);
        int[] times={15,30,60,120};
        for(int value:times){
            Button choice=CompanionStyle.button(ctx,
                (seconds==value?"✓ ":"")+(value==120?"2m":value+"s"),seconds==value);
            choice.setTextSize(12);
            switchTimes.addView(choice,new LinearLayout.LayoutParams(0,dp(43),1f));
            choice.setOnClickListener(v->{
                try{OscControlStore.setRotationInterval(ctx,value);drawList();}
                catch(Exception e){message(e.getMessage());}
            });
        }

        section(list,"YOUR CHATBOXES");
        LinearLayout defaultBox=card(list);
        defaultBox.addView(text("1 · Default Live Chatbox",14,CompanionStyle.TEXT,true));
        try{
            defaultBox.addView(text(OscControlStore.statusLine(ctx),12,
                CompanionStyle.MUTED,false));
        }catch(Exception e){
            defaultBox.addView(text("Configure the default Live Chatbox first.",
                12,CompanionStyle.GOLD,false));
        }
        button(defaultBox,"Edit default Live Chatbox",false,
            ()->host.navigate("mini_osc_status"));
        for(int i=0;i<saved.length();i++){
            JSONObject item=saved.optJSONObject(i);
            if(item==null)continue;
            String name=item.optString("name","");
            String value=item.optString("text","");
            boolean clock=item.optBoolean("time",false);
            boolean battery=item.optBoolean("battery",false);
            LinearLayout entry=card(list);
            entry.addView(text((i+2)+" · "+name,14,CompanionStyle.TEXT,true));
            try{
                entry.addView(text(OscControlStore.previewChatbox(ctx,value,clock,battery),
                    12,CompanionStyle.MUTED,false));
            }catch(Exception e){
                entry.addView(text("Preview unavailable: "+e.getMessage(),
                    12,CompanionStyle.GOLD,false));
            }
            button(entry,"Edit "+name,false,()->{
                oscEditingChatbox=name;
                drawList();
            });
            button(entry,"Remove "+name,false,()->confirm(
                "Delete chatbox “"+name+"”? The default Live Chatbox remains intact.",
                ()->{
                    try{
                        OscControlStore.deleteChatbox(ctx,name);
                        if(name.equals(oscEditingChatbox))oscEditingChatbox="";
                        drawList();
                    }catch(Exception e){message(e.getMessage());}
                }));
        }

        section(list,oscEditingChatbox.isEmpty()?"ADD CHATBOX":"EDIT CHATBOX");
        JSONObject editing=null;
        for(int i=0;i<saved.length();i++){
            JSONObject row=saved.optJSONObject(i);
            if(row!=null&&oscEditingChatbox.equals(row.optString("name",""))){
                editing=row;break;
            }
        }
        LinearLayout editor=card(list);
        editor.addView(text("Name",13,CompanionStyle.ACCENT,true));
        EditText nameInput=CompanionStyle.input(ctx,"Example: Staff status");
        nameInput.setSingleLine(true);
        nameInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(40)});
        if(editing!=null)nameInput.setText(editing.optString("name",""));
        oscTextEntry(nameInput);
        editor.addView(nameInput,new LinearLayout.LayoutParams(-1,dp(43)));
        editor.addView(text("Message",13,CompanionStyle.ACCENT,true));
        EditText messageInput=CompanionStyle.input(ctx,"Text shown in VRChat (up to 90 characters)");
        messageInput.setSingleLine(false);
        messageInput.setMinLines(2);
        messageInput.setMaxLines(3);
        messageInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(90)});
        if(editing!=null)messageInput.setText(editing.optString("text",""));
        oscTextEntry(messageInput);
        editor.addView(messageInput,new LinearLayout.LayoutParams(-1,-2));
        final boolean[] flags={
            editing!=null&&editing.optBoolean("time",false),
            editing!=null&&editing.optBoolean("battery",false)
        };
        Button timeButton=CompanionStyle.button(ctx,"Time: "+(flags[0]?"ON":"OFF"),false);
        Button batteryButton=CompanionStyle.button(ctx,
            "Phone battery: "+(flags[1]?"ON":"OFF"),false);
        editor.addView(timeButton,CompanionStyle.buttonParams(ctx));
        editor.addView(batteryButton,CompanionStyle.buttonParams(ctx));
        TextView preview=text("Preview not sent",12,CompanionStyle.MUTED,false);
        editor.addView(preview);
        Runnable refresh=()->{
            try{
                preview.setText("Preview: "+OscControlStore.previewChatbox(ctx,
                    messageInput.getText().toString(),flags[0],flags[1]));
            }catch(Exception e){preview.setText("Preview unavailable: "+e.getMessage());}
        };
        timeButton.setOnClickListener(v->{
            flags[0]=!flags[0];timeButton.setText("Time: "+(flags[0]?"ON":"OFF"));
            refresh.run();
        });
        batteryButton.setOnClickListener(v->{
            flags[1]=!flags[1];
            batteryButton.setText("Phone battery: "+(flags[1]?"ON":"OFF"));
            refresh.run();
        });
        if(editing!=null)refresh.run();
        button(editor,"Preview (no send)",false,()->{
            host.keyboard(false);messageInput.clearFocus();nameInput.clearFocus();
            refresh.run();
        });
        button(editor,editing==null?"Save new chatbox":"Save changes",true,()->{
            if(!StaffGate.unlocked(ctx))return;
            try{
                OscControlStore.saveChatbox(ctx,oscEditingChatbox,
                    nameInput.getText().toString(),messageInput.getText().toString(),
                    flags[0],flags[1]);
                oscEditingChatbox="";
                host.keyboard(false);
                drawList();
            }catch(Exception e){message("Cannot save: "+e.getMessage());}
        });
        if(editing!=null)button(editor,"Cancel editing",false,()->{
            oscEditingChatbox="";
            host.keyboard(false);
            drawList();
        });
        section(list,"BROADCAST SAFETY");
        line(list,"Manual messages still take priority",
            "Manual chats pause Live Chatbox for about 20 seconds. "+
            "Rotating messages remain silent, stop with the floating service, "+
            "and never start themselves after a restart.",null);
        line(list,"No network dependency",
            "Rotation uses the existing local VRChat OSC sender only. "+
            "It never uploads chat content or profiles to TGC's Relay.",null);
    }
    private void showOscParameters(){
        LinearLayout intro=card(list);
        intro.addView(text("Avatar OSC parameters",17,
            CompanionStyle.TEXT,true));
        intro.addView(text(
            "Control parameters in your CURRENT VRChat avatar. Add the exact "+
            "parameter name from its OSC configuration. These are local "+
            "one-shot actions, not background automation.",
            12,CompanionStyle.MUTED,false));
        JSONArray saved;
        try{saved=OscControlStore.parameters(ctx);}
        catch(Exception ex){
            line(list,"Personal VRChat login required",
                "Sign in to your VRChat account first.",null);return;
        }
        section(list,"SAVED PARAMETER CONTROLS");
        if(saved.length()==0)
            line(list,"No parameter controls yet",
                "Add a Bool, Int or Float control below.",null);
        for(int i=0;i<saved.length();i++){
            JSONObject item=saved.optJSONObject(i);if(item==null)continue;
            final String name=item.optString("name","");
            final String type=item.optString("type","");
            if(!name.matches("[A-Za-z_][A-Za-z0-9_]{0,63}"))continue;
            LinearLayout box=card(list);
            box.addView(text(name+" · "+type.toUpperCase(Locale.US),
                14,CompanionStyle.TEXT,true));
            TextView feedback=text("Not sent",11,CompanionStyle.MUTED,false);
            if("bool".equals(type)){
                LinearLayout row=new LinearLayout(ctx);
                row.setOrientation(LinearLayout.HORIZONTAL);
                box.addView(row);
                Button on=CompanionStyle.button(ctx,"TRUE / ON",false);
                Button off=CompanionStyle.button(ctx,"FALSE / OFF",false);
                row.addView(on,new LinearLayout.LayoutParams(0,dp(44),1f));
                row.addView(off,new LinearLayout.LayoutParams(0,dp(44),1f));
                on.setOnClickListener(v->sendOscParameter(name,type,"true",feedback));
                off.setOnClickListener(v->sendOscParameter(name,type,"false",feedback));
            }else if("int".equals(type)||"float".equals(type)){
                EditText value=CompanionStyle.input(ctx,
                    "int".equals(type)?"Enter 0–255":"Enter -1.0 to 1.0");
                value.setSingleLine(true);
                value.setInputType(android.text.InputType.TYPE_CLASS_NUMBER|
                    android.text.InputType.TYPE_NUMBER_FLAG_SIGNED|
                    ("float".equals(type)?
                        android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL:0));
                oscTextEntry(value);
                value.setText("0");
                box.addView(value,new LinearLayout.LayoutParams(-1,dp(43)));
                button(box,"Send "+name,false,()->{
                    host.keyboard(false);
                    value.clearFocus();
                    sendOscParameter(name,type,value.getText().toString(),feedback);
                });
            }
            box.addView(feedback);
            button(box,"Remove parameter control",false,()->confirm(
                "Remove saved OSC parameter “"+name+"”? No value will be sent.",
                ()->{
                    try{OscControlStore.deleteParameter(ctx,name);drawList();}
                    catch(Exception ex){message("Couldn't remove: "+ex.getMessage());}
                }));
        }
        section(list,"ADD PARAMETER");
        LinearLayout add=card(list);
        EditText name=CompanionStyle.input(ctx,"Exact avatar parameter name");
        name.setSingleLine(true);
        name.setFilters(new InputFilter[]{new InputFilter.LengthFilter(64)});
        oscTextEntry(name);
        add.addView(name,new LinearLayout.LayoutParams(-1,dp(43)));
        add.addView(text("TYPE",12,CompanionStyle.ACCENT,true));
        LinearLayout types=new LinearLayout(ctx);
        types.setOrientation(LinearLayout.HORIZONTAL);
        add.addView(types);
        String[] options={"bool","int","float"};
        ArrayList<Button> picks=new ArrayList<>();
        for(String option:options){
            Button choice=CompanionStyle.button(ctx,option.toUpperCase(Locale.US),
                oscPresetType.equals(option));
            types.addView(choice,new LinearLayout.LayoutParams(0,dp(45),1f));
            picks.add(choice);
        }
        Runnable paint=()->{
            for(int i=0;i<options.length;i++){
                Button choice=picks.get(i);
                choice.setText((oscPresetType.equals(options[i])?"✓ ":"")+
                    options[i].toUpperCase(Locale.US));
            }
        };
        for(int i=0;i<options.length;i++){
            final String option=options[i];
            picks.get(i).setOnClickListener(v->{oscPresetType=option;paint.run();});
        }
        paint.run();
        button(add,"Save parameter control",true,()->{
            if(!StaffGate.unlocked(ctx))return;
            try{
                OscControlStore.saveParameter(ctx,name.getText().toString(),
                    oscPresetType);
                host.keyboard(false);
                name.clearFocus();
                drawList();
                message("Parameter control saved. No OSC value was sent.");
            }catch(Exception ex){name.setError(ex.getMessage());}
        });
        section(list,"IMPORTANT");
        line(list,"Only supported avatar parameters work",
            "The avatar must use the exact parameter name and type. OSC "+
            "does not acknowledge commands; an unsupported parameter is "+
            "silently ignored by VRChat. Only the button you tap sends a value.",
            null);
    }
    /**
     * Native Android IME stays focused on the Companion overlay. VRChat Mobile
     * receives only the explicit local OSC chatbox packet when Send is tapped,
     * avoiding the currently unreliable VRChat Mobile keyboard.
     */
    private void autoOscTyping(boolean on){
        if(!StaffGate.unlocked(ctx))return;
        boolean enabled=false;
        try{enabled=OscControlStore.typingAuto(ctx);}
        catch(Exception ignored){}
        if(on&&!enabled)return;
        if(typingActive==on)return;
        typingActive=on;
        io.execute(()->{
            try{VRChatOscChat.sendTyping(on);}
            catch(Exception ignored){}
        });
    }
    private void showChat(){
        LinearLayout info=card(list);
        info.addView(text("Text chat without VRChat's keyboard",17,
            CompanionStyle.TEXT,true));
        info.addView(text(
            "Type here using your phone's keyboard. Tap Send to place the "+
            "message in VRChat's in-game chatbox. No Shizuku, PC, or "+
            "separate VRChat account login is needed for this tool.",
            12,CompanionStyle.MUTED,false));
        section(info,"BEFORE FIRST USE");
        info.addView(text(
            "In VRChat Mobile, open the Action Menu → OSC and enable OSC. "+
            "Leave VRChat open under this floating panel. If OSC is off, "+
            "the app cannot deliver the chat message.",
            12,CompanionStyle.GOLD,false));
        LinearLayout compose=card(list);
        section(compose,"YOUR MESSAGE");
        EditText entry=CompanionStyle.input(ctx,"Type a message to send in VRChat");
        entry.setSingleLine(false);
        entry.setInputType(InputType.TYPE_CLASS_TEXT|
            InputType.TYPE_TEXT_FLAG_MULTI_LINE|
            InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        entry.setMinLines(3);
        entry.setMaxLines(6);
        entry.setGravity(Gravity.TOP|Gravity.START);
        entry.setFilters(new InputFilter[]{
            new InputFilter.LengthFilter(VRChatOscChat.MAX_CHARACTERS)});
        entry.setText(chatDraft);
        entry.setSelection(entry.length());
        entry.setOnFocusChangeListener((v,focused)->{
            host.keyboard(focused);
            if(!focused)autoOscTyping(false);
        });
        entry.setOnTouchListener((v,event)->{
            if(event.getActionMasked()==android.view.MotionEvent.ACTION_DOWN)
                host.keyboard(true);
            return false;
        });
        compose.addView(entry,new LinearLayout.LayoutParams(-1,-2));
        TextView counter=text("",12,CompanionStyle.MUTED,false);
        counter.setGravity(Gravity.END);
        compose.addView(counter);
        TextView feedback=text(chatNotice,12,CompanionStyle.ACCENT,false);
        compose.addView(feedback);
        Button send=CompanionStyle.button(ctx,"Send to VRChat",true);
        compose.addView(send,CompanionStyle.buttonParams(ctx));
        Runnable refreshControls=()->{
            int count=chatDraft.codePointCount(0,chatDraft.length());
            counter.setText(count+"/144 characters");
            send.setEnabled(!chatSending&&!chatDraft.trim().isEmpty());
            send.setText(chatSending?"Sending…":"Send to VRChat");
        };
        entry.addTextChangedListener(new android.text.TextWatcher(){
            public void beforeTextChanged(CharSequence text,int start,int count,int after){}
            public void onTextChanged(CharSequence text,int start,int before,int count){
                chatDraft=text.toString();
                chatNotice="";
                feedback.setText("");
                autoOscTyping(!chatDraft.trim().isEmpty());
                refreshControls.run();
            }
            public void afterTextChanged(android.text.Editable text){}
        });
        refreshControls.run();
        send.setOnClickListener(v->{
            if(chatSending||!StaffGate.unlocked(ctx)){
                feedback.setText("Staff access must be verified before sending.");
                return;
            }
            final String message=chatDraft;
            try{VRChatOscChat.validate(message);}
            catch(IllegalArgumentException ex){
                feedback.setText(ex.getMessage());return;
            }
            final boolean sfx=chatSound;
            chatSending=true;
            autoOscTyping(false);
            refreshControls.run();
            host.keyboard(false);
            entry.clearFocus();
            feedback.setText("Sending to the VRChat OSC listener on this phone…");
            final int token=revision;
            io.execute(()->{
                String error=null;
                try{VRChatOscChat.send(message,sfx);}
                catch(Exception ex){
                    error=ex.getMessage()==null?"Could not send OSC packet":ex.getMessage();
                }
                final String problem=error;
                handler.post(()->{
                    chatSending=false;
                    chatNotice=problem==null?
                        "OSC packet sent locally. Check your VRChat chatbox; "+
                        "delivery cannot be confirmed by the Companion.":
                        "Couldn't send: "+problem;
                    if(token==revision&&root!=null&&"mini_chat".equals(screen)){
                        feedback.setText(chatNotice);
                        refreshControls.run();
                    }
                });
            });
        });
        button(compose,chatSound?
            "Chat notification sound: ON":"Chat notification sound: OFF",
            false,()->{
                chatSound=!chatSound;
                // Keep the current draft when changing this setting.
                drawList();
            });
        button(compose,"Copy message (fallback only)",false,()->{
            if(chatDraft.trim().isEmpty()){
                feedback.setText("Type something to copy first.");return;
            }
            copy("chat draft",chatDraft);
            feedback.setText("Copied. This does NOT send text to VRChat.");
        });
        button(compose,"Clear draft",false,()->{
            entry.setText("");
            chatDraft="";
            feedback.setText("Draft cleared");
        });
        LinearLayout help=card(list);
        help.addView(text("Troubleshooting",15,CompanionStyle.TEXT,true));
        help.addView(text(
            "If Send says the packet was transmitted but no message appears, "+
            "make sure OSC is enabled in VRChat Mobile and you're logged "+
            "into an instance. This is a local UDP command; VRChat does not "+
            "acknowledge receipt. The draft stays here so you can retry.",
            12,CompanionStyle.MUTED,false));
        help.addView(text(
            "VRChat chatbox limits: 144 characters, up to 9 lines. "+
            "Messages are not saved, sent to Discord or uploaded to the Relay.",
            11,CompanionStyle.MUTED,false));
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
        write(action,success,onSuccess,true);
    }
    private void write(Action action,String success,Runnable onSuccess,boolean reload){
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
                    if(reload){
                        message(success);
                        load();
                    }else{
                        // Avoid refetching every favorite group after selecting
                        // an avatar; update the preview/current selection now.
                        drawList();
                        message(success);
                    }
                }else message("Action failed: "+shortValue(problem,110));
            });
        });
    }
}
