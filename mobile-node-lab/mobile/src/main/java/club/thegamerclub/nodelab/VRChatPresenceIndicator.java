package club.thegamerclub.nodelab;

import java.util.Locale;

/** Personal VRChat account presence, never the Mobile Node/Relay status. */
public final class VRChatPresenceIndicator {
    private VRChatPresenceIndicator(){}
    public static final int GREEN=0xFF4BDD81;
    public static final int BLUE=0xFF459FFF;
    public static final int YELLOW=0xFFFFD14F;
    public static final int RED=0xFFFF6476;
    public static final int GRAY=0xFF9BA8B7;

    private static String normalize(String value){
        return value==null?"":value.trim().toLowerCase(Locale.US);
    }
    public static int color(String status){
        switch(normalize(status)){
            case "active":return GREEN;
            case "join me":return BLUE;
            case "ask me":return YELLOW;
            case "busy":return RED;
            default:return GRAY;
        }
    }
    public static String label(String status){
        switch(normalize(status)){
            case "active":return "Online";
            case "join me":return "Join Me";
            case "ask me":return "Ask Me";
            case "busy":return "Busy";
            case "offline":return "Offline";
            default:return "Unknown";
        }
    }
}
