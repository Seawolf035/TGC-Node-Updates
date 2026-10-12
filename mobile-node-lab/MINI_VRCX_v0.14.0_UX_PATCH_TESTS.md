# Staff Companion v0.14.0 — Social + Floating Overlay
Prepared: 2026-10-12. Development/test checklist; do not interpret as real-device signoff.

## Included changes
- Native Friends Locations: online friends grouped by the VRChat locations visible to the personal account. Private/unknown presence stays private/unknown.
- Persistent **Friend Log**: account-scoped, atomic local file with up to 5,000 observed online/offline, status, status-description, location, friend add/remove, and name-change events.
- First successful friend snapshot seeds the timeline; **no events are invented from before the app began observing**. Full online+offline pagination is required before storing a new snapshot.
- Social **Activity Feed** merges friend changes and locally retained captured Node player events.
- Mobile Node local history in SQLite schema v3, retaining up to 5,000 captured events or 30 days even after their separate Relay queue drains. Upgrade preserves v2 encounter summaries and unsent queue. The existing Node remains the only log reader.
- Optional, draggable floating TGC overlay with quick links to friend locations, log, activity, inbox, recent players, and Companion. Shows Node tracking status and recent captured event names. Separate service and notification; closing bubble must **not stop MobileNodeService**.
- Friend monitoring while Friends Hub is open or the optional overlay is active. The overlay samples the personal account about every 150 seconds (not push/websocket real-time); API errors retain the last complete snapshot. No personal VRChat credentials pass through Relay.
- Staff authorization is required for every new screen/service, and checked periodically. Existing Android package ID and Keystore-backed personal sessions are unchanged.

## Android real-device testing (not yet asserted complete)
1. Upgrade v0.13.1 signed APK to v0.14.0 in place: verify no uninstall, no logout, no lost Quick Deployment association, no lost local Node queue.
2. With authorized staff account, open **Explore → Friends Locations** and **Home → Friends Locations**. Confirm grouped locations and profile taps; private/unknown locations stay hidden. Try large friend lists and load counts.
3. Visit Friend Log: first refresh seeds without phantom events. Switch friend between online/offline, change status/location, then refresh; verify timestamp, search, filters, account separation, and only real observed changes.
4. Open Activity Feed: verify friend events and new Mobile Node sightings appear together and remain after Relay successfully uploads/clears queue. Previously completed uploads may not be reconstructable.
5. Deny overlay permission: correct Android system prompt; no crash. Grant overlay permission, enable bubble, launch VRChat Mobile, drag bubble, tap to expand, open each quick tool, and return to VRChat.
6. Confirm overlay floats over VRChat but is not shown on screens where Android restricts overlays. Verify compact panel scrolling on small phones, portrait rotations, and display cutouts.
7. Leave bubble running and VRChat logged in: observe new friend changes within sampling interval; dismissing the **panel** must not stop the bubble; **Close bubble** and notification Hide action must stop the overlay only.
8. Force-stop the Android overlay service / revoke staff access: bubble must disappear and protected screens must lock. An unauthorized Activity launch must immediately exit.
9. Disable Shizuku or network temporarily while tracking is active, then restore. Verify Node service stability, offline queue retention, deduplication, and later Relay uploads.
10. Log out of the personal VRChat account: social refresh shows a useful error, but Node/Quick Deployment stays independent. Log back into a different personal account: friend history is not mixed with the first account.
11. Verify no unbounded polling, duplicate entries on successive refreshes, battery drain, Android 13+ notifications permissions, or leaked cookies in diagnostics.
12. Check signed release certificate fingerprint against v0.13.1, strictly increasing versionCode, APK manifest SHA-256, updater URL, successful in-place installation.

## Known scope boundaries
- Observations are sampled while the Hub is open or optional overlay is enabled; this does **not** promise 24/7 cloud friend history, websocket real-time notifications, or events missed between polls.
- The overlay uses the existing Mobile Node database/status. It is **not** an independent log reader or second Shizuku client.
- Only locally captured Node events beginning with this schema upgrade have persistent post-upload event history. The new table does not fabricate earlier successfully uploaded events.
- The UI shows VRChat instance identifiers when no friendly world name is already available; a world metadata cache could improve this in a future update.
- The activity timeline uses personal VRChat friend data plus existing, authorized local device player events, not unrestricted private VRChat data.
