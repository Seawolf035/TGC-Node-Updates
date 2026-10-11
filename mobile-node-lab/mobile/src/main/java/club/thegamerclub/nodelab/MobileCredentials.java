package club.thegamerclub.nodelab;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.SecureRandom;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Stores staff deployment credentials encrypted with Android Keystore AES-GCM. */
public final class MobileCredentials {
  private static final String PREF="tgc_mobile_deployment";
  private static final String KEY="tgc_mobile_node_key_v1";
  private MobileCredentials(){}

  public static final class Record {
    public final String url,nodeId,nodeKey,discordId,nodeName;
    Record(JSONObject json){
      url=json.optString("url");nodeId=json.optString("node_id");
      nodeKey=json.optString("node_key");discordId=json.optString("discord_id");
      nodeName=json.optString("node_name");
    }
    boolean valid(){
      return url.startsWith("https://script.google.com/macros/s/") &&
        nodeId.matches("node_[0-9a-fA-F]{32}") && nodeKey.startsWith("tgc_node_");
    }
  }
  private static SecretKey secret() throws Exception {
    KeyStore ks=KeyStore.getInstance("AndroidKeyStore");
    ks.load(null);
    if(ks.containsAlias(KEY))return (SecretKey)ks.getKey(KEY,null);
    KeyGenerator gen=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
    gen.init(new KeyGenParameterSpec.Builder(KEY,
      KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
      .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
      .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
      .setKeySize(256).build());
    return gen.generateKey();
  }
  public static synchronized void save(Context ctx, String url,JSONObject data) throws Exception{
    JSONObject stored=new JSONObject();
    stored.put("url",url);
    for(String field:new String[]{"node_id","node_key","discord_id","node_name"})
      stored.put(field,data.optString(field));
    Record record=new Record(stored);
    if(!record.valid())throw new IllegalArgumentException("Invalid provisioning response");
    Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
    cipher.init(Cipher.ENCRYPT_MODE,secret());
    byte[] iv=cipher.getIV();
    byte[] encrypted=cipher.doFinal(stored.toString().getBytes(StandardCharsets.UTF_8));
    ctx.getSharedPreferences(PREF,0).edit()
      .putString("iv",Base64.encodeToString(iv,Base64.NO_WRAP))
      .putString("data",Base64.encodeToString(encrypted,Base64.NO_WRAP)).apply();
  }
  public static synchronized Record load(Context ctx) throws Exception{
    SharedPreferences p=ctx.getSharedPreferences(PREF,0);
    String ciphertext=p.getString("data","");
    String iv=p.getString("iv","");
    if(ciphertext.isEmpty()||iv.isEmpty())return null;
    Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
    cipher.init(Cipher.DECRYPT_MODE,secret(),
      new GCMParameterSpec(128,Base64.decode(iv,Base64.NO_WRAP)));
    JSONObject json=new JSONObject(new String(cipher.doFinal(Base64.decode(ciphertext,Base64.NO_WRAP)),
      StandardCharsets.UTF_8));
    Record r=new Record(json);
    if(!r.valid())throw new SecurityException("Stored deployment data invalid");
    return r;
  }
  public static String installId(Context context){
    SharedPreferences p=context.getSharedPreferences(PREF,0);
    String value=p.getString("install_id","");
    if(!value.isEmpty())return value;
    byte[] random=new byte[20];new SecureRandom().nextBytes(random);
    value="android_"+Base64.encodeToString(random,Base64.URL_SAFE|Base64.NO_WRAP|Base64.NO_PADDING);
    p.edit().putString("install_id",value).apply();
    return value;
  }
  public static synchronized void clear(Context context){
    context.getSharedPreferences(PREF,0).edit()
      .remove("data").remove("iv").apply();
  }
}
