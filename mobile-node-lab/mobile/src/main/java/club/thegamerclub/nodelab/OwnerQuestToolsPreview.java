package club.thegamerclub.nodelab;

import android.content.Context;
import org.json.JSONObject;

/**
 * Owner-only development preview of Quest Tools on an ordinary Android phone.
 *
 * Both identities are checked, not merely an Owner/Royalty rank:
 * - Discord ID comes from the authenticated and currently valid TGC Relay
 *   staff grant (not caller extras, user-editable settings, or role claims).
 * - VRChat user ID comes from the encrypted personal VRChat session, and is
 *   verified LIVE against VRChat /auth/user before displaying the preview.
 *
 * The IDs identify the only permitted tester. They are not secrets, passwords,
 * enrollment tokens, or a substitute for real Relay/VRChat authentication.
 * This gate never opens floating overlays on Quest and never grants extra
 * Relay, tracking, or group-invite capabilities.
 */
public final class OwnerQuestToolsPreview {
    private static final String OWNER_DISCORD_ID="197820946608947201";
    private static final String OWNER_VRCHAT_ID="usr_2a833076-03ac-4212-aeff-0708d8eab968";
    private OwnerQuestToolsPreview(){}

    public static boolean isOwnerStaff(Context context){
        return !QuestDevice.isQuestDevice() &&
            StaffGate.verifiedDiscordMatches(context,OWNER_DISCORD_ID);
    }

    /** Recheck both identities before every phone preview navigation. */
    public static boolean hasOwnerPair(Context context){
        if(!isOwnerStaff(context))return false;
        try{
            JSONObject session=VRChatSession.load(context);
            return OWNER_VRCHAT_ID.equals(session.optString("userId","")) &&
                !session.optString("auth","").isEmpty() &&
                !session.optBoolean("needs2fa",true);
        }catch(Exception e){return false;}
    }

    /** Execute off the main thread; authenticate the actual VRChat account. */
    public static void verifyLive(Context context)throws Exception{
        if(!isOwnerStaff(context))
            throw new SecurityException("Your verified TGC staff identity does not allow this preview.");
        JSONObject profile=VRChatApi.currentUser(context);
        if(!OWNER_VRCHAT_ID.equals(profile.optString("id","")) ||
           !hasOwnerPair(context))
            throw new SecurityException("Sign in with the authorized VRChat account to use the phone preview.");
    }
}
