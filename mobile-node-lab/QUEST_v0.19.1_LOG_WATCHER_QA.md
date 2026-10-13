# v0.19.1 candidate — Quest Shizuku live log watcher QA

**Status:** SOURCE ONLY. Signed v0.19.0 (code 52) remains published and unchanged.

## Evidence from private Quest 2 logs (do not commit or publish raw files)

- Quest 2 on Android 14 with VRChat Full logging enabled.
- Shizuku probe read a recent `output_log_*.txt` under `/storage/emulated/0/Documents/Logs`.
- Supplied Quest log started at 08:14:50 and has **3** exact `OnPlayerJoined` events (2 distinct players, including local player), **1** exact `OnPlayerLeft` event and **2** `Joining wrld_...:<instance>` entries. All match current `LogParser` and `PrivilegedProbe` patterns. One event belongs to another player.
- The earlier log recorded `Logging Enabled set to: Full` after the session began; it had no exact join/leave/instance matches.
- This demonstrates read access and compatible log event syntax, but NOT successful concurrent continuous monitoring, remote Relay upload or an automatic invitation.
- Do not upload raw log files. They may contain user IDs, display names, and authentication-related diagnostics.

## Targeted change

- When `QuestDevice.isQuestDevice()`, `PrivilegedProbe.latestVrchatFile()` selects the newest read-only `output_log_*.txt` under `Documents/Logs`, falling back to existing Android app-private locations only if no Quest log exists.
- Ordinary Android phones continue using ONLY the original app-private locations.
- Retain the existing watcher: five-second polling, EOF-on-start to prevent re-import, log rotation, partial-line handling, Shizuku permission checks, local history queue, encrypted deployment, durable Relay outbox, Main Launcher GM4 eligibility and GM1 invite authority. No direct Quest invitations.
- Increment Shizuku UserService code version 4 → 5 so a previously running Shizuku worker is refreshed on an in-place upgrade. No protocol change.
- No changes to phone floater, signing keys, credential storage, Relay or Main Launcher.

## Test on Quest 2 after a future signed build

1. Confirm Full logging under VRChat Settings > Debug and grant the Companion Shizuku permission.
2. Start the Mobile Node watcher. Its status should say `Watching VRChat output logs: source found`, show active polls, and no historical join replay. Confirm the Shizuku service is refreshed after upgrading.
3. Remain in VRChat while a second player joins. Confirm the join count increments, one unique user is recorded, the log recency changes and the watcher stays alive in the background.
4. Leave/change instances, then have another player join. Confirm correct instance context, log-switch handling and no duplicates or false joins.
5. Check a valid staff deployment: offline queue persists across network outages, then the Relay acknowledges a queued sighting; GM4 membership/ban checks and GM1 remain the only invitation authority.
6. Test headset sleep/wake, VRChat suspend/resume, Shizuku disconnect/reconnect, and document log rollover. Validate no duplicate old sightings or lost unacknowledged events.
7. On ordinary Android, regression-test the original app-private log watcher and existing floating Home/Social/OSC/Tools/PTT. Do not use the Quest Documents path on phone.

## Limitations

The sample confirms a real player event in a readable log; it does not prove Quest permits the watcher process to stay alive concurrently with VRChat or that VRChat events are generated with Errors Only logging. No success claim until a real on-device join reaches Relay and the normal invitation pipeline.

## Release boundary

This candidate must remain on a feature branch until requested for release: then bump versionName and versionCode above 0.19.0/52, run debug CI, use the existing permanent signing certificate, verify upgrade compatibility, commit the release APK and matching SHA256 feed, and test without uninstall or data wipe. No production Main Launcher or Relay changes.
