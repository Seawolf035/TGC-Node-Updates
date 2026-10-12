package club.thegamerclub.nodelab;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.LruCache;
import android.widget.ImageView;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Lightweight VRChat image loader with memory cache and bounded concurrency.
 * Only HTTPS VRChat-owned hosts are allowed. No personal auth or TGC secrets
 * are ever attached to image requests.
 */
public final class CompanionImages {
    private static final ExecutorService pool=Executors.newFixedThreadPool(3);
    private static final LruCache<String,Bitmap> cache=new LruCache<String,Bitmap>(12*1024){
        @Override protected int sizeOf(String key,Bitmap bitmap){
            return Math.max(1,bitmap.getByteCount()/1024);
        }
    };
    private CompanionImages(){}
    private static boolean approved(String raw){
        if(raw==null||raw.isEmpty())return false;
        try{
            URL url=new URL(raw);
            if(!"https".equalsIgnoreCase(url.getProtocol()))return false;
            String host=url.getHost().toLowerCase(Locale.US);
            return host.equals("vrchat.cloud")||host.endsWith(".vrchat.cloud")||
                host.equals("vrcimg.com")||host.endsWith(".vrcimg.com")||
                host.equals("vrchat.com")||host.endsWith(".vrchat.com");
        }catch(Exception ignored){return false;}
    }
    public static void load(ImageView into,String url){
        if(into==null)return;
        into.setTag(url==null?"":url);
        into.setImageDrawable(null);
        if(!approved(url))return;
        Bitmap ready;
        synchronized(cache){ready=cache.get(url);}
        if(ready!=null){into.setImageBitmap(ready);return;}
        WeakReference<ImageView> view=new WeakReference<>(into);
        pool.execute(()->{
            HttpURLConnection c=null;
            try{
                c=(HttpURLConnection)new URL(url).openConnection();
                c.setConnectTimeout(7500);
                c.setReadTimeout(10000);
                c.setInstanceFollowRedirects(false);
                c.setRequestProperty("Accept","image/*");
                if(c.getResponseCode()!=200)return;
                String contentType=c.getContentType();
                if(contentType!=null && !contentType.toLowerCase(Locale.US).startsWith("image/"))return;
                ByteArrayOutputStream bytes=new ByteArrayOutputStream();
                try(InputStream in=c.getInputStream()){
                    byte[] buff=new byte[8192];int n;
                    while((n=in.read(buff))!=-1){
                        if(bytes.size()+n>5_000_000)return;
                        bytes.write(buff,0,n);
                    }
                }
                byte[] data=bytes.toByteArray();
                if(data.length==0)return;
                BitmapFactory.Options bounds=new BitmapFactory.Options();
                bounds.inJustDecodeBounds=true;
                BitmapFactory.decodeByteArray(data,0,data.length,bounds);
                int sample=1;
                while(bounds.outWidth/sample>1100||bounds.outHeight/sample>1100)sample*=2;
                BitmapFactory.Options options=new BitmapFactory.Options();
                options.inSampleSize=sample;
                Bitmap bitmap=BitmapFactory.decodeByteArray(data,0,data.length,options);
                if(bitmap==null)return;
                synchronized(cache){cache.put(url,bitmap);}
                ImageView target=view.get();
                if(target!=null)target.post(()->{
                    if(url.equals(target.getTag()))target.setImageBitmap(bitmap);
                });
            }catch(Exception ignored){}
            finally{if(c!=null)c.disconnect();}
        });
    }
    public static String preferred(org.json.JSONObject data,String... fields){
        for(String field:fields){
            String url=data.optString(field,"");
            if(approved(url))return url;
        }
        return "";
    }
}
