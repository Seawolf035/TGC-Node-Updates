# Quest folder-access tracking test — v0.19.2

## Status and scope

Experimental **opt-in**, Quest-only, read-only SAF Documents/Logs watcher.
Shizuku remains the default and supported tracking mode. No phone watcher,
Quest native utility changes, Relay/GM4/GM1 changes or credential changes.

The system Android DocumentsUI picker is required; it may be absent or restricted
on Meta Quest. **Successful compilation cannot verify that Quest provides a
working folder picker, or that VRChat and our service run concurrently.**

## Setup

1. Install signed v0.19.2 over v0.19.1 (preserve staff enrollment/data).
2. On Quest, open VRChat > Quick Menu > Settings > Debug > Full.
3. Open TGC Companion > Tracking > Tracking controls.
4. Under **Quest log access — experimental**, tap
   "Try without Shizuku · choose Documents/Logs".
5. If the Android picker opens, navigate to **Internal storage > Documents > Logs**
   and grant access to **Logs**, not the Documents parent folder or storage root.
6. Confirm "Selected mode: Folder (no Shizuku)" and "Folder permission: Documents/Logs".
   Close/stop Shizuku entirely if you want to prove no Shizuku dependency.
7. Tap "Start tracking" **before** entering a VRChat instance.
8. Enter an instance while players join. After 2–5 min check Advanced diagnostics:
   tracking mode, source found, watcher polls, joins, bytes, read errors, Relay.
9. Verify Relay received real sightings and the existing GM4/GM1 pipeline remains unchanged.
10. Sleep/wake and switch instances. Recheck poll growth, new log rotation,
    timestamp changes, duplicate protection and background survival.

## Expected and fallback

- Picker unavailable / folder cannot be selected: **not supported on that headset**.
  Stop any experiment and select "Use Shizuku tracking (recommended)".
- Picker succeeds, polls=0: app/service was stopped or disconnected.
- Polls rise, source=false: provider cannot see log or permission was revoked.
- Polls rise, source=true, bytes=0 after joins: live provider might not surface
  growing files or watcher started after the joining players.
- Bytes rise, joins=0: inspect actual new output lines and parser.
- Joins rise, Relay unchanged: check durable local queue, authenticated staff
  deployment and Relay diagnostics.
- Folder grant read-only; no raw VRChat logs or user tokens are uploaded.
- Watcher starts at end of existing log to avoid replaying historical sightings.

## Rollback

Stop tracking, choose "Use Shizuku tracking (recommended)", then restart.
Never unlink, clear app data, or uninstall to switch modes.

## Security / release

No MANAGE_EXTERNAL_STORAGE; persist READ only on the explicit chosen folder.
Existing local SQLite queues and staff authentication are reused for both modes.
No raw logs or private device diagnostics may be committed to GitHub.
