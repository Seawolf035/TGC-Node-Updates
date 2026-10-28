package club.thegamerclub.nodelab;
import java.util.*;
final class StaffDirectoryRepository {
    static final class Entry {
        final String id;
        final String rank;
        Entry(String id,String rank){this.id=id;this.rank=rank;}
        boolean canFriend(){return id!=null&&!id.isEmpty();}
        boolean royalty(){return "The Royalty".equals(rank);}
    }
    static final class Snapshot {
        final String accountId;
        final ArrayList<Entry> members;
        final boolean rosterComplete,friendsComplete;
        final Set<String> friendIds;
        Snapshot(String viewer,boolean rosterComplete,boolean friendsComplete,
                 Set<String> friends,Entry...members){
            this.accountId=viewer;this.rosterComplete=rosterComplete;
            this.friendsComplete=friendsComplete;
            this.friendIds=Collections.unmodifiableSet(new HashSet<>(friends));
            this.members=new ArrayList<>(Arrays.asList(members));
        }
        boolean collectedId(String id){
            return id!=null&&(id.equals(accountId)||friendIds.contains(id));
        }
    }
}
