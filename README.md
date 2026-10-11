# TGC Node Updates

Public update files for the TGC distributed invite network and verified Staff Launcher.

The authoritative update feed is `manifest.json`. The standalone node, plugin node, Staff Launcher, and central launcher read it directly from GitHub. Apps Script is no longer used to publish release metadata.

## Current packages

### Standalone Node 1.1.9
- File: `updates/standalone/TGC_Invite_Node_v1.1.9_Required_Update.zip`
- SHA-256: `07db754be17e684af7a607379071b0a398b7fa8d7bee5b1f8d4d4540ba961a0e`
- Direct download: https://raw.githubusercontent.com/Seawolf035/TGC-Node-Updates/main/updates/standalone/TGC_Invite_Node_v1.1.9_Required_Update.zip
- Adds owner-issued one-link deployment and Windows DPAPI protection for the standalone Node Key.

- REQUIRED: Quick Deployment only; manual enrollment has been removed.

- REQUIRED v1.1.9 update: removes Link Discord, retains Quick Deployment attribution, and adds future hotfix-revision support. The version bump enables automatic updates from v1.1.7.

- REQUIRED v1.1.9: built-in Diagnose Tracking report, launched in background, with privacy-safe summary saved to Downloads.

- REQUIRED 1.1.9 compatibility hotfix (revision 1): diagnostic is embedded in the previously supported runtime files, so 1.1.8 installations can self-update normally. A 1.1.9 installation that already failed to start may require manual repair.

### Plugin Node 1.1.9-plugin
- File: `updates/plugin/TGC_Invite_Node_Plugin_v1.1.9-plugin_Required_Update.zip`
- SHA-256: `9c2e9f22bdf7afb6ceb87d0d810893a808eaffa3fbfa063cda1f7e68734b960a`
- Direct download: https://raw.githubusercontent.com/Seawolf035/TGC-Node-Updates/main/updates/plugin/TGC_Invite_Node_Plugin_v1.1.9-plugin_Required_Update.zip
- Adds owner-issued one-link deployment while retaining the parent Auto Invite app's encrypted Node Key storage.

- REQUIRED: Quick Deployment only; manual enrollment has been removed.

- REQUIRED v1.1.8 update: removes Link Discord, retains Quick Deployment attribution, and adds future hotfix-revision support. The version bump enables automatic updates from v1.1.7.

- REQUIRED v1.1.9: built-in Diagnose Tracking report, launched in background, with privacy-safe summary saved to Downloads.

- REQUIRED 1.1.9 compatibility hotfix (revision 1): diagnostic is embedded in the previously supported runtime files, so 1.1.8 installations can self-update normally. A 1.1.9 installation that already failed to start may require manual repair.

### Staff Launcher 1.1.4
- File: `updates/staff-launcher/TGC_Staff_Launcher_v1.1.4_Required_Node_Diagnostics.zip`
- SHA-256: `11d7b07ac57fc7a502d1f0b38fdc8439732584553f35ea27f5a3a002a64b7e07`
- Direct download: https://raw.githubusercontent.com/Seawolf035/TGC-Node-Updates/main/updates/staff-launcher/TGC_Staff_Launcher_v1.1.4_Required_Node_Diagnostics.zip
- Fixes the embedded Node relay queue handling: relay responses return an accepted count plus `accepted_event_ids`, which is the list used to acknowledge queued sightings.
- Preserves idempotent retries for previously accepted events and clears acknowledged entries locally.
- Retains the v1.1.2 option to run exactly one of three local VRChat slots in VR, with the others forced to Desktop mode using `--no-vr`.
- Keeps verified-only GitHub self-updates and preserves launcher settings, deployment credentials, Windows DPAPI Node Key, and Invite Node queue database.

- Installer hotfix prefers Python 3.13 and uses a local virtual environment.

- Windows installer offers to install missing Python 3.13 automatically through winget.

- REQUIRED v1.1.4: the embedded Invite Node gains Diagnose Tracking; the v1.1.3 updater's fixed-file allowlist is fully supported.

The plugin package is intended for Seawolf035's heavily expanded fork/build of TitanBCXR/vrchat-auto-invite, not as a guarantee of compatibility with an untouched upstream install.
