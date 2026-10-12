package club.thegamerclub.nodelab;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * User-scoped, on-device extended avatar library.
 * Does NOT create VRChat server favorites or circumvent the native favorite cap.
 * No relay/Discord traffic; subscription is checked online before UI access and mutation.
 */
public final class ExtendedAvatarStore extends SQLiteOpenHelper {
    private static final String NAME="tgc_vrcx_extended_avatar_favorites.db";
    private static final int VERSION=1;
    public ExtendedAvatarStore(Context context){super(context,NAME,null,VERSION);}
    @Override public void onCreate(SQLiteDatabase db){
        db.execSQL("CREATE TABLE saved_avatar ("+
            "account_id TEXT NOT NULL, avatar_id TEXT NOT NULL, group_name TEXT NOT NULL,"+
            "display_name TEXT NOT NULL, author_name TEXT NOT NULL, image_url TEXT NOT NULL,"+
            "saved_at INTEGER NOT NULL, PRIMARY KEY(account_id,avatar_id))");
        db.execSQL("CREATE INDEX idx_saved_avatar_group ON saved_avatar(account_id,group_name,saved_at)");
    }
    @Override public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion){}
    public static boolean validUser(String account){
        return account!=null&&account.matches("usr_[0-9a-fA-F-]{36}");
    }
    public static boolean validAvatar(String avatar){
        return avatar!=null&&avatar.matches("avtr_[0-9a-fA-F-]{36}");
    }
    public static String category(String value){
        String clean=value==null?"":value.trim();
        if(clean.isEmpty())return "General";
        if(clean.length()>40 || clean.contains("\n")||clean.contains("\r"))
            throw new IllegalArgumentException("Category name must be 1–40 characters");
        return clean;
    }
    public synchronized int total(String user){
        if(!validUser(user))throw new IllegalArgumentException("Invalid VRChat user");
        try(Cursor c=getReadableDatabase().rawQuery(
            "SELECT count(*) FROM saved_avatar WHERE account_id=?",new String[]{user})){
            return c.moveToFirst()?c.getInt(0):0;
        }
    }
    public synchronized void add(String user,JSONObject avatar,String group){
        if(!validUser(user))throw new IllegalArgumentException("Invalid VRChat user");
        String id=avatar.optString("id","");
        if(!validAvatar(id))throw new IllegalArgumentException("Invalid avatar ID");
        String category=category(group);
        SQLiteDatabase db=getWritableDatabase();
        db.beginTransaction();
        try{
            try(Cursor c=db.rawQuery(
                "SELECT count(*) FROM saved_avatar WHERE account_id=?",new String[]{user})){
                if(c.moveToFirst()&&c.getInt(0)>=10000)
                    throw new IllegalStateException("Extended library has reached its 10,000-avatar safety limit");
            }
            android.content.ContentValues values=new android.content.ContentValues();
            values.put("account_id",user);
            values.put("avatar_id",id);
            values.put("group_name",category);
            values.put("display_name",avatar.optString("name","Avatar").substring(0,
                Math.min(180,avatar.optString("name","Avatar").length())));
            values.put("author_name",avatar.optString("authorName","Unknown creator").substring(0,
                Math.min(160,avatar.optString("authorName","Unknown creator").length())));
            String thumbnail=CompanionImages.preferred(avatar,"thumbnailImageUrl","imageUrl");
            values.put("image_url",thumbnail);
            values.put("saved_at",System.currentTimeMillis());
            db.insertWithOnConflict("saved_avatar",null,values,SQLiteDatabase.CONFLICT_REPLACE);
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }
    public synchronized boolean contains(String user,String avatar){
        if(!validUser(user)||!validAvatar(avatar))return false;
        try(Cursor c=getReadableDatabase().rawQuery(
            "SELECT 1 FROM saved_avatar WHERE account_id=? AND avatar_id=?",
            new String[]{user,avatar})){return c.moveToFirst();}
    }
    public synchronized JSONArray list(String user,String category,String filter){
        if(!validUser(user))throw new IllegalArgumentException("Invalid VRChat user");
        JSONArray out=new JSONArray();
        String where="account_id=?", term=filter==null?"":filter.trim();
        java.util.ArrayList<String> args=new java.util.ArrayList<>();
        args.add(user);
        if(category!=null&&!category.isEmpty()&&!category.equals("All")){
            where+=" AND group_name=?";args.add(category);
        }
        if(!term.isEmpty()){
            where+=" AND (display_name LIKE ? ESCAPE '\\' OR author_name LIKE ? ESCAPE '\\')";
            String like="%"+term.replace("\\","\\\\").replace("%","\\%").replace("_","\\_")+"%";
            args.add(like);args.add(like);
        }
        try(Cursor c=getReadableDatabase().query("saved_avatar",
            new String[]{"avatar_id","group_name","display_name","author_name","image_url","saved_at"},
            where,args.toArray(new String[0]),null,null,"saved_at DESC","10000")){
            while(c.moveToNext()){
                JSONObject row=new JSONObject();
                try{
                    row.put("id",c.getString(0));row.put("category",c.getString(1));
                    row.put("name",c.getString(2));row.put("authorName",c.getString(3));
                    row.put("thumbnailImageUrl",c.getString(4));row.put("savedAt",c.getLong(5));
                    out.put(row);
                }catch(Exception ignored){}
            }
        }
        return out;
    }
    public synchronized java.util.List<String> categories(String user){
        if(!validUser(user))throw new IllegalArgumentException("Invalid VRChat user");
        java.util.ArrayList<String> categories=new java.util.ArrayList<>();
        try(Cursor c=getReadableDatabase().rawQuery(
            "SELECT DISTINCT group_name FROM saved_avatar WHERE account_id=? ORDER BY group_name",
            new String[]{user})){
            while(c.moveToNext())categories.add(c.getString(0));
        }
        return categories;
    }
    public synchronized void remove(String user,String avatar){
        if(!validUser(user)||!validAvatar(avatar))throw new IllegalArgumentException("Invalid identifiers");
        getWritableDatabase().delete("saved_avatar","account_id=? AND avatar_id=?",
            new String[]{user,avatar});
    }
    public synchronized void move(String user,String avatar,String to){
        if(!validUser(user)||!validAvatar(avatar))throw new IllegalArgumentException("Invalid identifiers");
        android.content.ContentValues values=new android.content.ContentValues();
        values.put("group_name",category(to));
        getWritableDatabase().update("saved_avatar",values,"account_id=? AND avatar_id=?",
            new String[]{user,avatar});
    }
}
