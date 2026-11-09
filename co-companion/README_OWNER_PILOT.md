# Co-Companion v0.13.0 — Group Search, Profile Artwork Fixes and Friend History

**Owner-only upgrade:** After CI and GitHub prerelease verification, use Updates → Check for Updates → Download & Install EXE → Restart. Previous versions remain installed for rollback. No staff, Node, Relay, Main Launcher or Android deployments.

## Fixed owner-reported problems

- Group discovery: Explore now searches **People, Worlds and Groups** together, and Social offers **Group Search**. The new authenticated personal VRChat query uses GET /groups?query=...&n=60&offset=0 instead of the users/worlds ?search=... parameter. Group results classify as Group, open their full Profiles and respect VRChat's published visibility.
- Artwork: Use only approved VRChat-owned HTTPS hosts, including vrcdn.cloud and vrcdn.live for signed image URLs. Prefer user icon/banner, then fall back independently to approved VRChat thumbnail/image candidates when the primary URL is expired or blocked. No cookies, Relay secrets or signed URL tokens leave the personal image fetch; URL contents are not logged. Limits remain 5 MiB compressed / 18 MP decoded, 32 images for 12 minutes, 2 bounded workers.
- Visibility: Visual Profiles now show safe per-image loading status (loaded, HTTP error code, no image URL, render error) rather than swallowing every image exception. This cannot guarantee that VRChat will serve an artwork URL; test with real Windows owner session.

## Included UI and VRCX-style improvements since 0.12.0

- Friends Hub has a searchable and filterable LOCAL DPAPI-encrypted friendship timeline from complete manual snapshots, with friend-profile navigation; no continuous tracking, Relay upload or invented past events.
- Improved Explore, Avatar collection, Bookmarks, personal Inbox, Recent Players and official Staff Chatboxes layouts. Explore, Avatar, Inbox and StaffDex views clear on sign-out; locally stored information is retained.
- Additional offline checks for group API routes and results, signed artwork candidates and expired-primary fallbacks, friend activity filtering, and frozen Windows executable UI smoke.

**Limitations:** First complete snapshot creates a baseline, not historical records. Real logged-in VRChat group results and artwork must still be tested on the owner's PC. Do not claim complete VRCX parity or full-window pixel QA.

---

# Co-Companion v0.12.0 — StaffDex, Social, Friends Hub and OSC Overhaul (Owner Only)

**Owner-only update path:** Updates → Check for Updates → Download & Install EXE → Restart,
after Windows/Linux CI and the owner prerelease are confirmed. The previous
installation remains available for rollback. No staff/Node/Relay/Main Launcher
or Android/Quest deployment accompanies this release.

### Focused workspaces
- StaffDex retains the same official roster, rank colors, complete friend scan,
  cached progress, hidden achievement eligibility and profile actions. The
  cramped directory/achievements side-by-side split becomes full-width
  **Staff Directory** and **Achievements** tabs, with two collection summary cards.
  Directory and achievements have independent vertical scrollbars.
- OSC Studio groups all existing actions into **Chatbox & Rotation**,
  **Avatar & Parameters** and **Voice** tabs, with one persistent VRChat PID
  target and honest UDP status. Existing launcher PID authorization,
  session lease, OSC routing, saved phrases, height presets, rotation and
  push-to-talk release safeguards are unchanged.
- Social browsing separates category/filter inputs from Load / Search and
  Load More actions instead of squeezing the results toolbar into one line.
  Friends Hub separates friend/group/favorite loads from filtering and pin refresh.
- Windows frozen executable smoke now asserts real StaffDex and OSC tab
  widgets exist, and existing image/Pages and no-network tests remain.

**Not yet complete:** This is the next incremental application overhaul,
not proof of perfect UI on the owner's monitor, nor full VRCX parity.
Screenshots and real runtime testing at 720p/1080p/4K are still needed.
Next pass: modernize the remaining Social, Inbox, Explore and collection
screens; plan VRCX-like activity summaries, richer favorites/history,
and instance/world tools that remain private and optional.

---

# Co-Companion v0.11.1 — Visual Profiles and Responsive Windows Layout

**Update from your installed EXE:** after v0.11.1 passes both CI suites and
publishes to the owner-preview prerelease channel, use **Updates → Check for
Updates → Download & Install EXE → Restart**. No manual ZIP installation.
The previous version remains available for rollback. Owner preview only;
no Node, Relay, Main Launcher, staff or Quest/Android deployment.

### Profiles: redesigned instead of raw JSON/text

- Dedicated visual identity header for each **VRChat user, world or group**
  including their public display name, subtitle/status, actual VRChat
  **profile picture and banner**, or world/group thumbnail/cover as provided
  by the authenticated personal VRChat API.
- Artwork downloads run asynchronously; the page stays responsive and shows
  dark placeholders when artwork is absent, inaccessible or fails. Only
  HTTPS images from approved VRChat image/CDN domains are allowed, without
  session cookies or staff tokens. Cross-site redirects are rejected.
  Compressed downloads are capped at 5 MiB and decoded dimensions at 18
  megapixels; in-memory LRU cache (32 items / 12 minutes) only, no image
  disk cache, no third-party tracking.
- Six readable detail cards display the fields applicable to the profile
  type (user status, member since, location; world author/visits/capacity;
  group members/visibility, etc.) rather than huge monospaced text.
  Long biographies/descriptions scroll in a dedicated content area.
- The prior **friend request, invite request, friendship status and private
  VRChat note** functions remain, still requiring personal sign-in and
  explicit confirmations for writes. User-only buttons and note editor are
  hidden for world/group profiles. Profile IDs can be copied. Asynchronous
  loading errors and competing tasks now show readable status messages.

### Responsive desktop window & settings

- Default window size adapts to the available screen instead of always
  forcing the same 1180×690 geometry. Minimum 760×500; tested window
  layout breakpoints from **760 to 3840 pixels**, including 800, 1024,
  1280, 1366, 1920, 2560 and 4K.
- The sidebar automatically uses the compact icon rail below 1100 px,
  **without changing your saved navigation preference**. At narrower widths,
  redundant Home/Updates header buttons and the page picker are hidden;
  Find Page (Ctrl+K), keyboard shortcuts, pinned pages and all features
  remain accessible. Normal sidebar returns when the window grows again.
- Profiles and Settings scroll vertically, including mouse wheel over
  embedded card widgets, keeping content available at short window heights.
  Settings breaks the wide color-editor buttons across rows.
- New **Settings → Display & Screen Scaling** offers 85%, 100%, 115%,
  130% or 150% control/text scale, saved locally and applied on next
  restart. 100% always retains Tk/Windows native DPI settings (no
  forced absolute DPI). This changes only UI preferences.
- New source `desktop_profile_view.py`, `desktop_profile_images.py`,
  `desktop_display.py`; Pillow is bundled in the EXE to decode common
  JPEG/PNG/WebP/GIF images and ImageTk supports actual raster display.

### Verification and limits

Unit tests validate user/world/group structured fields, approved image
URLs, image caching/decoding, corrupt display preferences and responsive
breakpoint logic. Windows executable startup tests instantiate Profiles,
render a sample user without any network, exercise multiple window widths,
create a real ImageTk photo and check UI modules are packaged. The tests
do **not** access real owner VRChat accounts or reproduce every Windows
DPI/multi-monitor configuration: screenshots and real-image behavior should
still be checked on the owner's Windows PC.

This is the first major **Profiles + application shell** overhaul, NOT a
claim that every older StaffDex/OSC/Node page has been redesigned. Follow-up
work should progressively migrate their dense legacy layouts to the shared
card/scroll patterns without changing permissions, Relay or Nodes.

---

# Co-Companion v0.10.0 — Native Windows Pinned Notifications + Alert Reliability

**Install via the EXE itself:** after the v0.10.0 owner prerelease passes
Windows/Linux CI, open **Updates → Check for Updates → Download & Install EXE
→ Restart**. No manual ZIP or Python. Previous versions remain available for
rollback. This is a public **owner preview**, not a staff deployment.

## Windows notification improvements

- Friends Hub → **Pinned Alerts** → Enable Pinned Alerts. Existing per-account
  offline→online detection, 15-minute open-window checks and silent initial
  baseline remain unchanged. Alerts still require a valid TGC staff lease,
  accepted Desktop Terms and the independently authenticated personal VRChat
  session. Missing/hidden users are never misreported as offline.
- **Also show Windows notifications** is a *separate, off-by-default opt-in*.
  With both switches enabled, friend-online events are delivered to the
  Windows shell as native notification balloons via `Shell_NotifyIconW`;
  Windows 10/11 may display these as modern notifications depending on system
  settings. Windows Do Not Disturb/Focus Assist and user preferences can
  suppress them. The existing in-app feed always remains the source of truth.
- **Test Windows Notification** sends a clearly identified test message and
  reports whether Windows accepted it, without faking an online friend event
  or changing any friend state. At most five shell notifications are submitted
  per observed batch; the full set still appears in the in-app feed.
- Native delivery uses only the Windows API through Python `ctypes`, with
  no PowerShell, shell commands, third-party toast package, elevated
  permissions, background service or network access. An app-lifetime tray
  icon is created on first successful native notification and removed during
  ordinary app shutdown. Non-Windows systems retain their in-app alerts.
- The Windows preference is saved *separately per personal VRChat account*
  within the same Windows-user DPAPI-encrypted alert state. Switching the
  general alerts toggle preserves the separate Windows preference but resets
  the friend baseline, ensuring no initial spam.
- **Reliability fix:** the app's shared async executor now reports whether a
  job was started. If an alert check arrives while another owner-preview task
  is running, it safely clears its busy flag and can retry on the next timer
  tick rather than becoming permanently stuck.
- Source update allowlist and packaged EXE smoke checks include the new
  `desktop_windows_notifications.py` module and toggle/test controls.

## Testing and boundaries

Offline Linux/Windows tests mock the Win32 tray API, verify registration,
notification update, shutdown removal, message sanitization, disabled-by-
default preferences, per-account isolation and corrupt DPAPI failure behavior.
The frozen Windows EXE startup check confirms the new Friends Hub controls
are packaged. **A real system notification still requires owner-PC testing**
because Windows may suppress or reroute notifications; passing mocks is not
equivalent to testing Action Center visibility.

NO TGC production Relay, inviter/sighting Node, staff software, Main Launcher
or Android/Quest code was changed. The app does not run in the background
after closing. Full Android/Quest Companion parity is not yet complete.

---

# Co-Companion v0.9.0 — Real VRChat Favorite Friends + Opt-In Pinned Alerts

**Installed owner EXE update:** Open Co-Companion → **Updates → Check for
Updates → Download & Install EXE → Restart** after the v0.9.0 owner
prerelease passes Windows/Linux CI. No manual ZIP/Python installer. The
previous install is kept for rollback. Owner-only, NOT a staff rollout.

## Friends Hub (Mobile Companion parity)

- **Load VRC Favorites:** reads the *actual personal VRChat favorite friend
  groups*, not Co-Companion pinned shortcuts, using the same official API
  paths and legacy fallback as the Mobile Companion's
  `VRChatFavoriteIndex.java`. It includes all selected favorite groups,
  deduplicates VRChat user IDs, and sorts favorites above normal friends
  in the existing Friends Hub. A complete refresh is required before replacing
  the prior result; a broken/missing group doesn't erase old favorites.
  The cache is per-VRChat-account, Windows-user DPAPI encrypted, and valid
  for **15 minutes**, so reopening the view doesn't hammer VRChat.
- **Pinned Alerts tab:** explicit **Enable Pinned Alerts**, **Disable Alerts**
  and **Check Now** actions. Only currently pinned friends can generate
  alerts. Enable requires accepted Desktop Terms, personal VRChat login and
  a current verified TGC staff lease (like Mobile's StaffGate).
  **The first observation is silent**. After the baseline, only confirmed
  offline → online transitions appear in the in-app feed and trigger a Windows
  sound. Newly pinned or absent/unavailable people receive a silent baseline;
  no false "offline" assumptions, unsolicited requests or mentions.
- Alerts are **off by default** and checked at most every **15 minutes**
  automatically **while Co-Companion is running**, or when Check Now is
  explicitly clicked. This is NOT a native Windows toast service and does
  NOT keep running after the app exits. There are no daemon processes.
  Alert state is account-scoped and encrypted with Windows-user DPAPI;
  notification text is session-only.
- **Account-switch safety:** friend/group lists, favorite cache displays,
  history capture and alert checks reject stale results when the personal
  VRChat account changes mid-request. Signing out clears the current
  Friends Hub display, including in-app alert history.
- Existing local 12 pinned-friend and 16 pinned-group shortcuts, manually
  triggered encrypted friendship history, and shared-instance sorting remain.
  Personal account cookies are never sent to the TGC Relay.

## Validation and boundaries

Offline tests cover actual endpoint shapes, fallback, 15-minute cache,
atomic complete-group refresh, account changes, pin-only alerts, first-seen
silence, offline→online changes, missing-user behavior, corrupt DPAPI state,
and required Python owner updater source files. Windows packaged EXE smoke
checks Friends Hub Favorites/Alerts controls exist. Live VRChat API and owner
Windows UI require field testing. No production Relay, staff Node, invite
uploader, Android/Quest code, staff updater or Main Launcher was modified.

**Still pending full cross-platform parity:** native background Windows toast
notifications, Android/Quest pairing and overlay Mini-VRCX, shared
mobile/desktop state, authenticated staff_rank backend signals and eventual
single-writer Node migration. Do not claim those completed.

---

# Co-Companion v0.8.0 — Friends Hub / Mobile Companion Friend Parity

**From the existing installed EXE, use Updates → Check for Updates →
Download & Install EXE → Restart after the verified owner prerelease appears.**
No manual ZIP or Python. The previous EXE and saved account data remain
available for rollback. Owner-only release; no Relay, production Node,
Main Launcher, Android/Quest deployment, or staff update feed changes.

## Friends Hub — new desktop page

- **Friends & Pins:** manually load the *complete* personal VRChat online and
  offline friend lists with bounded pagination. If either side fails, the
  previous complete result remains untouched. Pin up to **12 friends**,
  manually reorder with Move Up/Down, or unpin. Pinned friends appear first.
  Friend pin changes require verified TGC staff access, as in the Mobile
  Companion's `FriendPinStore`. These are local shortcuts, not friend
  requests or VRChat favorites.
- **Shared-instance grouping:** the remaining friends are grouped following
  Mobile's `FriendOrdering`: VRChat favorites (when provided in the future),
  shared instances, single known locations, private/hidden online and offline.
  Visible names are alphabetized within each section. Currently the desktop
  groups the fetched list by visible instance, hidden and offline; it does not
  yet retrieve the separate VRChat favorite-friend index.
- **Pinned Groups:** load your own VRChat group memberships and pin up to
  **16 group shortcuts** per personal account, matching Mobile's
  `GroupToolkitStore`. Open a group in the existing Profiles page.
- **Privacy:** pins/groups are stored in an atomic local JSON containing
  ONLY account-scoped IDs and display names; no tokens, Relay roles,
  credentials, group moderation rights, or remote friendship writes.
- **Friend Activity:** explicitly click **Capture Friend Changes** to fetch
  another complete friend snapshot. First capture initializes baseline
  without inventing earlier history. Later complete captures record friend
  added/removed, online/offline, location, status, description and name
  changes. Minimum 60 seconds between captures; last 5,000 events retained
  only in **per-account Windows-user DPAPI-encrypted** local files.
  **No background polling, log watching, notifications, uploads or Relay calls.**
  An incomplete/rate-limited response never overwrites prior history.

New source modules: desktop_social_shortcuts.py,
desktop_friend_ordering.py, desktop_friend_hub.py,
desktop_friend_activity.py. Existing personal login/2FA, StaffDex,
official staff OSC, ten-channel Color Studio and all other pages remain.

Tests cover per-account boundaries, limits, ordering, hidden/shared/offline
sections, full API pagination, partial failure handling, DPAPI manual history
first-run correctness, event recognition, throttling and no overwrite on
corruption. The frozen Windows EXE GUI smoke checks Friends Hub trees.
**Actual owner-PC VRChat API paging and screen QA are still required.**

## Remaining parity work

Mobile's separate live VRChat favorite index, optional pinned-friend alerts,
device-local friend change alerts, richer group toolkit, overlay/Mini-VRCX,
Quest/Android pairing and single-writer legacy Node migration are not yet
complete. This milestone intentionally leaves the one live TGC Relay and
staff-only permissions alone.

---

# Co-Companion v0.7.1 — Easier Staff VRChat OSC Role Boxes

Update directly in the running owner EXE:
**Updates → Check for Updates → Download & Install EXE → Restart**.
The verified Windows/Linux CI pipeline publishes a dedicated owner-preview
GitHub release after all tests pass. This is NOT a staff/Node/Relay deployment.

The Staff Chatboxes page is now much faster to use:

- **My Rank Box** picks your exact currently authorized rank label with one
  click (the rank comes from a live authenticated Relay lease, not local UI).
- **Common Staff**, **Recommended**, **All Available**, and **Clear All**
  replace the selected set in one step. No manually toggling 12 rows.
- Individual **role buttons** are arranged over two rows; unauthorized roles
  are visibly disabled. Clicking an authorized role checks *only* that role.
- The list displays a clear checked/unchecked/locked symbol. Double-click a
  row to toggle its checkbox without pressing another button.
- **Send My Rank Now** sends the live authorized rank message to the chosen
  local PCVR process without selecting a row first. **Send Highlighted**
  continues to work for one chosen individual message.
- **Start Rotation** uses checked messages (up to eight); if more are checked
  the status explicitly explains the 8-message rotation limit. Editing any
  selection immediately stops the existing official rotation so old queued
  chatboxes cannot continue broadcasting after preferences change.
- Selected template IDs remain per personally signed-in VRChat account and
  persist locally. Common staff messages still require verified staff
  access; no rank-specific labels unlock without a live authenticated
  `staff_rank` from the existing Relay.
- No new OSC networking, Node uploader, Relay process, or staff permissions.

Offline tests verify all seven rank buttons against explicit authorized
catalogs, all quick-select presets, account separation, clear behavior and
rejection of rank escalation. Windows packaged GUI smoke remains mandatory.

---

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
