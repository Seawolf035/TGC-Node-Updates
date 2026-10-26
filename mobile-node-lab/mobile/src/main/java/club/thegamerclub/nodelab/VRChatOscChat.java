package club.thegamerclub.nodelab;

import android.os.SystemClock;
import java.io.ByteArrayOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Personal VRChat chatbox text, sent only to the VRChat client on THIS device.
 * This is a local UDP OSC command, not the VRChat web API, TGC Relay, or a
 * Shizuku command. VRChat needs OSC enabled in its own Action Menu.
 *
 * OSC is UDP and provides no delivery acknowledgment: packet transmission
 * must never be presented as proof the message appeared in-game.
 */
public final class VRChatOscChat {
    public static final int MAX_CHARACTERS=144;
    public static final int MAX_LINES=9;
    private static final long MIN_SEND_INTERVAL_MS=1800L;
    private static final int VRCHAT_OSC_PORT=9000;
    private static final byte[] LOOPBACK={(byte)127,0,0,1};
    private static long lastSendAt=-MIN_SEND_INTERVAL_MS;
    private static long lastManualAt=-60000L;
    private static long lastTypingAt=0L;
    private static Boolean lastTyping=null;
    private VRChatOscChat(){}

    public static String validate(String raw){
        if(raw==null||raw.trim().isEmpty())
            throw new IllegalArgumentException("Enter a chat message first.");
        if(raw.indexOf('\0')>=0)
            throw new IllegalArgumentException("Chat text cannot contain null characters.");
        int count=raw.codePointCount(0,raw.length());
        if(count>MAX_CHARACTERS)
            throw new IllegalArgumentException("VRChat chat is limited to 144 characters.");
        int lines=1;
        for(int i=0;i<raw.length();i++){
            char c=raw.charAt(i);
            if(c=='\n')lines++;
            if(Character.isISOControl(c)&&c!='\n')
                throw new IllegalArgumentException("Remove unsupported control characters.");
        }
        if(lines>MAX_LINES)
            throw new IllegalArgumentException("VRChat chat supports at most 9 lines.");
        return raw;
    }

    /**
     * Serialize an OSC string using UTF-8, NUL terminator and 4-byte padding.
     * The boolean "send immediately" argument is encoded as the OSC T type
     * tag (there is no boolean payload). Optional notification-SFX is T/F.
     */
    private static void oscString(ByteArrayOutputStream out,String value){
        byte[] bytes=value.getBytes(StandardCharsets.UTF_8);
        out.write(bytes,0,bytes.length);
        out.write(0);
        while(out.size()%4!=0)out.write(0);
    }
    static byte[] packet(String value,boolean notificationSound){
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        oscString(out,"/chatbox/input");
        oscString(out,notificationSound?",sTT":",sTF");
        oscString(out,value);
        return out.toByteArray();
    }

    /**
     * Returns only after the local UDP packet has been sent. It cannot prove
     * VRChat is running, has OSC enabled, or displayed the chatbox text.
     */
    public static synchronized void send(String raw,boolean notificationSound)
        throws Exception {
        sendChat(raw,notificationSound,true);
    }
    /** Live status suppresses sound; manual messages take priority. */
    public static synchronized void sendLive(String raw)throws Exception{
        sendChat(raw,false,false);
    }
    public static synchronized long sinceManualChatMs(){
        return SystemClock.elapsedRealtime()-lastManualAt;
    }
    private static void sendChat(String raw,boolean notificationSound,
                                 boolean manual)throws Exception{
        String value=validate(raw);
        long now=SystemClock.elapsedRealtime();
        if(now-lastSendAt<MIN_SEND_INTERVAL_MS)
            throw new IllegalStateException("Wait a moment before sending again.");
        transmit(packet(value,notificationSound));
        lastSendAt=SystemClock.elapsedRealtime();
        if(manual)lastManualAt=lastSendAt;
    }

    /**
     * VRChat's documented /input/Voice action: integer 1 = pressed, 0 =
     * released. When toggle voice is disabled this is the PTT input.
     * The Android client may ignore input-controller OSC; UDP gives no
     * indication of whether the microphone is actually transmitting.
     */
    static byte[] voiceInputPacket(boolean pressed) throws IOException{
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        oscString(out,"/input/Voice");
        oscString(out,",i");
        new DataOutputStream(out).writeInt(pressed?1:0);
        return out.toByteArray();
    }
    public static synchronized void sendVoiceInput(boolean pressed)throws Exception{
        transmit(voiceInputPacket(pressed));
    }

    /** OSC /chatbox/typing, a true/false type tag without payload bytes. */
    public static synchronized void sendTyping(boolean typing)throws Exception{
        long now=SystemClock.elapsedRealtime();
        if(lastTyping!=null&&lastTyping.booleanValue()==typing&&
           now-lastTypingAt<2500L)return;
        // Turning typing OFF is always allowed: a fast dismiss must not
        // leave an OSC typing bubble stuck until VRChat times it out.
        if(typing&&now-lastTypingAt<200L)
            throw new IllegalStateException("Please wait before updating typing again.");
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        oscString(out,"/chatbox/typing");
        oscString(out,typing?",T":",F");
        transmit(out.toByteArray());
        lastTyping=typing;lastTypingAt=SystemClock.elapsedRealtime();
    }

    /**
     * Explicit, named avatar parameter writes. Only boolean, int, float.
     * OSC has no receipt signal and unsupported avatar parameters do nothing.
     */
    public static void sendParameter(String name,String type,String raw)
        throws Exception {
        // A parameter can be a nested path (Go/IsFeral), or the full
        // /avatar/parameters/Go/IsFeral address. Do not double-prefix.
        final String address=AvatarOscParameterPath.address(name);
        if(type==null)throw new IllegalArgumentException("Choose a parameter type.");
        ByteArrayOutputStream buffer=new ByteArrayOutputStream();
        oscString(buffer,address);
        switch(type){
            case "bool":{
                if(!"true".equals(raw)&&!"false".equals(raw))
                    throw new IllegalArgumentException("Boolean values must be true or false.");
                oscString(buffer,"true".equals(raw)?",T":",F");
                break;
            }
            case "int":{
                int number;
                try{number=Integer.parseInt(raw.trim());}
                catch(Exception e){throw new IllegalArgumentException("Enter a whole number (0–255).");}
                if(number<0||number>255)
                    throw new IllegalArgumentException("VRChat avatar int parameters range from 0 to 255.");
                oscString(buffer,",i");
                new DataOutputStream(buffer).writeInt(number);
                break;
            }
            case "float":{
                float number;
                try{number=Float.parseFloat(raw.trim());}
                catch(Exception e){throw new IllegalArgumentException("Enter a number from -1 to 1.");}
                if(!Float.isFinite(number)||number< -1f||number>1f)
                    throw new IllegalArgumentException("Float parameter must be between -1 and 1.");
                oscString(buffer,",f");
                new DataOutputStream(buffer).writeInt(Float.floatToIntBits(number));
                break;
            }
            default:throw new IllegalArgumentException("Unsupported parameter type.");
        }
        transmit(buffer.toByteArray());
    }

    /**
     * Sends the documented global VRChat OSC eye-height setting, not a
     * custom /avatar/parameters float (those are -1..1).
     * One explicit manual send; never auto-apply on launch.
     */
    public static synchronized void sendEyeHeight(float metres)throws Exception{
        transmit(AvatarEyeHeightOsc.packet(metres));
    }

    private static void transmit(byte[] data)throws Exception{
        InetAddress loopback=InetAddress.getByAddress(LOOPBACK);
        try(DatagramSocket socket=new DatagramSocket()){
            DatagramPacket outgoing=new DatagramPacket(data,data.length,
                loopback,VRCHAT_OSC_PORT);
            socket.send(outgoing);
        }
    }
}
