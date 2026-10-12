# TGC Staff Companion v0.17.3 — Floating Customization and Background QoL

Created 2026-10-12. The notes describe implemented source and a **pending real-device acceptance plan**; signed compilation alone is not device QA.

## Floating panel improvements
- **Compact / Standard / Expanded** panel layouts persisted on the device. Each size is clamped to the available Android display, including portrait/landscape; text and controls continue to scroll inside the panel.
- **Pinned Home shortcuts:** select between one and six, including Notifications, Friends, Status, Player history, Recent worlds, Search, Current instance, Activity, Verification, Join/leave, Groups and Saved worlds. Order with Move earlier/later, no separate app switch; changes persist locally.
- **Optional edge snapping:** a dragged bubble snaps to the nearest horizontal screen edge, or remains freely positioned when snapping is off. Position persists; Reset returns the bubble to the default area.
- Floating Tools → **Customize floating panel** exposes size, shortcut selection and order, snapping, reset and background recovery. The main Companion Settings also exposes the overlay size, snapping and recovery controls.
- Window sizing and bubble position are re-clamped on screen configuration changes, rather than leaving panels partly offscreen.
- Existing **The Gamer Club/Cult verification**, personal VRChat status colors and private account tools remain unchanged.

## Background use and recovery
- The **Mobile Node already uses Android START_STICKY** and its own Shizuku/reconnect and Relay loops. v0.17.3 does *not* rewrite or restart its log reader and does not tie the Node to the floating panel.
- Floating service recovery is **off by default** and explicitly user-enabled. A manually activated overlay stores the user's desired state; hiding/stopping the bubble clears it.
- Once recovery is enabled, Android may recreate the floating foreground service after reclaiming its process. If the process lost its staff grant, the overlay verifies TGC deployment **online** before displaying private views. Definite authorization denial disables recovery.
- Recovery retries an interrupted connection only a bounded number of times. If Android blocks restoration or no network is available, the Companion can make another bounded attempt when the user opens the main app, after successful staff revalidation.
- Recovery is **best effort**, subject to Android foreground-service start restrictions and battery modes. Force-stopping an app, revoking overlay permission, or a denied staff grant prevents automatic revival; no bypass or exemption is attempted.
- Personal VRChat polling becomes less frequent with the overlay collapsed (friend observations approximately five minutes; VRChat presence approximately three minutes) and refreshes on reopening only if stale; local tracking is not paused.
- Main Settings → **Background health** shows live Node/overlay heartbeats, inbox alert configuration, recovery preference and Android battery optimization state, with a shortcut to the app information/battery settings.
- Existing WorkManager 15-minute-minimum opt-in inbox alerts remain separate and unchanged.

## Device acceptance checklist — not yet verified
1. Install v0.17.3 over signed v0.17.2; confirm app ID, signing certificate, personal login, staff link, local histories, unsent Node queue, Shizuku permission, notification opt-in and previously saved bubble position.
2. Open Tools → Customize floating panel. Switch among Compact, Standard and Expanded on a narrow phone and on landscape rotation. The whole panel should remain reachable and scrollable without losing game focus.
3. Pin, unpin and reorder six shortcuts. Home should update immediately. Close and reopen the floater; shortcuts and selected size must persist.
4. Drag the bubble to either half of the screen with snapping enabled and disabled; confirm Reset position works. Rotate and verify the bubble and panel remain on-screen.
5. With auto-recovery **off**, close/kill floating tools. They should stay off; player tracking continues separately if enabled.
6. Enable background recovery while tools are on. Kill the **process** via a non-force-stop process-pressure test, then observe if Android chooses to recreate the sticky foreground service. Confirm it requires a fresh staff grant before displaying private data.
7. Stop the bubble using its bottom Hide bubble control, the foreground notification Hide bubble action, and the main Companion toggle. None may unintentionally restart due to sticky mode.
8. With the network offline during restart, confirm the bubble stays hidden; no cached staff grant is treated as fresh approval. Restore connectivity and reopen the Companion to test foreground recovery.
9. Remove staff authorization and test recovery. The overlay must not appear after denial, and no personal API call may start before staff verification.
10. Revoke Android Draw over other apps permission, force-stop the app, or enable aggressive battery restrictions. Confirm no bypass attempts and appropriate user guidance.
11. Monitor account API calls with floater collapsed for 10 minutes. Friend/status polling should back off without stopping Shizuku's single Node watcher or uploading any additional raw logs.
12. Verify Notifications still use opt-in WorkManager scheduling and user permission, and no new foreground service or Android battery-exemption permission is requested.
13. Check versionCode **36**, versionName **0.17.3**, signing certificate equality with v0.17.2, signed APK publication, correct updater SHA-256, and a successful in-place upgrade.

## Explicit limitations
- Android decides whether and when to restart a foreground service, and OEM battery managers can be stricter. Background recovery is not continuous guaranteed uptime.
- **Android force-stop cannot be bypassed** and recovery does not run automatically at phone boot.
- The floating panel shares account session cookies only with the Companion's existing encrypted personal VRChat client; no new credentials or Relay payload types are introduced.
- Quest log access and advanced group management remain future roadmap items.
