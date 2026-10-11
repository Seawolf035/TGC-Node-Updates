# TGC Node Updates

Public update files for the TGC distributed invite network and verified Staff Launcher.

The authoritative update feed is `manifest.json`. The standalone node, plugin node, Staff Launcher, and central launcher read it directly from GitHub. Apps Script is no longer used to publish release metadata.

## Current packages

### Standalone Node 1.1.7
- File: `updates/standalone/TGC_Invite_Node_v1.1.7_Link_Discord_Removal_Hotfix.zip`
- SHA-256: `2025e76907f9623ac7639a9c625ecf86bc9dcde83f9c21aa743d7d9f81380a24`
- Direct download: https://raw.githubusercontent.com/Seawolf035/TGC-Node-Updates/main/updates/standalone/TGC_Invite_Node_v1.1.7_Link_Discord_Removal_Hotfix.zip
- Adds owner-issued one-link deployment and Windows DPAPI protection for the standalone Node Key.

- REQUIRED: Quick Deployment only; manual enrollment has been removed.

- REQUIRED v1.1.7 hotfix: removes Link Discord; Quick Deployment links staff automatically. Existing v1.1.7 must be manually replaced once because the old updater only compares version numbers. Future hotfix revisions are supported.

### Plugin Node 1.1.7-plugin
- File: `updates/plugin/TGC_Invite_Node_Plugin_v1.1.7-plugin_Link_Discord_Removal_Hotfix.zip`
- SHA-256: `761f6e1ccd7ba83f860607c5e2703e4e2d1b1dc631e8c3d61c090d7cc92f01df`
- Direct download: https://raw.githubusercontent.com/Seawolf035/TGC-Node-Updates/main/updates/plugin/TGC_Invite_Node_Plugin_v1.1.7-plugin_Link_Discord_Removal_Hotfix.zip
- Adds owner-issued one-link deployment while retaining the parent Auto Invite app's encrypted Node Key storage.

- REQUIRED: Quick Deployment only; manual enrollment has been removed.

- REQUIRED v1.1.7 hotfix: removes Link Discord; Quick Deployment links staff automatically. Existing v1.1.7 must be manually replaced once because the old updater only compares version numbers. Future hotfix revisions are supported.

### Staff Launcher 1.1.3
- File: `updates/staff-launcher/TGC_Staff_Launcher_v1.1.3_Auto_Install_Python_Windows.zip`
- SHA-256: `fe31cce1b269e4b6b398545eb20b713a740cbd1544d53d71bc1f537db3bc5e98`
- Direct download: https://raw.githubusercontent.com/Seawolf035/TGC-Node-Updates/main/updates/staff-launcher/TGC_Staff_Launcher_v1.1.3_Auto_Install_Python_Windows.zip
- Fixes the embedded Node relay queue handling: relay responses return an accepted count plus `accepted_event_ids`, which is the list used to acknowledge queued sightings.
- Preserves idempotent retries for previously accepted events and clears acknowledged entries locally.
- Retains the v1.1.2 option to run exactly one of three local VRChat slots in VR, with the others forced to Desktop mode using `--no-vr`.
- Keeps verified-only GitHub self-updates and preserves launcher settings, deployment credentials, Windows DPAPI Node Key, and Invite Node queue database.

- Installer hotfix prefers Python 3.13 and uses a local virtual environment.

- Windows installer offers to install missing Python 3.13 automatically through winget.

The plugin package is intended for Seawolf035's heavily expanded fork/build of TitanBCXR/vrchat-auto-invite, not as a guarantee of compatibility with an untouched upstream install.
