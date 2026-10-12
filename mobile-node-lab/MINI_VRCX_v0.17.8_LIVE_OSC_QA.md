# TGC Staff Companion v0.17.8 — Dedicated OSC Tab and Continuous Live Chatbox

Prepared 2026-10-12. Compiled/signed release checks are separate from live VRChat Mobile OSC tests.

## OSC tab
The floating panel now has **Home / Social / OSC / Tools** as four top tabs, with a dedicated OSC dashboard for all chatbox controls. The prior Home/Social/Tools views remain available. The OSC tab includes:
- **Chatbox composer**: the existing native Android keyboard workaround, sending on explicit taps to local VRChat OSC.
- **Quick phrases**: save, load and delete up to 24 account-specific phrases, without automatic transmission; loading a phrase opens the chat composer for review.
- **Live Chatbox / status line**: user-edited text, optional current time and phone battery, in-app preview, one-shot silent Send, selected 5/10/15/30-second update interval, Start/Update with nearby explicit confirmation, Stop control.
- **Typing**: manual ON/OFF commands and opt-in typing indicator that follows active chat text; false is sent on losing focus, closing the page or sending.
- **Avatar parameters**: up to 16 saved per-account parameter names, with one-shot Bool, Int (0–255) and Float (-1..1) controls using typed OSC messages.
- New settings are scoped to the personal VRChat user account and stored only in local app preferences. No credentials are added or sent to external services.

## Continuous behavior
- Live status is **OFF by default**. A user must configure the content and select **Start Live Chatbox → Confirm Live Chatbox**. Enabling/configuring the other OSC controls never implicitly starts broadcasting.
- The **existing floating foreground service** schedules periodic UDP OSC sends while its bubble remains enabled, including when its panel is collapsed or the user navigates away from the OSC tab.
- Default update interval is **5 seconds**, with 10/15/30-second options. Messages use the documented `/chatbox/input` endpoint with `send=true` and `SFX=false` to avoid notification sounds. Content is reassembled at each tick so clock/battery change.
- The **normal chat composer takes priority**: following a user-sent chat message, live status yields for about 20 seconds before resuming.
- Tapping **Stop Live Chatbox** stops its timer. Hiding the bubble, stopping the floating service, losing staff access, disabling Quest overlay permission, changing the personal account or process destruction also stops it.
- **No automatic restart after a reboot or process restart**, even if the floating-service recovery preference is enabled. Re-enabling is explicit.
- OSC works over local loopback `127.0.0.1:9000/UDP`, without Shizuku or Relay. VRChat Mobile must have OSC enabled. A successfully sent UDP packet **does not confirm in-game display**.

## Device QA — pending
1. Install signed v0.17.8 (versionCode 41) over v0.17.7, preserving staff access, account login, Node outbox, overlay settings and Quest opt-in.
2. Verify Home / Social / **OSC** / Tools tabs display well at Compact, Standard and Expanded panel sizes and stay tappable on narrow Android phones.
3. In OSC → Live Chatbox, set custom prefix, clock and battery, verify preview, choose 5 seconds, Start and Confirm. While in VRChat Mobile, verify actual periodic chatbox messages every ~5 seconds with updated clock/battery and no notification SFX.
4. Collapse panel without hiding bubble. Verify continuous status updates continue. Switch to Friends or Tools and return; confirm still active. Tap Stop; verify no further periodic OSC packets.
5. With Live running, manually send a normal chat message. Verify periodic statuses defer ~20 seconds, then resume.
6. With OS network changes and memory pressure, verify it never silently restarts live broadcasting after foreground service destruction; staff access and Quest restrictions remain intact.
7. Tap Hide bubble and confirm status broadcasting stops. Reopening the Companion should show OFF; user must deliberately start again.
8. Save two quick phrases, load one into chat composer, confirm it doesn't send until Send is tapped. Delete one; confirm account isolation and no plaintext chat history logging.
9. Enable auto typing, enter text and check VRChat typing indicator ON; finish or leave editor and confirm OFF. Manual ON/OFF still works.
10. Add Bool/Int/Float avatar parameters by exact name; verify correct OSC wire types, numeric bounds and no send on saving. Test actual avatar parameter effects on a compatible avatar.
11. Test OSC-disabled VRChat: the Companion may transmit local UDP but must not claim guaranteed delivery. Do not confuse emitted packet with confirmed display.
12. Verify The Gamer Club/Cult verification, favorite avatar selection, friend/instance tools, Shizuku tracking, Relay, and Quest default-off settings unchanged.
13. Verify Android debug compilation, stable signing certificate comparison, GitHub-signed published APK, version 0.17.8/code 41, updater SHA-256 and in-place update.

## Technical limits
- VRChat chatbox permits 144 characters and up to 9 displayed lines including wrapping. If the composed line exceeds the limit, the sender reports an error instead of truncating silently.
- Even with five-second polling, visible chatbox duration depends on VRChat settings; the timer doesn't guarantee the message stays visible under every OS/client state.
- No Pulsoid, media playback or desktop MagicChatbox integrations have been added in this release. Those would require separate optional integrations and permissions.
- The OSC tool never changes VRChat's broken Android keyboard implementation; it works around it by sending via local OSC.
