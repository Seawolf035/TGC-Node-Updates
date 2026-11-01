# Milestone 4 — Relay-backed staff access and owner-approved 1–4 slots

**Status: cross-repository feature-branch implementation, not deployment.**
Date: November 1, 2026.

## Baseline and new code

The previous isolated Co-Companion foundation had **98 offline tests** passing
on Windows and Linux with a locked navigation preview, exact legacy log
watcher, a guarded Node supervisor, and a four-slot command/controller.

### Windows Co-Companion

- `tgc_cocompanion/staff_lease.py` implements a short-lived
  `RelayStaffLease` compatible with a future authenticated TGC Relay
  `cocompanion_access` call.
- The source of staff identity is the existing staff-launcher enrollment
  and DPAPI-protected Node credentials through the unchanged
  `StaffNodeService._auth_payload` and `_post` methods.
- Validates exact immutable Discord ID and Node ID, `staff_launcher`
  client type, deployment-code provisioning, server `verified` flag,
  1–4 integer quota and timezone-aware expiration. Rejects credentials
  from an unrelated node, different staff identity, wrong client type,
  missing/oversized quota, expired lease or unexpected response format.
- An explicit `refresh()` attempts online verification; error/revocation
  clears cached access immediately, never leaving a previously approved
  four-slot grant available. `current()` stops returning a lease after
  it expires. There is no background worker, automatic key enrollment,
  new API server, or automatic Node startup.
- `tests/test_staff_lease.py` + `tests/test_entitlement_integration.py`
  exercise lease parsing and the full gate-to-launch path with a synthetic
  Relay and mocked VRChat. The tests verify one normal slot, four
  after owner grant, revocation back to one, offline failure and
  preserving existing client processes without unauthorized new launches.

### Existing TGC Relay — separate isolated development branch

Repo: `Seawolf035/TGC-Invite-Relay`
branch: `feature/co-companion-instance-entitlements`.
This branch is **based on the existing v1.2.1 companion delegates
feature branch**, NOT current deployed Google Apps Script.
It adds three explicitly authenticated actions:

| Action | Auth | Meaning |
| --- | --- | --- |
| `cocompanion_access` | Previously enrolled, valid `staff_launcher` Node ID/key **and** current staff roster lease | Server-issued staff identity, 1–4 allowance, 120-second expiration |
| `set_cocompanion_instance_quota` | Owner Main Launcher Central Key | Assign 1, 2, 3 or 4 to exact Discord ID; 1 clears override |
| `list_cocompanion_instance_quotas` | Owner Main Launcher Central Key | List only explicit 2–4 approvals |

Extra quota data lives in script properties and is not accepted from
the Windows client. A linked mobile-only, rankless delegate cannot
obtain staff PCVR permissions. The staff roster must remain synced
and within the existing lease. The default for a **verified active**
staff member with no extra grant is one, never automatically four.

**Current Relay staging status:** source commit
`3f20c1a1bbd21fd2f6294e27686441f3be9c64e5`,
nonpublishing CI `41303610586` SUCCESS: 22 new authorization
cases plus previous mobile-rank, delegate and Relay regressions.
Detailed endpoint contract: `docs/CO_COMPANION_INSTANCE_ENTITLEMENTS.md`
on that Relay feature branch.

### Verification

**Co-Companion dual-OS CI** on commit
`0cbd697577fa701a835f4246be3048488e8f9d99`:
run `41303692433` SUCCESS, **118 / 118 unit tests passed**
on **both Windows and Linux**. All tests use mocked responses,
synthetic logs, fake processes and no live TGC credentials.

## Explicitly not yet implemented

- Actual owner-facing control widget for quota assignment in the Main
  Launcher. It must be integrated **only from the full currently
  owner-tested launcher source**, never from stale GitHub main.
- Production deployment of new Apps Script actions; these endpoints do
  **not** yet exist in the live Relay.
- Full Windows Co-Companion GUI verification enrollment, OS secure
  credential storage and Terms dialog. The current UI is still locked.
- Automatic staff lease background renewal and authorized logout.
- Guaranteed cross-process single Node collector/uploader, backed-up
  migration and rollback of the original DPAPI credential/SQLite queue.
- Real VRChat/Steam launch, OSC delivery or Quest/phone pairing.
- Social, StaffDex, achievements and other Android feature parity.

**Do not distribute/install this as a staff replacement.**
Do not deploy the Relay feature branch or modify the live Node
configuration/keys or updater automatically. Full staff release
requires rebase to actual deployed Relay, production-security review,
Windows end-to-end tests, installer migration checks and explicit owner
approval.
