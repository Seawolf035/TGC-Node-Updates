package club.thegamerclub.nodelab;

import android.content.Context;
import android.util.Base64;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

/** Personal-account VRChat API client: sessions NEVER go through the TGC relay. */
public final class VRChatApi {
    private static final String API="https://api.vrchat.cloud/api/1";
    private static final String AGENT="TGC-Staff-Companion/0.6.0 (Android VRChat companion)";
    private VRChatApi(){}

    private static final class Response {
        final int status; final String text; final JSONObject cookies;
        Response(int status,String text,JSONObject cookies){this.status=status;this.text=text;this.cookies=cookies;}
        JSONObject object()throws Exception{return new JSONObject(text);}
        JSONArray array()throws Exception{return new JSONArray(text);}
    }
    private static Response request(String method,String path,String cookie,String authorization,JSONObject payload)throws Exception{
        if(!path.startsWith("/")||path.contains("..")||path.contains("://"))
            throw new SecurityException("Invalid VRChat API path");
        HttpURLConnection conn=(HttpURLConnection)new URL(API+path).openConnection();
        conn.setConnectTimeout(12000);conn.setReadTimeout(17000);
        conn.setInstanceFollowRedirects(false);
        conn.setRequestMethod(method);
        conn.setRequestProperty("User-Agent",AGENT);
        conn.setRequestProperty("Accept","application/json");
        if(cookie!=null&&!cookie.isEmpty())conn.setRequestProperty("Cookie",cookie);
        if(authorization!=null&&!authorization.isEmpty())conn.setRequestProperty("Authorization",authorization);
        if(payload!=null){
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type","application/json");
            byte[] body=payload.toString().getBytes(StandardCharsets.UTF_8);
            try(OutputStream out=conn.getOutputStream()){out.write(body);}
        }
        try{
            int status=conn.getResponseCode();
            JSONObject cookies=new JSONObject();
            for(String hdr:conn.getHeaderFields().keySet()){
                if(hdr==null||!"Set-Cookie".equalsIgnoreCase(hdr))continue;
                List<String> values=conn.getHeaderFields().get(hdr);
                if(values==null)continue;
                for(String value:values){
                    String first=value.split(";",2)[0];
                    int equal=first.indexOf('=');
                    if(equal<=0)continue;
                    String key=first.substring(0,equal).trim();
                    if(key.equalsIgnoreCase("auth"))cookies.put("auth",first.substring(equal+1));
                    if(key.equalsIgnoreCase("twoFactorAuth"))cookies.put("twoFactorAuth",first.substring(equal+1));
                }
            }
            InputStream input=status>=200&&status<400?conn.getInputStream():conn.getErrorStream();
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();
            if(input!=null){
                try(InputStream stream=input){
                    byte[] buf=new byte[4096];int n;
                    while((n=stream.read(buf))!=-1){
                        if(bytes.size()+n>1024*1024)throw new SecurityException("VRChat response too large");
                        bytes.write(buf,0,n);
                    }
                }
            }
            String text=new String(bytes.toByteArray(),StandardCharsets.UTF_8);
            if(status==429)throw new IllegalStateException("VRChat rate limit reached. Wait before retrying.");
            if(status==401 && (authorization==null||authorization.isEmpty()))
                throw new SecurityException("VRChat session expired. Sign in again.");
            if(status<200||status>=300)
                throw new IllegalStateException("VRChat returned HTTP "+status+" (no changes made).");
            return new Response(status,text,cookies);
        }finally{conn.disconnect();}
    }
    private static String cookie(JSONObject session){
        StringBuilder s=new StringBuilder();
        for(String key:new String[]{"auth","twoFactorAuth"}){
            String value=session.optString(key,"");
            if(!value.isEmpty()){
                if(s.length()>0)s.append("; ");
                s.append(key).append("=").append(value);
            }
        }
        return s.toString();
    }
    private static void mergeCookies(JSONObject session,JSONObject from)throws Exception{
        for(String key:new String[]{"auth","twoFactorAuth"}){
            String value=from.optString(key,"");
            if(!value.isEmpty())session.put(key,value);
        }
    }
    private static void updateProfile(JSONObject session,JSONObject user)throws Exception{
        if(!user.optString("id","").matches("usr_[0-9a-fA-F-]{36}"))
            throw new IllegalStateException("VRChat session did not return a valid user");
        session.put("userId",user.getString("id"));
        session.put("displayName",user.optString("displayName","VRChat user"));
        session.put("needs2fa",false);
    }
    public static JSONObject login(Context context,String username,String password)throws Exception{
        if(username.trim().isEmpty()||password.isEmpty())throw new IllegalArgumentException("Username and password required");
        String basic=Base64.encodeToString((username+":"+password).getBytes(StandardCharsets.UTF_8),Base64.NO_WRAP);
        // Username/password only used for this one HTTPS call; never persisted.
        Response response=request("GET","/auth/user",null,"Basic "+basic,null);
        JSONObject session=new JSONObject();
        mergeCookies(session,response.cookies);
        JSONObject result=response.object();
        JSONArray requires=result.optJSONArray("requiresTwoFactorAuth");
        if(requires!=null && requires.length()>0){
            if(session.optString("auth","").isEmpty())throw new SecurityException("VRChat did not return a session cookie for 2FA");
            session.put("needs2fa",true);
            VRChatSession.save(context,session);
            JSONObject next=new JSONObject();
            next.put("requires2fa",true).put("methods",requires);
            return next;
        }
        updateProfile(session,result);
        if(session.optString("auth","").isEmpty())throw new SecurityException("VRChat returned no auth cookie");
        VRChatSession.save(context,session);
        return new JSONObject().put("requires2fa",false).put("displayName",session.getString("displayName"));
    }
    public static JSONObject verify2fa(Context context,String code,String mode)throws Exception{
        if(!mode.matches("totp|emailotp|otp"))throw new IllegalArgumentException("Unknown verification method");
        if(!code.matches("[A-Za-z0-9-]{4,32}"))throw new IllegalArgumentException("Invalid 2FA code");
        JSONObject session=VRChatSession.load(context);
        if(session.optString("auth","").isEmpty() || !session.optBoolean("needs2fa"))
            throw new SecurityException("Start VRChat login first");
        JSONObject input=new JSONObject().put("code",code.trim());
        Response verified=request("POST","/auth/twofactorauth/"+mode+"/verify",cookie(session),null,input);
        JSONObject result=verified.object();
        if(!result.optBoolean("verified",false))throw new SecurityException("VRChat did not verify the code");
        mergeCookies(session,verified.cookies);
        Response me=request("GET","/auth/user",cookie(session),null,null);
        mergeCookies(session,me.cookies);
        updateProfile(session,me.object());
        VRChatSession.save(context,session);
        return new JSONObject().put("displayName",session.getString("displayName"));
    }
    public static JSONObject currentUser(Context context)throws Exception{
        JSONObject session=validSession(context);
        Response response=request("GET","/auth/user",cookie(session),null,null);
        mergeCookies(session,response.cookies);
        JSONObject result=response.object();
        updateProfile(session,result);
        VRChatSession.save(context,session);
        return result;
    }
    private static JSONObject validSession(Context context)throws Exception{
        JSONObject session=VRChatSession.load(context);
        if(session.optString("auth","").isEmpty()||session.optBoolean("needs2fa"))
            throw new SecurityException("Sign in to your personal VRChat account first");
        return session;
    }
    public static JSONArray friends(Context context,boolean offline)throws Exception{
        JSONObject session=validSession(context);
        return request("GET","/auth/user/friends?n=80&offset=0&offline="+offline,
          cookie(session),null,null).array();
    }
    public static JSONArray worldFavorites(Context context)throws Exception{
        JSONObject session=validSession(context);
        return request("GET","/worlds/favorites?n=60&offset=0",cookie(session),null,null).array();
    }
    public static JSONArray groups(Context context)throws Exception{
        JSONObject session=validSession(context);
        String user=session.optString("userId","");
        if(!user.matches("usr_[0-9a-fA-F-]{36}"))throw new SecurityException("Personal VRChat session needs verification");
        return request("GET","/users/"+user+"/groups",cookie(session),null,null).array();
    }
    public static JSONArray recentWorlds(Context context)throws Exception{
        JSONObject session=validSession(context);
        return request("GET","/worlds/recent?n=40&offset=0",cookie(session),null,null).array();
    }
    public static void logout(Context context){VRChatSession.clear(context);}
}
