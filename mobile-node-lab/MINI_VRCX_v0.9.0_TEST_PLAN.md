# TGC Staff Companion v0.9.0 — Mini VRCX UI phase

## Visual design
- Native dark navy interface with teal accents; compact headers and list rows
- Android WindowInsets-based status bar, navigation bar and display cutout padding on all customer-facing screens; avoid overlap on Samsung/Android 15
- RecyclerView-backed friends, worlds and groups lists, with image thumbnails and real item counts instead of displaying at most 100
- Signed-in personal VRChat account remains independent of TGC staff deployment
- Personal login, 2FA, Shizuku Node background tracker, relay and updater remain as in v0.8.0

## Player profile
- Image hero, avatar icon, display name, pronouns and available status
- Full bio and bio links shown as selectable text, without opening a browser
- Presence and private/offline location respect VRChat privacy
- Local, device-only encounter count and last-seen timestamp
- Publicly visible user group links, if VRChat permits them
- Join date, last platform, last login when returned by API
- Copy profile ID, refresh; all related navigation stays inside the Companion

## World and group profiles
- Cover images, name, author, full description, player capacity/visits/favorites when returned
- World creator profile, published/updated and available platforms
- Group description, short code, member count, joining privacy, roles when returned
- Group owner profile, copy IDs; no untested moderation actions

## Layout and responsiveness
All major Android screens call CompanionStyle.safeArea; the insets include system bars and cutouts. RecyclerView lists allow smooth scrolling with large friend/group datasets and preserve Android navigation bar clearance. User and world image retrieval is HTTPS-only and limited to VRChat-owned domains, with limited redirect following and no auth cookies.

## Regression checklist on an actual phone
1. Update **in place from signed v0.8.0** through Settings → Check for updates. Confirm deployment and personal VRChat login retained.
2. Open Home and check logo/text/status icons do not overlap the clock or cutout.
3. Open Explore → Online Friends. Check truly offline entries are no longer shown as online; browse long list and bottom system navigation bar.
4. Open a friend; verify full bio, profile image when available, status and private/location behavior. Confirm local encounter history appears when recorded.
5. Open worlds and groups; verify artwork, descriptions, creator/owner links and list scrolling.
6. Browse groups with 100+ entries and verify ALL retrieved entries remain scrollable.
7. Try Search with high-contrast fields and Android keyboard, including landscape if supported.
8. Open Tracking, leave background Node on while playing VRChat; confirm sightings continue to be recorded and synced.
9. Open Settings → Advanced to access staff diagnostics. Confirm ordinary screens don't expose GM1/GM4 implementation details.
10. Allow the Companion to run for several minutes; confirm the staff verification lock/unlock loop is still fixed.

VRChat APIs apply account visibility constraints. Not every profile or world will expose every optional field. This version is Mini VRCX *profile/list improvements*, not full VRCX parity: invitations, group moderation, notifications, avatars and world instance management require additional implementation.
