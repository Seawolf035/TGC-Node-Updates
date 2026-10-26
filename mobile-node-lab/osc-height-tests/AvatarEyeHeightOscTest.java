package club.thegamerclub.nodelab;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public final class AvatarEyeHeightOscTest {
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    static void bad(String raw){
        boolean failed=false;
        try{AvatarEyeHeightOsc.parse(raw);}catch(IllegalArgumentException e){failed=true;}
        check(failed,"Must reject "+raw);
    }
    public static void main(String[] args)throws Exception{
        check(AvatarEyeHeightOsc.parse("0.8")==0.8f,"Minimum rule preset");
        check(AvatarEyeHeightOsc.parse("2.0")==2f,"Maximum rule preset");
        check(AvatarEyeHeightOsc.parse("0.1")==0.1f,"Supported minimum");
        check(AvatarEyeHeightOsc.parse("100")==100f,"Supported maximum");
        for(String s:new String[]{""," ","abc","NaN","Infinity","-2","0","0.09","100.01"})bad(s);
        byte[] packet=AvatarEyeHeightOsc.packet(.8f);
        byte[] address=("/avatar/eyeheight\0").getBytes(StandardCharsets.UTF_8);
        check(Arrays.equals(Arrays.copyOf(packet,address.length),address),"OSC endpoint");
        check(packet.length%4==0,"OSC padding");
        int offset=(address.length+3)/4*4;
        check(packet[offset]==','&&packet[offset+1]=='f',"Float OSC typetag");
        float out=ByteBuffer.wrap(packet,packet.length-4,4)
            .order(ByteOrder.BIG_ENDIAN).getFloat();
        check(out==.8f,"IEEE754 big-endian OSC packet");
        check(AvatarEyeHeightOsc.packet(2f).length==packet.length,"Same packet length");
        System.out.println("PASS: eyeheight presets, boundaries, float32 wire format");
    }
}
