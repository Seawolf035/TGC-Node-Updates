# TGC Co-Companion — Windows / Quest-PCVR Integration Contract

**Status: design + offline code only.** Nothing in this document is an
implemented live connection, approved Relay permission, or replacement Node.
This applies to the dev branch `feature/co-companion-foundation`.

## Source reuse / preservation

The unchanged `tgc_cocompanion/legacy/tgc_vrchat_multilog.py` was recovered
directly from the officially published
`TGC_Invite_Node_v1.1.11_Mandatory_Terms_Update.zip` inside
`Seawolf035/TGC-Node-Updates/main/updates/standalone`, not re-created from
memory. The extracted source is **12,407 bytes**, Git blob
`2d4925d20c97a3c2f8efe20fd7ca8f39b004dfb2`. The legacy module watches
multiple active `output_log_*.txt` files, handles room transitions, join/
leave observations, stale log warnings and the read-only VRChat process check.

The new `tracking.py` **adapts** this existing watcher; it does not change
the standalone Node's existing queue/Relay logic or copy the standalone
`tgc_invite_node.py` launcher, DPAPI code or Terms flow into a runnable UI.
The adapter starts at EOF to avoid sending historical joins on startup, keeps
only local counters/status, and intentionally has **no Relay uploader**.
The original underlying watcher temporarily retains active VRChat IDs in RAM
to count players; the adapter does **not persist or forward** those IDs.

`log_diagnostics.py` inspects output file metadata, not contents. It shows
missing/empty/stale vs recent logs, so no logs can never be reported as
"zero encountered people". Dev tests inject synthetic files.

## PCVR and standalone headset source selection

| User mode | Authoritative log/OSC source | Connected mobile role |
| --- | --- | --- |
| Standalone Quest VRChat | Quest Companion's own authorized log/OSC path | Runs locally; PC not required |
| PCVR using Quest | PC Co-Companion reads PC VRChat logs and owns local OSC | Quest Companion becomes paired viewer/controller |
| PCVR using Vive/Index/etc. | PC Co-Companion reads PC logs and owns OSC | Optional Android phone becomes paired viewer/controller |
| Windows Desktop-only | PC Co-Companion reads PC logs and owns OSC | No mobile device required |

Never upload duplicate sightings from two devices for one PC VRChat session.
Never claim that running the Android app over PCVR creates a headset overlay;
Quest Link, Steam Link, Virtual Desktop and native PCVR overlay usability
must be individually validated before adding a user-facing guarantee.

## Secure pairing (NOT implemented)

A future local encrypted transport must provide a **server-verified staff
identity** and a **server-issued, staff-identity-bound pairing authorization**
for each device. Device discovery and an on-screen QR code must not confer
access merely by appearing on the same Wi-Fi network. Pairing requires
explicit opt-in/confirmation from both sides, short-lived codes, replay-safe
challenge response and revocation. No permanent shared secrets embedded
inside the public Android APK or editable PC settings.

The existing `PcvrOscBridge` cannot accept incoming connections on its
own. It can only receive an authenticated `PairedDevice` value from a future
trusted transport adapter; incoming JSON must **never create this value
directly**. Both the authoritative staff grant and this pairing approval
must be fresh and bound to exactly the same immutable staff Discord ID.

All remote controls additionally require the Windows desktop agreement,
an **explicit PCVR mode**, a freshly verified Windows staff session and
an exact approved local PC VRChat process/OSC port mapping. On account
switch/logout/lock, remove permissions and connections. Pairing a phone
must not silently switch which VRChat client it controls.

## Remote OSC controls — current staged code

`pcvr_bridge.py` accepts only high-level, validated commands:
`chatbox`, `eye_height`, `avatar_parameter`. It checks:
- independently authenticated and unexpired device binding;
- staff grant live validity (no user-editable local increase of 1–4 slots);
- desktop Terms and explicit PCVR-mode flags;
- valid selected PID / OSC port from `SessionRouter` — never a blind send
  to port 9000 when multiple clients run;
- bounded sent timestamp, strict increasing per-device sequence numbers,
  and 5-second per-device chatbox cooldown.

Output uses loopback `127.0.0.1` only. No remote raw OSC arbitrary address,
remote login, instance spawning or remote microphone hold is provided.
Actual socket delivery cannot guarantee the VRChat client acted on the OSC.
No real UDP, Quest, VRChat or backend connection has been executed by tests.

## Legacy Node / Staff Launcher migration gates

The currently installed Standard Node or Staff Launcher remains the only
production collector/uploader. Before replacing either, development must:
1. Recover and inspect exact `TGC_Staff_Launcher.py`,
   `tgc_staff_node.py`, `tgc_invite_node.py`, DPAPI key stores and SQLite
   schemas from the published Windows ZIPs.
2. Inventory existing staff-specific Quick Deployment Node identities, keys,
   encrypted storage, pending queues, updater state and Terms acceptance.
   **Never echo plaintext credentials, migrate someone else's Node enrollment,
   erase queues or silently mint a new device identity.**
3. Use explicit, backed-up, in-place migration with rollback and durable
   already-processed sighting dedupe. Only one writer/uploader per enrolled
   Node and log stream, including old process coexistence.
4. Preserve current Staff Launcher launch processes and one-VR-slot behavior;
   add fourth slot **only after** owner-controlled online authorization,
   with single default slot and maximum four. Treat external unmanaged VRChat
   launches separately from slots our app controls.
5. Compare exact feature/role/achievement behavior with Android Companion,
   including hidden StaffDex content and official role OSC privileges.
6. Implement TGC-backed authentication and new Windows Terms explicitly;
   local acceptance of other desktop programs is NOT this app's authorization.
7. Test real Windows devices, multiple simultaneous VRChat processes,
   Quest Link/Air Link, errors, disconnections, avatar controls, rate limits,
   privilege changes, live relay ACK and rollback before any release.

**Current scope:** Shared log watcher recovered and tested, local monitoring
adapter and log readiness diagnostics implemented and unit-tested, PCVR OSC
command bridge validated offline. **No GUI is wired to these services yet,
and there is no shipped Windows Co-Companion.** No Main Launcher source,
Node package, Plugin Node, Android APK, Relay or live production changes.
