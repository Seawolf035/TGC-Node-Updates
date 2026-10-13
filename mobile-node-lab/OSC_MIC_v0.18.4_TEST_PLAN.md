# Staff Companion 0.18.4 — Experimental OSC Voice PTT on VRChat Android

## Scope and uncertainty
- Implements VRChat's **documented** OSC input `/input/Voice`: an int32 `1` press / int32 `0` release, sent to `127.0.0.1:9000` like the existing chatbox and avatar OSC tools.
- Official documentation defines the input behavior **when Toggle Voice is disabled**: `1` represents held/unmuted, `0` represents released/muted.
- **Unverified on VRChat Android**: its client may ignore OSC input control, or the mobile microphone limitation may supersede it. Local UDP send is not an in-game mic-state acknowledgment.
- No Android microphone recording, capture permission, device input interception, OS restrictions bypass, VRChat API changes, new tokens, server communication, Relay, Launcher or Node invite changes.

## User experience
1. Open floating Companion > OSC > **Experimental microphone input**.
2. Tap **Hold PTT via OSC (1)**; check VRChat's own microphone icon while speaking. The Companion reports only that a local OSC packet was *sent*.
3. Tap **RELEASE PTT via OSC (0)** to release.
4. While held, the floating panel has a persistent *quick-release strip* above all scrollable content, on every tab. A red PTT badge appears on the bubble when collapsed.
5. **Emergency release** sends `0` even if no local hold is currently recorded.
6. Continuous hold automatically releases after **five minutes**, and the service attempts release on user stop, access revocation and normal destruction. These are best-effort: Android can terminate the process without callbacks; always confirm VRChat mic status yourself.
7. No saved mic-held state. Background restore never re-arms the mic.
8. A failed send clears the tentative hold state and shows the error. A queued release invalidates any not-yet-transmitted press and is scheduled on the same OSC event worker.

## Manual functional test on device
- Turn OSC on in VRChat Mobile's Action Menu; keep VRChat running.
- Confirm VRChat Mobile microphone mode is actually Push-to-Talk. Disable Toggle Voice if available.
- Start with the mic visibly muted. Open the Companion OSC tab; tap Hold. Observe whether the VRChat microphone HUD changes, or whether a friend can hear audio.
- Release. Confirm VRChat microphone HUD is muted again and audio stops.
- Test a second hold/release, rapid hold/release, and the emergency release button.
- Scroll deep within the OSC panel or navigate to Social; confirm persistent quick release works without scrolling.
- Collapse just the panel; verify PTT indicator on the bubble and that opening the bubble allows release.
- Stop/hide floating bubble; verify best-effort release; confirm manually in VRChat. Test five-minute automatic release separately.
- If Android client ignores packets, label the experiment as **unsupported** instead of claiming success.
- Regression test chatbox/typing, selected rank-locked official tags, personal chatboxes, Shizuku Node, staff authorization, world bookmarks, Quest overlay constraints, and v0.18.3 popup confirmations.

## Technical verification
- Android versionName 0.18.4 / versionCode 47.
- `voiceInputPacket(boolean)` encodes '/input/Voice' OSC string, ',i' type tag and 4-byte big-endian `1` / `0`.
- GitHub Actions debug compile must pass first; signed release must use persistent certificate and in-place install verification. Physical Android mic success cannot be proven by CI.
- Documentation: https://docs.vrchat.com/docs/osc-as-input-controller
