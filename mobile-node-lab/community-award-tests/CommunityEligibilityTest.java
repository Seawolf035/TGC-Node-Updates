package club.thegamerclub.nodelab;
import java.util.*;
public final class CommunityEligibilityTest {
    static void check(boolean result,String label){
        if(!result)throw new AssertionError(label);
    }
    static StaffDirectoryRepository.Snapshot roster(String viewer,boolean rosterComplete,
        boolean friendsComplete,Set<String> friends,StaffDirectoryRepository.Entry... entries){
        return new StaffDirectoryRepository.Snapshot(
            viewer,rosterComplete,friendsComplete,friends,entries);
    }
    public static void main(String[] args){
        String viewer="usr_00000000-0000-0000-0000-000000000099";
        String wesman=SpecialStaffDexAchievements.WESMAN;
        String kaitlyn=SpecialStaffDexAchievements.KAITLYN;
        String fixed=SpecialStaffDexAchievements.SEAWOLF;
        String[] originalCommunity={
            SpecialStaffDexAchievements.DEVINITY,SpecialStaffDexAchievements.SOUP,
            SpecialStaffDexAchievements.KLASHY,SpecialStaffDexAchievements.KAITLYN
        };
        Set<String> friends=new HashSet<>(Arrays.asList(wesman,fixed));
        friends.addAll(Arrays.asList(originalCommunity));
        StaffDirectoryRepository.Entry founder=
            new StaffDirectoryRepository.Entry(fixed,"The Royalty");
        StaffDirectoryRepository.Snapshot outside=roster(viewer,true,true,friends,founder);
        check(SpecialStaffDexAchievements.collectedCommunityFriend(outside,wesman),
            "Base friendship is still confirmed for Wesman");
        check(SpecialStaffDexAchievements.collectedWesmanCommunityFriend(outside),
            "Wesman unlock exists when non-staff and a confirmed friend");
        StaffDirectoryRepository.Snapshot promotion=roster(viewer,true,true,friends,
            new StaffDirectoryRepository.Entry(wesman,"Trial Moderator"),founder);
        check(SpecialStaffDexAchievements.isCurrentStaff(promotion,wesman),
            "Trial Moderator is staff according to official roster");
        check(!SpecialStaffDexAchievements.collectedWesmanCommunityFriend(promotion),
            "Wesman reward vanishes while staff even if viewer remains a friend");
        check(SpecialStaffDexAchievements.collectedCommunityFriend(promotion,wesman),
            "Underlying original friend collection remains untouched (the display gate is separate)");
        check(SpecialStaffDexAchievements.collected(promotion,fixed),
            "Named fixed staff/leadership achievement unchanged");
        for(String id:originalCommunity){
            check(SpecialStaffDexAchievements.collectedCommunityFriend(outside,id),
                "Original community unlock still exists when earned: "+id);
            check(SpecialStaffDexAchievements.collectedCommunityFriend(promotion,id),
                "Wesman's promotion cannot remove another hidden unlock: "+id);
            StaffDirectoryRepository.Snapshot current=roster(viewer,true,true,friends,
                new StaffDirectoryRepository.Entry(id,"Admin"),founder);
            check(SpecialStaffDexAchievements.collectedCommunityFriend(current,id),
                "Other explicitly defined hidden achievements are NOT dynamically reclassified: "+id);
            StaffDirectoryRepository.Snapshot incompleteRoster=roster(viewer,false,true,friends,founder);
            check(SpecialStaffDexAchievements.collectedCommunityFriend(incompleteRoster,id),
                "Original hidden friend rewards stay visible while roster is loading/offline: "+id);
        }
        StaffDirectoryRepository.Snapshot demotion=roster(viewer,true,true,friends,founder);
        check(SpecialStaffDexAchievements.collectedWesmanCommunityFriend(demotion),
            "Wesman reward can return following staff departure");
        StaffDirectoryRepository.Snapshot partial=roster(viewer,false,true,friends,founder);
        check(!SpecialStaffDexAchievements.collectedWesmanCommunityFriend(partial),
            "Only Wesman needs a complete authoritative staff roster");
        StaffDirectoryRepository.Snapshot partialFriends=roster(viewer,true,false,friends,founder);
        check(!SpecialStaffDexAchievements.collectedWesmanCommunityFriend(partialFriends),
            "Wesman must be confirmed friend on a complete scan");
        for(String id:originalCommunity)
            check(!SpecialStaffDexAchievements.collectedCommunityFriend(partialFriends,id),
                "Others retain original complete-friend requirement");
        StaffDirectoryRepository.Snapshot self=roster(wesman,true,true,friends,founder);
        check(!SpecialStaffDexAchievements.collectedWesmanCommunityFriend(self),
            "Wesman cannot earn himself");
        check(!SpecialStaffDexAchievements.collectedWesmanCommunityFriend(null),
            "Unknown roster never shows Wesman");
        check(!SpecialStaffDexAchievements.collectedCommunityFriend(null,kaitlyn),
            "Original rewards reject missing snapshot");
        check(!SpecialStaffDexAchievements.collectedCommunityFriend(outside,null),
            "Original rewards reject missing ID");
        StaffDirectoryRepository.Snapshot noFriend=roster(viewer,true,true,
            Collections.emptySet(),founder);
        check(!SpecialStaffDexAchievements.collectedWesmanCommunityFriend(noFriend),
            "Wesman still requires friend");
        for(String id:originalCommunity)
            check(!SpecialStaffDexAchievements.collectedCommunityFriend(noFriend,id),
                "No other hidden rewards without confirmed friendship");
        System.out.println("PASS: four original hidden rewards preserved across roster changes/offline; Wesman-only dynamic staff; fixed awards unchanged");
    }
}
