# TGC Mobile Node Lab 0.3

Private-data-free test APK for VRChat Mobile / Quest log access. This is a diagnostic, not a deployed Node.

- Import a log from the Android file picker, or share it from VRChat.
- Check whether an Android app can read VRChat's files directly (Android often blocks this).
- Parse player joins/leaves, unique user IDs, and world/instance references, reporting counts only.
- Poll the selected log every 10 seconds while the test app is visible.
- Share a sanitized results summary.

No INTERNET permission, Discord login, Node keys, or relay traffic.

A successful manual log import **does not prove live automatic log access**. Modern Android restricts access to other apps' Android/data folders.

Build: JDK 17, Android SDK 35, Gradle 8.10.2: `gradle :mobile:assembleDebug`.

## Shizuku experiment (v0.2)

This lab now has an opt-in **Shizuku Automatic Source Test**. Install and start Shizuku via wireless debugging (no root), approve this Lab in Shizuku, and tap the test button.

The test requests limited shell-UID, read-only access to the original VRChat log folder and recent Android system-log events, then reports counts only. No player details, credentials or log bodies leave the phone. This is an *access probe*, not a live background service or relay Node. If the privileged file read or logcat checks work, we can make a foreground-service Node that watches the same source while VRChat is open.

**Security:** Shizuku provides powerful system-level API access to apps you authorize. Only approve apps you trust. Debugging/Shizuku may need restarting after a device reboot.

## Live watcher experiment (v0.3)

**Purpose:** verify that Shizuku can keep tailing VRChat Mobile's **original** private output log while VRChat is foreground on the phone.

1. Confirm Shizuku is running (13.x) and that TGC Mobile Node Lab is authorized.
2. Install v0.3, launch it, and tap **START live watcher**. This bookmarks the current end of the most recent *VRChat package* output log, not an exported copy in Downloads.
3. Open VRChat Mobile, visit a populated instance, and wait for new players to join/leave.
4. Return to the Lab, tap **VIEW live watcher results**, and share the sanitized report.
5. Tap **STOP live watcher** after testing.

The watcher polls every 5 seconds from a separate Shizuku shell-UID process. It counts new join/leave events and log bytes only. This is an opt-in experimental test, **not a finished automatic startup system or a node with relay uploads**. Android might still terminate the watcher in the background; check that `Polls completed` increases and `Last appended data` changes when VRChat logs new events.

To verify real live operation, we need **new joins since watcher start**, not just log file readability. A file found but modified ~1 hour ago only proves storage access.

No INTERNET permission, no staff account verification, no keys, no raw event IDs transmitted. Stored IDs for uniqueness are only kept transiently in the privileged process's memory and are cleared on monitoring restart. The Shizuku service is configured as a daemon for the experiment; STOP monitoring when finished and revoke app authorization in Shizuku when desired.

On installation: GitHub Actions generates ephemeral Android debug signing keys, so installing this debug build over a prior debug build may fail. Uninstall the older Lab APK first if Android reports a signature conflict. This does not affect VRChat or Shizuku.
