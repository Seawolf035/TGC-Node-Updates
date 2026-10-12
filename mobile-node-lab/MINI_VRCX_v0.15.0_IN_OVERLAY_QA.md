# TGC Staff Companion v0.15.0 — Mini VRCX entirely inside VRChat Mobile

## Reason for release
Switching from VRChat Mobile to the full Android Companion can disconnect users or crash the game. The overlay must perform everyday tasks **inside the floating window**, not simply launch another Activity.

## Changes
- **Floating bubble:** retain the existing Companion `tgc_logo` resource, but clip its square image to a true circular frame with cyan ring and existing tracking-state dot.
- **Overlay navigation:** internal pages, Back button and Home / Social / Tools navigation, no Activity launch for routine pages.
- **Player log:** on-device player sightings, timestamps and encounter counts, captured Node events, player-profile drilldown.
- **Friends & locations:** fetch complete personal-account friend list and group visible online friends by world/instance. Hidden locations remain hidden.
- **Friend Log & Activity Feed:** account-scoped observed friend transitions plus locally retained Node events.
- **Notifications:** read legacy/modern VRChat inboxes, filter invites/friend requests, view sender profiles, copy invite locations, self-invite, mark seen, dismiss, accept supported friend requests, with inline confirmation for account-changing actions.
- **Player profiles:** bio/status/location, copy player ID or profile link, personal notes, supported friend request/invite-request actions.
- **Personal notes:** read VRChat personal user notes and save through the existing authenticated VRChat API.
- **User search:** keyboard-capable in-overlay search and profile drilldown.
- **Avatar favorites:** read favorites, copy IDs, and select an avatar with confirmation.
- **Worlds / groups:** browse and view details and copy IDs/links; world and instance invite requests where supported.
- **Presence:** change personal VRChat status and status message without switching applications.
- **Node controls:** previous 0.14.1 Start/Stop tracking and status remain, bound to the single existing MobileNodeService.
- **Keyboard focus:** overlay becomes temporarily focusable only while entering text and returns to nonfocusable afterward. UI doesn't launch an Activity to request the keyboard.
- **Privacy:** staff gate checks, personal VRChat session remains isolated from Relay, private note actions require confirmation, no new log reader or central tracking dependency.

## Device acceptance checklist — NOT yet verified on real hardware
1. Upgrade the **v0.14.1** signed install to v0.15.0 without uninstall, Quick Deployment reset, sign-in reset, or lost pending Relay events.
2. Verify circular floating app icon (not square over a circular border), position dragging, collapse, status dot and tracking toggle.
3. While physically inside a VRChat Mobile instance, navigate from the overlay Home to Player Log; search, open profile and return with **Back**. Check that VRChat continues running.
4. Test Social tab: full friends by visible locations, friend status changes, friend log, activity; reload and go Back without opening a full-screen Activity.
5. Open Inbox inside overlay, filter friend requests and invites, view sender, mark seen, dismiss, accept request and request self-invite. Confirm account-changing actions require explicit inline confirmation and failures are reported.
6. Search for users using Android keyboard. Confirm overlay keeps data, keyboard appears and closes, no full Activity appears and VRChat instance persists.
7. Edit personal VRChat status description and user note using in-overlay keyboard. Verify server changes and correct user account.
8. Select an avatar from in-overlay favorites, verify user confirmation and VRChat API response; browse world/group details and copy IDs.
9. Test offline/expired personal session: overlay displays error and preserves local Node function.
10. Revoke staff authorization: overlay closes; UI must not allow another action once revoked.
11. Verify existing Shizuku log monitoring and Relay upload queue unaffected by overlay pages or dismissal.
12. Verify Android 13–16 behavior, landscape/portrait resize, keyboard window focus and touch handling on Samsung/Pixel and small phones.
13. Verify signed APK certificate same as v0.14.1, versionCode 26, SHA-256 release manifest and one-step upgrade.

## Known limits
- Personal VRChat APIs may rate-limit or block actions; failures are surfaced rather than fabricated.
- Overlay data loads using personal VRChat API in background; friend snapshots are periodic, not a guaranteed real-time server feed.
- The player event stream displays the Node's currently retained captured events; historical events deleted before local persistence were introduced cannot be restored.
- Android may restrict overlay keyboard, focus or launching interactive controls over certain games. Real-device testing is required; building is not proof of in-game behavior.
- The full Companion still exists as an explicitly chosen fallback, but primary Mini VRCX features do not require opening it.
