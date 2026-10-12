# TGC Staff Companion v0.16.2 — Local VRChat Log Tools + Shizuku Explanation

Prepared 2026-10-12. Source-level acceptance checklist; real-device testing is **still required**.

## User-facing explanation
- **Shizuku is optional:** Friends, groups, profiles, notifications, avatars, worlds, searches, and the personal VRChat account operate without Shizuku or playing VRChat Mobile on the device.
- Shizuku is used **only** to give the existing Mobile Node read-only access to VRChat Mobile log files.
- The log data serves **two distinct purposes**:
  1. **On-device Companion features**: observed current-instance players, join/leave timeline, encounter history, world/instance transitions and enhanced activity feed.
  2. **The TGC network**: the existing Node join-sighting records may be uploaded to its Relay. The complete raw logs, leave events and instance-change events are **not** uploaded by the new feature.
- The personal VRChat account and its login/session remain distinct from Node deployment and Shizuku authorization.
- Quest player-log support is **future research** and is not included. Shizuku should not be presented as a confirmed Quest solution.

## Implementation
- The **existing** `PrivilegedProbe` watcher retains its file offset logic, single read loop, and existing join-only sightings queue.
- Shizuku's AIDL interface adds `pendingActivity` and `ackActivity` for a separate local queue containing `join`, `leave`, and `instance` events. Events are ACKed only after durable SQLite writes, with event-ID deduplication.
- `MobileOutbox` upgrades its SQLite schema from **3 to 4** without deleting previously captured encounters, unsent Relay events or the existing local join-sighting history.
- New bounded local-only tables: `local_activity` (30-day / 5,000-event retention) and `active_players` (most recently observed joins, removed on leaves and observed instance changes).
- `MobileNodeService` saves local activity while retaining the independent Relay upload loop. Local leave and instance events never enter the Relay's `submit_sightings` payload.
- Staff-protected `PlayerActivityLogActivity` offers **Players**, **Join/Leave** and **Encounters** pages with searching, timestamps, profiles, refresh and an explicit completeness caveat.
- The same current-player and join/leave pages are available **inside floating tools** without launching an Activity. The existing combined Activity Feed can show the new local events.
- Stopping monitoring doesn't remove history; unlinking the phone explicitly warns that its local player history is cleared along with Node setup.

## Android / VRChat Mobile device test plan
1. In-place install over v0.16.1; confirm personal VRChat session, staff link, unsent Relay queue, previous encounters and previous Friend Log all remain.
2. Open account features with **Shizuku not running** and with no VRChat Mobile installed. Verify these tools continue to work normally.
3. Verify the Tracking setup explanation clearly differentiates optional Shizuku, on-device log features, optional TGC sharing, and future Quest research.
4. Enable monitoring and join a VRChat Mobile world. Have one player join and another leave; compare event order, profile names, timestamps, current player list, overlay list, and Activity Feed.
5. Verify the observed-player list does **not claim to include everyone** when monitoring starts mid-instance. Leave event removes a player from the observed list; instance entry clears the old observed roster.
6. Stop the Node. History stays searchable, the roster is labeled **not live**. Restart and confirm it starts a new observed-player session without inventing prior joins.
7. Lose Wi-Fi or the Relay connection while monitoring. Local events and join sightings must still persist; successful later uploads must remove only the Relay queue, not local history.
8. Simulate/retry duplicate AIDL responses and log-file rotations. SQLite should deduplicate by unique event ID and avoid double-counting sightings or corrupting the active player list.
9. Confirm the Relay continues to receive only its pre-existing **join-sighting schema**, with no raw log, leave event, instance event, personal cookie or account password.
10. Swipe across Companion main tabs and open/close the floating bubble during a live VRChat Mobile instance. The single Node watcher continues without spawning another privileged reader.
11. Test 30-day / 5,000-event retention with many events, and prior SQLite schema upgrades. Verify data remain bounded and searchable.
12. Force a staff-access revocation and verify the new Activity and overlay pages close/lock as expected.
13. Test Quest messaging: no Quest tracking or Quest Shizuku support is incorrectly advertised.
14. Confirm signed release certificate continuity, versionCode **29**, v0.16.2 manifest, SHA-256 and updater URL.

## Known constraints
- The local roster is **observed since tracking started**, not a guaranteed complete or authoritative instance roster.
- Not all VRChat Mobile log builds are guaranteed to emit the same event formats; unknown/missing lines cannot be reconstructed.
- A file created before tracking starts is read from its end to avoid importing old log lines as new events.
- A source-specific Quest event provider will require separate feasibility and permission work; this release does not modify or access Quest devices.
