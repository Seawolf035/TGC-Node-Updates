package club.thegamerclub.nodelab;

import android.content.Context;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Local acceptance of a versioned, proprietary staff end-user agreement. */
public final class CompanionLicense {
    public static final String VERSION="2026-10-13.2";
    private static final String PREFS="tgc_companion_license_acceptance";
    private CompanionLicense(){}

    public static boolean accepted(Context context){
        return VERSION.equals(context.getSharedPreferences(PREFS,0)
            .getString("accepted_version",""));
    }
    public static void accept(Context context){
        context.getSharedPreferences(PREFS,0).edit()
            .putString("accepted_version",VERSION)
            .putLong("accepted_at",System.currentTimeMillis()).apply();
    }
    public static String text(Context context){
        try(InputStream source=context.getAssets().open("tgc_companion_eula.txt");
            ByteArrayOutputStream buffer=new ByteArrayOutputStream()){
            byte[] chunk=new byte[4096];
            int count;
            while((count=source.read(chunk))!=-1)buffer.write(chunk,0,count);
            return new String(buffer.toByteArray(),StandardCharsets.UTF_8);
        }catch(Exception e){
            // Fail closed: an unreadable agreement must not be accepted.
            throw new IllegalStateException(
                "The user agreement couldn't be loaded. Please reinstall the official app.",e);
        }
    }
}
