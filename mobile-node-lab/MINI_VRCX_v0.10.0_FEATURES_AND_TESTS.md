# TGC Staff Companion v0.10.0 — Mini VRCX expanded features

This version is the first **VRChat account interaction** milestone. It is NOT full VRCX feature parity. It preserves the verified TGC staff gate, personal-account session, Shizuku watcher, mobile outbox, GM4/GM1 pipeline, invite-code reader and signed in-place updater.

## Available in v0.10.0

- **Native inbox**: read legacy and newer VRChat notifications on your personal account. Filter all, invites, and friend requests. A message opens its sender profile or associated instance as the API permits. Supports mark read, dismiss, and accept legacy friend requests, always requiring explicit taps (dismiss/accept require confirmation).
- **Native instance viewer**: follow a received world invite, fetch detailed instance information and its world, see display name, access/region and available capacity. Copy instance location, ask VRChat to invite your own account, or open the invite-friend picker.
- **Invite-friend picker**: personal-account friends list with local filtering. Choosing a friend prompts before sending the invitation. VRChat's permission checks still apply.
- **Profile actions**: send friend request or request invite on an eligible user profile after confirmation. Friend status is checked to conditionally present the appropriate action. Open a visible user's current instance.
- **Favorite avatars**: native personal favorite-avatar gallery, detail summary and confirmed avatar selection. No TGC/GM1 account access.
- Existing TGC staff invite code continues unchanged, including the working VRCX-standard `details.worldId` instance-name lookup.

## Safety and scope

All VRChat API requests go from the phone directly to VRChat using the signed-in user's encrypted session. No personal login or cookies are sent to the TGC relay. All account-changing actions require explicit confirmation and may be rejected by VRChat if the user lacks permission. The app cannot and does not circumvent private-instance restrictions, age gates or group roles.

## Still planned before full VRCX parity

1. Realtime socket/push presence and notification updates (rather than manual refresh)
2. Complete paginated friends, invites, groups and favorites, with search and sorting
3. User notes (private/local + VRChat user-notes sync where supported), favorite friend groups and mute/block controls
4. Native editable profile/status/bio, account settings and avatar management beyond favorites
5. World instance browsing, creation and advanced invitation/message slots
6. Native group members, roles, calendar/events, galleries and authorized moderation actions
7. Better offline caching, incremental rendering, API rate-limiting and test coverage

This is a major feature wave, not a claim of complete parity with desktop VRCX.

## Phone regression test

1. **In-place update** over stable-key v0.9.3 — do not uninstall. Verify TGC staff status, personal VRChat login and background Node still work.
2. Home -> Your inbox; test All, Invites, Friend requests. V1 and V2 APIs may have different permissions and response models.
3. Tap a received invite: use "View invited instance"; verify the instance display name and world page; optionally send **yourself** a fresh invite.
4. If comfortable, test sending an invite to one consenting friend from the picker. Confirm it prompts before sending and only uses your own VRChat account.
5. Tap another user's profile; test friend-request and request-invite actions only with a consenting account.
6. Explore -> Favorite avatars: check thumbnails, detail descriptions and avatar select confirmation. Do not press Change Avatar unless you want it changed.
7. Home -> Find verification invite: confirm the prior TGC code extractor still works.
8. Leave background Player Tracking on while in VRChat Mobile, then check recent encounters and uploaded counts.
9. Verify Samsung system bar safe areas, scrolling, and that verification doesn't flicker locked/unlocked.

No private API responses, 2FA codes, VRChat cookies, or staff deployment keys should be posted in bug reports.
