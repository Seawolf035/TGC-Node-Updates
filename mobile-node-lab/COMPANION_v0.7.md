# TGC Staff Companion 0.7 — Native VRChat navigation

## What changes

- Staff deployment remains mandatory. No personal-account feature can be opened without a currently valid TGC `mobile_companion` deployment.
- Personal VRChat account sign-in and 2FA remain separate from TGC Node credentials. No shared GM1 account.
- Tapping a friend, recently seen player, world or group from the Companion now opens a **native Android detail view**, not a web browser.
- Player details: status/status message, bio, pronouns, tags, join date and visible location; known world links lead to the in-app world profile.
- World details: description, creator profile, player count, capacity, favorites, visits, tags and public instance count when provided.
- Group details: description, member count, short code, visibility and available owner profile.
- Search for VRChat users by display name and VRChat worlds by name directly in the Companion, opening the same native detail screens.
- Your own VRChat profile can be opened in-app from the home screen or Account.
- Images are fetched without transmitting VRChat cookies to third-party servers and restricted to known VRChat-controlled HTTPS hosts. There is no embedded browser or third-party SDK.
- Existing Shizuku live Node, staff deployment, SQLite outbox, relay integration and updater have not been changed by the native detail UI.

## Constraints

The app retrieves data through VRChat's API using the *signed-in staff member's* own encrypted personal session. VRChat may hide profile/location details or deny group/world information depending on the account's permissions. The app displays those failures rather than attempting to bypass them.

This build makes the existing list-to-profile flow local to the Companion, but it is **not full VRCX feature parity**: friend requests and messaging, invite management, world/instance creation, avatars, notifications and privileged group moderation need separate implementation and testing.

## Testing on Android

1. Start TGC Staff Companion and let it verify your existing deployment with the relay.
2. Open **Online friends**, tap a friend, and verify that the player profile opens *inside the app*.
3. Open **Favorite worlds** and **My groups** and tap entries; verify native details and back navigation.
4. Open **Recently encountered** and tap a player from the Shizuku sighting history.
5. Try **Search players** and **Search worlds**, then tap a result.
6. Open **My VRChat profile** from Home.
7. Confirm the foreground Shizuku Node is still tracking, saving and uploading sightings. No new changes were made to the Node protocol.
8. Test staff revocation; if the relay rejects the node, native screens should lock when authorization expires.

**Signing:** Existing GitHub debug builds use ephemeral signing keys unless private release signing secrets are configured. You may need to uninstall an older preview, which removes the encrypted deployment and local Node queue. Consider keeping a working Node installed until permanent signing is configured.
