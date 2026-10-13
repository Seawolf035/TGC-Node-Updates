# TGC Staff Companion v0.18.2 — Local world bookmarks

## Feature
An additive, staff-gated **VRChat world utility**, not a TGC management feature. Users can bookmark up to **24** worlds on the Android device regardless of VRChat's remote favorite limits. Each personal VRChat account gets its own local bookmark list.

- Native world details: `☆ Bookmark this world` / `★ Remove world bookmark`.
- Native app Places & Community: `★ Bookmarked worlds`, searchable locally and independent of loading VRChat favorite-world pages.
- Native favorite/recent/world-search result rows: starred title indicates local bookmark.
- Floating Mini-VRCX Saved worlds: local bookmarks listed first, above actual VRChat favorites.
- Floating Mini-VRCX world details: bookmark/unbookmark and existing copy-ID/link.
- Floating button label now says `World bookmarks & favorites`.

## Compatibility and security
- No change to VRChat remote favorites, server writes, TGC staff hierarchy or invite network.
- No new permissions, login screens, endpoints, credential storage or background broadcasts.
- Only valid `wrld_<UUID>` IDs; a max of 24 entries, short labels and duplicate filtering.
- Validated account-scoped preferences; account switch isolates bookmarks.
- All mutations require active `StaffGate` authorization and the owner's personal VRChat session. View screens are already staff-gated.
- Existing v0.18.1 official rank-locked chatboxes and every prior Mini-VRCX/OSC tool unchanged.
- A local bookmark is a **shortcut**, not a VRChat account favorite. Pinning a world does not join or invite to an instance.

## CI and release
- Android `:mobile:assembleDebug` on the v0.18.2 feature branch.
- Promote source to `experiment/mobile-node-lab` only after a successful compile.
- Existing GitHub Actions permanent-signing workflow publishes the same stable APK path, verifies certificate compatibility and updates `mobile-node-lab/mobile-update.json` to v0.18.2 / code 45.
- Verify published SHA256 and APK are included in the release commit. On-device QA is not established by successful CI.

## Manual device QA (after install)
1. In-place upgrade from v0.18.1; staff deployment, personal VRChat sessions, Node queue, group shortcuts, chats and avatar favorites persist.
2. Bookmark a non-favorite world from native details; verify it appears in Bookmarked worlds and at top of the floating Saved worlds.
3. Bookmark a second world from floating details; verify star marker on native search/favorite result, if loaded.
4. Remove a bookmark from either details screen; ensure both lists reflect it after reopening/refresh.
5. Try invalid world ID, empty label, duplicate bookmark and 25th bookmark: invalid/overflow disallowed.
6. Switch personal VRChat accounts; pins must not mix. Switch back; original list returns.
7. Disable staff authentication; no new bookmarks can be written, existing VRChat remote favorites unchanged.
8. Confirm a bookmark selection only opens a world profile; no silent instance join, network invite or OSC packet.
9. Confirm Quest opt-in, overlay collapse/reopen, app restart, personal notes, OSC, official chatbox rank gating, Shizuku and Mobile Node regression.
