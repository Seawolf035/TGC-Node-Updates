# TGC Staff Companion v0.17.7 — In-game VRChat Mobile text chat via local OSC

Prepared October 12, 2026. Source/compilation checks are distinct from live-device VRChat OSC delivery tests.

## Why we added this

Some Android VRChat Mobile builds have keyboard problems, including an October 2026 bug affecting text visibility in the native chat IME. VRChat documents the OSC `/chatbox/input` endpoint: a string and a **true** second boolean sends to the chatbox without opening the in-game keyboard. VRChat supports OSC on Android, but actual OSC availability varies with its client status/configuration.

References:
- Official VRChat chatbox OSC documentation: https://docs.vrchat.com/docs/osc-as-input-controller
- Official community OSC documentation: https://wiki.vrchat.com/wiki/OSC
- VRChat Android Mobile OSC feedback: https://feedback.vrchat.com/mobile-beta/p/osc-on-androidoculus-quest
- Recent VRChat Mobile keyboard report: https://vrchat.canny.io/bug-reports/p/invisible-text-when-typing-in-chatbox

## User workflow

1. In **VRChat Mobile**, enable OSC from the VRChat Action Menu.
2. Show the Companion floating bubble over VRChat Mobile.
3. Open **Home → Type & send to VRChat**, **Social → In-game communication**, **Tools → In-game text chat**, or add **In-game text chat** as a pinned Home shortcut.
4. Type in the **Companion's Android EditText field**. The overlay takes IME focus, so it does not depend on VRChat Mobile's own text input implementation.
5. Tap **Send to VRChat**. It issues exactly one OSC `/chatbox/input` message with a **true** send flag to **127.0.0.1:9000/UDP**. The packet goes directly to the same phone, not through TGC Relay, Discord, Shizuku, or the VRChat Web API.
6. Check the message appeared above the avatar in VRChat. **A successful UDP send does not prove VRChat received/displayed it**; OSC must be on and working in the mobile client.

## Supported behavior and safeguards

- OSC UTF-8 string and tags `,sTT` (send now + play SFX) or `,sTF` (send now + silent) serialized with NUL terminators and 4-byte padding.
- 144 codepoint maximum, at most nine lines; no embedded NUL, CR, or other unhandled control characters. The editor caps UTF-16 units at 144 for a conservative client-side bound.
- An explicit tap is required for every message; no background auto chat, no chat scheduled from a file, no bot, and no new web credentials.
- Rate-limit outgoing packets locally to at most one every approximately 1.8 seconds. No repetitive retry loop.
- Chat draft remains in memory if the floater page is closed and reopened in the same service instance. Messages are not written to preferences, SQLite, logcat, or TGC infrastructure.
- **Copy message (fallback only)** explicitly does not send to VRChat. Clear draft is separate.
- Existing Quest default-off overlay setting, staff gating, VRChat account functions, Local Node/Shizuku watcher, Relay and history remain unchanged.

## Pending device acceptance tests

1. Install signed v0.17.7 (versionCode **40**) over v0.17.6; confirm saved sessions, local activity, Node queues, Shizuku grant, overlay presets and Quest configuration survive.
2. In VRChat Mobile, ensure OSC enabled (preferably test with a known working external OSC monitor too). Open floater → In-game text chat. Tap editor, verify **Android keyboard appears**, input is visible/editable, and VRChat's broken input is not involved.
3. Type `Hello from TGC Companion`, then press Send. Check actual text appears above avatar/chatbox in VRChat. A "packet sent" response without an in-game message is **not a pass**; troubleshoot VRChat's OSC state.
4. Turn OSC off. Press Send. The Companion may still transmit UDP, but must **not** claim message delivery; the on-screen feedback should explain the lack of OSC acknowledgment.
5. Test 144 characters, 145 characters, Unicode text, and multiple line breaks. Confirm input validation, counts, no unsolicited messages and no crash.
6. Disable sound toggle; inspect optional OSC third argument behavior. Verify sending with sound on/off.
7. Try sending twice quickly; ensure rate limit and preserved draft with clear feedback. Switch pages or collapse overlay while a message sends, then return; verify no auto-resend and no draft leakage to another app.
8. Close keyboard, reopen, clear draft, copy to clipboard. Copy must not independently send any chatbox message.
9. Turn Shizuku/Node off while keeping overlay enabled; chat should still function because it uses only a loopback socket.
10. Confirm no messages are uploaded in Relay payloads, saved in local staff logs or sent to Discord.
11. Test small phones, landscape, rotated devices, background overlay focus and VRChat Mobile connection/scene changes.
12. On Quest, floater remains disabled by default; experimental Quest floater support remains separate and not guaranteed.
13. Verify published manifest `0.17.7 / 40`, permanent signing certificate, APK SHA256 and in-place upgrade.

## Limits

- VRChat has to allow OSC, listen on the default port 9000, and accept the chatbox OSC command. The Companion cannot read a message acknowledgment from VRChat because this OSC endpoint is one-way UDP.
- VRChat or a user's local firewall/other app can interfere with loopback OSC delivery. The tool doesn't promise that VRChat's native keyboard bug itself is fixed.
- This tool sends the VRChat public-in-instance **chatbox**, **not** Discord messages, a VRChat private DM, or typed input to third-party Udon world input fields.
