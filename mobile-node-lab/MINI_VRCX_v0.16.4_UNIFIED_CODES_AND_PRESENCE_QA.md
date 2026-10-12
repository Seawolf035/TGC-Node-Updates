# TGC Staff Companion v0.16.4 — Automatic verification and VRChat presence dot

Prepared 2026-10-12. These are implementation notes and **device tests still required**, not a claim that live VRChat invites have been verified.

## One verification-code reader
- The main app and floating overlay each expose **one** `Verification code` screen, replacing separate TGC and Cult buttons.
- Verification **begins and finishes in Discord** for both systems. The Companion only reads the resulting personal-account VRChat invitations and displays/copies a code.
- The common `scanLatest` reader fetches the two VRChat invitation formats once per scan, examines recent verified invite types, and detects whichever valid code is most recent:
  - Gamer Music 3: `TGC-XXXX-XXXX` from its known TGC verification world and/or the verified invited instance's custom display name. Existing TGC character restrictions and approximately ten-minute expiry continue to apply.
  - Gamer Music 2 / Cult: `CULT-XXXX-XXXX` from the invite or the invited private instance's custom display name. Cult expiry is not assumed; its exact invite payload remains to be checked against a real invite.
- The scan prefers newer invitation timestamps and a complete v1/v2 record with a code when duplicates exist. Metadata calls are limited and deduplicated.
- An active code can be replaced automatically when a newer code arrives; one copy action works for either type.
- No Discord messages are sent. No new verification session is created. Personal VRChat cookies remain local, as before.

## Bubble dot represents **VRChat presence**, not Node tracking
- Green = **Online** (`active`)
- Blue = **Join Me** (`join me`)
- Yellow = **Ask Me** (`ask me`)
- Red = **Busy** (`busy`)
- Gray = **Offline**, unsigned-in or temporarily unavailable
- The floating service reads the signed-in personal account's status via the existing `VRChatApi.currentUser` at a 75-second interval, without Shizuku or an additional log reader.
- The expanded floating header shows the text status; Tools explains the color legend. Player tracking remains controlled and displayed on Home and never affects the status dot.

## Real-device tests
1. Upgrade in place from v0.16.3; preserve Node Shizuku permissions, tracking, local logs, personal login, staff grant, and overlay position.
2. Start GM3 verification in Discord. Receive a TGC private invite. Open Companion → Verification code and verify it automatically shows the `TGC-` code with Copy; no manual selector.
3. Start Cult verification in Discord and receive its invite. Verify the same screen switches to `CULT-` when the new invite arrives. Copy and paste into the existing Cult Discord verification prompt.
4. Repeat tests in floating tools while VRChat Mobile remains open. Confirm **one** Verification code button, automatic detection and no Activity switching.
5. Test a mix of older/newer TGC/Cult invites, v1/v2 duplicates, account logout, a rate limit or API error, and an invite with no accessible instance name. The reader must never fabricate a code.
6. Check ongoing scans replace older codes with newer ones while the window remains open. Avoid repeated high-rate API requests and respect the VRChat server response.
7. Change personal status to Online, Join Me, Ask Me and Busy, and wait up to 75 seconds per change. Check dot and expanded header agree. Test Offline and logged-out states (gray).
8. Pause/start the **Mobile Node** while signed into VRChat: dot color must not change solely because tracking changed.
9. Leave the game running with the collapsed bubble for 10–20 minutes and verify periodic status refresh does not open another Activity, require Shizuku, or disrupt monitoring.
10. Verify versionCode **31**, versionName **0.16.4**, signed release certificate continuity and valid updater SHA-256 after GitHub Actions succeeds.

## Deliberate limits
- VRChat status polling is periodic, not push-based; the colored dot may lag briefly.
- Cult's private-instance naming/payload has not yet been confirmed on a real invite; successful compilation does not prove code extraction.
- A gray dot can mean Offline **or unavailable**, not necessarily explicitly invisible to others.
- There is no automatic Discord code entry or verification submission; clipboard copying remains the supported workflow.
