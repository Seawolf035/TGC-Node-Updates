# TGC Node Updates

**License notice:** Original TGC Staff Companion code and artwork are proprietary and all rights are reserved, except for third-party material governed by its own licenses. The staff application's [license notice](PROPRIETARY_LICENSE.md) and [in-app agreement](mobile-node-lab/mobile/src/main/assets/tgc_companion_eula.txt) explain the restrictions and exceptions. Public source visibility does not make it an open-source release.

Downloads and update files for **The Gamer Club's VRChat invite network**.

The Nodes and Staff Launcher check [`manifest.json`](manifest.json) for new releases. This repository holds the update packages; the Main Launcher manages the network itself.

## Downloads

| Package | Version | Update | Download |
| --- | --- | --- | --- |
| Standalone Node | 1.1.10 | Required | [Download ZIP](https://raw.githubusercontent.com/Seawolf035/TGC-Node-Updates/main/updates/standalone/TGC_Invite_Node_v1.1.10_Required_Update.zip) |
| Plugin Node | 1.1.10-plugin | Required | [Download ZIP](https://raw.githubusercontent.com/Seawolf035/TGC-Node-Updates/main/updates/plugin/TGC_Invite_Node_Plugin_v1.1.10-plugin_Required_Update.zip) |
| Staff Launcher | 1.1.5 | Required | [Download ZIP](https://raw.githubusercontent.com/Seawolf035/TGC-Node-Updates/main/updates/staff-launcher/TGC_Staff_Launcher_v1.1.5_Required_Logging_Warning.zip) |

### Which one do I need?

- **Standalone Node:** Runs the invite-network Node on its own.
- **Plugin Node:** Adds Node support to our customized build of [TitanBCXR/vrchat-auto-invite](https://github.com/TitanBCXR/vrchat-auto-invite). It isn't intended as a drop-in plugin for the untouched upstream version.
- **Staff Launcher:** The staff tool with an embedded Invite Node and three local VRChat slots. One slot can run in VR; the others use Desktop mode.

## Setting up a Node

Nodes use **Quick Deployment**. The network owner issues a deployment link for a specific staff member; that link connects the Node and handles staff attribution. There's no separate **Link Discord** step and no need to enter relay credentials by hand.

Player tracking reads VRChat's output logs, so **VRChat logging must be enabled** on the hosting PC. If VRChat is running without usable logs, the Node warns you instead of silently showing zero players.

If tracking isn't working, click **Diagnose Tracking**. The report is saved to your Downloads folder and doesn't include player names, VRChat account IDs, or Node credentials.

## Updates

Each app checks GitHub for releases. When a newer version is available, use its update prompt or **Check for Updates** option to install it. Updates marked **Required** must be installed before normal relay uploads resume; sightings stay queued in the meantime.

The update feed includes version numbers, download URLs, SHA-256 checksums, and update policies: [view manifest.json](manifest.json).

**Already running a Node?** Update your existing installation. Don't delete its settings, encrypted Node key, or queue database, and don't generate a new deployment link just to update.

**Node won't start after the first 1.1.9 release?** An early package was missing a required diagnostic module. Download the current package above and replace the program files in the existing installation, keeping its settings and queue data.

## Current changes

- **Nodes 1.1.10:** Warn when VRChat is running but isn't writing usable output logs. The warning clears when logging resumes.
- **Nodes 1.1.9:** Added the built-in tracking diagnostic.
- **Nodes 1.1.8:** Removed the redundant Discord-link button and added support for future hotfix revisions.
- **Staff Launcher 1.1.5:** Added the same logging warning to its embedded Node. Staff Launcher 1.1.4 added the tracking diagnostic.

Older release ZIPs remain in the [`updates/`](updates/) folders.
