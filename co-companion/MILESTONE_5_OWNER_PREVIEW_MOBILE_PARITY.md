# Milestone 5 — Owner Preview v0.2: Windows/Mobile Companion Feature Parity

Date: November 1, 2026. Branch: `feature/co-companion-foundation`.

**No staff rollout or production deployment.** This is an incremental
OWNER-ONLY functional desktop preview, not a replacement for the installed
Standalone Node, Staff Launcher or custom Plugin Node. The one existing
Invite Relay and owner Main Launcher v2.2.32 are unchanged by this milestone.
The working v0.1 1–4 owner-authorized VRChat slot functionality is retained.

## Code implemented — source-first

- `tgc_cocompanion/vrchat_personal.py`: Separate direct personal VRChat API
  HTTPS client implementing login, TOTP/email/OTP 2FA, securely stored
  account cookies (Windows DPAPI), personal account identity, friends
  pagination, friends complete snapshot, worlds (favorites/recent),
  world/user search, groups, modern inbox and logout. Passwords aren't
  persisted, VRChat API cookies don't enter Relay or Node config, and
  redirect-to-untrusted-host credentials are blocked. API request size
  bounded, username-lookup routes URL encoded and path patterns validated.
  Interfaces are derived from existing Android `VRChatApi.java` and
  `VRChatSession.java`. Personal VRChat identity does not grant staff
  privileges: TGC Relay lease is independent and still required.
- `tgc_cocompanion/staffdex_desktop.py`: Exact Mobile staff directory
  public JSON feed, roster rank/royalty labels, full VRChat online and
  offline friend scan, 5 official Gamer Music collection awards,
  11 leadership/owner/royalty achievement conditions, and earned-only
  hidden community encounter awards. Wesman Community Encounter remains
  dynamic non-staff only with fresh roster. Devinity and other special
  awards follow Mobile's exact friendship conditions. Complete StaffDex
  snapshots are Windows DPAPI encrypted and scoped to *both* immutable
  verified TGC Discord identity and signed-in personal VRChat identity.
  No partial scans or sightings unlock achievements.
- `tgc_cocompanion/desktop_osc_tools.py`: Up to 24 locally saved
  personal-account-scoped OSC phrases, rate-limited chatbox, up to 8
  rotating phrases (minimum 5s), eye height 0.8m / 2.0m / custom,
  bool/int/float avatar parameters, microphone PTT on/off with explicit
  safe release on mouse-up/logout/app close, plus 300s max hold.
  Rotation never blindly targets any client; it uses the existing
  verified PID-to-OSC-port router. Held PTT release can be retried
  after send failures, even if staff authorization expires.
- `tgc_cocompanion/desktop_pages.py`: Real interactive VRChat Account,
  Social, StaffDex, OSC Studio, and Home screens in the running Windows
  owner pilot. StaffDex displays all 16 normal awards plus only earned
  hidden awards. Social is deliberately READ-ONLY to avoid mutations
  during first owner test. Home has staff/VRChat/instance/StaffDex
  status with quick navigation.
- `tgc_cocompanion/desktop_theme.py`: Complete Windows-native dark
  navy/cyan ttk theme and styled lists; `owner_pilot.py` now has a
  high-DPI-friendly 1120x650 window and a persistent **Go to page**
  dropdown to keep all nine pages accessible at 1280x720.
- `co-companion/RUN_OWNER_PILOT.bat`: Uses the installed Python 3.13
  directly (fallback to `py -3`), never creates `.venv`. Checks
  Tkinter and imports/installs missing `psutil` / `pywin32` into the
  user Python account.
- `co-companion/owner_pilot_entry.py`: Explicit safe top-level entry
  for PyInstaller `--onedir` Windows owner-preview EXE, bundling
  Python 3.13/Tkinter/psutil/pywin32 and the exact Desktop Terms file.
  **Not code-signed**, owner only. No host Python / .venv / pip needed.
- `co-companion/README_OWNER_PILOT.md`: Updated real installation,
  upgrade from v0.1 (preserve existing `%LOCALAPPDATA%/TGC/CoCompanionOwnerPilot`
  enrollment), sign-in and 2FA, testing/limitations.

## Test and build evidence

Nonpublishing CI `.github/workflows/co-companion-foundation.yml`
tests on Linux Python 3.11 and Windows Python 3.13. Windows CI
also constructs the real owner-pilot Tk app with no credentials/network
and packages BOTH an explicit source ZIP and standalone Windows executable
ZIP using PyInstaller. Test modules:
`test_desktop_social.py`, `test_desktop_osc.py`,
`test_windows_gui_smoke.py`, plus all pre-existing tests.
**149 / 149 offline tests PASS on both systems** on GitHub Actions run
`41309141493` (also earlier v0.2 runs). The packaging workflow
creates two owner-only artifacts:
`TGC-CoCompanion-v0.2-OWNER-EXE-NOT-STAFF`
and `TGC-CoCompanion-v0.2-OWNER-SOURCE-NOT-STAFF`,
each with 14-day artifact retention. Latest test/build run should be
verified after final documentation commit before pointing owner to it.

CI is not evidence that live VRChat login/2FA, Windows OSC, official
roster network access, physical Quest/PCVR behavior or actual
VRChat.exe launch works without bugs on the owner's PC.
User has already personally confirmed v0.1's 1–4 instances working.

## User upgrade flow — no production updates required

1. Download the new owner-only EXE ZIP from GitHub Actions on the
   branch above. Extract into a **new v0.2 folder**, keeping `_internal`
   with the executable. Don't overwrite existing installed Node/Staff Launcher.
2. Run `TGC_CoCompanion_OwnerPreview_v0.2.exe`. Existing v0.1
   enrollment and Terms remain in per-user AppData; click Verify Pilot
   Access. The single Relay and Main Launcher are **already configured**
   for owner pilot; no new Relay deploy for social features.
3. Test 1–4 slots first; sign in to personal VRChat Account and complete
   2FA; browse Social and refresh full StaffDex; test OSC commands with
   selected running VRChat PID. Report actual Windows bugs to continue.
4. Old staff software, inviter, Plugin Node, Android APK, and live Relay
   are unaffected. Owner pilot still **does not run a Node sighting
   collector/uploader** to avoid duplicate logs.

## Remaining Mobile parity and Staff Launcher completion

- Full in-place legacy Node identity/SQLite queue migration with exactly
  ONE active uploader; Windows process + enrollment exclusivity and
  rollback before retiring old Staff Launcher.
- VRChat profile details, user notes, friend request/invites, inbox
  actions, avatar favorites/extended favorites, groups admin tools,
  worlds/bookmarks/instance creation, saved themes, detailed social
  notifications and pinned friend alerts.
- Persisted StaffDex rules/achievements parity beyond current collection
  catalogs and Mobile's per-user social helpers where applicable.
- Secure encrypted Quest/Android pairing, explicit standalone vs PCVR
  source selection and overlay controls.
- Official updater, signed installer, planned staff rollout and
  production regression tests. These should be handled as further
  milestones, never silently enabled by a pilot download.
