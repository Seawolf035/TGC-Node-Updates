# TGC Node Updates

Public update files for the TGC distributed invite network and verified Staff Launcher.

The authoritative update feed is `manifest.json`. The standalone node, plugin node, Staff Launcher, and central launcher read it directly from GitHub. Apps Script is no longer used to publish release metadata.

## Current packages

### Standalone Node 1.1.6
- File: `updates/standalone/TGC_Invite_Node_v1.1.6_One_Link_Deployment.zip`
- SHA-256: `e71a5cc69ca96c70df41705429b26289a9b8ac338130b8cc9172d52923e4ca02`
- Direct download: https://raw.githubusercontent.com/Seawolf035/TGC-Node-Updates/main/updates/standalone/TGC_Invite_Node_v1.1.6_One_Link_Deployment.zip
- Adds owner-issued one-link deployment and Windows DPAPI protection for the standalone Node Key.

### Plugin Node 1.1.6-plugin
- File: `updates/plugin/TGC_Invite_Node_Plugin_v1.1.6-plugin_One_Link_Deployment.zip`
- SHA-256: `3e1edc731a6104abae3ee54b77d2af1c2a0255dbfaeca638e23382c0c3cb1129`
- Direct download: https://raw.githubusercontent.com/Seawolf035/TGC-Node-Updates/main/updates/plugin/TGC_Invite_Node_Plugin_v1.1.6-plugin_One_Link_Deployment.zip
- Adds owner-issued one-link deployment while retaining the parent Auto Invite app's encrypted Node Key storage.

### Staff Launcher 1.1.3
- File: `updates/staff-launcher/TGC_Staff_Launcher_v1.1.3_Relay_Queue_Fix.zip`
- SHA-256: `f74d332c18100862b8187344551b2b7dc5286e3a8dac34ba2472e234d0730c5b`
- Direct download: https://raw.githubusercontent.com/Seawolf035/TGC-Node-Updates/main/updates/staff-launcher/TGC_Staff_Launcher_v1.1.3_Relay_Queue_Fix.zip
- Fixes the embedded Node relay queue handling: relay responses return an accepted count plus `accepted_event_ids`, which is the list used to acknowledge queued sightings.
- Preserves idempotent retries for previously accepted events and clears acknowledged entries locally.
- Retains the v1.1.2 option to run exactly one of three local VRChat slots in VR, with the others forced to Desktop mode using `--no-vr`.
- Keeps verified-only GitHub self-updates and preserves launcher settings, deployment credentials, Windows DPAPI Node Key, and Invite Node queue database.

The plugin package is intended for Seawolf035's heavily expanded fork/build of TitanBCXR/vrchat-auto-invite, not as a guarantee of compatibility with an untouched upstream install.
