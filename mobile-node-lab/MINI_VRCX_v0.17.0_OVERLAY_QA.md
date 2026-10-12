# Staff Companion v0.17.0 — Floating Status Picker & Expanded Discovery

Prepared 2026-10-12. This describes the implemented source and remaining **real-device validation**, not a claim the interactions have been tested in VRChat Mobile.

## User-facing improvements

### Floating VRChat status
- The floating panel's **Tools** tab shows the signed-in personal VRChat account's current status and a prominent **Change status or message** button. The floating header's status label opens the same screen.
- Status choice is now a **clear color-coded two-column selector**: green Online, blue Join Me, yellow Ask Me, red Busy, and gray Offline.
- Current remote status appears at the top. Selected status is visually highlighted; selecting does **not** immediately send an update.
- Edit the 32-character status message, then tap **Apply status** once; an inline confirmation appears immediately by the action.
- The app checks that the VRChat account is unchanged before updating the status through the existing personal VRChat API. The bubble's presence dot updates immediately **after a successful response**, instead of waiting for its next scheduled poll.
- **Saved status presets** load from the full Companion's existing account-specific storage, `tgc_vrcx_presets/status_presets_<account ID>`. Staff can load or save a preset in the floating panel without leaving VRChat; newly saved presets appear immediately. Only Apply status sends changes to VRChat.

### Discovery & activity without app switching
- **Find anything:** one in-overlay search with **People**, **Worlds** and **Seen here** modes. People/world searches call the existing authenticated VRChat API only after pressing Search; Seen here searches the local encounter summary and works offline.
- **Recent worlds:** inspect your personal VRChat account's recently visited worlds, then open world details or search elsewhere.
- **Your instance:** view the **most recently observed** world/instance from existing local log history, the currently observed player count, copy the location, open world details, request a self-invite with confirmation, or jump to current players and join/leave history. This is not guaranteed real-time or complete.
- The floating Home, Social, and Tools panels expose these new pages as direct shortcuts; all render inside the overlay.
- Existing Shizuku watcher, join/leave history, Node, Relay, staff access checks, verification codes and notifications are unchanged. Personal VRChat cookies are not sent to Relay.

## Device acceptance tests (not yet completed)
1. Upgrade signed v0.16.5 in place to v0.17.0, preserving existing setup, app ID, sessions, Node database, offline queue, friend history and overlay position.
2. In VRChat Mobile, open floater → Tools → **Change status or message**. Confirm colored choices with a clear selected state and current status label. Open the same screen by tapping the floating panel's status label.
3. Select a status: **no server change until Apply status and Confirm change**. Cancel or leave screen without updating; previous VRChat status remains.
4. Apply each of Online, Join Me, Ask Me, Busy and Offline. Verify status description limits, server confirmation, bubble-dot update, error handling, and colors. Confirm an API error does not falsely update the dot.
5. Load a saved status preset from the full Companion, then use Apply. Save a new preset and confirm it is immediately available in the overlay and subsequently in the full Companion. Presets are separated by personal VRChat account.
6. Open **Find anything** inside the floater and search a person. Switch to Worlds and search; inspect a world detail. Switch to Seen here with VRChat offline; verify locally captured players remain searchable. Test keyboard focus and Back navigation without opening the full app.
7. Open **Recent worlds** and **Your instance**. If no world has been observed locally, show a useful empty state. If tracking began in the middle of an instance, the view must not falsely claim the observed roster is complete.
8. Copy a known instance location and check the clipboard. Test the explicit confirmation and permission checks for requesting an invite.
9. Close the floating panel and continue playing VRChat. The existing Mobile Node must continue tracking with no second log reader or restart. Pausing the Node should not affect the dot's status.
10. Force network failures, staff-access revocation, VRChat session logout, private world restrictions, and screen rotation. Verify the error messages and no unintended account updates.
11. Verify compiled debug APK, stable-key signed release, certificate equality with v0.16.5, versionCode 33, versionName 0.17.0, manifest SHA-256 and in-place update.

## Scope boundaries
- Search is intentional and request-based; the overlay does not continuously search online people or worlds in the background.
- The most recent instance comes from locally observed logs, not from private VRChat location data that the app cannot access.
- New presets remain local to the signed-in account and device. Cross-device preset sync and export/backup remain roadmap items.
- Ongoing Quest log-source research is separate and is not delivered in v0.17.0.
