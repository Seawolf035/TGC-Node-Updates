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
