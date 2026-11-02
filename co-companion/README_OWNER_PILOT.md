# TGC Co-Companion v0.5.0 — Windows Owner Preview EXE (recommended)

**This supersedes the old Python-launch instructions below.** The v0.4 Python
installation had a startup failure because its inherited source-updater file
list did not download the v0.4 shell modules. The owner-approved next build is
a **self-updating EXE**. Download the latest successful
`TGC-CoCompanion-OWNER-EXE-NOT-STAFF` GitHub Actions artifact, extract its
inner `TGC_CoCompanion_OwnerPreview_v0.5_WINDOWS.zip`, keep `_internal`
beside `TGC_CoCompanion_OwnerPreview.exe`, and **double-click the EXE**.
The app will offer to copy itself into your existing owner AppData install
root, smoke-check the installed copy and create the **TGC Co-Companion**
Start Menu shortcut. Launch it via Start or pin it to the taskbar thereafter.
No Python, BAT, extra Node, Relay or admin rights are required. The earlier
versions and credentials remain untouched.

**Future EXE updates** use the separate public GitHub owner-prerelease channel
when an approved release asset has been published. The EXE's Updates page can
check/download/checksum/stage/smoke-check/restart and retarget the Start Menu
shortcut automatically. **A successful Actions artifact by itself is NOT a
public GitHub Release**, and thus does not activate one-click EXE downloads.
The initial owner preview build remains manually downloadable; do not claim
automatic release publication. See `co-companion/README_OWNER_EXE.md`
for owner-approved publishing rules and rollback.

**Owner-only. NOT STAFF RELEASE. No changes to the single existing live
TGC Relay, Main Launcher, Staff Launcher, Node uploader or staff update feed.**

---

# TGC Co-Companion v0.4 — Windows Owner Preview / Python

**Recommended path for existing v0.3 Python owners:** Open **Updates → Check For Updates → Install Python Update → Restart**. The verified updater downloads only approved source from the latest successful CI commit, stages it side-by-side and keeps the previous Python version for rollback. **Do not install an EXE and do not replace your existing staff Node or the one Relay.** Only Windows/Python owner testing is authorized.

## What's new in v0.4

- **New desktop shell:** full-height dark/cyan header, responsive left navigation, grouped feature areas and page details, and existing working functional pages hosted without replacing their APIs or permission checks.
- **Discoverability:** `Ctrl+K` opens a searchable page palette; `Ctrl+F` focuses sidebar filtering; `Ctrl+1–9` opens common pages; `Alt+Left` returns to the previous page. A fallback page-picker remains at the top, including compact mode. No more horizontally overflowing main tabs.
- **Pin your most-used pages:** up to eight pinned sidebar shortcuts; recent pages and last visited page persist to a new **UI-only** `co_companion_navigation.json` in the same owner's AppData. No keys, secrets or VRChat data in that preference file.
- **Overhauled Home:** two-column live staff access, personal VRChat, managed sessions, StaffDex cards plus direct quick actions; read-only status data only.
- **World Bookmarks:** instant local filter/search, shown/total counts, sorted display and Copy World Link; bookmarks remain scoped to the personal VRChat account and never upload to Relay.
- **New Settings page:** compact sidebar toggle, optional startup at Home, reset page pins/history, open installed versions folder for rollback and safe clipboard diagnostics (no account IDs/tokens/cookies).
- **Preservation:** old UI functionality, login/2FA, StaffDex roster, permission-controlled 1–4 instances, personal avatar and notification actions, OSC studio, Python self-updates, existing Terms and DPAPI/local settings are retained. No embedded Invite Network uploader, new Relay, Quest/phone pairing or production rollout.

**Testing:** New tests cover navigation persistence, page search, pin limits, compact mode, page switching and Windows 1280×720 GUI smoke. Release only after final Windows+Linux CI and owner Windows field testing; CI alone does not prove a finished staff replacement.

---

# TGC Co-Companion — Python v0.3+ with built-in Updates (owner preview)

**Recommended Windows owner installation (replaces EXE delivery):** If your running v0.2 has no Updates tab, use the one-time `TGC_CoCompanion_Python_SelfUpdating_v0.3_Installer.zip` package, launch `INSTALL_FROM_V02.bat`, then launch `RUN_OWNER_PILOT.bat` inside the newly installed Python folder. The one-time bootstrap downloads ONLY source files from the latest successful GitHub Actions commit on `feature/co-companion-foundation`, validates every file against the Git blob hash, and places the source under `%LOCALAPPDATA%\\TGC\\CoCompanionOwnerPilot\\installed_versions\\python-<commit>`. Your existing v0.2 folder and all AppData enrollment, sessions, saved phrases and StaffDex remain in place. It requires Python 3.11+, Tcl/Tk, and network access to GitHub; no venv.

**After that, all updates are handled in Co-Companion itself:** `Go to page → Updates → Check For Updates → Install Python Update → Restart`. The built-in owner-preview Python updater queries successful CI runs from the **exact** owner branch, reads the immutable Git commit tree, downloads only allowlisted `co-companion/` files from `raw.githubusercontent.com` and checks each against its Git blob hash before atomically staging a new folder. The old version remains for rollback; no GitHub PAT or manual ZIP or EXE is required. A failed, incomplete or tampered update never overwrites the current app.

**Status:** GitHub Actions `41322794303` reported SUCCESS on code commit `05b56e313a2b86c074918afb3098c8e97e2ea989`, covering offline Windows+Linux tests and artifact packaging. This does **not** establish that the owner has installed or run it on Windows. EXE instructions below are retained for legacy archival installs; the Python route above is now recommended. This is owner-only, not a staff release, and does not update any live Relay, Node, Staff Launcher or Main Launcher.

---

# TGC Co-Companion — Windows Owner Preview v0.3

**Owner-only development build. NOT a staff release.**

Your working 1–4-instance authorization and existing **single** TGC Relay are
unchanged. Do NOT reissue your enrollment or redeploy the Relay/Main Launcher
simply to install this Windows preview.

## Install v0.3 over your working v0.2

1. Exit the old Co-Companion window. This does not close VRChat processes.
2. Download `TGC-CoCompanion-OWNER-EXE-NOT-STAFF` from the latest
   SUCCESSFUL GitHub Actions build on
   `Seawolf035/TGC-Node-Updates` `feature/co-companion-foundation`.
3. GitHub downloads an outer ZIP; open it and extract the contained
   `TGC_CoCompanion_OwnerPreview_v0.3_WINDOWS.zip` to a **NEW** folder.
4. Extract that Windows ZIP. Keep the EXE and its `_internal` folder
   together. Run `TGC_CoCompanion_OwnerPreview.exe`.
5. Existing Windows user enrollment, Desktop Terms, personal VRChat
   sessions, StaffDex data and saved OSC phrases remain in
   `%LOCALAPPDATA%\\TGC\\CoCompanionOwnerPilot`. They are not
   stored alongside the EXE. Sign in or Verify Pilot Access if needed.
6. This is a self-contained Windows EXE with a STABLE executable name across future owner previews, with **no Python, pip or .venv**.
   The original source/BAT artifact is available for debugging only.
   Windows may warn because this owner build is unsigned.

## NEW in v0.3 — Updates inside the app

1. Use **Go to page → Updates** and click **Check For Updates**.
2. If a newer owner-preview build exists, click **Open Latest Build in
   GitHub**. This uses your ordinary browser's GitHub sign-in, not a
   GitHub token stored inside Co-Companion.
3. Download the artifact named
   `TGC-CoCompanion-OWNER-EXE-NOT-STAFF` from the successful
   workflow. **KEEP THE OUTER ZIP UNEXTRACTED** for this step.
4. Return to Co-Companion, click **Install Downloaded Update ZIP** and
   select the untouched GitHub artifact ZIP.
5. Co-Companion checks the SHA256 of the entire downloaded artifact
   against GitHub's current successful workflow metadata, verifies the
   exact embedded commit/run identity and checks the ZIP's structure,
   then stages it under the SAME WINDOWS USER'S pilot AppData folder
   in a NEW version directory. It never overwrites the currently
   running EXE, TGC Node queue, credential key, existing VRChat clients
   or any staff application.
6. Accept **Restart Into New Co-Companion**. The previous EXE remains
   intact in its original folder; run that old EXE to roll back if
   needed. All per-user enrollment remains shared.

**Why isn't this one-click downloading yet?** GitHub Actions artifacts
may require GitHub authentication. We deliberately use your browser
instead of requesting/storing a GitHub PAT or introducing a second
public update server. Check/install/restart and rollback are now
handled inside the app. An owner-preview RELEASE channel could add
direct one-click downloading later without touching the production
staff updater.

## More Mobile/Quest Companion features added in v0.3

- **Profiles** — read full personal VRChat user details (using the
  modern profile endpoint with fallback), world and group details,
  check friendship, send a confirmed friend request, request an
  invite, and save a private personal-account user note (1–2000
  characters). All writes require explicit user confirmation.
- **Presence** — read/edit your personal VRChat status and
  32-character status message, with confirmation before writing.
- **Avatars** — browse your actual VRChat avatar favorites and favorite
  group names, view selected avatar details, and explicitly choose
  an avatar to wear (with confirmation).
- **Bookmarks** — save up to 100 personal-account-scoped local world
  bookmarks; bookmark a selected world directly from Social. These
  don't alter VRChat world-favorite quotas or TGC Relay data.
- **Inbox** — a dedicated notification list with explicit Mark Seen
  and confirmed Dismiss operations using personal VRChat API.
- **Updates** — in-app check/download guidance, integrity verification,
  staged install, restart, and old-EXE rollback.
- **Preserved from v0.2** — Home, dark theme, personal VRChat account/
  TOTP/email 2FA, online/offline Friends, user/world search, favorite
  and recent worlds, groups, StaffDex real roster and friendship-based
  achievements, OSC Studio with personal phrases, rotating chatboxes,
  avatar parameters/height, push-to-talk, original VRChat launcher and
  metadata-only tracking diagnostics.

## Security and limitations

- The existing **one Relay** and Main Launcher permissions panel still
  authorize the single explicitly enabled pilot Discord ID. No Relay
  or Main Launcher update is required solely for v0.3.
- Personal VRChat credentials/sessions remain separate from TGC
  enrollment. Passwords are never saved; cookies encrypted using
  the current Windows user's DPAPI. No Central Key is requested.
- **Co-Companion still does NOT run the production Node sighting
  collector/uploader.** Keep your old Staff Launcher/Node running
  for authorized TGC sightings until the single-uploader migration
  is tested. Do not copy old keys/queues into the preview.
- Native Quest/Android pairing, the PCVR overlay, full instance
  creation/group moderation, all extended avatar favorites, advanced
  notification rules, theme editor, and complete automatic tracking
  and self-update channel are **not yet implemented**.
- GitHub Actions artifacts expire; the Updates page should report no
  newer completed build if artifacts are expired or unavailable.
- Verified online functions are subject to VRChat's API limits.
  Avoid spamming refreshes or friend requests. Do not claim
  a UDP OSC packet guarantees in-game delivery.
- This app, its integrations and updater have been tested with
  synthetic/offline data and bundled Tk on Windows CI. They still need
  your physical Windows/VRChat testing before any staff-wide release.

## Source and development status

Full source is on the isolated
`feature/co-companion-foundation` development branch.
The nonpublishing Windows CI produces both an owner-only source ZIP
and a Windows EXE ZIP; no GitHub production/main release, staff
update manifest or Relay deployment changes are performed.
