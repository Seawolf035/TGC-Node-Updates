# TGC Mobile Node Lab 0.1

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
