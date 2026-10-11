# TGC Staff Companion v0.6 — staff-gated VRChat account preview

The Companion is no longer publicly usable just because its APK is installed. It starts on a locked screen. Only an active, owner-issued `mobile_companion` node deployment confirmed against the live TGC relay `node_status` can unlock the main interface. The app revalidates about every 45 seconds while on the main screen; authorization grants are process-local and expire after 90 seconds. Failed requests/revoked staff **fail closed**.

## Staff onboarding

1. Owner generates a **Mobile Companion** deployment from Main Launcher v2.2.7.
2. Staff install the APK and tap **Redeem Quick Deployment link**.
3. Staff redeem their one-use setup link. The TGC relay validates the staff roster and issues credentials.
4. The app re-checks deployment status online and unlocks the native Companion.
5. Staff may optionally sign into their **own, personal VRChat account** for read-only API features; personal VRChat login is entirely separate from the Node identity.

Revocation in Main Launcher disables relay authorization, causing the Companion to lock at its next online check. Offline use of restricted screens is **not** supported; this is intentional.

## What is functional

- Encrypted staff deployment key via Android Keystore and existing relay; separate Shizuku foreground log watcher, offline SQLite queue and GM4/GM1 pipeline preserved
- Separate VRChat account sign-in through `https://api.vrchat.cloud/api/1`: username/password used transiently in direct HTTPS request, 2FA authenticator/email/recovery support; password never saved, session cookies encrypted with Android Keystore in their own store
- Personal VRChat online/offline friends via `/auth/user/friends`
- Favorite worlds `/worlds/favorites` and recent worlds `/worlds/recent`
- Joined, publicly visible groups via `/users/{userId}/groups`
- Recently encountered player history, stored only on-device for 30 days (max 500 unique players); populated through Shizuku join events
- Tapping a friend/world/group/encounter opens their official VRChat profile in the browser
- Existing background Node status, diagnostics, and app updater

## What's NOT implemented yet

This is the first VRCX-style foundation, **not feature parity with VRCX**. We still need a tested personal-account instance dashboard, native friends search/details, avatar lists, favorite editing, invite tools, notifications, real-time socket events, group moderation per the staff member's own permissions, UX polish, and comprehensive Android device tests. We do **not** reuse VRCX sources or claim VRChat endorsement.

## Safety

VRChat warns that third-party apps asking for account credentials carry risks. Each staff member should choose whether to use native login. The Companion sends their password **only to VRChat's API endpoint**, never to the TGC relay or Discord. They should use 2FA and avoid account sharing. Staff deployment keys and VRChat cookies use different encryption keys and storage; neither appears in diagnostics or telemetry. The app limits API requests; read-only views require an explicit refresh.

## Testing

- Without staff deployment: app shows only access screen, Quick Deployment, and updater
- Successful deployment: main screens unlock after live relay verification; without a connection, fail closed
- Revoked deployment: verify failure locks screen even if encrypted node credentials remain stored
- Log in to **personal** VRChat account and complete 2FA if required; verify displayed username matches account
- Check friends/worlds/groups and one populated instance's local recent encounters
- Leave VRChat running and check Node counters/queued events; ensure background tracking is unaffected
- Logout personal VRChat: the Node and staff deployment should remain linked
- Check `Update` only with a stable release-signed APK; debug signing changes require reinstall and re-deployment

**Do not share raw auth cookies, VRChat passwords, 2FA/recovery codes, signed private APK keys, or staff deployment links in bug reports.**
