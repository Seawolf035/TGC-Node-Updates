# v0.19.0 — Quest-native staff tools, preserving regular phone behavior

## Intent
The Quest headset cannot rely on system-window overlays. This release adds **internal non-exported QuestToolsActivity** with Home, Social, OSC and Tools screens inside the normal Android Companion. It is linked only on Quest devices and requires the same verified TGC staff access as other Companion features. The existing phone FloatingCompanionService and navigation stay untouched.

## Portability
- Reuses `OverlayMiniVrcx` for every existing Mini-VRCX page. Its host context type changes from Android Service to generic Android Context, allowing both the original floater and Quest native Activity to use the exact same data sources, permission checks, VRChat session, navigation, search, edit, and confirmation logic.
- Native hub includes full friend list, groups and instances, profiles, inbox, avatar favorites, private notes, world bookmarks, recent worlds, user search, presence, verification, encounters, instance and player history, OSC live-status editors, official tags, saved phrases, chat composer, avatar OSC params, typing control, quick shortcuts and compact pinned friends with Edit order and online alerts.
- Existing Main App screens remain reachable. Stored lists, pinned friends, messages, presets and authorization use the same account-scoped services.
- Original tracking via Shizuku/VRChat logs remains independently governed by the existing compatibility logic; a Quest headset that cannot expose VRChat logs cannot claim to have fully functional join/leave capture.

## Quest-only runtime OSC
- New internal `QuestOscService` (not the floating service) implements start/stop/pause/resume Live Chatbox and manual PTT press/release, without requiring overlay permissions.
- Actions start only from verified Quest UI; returning to VRChat leaves a persistent foreground OSC service notification showing state, Stop OSC and RELEASE PTT actions.
- Mic hold times out after five minutes; normal service shutdown sends redundant OFF packets. Android force-kill may bypass that guarantee; an OSC packet does not confirm headset mic state.
- No automatic service restart after a process crash. Live chatbox always starts explicitly. Account changes and staff grant expiration stop live sending. OSC always targets local loopback and VRChat must accept it.
- Switching away from VRChat can suspend Quest's Android application or the VRChat client; actual concurrent VR rendering, OSC availability and immersive multi-tasking must be tested on Quest hardware.

## Device QA
1. **Ordinary Android phone** install over signed v0.18.8: same Main App 4 tabs, same floater, same sidecar PTT, same mini screens, same online alerts and overlay permissions. The Quest native hub should NOT appear or launch on a phone.
2. **Quest**: new Native Tools button appears in Main App Home/Explore/Settings. Quest floater stays disabled by default. Open Native Tools without requesting Draw Over Other Apps.
3. Navigate Native Home/Social/OSC/Tools, then all relevant Mini-VRCX pages and the Back action. Test keyboard, search, confirmation modals, account actions and profile browsing; no overlays appear.
4. Pinned Social: tap to open profiles, Edit order/Done, persistent saved order and online alert toggle.
5. Live OSC: select eligible rank-locked official messages, start Live, leave native Activity to Quest VRChat, observe notification and actual chatbox output. Pause/resume and Stop; verify service stops on account change.
6. PTT: with OSC enabled in Quest VRChat, Hold PTT, return to VRChat, test client icon, release using notification/app, test five-minute auto-release. **Do not claim Quest mic OSC works until tested.**
7. Shared native home shortcut and Quick Bar settings remain local, not server-managed.
8. Check staff revocation and inactive VRChat session deny unintended actions. Confirm native Quest Activity is not exported and native service is not exported.
9. Test Quest sleep/resume, leaving the app, VRChat suspension, and Android foreground-service restrictions. Background online friend alerts remain subject to WorkManager scheduling.
10. Validate both x86/ARM-compatible Android app compilation and permanent signer upgrade. CI is not device QA.

## Scope exclusions
No VRChat protocol bypass, headset privileges, standalone app injection, hidden floating overlay, new VRChat credentials, production deployment, or modifications to Relay/Main Launcher.
