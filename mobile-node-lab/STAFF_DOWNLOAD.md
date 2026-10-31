# TGC Staff Companion — official Android download

**Current public release link (stable):**
[Download the signed TGC Staff Companion APK](https://raw.githubusercontent.com/Seawolf035/TGC-Node-Updates/experiment/mobile-node-lab/mobile-node-lab/downloads/TGC_Staff_Companion_Release.apk)

**Check the published version:** [Official update manifest](https://raw.githubusercontent.com/Seawolf035/TGC-Node-Updates/experiment/mobile-node-lab/mobile-node-lab/mobile-update.json).

This link always targets the latest **published, signed** Companion APK, not a GitHub Actions debug build or an experimental candidate. Only share the link as **v1.0.0** once the update manifest reports `1.0.0` / build `104` and the signing/Android build checks pass. Until then the existing published release remains current.

## Staff install instructions

1. Open the **signed APK** link above on your Android phone, download the file, and approve the Android installation prompt. If Android asks, allow installs from the browser or Files app you used. This is a direct APK distribution; it is not a Google Play listing.
2. Open **TGC Staff Companion**, review the User Agreement and complete your own approved staff/device setup and verification. A public download link **never grants staff access**.
3. Sign into VRChat with your **own account** using the in-app flow. Never share a Discord/VRChat password, one-time code, or account cookie with another staff member.
4. For phone player tracking only, opt into Shizuku and grant the required device permissions if you choose to use that optional feature. Other Companion tools do not require Shizuku. Quest uses its own supported setup and permissions.
5. For later releases, open **Home > App updates & release notes**, or **Settings > App, Battery & Legal > Check for updates**. The updater verifies the published file hash and APK signing identity before offering installation.

## Where to find things

- **Avatar eye height:** Floating Tools or Quest Tools > Home > Avatar eye height, or near the top of their OSC tab. The same account-scoped 0.80 m, 2.00 m, and custom presets are retained. Add **Avatar eye height** to customizable Home shortcuts / persistent quick bar if desired.
- **Setup and troubleshooting:** Main Companion > Home > More > Setup & troubleshooting.
- **Support diagnostics:** Main Companion > Settings > App, Battery & Legal > Background health > **Copy summary**. This copies app status, not passwords or tokens.
- **What's New:** Main Companion > Settings > App, Battery & Legal > What's New, and on first launch after a signed update.

## Upgrade and authenticity

- The official package identifier is `club.thegamerclub.nodelab`. The signing certificate must match the prior signed official APK to preserve in-place upgrades and settings.
- Staff using a **debug-key preview** rather than the official signed APK may encounter Android's “app not installed” / signing conflict; **do not uninstall** casually, because uninstalling erases local app data. Ask for help with backup/transition first.
- This repository is public so that the Android package can be downloaded. No Main Launcher source, Relay credentials, VRChat cookies, DPAPI session state, staff keys, or Android signing keys are required or included in the APK download instructions.
- Downloading an APK is not proof of successful app/device authorization. Staff-only functions remain gated.
- The app is for Android/Quest where supported, **not iOS**.
