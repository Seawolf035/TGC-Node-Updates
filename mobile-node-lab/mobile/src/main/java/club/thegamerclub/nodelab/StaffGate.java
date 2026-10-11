package club.thegamerclub.nodelab;

import android.content.Context;
import org.json.JSONObject;

/**
 * Fail-closed TGC staff gate. Only live relay node_status approval unlocks
 * Companion screens. Stored deployment credentials alone DO NOT grant access.
 * All grants are process-local and short-lived.
 */
public final class StaffGate {
    private static final long MAX_GRANT_MS=90_000L;
    private static volatile long verifiedUntil=0;
    private static volatile String verifiedNode="",verifiedDiscord="";
    private StaffGate(){}

    public static synchronized void lock(){
        verifiedUntil=0;verifiedNode="";verifiedDiscord="";
    }
    public static boolean unlocked(Context context){
        if(System.currentTimeMillis()>=verifiedUntil)return false;
        try{
            MobileCredentials.Record c=MobileCredentials.load(context);
            return c!=null && c.nodeId.equals(verifiedNode) && c.discordId.equals(verifiedDiscord);
        }catch(Exception ignored){return false;}
    }
    public static synchronized JSONObject verifyOnline(Context context) throws Exception{
        lock(); // Offline, rejected or revoked nodes fail closed.
        MobileCredentials.Record creds=MobileCredentials.load(context);
        if(creds==null)throw new IllegalStateException("Staff Quick Deployment required");
        JSONObject result=MobileRelay.post(creds.url,
          MobileRelay.authorized(creds,"node_status"));
        if(!result.optBoolean("verified",false) ||
            !"mobile_companion".equals(result.optString("client_type")) ||
            !creds.discordId.equals(result.optString("discord_id"))){
            throw new SecurityException("TGC staff deployment revoked or not currently authorized");
        }
        verifiedNode=creds.nodeId;
        verifiedDiscord=creds.discordId;
        verifiedUntil=System.currentTimeMillis()+MAX_GRANT_MS;
        return result;
    }
}
