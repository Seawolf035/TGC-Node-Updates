# TGC Staff Companion v0.19.10 — Tracking-tab setup and proprietary EULA

## Tracking setup location

- The **main Tracking tab** displays a one-time inline setup guide on first use:
  1. Set VRChat → Settings → Debug → Logging → **FULL**
  2. Quest: grant read-only Documents/Logs folder permission; phone: Shizuku
  3. Start tracking before joining an instance
- The user acknowledges with **I understand · Continue** ON the Tracking tab.
  Their choice persists under the exact existing
  `tgc_tracking_first_setup_v1/logging_guide_acknowledged` setting.
- After acknowledgement, the large instructions disappear; a small
  **View setup guide** control remains ON the Tracking tab for later review.
- Tracking controls no longer contains the tutorial dialog or any Advanced-only
  reopening action. If Start is tapped before the acknowledgement, it routes
  back to the Tracking tab and doesn't start tracking.
- Existing linked installs and existing v0.19.9 acknowledgements remain
  acknowledged. This confirmation is NOT a sensor or proof of Full logging.

## Proprietary software terms

- The official Companion includes an in-app, scrollable staff EULA bundled at
  `mobile-node-lab/mobile/src/main/assets/tgc_companion_eula.txt`.
- **First app launch after installation/this update** presents a separate
  license screen before ordinary application interaction on both phone and Quest.
  An unchecked agreement box disables **I Agree & Continue**.
- **Decline & Exit** closes the app and does not record agreement.
- Acceptance stores agreement version `2026-10-13.1` and timestamp in
  app-private preferences. Future app releases need not re-prompt unless terms
  version changes.
- Settings → **License & user agreement** reopens the terms read-only.
- The staff agreement only covers TGC's original protectable software, without
  restricting third-party/open-source components or independent software built
  without copying TGC protectable expression. It does not grant exclusivity to
  VRChat or its APIs.
- Public `PROPRIETARY_LICENSE.md` and README mark the original source as
  all-rights-reserved. **This repository is still public**: its contents are
  discoverable. Keeping future source confidential requires a private
  development repository plus a separate public APK/update-feed repo.
- This legal draft needs the actual copyright holder / licensor verified and
  attorney review before relying on it for enforcement. Provide a complete
  separate privacy disclosure before wider distribution.

## Manual QA

1. New Quest phone install: license appears before normal app screens, checkbox
   starts unchecked, declining exits, agreeing stores acceptance.
2. Upgrade already linked v0.19.9 device: license prompts once (new EULA),
   while previous Tracking tutorial acknowledgement is preserved.
3. Use first-time device that has no tracking acknowledgement: open main
   Tracking tab; inline setup appears. Accept; reenter tab; only small View
   setup guide remains. That button expands guide in place.
4. On Quest and Android phone, verify step 2 differs correctly.
5. Start tracking via controls before acknowledgement; ensure it routes to
   the Tracking tab and doesn't start the service.
6. Confirm Tracking controls → Advanced has NO setup guide button and
   no auto-open modal. Existing Shizuku instructions and Quest fallback remain.
7. Open Settings → License & user agreement; terms render read-only.
8. Quest universal Back continues working on Companion pages, and license
   screen cannot be dismissed to bypass required initial acceptance.
9. Validate no new network permission and no signing key changes.
10. Test clean install, in-place upgrade, app restart, process recreation,
    non-Quest phone, and a pending share intent to MainActivity.

Release version: 0.19.10, versionCode 62.
