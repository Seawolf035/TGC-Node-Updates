package club.thegamerclub.nodelab;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.app.AlertDialog;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Mini VRCX native profile pages: account bios, status, full art,
 * linked worlds and groups, creator information and local sightings.
 * Never opens a browser or sends a personal VRChat cookie to the TGC relay.
 */
public final class VRChatDetailActivity extends StaffProtectedActivity {
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private LinearLayout content;
    private TextView status;
    private String kind,id;
    private long lastRefresh=0L;
    private int dp(float value){return CompanionStyle.dp(this,value);}
    private TextView text(String value,int size,int color,boolean bold){
        return CompanionStyle.text(this,value,size,color,bold);
    }
    private void space(LinearLayout root,int height){
        root.addView(new View(this),new LinearLayout.LayoutParams(1,dp(height)));
    }
    private LinearLayout box(){
        LinearLayout box=CompanionStyle.card(this);
        content.addView(box,CompanionStyle.cardParams(this));
        return box;
    }
    private void kicker(String heading){content.addView(CompanionStyle.section(this,heading));}
    private void detail(LinearLayout box,String title,String value){
        if(value==null||value.trim().isEmpty()||"null".equalsIgnoreCase(value))return;
        TextView label=text(title.toUpperCase(java.util.Locale.US),11,CompanionStyle.ACCENT,true);
        label.setLetterSpacing(.08f);
        box.addView(label);
        space(box,5);
        TextView body=text(value,14,CompanionStyle.TEXT,false);
        body.setTextIsSelectable(true);
        box.addView(body);space(box,15);
    }
    private void link(LinearLayout box,String title,String subtitle,String type,String value){
        if(!valid(type,value))return;
        LinearLayout row=new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(13),dp(14),dp(12),dp(14));
        row.setBackground(CompanionStyle.shape(this,CompanionStyle.RAISED,12,0));
        box.addView(row,CompanionStyle.cardParams(this));
        LinearLayout labels=new LinearLayout(this);labels.setOrientation(LinearLayout.VERTICAL);
        row.addView(labels,new LinearLayout.LayoutParams(0,-2,1f));
        labels.addView(text(title,15,CompanionStyle.TEXT,true));
        if(subtitle!=null&&!subtitle.isEmpty()){
            space(labels,3);labels.addView(text(subtitle,12,CompanionStyle.MUTED,false));
        }
        row.addView(text("›",27,CompanionStyle.ACCENT,false));
        row.setOnClickListener(v->open(type,value));
    }
    private void open(String type,String target){
        if(!StaffGate.unlocked(this)){finish();return;}
        startActivity(new Intent(this,VRChatDetailActivity.class)
            .putExtra("kind",type).putExtra("id",target));
    }
    private static boolean valid(String type,String value){
        String prefix="user".equals(type)?"usr_":"world".equals(type)?"wrld_":
            "group".equals(type)?"grp_":"";
        return !prefix.isEmpty() && value!=null && value.matches(prefix+"[0-9a-fA-F-]{36}");
    }
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);
        if(!StaffGate.unlocked(this)){finish();return;}
        kind=getIntent().getStringExtra("kind");
        id=getIntent().getStringExtra("id");
        if(!valid(kind,id)){finish();return;}
        render();load(false);
    }
    @Override protected void onResume(){super.onResume();if(!StaffGate.unlocked(this))finish();}
    private void render(){
        getWindow().setStatusBarColor(CompanionStyle.BG);
        getWindow().setNavigationBarColor(CompanionStyle.BG);
        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(CompanionStyle.BG);
        content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(15),dp(11),dp(15),dp(24));
        scroll.addView(content);
        setContentView(scroll);
        CompanionStyle.safeArea(this,scroll);
        LinearLayout nav=new LinearLayout(this);nav.setGravity(Gravity.CENTER_VERTICAL);
        content.addView(nav);
        Button back=CompanionStyle.button(this,"‹",false);
        back.setTextSize(27);nav.addView(back,new LinearLayout.LayoutParams(dp(51),dp(43)));
        back.setOnClickListener(v->finish());
        TextView title=text(kind.equals("user")?"Player profile":
            kind.equals("world")?"World details":"Group details",19,CompanionStyle.TEXT,true);
        title.setPadding(dp(11),0,0,0);nav.addView(title);
        space(content,13);
        status=text("Loading details...",13,CompanionStyle.MUTED,false);
        content.addView(status);
        space(content,9);
    }
    private void load(boolean refresh){
        if(!StaffGate.unlocked(this)){finish();return;}
        if(refresh && System.currentTimeMillis()-lastRefresh<30_000){
            status.setText("Please wait a little before refreshing.");return;
        }
        lastRefresh=System.currentTimeMillis();
        io.execute(()->{
            try{
                JSONObject result=kind.equals("user")?VRChatApi.getUserProfile(this,id):
                    kind.equals("world")?VRChatApi.getWorld(this,id):VRChatApi.getGroup(this,id);
                JSONObject encountered=null;
                if(kind.equals("user")){
                    try(MobileOutbox outbox=new MobileOutbox(this)){
                        encountered=outbox.encounter(id);
                    }catch(Exception ignored){}
                }
                final JSONObject local=encountered;
                runOnUiThread(()->{
                    if(!StaffGate.unlocked(this)||isFinishing()||isDestroyed())return;
                    render();
                    show(result,local);
                });
            }catch(Exception e){
                String m=e.getMessage();if(m==null)m="Please try again.";
                final String error=m.substring(0,Math.min(140,m.length()));
                runOnUiThread(()->{if(status!=null)status.setText(
                    "Couldn't load profile. "+error);});
            }
        });
    }
    /** Profile colors are user-chosen 6-digit hex values, never raw drawables. */
    private static int profileColor(String value,int fallback){
        if(value==null)return fallback;
        String hex=value.trim().replace("#","");
        if(!hex.matches("(?i)[0-9a-f]{6}"))return fallback;
        try{return 0xFF000000 | (int)Long.parseLong(hex,16);}
        catch(Exception ignored){return fallback;}
    }
    private void show(JSONObject item,JSONObject local){
        status.setVisibility(View.GONE);
        String title=item.optString("displayName",item.optString("name","Unknown"));
        // VRChat's 2026 profile art uses *different* banner and icon fields.
        // The avatar image must never replace an explicitly configured banner.
        String hero=kind.equals("user")?
            CompanionImages.preferred(item,"bannerUrl","bannerCustomUrl",
                "profilePicOverride"):
            kind.equals("world")?
                CompanionImages.preferred(item,"imageUrl","thumbnailImageUrl"):
                CompanionImages.preferred(item,"bannerUrl","iconUrl");
        String portrait=kind.equals("user")?
            CompanionImages.preferred(item,"iconUrl","userIcon",
                "profilePicOverrideThumbnail","currentAvatarThumbnailImageUrl",
                "currentAvatarImageUrl"):
            kind.equals("world")?
                CompanionImages.preferred(item,"thumbnailImageUrl","imageUrl"):
                CompanionImages.preferred(item,"iconUrl","bannerUrl");
        FrameLayout banner=new FrameLayout(this);
        int bannerTop=profileColor(item.optString("backgroundGradientTop",
            item.optString("bannerColor","")),0xFF244E67);
        int bannerBottom=profileColor(item.optString("backgroundGradientBottom",
            item.optString("bannerColor","")),0xFF15283F);
        banner.setBackground(CompanionStyle.gradient(this,bannerTop,bannerBottom,18));
        banner.setClipToOutline(true);
        content.addView(banner,new LinearLayout.LayoutParams(-1,dp(158)));
        ImageView background=new ImageView(this);
        background.setScaleType(ImageView.ScaleType.CENTER_CROP);
        banner.addView(background,new FrameLayout.LayoutParams(-1,-1));
        CompanionImages.load(background,hero);
        TextView label=CompanionStyle.chip(this,
            kind.equals("user")?"PLAYER":kind.equals("world")?"WORLD":"GROUP",
            CompanionStyle.TEXT,0xCC123149);
        FrameLayout.LayoutParams tag=new FrameLayout.LayoutParams(-2,-2,Gravity.BOTTOM|Gravity.START);
        tag.setMargins(dp(12),0,0,dp(12));banner.addView(label,tag);
        space(content,13);

        LinearLayout person=new LinearLayout(this);person.setGravity(Gravity.CENTER_VERTICAL);
        content.addView(person);
        FrameLayout photo=new FrameLayout(this);
        photo.setBackground(CompanionStyle.shape(this,CompanionStyle.RAISED,16,0));
        photo.setClipToOutline(true);
        person.addView(photo,new LinearLayout.LayoutParams(dp(75),dp(75)));
        TextView glyph=text(kind.equals("user")?"☺":kind.equals("world")?"◇":"◈",
            30,CompanionStyle.ACCENT,true);
        glyph.setGravity(Gravity.CENTER);
        photo.addView(glyph,new FrameLayout.LayoutParams(-1,-1));
        ImageView image=new ImageView(this);image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        photo.addView(image,new FrameLayout.LayoutParams(-1,-1));
        CompanionImages.load(image,portrait);
        LinearLayout names=new LinearLayout(this);names.setOrientation(LinearLayout.VERTICAL);
        names.setPadding(dp(13),0,0,0);
        person.addView(names,new LinearLayout.LayoutParams(0,-2,1f));
        TextView header=text(title,23,CompanionStyle.TEXT,true);
        header.setMaxLines(3);names.addView(header);
        String descriptor=kind.equals("user")?item.optString("pronouns",""):
            kind.equals("world")?"By "+item.optString("authorName","Unknown"):
            item.optString("shortCode","");
        if(!descriptor.isEmpty()){
            space(names,5);
            names.addView(text(descriptor,13,CompanionStyle.MUTED,false));
        }
        Button shareLink=CompanionStyle.button(this,"Copy VRChat link",false);
        shareLink.setTextSize(12);
        LinearLayout.LayoutParams quickLink=new LinearLayout.LayoutParams(-1,dp(42));
        quickLink.topMargin=dp(8);
        names.addView(shareLink,quickLink);
        shareLink.setOnClickListener(v->{
            String part=kind.equals("user")?"user":kind.equals("world")?"world":"group";
            String url="https://vrchat.com/home/"+part+"/"+id;
            ClipboardManager clipboard=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
            if(clipboard!=null){
                clipboard.setPrimaryClip(ClipData.newPlainText("VRChat "+kind+" link",url));
                Toast.makeText(this,"Profile link copied",Toast.LENGTH_SHORT).show();
            }
        });
        space(content,14);
        if(kind.equals("user"))showUser(item,local);
        else if(kind.equals("world"))showWorld(item);
        else showGroup(item);

        kicker("MORE");
        LinearLayout tools=box();
        Button copy=CompanionStyle.button(this,"Copy "+kind+" ID",false);
        tools.addView(copy,CompanionStyle.buttonParams(this));
        copy.setOnClickListener(v->{
            ClipboardManager clip=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
            if(clip!=null){
                clip.setPrimaryClip(ClipData.newPlainText("VRChat ID",id));
                Toast.makeText(this,"Copied",Toast.LENGTH_SHORT).show();
            }
        });
        Button refresh=CompanionStyle.button(this,"Refresh details",true);
        tools.addView(refresh,CompanionStyle.buttonParams(this));
        refresh.setOnClickListener(v->load(true));
    }
    private void showUser(JSONObject user,JSONObject encountered){
        String state=user.optString("status","");
        String location=user.optString("location","");
        LinearLayout statusCard=box();
        String display=state.isEmpty()?"Presence unavailable":
            Character.toUpperCase(state.charAt(0))+state.substring(1).replace('_',' ');
        int color=location.equalsIgnoreCase("offline")?CompanionStyle.MUTED:
            state.equalsIgnoreCase("join me")?CompanionStyle.GOOD:
            state.equalsIgnoreCase("busy")?CompanionStyle.GOLD:CompanionStyle.ACCENT;
        statusCard.addView(CompanionStyle.chip(this,"● "+display,color,0xFF1D3547));
        space(statusCard,13);
        detail(statusCard,"Status message",user.optString("statusDescription",""));
        if(location.equals("offline"))detail(statusCard,"Location","Offline");
        else if(location.equals("private"))detail(statusCard,"Location","Private");
        else if(location.equals("traveling"))detail(statusCard,"Location","Traveling");
        else if(!location.isEmpty() && !location.startsWith("wrld_"))
            detail(statusCard,"Location",location);

        // Keep the actions people use most within the first screen of a profile.
        showUserActions(user);
        kicker("ABOUT");
        LinearLayout bio=box();
        String content=user.optString("bio","").trim();
        // "No bio set" must not be shown when a limited API response omitted
        // the bio entirely. This page prioritizes /profile/{id}.
        String visibleBio=content.isEmpty()?
            (user.has("bio")?"No bio provided.":"Bio isn't available for this profile."):
            content;
        TextView bioText=text(visibleBio,14,
            content.isEmpty()?CompanionStyle.MUTED:CompanionStyle.TEXT,false);
        bioText.setTextIsSelectable(true);
        bio.addView(bioText);
        JSONArray bioLinks=user.optJSONArray("bioLinks");
        if(bioLinks!=null&&bioLinks.length()>0){
            space(bio,14);
            bio.addView(text("LINKS",11,CompanionStyle.ACCENT,true));
            for(int i=0;i<Math.min(bioLinks.length(),6);i++){
                String url=bioLinks.optString(i,"");
                if(!url.isEmpty()){
                    TextView link=text(url,12,CompanionStyle.MUTED,false);
                    link.setTextIsSelectable(true);bio.addView(link);
                }
            }
        }
        kicker("PRIVATE VRCHAT NOTE");
        LinearLayout personalNote=box();
        personalNote.addView(text("Visible only to your personal VRChat account. It is not sent to the TGC staff system.",
            13,CompanionStyle.MUTED,false));
        space(personalNote,11);
        Button note=CompanionStyle.button(this,"View or edit note",false);
        personalNote.addView(note,CompanionStyle.buttonParams(this));
        final String noteTarget=id;
        final String noteName=user.optString("displayName","this player");
        note.setOnClickListener(v->{
            if(!StaffGate.unlocked(this)){finish();return;}
            startActivity(new Intent(this,VRChatNotesActivity.class)
                .putExtra("target_id",noteTarget).putExtra("target_name",noteName));
        });
        if(encountered!=null){
            kicker("YOUR HISTORY");
            LinearLayout history=box();
            history.addView(text(encountered.optInt("count",0)+" encounter"+
                (encountered.optInt("count",0)==1?"":"s"),20,CompanionStyle.TEXT,true));
            space(history,6);
            history.addView(text("Last encountered "+encountered.optString("last_seen",""),
                13,CompanionStyle.MUTED,false));
        }
        String world=location.split(":",2)[0];
        if(valid("world",world)){
            kicker("CURRENT LOCATION");
            LinearLayout nearby=box();
            link(nearby,"View current world","Location shared with your account","world",world);
        }
        kicker("ACCOUNT DETAILS");
        LinearLayout details=box();
        detail(details,"Joined VRChat",user.optString("date_joined",""));
        detail(details,"Last platform",user.optString("last_platform",""));
        detail(details,"Last login",user.optString("last_login",""));
        showProfileExtras(user);
    }
    private interface PersonalAction{void execute()throws Exception;}
    private void confirmedAction(String title,String explanation,PersonalAction task){
        new AlertDialog.Builder(this).setTitle(title)
            .setMessage(explanation+" This action affects your personal VRChat account.")
            .setNegativeButton("Cancel",null)
            .setPositiveButton("Continue",(dialog,which)->{
                if(!StaffGate.unlocked(this)){finish();return;}
                Toast.makeText(this,"Sending request...",Toast.LENGTH_SHORT).show();
                io.execute(()->{
                    String result;
                    try{task.execute();result="Request sent successfully.";}
                    catch(Exception ex){result="VRChat declined: "+ex.getMessage();}
                    final String message=result;
                    runOnUiThread(()->Toast.makeText(this,message,Toast.LENGTH_LONG).show());
                });
            }).show();
    }
    private void showUserActions(JSONObject person){
        String theirs=id;
        String mine="";
        try{mine=VRChatSession.load(this).optString("userId","");}
        catch(Exception ignored){}
        if(theirs.equals(mine))return;
        kicker("INTERACT");
        LinearLayout actionCard=box();
        Button friendship=CompanionStyle.button(this,"Send friend request",false);
        actionCard.addView(friendship,CompanionStyle.buttonParams(this));
        friendship.setOnClickListener(v->confirmedAction("Send friend request?",
            "Ask to become friends with "+person.optString("displayName","this player")+".",
            ()->VRChatApi.sendFriendRequest(this,theirs)));
        Button requestInvite=CompanionStyle.button(this,"Request an invite",true);
        actionCard.addView(requestInvite,CompanionStyle.buttonParams(this));
        requestInvite.setOnClickListener(v->confirmedAction("Request an invite?",
            "Ask this player to invite you to their VRChat instance.",
            ()->VRChatApi.requestInvite(this,theirs)));
        String loc=person.optString("location","");
        try{
            VRChatApi.normalizeLocation(loc);
            Button instance=CompanionStyle.button(this,"View current instance",false);
            actionCard.addView(instance,CompanionStyle.buttonParams(this));
            instance.setOnClickListener(v->startActivity(
                new Intent(this,VRChatInstanceActivity.class).putExtra("location",loc)));
        }catch(Exception ignored){}
        io.execute(()->{
            try{
                JSONObject result=VRChatApi.getFriendStatus(this,theirs);
                boolean friends=result.optBoolean("isFriend",false);
                runOnUiThread(()->{
                    if(isFinishing()||isDestroyed()||!StaffGate.unlocked(this))return;
                    friendship.setVisibility(friends?View.GONE:View.VISIBLE);
                    requestInvite.setVisibility(friends?View.VISIBLE:View.GONE);
                });
            }catch(Exception ignored){
                // Leave the options visible; VRChat enforces account permissions.
            }
        });
    }
    /** Show only items supplied by VRChat's publicly visible profile. */
    private void showProfileExtras(JSONObject user){
        JSONArray badges=user.optJSONArray("badges");
        if(badges!=null){
            LinearLayout badgeCard=null;
            for(int i=0;i<badges.length() && i<40;i++){
                JSONObject badge=badges.optJSONObject(i);
                if(badge==null || badge.optBoolean("hidden",false))continue;
                String badgeName=badge.optString("badgeName","");
                if(badgeName.isEmpty())continue;
                if(badgeCard==null){
                    kicker("BADGES");
                    badgeCard=box();
                }
                badgeCard.addView(text("✦ "+badgeName,14,CompanionStyle.TEXT,true));
                String description=badge.optString("badgeDescription","").trim();
                if(!description.isEmpty()){
                    space(badgeCard,3);
                    badgeCard.addView(text(description,12,CompanionStyle.MUTED,false));
                }
                space(badgeCard,12);
            }
        }
        JSONObject represented=user.optJSONObject("representedGroup");
        if(represented!=null){
            String gId=represented.optString("groupId",represented.optString("id",""));
            if(valid("group",gId)){
                kicker("REPRESENTED GROUP");
                LinearLayout representative=box();
                link(representative,represented.optString("name","Group"),
                    represented.optString("shortCode",""),"group",gId);
            }
        }
        JSONObject groupResponse=user.optJSONObject("groups");
        JSONArray groups=groupResponse==null?user.optJSONArray("groups"):groupResponse.optJSONArray("list");
        if(groups!=null && groups.length()>0){
            kicker("VISIBLE GROUPS");
            LinearLayout container=box();
            for(int i=0;i<Math.min(groups.length(),24);i++){
                JSONObject group=groups.optJSONObject(i);
                if(group==null)continue;
                String groupId=group.optString("groupId",group.optString("id",""));
                link(container,group.optString("name","Group"),
                    group.optString("shortCode",""),"group",groupId);
            }
        }
        JSONArray worlds=user.optJSONArray("publicWorlds");
        if(worlds!=null && worlds.length()>0){
            kicker("PUBLIC WORLDS");
            LinearLayout worldCard=box();
            for(int i=0;i<Math.min(worlds.length(),12);i++){
                JSONObject world=worlds.optJSONObject(i);
                if(world==null)continue;
                link(worldCard,world.optString("name","World"),"",
                    "world",world.optString("id",""));
            }
        }
    }
    private void showWorld(JSONObject world){
        kicker("ABOUT THIS WORLD");
        LinearLayout info=box();
        String description=world.optString("description","").trim();
        info.addView(text(description.isEmpty()?"No description available.":description,
            14,CompanionStyle.TEXT,false));

        kicker("WORLD DETAILS");
        LinearLayout stats=box();
        detail(stats,"Players now",Integer.toString(world.optInt("occupants",0)));
        detail(stats,"Capacity",Integer.toString(world.optInt("capacity",0)));
        detail(stats,"Total visits",Integer.toString(world.optInt("visits",0)));
        detail(stats,"Favorites",Integer.toString(world.optInt("favorites",0)));
        detail(stats,"Published",world.optString("publicationDate",""));
        detail(stats,"Updated",world.optString("updated_at",world.optString("lastUpdate","")));
        JSONArray packages=world.optJSONArray("unityPackages");
        if(packages!=null){
            StringBuilder platforms=new StringBuilder();
            for(int i=0;i<packages.length();i++){
                JSONObject pkg=packages.optJSONObject(i);
                if(pkg==null)continue;
                String platform=pkg.optString("platform","");
                if(!platform.isEmpty()&&platforms.indexOf(platform)<0){
                    if(platforms.length()>0)platforms.append(" · ");
                    platforms.append(platform);
                }
            }
            detail(stats,"Platforms",platforms.toString());
        }
        String worldId=world.optString("id",id);
        if(valid("world",worldId)){
            kicker("LOCAL WORLD BOOKMARKS");
            LinearLayout shortcuts=box();
            boolean bookmarked=false;
            try{bookmarked=WorldBookmarkStore.contains(this,worldId);}
            catch(Exception ignored){}
            Button save=CompanionStyle.button(this,
                bookmarked?"★ Remove world bookmark":"☆ Bookmark this world",false);
            shortcuts.addView(save,CompanionStyle.buttonParams(this));
            save.setOnClickListener(v->{
                try{
                    boolean next=!WorldBookmarkStore.contains(this,worldId);
                    WorldBookmarkStore.setBookmarked(this,worldId,
                        world.optString("name","VRChat world"),next);
                    save.setText(next?"★ Remove world bookmark":"☆ Bookmark this world");
                }catch(Exception e){
                    Toast.makeText(this,"Couldn't save bookmark: "+e.getMessage(),
                        Toast.LENGTH_LONG).show();
                }
            });
            shortcuts.addView(text(
                "Local to this device and your VRChat account; does not change your VRChat favorites.",
                12,CompanionStyle.MUTED,false));
        }
        kicker("CREATOR");
        LinearLayout creator=box();
        link(creator,world.optString("authorName","World creator"),"View creator profile",
            "user",world.optString("authorId",""));
    }
    private void showGroup(JSONObject group){
        kicker("ABOUT THIS GROUP");
        LinearLayout bio=box();
        String description=group.optString("description","").trim();
        bio.addView(text(description.isEmpty()?"No group description.":description,
            14,CompanionStyle.TEXT,false));
        kicker("COMMUNITY");
        LinearLayout detail=box();
        detail(detail,"Members",Integer.toString(group.optInt("memberCount",0)));
        detail(detail,"Online members",Integer.toString(group.optInt("onlineMemberCount",0)));
        detail(detail,"Short code",group.optString("shortCode",""));
        detail(detail,"Joining",group.optString("joinState",""));
        detail(detail,"Visibility",group.optString("privacy",""));
        String groupId=group.optString("id","");
        if(groupId.matches("grp_[0-9a-fA-F-]{36}")){
            kicker("YOUR GROUP SHORTCUTS");
            LinearLayout shortcuts=box();
            boolean pinned=false;
            try{pinned=GroupToolkitStore.contains(this,groupId);}
            catch(Exception ignored){}
            Button pin=CompanionStyle.button(this,
                pinned?"★ Unpin this group":"☆ Pin this group",false);
            shortcuts.addView(pin,CompanionStyle.buttonParams(this));
            pin.setOnClickListener(v->{
                try{
                    boolean next=!GroupToolkitStore.contains(this,groupId);
                    GroupToolkitStore.setPinned(this,groupId,
                        group.optString("name","VRChat group"),next);
                    pin.setText(next?"★ Unpin this group":"☆ Pin this group");
                }catch(Exception ex){
                    android.widget.Toast.makeText(this,
                        "Cannot save shortcut: "+ex.getMessage(),
                        android.widget.Toast.LENGTH_LONG).show();
                }
            });
            shortcuts.addView(text(
                "Pinned groups appear in your saved group shortcuts.",
                12,CompanionStyle.MUTED,false));
            kicker("ADVANCED GROUP TOOLS");
            LinearLayout tools=box();
            for(String mode:new String[]{"members","instances","bans"}){
                String label="members".equals(mode)?"Browse / search members":
                    "instances".equals(mode)?"Active group instances":
                    "Visible group bans (read-only)";
                Button control=CompanionStyle.button(this,label,"members".equals(mode));
                tools.addView(control,CompanionStyle.buttonParams(this));
                control.setOnClickListener(v->startActivity(
                    new Intent(this,VRChatGroupToolsActivity.class)
                        .putExtra("groupId",groupId).putExtra("mode",mode)));
            }
            tools.addView(text("VRChat's personal account permissions apply. "+
                "No group moderation actions are performed by these tools.",
                12,CompanionStyle.MUTED,false));
        }
        JSONArray roles=group.optJSONArray("roles");
        if(roles!=null&&roles.length()>0){
            kicker("GROUP ROLES");
            LinearLayout roleCard=box();
            for(int i=0;i<Math.min(roles.length(),20);i++){
                JSONObject role=roles.optJSONObject(i);
                if(role==null)continue;
                String roleName=role.optString("name","");
                if(!roleName.isEmpty()){
                    roleCard.addView(text("• "+roleName,14,CompanionStyle.TEXT,false));
                    space(roleCard,8);
                }
            }
        }
        kicker("OWNER");
        LinearLayout owner=box();
        link(owner,"View group owner","Owner profile","user",group.optString("ownerId",""));
    }
    @Override protected void onDestroy(){io.shutdownNow();super.onDestroy();}
}
