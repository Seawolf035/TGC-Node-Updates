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
    private LinearLayout root,content;
    private TextView status;
    private EditText filter;
    private StaffDirectoryRepository.Snapshot roster;
    private boolean loading=false,showUncollected=false;
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
        content=new LinearLayout(activity);content.setOrientation(LinearLayout.VERTICAL);
        root.addView(content);
        roster=StaffDirectoryRepository.cached(activity);
        if(roster!=null)draw();
        else if(!hasVrchatLogin()){
            status.setText("Connect your personal VRChat account to check staff friendships "+
                "and collect achievements. The official staff list is available after login.");
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
    /** Release asynchronous resources before the main tab replaces this panel. */
    public void dispose(){disposed=true;io.shutdownNow();}
    private void refresh(boolean force){
        if(loading)return;
        if(!force){
            StaffDirectoryRepository.Snapshot cache=StaffDirectoryRepository.cached(activity);
            if(cache!=null){roster=cache;draw();return;}
        }
        loading=true;status.setText("Loading live staff membership and confirmed friendships…");
        io.execute(()->{
            StaffDirectoryRepository.Snapshot result=null;
            String error=null;
            try{result=StaffDirectoryRepository.load(activity);}
            catch(Exception ex){error=ex.getMessage();}
            final StaffDirectoryRepository.Snapshot available=result;
            final String failure=error;
            activity.runOnUiThread(()->{
                loading=false;
                if(disposed||activity.isFinishing()||activity.isDestroyed()||!StaffGate.unlocked(activity))return;
                if(available!=null){roster=available;skipped.clear();draw();}
                else{status.setText("Could not load Staff Directory: "+failure+
                    "\nSign into VRChat and check the official roster endpoint. "+
                    "Nothing will be counted or requested until live data loads.");}
            });
        });
    }
    private void achievement(LinearLayout target,String title,String detail,boolean complete){
        LinearLayout row=card(target);
        row.addView(text((complete?"✓ ":"○ ")+title,15,
            complete?CompanionStyle.GOOD:CompanionStyle.TEXT,true));
        row.addView(text(detail,12,CompanionStyle.MUTED,false));
    }
    private void draw(){
        if(content==null||roster==null)return;
        content.removeAllViews();
        int total=0,confirmed=0;
        for(StaffDirectoryRepository.Entry e:roster.members){
            // The player's own profile isn't collectible.
            if(e.id.equals(roster.accountId)||!e.canFriend())continue;
            total++;if(e.friend)confirmed++;
        }
        final int c=confirmed,t=total;
        status.setText("Confirmed friends: "+c+" / "+t+" staff"+
            (roster.rosterComplete?"":" · partial staff list")+
            (roster.friendsComplete?"":" · friend scan incomplete")+
            "\nA sent or pending friend request does not count as collected.");
        LinearLayout achievements=card(content);
        achievements.addView(text("YOUR STAFFDEX · ACHIEVEMENTS",16,CompanionStyle.ACCENT,true));
        space(achievements,6);
        achievements.addView(text(total==0?"No staff results yet":
            Math.round(100f*c/total)+"% confirmed ("+c+"/"+total+")",
            21,CompanionStyle.TEXT,true));
        achievement(achievements,"First Catch!","Add 1 staff member",c>=1);
        achievement(achievements,"Full Party","Add 6 staff members",c>=6);
        achievement(achievements,"Half the Roster","Add at least half the confirmed roster",
            roster.rosterComplete&&roster.friendsComplete&&total>0&&c*2>=total);
        achievement(achievements,"Gotta Add ’Em All!","Add all current staff (including new roster members)",
            roster.rosterComplete&&roster.friendsComplete&&total>0&&c==total);
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
