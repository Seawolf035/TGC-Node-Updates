package club.thegamerclub.nodelab;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Achievement catalog based on the owner's exact VRChat IDs and the current
 * authoritative staff roster. Never infer a person's identity from their name.
 * Friend requests and player sightings cannot unlock these achievements.
 */
public final class SpecialStaffDexAchievements {
    public static final String GAMEWITCH="usr_ff419c82-c92b-4494-b821-035d33253ef0";
    public static final String LILY="usr_b85631a8-5e23-4b88-a14b-4c8a2ae863c1";
    public static final String SEAWOLF="usr_2a833076-03ac-4212-aeff-0708d8eab968";
    public static final String IMPOSTER="usr_3f229b32-5059-4889-af22-a98203d7a2b3";
    public static final String AJ="usr_f430e93d-d7f4-4e23-a0e9-e332f2228dff";
    public static final String ASRIEL="usr_740ff8b0-6d49-4b38-b28b-a45f33ea8ef9";
    public static final String DARKFLAMES="usr_cd566dbc-b21d-4a84-9548-4767128f0bec";
    // The former staff's independently operated Gamer Music Jr is excluded.
    private SpecialStaffDexAchievements(){}

    public static Set<String> gamerMusic(){
        LinkedHashSet<String> ids=new LinkedHashSet<>();
        for(String id:GamerMusicProfiles.SET)ids.add(id);
        return Collections.unmodifiableSet(ids);
    }
    public static Set<String> royalIds(StaffDirectoryRepository.Snapshot roster){
        LinkedHashSet<String> ids=new LinkedHashSet<>();
        // Honor the owner's explicit Gamewitch/Royalty recognition, including
        // accounts absent from a current staff roster.
        ids.add(GAMEWITCH);
        for(StaffDirectoryRepository.Entry entry:roster.members)
            if(entry.royalty()&&entry.canFriend())ids.add(entry.id);
        return ids;
    }
    public static Set<String> ownerIds(StaffDirectoryRepository.Snapshot roster){
        LinkedHashSet<String> ids=new LinkedHashSet<>();
        for(StaffDirectoryRepository.Entry entry:roster.members)
            if("Owner".equals(entry.rank)&&entry.canFriend())ids.add(entry.id);
        // Owner-confirmed identities remain collectible even if the live
        // roster temporarily omits the user.
        ids.add(IMPOSTER);
        ids.add(AJ);
        ids.add(ASRIEL);
        return ids;
    }
    public static Set<String> union(Set<String> a,Set<String> b){
        LinkedHashSet<String> ids=new LinkedHashSet<>(a);
        ids.addAll(b);return ids;
    }
    public static int count(StaffDirectoryRepository.Snapshot snapshot,
        Set<String> ids){
        int result=0;
        for(String id:ids)if(snapshot.collectedId(id))result++;
        return result;
    }
    public static boolean complete(StaffDirectoryRepository.Snapshot snapshot,
        Set<String> ids){
        return snapshot.rosterComplete && snapshot.friendsComplete &&
            !ids.isEmpty() && count(snapshot,ids)==ids.size();
    }
    public static boolean collected(StaffDirectoryRepository.Snapshot snapshot,
        String id){
        return snapshot.friendsComplete && snapshot.collectedId(id);
    }
}
