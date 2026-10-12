# TGC Staff Companion v0.17.9 — Rotating Live Chatboxes (unreleased)

**Source branch:** `feature/mobile-v0.17.9-rotating-chatboxes` from `experiment/mobile-node-lab` at v0.17.8.  
**Status:** code prepared for review, **not built, signed, published, installed, or real-device tested**. Existing v0.17.8 release remains authoritative.  
**Proposed version:** 0.17.9 / versionCode 42.

## Behavior
- The original configured status line remains **Default Live Chatbox** and is always first in a rotation. Existing settings migrate implicitly, without deleting or replacing any values.
- Staff can save up to **8 additional named chatboxes** per signed-in personal VRChat account. Each has its own name, status text, clock toggle and phone battery toggle.
- No newly saved or edited chatbox sends anything until the user explicitly **starts/confirms Live Chatbox** from the existing status screen. Live Chatbox still starts OFF after service teardown or app restart.
- **Automatic rotation is OFF by default** and requires one additional saved chatbox. The switch interval is independent of the periodic OSC refresh: 15/30/60/120-second switching, 5/10/15/30-second refresh. Each successful tick can advance at most one slot; no profiles are skipped when refresh is slower than switching.
- Editing/adding/deleting profiles and disabling rotation do not affect the staff-only gate, native chat composer, typing indicator, OSC avatar parameter controls, Shizuku Node, Relay, or other services.
- Existing manual chat priority pauses live status around **20 seconds**. Rotation stops with the floating service or personal-account/staff invalidation. No silent auto-restart.
- All OSC messaging is local UDP `127.0.0.1:9000`. Nothing is uploaded to the TGC Relay. Successful packet transmission is not a VRChat display receipt.

## Build and release gate
1. Compile debug and release builds via the existing Android GitHub Actions workflow; verify Java compilation and warnings.
2. Verify Gradle `versionCode 42` and `versionName '0.17.9'`; test upgrade from release-signed v0.17.8 and permanent signing certificate match.
3. Only with explicit owner approval, merge/publish to `experiment/mobile-node-lab`. Its push workflow **automatically signs and publishes a new APK and update manifest**, so merging is a release operation.
4. Check APK signing, published SHA256 and manifest against actual artifact. Don't report release success before those checks.

## Real-device QA
1. In v0.17.8, save an existing default text/time/battery configuration. Upgrade in place; verify unchanged default content and all staff/session/Node outbox data.
2. OSC -> Live Chatbox -> Manage rotating chatboxes; create two new named chatboxes with different clock/battery toggles and verify their previews.
3. Save, rename, edit and delete profiles. Confirm name collisions, empty content without flags, 90-character text bound, 144-character OSC final bound and 8-profile cap all give clear errors.
4. Enable rotation at 30 seconds and refresh at 5 seconds. Confirm three slots appear sequentially in VRChat (Default → first saved → second saved → Default), with updates within each interval.
5. Check rotation enabled with 30-second refresh/15-second switch: **none of the profiles should disappear from the sequence**.
6. Confirm manual messages pause the live broadcaster for about 20 seconds and afterward the currently due profile resumes.
7. Collapse the panel, switch floating tabs and return. Confirm rotation continues without opening the editor.
8. Disable rotation while Live Chatbox is active; confirm subsequent sends use the default status. Re-enable rotation only by the user's control.
9. Remove the final extra profile; rotation should automatically disable. Confirm default status still works.
10. Stop Live Chatbox, hide bubble, switch personal VRChat account, revoke staff access, simulate service death and relaunch; no automatic background broadcasts.
11. Test narrow floating overlay sizes, switching and editing interactions using the Android keyboard while VRChat is visible.
12. Regression: manual OSC chat, saved quick phrases, typing, avatar Bool/Int/Float parameters, Quest default-off overlay, TGC/Cult code reading, Shizuku log watcher, Relay offline queues and Main Launcher integration.

## Known constraints
- Current source has only timing-based rotation; manual next/previous and condition-triggered modes are not part of this draft.
- A profile can switch only on an actual OSC refresh, so visible switching may be slower than the selected rotation interval.
- This feature is wholly client-local and requires no Relay, desktop Node, Main Launcher, or staff authorization contract changes.
