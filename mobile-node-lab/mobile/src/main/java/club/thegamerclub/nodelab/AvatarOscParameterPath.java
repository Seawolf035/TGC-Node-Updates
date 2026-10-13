package club.thegamerclub.nodelab;

import java.util.regex.Pattern;

/**
 * Normalizes user-entered VRChat avatar OSC addresses.
 *
 * Accepts "Go/IsFeral" (parameter path) or the complete address
 * "/avatar/parameters/Go/IsFeral". Store only the relative path so existing
 * single-segment saved controls continue to work unchanged.
 *
 * Validation is deliberately limited to well-formed avatar parameter paths:
 * no empty segments, whitespace, control chars, alternate OSC endpoints, or
 * arbitrary UDP addresses. OSC packets are always sent to local VRChat:9000.
 */
public final class AvatarOscParameterPath {
    public static final String PREFIX="/avatar/parameters/";
    public static final int MAX_RELATIVE_LENGTH=192;
    public static final int MAX_INPUT_LENGTH=PREFIX.length()+MAX_RELATIVE_LENGTH;
    private static final Pattern SEGMENTS=Pattern.compile(
        "[A-Za-z_][A-Za-z0-9_]{0,63}(?:/[A-Za-z_][A-Za-z0-9_]{0,63})*");

    private AvatarOscParameterPath(){}

    public static String relative(String input){
        if(input==null)throw invalid();
        String value=input.trim();
        if(value.startsWith(PREFIX))value=value.substring(PREFIX.length());
        else if(value.startsWith("/"))
            throw new IllegalArgumentException(
                "Use /avatar/parameters/Go/IsFeral or Go/IsFeral.");
        if(value.isEmpty()||value.length()>MAX_RELATIVE_LENGTH||
            !SEGMENTS.matcher(value).matches())throw invalid();
        return value;
    }

    public static boolean isValid(String value){
        try{relative(value);return true;}
        catch(IllegalArgumentException e){return false;}
    }

    public static String address(String input){
        return PREFIX+relative(input);
    }

    private static IllegalArgumentException invalid(){
        return new IllegalArgumentException(
            "Enter an avatar parameter such as Go/IsFeral or "+
            "/avatar/parameters/Go/IsFeral. Use letters, digits, underscores "+
            "and / between non-empty path segments.");
    }
}
