package club.thegamerclub.nodelab;

import android.content.Context;
import org.json.JSONObject;

/**
 * Owner development/test access for features and exclusive local UI previews.
 *
 * Future exclusive-feature eligibility checks may combine their intended
 * user's exact authorization with {@link #eligible(Context)}. This never
 * supplies a staff role, bypasses a Relay check, or bypasses an authenticated
 * VRChat session. Existing backend access rules remain authoritative.
 */
public final class OwnerFeaturePreviewAccess {
    private static final String OWNER_DISCORD_ID="197820946608947201";
    private static final String OWNER_VRCHAT_ID="usr_2a833076-03ac-4212-aeff-0708d8eab968";

    private OwnerFeaturePreviewAccess(){}

    /**
     * Exact verified owner pair, on phone OR Quest.
     * Keep this centralized for all future account-exclusive UI previews.
     */
    public static boolean eligible(Context context){
        if(context==null || StaffGate.delegated(context) ||
            !StaffGate.verifiedDiscordMatches(context,OWNER_DISCORD_ID))return false;
        try{
            JSONObject session=VRChatSession.load(context);
            return OWNER_VRCHAT_ID.equals(session.optString("userId","")) &&
                !session.optString("auth","").isEmpty() &&
                !session.optBoolean("needs2fa",true);
        }catch(Exception ignored){return false;}
    }
}
