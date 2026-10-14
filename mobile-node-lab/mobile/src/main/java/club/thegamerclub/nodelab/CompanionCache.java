package club.thegamerclub.nodelab;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.AtomicFile;
import android.util.Base64;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.MessageDigest;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Local-first display cache, encrypted with the Android Keystore.
 * Never used as authorization or to justify a remote VRChat mutation.
 * Data is scoped to the currently authenticated personal VRChat account,
 * stays only in no-backup private app storage, and contains no cookies/tokens.
 */
public final class CompanionCache {
    private static final String KEY_ALIAS="tgc_companion_cache_v1";
    private static final int MAX_BYTES=3_000_000;
    private static final int MAX_SEALED_BYTES=MAX_BYTES*2+1024;
    private CompanionCache(){}

    public static final class Record {
        public final JSONObject body;
        public final long updatedAt;
        Record(JSONObject body,long updatedAt){
            this.body=body;this.updatedAt=updatedAt;
        }
        public boolean fresh(long maxAgeMs){
            long age=System.currentTimeMillis()-updatedAt;
            return updatedAt>0&&age>=0&&age<maxAgeMs;
        }
    }
    private static String account(Context ctx)throws Exception{
        JSONObject session=VRChatSession.load(ctx);
        String id=session.optString("userId","");
        if(!id.matches("usr_[0-9a-fA-F-]{36}") ||
           session.optString("auth","").isEmpty() ||
           session.optBoolean("needs2fa",false))
            throw new SecurityException("Sign in to your personal VRChat account first");
        return id;
    }
    private static String hash(String text)throws Exception{
        byte[] digest=MessageDigest.getInstance("SHA-256").digest(
            text.getBytes(StandardCharsets.UTF_8));
        StringBuilder hex=new StringBuilder();
        for(byte b:digest)hex.append(String.format(java.util.Locale.US,"%02x",b&255));
        return hex.toString();
    }
    private static File file(Context ctx,String kind)throws Exception{
        if(kind==null||!kind.matches("[a-zA-Z0-9_:-]{1,115}"))
            throw new IllegalArgumentException("Invalid local cache key");
        return new File(ctx.getNoBackupFilesDir(),"vrc_cache_"+hash(account(ctx))+
            "_"+hash(kind).substring(0,24)+".dat");
    }
    private static SecretKey key()throws Exception{
        KeyStore store=KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        if(store.containsAlias(KEY_ALIAS))return (SecretKey)store.getKey(KEY_ALIAS,null);
        KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256).build());
        return generator.generateKey();
    }
    public static synchronized Record read(Context ctx,String kind){
        try{
            File destination=file(ctx,kind);
            if(!destination.isFile()||destination.length()>MAX_SEALED_BYTES)return null;
            AtomicFile atom=new AtomicFile(destination);
            byte[] bytes;
            try(FileInputStream input=atom.openRead();
                ByteArrayOutputStream output=new ByteArrayOutputStream()){
                byte[] buffer=new byte[8192];int n;
                while((n=input.read(buffer))!=-1){
                    if(output.size()+n>MAX_SEALED_BYTES)return null;
                    output.write(buffer,0,n);
                }
                bytes=output.toByteArray();
            }
            JSONObject encrypted=new JSONObject(new String(bytes,StandardCharsets.UTF_8));
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,
                Base64.decode(encrypted.getString("iv"),Base64.NO_WRAP)));
            byte[] opened=cipher.doFinal(Base64.decode(
                encrypted.getString("data"),Base64.NO_WRAP));
            if(opened.length>MAX_BYTES)return null;
            JSONObject payload=new JSONObject(new String(opened,StandardCharsets.UTF_8));
            if(!account(ctx).equals(payload.optString("account","")) ||
               !kind.equals(payload.optString("kind","")))return null;
            JSONObject body=payload.optJSONObject("body");
            return body==null?null:new Record(body,payload.optLong("updatedAt",0));
        }catch(Exception ignored){return null;}
    }
    /** Defensively remove auth-bearing fields even if an API adds them later. */
    private static Object safeValue(Object source)throws Exception{
        if(source instanceof JSONObject){
            JSONObject src=(JSONObject)source,dst=new JSONObject();
            java.util.Iterator<String> keys=src.keys();
            while(keys.hasNext()){
                String name=keys.next(),normalized=name.toLowerCase(java.util.Locale.US);
                if(normalized.equals("auth")||normalized.equals("authtoken")||
                   normalized.equals("twofactorauth")||
                   normalized.equals("password")||normalized.equals("cookie")||
                   normalized.equals("friendkey")||normalized.equals("email")||
                   normalized.equals("secrettoken")||
                   normalized.equals("recoverycode")||
                   normalized.endsWith("sessiontoken")||
                   normalized.endsWith("accesstoken")||
                   normalized.endsWith("refreshtoken"))continue;
                Object value=src.opt(name);
                dst.put(name,safeValue(value));
            }
            return dst;
        }
        if(source instanceof JSONArray){
            JSONArray src=(JSONArray)source,dst=new JSONArray();
            for(int i=0;i<src.length();i++)dst.put(safeValue(src.opt(i)));
            return dst;
        }
        return source==null?JSONObject.NULL:source;
    }
    public static synchronized void write(Context ctx,String kind,JSONObject body)throws Exception{
        if(body==null)throw new IllegalArgumentException("Missing cache data");
        // JSON source data never includes a VRChat auth cookie. Only caller-supplied
        // public-facing display data is persisted; fail closed on oversized payloads.
        String serialized=((JSONObject)safeValue(body)).toString();
        if(serialized.length()>MAX_BYTES)throw new IllegalArgumentException("Cache entry too large");
        JSONObject payload=new JSONObject()
            .put("account",account(ctx)).put("kind",kind)
            .put("updatedAt",System.currentTimeMillis())
            .put("body",new JSONObject(serialized));
        byte[] plain=payload.toString().getBytes(StandardCharsets.UTF_8);
        if(plain.length>MAX_BYTES)throw new IllegalArgumentException("Cache entry too large");
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE,key());
        byte[] ciphertext=cipher.doFinal(plain);
        JSONObject sealed=new JSONObject()
            .put("iv",Base64.encodeToString(cipher.getIV(),Base64.NO_WRAP))
            .put("data",Base64.encodeToString(ciphertext,Base64.NO_WRAP));
        AtomicFile atom=new AtomicFile(file(ctx,kind));
        FileOutputStream output=atom.startWrite();
        try{
            output.write(sealed.toString().getBytes(StandardCharsets.UTF_8));
            atom.finishWrite(output);
        }catch(Exception e){atom.failWrite(output);throw e;}
    }
    public static JSONArray cachedArray(Context ctx,String kind){
        Record r=read(ctx,kind);
        return r==null?null:r.body.optJSONArray("items");
    }
    public static JSONObject cachedObject(Context ctx,String kind){
        Record r=read(ctx,kind);
        return r==null?null:r.body.optJSONObject("item");
    }
    public static void saveArray(Context ctx,String kind,JSONArray array)throws Exception{
        write(ctx,kind,new JSONObject().put("items",array));
    }
    public static void saveObject(Context ctx,String kind,JSONObject object)throws Exception{
        write(ctx,kind,new JSONObject().put("item",object));
    }
}
