# TGC Staff Companion v0.19.4 — Quest System UI and hard floater disable

## User-visible changes

**Quest 2 / Quest 3 / Quest 3S / Quest Pro (when detected as Quest):**
- The Home shortcut formerly labeled **Show floating tools** is now **Quest System**. It launches `QuestToolsActivity` directly instead of sending users to Settings.
- The Quest System UI is an ordinary, full-screen Android activity; all existing native Mini-VRCX, OSC and pinned quick-action tools remain available.
- All Quest floating overlays are permanently blocked: no experimental opt-in, no overlay-permission prompt, no floater size/snap setting, and no background auto-recovery.
- Existing `tgc_overlay.desired` and `tgc_overlay.recover_after_stop` flags are reset on Companion open. A direct or sticky `FloatingCompanionService` start on Quest also clears them and stops.
- The Settings page labels **Quest System** and explains that it is accessed through Home; it does not add a duplicate launch button.

**Android phones:**
- Keep the existing Show/Hide floating tools Home action, size/snap settings, optional restore, overlay runtime and permissions unchanged.
- Shizuku remains required for Android phone VRChat log monitoring. Quest defaults to Documents/Logs folder permission, with its Shizuku fallback in Advanced.

**Navigation cleanup:**
- Remove duplicate Settings shortcuts for inbox, normal favorites, extended favorites, and private notes. They remain on Home and/or Explore. Keep the account, status, notification settings, staff access, and other Settings actions.

## Validation checklist

1. Upgrade a Quest with the old experimental overlay preference set to true. Launch the Companion: verify it cannot start an overlay, the saved desired/recovery flags are cleared and the Quest System Home action opens native tools.
2. From Home, tap Quest System and verify Social, OSC, Tools, pinned quick-action row and Mini-VRCX pages still function. No Android overlay permission prompt should appear.
3. Open Settings. Confirm there is no experimental floater toggle, Show floating tools, floater size, snap, or recovery button. Background health describes Quest System as native.
4. Stop and restart the Companion; simulate Android service restart / direct `FloatingCompanionService` start. It must stop with `START_NOT_STICKY`, never display a bubble.
5. Verify folder tracking runs on Quest and the Shizuku fallback can still be selected from Tracking → Advanced. Check Relay sightings continue and Quest OSC runs independently.
6. On an Android phone, test Show/Hide floating tools, the overlay permission prompt, panel size/snap, optional recovery, and the Shizuku-based phone log tracker.
7. Verify inbox, private notes, normal and extended avatar favorites remain available in Home/Explore.
8. Verify signing key continuity and v0.19.4 (versionCode 56) updater metadata before publishing.

## Scope

No new Avatar favorites save flow or Player Groups browser in this release; those are separate upcoming functionality updates. No change to Node/Relay or GM4/GM1 invitation authorization.
