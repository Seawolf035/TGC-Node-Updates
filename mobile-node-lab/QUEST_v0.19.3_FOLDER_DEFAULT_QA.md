# Quest-first folder tracking v0.19.3 — QA and upgrade notes

## Platform defaults
- **Quest 2 / Quest 3 / Quest 3S (if detected as Quest):** persisted read-only SAF access to VRChat's `Documents/Logs` is the default tracking source. No Shizuku is needed **for this mode**.
- **Android phones running VRChat Mobile:** existing Shizuku tracker is still REQUIRED for player tracking. Other Companion functions do not require Shizuku.
- **Quest fallback:** Tracking → Tracking controls → Advanced tools → Alternative Quest method → Switch Quest tracking to Shizuku. The subpanel is collapsed until requested.
- Shizuku is NEVER automatically activated when folder permission is unavailable. No data is cleared when switching modes.

## Upgrade / migration
- v0.19.2 Quest folder users retain their permission and mode.
- Quest users with no saved selection are now offered folder access and must grant `Documents/Logs` before tracking begins. On an automatic service restart without the grant, service stops with a clear permission-required status instead of silently switching to Shizuku or running without a source.
- Quest users who explicitly chose Shizuku retain that choice until they choose folder mode. Switching modes requires stopping tracking first.
- A failed or canceled picker leaves an explicitly selected Shizuku mode unchanged.
- No change to existing staff enrollment, durable SQLite records, role checks, Relay routing or GM1/GM4 invite authority.

## Test plan
1. **New Quest setup:** with Shizuku uninstalled/stopped, link device, open Tracking controls. Confirm folder access is recommended, Shizuku not shown until Advanced → Alternative. Tap Start tracking; absent permission must prompt the Android tree picker (not ask for Shizuku).
2. Grant **Documents/Logs**, set VRChat Debug → Full; start tracking before entering a busy instance. Check watcher polls/bytes/joins and Relay accepts sightings.
3. Restart Companion, restart headset, rotate output_log files, enter a new instance, test sleep/wake and background persistence.
4. **Existing folder-mode Quest:** upgrade from 0.19.2 and ensure the selected folder and tracker still work.
5. **Existing Shizuku Quest:** upgrade, ensure it stays on Shizuku until user manually switches to folder; verify no silent switch or loss of staff enrollment.
6. **Quest fallback:** stop tracking → Advanced → Alternative → Shizuku → start. Reverse with Switch back to folder tracking; verify both paths work without duplicate historical sightings.
7. **Android phone:** must still request/check Shizuku to start tracking and must never offer folder permission as a phone tracking source.
8. Validate staff-only restrictions, independent local activity vs Relay sightings, no raw logs uploaded and no sensitive logs checked into GitHub.

## Release
VersionName 0.19.3 / versionCode 55. Keep existing permanent signing identity and minimum upgrade checks. The signed workflow publishes the APK/update JSON ONLY after merge into the experimental release branch.
