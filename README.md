# TGC Node Updates

Downloads and update files for **The Gamer Club's VRChat invite network**.

The Nodes and Staff Launcher check [`manifest.json`](manifest.json) for new releases. This repository holds the update packages; the Main Launcher manages the network itself.

## Downloads

| Package | Version | Update | Download |
| --- | --- | --- | --- |
| Standalone Node | **1.1.11** | **Required** | [Download ZIP](https://raw.githubusercontent.com/Seawolf035/TGC-Node-Updates/main/updates/standalone/TGC_Invite_Node_v1.1.11_Mandatory_Terms_Update.zip) |
| Plugin Node | **1.1.11-plugin** | **Required** | [Download ZIP](https://raw.githubusercontent.com/Seawolf035/TGC-Node-Updates/main/updates/plugin/TGC_Invite_Node_Plugin_v1.1.11-plugin_Mandatory_Terms_Update.zip) |
| Staff Launcher | **1.1.6** | **Required** | [Download ZIP](https://raw.githubusercontent.com/Seawolf035/TGC-Node-Updates/main/updates/staff-launcher/TGC_Staff_Launcher_v1.1.6_Mandatory_Terms_Update.zip) |

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

## Mandatory Desktop Terms of Use (October 26, 2026)

**All three desktop packages now require the v2026-10-26.1 TGC Desktop Terms agreement.** On the updated program's first launch (or when terms change), each operator must read the scrollable agreement, check **I have read and agree**, and click **I Agree**. Declining or closing prevents that TGC program from starting. For the optional plugin, declining does **not** stop the unrelated third-party host application.

Acceptance is saved **locally per Windows user and per TGC program** with the agreement version and file hash. It is not an enrollment token or staff verification, and no acceptance record is uploaded to the Relay. Old users must accept the new terms; prior enrollment does not count as consent. Viewing the agreement is always possible from the program UI.

The Terms text is included in each official ZIP and [available for review here](desktop-agreement/TGC_DESKTOP_TERMS_2026-10-26.1.txt). The Python startup gates embed the same text for compatibility with older updaters that copy only known runtime files. Updates preserve existing enrollment, DPAPI Node keys, device settings, user data, and SQLite queues. **Do not uninstall or clear data.** The Staff Launcher and Nodes still require their separate Relay authorization.

**Version enforcement:** The official [manifest](manifest.json) marks all three releases `required`. Previously installed applications check this feed under their existing updater behavior; offline, outdated or non-running clients cannot be remotely forced to update immediately. Update/restart the old program to reach the new agreement screen.

## Current changes

- **Nodes 1.1.11 / Plugin 1.1.11-plugin:** Mandatory TGC Desktop Terms clickthrough; previous diagnostics, staff deployment, attribution and queue behavior retained.
- **Staff Launcher 1.1.6:** Mandatory TGC Desktop Terms clickthrough before verification and embedded Node startup.
- **Nodes 1.1.10:** Warn when VRChat is running but isn't writing usable output logs. The warning clears when logging resumes.
- **Nodes 1.1.9:** Added the built-in tracking diagnostic.
- **Nodes 1.1.8:** Removed the redundant Discord-link button and added support for future hotfix revisions.
- **Staff Launcher 1.1.5:** Added the same logging warning to its embedded Node. Staff Launcher 1.1.4 added the tracking diagnostic.

Older release ZIPs remain in the [`updates/`](updates/) folders.
