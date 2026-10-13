# Staff Companion v0.18.1 — Official TGC rank-locked chatboxes

**Development baseline:** v0.18.0 / versionCode 43. **New version:** v0.18.1 / versionCode 44.  
**Source:** the owner's supplied `StatusItems_12.json` Magic Chatbox export (12 messages, original `UseInCycle` labels preserved as informational suggestions).  
**Signed live APK status:** validate using the existing GitHub Actions release workflow after source promotion; source commits or debug builds alone are not production approval.

## User-visible behavior
The floating panel's OSC → Live Chatbox → Manage rotating chatboxes now contains `TGC OFFICIAL STAFF CHATBOXES` above personal chatboxes. Existing default and 8 personal chatboxes remain available. Official presets are immutable, selected by ID, and never transmitted merely by toggling. Automatic rotation must still be enabled and Live Chatbox must still be started + confirmed explicitly.

Five common messages are available to verified active staff. Seven rank-specific messages unlock based on the highest current authenticated roster rank, including all lower tiers: Trial Moderator, Moderator, Head Moderator, Admin, Head Admin, Owner, The Royalty. The three Royalty IDs (including founder) are the entire override list, not separate Founder and Royalty lists.

A demotion removes higher-tier messages from the *eligible* active rotation immediately after authenticated rank refresh; selection IDs may remain saved locally for a later promotion, but a lower rank cannot transmit them as official presets. Staff access expiry or account change also prevents unauthorized use.

## Privacy and security
- The only new network field is `staff_rank`, returned by the existing authenticated Relay `node_status`. Android NEVER submits its claimed rank to the server.
- Rank is maintained process-locally by `StaffGate` and tied to approved deployment identity; no rank or staff permissions are persisted in Android preferences.
- Per-personal-VRChat-account preferences store template IDs only. No new cookies, tokens, remote chat logs or Relay text payloads.
- Old Relay response/no rank: verified staff may use common templates only, with the seven rank templates locked.
- OSC is always local UDP, no additional servers or permission requests. Existing user message pause, overlay/Quest controls, staff gate and no auto-start behavior preserved.
- Staff cannot be prevented from manually *typing* any text as a personal message. Rank restrictions govern catalog selection and rotation, not ownership of those words.

## Release verification
1. Confirm Relay's offline Node syntax + mocked Apps Script test suite passes and Main Launcher rank sync source is reviewed. Do not infer live Apps Script deployment from GitHub.
2. Android debug compile on the feature branch; verify version 0.18.1 code 44 and identical package/signature after promotion.
3. Sign with **existing** permanent GitHub Action secrets. CI must verify certificate matches installed previous APK, produce new checksum, publish to existing stable release URL and update manifest.
4. Check manifest version, code, SHA256, new release commit and action job result; do not fabricate app installation or test outcomes.

## Real device functional checks
1. In-place upgrade over v0.18.0 (without uninstall). Confirm login/staff deployment, Node outbox, Shizuku, Mini-VRCX, avatar favorites and existing personal OSC presets survive.
2. Verify exactly five common and seven rank messages, with original wording/brackets, locked labels and selection toggles. Toggle a common template; ensure it does **not** immediately broadcast.
3. On a newly deployed Relay + Main Launcher ranked roster sync, verify authorized Trial receives tier 1; Moderator tiers 1–2; Head Mod 1–3; Admin 1–4; Head Admin 1–5; Owner 1–6; the **three** Royalty IDs 1–7.
4. Try non-Royalty Owner, unknown role, stale roster lease, removed staff, swapped Discord node credentials, revoked node, wrong personal VRChat account. None should unlock tier 7.
5. Enable rotation and select common + ranked + personal messages. Confirm ordered rotation, time/battery on personal slots, silent official sends, independent 5/10/15/30 refresh and 15/30/60/120 switch intervals.
6. Manual chat must pause scheduled rotation roughly 20 seconds. Collapse bubble, change tabs, stop bubble and restart app: preserves selections but does not auto-broadcast.
7. While running, demote staff in roster; sync + refresh authorized status; restricted preset must disappear from next scheduler evaluation.
8. Test an old Relay that has no `staff_rank`; basic personal chatboxes must work and ranked official templates remain locked. Old Main Launcher ID-only roster sync should similarly not elevate.
9. Run basic invite-network, group browsing, OSC parameter/typing, mobile account, and signed updater regression checks.

## Coordinated release dependency
Main Launcher and Relay GitHub branches contain **source patches only**. Main Launcher running Windows installation must be updated to the ranked sync version, and updated Relay source must be deployed into the **same existing Apps Script project and Web App URL**. Avoid creating new projects or overwriting Script Properties/Sheets. No chat text ever goes through Relay.
