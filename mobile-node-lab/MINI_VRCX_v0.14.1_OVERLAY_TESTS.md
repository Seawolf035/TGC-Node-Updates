# TGC Staff Companion v0.14.1 — Floating Mini VRCX usability test

Build scope: targeted v0.14.1 update on top of v0.14.0.
This is a device-test checklist, **not a claim of real-device verification**.

## Overview
- Floating bubble now uses the exact in-app `tgc_logo` resource instead of "TGC" text.
- Circular floating icon includes a small Node status indicator: green for running with a recent heartbeat, gold otherwise.
- Expanded panel uses a branded header, compact **Home / Social / Tools** tabs, scrollable content, and fixed lower quick controls.
- **Home** includes Start/Stop Tracking using the existing `MobileNodeService.START/STOP` actions. Stop requires two taps within eight seconds.
- Node status presents captured player count, uploaded count, watcher description and latest local captured player names.
- Recent player names open a personal VRChat profile through the existing staff-protected details screen.
- **Social** provides Friends Locations, online/offline friends, Friend Log, Activity Feed, inbox, invitations, friend requests, player search and groups.
- **Tools** provides personal VRChat account, status presets, avatars, worlds, private notes, verification invite, tracking settings and history.
- No second Shizuku probe, Node log parser, Relay sender, or personal VRChat session store was created.
- Optional overlay tracks its own heartbeat to avoid the main app getting stuck on "overlay on" following Android process death.

## Device checklist
1. Install over **v0.14.0** without uninstalling. Verify staff Quick Deployment, personal VRChat session and Mobile Node queued sightings survive.
2. Confirm floating bubble displays exactly the TGC Companion icon; drag and reposition; tap to expand and collapse; confirm badge changes with Node state.
3. Test a narrow display and tall display: header, Home / Social / Tools tabs, scrolling, and bottom actions are reachable without clipping.
4. With Shizuku already authorized and staff grant valid, tap **Start tracking**. Confirm Node heartbeat becomes active and logged encounters continue after overlay is collapsed or hidden.
5. Tap **Stop tracking** once: tracking must keep running. Tap again within eight seconds: existing Node service must stop; queue/history must remain.
6. With Shizuku not running or not permitted, Start Tracking must open the existing Node settings/permission workflow instead of bypassing authorization.
7. With no TGC deployment, Start Tracking must not start a Node. Staff revocation must stop the overlay and prevent protected Activity access.
8. Leave the overlay on while VRChat Mobile is active: recent captured events should populate the local feed and profile links open for valid VRChat IDs.
9. Switch each Social / Tools action, return to VRChat Mobile, and verify the overlay remains functional.
10. Hide only the overlay: `MobileNodeService` must continue capturing and uploading while previously active.
11. Force-kill the overlay service or app process. Reopen the Companion and verify its toggle is not stuck in an incorrect running state.
12. Verify permanent signing certificate continuity, published APK SHA-256, versionCode **25**, update feed **0.14.1**, and in-place upgrade.
13. Compare memory, foreground resource usage, touch/scroll behavior, screen cutout placement and battery performance on real devices.

## Notes and bounds
- The overlay offers existing Companion features; its shortcuts open the corresponding secure Activity where full-screen interaction is more appropriate.
- Recent player events come from the existing local Node event history and update periodically, not a new network API.
- Friend sampling remains unchanged from 0.14.0.
- Starting the Node requires an authorized staff grant, valid Quick Deployment and granted Shizuku access. Android-level notification/foreground restrictions may require the existing setup Activity.
