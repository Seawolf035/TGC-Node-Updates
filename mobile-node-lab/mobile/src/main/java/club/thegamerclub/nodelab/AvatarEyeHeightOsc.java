package club.thegamerclub.nodelab;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** Standard VRChat OSC avatar eye height in metres; no Android/network dependencies. */
final class AvatarEyeHeightOsc {
    static final float MIN_SUPPORTED_METERS=0.1f;
    static final float MAX_SUPPORTED_METERS=100.0f;
    static final String ADDRESS="/avatar/eyeheight";
    private AvatarEyeHeightOsc(){}

    static float parse(String text){
        if(text==null||text.trim().isEmpty())
            throw new IllegalArgumentException("Enter an eye height in metres.");
        final float metres;
        try{metres=Float.parseFloat(text.trim());}
        catch(NumberFormatException e){
            throw new IllegalArgumentException("Enter a valid height in metres.");
        }
        if(!Float.isFinite(metres)||
           metres<MIN_SUPPORTED_METERS||metres>MAX_SUPPORTED_METERS)
            throw new IllegalArgumentException("Use 0.1 to 100 metres (VRChat's supported range).");
        return metres;
    }
    static String display(float metres){
        return String.format(Locale.US,"%.2f m",metres);
    }
    private static void oscString(ByteArrayOutputStream out,String value){
        byte[] data=value.getBytes(StandardCharsets.UTF_8);
        out.write(data,0,data.length);out.write(0);
        while(out.size()%4!=0)out.write(0);
    }
    static byte[] packet(float metres)throws Exception{
        if(!Float.isFinite(metres)||
           metres<MIN_SUPPORTED_METERS||metres>MAX_SUPPORTED_METERS)
            throw new IllegalArgumentException("Unsupported eye height.");
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        oscString(out,ADDRESS);
        oscString(out,",f");
        new DataOutputStream(out).writeFloat(metres); // OSC float32, network order
        return out.toByteArray();
    }
}
