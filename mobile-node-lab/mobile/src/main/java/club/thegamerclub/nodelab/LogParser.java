package club.thegamerclub.nodelab;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LogParser {
  private static final Pattern JOIN=Pattern.compile("OnPlayerJoined\\s+.*?\\s+\\((usr_[a-fA-F0-9-]{36})\\)");
  private static final Pattern LEAVE=Pattern.compile("OnPlayerLeft\\s+.*?\\s+\\((usr_[a-fA-F0-9-]{36})\\)");
  private static final Pattern WORLD=Pattern.compile("\\bwrld_[a-fA-F0-9-]{36}\\b");
  private static final Pattern INSTANCE=Pattern.compile("\\bJoining\\s+wrld_[a-fA-F0-9-]{36}:[^\\s]*");
  private LogParser() {}

  public static final class Summary {
    public int joinEvents,leaveEvents,uniquePlayers,worldReferences,instanceEntries,unrecognizedJoins,lines;
    public boolean truncated;
    public String asText(){
      return "Join events: "+joinEvents+"\nUnique player IDs: "+uniquePlayers+
       "\nLeave events: "+leaveEvents+"\nUnrecognized join lines: "+unrecognizedJoins+
       "\nWorld IDs: "+worldReferences+"\nInstance entries: "+instanceEntries+
       "\nLines examined: "+lines+(truncated?"\n(Truncated to 32 MB)":"");
    }
  }

  public static Summary parse(Reader source) throws IOException {
    Summary s=new Summary();
    Set<String> players=new HashSet<>(),worlds=new HashSet<>();
    BufferedReader reader=new BufferedReader(source,32*1024);
    long chars=0;
    String line;
    while((line=reader.readLine())!=null){
      chars+=line.length()+1L;
      if(chars>32L*1024L*1024L){s.truncated=true;break;}
      s.lines++;
      Matcher joined=JOIN.matcher(line);
      if(joined.find()){s.joinEvents++;players.add(joined.group(1));}
      else if(line.contains("OnPlayerJoined"))s.unrecognizedJoins++;
      if(LEAVE.matcher(line).find())s.leaveEvents++;
      Matcher match=WORLD.matcher(line);
      while(match.find())worlds.add(match.group());
      if(INSTANCE.matcher(line).find())s.instanceEntries++;
    }
    s.uniquePlayers=players.size();s.worldReferences=worlds.size();
    return s;
  }
}
