# TGC Staff Companion — In-app updates

The Android Companion checks the version feed automatically when opened (at most once every six hours), and also has a **Check / install updates** screen. The updater downloads the APK from a fixed official repository URL, verifies the published SHA-256, checks the APK package ID **and matches the installed Android signing certificate**, then asks Android to install it. Android requires a user confirmation. It does not silently install.

## Permanent signing key: required for in-place APK updates

The v0.4 and earlier experimental builds were signed with changing CI debug keys. Android will not treat those as compatible upgrades. Every production release **must** be signed with the SAME private release keystore.

Do not add the keystore or its password to this public repo.

To configure it once, put these secrets in **Repository Settings → Secrets and variables → Actions**:

- `TGC_ANDROID_RELEASE_KEYSTORE_B64`: base64 of the binary `.jks` file
- `TGC_ANDROID_RELEASE_STORE_PASSWORD`: keystore password
- `TGC_ANDROID_RELEASE_KEY_ALIAS`: alias for release key
- `TGC_ANDROID_RELEASE_KEY_PASSWORD`: private key password

A private setup ZIP was provided to the owner in the chat with the generated key, passphrase, and a helper PowerShell `Setup-GitHub-Secrets.ps1` script. It is **not included in GitHub**.

Once secrets are configured, run **Build TGC Staff Companion with verified updates** in GitHub Actions from `experiment/mobile-node-lab` (or commit a new version). The pipeline builds a signed `mobile-release.apk`, publishes `downloads/TGC_Staff_Companion_Release.apk`, and writes `mobile-update.json` containing the SHA-256 and Android version code. `versionCode` and `versionName` are read automatically from `mobile/build.gradle`, so new builds only need a version bump there.

**Initial migration:** Anyone using experimental CI-debug-signed builds must uninstall them before installing the first stable release. This loses the local device key and queue. Redeploy them with a fresh single-use staff setup link. From then onward, stable signed updates install in place.

**Ownership:** Permanently store two private backups of the keystore/password. Losing it makes in-place signing compatibility impossible.

## Security / staging

This code is a test branch. Do not enable mobile relay uploads in your live Apps Script until you have installed and tested the companion-specific Relay v1.1.11 and Main Launcher v2.2.7 together.

Do not promote an unsigned or differently signed APK as an automatic update; the app will refuse it.
The main GitHub `manifest.json` for standalone/plugin/staff-launcher is unaffected.
