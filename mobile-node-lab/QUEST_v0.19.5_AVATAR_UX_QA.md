# Staff Companion v0.19.5 — Extended Avatar Favorites and terminology QA

## User-facing changes

- On **Favorite avatars**, each VRChat favorite now has a **☆ Save / ★ Saved** action.
  Tapping Save opens a local category picker, then saves to Extended Favorites
  without displaying or copying the avatar ID.
- If already saved, the workflow shows the existing category and can **move**
  the avatar to another category. Re-selecting the same category does nothing;
  there are no silent overwrites or duplicate rows.
- The **Extended avatar favorites** page now has **+ From favorites** as the primary
  action. It opens the same personal VRChat favorite browser in selection mode,
  returning to the local collection on Back.
- **Add by avatar link / ID · Advanced** remains available for manually adding
  avatars that are not already in normal favorites.
- User-facing **Mini-VRCX** labels in Quest native tool pages, group tools, and
  pinned-group explanation are replaced with Companion/Quest terminology.
  Internal class names, prefs `tgc_vrcx_presets`,
  `tgc_mini_vrcx_*`, and SQLite database
  `tgc_vrcx_extended_avatar_favorites.db` remain unchanged to preserve data.

## Data and authority

- Only the **signed-in personal VRChat account** can save local Extended Favorites.
  Each save/move revalidates active VRC+ entitlement and current account identity.
- Save a new avatar by fetching the real VRChat avatar detail and confirming
  its ID. Editing an existing category is local-only after auth checks.
- Saved favorites remain local. VRChat's server-managed favorite slots are never
  modified, and no avatar data is submitted to the Relay or GM1/GM4.
- StaffGate continues guarding these screens and actions.
- No database migration, data deletion, or account relinking.

## Test checklist

1. On an active VRC+ account with normal VRChat avatar favorites, open
   **Favorite avatars**. The quick Save button should work from a card even
   without entering an ID; inspect/details should offer the same save action.
2. Save to **General**, then to a newly typed category. The saved badge updates.
   Save again to the same category: no duplicate or overwrite; move to another
   category and verify it appears there.
3. From **Extended avatar favorites**, tap **+ From favorites**. Choose a
   favorite, save, and press Back; the existing local collection and counts
   should refresh. The advanced manual link/ID option still works.
4. Test favorite list refresh, search, categories and partial/fallback VRChat
   responses. Confirm errors are presented without clearing a previous list.
5. Test a user without VRC+: the save flow refuses mutation and explains
   entitlement; do not touch existing saved entries.
6. Switch between two signed-in VRChat accounts: badges and categories must
   remain account-specific.
7. Verify an old v0.19.4 database survives upgrade unchanged.
8. Inspect Quest System, group tools and pinned group help for any leftover
   visible Mini-VRCX labels. Internal file names and preference identifiers
   intentionally remain unchanged.
9. Confirm Quest Documents/Logs tracking, the Shizuku fallback, Android phone
   floater and tracking, and existing signed upgrade continue working.

## Release scope

v0.19.5 / Android versionCode 57. The dedicated user-group browser is planned
separately, not silently included in the avatar release.
