package club.thegamerclub.nodelab;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONObject;

/**
 * Local onboarding state. This does not authorize staff access, assert a
 * VERIFIED VRChat logging setting, or silently accept a software license.
 */
public final class CompanionSetupState {
    private static final String PREF="tgc_companion_guided_setup_v1";
    private static final String COMPLETE="completed";
    private static final String FULL_ACK="quest_full_logging_user_confirmed";
    private CompanionSetupState(){}

    public static boolean vrchatSessionReady(Context context){
        try{
            JSONObject session=VRChatSession.load(context);
            String id=session.optString("userId","");
            String assigned=StaffGate.authorizedVrchatUser(context);
            return id.matches("usr_[0-9a-fA-F-]{36}") &&
                (assigned.isEmpty()||assigned.equals(id)) &&
                !session.optString("auth","").isEmpty() &&
                !session.optBoolean("needs2fa",true);
        }catch(Exception e){return false;}
    }
    public static boolean fullLoggingAcknowledged(Context context){
        return context.getSharedPreferences(PREF,0).getBoolean(FULL_ACK,false);
    }
    /** User confirmation only: VRChat's setting cannot be verified from here. */
    public static void confirmFullLogging(Context context){
        context.getSharedPreferences(PREF,0).edit().putBoolean(FULL_ACK,true).apply();
        TrackingSetupGuide.acknowledge(context);
    }
    public static boolean isComplete(Context context){
        if(!context.getSharedPreferences(PREF,0).getBoolean(COMPLETE,false))
            return false;
        if(!vrchatSessionReady(context))return false;
        if(QuestDevice.isQuestDevice()){
            return fullLoggingAcknowledged(context) &&
                QuestSafLogWatcher.hasGrant(context);
        }
        return true;
    }
    public static boolean shouldShow(Context context){
        return CompanionLicense.accepted(context) &&
            StaffGate.unlocked(context) && !isComplete(context);
    }
    public static boolean complete(Context context){
        if(!StaffGate.unlocked(context)||!vrchatSessionReady(context))
            return false;
        if(QuestDevice.isQuestDevice() &&
            (!fullLoggingAcknowledged(context)||
             !QuestSafLogWatcher.hasGrant(context)))
            return false;
        return context.getSharedPreferences(PREF,0).edit()
            .putBoolean(COMPLETE,true).commit();
    }
}
