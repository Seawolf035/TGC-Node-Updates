# Co-Companion v0.7.0 — Windows UI Color Studio and StaffDex Filters

**Update from the EXE itself:** Open Co-Companion → **Updates → Check for
Updates → Download & Install EXE** after CI publishes the v0.7.0 owner
prerelease. The update installs beside your previous version, performs the
frozen Windows UI smoke test, and automatically retargets your Start Menu
shortcut. No Python, BAT, or manual ZIP replacement. This is **owner-preview
only**, not a staff deployment, and does not modify the one live TGC Relay,
Main Launcher, Node, Quest, Android or staff update feeds.

### Custom desktop UI Color Studio

- Open **Settings → Open 10-Channel Color Studio** or its sidebar page.
- All **10 Mobile Companion channels** are separately editable: Background,
  Card Surface, Raised Panels, Input Fields, Accent, Borders, Icons, Buttons,
  Fonts, and Subtext.
- Import exactly **six VRChat UI codes** (Highlight, Icons, Buttons,
  Background, Fonts, Subtext) using the clipboard button. Its ten-channel
  surface colors are derived using the same channel mixing as Mobile.
  Export copies six colors in VRChat's exact import/export order.
- Save up to **25 named custom palettes**, delete or replace them with an
  explicit overwrite confirmation, and apply them. Records are bound to the
  TGC Discord ID, stored only in the owner pilot AppData, never uploaded.
  Built-in themes remain available for all staff regardless of rank.
- **Authorization:** custom editing, saved colors, import/export and
  activation require a **currently valid authenticated Relay lease** whose
  verified `staff_rank` is Admin+ and matches the enrolled Discord ID.
  Unknown/older Relay rank returns an explanatory lock instead of incorrectly
  granting Admin. Local theme files, roster display colors and VRChat groups
  cannot unlock it. No creator override is forged on the client.
- The last active custom color palette restores on the next successful
  verified Admin+ lease; choosing a built-in theme clears the active custom
  choice. This is a UI-only feature, not a staff permission.

### StaffDex usability

- Search by staff name or exact VRChat account ID.
- Filter by official roster rank (Royalty through Trial Moderator).
- Filter to Collected, Missing, or Not Scanned. This filtering is
  presentation-only: no progress changes, no new friend scans and no
  additional Relay requests.
- The existing read-only roster, full friendship scan, completed DPAPI cache,
  rank colors, achievements and links to individual staff profiles remain.

**QA:** offline unit tests verify imported code order and channel blending,
invalid hex rejection, per-account isolation, 25-preset limit, safe overwrite,
corrupted preferences, authenticated Admin+ gates, and StaffDex filtering.
Windows packaged EXE smoke checks that the Color Studio opens and exports a
valid six-color code without needing any external login. CI does not replace
the owner's actual Windows/VRChat validation. Some rank-gated features will
stay locked until the existing Relay starts returning authenticated
`staff_rank` metadata; **do not change production Relay just to unlock them**.

---

# Co-Companion v0.6.0 — Desktop Companion Parity Milestone

**INSTALL/UPDATE:** If you already have the owner-only v0.5.0, v0.5.1 or
v0.5.2 Windows EXE, use **Updates → Check for Updates → Download & Install EXE**
after the v0.6.0 verified GitHub prerelease is published. No manual ZIP, Python,
or BAT is needed. The app verifies the public owner preview asset checksum,
stages it side-by-side, tests EXE startup, restarts and retargets your Start Menu
shortcut. You can reopen the previous EXE folder to roll back.

## What works in this milestone

- **StaffDex loading fixed:** a public authoritative TGC roster can now be
  loaded separately from a long full-friend scan even when an ephemeral Relay
  lease is absent. The scan requires accepted desktop Terms, an enrolled
  Discord identity and personal VRChat login. It is **local collection only**,
  never staff authorization, and saves ONLY complete friend snapshots under
  user-bound Windows DPAPI. Failed scans explain what failed, preserve the prior
  complete result, and never count partial friend lists. Previous completed
  snapshots are displayed again after personal login. Staff rank colors match
  the official roster; a staff row opens its VRChat profile with double-click.
- **Staff Chatboxes:** exactly the 12 fixed phrases and tiers from the Mobile
  `TgcOfficialChatboxes.java` catalog. Five common presets require current
  owner-preview Relay staff verification; rank-specific presets require
  `staff_rank` in the live **authenticated** Relay response, which the old
  backend may not supply (these higher labels remain safely locked). The exact
  three Royalty overrides are preserved. Staff labels cannot be unlocked
  by selecting a client-side role or by scraping the public roster. Selected
  template IDs persist per personal VRChat account, never auth claims. OSC
  messages send ONLY to the selected authorized local VRChat PID; official
  rotations stop on downgrade, revoked lease, account switch or deselection.
  Existing personal chatbox editor and rotation remain unaffected.
- **UI Colors:** Settings includes 12 public Mobile Companion built-in named
  palettes, chosen locally and persisted across desktop launches. The private
  exclusive delegated theme is intentionally unavailable. No colors give staff
  privileges. Full ten-channel custom editing and six-color VRChat palette
  import remain OPEN pending a verified Admin+ rank signal.
- **Explore:** unified person/world search using the logged-in personal VRChat
  API, quick browses recent/favorite worlds, online friends and groups, opens
  profiles/VRChat.com and bookmarks worlds. Partial searches are labeled when
  one API endpoint fails (no fake all-clear).
- **Recent Players:** manual opt-in PCVR history sourced ONLY from bounded
  recent local VRChat output log tails; excludes own logged-in user when known,
  deduplicates user IDs, supports filter/profile/VRChat.com/copy ID, and never
  writes encounter history, submits sightings, contacts the Relay or starts
  a new watcher/Node.
- All previous Windows owner preview pages, VRChat login email/TOTP, StaffDex
  achievements, OSC, session launcher, Settings and in-app updates remain.
  The EXE smoke check now opens the new pages before an approved release.

## Boundaries and next stages

The production TGC Relay/Node source, staff invite uploader, Android/Quest
Companion, and Main Launcher are NOT changed. No new Relay or Node process.
Cross-device phone/Quest pairing, overlays, staff authorized rank sync in the
live Relay, full ten-channel Admin+ editing, screenshot QA, and actual Windows
VRChat field testing remain OPEN. Do not claim complete Companion parity yet.
The owner preview EXE assets are publicly downloadable from the existing
public TGC-Node-Updates repository, but runtime staff privileges remain gated.

---

# Co-Companion v0.5.1 — Personal VRChat Login, OTP and Compact UI Fixes (Owner EXE)

**Install this updated Windows owner EXE from the successful v0.5.1 Actions artifact.** Close the old app, extract the inner Windows ZIP with `_internal` alongside the EXE, and run the executable. The first-run flow installs side-by-side and updates the Start Menu shortcut. The prior working v0.5.0 and local encrypted VRChat session are preserved. This is still **NOT A STAFF RELEASE**, and must not alter the single live TGC Relay, existing Nodes, staff update feed or Main Launcher.

**VRChat login fixes:** email `emailOtp` 2FA challenges are now handled correctly; authenticator (`totp`), email (`emailotp`) and recovery codes (`otp`) each use their official VRChat API endpoint. The old OTP dropdown is gone; a single contextual code field appears only when VRChat asks for 2FA. Ordinary codes auto-route based on the server challenge, while ambiguous combinations get explanatory email/authenticator buttons. No blind retries against alternate endpoints. The password is cleared from the GUI, never saved, and session cookies are saved only under Windows-user DPAPI. Saved sessions now receive a nonblocking real API check with clearer failures. **Personal VRChat login no longer requires a verified Relay lease**; staff-only launch/OSC/StaffDex capabilities remain gated by valid Terms and staff authorization.

**Navigation:** compact mode now shows clear pinned-page symbols, current-page context and a Find All Pages control (Ctrl+K) with hover tooltips, not 17 chopped-up 3-letter tabs. Sidebar size and selected-page contrast have been tuned.

**More Companion parity:** OSC Studio already offers 0.8 m / 2.0 m eye-height presets; v0.5.1 adds **Save My Height**, a local, account-specific custom height that reloads after sign-in. Existing personal profile, friends, worlds/groups/search, avatars, notifications/inbox, StaffDex, notes, presence and OSC controls remain in their respective working pages. Not yet full Android/Quest parity: cross-device pairing, floating Quest overlays and production Node uploader migration are not implemented or claimed. All services and personal account permissions stay separate.

**QA:** Offline tests cover `emailOtp`, TOTP vs OTP recovery, ambiguous codes, unsupported-method refusal, account-specific saved heights and compact navigation. Windows packaged EXE `--smoke-check` now creates full Tk UI, toggles compact mode, and checks that the contextual 2FA panel starts hidden, so missing packaged modules fail CI. Field validation with the owner's actual VRChat login is still required; do not transmit passwords or 2FA codes in troubleshooting messages.

---

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
