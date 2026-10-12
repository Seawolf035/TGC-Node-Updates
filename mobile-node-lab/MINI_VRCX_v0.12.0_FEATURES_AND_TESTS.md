# TGC Staff Companion v0.12.0 — Mini VRCX expansion

## Implemented
- **Extended avatar favorites (VRC+ only):** Active VRC+ is verified from the personal VRChat API's `/auth/user` response via the `system_supporter` user tag. Entitlement is checked before opening and reconfirmed before saving, removing, moving or selecting an avatar from the extended library. A temporary API error fails closed, but does not delete any saved data.
- **On-device extended favorite library:** Android SQLite database, keyed by VRChat account ID and avatar ID. Supports category names, filtering, searching names/creators, saving by ID, changing category, removing a bookmark and selecting an available avatar. Capacity safety limit of 10,000 local bookmarks/account. This does NOT increase VRChat's server-side favorite limits. Each account has a private on-device library, separate from any other signed-in VRChat account.
- **Copy from regular favorites:** Open a regular VRChat favorite avatar and choose **Save to Extended**, then select/create a category. Normal VRChat avatar favorites stay intact, without VRC+ requirement.
- **Avatar access restrictions:** Before saving, verify the supplied avatar ID resolves to a real accessible avatar on the user's own session. Avatar switches use the existing API and require explicit confirmation. On inaccessible/deleted avatars, keep the bookmark and offer safe removal.
- **Social status editor:** Choose Online / Join Me / Ask Me / Do Not Disturb / Offline, edit status message, and confirm before applying via the personal VRChat API.
- **Saved status presets:** Store up to 30 presets on-device and segregate by VRChat account ID. Loading a preset fills the editor; applying it requires a separate explicit action.
- **Security:** VRChat login cookies never go through TGC's relay. Staff Quick Deployment and entitlement verification remain separate. The background Node, Shizuku log monitor, sightings, mobile update/signing and staff authorization are preserved.

## Real-device checks
1. Install v0.12.0 directly over signed v0.11.0, without uninstalling. Confirm TGC staff deployment, VRChat login and player tracking remain unchanged.
2. Sign in to a **VRC+ subscriber account**. Open Explore → Extended avatar favorites and confirm access. Try manually entering an accessible `avtr_...` ID; use a category such as "Sharks".
3. Test saving from Avatar Favorites → tap existing favorite → Save to Extended. Confirm the avatar appears in the extended collection without deleting it from VRChat's standard favorites.
4. Save into a second category, filter categories, search, move an avatar between categories, and remove a bookmark. Confirm other saved bookmarks persist after app restart.
5. Open a local favorite; review avatar details, select Use, confirm that VRChat actually changes the avatar. Verify unavailable avatars are clearly identified.
6. Sign in to a **non-VRC+ account**. Confirm ordinary VRChat favorites still work, and Extended Favorites are locked. If the VRChat API fails, the extra library must remain locked until a new successful VRC+ check.
7. Switch VRChat accounts on one phone. Confirm extended lists and status presets are segregated by account and cannot be loaded under the other account.
8. Open Status & presets, choose a status and message. Save as preset without applying; confirm VRChat status is unchanged. Then load and apply the preset, accept confirmation, and verify in VRChat.
9. Confirm verification invite extraction, notification actions, personal notes, world/group browsing, paginated friends, in-place updates and background player tracking still work.

## Limitations
- Extended favorites are **device-local**, like VRCX's local favorites. They are not VRChat's own additional server slots and do not automatically sync to another phone or a desktop VRCX installation.
- Libraries are scoped to a personal VRChat account, but app removal/clear data erases the local database. An import/export feature could be added later for portability.
- VRC+ status depends on the VRChat API's `system_supporter` tag, matching established VRCX behavior. If VRChat changes entitlement reporting, fail closed and update the checker rather than treating failed checks as subscribed.
- Android CI verifies compilation and package signing; changing avatar/status and VRC+ membership requires real-account testing before claiming compatibility.
