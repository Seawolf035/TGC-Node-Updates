package club.thegamerclub.nodelab;

import android.content.Context;
import android.os.SystemClock;
import org.json.JSONObject;
import java.util.Locale;

/**
 * Deployment-locked staff authorization.
 *
 * An active verified grant MUST remain valid while routine HTTPS refreshes run.
 * Transient relay/network issues never fabricate an approval, and can only keep
 * the last explicitly approved, process-local grant until its bounded deadline.
 * A confirmed authorization rejection immediately revokes the grant.
 *
 * Cold app starts always require a successful live node_status call.
 */
public final class StaffGate {
    private static final long OFFLINE_GRACE_MS=12L*60*1000; // max since last successful relay response
    private static volatile long approvedElapsed=0L;
    private static volatile String verifiedNode="",verifiedDiscord="",verifiedRank="";
    private static volatile String verifiedAccessType="",authorizedVrchatUser="";
    private StaffGate(){}

    public static synchronized void lock(){
        approvedElapsed=0L;
        verifiedNode="";
        verifiedDiscord="";
        verifiedRank="";
        verifiedAccessType="";
        authorizedVrchatUser="";
        ThemePreferences.resetRuntime();
    }
    public static boolean unlocked(Context context){
        long approved=approvedElapsed;
        // elapsedRealtime includes device sleep, and is not affected by clock changes.
        if(approved==0L || SystemClock.elapsedRealtime()-approved>=OFFLINE_GRACE_MS)return false;
        try{
            MobileCredentials.Record c=MobileCredentials.load(context);
            return c!=null && c.nodeId.equals(verifiedNode) && c.discordId.equals(verifiedDiscord);
        }catch(Exception ignored){return false;}
    }
    /** An owner-issued, Relay-verified non-staff Companion deployment. */
    public static boolean delegated(Context context){
        return unlocked(context) && "delegated".equals(verifiedAccessType);
    }
    public static boolean officialOscTagsAllowed(Context context){
        return unlocked(context) && !delegated(context);
    }
    /** The VRChat identity bound to the current delegated access grant.
     *  Never trusted from the Android client's settings or intent extras. */
    public static String authorizedVrchatUser(Context context){
        return delegated(context)?authorizedVrchatUser:"";
    }
    public static void assertPersonalVrchatAccount(Context context,String accountId){
        String required=authorizedVrchatUser(context);
        if(!required.isEmpty()&&!required.equals(accountId))
            throw new SecurityException("This delegated Companion is linked to a different VRChat account. Sign in with the authorized account.");
    }
    /** Exact Discord identity asserted by a currently valid Relay grant. */
    public static boolean verifiedDiscordMatches(Context context,String expectedId){
        return expectedId!=null && !expectedId.isEmpty() &&
            unlocked(context) && expectedId.equals(verifiedDiscord);
    }
    /** Only a currently authorized, process-local Relay response can unlock ranks. */
    public static int rankTier(Context context){
        if(!unlocked(context))return 0;
        switch(verifiedRank){
            case "Trial Moderator":return 1;
            case "Moderator":return 2;
            case "Head Moderator":return 3;
            case "Admin":return 4;
            case "Head Admin":return 5;
            case "Owner":return 6;
            case "The Royalty":return 7;
            default:return 0; // Old Relay or no rank: common-only, never elevate.
        }
    }
    public static String rankLabel(Context context){
        if(!unlocked(context))return "Not verified";
        if(delegated(context))return "Delegated Companion";
        return verifiedRank.isEmpty()?"Rank unavailable":verifiedRank;
    }
    /** True only if the last attempt was online approved; never treat cached credentials as approval. */
    public static synchronized JSONObject verifyOnline(Context context) throws Exception{
        MobileCredentials.Record creds;
        try{
            creds=MobileCredentials.load(context);
        }catch(Exception invalidKey){
            lock();
            throw new SecurityException("Stored deployment credentials cannot be read",invalidKey);
        }
        if(creds==null){
            lock();
            throw new SecurityException("Staff Quick Deployment required");
        }

        JSONObject result;
        try{
            result=MobileRelay.post(creds.url,MobileRelay.authorized(creds,"node_status"));
        }catch(Exception e){
            // Only a specific authorization denial is conclusive. HTTPS timeouts,
            // relay outages, rate limits and parse errors are NOT staff revocations.
            if(isDefiniteDenial(e)){
                lock();
                throw new SecurityException("The TGC relay rejected this deployment",e);
            }
            throw e;
        }
        confirmRelayApproval(creds,result);
        return result;
    }
    /**
     * The already-running foreground Node performs its own authenticated
     * node_status check every ~90 seconds. Successful replies can extend the
     * same process-local staff grant while VRChat is foregrounded.
     * There is no extra network request and no authorization from cached data.
     */
    public static synchronized void confirmRelayApproval(MobileCredentials.Record creds,
                                                          JSONObject result){
        if(creds==null || result==null ||
           !result.optBoolean("verified",false) ||
           !"mobile_companion".equals(result.optString("client_type")) ||
           !creds.discordId.equals(result.optString("discord_id"))){
            lock();
            throw new SecurityException("TGC staff deployment revoked or no longer authorized");
        }
        String access=result.optString("access_type","staff");
        String delegatedVrcId=result.optString("linked_vrchat_user_id","");
        if("delegated".equals(access)){
            // Only one specific Relay-asserted grant is currently supported.
            // Any unrecognized delegation, forged staff rank, or mismatched
            // VRChat identity fails closed.
            if(!"1228529346788982794".equals(creds.discordId) ||
               !"usr_0f534ae6-c5c4-4025-9478-d5423da1f296".equals(delegatedVrcId) ||
               !result.optString("staff_rank","").isEmpty()){
                lock();
                throw new SecurityException("Delegated Companion identity was not authorized");
            }
        }else if(!"staff".equals(access)){
            lock();
            throw new SecurityException("Unknown Relay Companion access grant");
        }
        String returnedRank="delegated".equals(access)?"":result.optString("staff_rank","");
        // Defense in depth: even a malformed rank response cannot grant
        // Royalty to a fourth ID. Backend roster sync also enforces this.
        if("The Royalty".equals(returnedRank) &&
            !"197820946608947201".equals(creds.discordId) &&
            !"344278828014895104".equals(creds.discordId) &&
            !"1385600758010876024".equals(creds.discordId))
            returnedRank="";
        verifiedRank=returnedRank;
        verifiedAccessType=access;
        authorizedVrchatUser="delegated".equals(access)?delegatedVrcId:"";
        verifiedNode=creds.nodeId;
        verifiedDiscord=creds.discordId;
        approvedElapsed=SystemClock.elapsedRealtime();
    }
    private static boolean isDefiniteDenial(Exception e){
        // The Apps Script API often returns errors inside JSON with HTTP 200.
        // Don't classify connection errors or temporary server errors as revocation.
        String message=e.getMessage();
        if(message==null)return false;
        String m=message.toLowerCase(Locale.US);
        return m.contains("revoked") ||
          m.contains("invalid node key") ||
          m.contains("unknown node") ||
          m.contains("node not found") ||
          m.contains("staff id not") ||
          m.contains("not active staff") ||
          m.contains("not authorized staff") ||
          m.contains("staff no longer") ||
          m.contains("node disabled") ||
          m.contains("delegated companion access revoked") ||
          m.contains("relay http 401") ||
          m.contains("relay http 403");
    }
}
