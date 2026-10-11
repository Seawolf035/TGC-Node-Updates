# TGC Node Updates

Public update files for the TGC distributed invite network and verified Staff Launcher.

The authoritative update feed is `manifest.json`. The standalone node, plugin node, Staff Launcher, and central launcher read it directly from GitHub. Apps Script is no longer used to publish release metadata.

## Current packages

### Standalone Node 1.1.10
- File: `updates/standalone/TGC_Invite_Node_v1.1.10_Required_Update.zip`
- SHA-256: `981bec830dc04fe81caa88526499ce4884d22c01419858a513f1d6425a29f607`
- Direct download: https://raw.githubusercontent.com/Seawolf035/TGC-Node-Updates/main/updates/standalone/TGC_Invite_Node_v1.1.10_Required_Update.zip
- Adds owner-issued one-link deployment and Windows DPAPI protection for the standalone Node Key.

- REQUIRED: Quick Deployment only; manual enrollment has been removed.

- REQUIRED v1.1.10 update: removes Link Discord, retains Quick Deployment attribution, and adds future hotfix-revision support. The version bump enables automatic updates from v1.1.7.

- REQUIRED v1.1.10: built-in Diagnose Tracking report, launched in background, with privacy-safe summary saved to Downloads.

- REQUIRED 1.1.10 compatibility hotfix (revision 1): diagnostic is embedded in the previously supported runtime files, so 1.1.8 installations can self-update normally. A 1.1.10 installation that already failed to start may require manual repair.

- REQUIRED: automatic VRChat logging-disabled warning (60-second grace; clears on recovery).

### Plugin Node 1.1.10-plugin
- File: `updates/plugin/TGC_Invite_Node_Plugin_v1.1.10-plugin_Required_Update.zip`
- SHA-256: `9e96872a0e1c6e4371be8a0013c3ab5f6e060c6500a612006e068b832e9a8402`
- Direct download: https://raw.githubusercontent.com/Seawolf035/TGC-Node-Updates/main/updates/plugin/TGC_Invite_Node_Plugin_v1.1.10-plugin_Required_Update.zip
- Adds owner-issued one-link deployment while retaining the parent Auto Invite app's encrypted Node Key storage.

- REQUIRED: Quick Deployment only; manual enrollment has been removed.

- REQUIRED v1.1.8 update: removes Link Discord, retains Quick Deployment attribution, and adds future hotfix-revision support. The version bump enables automatic updates from v1.1.7.

- REQUIRED v1.1.9: built-in Diagnose Tracking report, launched in background, with privacy-safe summary saved to Downloads.

- REQUIRED 1.1.9 compatibility hotfix (revision 1): diagnostic is embedded in the previously supported runtime files, so 1.1.8 installations can self-update normally. A 1.1.9 installation that already failed to start may require manual repair.

- REQUIRED: automatic VRChat logging-disabled warning (60-second grace; clears on recovery).

### Staff Launcher 1.1.5
- File: `updates/staff-launcher/TGC_Staff_Launcher_v1.1.5_Required_Logging_Warning.zip`
- SHA-256: `abaa6d04d14f89e3a0861a6349966b969f328e032d04971d380bd40b7f785069`
- Direct download: https://raw.githubusercontent.com/Seawolf035/TGC-Node-Updates/main/updates/staff-launcher/TGC_Staff_Launcher_v1.1.5_Required_Logging_Warning.zip
- Fixes the embedded Node relay queue handling: relay responses return an accepted count plus `accepted_event_ids`, which is the list used to acknowledge queued sightings.
- Preserves idempotent retries for previously accepted events and clears acknowledged entries locally.
- Retains the v1.1.2 option to run exactly one of three local VRChat slots in VR, with the others forced to Desktop mode using `--no-vr`.
- Keeps verified-only GitHub self-updates and preserves launcher settings, deployment credentials, Windows DPAPI Node Key, and Invite Node queue database.

- Installer hotfix prefers Python 3.13 and uses a local virtual environment.

- Windows installer offers to install missing Python 3.13 automatically through winget.

- REQUIRED v1.1.4: the embedded Invite Node gains Diagnose Tracking; the v1.1.3 updater's fixed-file allowlist is fully supported.

- REQUIRED: embedded Node automatically warns when VRChat output logging appears disabled.

The plugin package is intended for Seawolf035's heavily expanded fork/build of TitanBCXR/vrchat-auto-invite, not as a guarantee of compatibility with an untouched upstream install.
