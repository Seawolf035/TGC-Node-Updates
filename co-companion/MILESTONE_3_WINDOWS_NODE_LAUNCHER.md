# TGC Co-Companion — Milestone 3: Windows Node and VRChat Launcher

**Status: source-first development only.** As of November 1, 2026, this code
is on `feature/co-companion-foundation`, NOT the official desktop updater.
**No executable release, live Node takeover, account pairing, VRChat run,
authorization or Relay operation has taken place.**

## Authentic published source recovered

The unchanged Staff Launcher **v1.1.6** package in public
`TGC-Node-Updates/main/updates/staff-launcher` contains:
- `TGC_Staff_Launcher.py` (79,027 original UTF-8 bytes, archived at
  `tgc_cocompanion/legacy/TGC_Staff_Launcher.py`).
- `tgc_staff_node.py` (30,522 original UTF-8 bytes, archived unchanged at
  `tgc_cocompanion/legacy/tgc_staff_node.py`).
- Shared `tgc_vrchat_multilog.py` (same 12,407-byte canonical watcher
  recovered from official Standalone Node v1.1.11 earlier).

**Published Staff Launcher original behavior:** three hardcoded instance
slots, at most one designated VR slot, other slots use `--no-vr`; launching
`launch.exe` with per-slot `--osc=<base_in + slot - 1>:127.0.0.1:<base_out + slot - 1>`.
Defaults in input ports 9001..9003, output ports 9101..9103. The `launch.exe`
may hand off to a different `VRChat.exe` PID, so the existing code polls
running processes by OSC argument. Its embedded `StaffNodeService` uses
`tgc_staff_node_config.json`, `tgc_staff_node_key.dpapi` and
`tgc_staff_node_queue.db` in the original folder, and verifies the
`staff_launcher` client type plus owner-issued deployment-code identity before
starting watcher/upload. These file names and authorization rules were read
from actual source, not guessed.

## Implementation in current branch

### Permission-aware Windows VRChat launcher

`launch_controller.py` produces exactly the legacy launch argument pattern
and reserves slots under `InstanceGate`. Verified staff get one slot by
default from a **future backend grant**, while the owner can explicitly
authorize 2, 3 or 4. Exactly one configured VR slot (or zero) and additional
Desktop slots; the fourth is an authorized Desktop slot when another is VR.

- Launching is a **trusted local UI command**, never a direct remote
  Quest/phone packet.
- A new launch needs live verified entitlement + desktop Terms. Stale or
  missing entitlements fail closed. All four slots use fixed unique OSC
  input and output ports; no arbitrary network host.
- A launch-in-progress reserves capacity until a real `VRChat.exe` process
  with the correct per-slot marker is found. The temporary `launch.exe` PID
  is **not** mistaken for a running game.
- A process that launches late must not silently free its reservation and
  allow another simultaneous client; unresolved launches currently
  require manual future reconciliation. No automatic VRChat termination
  is performed on revocation, closing, or permissions changing.
- `windows_process.py` supplies a bounded launcher (no shell), a
  `VRChat.exe` process finder using *exact argument* matching, and
  ambiguity refusal. The process adapter is not connected to a real
  UI/backend yet. Actual VRChat launch not attempted.

### Safe preserved Node takeover interface

`migration_inventory.py` checks **only file existence** and reports
the original Staff Launcher source/DPAPI encrypted key/config/SQLite queue
and possible SQLite WAL/SHM sidecars. It **does not read secrets, rows, keys,
configuration values, account IDs or credentials**, and never considers
mere file presence sufficient for migration.

`legacy_node_supervisor.py` is a fail-closed interface to the unchanged
embedded `StaffNodeService`. It requires ALL of:
1. Fresh, valid immutable Discord staff identity and permitted grant.
2. Accepted **new Co-Companion** desktop Terms.
3. Explicit trusted migration approval.
4. Trusted proof that any previous Node is stopped.
5. `StaffNodeService.verified_session` from existing verified Relay
   enrollment; matching identity, `staff_launcher` client type and
   `deployment_code` method.

Only after these checks would it construct/load the real legacy Node and
call its `start()`. **The production adapters proving 3 and 4 are NOT
implemented**, so nothing in the app instantiates this supervisor or
actually starts the legacy Node. Full cross-process old-vs-new exclusion,
migrating queue and credentials with safe backups, Terms consent and
recoverable rollback must be implemented and physically tested before use.

### Existing Windows/Quest boundary

Quest/phone-to-PC OSC must still use future secure pairing and authenticated
encrypted transport. No listener or pairing bypass exists in the current
PCVR OSC bridge. Existing standalone Quest tracking remains separate.

## Test evidence

The nonpublishing GitHub Actions workflow now runs **Linux AND Windows** jobs.
Both compile all Co-Companion Python source and run the *same* offline unit
suite. **98 tests passed on both operating systems** (run `41302480242`,
Nov 1, 2026). Coverage includes:
- owner-grant one-to-four limit, expired/revoked grants, in-flight slot
  counting, launch failure, 1VR/desktop slot mapping;
- legacy exact `--osc` and `--no-vr` argument construction;
- actual-PID vs launcher-PID distinction, duplicate/ambiguous clients;
- safe read-only migration inventory without opening credentials;
- blocked Node starts on missing authorization/migration or mismatched
  legacy enrollment, stop-on-revocation and failure handling;
- existing multi-log tracking, PCVR OSC remote-action validation, privacy,
  rate limits and replay protection.

Two additional regression tests confirm that a legacy Node's upload thread and watcher must really stop before the supervisor reports a safe shutdown. The Windows job identified a bounded filesystem timestamp rounding issue in
metadata-only log diagnostics. It is fixed with a 2-second future skew
tolerance and a regression test; rerun confirms Windows and Linux pass.

**No CI test validates actual VRChat launching, Windows pywin32/psutil
packaging, live Relay token issuance, encrypted Node key decryption, linked
client behavior, full UI, or headset pairing.**

## Next milestones

1. Implement TGC-backed Windows staff auth and server-issued owner grants;
   arrange Terms acceptance and revocation handling, never self-issue slots.
2. Create trusted Windows single-Node takeover gate, with cross-process
   coexistence detection and backups preserving all original DPAPI keys,
   SQLite queues/ACKs, Node identity and updater settings.
3. Wire VRChat launch/process status into a usable Windows UI, with explicit
   troubleshooting for ambiguous ports and late process startups.
4. Bring Windows social/StaffDex feature parity, then secure Android/Quest
   PCVR pairing and selected-instance remote controls.
5. End-to-end Windows/VRChat/Relay/Quest tests and safe in-place upgrade.
   Keep Standalone Node v1.1.11, Staff Launcher v1.1.6 and customized
   Plugin Node v1.1.11-plugin unchanged until all gates pass.
