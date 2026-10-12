package club.thegamerclub.nodelab;

import android.os.SystemClock;
import java.io.ByteArrayOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;

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
        String value=validate(raw);
        long now=SystemClock.elapsedRealtime();
        if(now-lastSendAt<MIN_SEND_INTERVAL_MS)
            throw new IllegalStateException("Wait a moment before sending again.");
        byte[] data=packet(value,notificationSound);
        InetAddress loopback=InetAddress.getByAddress(LOOPBACK);
        try(DatagramSocket socket=new DatagramSocket()){
            DatagramPacket outgoing=new DatagramPacket(data,data.length,
                loopback,VRCHAT_OSC_PORT);
            socket.send(outgoing);
        }
        lastSendAt=SystemClock.elapsedRealtime();
    }
}
