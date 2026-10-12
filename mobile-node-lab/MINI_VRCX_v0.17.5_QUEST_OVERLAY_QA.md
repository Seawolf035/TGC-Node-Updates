# TGC Staff Companion v0.17.5 — Quest Floating Tools Default Off, Experimental Opt-In

Prepared 2026-10-12. The release adds device-gated behavior and should be tested on actual Quest hardware. **Signed compilation alone does not establish compatibility with Quest VR overlays.**

## Changes
- `QuestDevice.isQuestDevice()` identifies VR headset builds through Meta/Oculus `Build` identifiers and known Quest models/device codenames without querying installed apps or requiring network.
- On detected Quest hardware, the **floating tools are disabled by default**, even if an existing pre-update overlay preference was on. Normal Companion account features, player history, notifications and optional tracking are unaffected.
- The Home floating-tools status shows `Quest: disabled`, and its button directs the user to **Settings → Floating tools** rather than requesting overlay permission.
- Settings explains the Quest limitations and shows **Enable experimental Quest floater** (off by default). Turning it on requires an explicit confirmation. Enabling only permits future manual startup; it **does not automatically start** the overlay or claim that Android VR-composited overlays will work.
- The user can turn the setting **off** at any time. This immediately stops an existing floating service, clears its desired/recovery state and reports disabled on Home.
- `FloatingCompanionService` checks the same Quest opt-in at service launch, start-visible, sticky recovery, scheduled verification and heartbeat. Direct intents and process resurrection cannot bypass the setting.
- Existing Android phones/tablets are unchanged: their floating-tools defaults and recovery options work as before.
- On Quest, floating tools can also remain unavailable after opt-in if `SYSTEM_ALERT_WINDOW`, Quest OS restrictions or the game compositor blocks the overlay. This is expected, not evidence of a malfunction in the other features.
- Quest **VRChat log reading / Mobile Node source support remains future work**, separate from allowing a 2D floating tool. This setting does not enable Quest log capture.

## Real-device tests (pending)
1. Install signed v0.17.5 over v0.17.4 on Quest and on a normal Android phone; confirm signed in-place updates and existing personal VRChat credentials remain.
2. On Quest, verify Settings displays **Meta Quest headset detected** and **Enable experimental Quest floater** OFF. Home should show `Quest: disabled` and **Enable in Settings**.
3. Tap Home's **Enable in Settings** and verify it navigates to the Quest option, not Android's overlay-permission page.
4. Keep Quest setting OFF. Send a direct floating-service ACTION_START intent and test a sticky process restart: the service must not create the bubble or recover.
5. Enable the experiment; verify the warning/confirmation and that **nothing starts automatically**. Return Home and attempt Show floating tools; if overlay permission is unavailable or VR compositor hides it, other Companion functionality must remain usable.
6. If Quest overlays actually work, verify panel/dragging/pinned tools, stopping/closing, and optional background recovery only while the Quest-specific toggle remains ON.
7. Turn Quest option OFF with a running floater; ensure the bubble/service stops, desired state clears, and it does not reappear after process restart, rotation, or opening Companion.
8. Test Quest with network offline and a revoked staff grant: neither experimental opt-in nor recovery may bypass online staff authorization.
9. On a regular Android phone, confirm **no Quest-specific setting is presented** and the Home button and overlay behavior remain unchanged.
10. Verify Node tracking, Shizuku, Relay uploads, notifications, friends, avatar favorites and verification codes still work independently of floater availability.
11. Confirm versionCode **38**, versionName **0.17.5**, signer continuity, released APK and updater SHA-256.

## Constraints
- Build identifiers can vary with OS updates or unusual custom ROMs. Report Quest model, brand and manufacturer (not personal IDs) if hardware is misdetected.
- Android's `SYSTEM_ALERT_WINDOW` permission is necessary for standard 2D overlays but does not guarantee the panel will be visible inside VR on Quest.
- Quest companion features and Quest VRChat log tracking are distinct areas of compatibility; neither should be assumed available just because an experimental floater toggle is on.
