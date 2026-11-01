# TGC Co-Companion — Windows (development foundation)

> **Not a production release.** Development branch: `feature/co-companion-foundation`.
> Do not distribute this as a replacement for an installed TGC Node or Staff Launcher.
> This initial milestone has no functioning VRChat launcher, Node/Relay connection,
> account sign-in, or Companion pairing. It includes a **non-operational Tkinter
> navigation preview**, not a connected staff application.

## Product definition

A single Windows edition of **TGC Companion** for both desktop VRChat and PCVR.
It will *eventually* replace the existing Standalone Invite Node and Staff
Launcher **after a verified, in-place migration**. Existing customized-inviter
**Plugin Node remains independent**. Owner's Main Launcher is separate.

- **Windows-only staff without Android/Quest:** full Windows Companion experience:
  Home, social/friends, VRChat profile/worlds/groups/favorites/inbox, personal OSC
  and approved TGC rank chatboxes, tracking/Node, StaffDex/achievements, three-slot
  Staff Launcher parity plus a fourth owner-granted desktop slot, settings.
- **PCVR staff with Quest/Android Companion:** Windows reads PC VRChat logs and
  owns PC OSC endpoints. A paired Companion views relevant PC session activity
  and sends approved controls to the *selected* PC process. Standalone Quest
  tracking and local standalone OSC remain their own explicit modes.
- **Verified staff default:** at most **one managed VRChat slot**. Owner explicitly
  grants **2, 3 or 4** for an individual. No access from rank alone. A grant is
  tied to verified immutable identity and expires/revokes centrally. No promise
  to limit third-party programs running outside the Co-Companion.
- **VR concurrency:** no more than one managed VR session at a time; other granted
  slots operate as Desktop sessions unless a future explicit design changes this.

## First code milestone — delivered on this isolated branch

| File | Purpose |
| --- | --- |
| `tgc_cocompanion/permissions.py` | Fail-closed, **not** self-issuing 1–4 permission policy; atomic provisional slot reservations |
| `tgc_cocompanion/sessions.py` | Explicit process/OSC-port mapping; refuses ambiguous, stale or unknown targets |
| `tgc_cocompanion/osc.py` | Packet encoders for chatbox, float/bool/int avatar parameters and avatar eye height; local-loopback-only sender |
| `tgc_cocompanion/app.py`, `__main__.py` | Locked, non-operational Windows navigation preview; no real actions |\n| `tests/test_foundation.py` | Offline policy / multiple clients / OSC regression tests |
| `.github/workflows/co-companion-foundation.yml` | Nonpublishing Python compile and tests on this branch only |

**These are foundation components, shown in a non-operational interface only; they are not wired to a Windows Node service, verified authorization system or VRChat runtime.**
The port-to-process mapping is **not** inferred or invented; the planned launcher/
discovery layer must supply it only after validating ownership.

### Offline tests

From repository root, with Python 3.11+:

```sh
PYTHONPATH=co-companion python -m unittest discover -s co-companion/tests -p 'test_*.py' -v
```

No real UDP sends, VRChat API calls or Relay connections occur in tests.\n\nThe **locked UI preview** can be opened locally on a Windows development machine\nwith Python/Tkinter installed:\n\n```sh\nPYTHONPATH=co-companion python -m tgc_cocompanion\n```\n\nIt deliberately has no live features or staff verification, and is **not a release**.

## Source baselines verified before development

As of November 1, 2026, the **public** `TGC-Node-Updates/main` updater identifies:

| Current desktop package | Version | Source inside published ZIP |
| --- | --- | --- |
| Standalone Invite Node | 1.1.11 | `tgc_invite_node.py`, `tgc_vrchat_multilog.py`, updater helper |
| Staff Launcher | 1.1.6 | `TGC_Staff_Launcher.py`, `tgc_staff_node.py`, `tgc_vrchat_multilog.py`, updater helper |
| Customized inviter Plugin Node | 1.1.11-plugin | **Unchanged, independent** |

The Staff Launcher currently provides **three** VRChat slots (one VR plus Desktop
slots), embedded Node, staff verification, desktop Terms and updater. Published
ZIP source remains authoritative pending complete extraction and behavior review.
Do not claim the legacy systems are migrated until end-to-end tests pass.

## Security and compatibility requirements

1. **Authorization:** backend-issued and refreshed verified staff identity, plus
   owner-controlled `max_instances` integer **1–4**. Local files or a Discord
   role cannot promote the quota. If status is unknown/expired, no **new** launch
   is permitted, especially no extra slots. Preserve already-running sessions.
   Backend/API design and approval are **not implemented** yet.
2. **Migration:** preserve Node's existing encrypted enrollment credentials and
   immutable Node identity, SQLite pending queue, accepted/rejected ACKs, client
   attribution, and terms acceptance. Never simply delete old installs. Ensure
   exactly one active collector/uploader for each enrolled Node. Provide rollback.
3. **Tracking:** parse multiple PC VRChat output logs with verified process/log
   mapping; no duplication of Invite Network sightings from paired Quest/phone.
   No logs -> explicit warning, **not** zero players. Maintain opted-in local
   staff operation and minimized data upload for authorized invite work.
4. **OSC:** the PC owning VRChat sends to its *specific* local port. A paired
   Quest/phone sends commands to Co-Companion, **not** to an unrelated client.
   No OSC UDP packets or privileges exposed directly to the local network.
   Remote control must require authenticated, encrypted device pairing.
   Lost remote connection releases any held mic/PTT and stops unsafe rotations.
5. **Features:** port social tools, StaffDex and hidden-achievement policies
   without changing who qualifies, leaking secret triggers in public notes,
   auto-synchronizing account passwords, or bypassing verification.
6. **Terms:** preserve mandatory desktop agreement and independent per-application
   record; never treat an old Node or Android license approval as Co-Companion
   Terms consent without an explicit policy decision.
7. **Release:** separate source-first checked code, full integration tests,
   migration/downgrade tests, and Windows device testing before signed download,
   in-place updater and legacy package retirement. **No live production changes
   have been performed for this foundation.**

## Near-term development work, in order

1. Extract and audit both published Windows ZIP sources (no stored credentials).
   Reuse the *existing* `tgc_vrchat_multilog`, queuing, ACK, Terms, and updater
   components without rewriting their semantics.
2. Add a testable backend adapter for owner-issued quota and verified identity.
   Define revocation, expiry, rate limits, launch-slot gating and migrations.
3. Build Co-Companion's Windows UI with all promised sections. Only enable
   real actions after correct enrollment and verified capability.
4. Build the Windows Node/launcher adapter and a one-collector migration path.
5. Add secure local Quest/phone pairing, source switch between standalone/PCVR,
   remote OSC and verified user-experience testing.
6. Keep the standalone Node/Staff Launcher packages maintained until migration
   and rollback are proven. Keep Plugin Node untouched.
