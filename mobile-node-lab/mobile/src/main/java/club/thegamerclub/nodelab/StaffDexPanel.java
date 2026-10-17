package club.thegamerclub.nodelab;

import android.app.AlertDialog;
import android.content.Intent;
import android.app.Activity;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;

import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Inline full-featured StaffDex, hosted directly inside the main Staff tab. */
public final class StaffDexPanel extends LinearLayout {
    private final Activity activity;
    private boolean disposed=false;
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private LinearLayout root,content,achievementHost;
    private TextView status,syncStatus;
    private EditText filter;
    private StaffDirectoryRepository.Snapshot roster;
    private boolean loading=false,showUncollected=false;
    /** All achievements are collapsed initially, even when already unlocked. */
    private final Set<String> expandedAchievements=new HashSet<>();
    private int nextIndex=0;
    private final Set<String> skipped=new HashSet<>();
    private int dp(float n){return CompanionStyle.dp(activity,n);}
    private TextView text(String s,int n,int c,boolean bold){
        return CompanionStyle.text(activity,s,n,c,bold);
    }
    private LinearLayout card(LinearLayout host){
        LinearLayout c=CompanionStyle.card(activity);
        host.addView(c,CompanionStyle.cardParams(activity));return c;
    }
    private Button button(LinearLayout parent,String title,boolean primary,Runnable callback){
        Button b=CompanionStyle.button(activity,title,primary);
        parent.addView(b,CompanionStyle.buttonParams(activity));
        b.setOnClickListener(v->callback.run());return b;
    }
    private void space(LinearLayout host,int n){
        host.addView(new View(activity),new LinearLayout.LayoutParams(1,dp(n)));
    }
    public StaffDexPanel(Activity host){
        super(host);
        activity=host;
        if(!StaffGate.unlocked(host))return;
        ThemePreferences.apply(host);
        root=this;
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(0,0,0,dp(15));
        root.addView(text("THE GAMER CLUB",11,CompanionStyle.ACCENT,true));
        root.addView(text("Staff Directory",26,CompanionStyle.TEXT,true));
        root.addView(text("COLLECT-A-STAFF! · StaffDex",15,ThemePreferences.royalGold(),true));
        space(root,8);
        root.addView(text("Directory powered by the official TGC Staff Roster. "+
            "Send individual VRChat friend requests and earn achievements for confirmed friends.",
            12,CompanionStyle.MUTED,false));
        LinearLayout controls=card(root);
        button(controls,"Refresh roster and friendships",false,()->refresh(true));
        button(controls,"Collect a Staff! · Next to add",true,this::nextUncollected);
        button(controls,"Show only staff not confirmed as friends",false,()->{
            showUncollected=!showUncollected;draw();
        });
        filter=CompanionStyle.input(activity,"Find staff by name or rank");
        controls.addView(filter,CompanionStyle.buttonParams(activity));
        filter.addTextChangedListener(new TextWatcher(){
            public void beforeTextChanged(CharSequence s,int st,int count,int after){}
            public void onTextChanged(CharSequence s,int st,int before,int count){draw();}
            public void afterTextChanged(Editable s){}
        });
        status=text("Checking the official TGC roster and VRChat friendships…",
            13,CompanionStyle.MUTED,false);
        root.addView(status);
        syncStatus=text("Checking saved StaffDex…",12,CompanionStyle.MUTED,false);
        root.addView(syncStatus);
        content=new LinearLayout(activity);content.setOrientation(LinearLayout.VERTICAL);
        root.addView(content);
        roster=StaffDirectoryRepository.cached(activity);
        if(roster!=null){
            draw();syncStatus.setText("Showing saved results from "+snapshotTime(roster)+
                " · Checking for updates in the background…");
        }
        if(!hasVrchatLogin()){
            if(roster==null)status.setText(
                "Connect your personal VRChat account to check staff friendships "+
                "and collect achievements.");
            syncStatus.setText("Sign in to VRChat to update StaffDex.");
            button(controls,"Sign in to VRChat",true,()->activity.startActivity(
                new Intent(activity,VRChatAccountActivity.class)));
        }else refresh(false);
    }
    private boolean hasVrchatLogin(){
        try{
            org.json.JSONObject session=VRChatSession.load(activity);
            return session.optString("userId","").matches("usr_[0-9a-fA-F-]{36}") &&
                !session.optString("auth","").isEmpty() &&
                !session.optBoolean("needs2fa",true);
        }catch(Exception e){return false;}
    }
    private String snapshotTime(StaffDirectoryRepository.Snapshot snapshot){
        return android.text.format.DateFormat.format(
            "MMM d, h:mm a",new java.util.Date(snapshot.capturedAt)).toString();
    }
    /** Release asynchronous resources before the main tab replaces this panel. */
    public void dispose(){disposed=true;io.shutdownNow();}
    /**
     * Always revalidate in the background on StaffDex open. Keep the previous
     * completed snapshot on screen until a complete scan succeeds, and keep
     * showing it with an explicit stale-data label when offline.
     */
    private void refresh(boolean force){
        if(loading)return;
        if(!hasVrchatLogin()){
            syncStatus.setText("Sign in to VRChat before refreshing StaffDex.");
            return;
        }
        loading=true;
        if(roster!=null){
            syncStatus.setText("Showing saved results from "+snapshotTime(roster)+
                " · Refreshing roster and friends…");
        }else{
            status.setText("Loading StaffDex for the first time…");
            syncStatus.setText("Fetching official roster and VRChat friendships…");
        }
        io.execute(()->{
            StaffDirectoryRepository.Snapshot result=null;
            String error=null;
            try{result=StaffDirectoryRepository.load(activity);}
            catch(Exception ex){error=ex.getMessage();}
            final StaffDirectoryRepository.Snapshot available=result;
            final String failure=error;
            activity.runOnUiThread(()->{
                loading=false;
                if(disposed||activity.isFinishing()||activity.isDestroyed()||
                   !StaffGate.unlocked(activity))return;
                if(available!=null&&available.rosterComplete&&available.friendsComplete){
                    roster=available;skipped.clear();draw();
                    syncStatus.setText("✓ Updated from the official roster and VRChat · "+
                        snapshotTime(available));
                }else if(roster!=null){
                    syncStatus.setText("Couldn't fully refresh; showing saved results from "+
                        snapshotTime(roster)+". "+
                        (failure==null?"Friend scan incomplete.":"Try Refresh when connected."));
                }else if(available!=null){
                    roster=available;draw();
                    syncStatus.setText("Live friend scan incomplete. Progress is provisional; "+
                        "try Refresh again.");
                }else{
                    syncStatus.setText("Couldn't load StaffDex: "+failure+
                        " · Please check your connection and VRChat login.");
                }
            });
        });
    }
    private void achievement(LinearLayout target,String title,String detail,boolean complete){
        LinearLayout row=card(target);
        row.addView(text((complete?"✓ ":"○ ")+title,15,
            complete?CompanionStyle.GOOD:CompanionStyle.TEXT,true));
        row.addView(text(detail,12,CompanionStyle.MUTED,false));
    }
    private void specialItem(LinearLayout holder,String title,String description,
            String userId,boolean owned,int color){
        LinearLayout row=card(holder);
        row.setPadding(dp(11),dp(10),dp(11),dp(10));
        LinearLayout line=new LinearLayout(activity);
        line.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(line);
        LinearLayout words=new LinearLayout(activity);
        words.setOrientation(LinearLayout.VERTICAL);
        line.addView(words,new LinearLayout.LayoutParams(0,-2,1));
        words.addView(text((owned?"✓ ":"○ ")+title,14,
            owned?CompanionStyle.GOOD:color,true));
        words.addView(text(description,11,CompanionStyle.MUTED,false));
        if(userId!=null && userId.matches("usr_[0-9a-fA-F-]{36}")){
            Button visit=CompanionStyle.button(activity,"Profile ›",false);
            visit.setTextSize(12);
            line.addView(visit,new LinearLayout.LayoutParams(dp(89),dp(45)));
            visit.setOnClickListener(v->profile(userId));
        }
    }
    private void specialProgress(LinearLayout host,
        StaffDirectoryRepository.Snapshot snapshot,String title,String hint,
        java.util.Set<String> ids,int color){
        int obtained=SpecialStaffDexAchievements.count(snapshot,ids);
        boolean done=SpecialStaffDexAchievements.complete(snapshot,ids);
        specialItem(host,title,
            hint+" · "+obtained+"/"+ids.size()+
            (snapshot.friendsComplete?"":" · scan incomplete"),
            null,done,color);
    }
    private int gmEarned(StaffDirectoryRepository.Snapshot snapshot,
            java.util.Set<String> gm){
        int earned=0;
        for(String id:GamerMusicProfiles.SET)
            if(SpecialStaffDexAchievements.collected(snapshot,id))earned++;
        if(SpecialStaffDexAchievements.complete(snapshot,gm))earned++;
        return earned;
    }
    private int leadershipEarned(StaffDirectoryRepository.Snapshot snapshot,
            boolean couple,java.util.Set<String> royalty,
            java.util.Set<String> owners,java.util.Set<String> combined){
        int earned=couple?1:0;
        if(SpecialStaffDexAchievements.collected(snapshot,
            SpecialStaffDexAchievements.SEAWOLF))earned++;
        if(SpecialStaffDexAchievements.collected(snapshot,
            SpecialStaffDexAchievements.LILY))earned++;
        for(String id:new String[]{
            SpecialStaffDexAchievements.GAMEWITCH,
            SpecialStaffDexAchievements.IMPOSTER,SpecialStaffDexAchievements.AJ,
            SpecialStaffDexAchievements.ASRIEL,
            SpecialStaffDexAchievements.DARKFLAMES
        })if(SpecialStaffDexAchievements.collected(snapshot,id))earned++;
        if(SpecialStaffDexAchievements.complete(snapshot,royalty))earned++;
        if(SpecialStaffDexAchievements.complete(snapshot,owners))earned++;
        if(SpecialStaffDexAchievements.complete(snapshot,combined))earned++;
        return earned;
    }
    /**
     * Hidden rewards are completely absent from the catalog and total until
     * unlocked by a COMPLETE confirmed-friend scan. A pending friendship,
     * sightings data, or membership in a TGC group never qualifies.
     */
    private int hiddenEarned(StaffDirectoryRepository.Snapshot snapshot){
        return SpecialStaffDexAchievements.collected(snapshot,
            SpecialStaffDexAchievements.DEVINITY)?1:0;
    }
    /** Total special progress shared by the outer header and the expanded categories. */
    private int specialEarned(StaffDirectoryRepository.Snapshot snapshot){
        java.util.Set<String> gm=SpecialStaffDexAchievements.gamerMusic();
        java.util.Set<String> royal=SpecialStaffDexAchievements.royalIds(snapshot);
        java.util.Set<String> owners=SpecialStaffDexAchievements.ownerIds(snapshot);
        java.util.Set<String> combined=SpecialStaffDexAchievements.union(royal,owners);
        boolean couple=SpecialStaffDexAchievements.collected(snapshot,
            SpecialStaffDexAchievements.LILY) &&
            SpecialStaffDexAchievements.collected(snapshot,SpecialStaffDexAchievements.SEAWOLF);
        int earned=gmEarned(snapshot,gm)+
            leadershipEarned(snapshot,couple,royal,owners,combined)+hiddenEarned(snapshot);
        return earned;
    }
    /** Compact one-line header; tapping expands only that achievement group. */
    private boolean categoryHeader(String key,String title,int earned,int total,int color){
        final boolean open=expandedAchievements.contains(key);
        LinearLayout shell=card(achievementHost==null?content:achievementHost);
        LinearLayout bar=new LinearLayout(activity);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(8),dp(4),dp(8),dp(4));
        shell.addView(bar,new LinearLayout.LayoutParams(-1,-2));
        LinearLayout labels=new LinearLayout(activity);
        labels.setOrientation(LinearLayout.VERTICAL);
        bar.addView(labels,new LinearLayout.LayoutParams(0,-2,1));
        labels.addView(text(title,15,color,true));
        labels.addView(text(earned+"/"+total+" unlocked",12,
            CompanionStyle.MUTED,false));
        bar.addView(text(open?"⌃":"⌄",24,CompanionStyle.ACCENT,true));
        bar.setContentDescription(title+", "+earned+" of "+total+
            " unlocked, "+(open?"expanded":"collapsed"));
        bar.setOnClickListener(v->{
            if(!StaffGate.unlocked(activity))return;
            if(!expandedAchievements.add(key))expandedAchievements.remove(key);
            draw();
        });
        return open;
    }
    private void specialAchievements(){
        java.util.Set<String> gm=SpecialStaffDexAchievements.gamerMusic();
        java.util.Set<String> royalty=SpecialStaffDexAchievements.royalIds(roster);
        java.util.Set<String> owners=SpecialStaffDexAchievements.ownerIds(roster);
        java.util.Set<String> combined=
            SpecialStaffDexAchievements.union(royalty,owners);
        int totalSpecial=16+hiddenEarned(roster);
        int earned=specialEarned(roster);
        boolean couple=SpecialStaffDexAchievements.collected(roster,
            SpecialStaffDexAchievements.LILY) &&
            SpecialStaffDexAchievements.collected(roster,SpecialStaffDexAchievements.SEAWOLF);
        // Category summaries are always visible. The many achievement tiles
        // are only created when a particular category is expanded.
        LinearLayout summary=card(achievementHost);
        summary.addView(text("SPECIAL COLLECTIONS · "+earned+"/"+totalSpecial,
            16,CompanionStyle.ACCENT,true));
        summary.addView(text(
            "Open a category below to view its achievements. "+
            "Only confirmed friendships count.",
            12,CompanionStyle.MUTED,false));
        boolean showGm=categoryHeader("gm","♫ GAMER MUSIC",gmEarned(roster,gm),5,
            GamerMusicProfiles.CYAN);
        LinearLayout gmPanel=showGm?card(achievementHost):null;
        if(showGm){
            gmPanel.addView(text("Official numbered accounts, plus the full set",
                12,CompanionStyle.MUTED,false));
        final String[] titles={
            "GM1 · Original Broadcast",
            "GM2 · Initiated into the Cult",
            "GM3 · Third Channel",
            "GM4 · Fourth Frequency"
        };
        final String[] descriptions={
            "Befriend the original Gamer Music account",
            "Befriend the account that runs the Cult of Gamer Music",
            "Befriend Gamer Music 3",
            "Befriend Gamer Music 4"
        };
        for(int i=0;i<GamerMusicProfiles.SET.length;i++){
            String id=GamerMusicProfiles.SET[i];
            specialItem(gmPanel,titles[i],descriptions[i],id,
                SpecialStaffDexAchievements.collected(roster,id),
                GamerMusicProfiles.CYAN);
        }
        specialProgress(gmPanel,roster,"Gamer Music: Complete Discography",
            "Collect all four official Gamer Music accounts",gm,
            GamerMusicProfiles.VIOLET);
        }
        int leadershipEarned=leadershipEarned(roster,couple,royalty,owners,combined);
        boolean showLeaders=categoryHeader("leaders","♛ LEGENDS & LEADERSHIP",
            leadershipEarned,11,ThemePreferences.royalGold());
        LinearLayout leadership=showLeaders?card(achievementHost):null;
        if(showLeaders){

        leadership.addView(text("♛ TGC LEGENDS & LEADERSHIP",15,
            ThemePreferences.royalGold(),true));
        specialItem(leadership,"Gamewitch · The Mysterious One",
            "A legendary friend request: rarely seen in VRChat",
            SpecialStaffDexAchievements.GAMEWITCH,
            SpecialStaffDexAchievements.collected(roster,
                SpecialStaffDexAchievements.GAMEWITCH),
            ThemePreferences.royalGold());
        specialItem(leadership,"Seawolf · The Founder",
            "Befriend Seawolf, founder of The Gamer Club",
            SpecialStaffDexAchievements.SEAWOLF,
            SpecialStaffDexAchievements.collected(roster,
                SpecialStaffDexAchievements.SEAWOLF),
            ThemePreferences.royalGold());
        specialItem(leadership,"Lily · The Lil shit",
            "Befriend Lily",
            SpecialStaffDexAchievements.LILY,
            SpecialStaffDexAchievements.collected(roster,
                SpecialStaffDexAchievements.LILY),
            ThemePreferences.royalGold());
        specialItem(leadership,"Seawolf & Lily · Two Hearts",
            "Befriend both Seawolf and Lily",
            null,couple,ThemePreferences.royalGold());
        specialItem(leadership,"Imposter · Sus",
            "Befriend Imposter",SpecialStaffDexAchievements.IMPOSTER,
            SpecialStaffDexAchievements.collected(roster,
                SpecialStaffDexAchievements.IMPOSTER),CompanionStyle.ACCENT);
        specialItem(leadership,"AJ · The Chairman",
            "Befriend AJ",SpecialStaffDexAchievements.AJ,
            SpecialStaffDexAchievements.collected(roster,
                SpecialStaffDexAchievements.AJ),CompanionStyle.ACCENT);
        specialItem(leadership,"Asriel · The Spawn of Lily and Seawolf",
            "Befriend Asriel",SpecialStaffDexAchievements.ASRIEL,
            SpecialStaffDexAchievements.collected(roster,
                SpecialStaffDexAchievements.ASRIEL),CompanionStyle.ACCENT);
        specialItem(leadership,"Darkflames · ICE Breaker",
            "Even Border Patrol couldn't keep this one off your friends list.",
            SpecialStaffDexAchievements.DARKFLAMES,
            SpecialStaffDexAchievements.collected(roster,
                SpecialStaffDexAchievements.DARKFLAMES),CompanionStyle.ACCENT);
        specialProgress(leadership,roster,"Royal Flush",
            "Befriend every Royalty account (including Gamewitch)",
            royalty,ThemePreferences.royalGold());
        specialProgress(leadership,roster,"Owners' Meeting",
            "Befriend every current Owner and named owner",
            owners,CompanionStyle.ACCENT);
        specialProgress(leadership,roster,"The Entire Upper Deck",
            "Complete both Owner and Royalty collections",
            combined,ThemePreferences.royalGold());
        }
        // This category, reward and even its count do NOT exist in the
        // visible UI until the user's friendship has been confirmed.
        if(hiddenEarned(roster)>0){
            boolean showCommunity=categoryHeader("community",
                "✦ COMMUNITY ENCOUNTERS",1,1,NotableCommunityProfiles.ROSE);
            if(showCommunity){
                LinearLayout community=card(achievementHost);
                specialItem(community,"Devinity • The Gothic Puppy",
                    "You befriended Seawolf's Second Wife.",
                    SpecialStaffDexAchievements.DEVINITY,true,
                    NotableCommunityProfiles.ROSE);
            }
        }
    }
    private void draw(){
        if(content==null||roster==null)return;
        content.removeAllViews();
        achievementHost=null;
        int total=0,confirmed=0;
        for(StaffDirectoryRepository.Entry e:roster.members){
            // The player's own profile isn't collectible.
            if(e.id.equals(roster.accountId)||!e.canFriend())continue;
            total++;if(e.friend)confirmed++;
        }
        final int c=confirmed,t=total;
        // Round only for display; achievement thresholds use exact integer counts.
        int addedPercent=t>0?(int)Math.round(100.0*c/t):0;
        String progress=t>0?addedPercent+"%":"—";
        status.setText("Confirmed friends: "+c+" / "+t+" staff · "+progress+" added"+
            (roster.rosterComplete?"":" · partial staff list")+
            (roster.friendsComplete?"":" · friend scan incomplete")+
            "\nA sent or pending friend request does not count as collected.");
        boolean half=roster.rosterComplete&&roster.friendsComplete&&total>0&&c*2>=total;
        boolean entire=roster.rosterComplete&&roster.friendsComplete&&total>0&&c==total;
        int basic=(c>=1?1:0)+(c>=6?1:0)+(half?1:0)+(entire?1:0);
        // Only one achievement section is visible until the master is opened.
        // Each of the four inner categories keeps its own expand/collapse state.
        if(categoryHeader("all","ACHIEVEMENTS",basic+specialEarned(roster),
            20+hiddenEarned(roster),
            CompanionStyle.ACCENT)){
            achievementHost=new LinearLayout(activity);
            achievementHost.setOrientation(LinearLayout.VERTICAL);
            achievementHost.setPadding(dp(8),0,dp(8),dp(4));
            content.addView(achievementHost);
            if(categoryHeader("basic","STAFFDEX ACHIEVEMENTS",basic,4,
                CompanionStyle.ACCENT)){
                LinearLayout achievements=card(achievementHost);
                achievements.addView(text("Confirmed staff friends: "+c+"/"+t,
                    13,CompanionStyle.MUTED,false));
                achievement(achievements,"First Catch!","Add 1 staff member",c>=1);
                achievement(achievements,"Full Party","Add 6 staff members",c>=6);
                achievement(achievements,"Half the Roster","Add at least half the confirmed roster",half);
                achievement(achievements,"Gotta Add ’Em All!",
                    "Add every current roster member",entire);
            }
            specialAchievements();
            achievementHost=null;
        }
        content.addView(CompanionStyle.section(activity,"STAFF ROSTER"));
        String query=filter==null?"":filter.getText().toString().trim().toLowerCase(Locale.US);
        int shown=0;
        for(StaffDirectoryRepository.Entry e:roster.members){
            if(!query.isEmpty()&&!e.name.toLowerCase(Locale.US).contains(query)&&
               !e.rank.toLowerCase(Locale.US).contains(query))continue;
            if(showUncollected&&(e.friend||e.id.equals(roster.accountId)||!e.canFriend()))continue;
            shown++;
            LinearLayout tile=card(content);
            final boolean royal=e.royalty();
            if(royal)tile.setBackground(CompanionStyle.shape(activity,
                CompanionStyle.SURFACE,18,ThemePreferences.royalGold()));
            tile.addView(text((royal?"♛ ":"")+e.name,17,
                royal?ThemePreferences.royalGold():CompanionStyle.TEXT,true));
            tile.addView(text(e.rank,12,StaffDirectoryRepository.rankColor(e.rank),true));
            tile.addView(text(e.id.equals(roster.accountId)?"Your profile":
                !e.canFriend()?"No VRChat profile linked in roster":
                e.friend?"✓ Confirmed VRChat friend":
                "Not confirmed as a friend",12,
                e.friend?CompanionStyle.GOOD:CompanionStyle.MUTED,false));
            if(e.canFriend())button(tile,"View VRChat profile",false,()->profile(e.id));
            if(e.canFriend()&&!e.friend&&!e.id.equals(roster.accountId))
                button(tile,"Send friend request",true,()->confirmRequest(e));
        }
        if(shown==0)card(content).addView(text("No staff match these filters.",
            13,CompanionStyle.MUTED,false));
    }
    private void profile(String id){
        if(!StaffGate.unlocked(activity))return;
        activity.startActivity(new Intent(activity,VRChatDetailActivity.class)
            .putExtra("kind","user").putExtra("id",id));
    }
    private void nextUncollected(){
        if(roster==null){status.setText("Load the staff directory first.");return;}
        ArrayList<StaffDirectoryRepository.Entry> candidates=new ArrayList<>();
        for(StaffDirectoryRepository.Entry e:roster.members)
            if(e.canFriend()&&!e.friend&&!e.id.equals(roster.accountId)&&!skipped.contains(e.id))
                candidates.add(e);
        if(candidates.isEmpty()){
            new AlertDialog.Builder(activity).setTitle("StaffDex")
                .setMessage("No more eligible staff in the current list. "+
                    "Skipped people will return after Refresh.")
                .setPositiveButton("OK",null).show();return;
        }
        StaffDirectoryRepository.Entry pick=candidates.get(nextIndex%candidates.size());
        new AlertDialog.Builder(activity).setTitle("Collect-a-Staff!")
            .setMessage("Next: "+pick.name+" · "+pick.rank+
                "\n\nSend one VRChat friend request? Requests are not counted "+
                "until the friendship is confirmed.")
            .setNeutralButton("Skip for now",(d,w)->{
                skipped.add(pick.id);nextIndex=0;nextUncollected();
            })
            .setNegativeButton("View profile",(d,w)->profile(pick.id))
            .setPositiveButton("Send request",(d,w)->sendRequest(pick)).show();
    }
    private void confirmRequest(StaffDirectoryRepository.Entry e){
        new AlertDialog.Builder(activity).setTitle("Send VRChat friend request?")
            .setMessage("Send a request to "+e.name+"? This requires their acceptance "+
                "and will not automatically award any achievement.")
            .setNegativeButton("Cancel",null)
            .setPositiveButton("Send",(d,w)->sendRequest(e)).show();
    }
    private void sendRequest(StaffDirectoryRepository.Entry e){
        if(!StaffGate.unlocked(activity))return;
        status.setText("Sending one VRChat friend request…");
        io.execute(()->{
            String result;
            try{VRChatApi.sendFriendRequest(activity,e.id);
                result="Request sent to "+e.name+
                    ". Achievement progress updates only after you become friends. ";
            }catch(Exception ex){result="Request not sent: "+ex.getMessage();}
            final String message=result;
            activity.runOnUiThread(()->{
                if(disposed||activity.isFinishing()||activity.isDestroyed())return;
                status.setText(message);
                Toast.makeText(activity,message,Toast.LENGTH_LONG).show();
                skipped.add(e.id);nextIndex=0;
            });
        });
    }
}
