# TGC Node Updates

Public update files for the TGC distributed invite network and verified Staff Launcher.

The authoritative update feed is `manifest.json`. The standalone node, plugin node, Staff Launcher, and central launcher read it directly from GitHub. Apps Script is no longer used to publish release metadata.

## Current packages

### Standalone Node 1.1.7
- File: `updates/standalone/TGC_Invite_Node_v1.1.7_Quick_Deployment_Only.zip`
- SHA-256: `fb2521d0d37bbe528f02013187ce95586129c9d37ab4b8a9225212eead26b729`
- Direct download: https://raw.githubusercontent.com/Seawolf035/TGC-Node-Updates/main/updates/standalone/TGC_Invite_Node_v1.1.7_Quick_Deployment_Only.zip
- Adds owner-issued one-link deployment and Windows DPAPI protection for the standalone Node Key.

- REQUIRED: Quick Deployment only; manual enrollment has been removed.

### Plugin Node 1.1.7-plugin
- File: `updates/plugin/TGC_Invite_Node_Plugin_v1.1.7-plugin_Quick_Deployment_Only.zip`
- SHA-256: `19093a3b343a1212d8ca74f0e372ea7c64c46cdd5d2430b699ac6bd72e2e6f3b`
- Direct download: https://raw.githubusercontent.com/Seawolf035/TGC-Node-Updates/main/updates/plugin/TGC_Invite_Node_Plugin_v1.1.7-plugin_Quick_Deployment_Only.zip
- Adds owner-issued one-link deployment while retaining the parent Auto Invite app's encrypted Node Key storage.

- REQUIRED: Quick Deployment only; manual enrollment has been removed.

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
