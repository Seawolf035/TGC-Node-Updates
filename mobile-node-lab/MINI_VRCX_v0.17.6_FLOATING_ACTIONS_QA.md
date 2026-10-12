# TGC Staff Companion v0.17.6 — Floating Tools: Avatar Picker and Quick Actions

Prepared 2026-10-12. This documents implemented source and the **remaining real-device acceptance tests**. A compiled signed APK does not prove successful live VRChat avatar switching.

## Main feature: Favorite Avatars, now a status-style in-overlay picker

- Open the floating panel's **Tools → Favorite avatars**. The page shows the currently active avatar returned by the signed-in personal VRChat account, where available.
- A compact **favorite-list selector** lets staff choose All or any actual VRChat favorite group, including server-reported VRC+ groups. Lists are collapsed by default rather than taking up the whole floating screen.
- Avatars display **thumbnail images**, avatar names, authors and (in All mode) their favorite-group names. All mode deduplicates by avatar ID.
- **Tap an avatar to preview and select it.** The selection is highlighted, with a larger avatar thumbnail, description and avatar ID. A selection **does not** change the VRChat account.
- The selected card has one **Apply selected avatar** control, followed by an inline **Confirm avatar change / Cancel** step, matching the new status-picker philosophy. It checks that the personal VRChat account hasn't changed before using the existing avatar-select API.
- Once VRChat confirms the action, the floating view updates its **Current avatar** indicator immediately without unnecessarily refetching all favorite groups. The selected avatar remains available for review.
- The optional **View full avatar details** action fetches more server metadata on demand. **Copy avatar ID** and **Clear selection** remain in the floating view.
- Favorited avatars are paginated in small groups (initially 18) to make mobile rendering and image loading easier. Searching by avatar name, ID or author still works. Selecting an avatar returns the panel scroll to the preview.
- Uses the existing bounded, HTTPS-only `CompanionImages` loader and account's existing remote avatar favorite groups. No external image library, new permission, or new credential storage.

## Additional floating-tool functions

- **Friends & locations** has **All / Online / Offline** filters, live status labels, a matching-friend count, **Show more friends** and direct confirmed self-invite actions where a friend's location is visible and valid.
- **Player profiles** now show supported VRChat profile imagery, status, friend relationship, bio, visible location and quicker actions for profiles, notes, friend requests and invite requests. Actions remain gated on existing permissions and confirmed where appropriate.
- **Inbox** now supports **Show more notifications** beyond its first 65 visible results, using the already-loaded deduplicated inbox data.
- All these tools remain native in the overlay; no switch to the full Companion is necessary.

## Device tests still required

1. Install signed v0.17.6 (versionCode **39**) over v0.17.5 (38). Confirm stored favorites, personal VRChat sign-in, Node logs/queue, pinned floating shortcuts, position, sizes and Quest opt-in stay unchanged.
2. Open VRChat Mobile, then floating panel → Tools → Favorite avatars. Confirm current avatar, favorite-list selector and thumbnails display correctly. Check each actual regular/VRC+ favorite group and the deduplicated All view.
3. Tap a different favorite avatar. Ensure VRChat remains unchanged; the selection panel opens at the top with **Not applied yet**. Tap Clear selection and verify no server mutation.
4. Tap Apply and then Cancel: VRChat remains unchanged. Tap Apply + Confirm: the VRChat API should change the selected avatar and the current-avatar preview should update immediately after success.
5. Try applying an avatar already in use. It must display **Currently in use** without an unnecessary Apply action.
6. Test avatar metadata fetch when VRChat permits it, and test a private/deleted/unavailable avatar. Display errors without switching avatars.
7. Search avatars by name, author, and ID; switch lists; expand Show more. Check image loading and panel scrolling on a small phone.
8. Sign out or change the personal VRChat account after loading favorites; the Apply command must refuse to affect another account and must require reloading data.
9. Under Friends & locations, check Online/Offline filtering, paginated results, and a confirmed invite to a visible instance; test a hidden/private location and a network error.
10. On a player profile, check image, friendship state and permitted friend-request/invite options. Confirm status and personal notes still work.
11. Under Notifications, ensure entries after number 65 are visible via Show more, and action confirmation/deduplication remain unchanged.
12. Check the bubble's VRChat presence color and status picker remain independent of tracking. Confirm Shizuku is not invoked by avatar or other personal account actions.
13. On Meta Quest, verify the floating tool remains disabled by default, and enabling it experimentally remains user-driven only; no new Quest overlay support is claimed.
14. Confirm Android debug compile, stable-key signed release, certificate continuity, published APK, updater JSON version **0.17.6**, code **39**, and SHA-256.

## Boundaries
- VRChat decides whether an avatar can be selected; removed, private, nonpermitted or incompatible avatar variants may fail. The floater must show an error instead of claiming success.
- The displayed current-avatar metadata depends on what the personal VRChat API returns; if unavailable, a neutral unavailable label is shown.
- Group moderation write actions, Quest VR overlays, and Shizuku/Quest log compatibility remain separate work.
