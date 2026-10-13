package club.thegamerclub.nodelab;

import android.content.Context;
import org.json.JSONArray;

/** Local per-account shortcuts for friends, not remote VRChat favorites. */
public final class FriendPinStore {
    private FriendPinStore(){}
    public static JSONArray pins(Context ctx){
        return new JSONArray();
    }
}