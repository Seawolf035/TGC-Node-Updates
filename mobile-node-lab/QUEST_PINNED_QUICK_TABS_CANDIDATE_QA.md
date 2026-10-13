# Quest native pinned quick-action bar — candidate QA

**Status:** Source-only improvement off signed v0.19.0 (code 52). Not published; no Android version bump or APK replacement yet.

## Scope

- Quest-only `QuestToolsActivity.java`: the user-configured 3-action quick bar remains fixed directly below the Home/Social/OSC/Tools tab row while the page scrolls.
- No changes to `FloatingCompanionService.java`, ordinary phone navigation, shared Mini-VRCX controls, signing, credentials, Relay, or Main Launcher. A separate Quest-only Shizuku log path enhancement is documented in `QUEST_v0.19.1_LOG_WATCHER_QA.md`; the ordinary Android phone's live log directories remain unchanged.
- Existing behavior retained: the quick bar appears on the main Quest hubs, not the Shortcuts editor or detailed Mini-VRCX pages.

## Test on Quest 2

1. Open Quest native tools. Configure up to 3 quick actions through existing Shortcuts controls.
2. On each Quest hub (Home, Social, OSC, Tools), scroll through long content. Verify both the main tab row and configured quick actions stay visible at the top.
3. Tap each quick action; verify it still navigates to the same Mini-VRCX page. Return, switch tabs, and verify the configured actions remain selected.
4. Open the Shortcuts editor and a detailed Mini-VRCX page. Verify existing navigation/back behavior; the editor must not show duplicate toolbars.
5. Pause/resume the app and test Quest keyboard interactions and scroll height.
6. Recheck the ordinary Android phone's Home/Explore/Tracking/Settings and original floating quick bar; those files were not changed.

## Release boundary

Only after approval: bump versionName/versionCode above 0.19.0/52; validate Android CI; use existing permanent signer; verify certificate continuity, published APK, SHA256 update feed, and in-place upgrade. Do not uninstall, reset app data, or deploy Main Launcher/Relay.
