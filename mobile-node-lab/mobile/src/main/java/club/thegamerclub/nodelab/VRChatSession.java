package club.thegamerclub.nodelab;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import org.json.JSONObject;
import java.security.KeyStore;
import java.nio.charset.StandardCharsets;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * VRChat session cookies, encrypted at rest using Android Keystore.
 * Kept separate from TGC deployment credentials and never sent to TGC.
 * Password is never persisted.
 */
public final class VRChatSession {
    private static final String ALIAS="tgc_staff_vrchat_session_v1";
    private static final String PREF="tgc_staff_vrchat_session";
    private VRChatSession(){}

    private static SecretKey getKey() throws Exception{
        KeyStore ks=KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        if(ks.containsAlias(ALIAS))return (SecretKey)ks.getKey(ALIAS,null);
        KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(ALIAS,
            KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build());
        return generator.generateKey();
    }
    public static synchronized void save(Context context,JSONObject data)throws Exception{
        JSONObject sanitized=new JSONObject();
        for(String field:new String[]{"auth","twoFactorAuth","userId","displayName","needs2fa"}){
            if(field.equals("needs2fa"))sanitized.put(field,data.optBoolean(field,false));
            else sanitized.put(field,data.optString(field,""));
        }
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE,getKey());
        byte[] encrypted=cipher.doFinal(sanitized.toString().getBytes(StandardCharsets.UTF_8));
        context.getSharedPreferences(PREF,0).edit()
            .putString("iv",Base64.encodeToString(cipher.getIV(),Base64.NO_WRAP))
            .putString("data",Base64.encodeToString(encrypted,Base64.NO_WRAP))
            .apply();
    }
    public static synchronized JSONObject load(Context context)throws Exception{
        SharedPreferences pref=context.getSharedPreferences(PREF,0);
        String data=pref.getString("data",""),iv=pref.getString("iv","");
        if(data.isEmpty()||iv.isEmpty())return new JSONObject();
        Cipher c=Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE,getKey(),new GCMParameterSpec(128,Base64.decode(iv,Base64.NO_WRAP)));
        return new JSONObject(new String(c.doFinal(Base64.decode(data,Base64.NO_WRAP)),StandardCharsets.UTF_8));
    }
    public static synchronized void clear(Context context){
        context.getSharedPreferences(PREF,0).edit().clear().apply();
    }
}
