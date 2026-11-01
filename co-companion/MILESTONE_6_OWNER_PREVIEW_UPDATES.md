# Milestone 6 — Windows Owner Preview v0.3: in-app updater and deeper Mobile feature parity

**Date:** November 1, 2026
**Branch:** `feature/co-companion-foundation`
**Status:** OWNER-ONLY development preview, no production staff release.

The owner confirmed v0.1 1–4 managed VRChat instance counts work and v0.2
looks better but still needs more functionality and an updater. This milestone
delivers functional pages and a **non-destructive, owner-controlled
check/install/restart experience in the app**.

## Update architecture

Module `tgc_cocompanion/owner_updater.py` and GUI
`desktop_update_page.py` implement an **owner-preview channel** tied to the
existing public GitHub repo's successful `co-companion-foundation.yml`
workflow on the exact `feature/co-companion-foundation` branch.

- `Updates > Check For Updates` checks the latest successful workflow run
  and exact `TGC-CoCompanion-v0.3-OWNER-EXE-NOT-STAFF` artifact.
- `Open Latest Build in GitHub` uses the user's logged-in normal browser.
  GitHub Actions artifacts may require authentication; the app NEVER asks
  for or stores a GitHub PAT, GitHub password or owner Main Launcher key.
- `Install Downloaded Update ZIP` accepts the **original OUTER GitHub
  artifact ZIP**, not a random Windows EXE or extracted inner ZIP.
- The updater obtains GitHub's artifact SHA256 digest and compares it to
  actual downloaded ZIP bytes, checks every ZIP path for traversal,
  duplicate entries, symlinks and size limits; verifies the embedded
  `build_info.json` commit SHA/run ID/owner-preview channel/version.
- Installs to a **new per-user version folder** under
  `%LOCALAPPDATA%\\TGC\\CoCompanionOwnerPilot\\installed_versions`;
  never overwrites running program, Node SQLite/DPAPI, existing VRChat
  sessions or older version. Owner confirms restart explicitly. Old EXE
  remains intact for manual rollback. AppData enrollment is preserved.
- GitHub Actions build creates `build_info.json` pinned to its exact
  `GITHUB_SHA`/`GITHUB_RUN_ID`, bundles it into PyInstaller/Windows
  owner-preview source. Full SHA256 protection tested against actual
  GitHub Actions downloaded Windows v0.3 artifact using the staging
  code outside CI as well as offline synthetic ZIP tests.

**Limit:** Fully automatic GitHub Actions artifact download would require
authenticated GitHub API access. Preview intentionally opens the signed-in
browser for download and performs **verification, install and restart inside
the app**, without implementing a separate Relay or publishing a public
preview release. Future direct-download previews may use a separate
non-production prerelease asset channel with owner approval.

## Feature additions

- `desktop_more_pages.py`: **Profiles** for user/world/group details,
  checked friendship state, explicit confirmed personal VRChat friend
  requests, request invite, 1–2000 char private personal user notes.
  **Presence** for explicitly confirmed personal status and 32-character
  description edits.
- `desktop_collection_pages.py`: **Avatars** favorite avatar browser,
  avatar groups, detailed avatar lookup and confirmed "Wear Avatar";
  **Bookmarks** for local per-personal-VRChat-account world bookmarks
  (up to 100) and add-current-Social-world shortcut; **Inbox** with
  list, Mark Seen and confirmed Dismiss operations.
- `vrchat_personal.py`: additional API routes match Mobile's
  `VRChatApi.java` endpoint syntax. VRChat requests operate on the
  separately logged-in **personal VRChat** account, never on TGC
  Relay or Invite Network staff permissions. Writes require explicit
  confirmation from Tk UI.
- Original v0.2 Home, dark theme, one-to-four permitted VRChat sessions,
  VRChat login/2FA, Social friends/search/worlds/groups, StaffDex,
  OSC studio, tracking diagnostics, separate mandatory Desktop Terms
  and owner pilot lease are all preserved.
- Updated Go to page selector includes all new tabs, so horizontal
  notebook overflow does not hide features at 1280×720.
- `RUN_OWNER_PILOT.bat` source option still uses system Python, no
  virtual environment. Normal Windows standalone EXE requires no
  Python, pip or venv.

## Testing/release

- `tests/test_owner_updates.py`: exact owner feature-branch and SHA
  pinning, valid successful runs, archive integrity, metadata validation,
  zip slip, known checksum requirement, mismatched build rejection.
- `tests/test_personal_actions.py`: profile fallback, personal friend
  request/invite, notes, presence, invalid IDs, logout behavior.
- `tests/test_desktop_collections.py`: per-user bookmark isolation,
  avatar selection and inbox action validation.
- Windows Tk GUI smoke verifies all pages. The PyInstaller
  **packaged Windows EXE itself** is run in `--smoke-check` mode after
  build, without VRChat or Relay network access.
- Nonpublishing GitHub Actions branch produces independent source ZIP
  and standalone Windows EXE ZIP, 14-day artifact retention.
  NO public production Node/Staff Launcher manifest, Relay action, owner
  Main Launcher, Android APK, mandatory staff update or rollout was changed.
- Source test counts and final successful Actions run must be confirmed
  after the latest package commit.

## Honest limitations / next work

These are genuine functional owner-preview surfaces, but not yet a complete
Quest/Mobile feature parity or Staff Launcher replacement. Remaining:
single-uploader Node transition with preserved DPAPI enrollment and
SQLite pending ACK queue; private PCVR Quest/Android pairing and overlay;
full avatar extended favorites and group/instance tooling; friends pins,
friend alerts, detailed history, theme editor, account-specific advanced
OSC rotation/custom status, notification settings; signed Windows
installer, migration/updater rollback, actual physical owner tests.

**Do not install the owner preview for other staff.** Existing live TGC
infrastructure stays unchanged. User can test v0.3 without another
Main Launcher or Relay update because the v0.1 pilot already works.
