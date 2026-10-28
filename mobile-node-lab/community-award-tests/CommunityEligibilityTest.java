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
        Set<String> friends=new HashSet<>(Arrays.asList(wesman,kaitlyn,fixed));
        StaffDirectoryRepository.Snapshot outside=roster(viewer,true,true,friends,
            new StaffDirectoryRepository.Entry(fixed,"The Royalty"));
        check(SpecialStaffDexAchievements.collectedCommunityFriend(outside,wesman),
            "Former staff member gets community reward after leaving staff, if still friends");
        check(SpecialStaffDexAchievements.collectedCommunityFriend(outside,kaitlyn),
            "Non-staff encounter remains eligible");
        StaffDirectoryRepository.Snapshot promotion=roster(viewer,true,true,friends,
            new StaffDirectoryRepository.Entry(wesman,"Trial Moderator"),
            new StaffDirectoryRepository.Entry(fixed,"The Royalty"));
        check(SpecialStaffDexAchievements.isCurrentStaff(promotion,wesman),
            "Trial Moderator is staff for Community eligibility");
        check(!SpecialStaffDexAchievements.collectedCommunityFriend(promotion,wesman),
            "Community reward ceases to EXIST even for an already confirmed friend");
        check(SpecialStaffDexAchievements.collectedCommunityFriend(promotion,kaitlyn),
            "Unchanged non-staff friend remains eligible");
        check(SpecialStaffDexAchievements.collected(promotion,fixed),
            "Existing fixed leadership achievement is NOT dynamically suppressed");
        StaffDirectoryRepository.Snapshot demotion=roster(viewer,true,true,friends,
            new StaffDirectoryRepository.Entry(fixed,"The Royalty"));
        check(SpecialStaffDexAchievements.collectedCommunityFriend(demotion,wesman),
            "Award dynamically reappears following staff departure, no archived unlock needed");
        StaffDirectoryRepository.Snapshot staffKaitlyn=roster(viewer,true,true,friends,
            new StaffDirectoryRepository.Entry(kaitlyn,"Moderator"));
        check(!SpecialStaffDexAchievements.collectedCommunityFriend(staffKaitlyn,kaitlyn),
            "Other community encounters follow same non-staff-only policy");
        check(SpecialStaffDexAchievements.collectedCommunityFriend(staffKaitlyn,wesman),
            "Staff state of one encounter does not suppress others");
        StaffDirectoryRepository.Snapshot partial=roster(viewer,false,true,friends);
        check(!SpecialStaffDexAchievements.collectedCommunityFriend(partial,wesman),
            "Incomplete staff roster must not unlock hidden community awards");
        StaffDirectoryRepository.Snapshot partialFriends=roster(viewer,true,false,friends);
        check(!SpecialStaffDexAchievements.collectedCommunityFriend(partialFriends,wesman),
            "Incomplete friend scan must not unlock hidden community awards");
        StaffDirectoryRepository.Snapshot self=roster(wesman,true,true,friends);
        check(!SpecialStaffDexAchievements.collectedCommunityFriend(self,wesman),
            "No self-collect");
        check(!SpecialStaffDexAchievements.collectedCommunityFriend(null,wesman),
            "Missing snapshot fails closed");
        check(!SpecialStaffDexAchievements.collectedCommunityFriend(outside,null),
            "Missing user ID fails closed");
        StaffDirectoryRepository.Snapshot noFriend=roster(viewer,true,true,
            Collections.emptySet(),new StaffDirectoryRepository.Entry(fixed,"The Royalty"));
        check(!SpecialStaffDexAchievements.collectedCommunityFriend(noFriend,wesman),
            "Former staff must still be a confirmed friend");
        for(String id:new String[]{
            SpecialStaffDexAchievements.DEVINITY,SpecialStaffDexAchievements.SOUP,
            SpecialStaffDexAchievements.KLASHY,SpecialStaffDexAchievements.WESMAN,
            SpecialStaffDexAchievements.KAITLYN}){
            StaffDirectoryRepository.Snapshot current=roster(viewer,true,true,
                Collections.singleton(id),new StaffDirectoryRepository.Entry(id,"Admin"));
            check(!SpecialStaffDexAchievements.collectedCommunityFriend(current,id),
                "No Community Encounter for current staff ID "+id);
        }
        System.out.println("PASS: dynamic Community-only suppression, return after leaving, fixed staff unchanged, friendship & roster fail-closed");
    }
}
