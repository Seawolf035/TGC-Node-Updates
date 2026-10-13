# TGC Staff Companion v0.19.6 — Player Groups and Status QA

## Scope

- **Player profile → Groups → View all visible groups** opens a dedicated,
  searchable, alphabetical group directory for that VRChat user.
- Uses existing authenticated `GET /users/{userId}/groups` endpoint and
  respects the memberships VRChat actually exposes to the current account.
  Membership ids (`gmem_...`) are NOT group ids; links always use `groupId`
  (`grp_...`). The renderer supports both the documented flat response and
  responses containing a nested `group` object.
- Each result can open the preexisting VRChat group details and group tools.
  Search/filter is local to fetched results, not an undocumented remote search.
- Profile shows only a five-item preview. A separate browse screen works even
  when a profile omits the groups preview entirely.
- No hidden/private memberships are discovered or claimed to be visible.
  No privileged group-member or ban endpoint is called by the browser.
- Removed **Offline** from selectable VRChat personal statuses in full Status
  & Presets and Quest/phone native tools. The API also refuses Offline as an
  account status change. This does **NOT** change real offline friend status
  display, friend list filters, or offline presence detection.
- Legacy saved status presets that specify Offline remain in local settings,
  can still be deleted, and are visibly marked unsupported/unloadable.
- Existing VRCX-named local preferences and extended avatar databases remain
  unchanged so upgrades preserve data.

## Test plan

1. Open a player profile which has 30+ publicly visible groups. Confirm only
   up to five groups appear in the profile preview and **View all visible
   groups** loads the complete permitted endpoint response, sorted by name.
2. Check name, short code, icon and navigation to a group's existing detail
   page. Ensure the group ID is `grp_...`, never `gmem_...`.
3. Open a profile with zero visible groups. The button must still be present,
   and the directory must show a privacy-aware empty state.
4. Open your own profile and another user's profile. Confirm the directory
   only shows groups allowed by the current VRChat session, not hidden ones.
5. Search by name and short code, clear the search, reload, and test API
   errors/rate limits. Existing results must survive a failed refresh.
6. Full Status & Presets must offer only Online, Join Me, Ask Me, and Do Not
   Disturb. Compact Quest/phone native tools must offer the same four values.
7. A previously saved **Offline** preset must be visible but disabled in the
   full editor; deletion should still work. On compact UI it cannot be
   applied. Actual offline friends and profile indicators must remain.
8. Test status mutation on all four supported values. The API rejects a
   programmatic `offline` status mutation.
9. Verify tracking on Quest 2/3 folder permission and phone Shizuku, phone
   floater, Quest System, avatar favorites, Relay and staff gates still work.
10. Upgrade over 0.19.5 using the permanent signing key, without uninstalling.

## Version

0.19.6 (Android code 58). No Main Launcher, Relay or invite authority changes.
