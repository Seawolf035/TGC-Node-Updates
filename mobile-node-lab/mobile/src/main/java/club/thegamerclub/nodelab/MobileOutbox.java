package club.thegamerclub.nodelab;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import org.json.JSONArray;
import org.json.JSONObject;

/** A local SQLite queue to survive disconnects, app restarts and retryable relay errors. */
public final class MobileOutbox extends SQLiteOpenHelper {
  public MobileOutbox(Context context){super(context,"tgc_mobile_outbox.sqlite",null,1);}
  @Override public void onCreate(SQLiteDatabase db){
    db.execSQL("CREATE TABLE IF NOT EXISTS events(event_id TEXT PRIMARY KEY, json TEXT NOT NULL, created_at INTEGER NOT NULL)");
    db.execSQL("CREATE INDEX IF NOT EXISTS idx_events_time ON events(created_at)");
  }
  @Override public void onUpgrade(SQLiteDatabase db,int a,int b){}
  public synchronized int insert(JSONArray events){
    SQLiteDatabase db=getWritableDatabase();
    db.beginTransaction();
    int stored=0;
    try{
      android.database.sqlite.SQLiteStatement statement=db.compileStatement(
        "INSERT OR IGNORE INTO events(event_id,json,created_at) VALUES(?,?,?)");
      for(int i=0;i<events.length();i++){
        JSONObject v=events.optJSONObject(i);
        if(v==null)continue;
        String id=v.optString("event_id");
        String user=v.optString("user_id");
        if(!id.matches("mobile_[0-9a-fA-F-]{36}") ||
           !user.matches("usr_[0-9a-fA-F-]{36}"))continue;
        statement.clearBindings();
        statement.bindString(1,id);
        statement.bindString(2,v.toString());
        statement.bindLong(3,System.currentTimeMillis());
        if(statement.executeInsert()!=-1)stored++;
      }
      // Bound disk use: 15,000 events maximum, preserve newest if excess.
      db.execSQL("DELETE FROM events WHERE event_id IN (SELECT event_id FROM events ORDER BY created_at DESC LIMIT -1 OFFSET 15000)");
      db.setTransactionSuccessful();
    }finally{db.endTransaction();}
    return stored;
  }
  public synchronized JSONArray batch(int limit){
    JSONArray out=new JSONArray();
    try(Cursor cursor=getReadableDatabase().rawQuery(
      "SELECT json FROM events ORDER BY created_at ASC LIMIT ?",new String[]{String.valueOf(Math.max(1,Math.min(limit,200)))})){
      while(cursor.moveToNext()){
        try{out.put(new JSONObject(cursor.getString(0)));}catch(Exception ignored){}
      }
    }
    return out;
  }
  public synchronized void remove(JSONArray ids){
    SQLiteDatabase db=getWritableDatabase();
    db.beginTransaction();
    try{
      android.database.sqlite.SQLiteStatement statement=db.compileStatement("DELETE FROM events WHERE event_id=?");
      for(int i=0;i<ids.length();i++){
        String id=ids.optString(i,"");
        if(!id.matches("mobile_[0-9a-fA-F-]{36}"))continue;
        statement.clearBindings();statement.bindString(1,id);statement.executeUpdateDelete();
      }
      db.setTransactionSuccessful();
    }finally{db.endTransaction();}
  }
  public synchronized int count(){
    try(Cursor c=getReadableDatabase().rawQuery("SELECT COUNT(*) FROM events",null)){
      return c.moveToNext()?c.getInt(0):0;
    }
  }
  public synchronized void wipe(){
    getWritableDatabase().execSQL("DELETE FROM events");
  }
}
