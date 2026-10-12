package club.thegamerclub.nodelab;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import org.json.JSONArray;
import org.json.JSONObject;

/** A local SQLite queue to survive disconnects, app restarts and retryable relay errors. */
public final class MobileOutbox extends SQLiteOpenHelper {
  public MobileOutbox(Context context){super(context,"tgc_mobile_outbox.sqlite",null,4);}
  @Override public void onCreate(SQLiteDatabase db){
    db.execSQL("CREATE TABLE IF NOT EXISTS events(event_id TEXT PRIMARY KEY, json TEXT NOT NULL, created_at INTEGER NOT NULL)");
    db.execSQL("CREATE INDEX IF NOT EXISTS idx_events_time ON events(created_at)");
    db.execSQL("CREATE TABLE IF NOT EXISTS encounters(user_id TEXT PRIMARY KEY, display_name TEXT NOT NULL DEFAULT '', seen_at INTEGER NOT NULL, visits INTEGER NOT NULL DEFAULT 1)");
    db.execSQL("CREATE TABLE IF NOT EXISTS event_history(event_id TEXT PRIMARY KEY, json TEXT NOT NULL, seen_at INTEGER NOT NULL)");
    db.execSQL("CREATE INDEX IF NOT EXISTS idx_history_time ON event_history(seen_at)");
    createLocalActivity(db);
  }
  @Override public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion){
    if(oldVersion<2)db.execSQL("CREATE TABLE IF NOT EXISTS encounters(user_id TEXT PRIMARY KEY, display_name TEXT NOT NULL DEFAULT '', seen_at INTEGER NOT NULL, visits INTEGER NOT NULL DEFAULT 1)");
    if(oldVersion<3){
      db.execSQL("CREATE TABLE IF NOT EXISTS event_history(event_id TEXT PRIMARY KEY, json TEXT NOT NULL, seen_at INTEGER NOT NULL)");
      db.execSQL("CREATE INDEX IF NOT EXISTS idx_history_time ON event_history(seen_at)");
    }
    if(oldVersion<4)createLocalActivity(db);
  }
  /** Local-only VRCX event history, intentionally distinct from Relay uploads. */
  private static void createLocalActivity(SQLiteDatabase db){
    db.execSQL("CREATE TABLE IF NOT EXISTS local_activity(event_id TEXT PRIMARY KEY,kind TEXT NOT NULL,user_id TEXT NOT NULL DEFAULT '',display_name TEXT NOT NULL DEFAULT '',location TEXT NOT NULL DEFAULT '',event_at INTEGER NOT NULL)");
    db.execSQL("CREATE INDEX IF NOT EXISTS idx_local_activity_time ON local_activity(event_at)");
    db.execSQL("CREATE TABLE IF NOT EXISTS active_players(user_id TEXT PRIMARY KEY,display_name TEXT NOT NULL DEFAULT '',location TEXT NOT NULL DEFAULT '',joined_at INTEGER NOT NULL)");
  }
  public synchronized int insert(JSONArray events){
    SQLiteDatabase db=getWritableDatabase();
    db.beginTransaction();
    int stored=0;
    try{
      android.database.sqlite.SQLiteStatement statement=db.compileStatement(
        "INSERT OR IGNORE INTO events(event_id,json,created_at) VALUES(?,?,?)");
      android.database.sqlite.SQLiteStatement addHistory=db.compileStatement(
        "INSERT OR IGNORE INTO event_history(event_id,json,seen_at) VALUES(?,?,?)");
      android.database.sqlite.SQLiteStatement addEncounter=db.compileStatement(
        "INSERT OR IGNORE INTO encounters(user_id,display_name,seen_at,visits) VALUES(?,?,?,0)");
      android.database.sqlite.SQLiteStatement updateEncounter=db.compileStatement(
        "UPDATE encounters SET display_name=CASE WHEN ? != '' THEN ? ELSE display_name END, seen_at=?,visits=visits+1 WHERE user_id=?");
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
        if(statement.executeInsert()!=-1){
          stored++;
          long stamp=System.currentTimeMillis();
          addHistory.clearBindings();
          addHistory.bindString(1,id);
          addHistory.bindString(2,v.toString());
          addHistory.bindLong(3,stamp);
          addHistory.executeInsert();
          String name=v.optString("display_name","");
          if(name.length()>180)name=name.substring(0,180);
          addEncounter.clearBindings();
          addEncounter.bindString(1,user);
          addEncounter.bindString(2,name);
          addEncounter.bindLong(3,stamp);
          addEncounter.executeInsert();
          updateEncounter.clearBindings();
          updateEncounter.bindString(1,name);
          updateEncounter.bindString(2,name);
          updateEncounter.bindLong(3,stamp);
          updateEncounter.bindString(4,user);
          updateEncounter.executeUpdateDelete();
        }
      }
      // Bound disk use: 15,000 events maximum, preserve newest if excess.
      db.execSQL("DELETE FROM events WHERE event_id IN (SELECT event_id FROM events ORDER BY created_at DESC LIMIT -1 OFFSET 15000)");
      db.execSQL("DELETE FROM event_history WHERE seen_at < "+(System.currentTimeMillis()-30L*86400000L));
      db.execSQL("DELETE FROM event_history WHERE event_id IN (SELECT event_id FROM event_history ORDER BY seen_at DESC LIMIT -1 OFFSET 5000)");
      db.execSQL("DELETE FROM encounters WHERE seen_at < "+(System.currentTimeMillis()-30L*86400000L));
      db.execSQL("DELETE FROM encounters WHERE user_id IN (SELECT user_id FROM encounters ORDER BY seen_at DESC LIMIT -1 OFFSET 500)");
      db.setTransactionSuccessful();
    }finally{db.endTransaction();}
    return stored;
  }
  /**
   * Locally recorded join/leave/instance events are NEVER queued for the Relay.
   * Insert and active-player state transitions are atomic; duplicate binder retries
   * cannot alter join counts or player presence.
   */
  public synchronized int insertLocalActivity(JSONArray events){
    SQLiteDatabase db=getWritableDatabase();
    db.beginTransaction();
    int inserted=0;
    try{
      android.database.sqlite.SQLiteStatement put=db.compileStatement(
        "INSERT OR IGNORE INTO local_activity(event_id,kind,user_id,display_name,location,event_at) VALUES(?,?,?,?,?,?)");
      android.database.sqlite.SQLiteStatement present=db.compileStatement(
        "INSERT OR REPLACE INTO active_players(user_id,display_name,location,joined_at) VALUES(?,?,?,?)");
      android.database.sqlite.SQLiteStatement left=db.compileStatement(
        "DELETE FROM active_players WHERE user_id=?");
      for(int i=0;i<events.length();i++){
        JSONObject e=events.optJSONObject(i);
        if(e==null)continue;
        String id=e.optString("event_id","");
        String kind=e.optString("kind","");
        String user=e.optString("user_id","");
        String name=e.optString("display_name","");
        String location=e.optString("location","");
        long time=e.optLong("time",System.currentTimeMillis());
        if(!id.matches("local_[0-9a-fA-F-]{36}") ||
            !("join".equals(kind)||"leave".equals(kind)||"instance".equals(kind)))continue;
        if(!"instance".equals(kind)&&!user.matches("usr_[0-9a-fA-F-]{36}"))continue;
        if(name.length()>180)name=name.substring(0,180);
        if(location.length()>220)location=location.substring(0,220);
        long now=System.currentTimeMillis();
        if(time<=0||time>now+300_000L||time<now-2L*86400000L)time=now;
        put.clearBindings();
        put.bindString(1,id);put.bindString(2,kind);
        put.bindString(3,user);put.bindString(4,name);
        put.bindString(5,location);put.bindLong(6,time);
        if(put.executeInsert()==-1)continue;
        inserted++;
        if("instance".equals(kind)){
          db.execSQL("DELETE FROM active_players");
        }else if("join".equals(kind)){
          present.clearBindings();
          present.bindString(1,user);present.bindString(2,name);
          present.bindString(3,location);present.bindLong(4,time);
          present.executeInsert();
        }else{
          left.clearBindings();left.bindString(1,user);
          left.executeUpdateDelete();
        }
      }
      db.execSQL("DELETE FROM local_activity WHERE event_at < "+(System.currentTimeMillis()-30L*86400000L));
      db.execSQL("DELETE FROM local_activity WHERE event_id IN (SELECT event_id FROM local_activity ORDER BY event_at DESC LIMIT -1 OFFSET 5000)");
      db.setTransactionSuccessful();
    }finally{db.endTransaction();}
    return inserted;
  }
  /** Reset a stale player list when a new log watcher session begins. History remains. */
  public synchronized void resetCurrentPlayers(){
    getWritableDatabase().execSQL("DELETE FROM active_players");
  }
  public synchronized JSONArray currentPlayers(int max){
    JSONArray out=new JSONArray();
    try(Cursor c=getReadableDatabase().rawQuery(
      "SELECT user_id,display_name,location,joined_at FROM active_players ORDER BY joined_at DESC LIMIT ?",
      new String[]{String.valueOf(Math.max(1,Math.min(max,250)))})){
      while(c.moveToNext()){
        JSONObject row=new JSONObject();
        try{row.put("user_id",c.getString(0)).put("display_name",c.getString(1))
          .put("location",c.getString(2)).put("time",c.getLong(3));
          out.put(row);
        }catch(Exception ignored){}
      }
    }
    return out;
  }
  public synchronized JSONArray recentLocalActivity(int max){
    JSONArray out=new JSONArray();
    try(Cursor c=getReadableDatabase().rawQuery(
      "SELECT kind,user_id,display_name,location,event_at FROM local_activity ORDER BY event_at DESC LIMIT ?",
      new String[]{String.valueOf(Math.max(1,Math.min(max,300)))})){
      while(c.moveToNext()){
        JSONObject item=new JSONObject();
        try{
          item.put("kind",c.getString(0)).put("user_id",c.getString(1))
            .put("display_name",c.getString(2)).put("location",c.getString(3))
            .put("time",c.getLong(4));
          out.put(item);
        }catch(Exception ignored){}
      }
    }
    return out;
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
  public synchronized JSONArray recentEncounters(int max){
    JSONArray result=new JSONArray();
    try(Cursor cursor=getReadableDatabase().rawQuery(
      "SELECT user_id,display_name,seen_at,visits FROM encounters ORDER BY seen_at DESC LIMIT ?",
      new String[]{String.valueOf(Math.min(Math.max(1,max),100))})){
      while(cursor.moveToNext()){
        JSONObject entry=new JSONObject();
        try{
          entry.put("user_id",cursor.getString(0));
          entry.put("display_name",cursor.getString(1));
          entry.put("last_seen",new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm",java.util.Locale.US)
            .format(new java.util.Date(cursor.getLong(2))));
          entry.put("count",cursor.getInt(3));
          result.put(entry);
        }catch(org.json.JSONException ignored){}
      }
    }
    return result;
  }
  /** Recently captured log events remain available even after the Relay upload queue drains. */
  public synchronized JSONArray recentTrackedEvents(int max){
    JSONArray result=new JSONArray();
    try(Cursor cursor=getReadableDatabase().rawQuery(
      "SELECT json,seen_at FROM event_history ORDER BY seen_at DESC LIMIT ?",
      new String[]{String.valueOf(Math.max(1,Math.min(max,150)))})){
      while(cursor.moveToNext()){
        try{
          JSONObject event=new JSONObject(cursor.getString(0));
          event.put("local_seen_at",cursor.getLong(1));
          result.put(event);
        }catch(Exception ignored){}
      }
    }
    return result;
  }
  /** Private, local encounter summary for one player profile. */
  public synchronized JSONObject encounter(String userId){
    if(userId==null||!userId.matches("usr_[0-9a-fA-F-]{36}"))return null;
    try(Cursor c=getReadableDatabase().rawQuery(
        "SELECT display_name,seen_at,visits FROM encounters WHERE user_id=?",
        new String[]{userId})){
      if(!c.moveToFirst())return null;
      JSONObject entry=new JSONObject();
      entry.put("display_name",c.getString(0));
      entry.put("last_seen",new java.text.SimpleDateFormat(
        "MMM d, yyyy 'at' h:mm a",java.util.Locale.getDefault())
        .format(new java.util.Date(c.getLong(1))));
      entry.put("count",c.getInt(2));
      return entry;
    }catch(Exception ignored){return null;}
  }
  public synchronized void wipe(){
    getWritableDatabase().execSQL("DELETE FROM events");
    getWritableDatabase().execSQL("DELETE FROM encounters");
    getWritableDatabase().execSQL("DELETE FROM event_history");
    getWritableDatabase().execSQL("DELETE FROM local_activity");
    getWritableDatabase().execSQL("DELETE FROM active_players");
  }
}
