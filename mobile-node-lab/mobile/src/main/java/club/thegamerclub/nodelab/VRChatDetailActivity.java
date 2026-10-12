package club.thegamerclub.nodelab;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Native VRChat user/world/group profile. No ACTION_VIEW or external web browser.
 * Read-only endpoints are loaded with the staff member's separate VRChat session.
 */
public final class VRChatDetailActivity extends StaffProtectedActivity {
    private static final int BG=CompanionStyle.BG,SURFACE=CompanionStyle.SURFACE,TEXT=CompanionStyle.TEXT,
        MUTED=CompanionStyle.MUTED,ACCENT=CompanionStyle.ACCENT;
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private LinearLayout content;
    private TextView status;
    private String kind,id;
    private long lastRefresh=0;
    private int dp(float value){return Math.round(getResources().getDisplayMetrics().density*value);}
    private TextView text(String value,int size,int color,boolean bold){
        TextView t=new TextView(this);t.setText(value);t.setTextColor(color);t.setTextSize(size);
        t.setPadding(0,dp(4),0,dp(5));
        if(bold)t.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
        return t;
    }
    private void spacer(int height){content.addView(new View(this),new LinearLayout.LayoutParams(1,dp(height)));}
    private void row(String label,String value){
        if(value==null||value.trim().isEmpty()||"null".equals(value))return;
        LinearLayout line=new LinearLayout(this);line.setOrientation(LinearLayout.VERTICAL);
        line.setPadding(dp(16),dp(13),dp(16),dp(13));
        line.setBackground(CompanionStyle.shape(this,SURFACE,14,CompanionStyle.BORDER));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(6);
        content.addView(line,p);
        line.addView(text(label,12,ACCENT,true));
        TextView text=text(value,14,TEXT,false);text.setTextIsSelectable(true);line.addView(text);
    }
    private void navigate(String title,String description,String targetKind,String targetId){
        if(!valid(targetKind,targetId))return;
        LinearLayout item=new LinearLayout(this);
        item.setPadding(dp(15),dp(12),dp(15),dp(12));item.setOrientation(LinearLayout.VERTICAL);
        item.setBackground(CompanionStyle.shape(this,CompanionStyle.RAISED,14,CompanionStyle.BORDER));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(8);
        content.addView(item,p);
        item.addView(text(title+"  →",15,TEXT,true));
        if(description!=null&&!description.isEmpty())item.addView(text(description,12,MUTED,false));
        item.setOnClickListener(v->open(targetKind,targetId));
    }
    private void open(String type,String value){
        if(!StaffGate.unlocked(this)){finish();return;}
        startActivity(new Intent(this,VRChatDetailActivity.class)
            .putExtra("kind",type).putExtra("id",value));
    }
    private static boolean valid(String type,String value){
        String prefix="user".equals(type)?"usr_":"world".equals(type)?"wrld_":"group".equals(type)?"grp_":"";
        return !prefix.isEmpty()&&value!=null&&value.matches(prefix+"[0-9a-fA-F-]{36}");
    }
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);
        if(!StaffGate.unlocked(this)){finish();return;}
        kind=getIntent().getStringExtra("kind");
        id=getIntent().getStringExtra("id");
        if(!valid(kind,id)){finish();return;}
        render();load(false);
    }
    @Override protected void onResume(){
        super.onResume();
        if(!StaffGate.unlocked(this))finish();
    }
    private void render(){
        getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(BG);
        content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(17),dp(17),dp(17),dp(28));
        scroll.addView(content);setContentView(scroll);
        CompanionStyle.safeArea(this,scroll);
        Button back=CompanionStyle.button(this,"← Back",false);
        content.addView(back,new LinearLayout.LayoutParams(dp(110),dp(44)));
        back.setOnClickListener(v->finish());
        spacer(20);
        TextView kicker=text(kind.equals("user")?"PLAYER":kind.equals("world")?"WORLD":"GROUP",12,ACCENT,true);
        kicker.setLetterSpacing(.15f);
        content.addView(kicker);
        content.addView(text("Details",27,TEXT,true));
        spacer(7);
        status=text("Loading...",13,MUTED,false);
        content.addView(status);
    }
    private void load(boolean force){
        if(!StaffGate.unlocked(this)){finish();return;}
        if(force && System.currentTimeMillis()-lastRefresh<45_000){
            status.setText("Please wait at least 45 seconds between refreshes.");return;
        }
        lastRefresh=System.currentTimeMillis();
        io.execute(()->{
            try{
                JSONObject item="user".equals(kind)?VRChatApi.getUser(this,id):
                    "world".equals(kind)?VRChatApi.getWorld(this,id):VRChatApi.getGroup(this,id);
                runOnUiThread(()->{
                    if(!StaffGate.unlocked(this)){finish();return;}
                    render();
                    showDetails(item);
                });
            }catch(Exception ex){
                String msg=ex.getMessage()==null?ex.getClass().getSimpleName():ex.getMessage();
                if(msg.length()>180)msg=msg.substring(0,180);
                final String error=msg;
                runOnUiThread(()->status.setText("Unable to fetch this "+kind+": "+error+
                    ". The item may be private or your account may lack access."));
            }
        });
    }
    private void sectionTitle(String title){spacer(18);content.addView(text(title,24,TEXT,true));spacer(15);}
    private static String arr(JSONArray data){
        if(data==null||data.length()==0)return "";
        StringBuilder s=new StringBuilder();
        for(int i=0;i<Math.min(data.length(),14);i++){
            if(i>0)s.append(" · ");
            s.append(data.optString(i,""));
        }
        return s.toString();
    }
    private void showDetails(JSONObject item){
        status.setText("Details are shown based on your account's access.");
        String title=item.optString("displayName",item.optString("name","Unknown"));
        sectionTitle(title);
        String url="";
        if(kind.equals("user"))url=item.optString("profilePicOverride",
          item.optString("currentAvatarThumbnailImageUrl",item.optString("userIcon","")));
        if(kind.equals("world"))url=item.optString("thumbnailImageUrl",item.optString("imageUrl",""));
        if(kind.equals("group"))url=item.optString("iconUrl",item.optString("bannerUrl",""));
        if(url.isEmpty() && kind.equals("user"))url=item.optString("currentAvatarImageUrl","");
        if(!url.isEmpty())addImage(url);
        if(kind.equals("user"))showUser(item);
        if(kind.equals("world"))showWorld(item);
        if(kind.equals("group"))showGroup(item);
        spacer(16);
        Button refresh=CompanionStyle.button(this,"Refresh details",true);
        content.addView(refresh,CompanionStyle.buttonParams(this));
        refresh.setOnClickListener(v->load(true));
    }
    private void showUser(JSONObject u){
        row("Display name",u.optString("displayName"));
        row("Pronouns",u.optString("pronouns"));
        row("Status",u.optString("status"));
        row("Status message",u.optString("statusDescription"));
        row("Bio",u.optString("bio"));
        // Internal account tags are not meaningful to ordinary staff.
        row("Joined VRChat",u.optString("date_joined"));
        row("Last login",u.optString("last_login"));
        row("Last platform",u.optString("last_platform"));
        // Technical identifiers are intentionally hidden from ordinary staff.
        String location=u.optString("location","");
        if(location.startsWith("wrld_")){
            String world=location.split(":",2)[0];
            if(valid("world",world))navigate("View current world","World location published to your account","world",world);
        }else{
            row("Location",location.equals("private")?"Private":location.equals("offline")?"Offline":location);
        }
    }
    private void showWorld(JSONObject w){
        row("Description",w.optString("description"));
        row("Author",w.optString("authorName"));
        row("Players now",Integer.toString(w.optInt("occupants",0)));
        row("Capacity",Integer.toString(w.optInt("capacity",0)));
        row("Favorites",Integer.toString(w.optInt("favorites",0)));
        row("Visits",Integer.toString(w.optInt("visits",0)));
        row("Publication",w.optString("publicationDate"));
        row("Release status",w.optString("releaseStatus"));
        // Raw API tags and internal IDs are hidden from normal profile views.
        String author=w.optString("authorId","");
        navigate("View creator profile",w.optString("authorName",""),"user",author);
        // Instance tools will be presented in a dedicated native screen later.
    }
    private void showGroup(JSONObject g){
        row("Description",g.optString("description"));
        row("Short code",g.optString("shortCode"));
        row("Access type",g.optString("joinState"));
        row("Visibility",g.optString("discriminator"));
        row("Members",Integer.toString(g.optInt("memberCount",0)));
        row("Online members",Integer.toString(g.optInt("onlineMemberCount",0)));
        row("Privacy",g.optString("privacy"));
        // Raw group IDs are not needed here.
        String owner=g.optString("ownerId","");
        navigate("View group owner",null,"user",owner);
        // No moderation action unless separately authorized and explicitly requested.
    }
    private void addImage(String original){
        // Unauthenticated CDN images only, avoiding token leaks and arbitrary hosts.
        try{
            URL u=new URL(original);
            String host=u.getHost().toLowerCase(java.util.Locale.US);
            if(!"https".equalsIgnoreCase(u.getProtocol()) ||
               !(host.equals("vrchat.cloud")||host.endsWith(".vrchat.cloud")||
                 host.equals("vrcimg.com")||host.endsWith(".vrcimg.com")||
                 host.equals("vrchat.com")||host.endsWith(".vrchat.com")))return;
        }catch(Exception e){return;}
        ImageView image=new ImageView(this);image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setBackground(CompanionStyle.shape(this,CompanionStyle.RAISED,18,0));
        image.setClipToOutline(true);
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,dp(202));
        params.bottomMargin=dp(10);content.addView(image,params);
        io.execute(()->{
            HttpURLConnection c=null;
            try{
                c=(HttpURLConnection)new URL(original).openConnection();
                c.setConnectTimeout(7500);c.setReadTimeout(9000);
                c.setInstanceFollowRedirects(false);
                if(c.getResponseCode()!=200)return;
                ByteArrayOutputStream bytes=new ByteArrayOutputStream();
                try(InputStream in=c.getInputStream()){
                    byte[] buffer=new byte[8192];int n;
                    while((n=in.read(buffer))!=-1){
                        if(bytes.size()+n>5_000_000)return;
                        bytes.write(buffer,0,n);
                    }
                }
                BitmapFactory.Options opt=new BitmapFactory.Options();opt.inSampleSize=2;
                Bitmap bitmap=BitmapFactory.decodeByteArray(bytes.toByteArray(),0,bytes.size(),opt);
                if(bitmap!=null)runOnUiThread(()->image.setImageBitmap(bitmap));
            }catch(Exception ignored){}
            finally{if(c!=null)c.disconnect();}
        });
    }
    @Override protected void onDestroy(){io.shutdownNow();super.onDestroy();}
}
