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
    // NOT staff. Uses the same exact VRChat account as her exclusive theme.
    public static final String DEVINITY=NotableCommunityProfiles.DEVINITY;
    // Owner-specified notable community users, NOT official TGC staff.
    public static final String SOUP="usr_d2698c92-1c49-4fde-bf3b-ea1809cceed1";
    public static final String KLASHY="usr_42d46072-71c6-4fb9-8391-d1bbd0e543a3";
    public static final String WESMAN="usr_7a5238d4-9017-49dd-a0df-df25800d119c";
    public static final String KAITLYN="usr_a4ee31d2-0c71-4e12-a0ab-4c1c6693a315";
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
    /** True friendship only: notable non-staff users cannot earn self-friend awards. */
    public static boolean collectedCommunityFriend(
        StaffDirectoryRepository.Snapshot snapshot,String id){
        return snapshot.friendsComplete && id!=null &&
            !id.equals(snapshot.accountId) && snapshot.friendIds.contains(id);
    }
    public static boolean collected(StaffDirectoryRepository.Snapshot snapshot,
        String id){
        return snapshot.friendsComplete && snapshot.collectedId(id);
    }
}
