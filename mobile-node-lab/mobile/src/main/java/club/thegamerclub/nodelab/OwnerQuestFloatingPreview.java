package club.thegamerclub.nodelab;

import android.content.Context;
import org.json.JSONObject;

/** Manual, rankless-proof, exact-owner-only Quest 2D panel preview.
 *  Independent of the existing phone Quest Tools preview, and does not grant
 *  staff privileges or attempt to bypass Horizon OS panel/pinning controls.
 */
public final class OwnerQuestFloatingPreview {
    private static final String DISCORD_ID="197820946608947201";
    private static final String VRCHAT_ID="usr_2a833076-03ac-4212-aeff-0708d8eab968";
    private OwnerQuestFloatingPreview(){}

    public static boolean eligible(Context context){
        if(!QuestDevice.isQuestDevice() ||
           !StaffGate.verifiedDiscordMatches(context,DISCORD_ID) ||
           StaffGate.delegated(context))return false;
        try{
            JSONObject session=VRChatSession.load(context);
            return VRCHAT_ID.equals(session.optString("userId","")) &&
                !session.optString("auth","").isEmpty() &&
                !session.optBoolean("needs2fa",true);
        }catch(Exception e){return false;}
    }

    /** Call on a background executor on panel open/resume. */
    public static void verifyLive(Context context)throws Exception{
        if(!eligible(context))
            throw new SecurityException("This Quest panel is limited to the verified owner account.");
        JSONObject live=VRChatApi.currentUser(context);
        if(!VRCHAT_ID.equals(live.optString("id","")) || !eligible(context))
            throw new SecurityException("The currently signed-in VRChat account does not match the authorized owner.");
    }
}
