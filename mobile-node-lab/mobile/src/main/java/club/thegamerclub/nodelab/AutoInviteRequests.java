package club.thegamerclub.nodelab;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import org.json.JSONArray;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Explicit per-personal-account opt-in. Responds ONLY to new VRChat requestInvite
 * notifications from this Companion's locally pinned Favorite Friends by sending
 * an ordinary VRChat invite to the user's verified CURRENT instance.
 *
 * Never accepts friend requests, never joins a world, never uses cached inbox
 * entries for actions, and never touches the TGC GM1/GM4 group invite network.
 */
public final class AutoInviteRequests {
    private static final String PREF="tgc_auto_invite_requests_v1";
    private static final String PERIODIC="tgc_auto_invite_request_checks";
    private static final String FIRST="tgc_auto_invite_request_first";
    private static final long MAX_AGE_MS=24L*60L*60_000L;
    private AutoInviteRequests(){}

    private static String account(Context ctx){
        try{
            JSONObject s=VRChatSession.load(ctx);
            String user=s.optString("userId","");
            if(s.optString("auth","").isEmpty()||s.optBoolean("needs2fa",false)||
               !user.matches("usr_[0-9a-fA-F-]{36}"))return "";
            byte[] digest=MessageDigest.getInstance("SHA-256").digest(
                user.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex=new StringBuilder();
            for(byte b:digest)hex.append(String.format(Locale.US,"%02x",b&255));
            return hex.toString();
        }catch(Exception e){return "";}
    }
    private static SharedPreferences prefs(Context ctx){
        return ctx.getSharedPreferences(PREF,Context.MODE_PRIVATE);
    }
    public static boolean enabled(Context ctx){
        String user=account(ctx);
        return !user.isEmpty() && prefs(ctx).getBoolean(user+"_enabled",false);
    }
    public static void setEnabled(Context ctx,boolean enabled){
        if(!StaffGate.unlocked(ctx))throw new SecurityException("Verified staff required");
        String user=account(ctx);
        if(user.isEmpty())throw new SecurityException("Sign in to your VRChat account first");
        SharedPreferences.Editor edit=prefs(ctx).edit().putBoolean(user+"_enabled",enabled);
        if(enabled)edit.putLong(user+"_enabled_at",System.currentTimeMillis());
        edit.apply();
        schedule(ctx);
    }
    public static void schedule(Context ctx){
        WorkManager manager=WorkManager.getInstance(ctx.getApplicationContext());
        if(!enabled(ctx)){
            manager.cancelUniqueWork(PERIODIC);
            manager.cancelUniqueWork(FIRST);
            return;
        }
        Constraints network=new Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED).build();
        manager.enqueueUniquePeriodicWork(PERIODIC,ExistingPeriodicWorkPolicy.KEEP,
            new PeriodicWorkRequest.Builder(AutoInviteRequestWorker.class,15,TimeUnit.MINUTES)
                .setConstraints(network).build());
        manager.enqueueUniqueWork(FIRST,ExistingWorkPolicy.REPLACE,
            new OneTimeWorkRequest.Builder(AutoInviteRequestWorker.class)
                .setConstraints(network).build());
    }
    /** Restores previously opted-in periodic work without enrolling new users. */
    public static void restore(Context ctx){
        if(!enabled(ctx))return;
        Constraints network=new Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED).build();
        WorkManager.getInstance(ctx.getApplicationContext()).enqueueUniquePeriodicWork(
            PERIODIC,ExistingPeriodicWorkPolicy.KEEP,
            new PeriodicWorkRequest.Builder(AutoInviteRequestWorker.class,15,TimeUnit.MINUTES)
                .setConstraints(network).build());
    }
    private static long timestamp(JSONObject item){
        String raw=item.optString("created_at",item.optString("createdAt",""));
        try{return Instant.parse(raw).toEpochMilli();}
        catch(Exception ignored){
            try{return OffsetDateTime.parse(raw).toInstant().toEpochMilli();}
            catch(Exception ex){return 0L;}
        }
    }
    public static synchronized int processLive(Context ctx)throws Exception{
        if(!enabled(ctx)||!StaffGate.unlocked(ctx))return 0;
        String account=account(ctx);
        if(account.isEmpty())return 0;
        long enabledAt=prefs(ctx).getLong(account+"_enabled_at",0L);
        if(enabledAt<=0L)return 0;
        if(FriendPinStore.pins(ctx).length()==0)return 0;
        // Both inbox endpoints must succeed; do not act on a partial or cached
        // response. No action from a first-use or an old unread notification.
        InboxRepository.Snapshot live=InboxRepository.refresh(ctx);
        if(live.partial)return 0;

        HashSet<String> pinned=new HashSet<>();
        JSONArray pins=FriendPinStore.pins(ctx);
        for(int i=0;i<pins.length();i++){
            JSONObject pin=pins.optJSONObject(i);
            if(pin!=null)pinned.add(pin.optString("id",""));
        }
        JSONArray previous;
        try{previous=new JSONArray(prefs(ctx).getString(account+"_handled","[]"));}
        catch(Exception e){previous=new JSONArray();}
        HashSet<String> handled=new HashSet<>();
        for(int i=0;i<previous.length();i++)handled.add(previous.optString(i,""));
        JSONArray updates=new JSONArray();
        long now=System.currentTimeMillis();
        for(int i=0;i<live.items.length();i++){
            JSONObject notification=live.items.optJSONObject(i);
            if(notification==null||
               !"requestInvite".equalsIgnoreCase(notification.optString("type",""))||
               notification.optBoolean("seen",false))continue;
            String id=notification.optString("id","");
            String sender=notification.optString("senderUserId","");
            long created=timestamp(notification);
            if(!id.matches("not_[0-9a-zA-Z_-]{4,90}")||
               !sender.matches("usr_[0-9a-fA-F-]{36}")||
               !pinned.contains(sender)||handled.contains(id)||
               created<=enabledAt||created>now+60_000L||
               now-created>MAX_AGE_MS)continue;
            updates.put(new JSONObject().put("id",id).put("sender",sender));
            if(updates.length()>=5)break; // Conservative outbound VRChat limit.
        }
        if(updates.length()==0)return 0;
        // This CURRENT location is retrieved from VRChat online, never from
        // saved user profile, friend presence, or tracking logs.
        JSONObject me=VRChatApi.currentUser(ctx);
        String location=me.optString("location","");
        // Do not invite if VRChat reports offline, traveling or hidden.
        // A VRChat world location is worldId:instanceId; the invite API
        // expects only the instanceId portion. Refuse mismatched fields.
        try{VRChatApi.normalizeLocation(location);}
        catch(IllegalArgumentException invalid){return 0;}
        String instance=location.substring(location.indexOf(':')+1);
        String separateId=me.optString("instanceId","");
        if(instance.isEmpty()||
           (!separateId.isEmpty()&&!instance.equals(separateId)))return 0;

        int sent=0;
        for(int i=0;i<updates.length();i++){
            if(!enabled(ctx)||!StaffGate.unlocked(ctx)||
               !account.equals(account(ctx)))break;
            JSONObject todo=updates.optJSONObject(i);
            if(todo==null)continue;
            String sender=todo.optString("sender","");
            // A removed pin or ended friendship can never keep authorization.
            if(!FriendPinStore.contains(ctx,sender))continue;
            JSONObject status=VRChatApi.getFriendStatus(ctx,sender);
            if(!status.optBoolean("isFriend",false))continue;
            VRChatApi.inviteUserToCurrentInstance(ctx,sender,instance);
            handled.add(todo.optString("id",""));
            // Commit each success to avoid repeat sends after process death.
            JSONArray bounded=new JSONArray();
            java.util.ArrayList<String> ids=new java.util.ArrayList<>(handled);
            int start=Math.max(0,ids.size()-500);
            for(int n=start;n<ids.size();n++)bounded.put(ids.get(n));
            prefs(ctx).edit().putString(account+"_handled",bounded.toString())
                .putLong(account+"_last_sent",System.currentTimeMillis()).apply();
            sent++;
        }
        return sent;
    }
}
