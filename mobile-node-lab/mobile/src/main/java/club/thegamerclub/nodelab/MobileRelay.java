package club.thegamerclub.nodelab;

import android.content.Context;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** Only talks to an owner-issued Google Apps Script relay URL. */
public final class MobileRelay {
  private MobileRelay(){}
  public static JSONObject post(String endpoint,JSONObject body) throws Exception{
    URL url=new URL(endpoint);
    if(!"https".equalsIgnoreCase(url.getProtocol()) ||
      !"script.google.com".equalsIgnoreCase(url.getHost()) ||
      !url.getPath().matches("/macros/s/[^/]+/exec")){
      throw new SecurityException("Not a TGC Google Apps Script /exec deployment endpoint");
    }
    HttpURLConnection conn=(HttpURLConnection)url.openConnection();
    conn.setConnectTimeout(16000);
    conn.setReadTimeout(24000);
    conn.setInstanceFollowRedirects(true);
    conn.setRequestMethod("POST");
    conn.setDoOutput(true);
    conn.setRequestProperty("Content-Type","application/json; charset=utf-8");
    conn.setRequestProperty("Accept","application/json");
    byte[] bytes=body.toString().getBytes(StandardCharsets.UTF_8);
    try{
      try(OutputStream out=conn.getOutputStream()){out.write(bytes);}
      int code=conn.getResponseCode();
      if(code<200||code>=300)throw new IllegalStateException("Relay HTTP "+code);
      ByteArrayOutputStream output=new ByteArrayOutputStream();
      try(InputStream in=conn.getInputStream()){
        byte[] chunk=new byte[4096];int n;
        while((n=in.read(chunk))!=-1){
          if(output.size()+n>512000)throw new IllegalStateException("Relay response oversized");
          output.write(chunk,0,n);
        }
      }
      JSONObject json=new JSONObject(new String(output.toByteArray(),StandardCharsets.UTF_8));
      if(!json.optBoolean("ok",false)){
        String error=json.optString("error","Request rejected");
        if(error.length()>180)error=error.substring(0,180);
        throw new IllegalStateException(error);
      }
      return json;
    }finally{conn.disconnect();}
  }
  public static JSONObject authorized(MobileCredentials.Record record,String action) throws Exception{
    JSONObject body=new JSONObject();
    body.put("action",action);
    body.put("node_id",record.nodeId);
    body.put("node_key",record.nodeKey);
    body.put("client_type","mobile_companion");
    body.put("version","0.5.0-mobile");
    return body;
  }
}
