# TGC Staff Companion v0.17.4 — Avatar Favorite Lists + Official Shizuku Links

Prepared 2026-10-12. Source implementation plus device acceptance checks; **live authenticated VRChat testing remains necessary**.

## Avatar favorites: why lists were missing
- Before: the native Favorite avatars screen requested `GET /avatars/favorites?n=60&offset=...` and presented a **flat feed**, while the floating avatar view only requested its first **60** results.
- Now: `AvatarFavoritesRepository` obtains the personal account's **actual server-provided avatar favorite groups** using `GET /favorites/groups/avatar`, with legacy fallback to `GET /favorite/groups?type=avatar`.
- For each returned group, retrieve `GET /favorites/groups/avatar/{name}`. This API's `favorites` entries contain nested `avatar` objects, which are displayed directly; a favorite ID can provide a placeholder when avatar metadata isn't supplied.
- Never assume that the account has only four or six lists; use actual group names and labels returned by VRChat (including VRC+ groups and renamed lists).
- The native avatar gallery has an **All favorite lists** selector with actual group names and counts. Searching filters locally over the selected group, or unique avatar IDs across all retrieved groups. Existing avatar details, avatar selection confirmation and local Extended collections remain separate.
- The floating-tools avatar screen has matching group selection and a deduplicated **All avatar lists** view, including Search, Select avatar, Copy ID and Show more.
- Failed individual lists are explicitly labeled **unavailable**; avoid representing them as empty. Partial-load warnings are shown and other groups remain browsable.
- If VRChat's group discovery endpoints are both unavailable, fall back to a bounded paginated legacy avatars feed, **clearly labeled a compatibility view**, rather than claiming every group loaded.
- This feature uses the existing personal-account VRChat session only: no Shizuku, Node, Relay or third-party group credentials.

## Shizuku download links
- Tracking setup's folded optional help card now contains a **Download Shizuku • official website** button which opens `https://shizuku.rikka.app/download/`.
- Expanded help also has **How to set up Shizuku**, opening `https://shizuku.rikka.app/guide/setup/`.
- Neither link installs any app without user action; the Companion remains fully useful for personal-account features without Shizuku.
- Shizuku is still used only for the separate Mobile Node's VRChat Mobile log-reading system.

## Acceptance checklist — real Android / VRChat login testing
1. Install signed v0.17.4 over v0.17.3 (versionCode 37); confirm no logout, Node reset, history loss, or changed signing certificate.
2. On an account with multiple avatar favorites lists, including the standard and VRC+ lists, open **Favorite avatars**. Tap the list selector and confirm **every list that VRChat reports** appears with its proper display name and avatar count.
3. Change lists and inspect avatars. Check that all lists display their own contents, including avatars beyond the first 60 of the previous flat feed.
4. Select **All favorite lists** and verify duplicate avatar IDs across lists are shown only once, without removing those entries from their individual lists.
5. Use Search inside a selected list and All. Refresh; ensure selections persist when the list still exists and reset to All otherwise.
6. Open VRChat Mobile with floating tools. Navigate to Favorite avatars; verify each remote list, Select avatar confirmation, Copy avatar ID, and Show more, without opening the full Companion.
7. Disable the group endpoint or simulate an HTTP error: partial/unavailable groups should be reported honestly; older flat-list fallback should never claim it is the definitive group view.
8. Log in with no favorites or without a VRC+ subscription; display whatever lists VRChat actually returns and keep local Extended favorites separate.
9. Open **Tracking → Download Shizuku** while help is collapsed; confirm it opens the real official website. Expand help and test Setup guide. Without Shizuku installed, confirm regular avatar, friend, world and notification tools work.
10. Confirm debug compilation, permanent signing-certificate comparison, published v0.17.4 APK, updated manifest SHA-256, versionCode **37**, and in-place upgrade.

## Known limits
- VRChat may restrict access to individual favorite list contents; inaccessible lists are shown as partial rather than quietly dropped.
- The total of avatar IDs is the sum of successfully loaded unique avatars, not a claim that inaccessible private avatars were retrieved.
- This update does not sync or create VRChat favorite lists, nor overwrite local Extended collections.
