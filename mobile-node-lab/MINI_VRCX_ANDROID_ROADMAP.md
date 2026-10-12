# TGC Staff Companion — Mini VRCX Android Roadmap
Updated: 2026-10-12. Planning document, not a feature-completion claim.

## Audience and purpose
Build a full-featured Android VRCX alternative for TGC staff who have no PC or limited PC access. The existing staff-only Mobile Node remains integrated, but personal Mini VRCX functions use each staff member's own VRChat account. Do not confuse staff authorization, personal VRChat authentication, or central inviter credentials.

## Confirmed baseline
Last confirmed published version before this pass: v0.17.7 (versionCode 40). v0.17.8 is in development until signed CI passes. Verify actual GitHub build and manifest before later work.

**Already tested in early development:** live VRChat Mobile log feeds via Shizuku, background monitoring, player encounters, SQLite offline queue, and Relay uploads. Do not reopen basic log-access feasibility as an untested assumption. Check specific log event coverage when implementing each new feature.

## Planned releases (tentative versions, not promises)
### v0.14 — Friends Locations, Activity Feed, and Floating Overlay
- Group friends by visible world/instance, showing who's together; respect hidden/private locations.
- Persistent, locally observed friend activity feed: online/offline, location and status changes where accessible; timestamps and deduplication.
- Optional draggable Android chat-bubble overlay above VRChat Mobile, expanding into a compact Mini VRCX panel.
- Overlay shortcuts: detected current-instance players, live log events, encounter history, world/instance info, friends, notifications, tracking status, profile lookup and notes.
- Reuse the **existing** live log reader/tracking service and database; no second log reader. Overlay dismissal must not stop tracking.
- Android overlay permission, foreground-service requirements, security gates, battery/performance and VRChat foreground detection.

- **Friend Log (new v0.14 requirement):** a searchable, persistent chronological record of observed friend online/offline transitions, location changes, status changes and friend-list additions/removals where detectable; show timestamps, names and profile links, filtering by friend/event, deduplicate repeated polls, and label unknown/unobserved periods accurately. Keep it distinct from the general activity feed. Store account-scoped local history and never claim complete history before monitoring began.

### v0.15 — Actual delivered scope: Mini VRCX inside the floating overlay
- In-overlay player encounters, profiles, friends and visible locations, Friend Log, Activity Feed and notification actions; support in-overlay Android keyboard entry.
- Personal VRChat notes, presence, avatars, world/group details and user search use the user's separate personal session.
- Circle-cropped TGC icon, internal Back navigation, draggable bubble and existing Mobile Node controls.
- Previously planned full world/instance history and unlimited custom favorite collections are **still future backlog**, not v0.15 deliverables.

### v0.16 — Homepage Refresh + Advanced Group Tools
- **Homepage redesign:** unified staff/access status, fast enable/hide overlay control, live Node status, recent captured activity, queue and friend summary metrics, and prioritized shortcuts.
- **Group member tools inside overlay:** group member listing and server-side name search, member profile lookup, visible group instances and read-only group bans. Respect personal account permissions.
- Retain existing in-overlay profiles, invites, notes, presence, avatars and group browsing. Extend instead of duplicating services.
- Still pending for later releases: broad instance creation, group post/event editing, permission-changing moderation, exhaustive mutual friends, and further account profile customization. Explicitly validate APIs and authorization before shipping.

### v0.16.1 — Compact Homepage and Gesture Navigation
- Promote Quick Access above the detailed metrics, reduce the greeting and oversized status card, and tighten the native shortcut tiles.
- Swipe left/right between Home, Explore, Tracking and Settings with a horizontal threshold that preserves ordinary vertical scrolling and button taps; bottom tabs stay present.
- Simplify labels across the Home screen and floating tools; move infrastructure terms to Advanced diagnostics while preserving technical functionality.
- Preserve previous v0.16 group browser functionality and verify in-place signing/updater continuity.

### v0.16.2 — Local VRChat Log History + Clear Shizuku Explanation
- Shizuku is **optional**, and only allows the existing Mobile Node watcher to read VRChat Mobile logs. Personal account features (friends, notifications, groups, avatars, worlds) do not need Shizuku or a VRChat Mobile installation.
- The Node's existing Shizuku watcher now exposes a separate **local-only** stream of player join, player leave and observed world/instance changes. No additional privileged log reader or change to the Relay's join-only sightings payload.
- The Companion uses locally saved activity for a VRCX-like **player list**, chronological **join/leave log**, searchable encounters and links to personal-account player profiles. Surface in the main Companion and the in-game overlay; clearly label that a watcher started mid-session may miss players already in the instance.
- Versioned SQLite storage retains at most 5,000 local events / 30 days; preserve old events and unsent join sightings through migration.
- User-facing Tracking explains the dual value of reading logs: the local activity tools and optional contribution of join sightings to the TGC network. Raw VRChat log files and personal session tokens are not uploaded.

### Future — Quest Log Sources (research; not shipped)
- Investigate whether Quest exposes usable VRChat Mobile logs and how permission boundaries differ from Android phone/Shizuku; **do not assume Shizuku works on Quest**.
- Reuse the same on-device normalized event types (join, leave, instance) from a future Quest-compatible read-only provider; keep the player list, local history and Node/Relay policy independent of the source implementation.
- Require real-device feasibility, permission, privacy, performance and update testing before advertising Quest player tracking.
- Quest users may have different device requirements; personal VRChat account features remain conceptually separate from log tracking.

### v0.16.3 — Compact Help + Gamer Music 2 Cult Invite Codes
- Tracking setup's Shizuku explanation starts **collapsed**, behind an accessible expand/hide control, below primary tracking controls and device status. Preserve help state on Activity recreation.
- Gamer Music 3 verification remains the existing Discord-started TGC invite flow. Add a **separate** Gamer Music 2 / Cult verification reader for `CULT-XXXX-XXXX` codes found in private VRChat invites / invited-instance custom names.
- Verification for both systems starts and finishes in Discord. The Companion (including floating tools) only locates and copies the code from the VRChat invite, helping staff paste it into the original Discord prompt; it never initiates or submits verification.
- Cult TTL and a dedicated world identifier are currently unknown; do not invent either. Limit API lookups, keep personal VRChat credentials isolated, and require real-device verification.
- Preserve existing Node, logs, and Quest feasibility plans.

### v0.16.4 — Automatic Verification Codes + VRChat Presence Dot
- Replace separate Gamer Music 3 / Cult code selectors with **one** auto-detecting `Verification code` screen in the app and floating panel. Identify whichever newest valid `TGC-XXXX-XXXX` or `CULT-XXXX-XXXX` invite arrives and provide one Copy button for Discord; both verifications still begin and finish in Discord.
- Consolidate the two VRChat invite formats into a single bounded, deduplicated scan. Preserve GM3 world/expiry checks and avoid assuming a Cult-specific world or TTL.
- Float bubble dot reflects **personal VRChat presence**, not tracking: green Online, blue Join Me, yellow Ask Me, red Busy, gray Offline/unknown. Poll status conservatively using the existing personal VRChat account; keep Node tracking status in its own Home controls.
- No new Shizuku reader, Node functionality, Relay changes, or Quest support claims. Validate real Cult invites and color changes on-device.

### v0.16.5 — Community Name Cleanup
- Display **The Gamer Club** and **Cult of Gamer Music** in the verification-code screens and floating tools instead of internal bot/version names, preserving unchanged TGC/CULT code formats.

### v0.17.0 — Intuitive In-Game Status + More Discovery
- Floating Status screen: one selected color-coded status choice, a 32-character message editor, one explicit Apply/Confirm step, current status preview and immediate status-dot refresh on server success.
- Reuse the full Companion's existing account-specific VRChat status presets from inside the floating panel. Load or save a preset locally without switching applications; applying to VRChat remains a separate confirmed action.
- **Find anything** within the overlay: searchable VRChat people, worlds and locally encountered players, using request-driven server searches and local offline lookups.
- **Recent worlds** from the personal VRChat account, plus **Your instance** from the existing local join/leave/instance log history (copyable location, observed-player count, world details and self-invite where permitted).
- Keep routine actions within the overlay; preserve unchanged Node/Shizuku tracking and Relay submissions.

### v0.17.1 — Home Layout Clarification
- Align the Tracking on/off indicator directly above the Tracking button and Tools on/off directly above Show/Hide floating tools, preserving their live updates.

### v0.17.2 — Notification, Invite & Background Reliability
- Shared deduplicated legacy/v2 personal VRChat Inbox for full Companion and floating tools, including Unread, Invites and Friends filters, foreground refresh and preserving already-loaded items during network failures.
- Make it easier to review an invite: open its instance or sender, copy location, request a self-invite with confirmation, mark read, accept supported friend requests, and dismiss with confirmation.
- **Explicitly opt-in** Android background notifications under Settings. WorkManager checks at a minimum 15-minute cadence with network/battery constraints; no claim of real-time push delivery.
- Cold-start work independently verifies TGC staff authorization before reading personal-account VRChat APIs. Shizuku and the Node are **not** needed for account notifications.
- Generic notifications hide personal message bodies and locations. Store only bounded, per-account hashed notification IDs for baseline/dedup; establish first sync and per-endpoint baseline without old-request alert floods.
- Real-device test Android 13–16 notification permission, battery deferrals, dual-inbox API errors, and staff revocation.

### v0.17.3 — Floating Customization + Background QoL
- Floating panel **Compact / Standard / Expanded** layouts clamped to device bounds, persistent user-configurable shortcut pins (one to six, reorderable), drag/edge-snap and reset-position controls.
- User-driven opt-in **overlay recovery**: Android may recreate a killed sticky floating service, but it must verify staff authorization online before opening private views; after offline interruption retry a bounded number of times. Explicitly hidden bubble stays hidden.
- Automatic foreground restoration checks only when the full Companion is visible and a verified staff grant and prior recovery consent are present. No force-stop/Doze or battery restriction bypass.
- Lower-rate VRChat API polls while the overlay is collapsed. Node tracking, Shizuku watcher and Relay remain wholly independent of the floating panel and new preferences.
- A Background health dashboard gives honest Node/overlay/alerts state and Android battery settings access; no new dangerous permissions or second log reader.
- Device-test process recreation, offline and revoked-staff cases, Android 13–16 foreground-service restrictions and orientation changes.

### v0.17.4 — Complete VRChat Favorite Avatar Groups + Shizuku Links
- Replace the old flattened first-page avatar gallery with server-discovered **avatar favorite lists** (including VRChat's regular and VRC+ favorites groups), using the VRChat favorites-group endpoints and actual group names instead of guessing available slots.
- Show every accessible group's actual avatars and name/count in both the full gallery and floating tools, plus a deduplicated combined All view with search, avatar details and safe Select avatar actions.
- Explicitly report partial or inaccessible groups; if discovery is unavailable, use a clearly labeled bounded legacy combined fallback rather than claiming it retrieved every list.
- In optional Node Tracking help, add a **Download Shizuku** link to the official site and a setup-guide link. The personal-account Companion requires neither Shizuku nor VRChat Mobile installation.

### v0.17.5 — Quest-Aware Floating Tools
- Detect Oculus/Meta Quest Android hardware through local Build device identifiers.
- On Quest, disable the 2D floating panel **by default**, without disabling the main Companion or assuming Quest-compatible log tracking is shipped.
- Offer an explicit **experimental opt-in** under Companion Settings with a clear Quest incompatibility warning. Opt-in permits manual attempts to launch but does not grant overlay permission or guarantee visibility inside VR.
- Enforce the same setting in Home, the service's direct start, sticky background recovery and active runtime checks; disabling the preference stops any existing floater and prevents revival.
- Keep normal Android phone/tablet floating features unchanged. Actual Quest device verification remains pending.

### v0.17.6 — Floating Avatar Picker and Actionable Mini VRCX
- Replace the floating favorite-avatars screen's one-button-per-avatar immediate action with a status-style workflow: current avatar preview, collapsed VRChat favorite-list selector, image-backed selectable avatar cards, selected preview/details, a separate **Apply selected avatar** step and an inline **Confirm / Cancel** choice. Never apply an avatar merely by tapping its tile.
- Reuse all remote favorites groups from v0.17.4, deduplicate in the All view, support avatar name/author search and show-more pagination. Update current-avatar UI immediately on successful selection without reloading every list; refuse an Apply if the personal VRChat account changed.
- Add **All / Online / Offline** friend filters, expandable friend results, visible-instance actions, richer image-backed player profiles and additional Inbox pagination. Keep the actions within the floater.
- Preserve user-selected Quest floater opt-in, Node tracking, background/recovery behavior, The Gamer Club/Cult verification and personal-account cookie isolation.

### v0.17.7 — VRChat Mobile Chatbox Without Its Native Keyboard
- Add a dedicated **In-game text chat** composer to the floating Home, Social and Tools tabs; it can also be pinned as a Home shortcut.
- Use VRChat's documented **local OSC chatbox input**, `/chatbox/input`, to send typed text over UDP loopback to the VRChat Mobile client **without opening the broken in-game keyboard**.
- Android's normal Companion overlay IME provides entry; users must turn on OSC in the VRChat Mobile Action Menu. Only explicit user sends, with 144-character validation, limited send rate and optional chat SFX.
- Preserve the unsent draft in memory and offer copy/clear controls. State clearly that UDP packet transmission is **not** delivery confirmation: in-game display still requires VRChat OSC to be enabled and needs real-device testing.
- No Shizuku dependency, Remote Relay messages, special user access, background auto-send, additional credentials, or changes to the Node log watcher.

### v0.17.8 — Dedicated OSC Tab and MagicChatbox-style Live Chatbox
- Separate floating OSC tab (fourth main tab) for chat composer, local saved quick phrases, typing indicator, status composer and typed avatar OSC parameter controls.
- Explicitly start a **continuous chatbox line** with custom text, clock and battery. Default OFF; 5/10/15/30-second intervals, immediate preview, one-shot send, nearby confirmation, Stop and clear status.
- Continue local OSC updates while the floating panel is collapsed but the foreground floating service runs. Stop on deliberate bubble hide, loss of authorization, account change or service destruction; **no automatic broadcast restoration** on process relaunch.
- Give manual typed chat priority for approximately 20 seconds, keep repetitive status messages silent and respect a shared rate limit. All OSC traffic stays on the phone's loopback address and does not involve Shizuku or TGC Relay.
- Add VRChat avatar parameter Bool/Int/Float one-shot controls (saved by personal account). Music integrations, Pulsoid input and further MagicChatbox integrations remain future options.

### v0.18 — Universal Search and Reliability
- Broaden unified search to groups/avatars/collections, caching, accessibility, logging and optional backup/export, followed by controlled group/instance management.

### v0.18 — Remaining Convenience and Reliability
- Improve inbox retry explanations, pagination, accessibility, rate-limit backoff and safe optional caching. Strengthen offline data handling and export/backups; overlay sizing and shortcut pinning began in v0.17.3.
- Full-scale group management and advanced instance tools only after verifying VRChat authorization scopes and safe write workflows.
- Extend unified search to groups, avatars, friend collections and other supported data beyond people/world/local modes; validate battery impact and privacy.
- Safe import/export and migration of local collections and history, excluding credentials.
- Accessible controls, pagination, caching, improved back navigation and offline states.

### Later / optional
- Friend activity statistics and heatmaps based on actual observed history.
- Advanced avatar/world collections and device migration.
- Profile customization and customizable dashboard widgets (beyond the v0.16 homepage refresh).
- Additional group and staff investigation views, subject to API permissions and TGC authorization.

## Engineering invariants
- Do not rebuild the Companion or rewrite the existing Node.
- Keep staff Quick Deployment and periodic authorization checks, including revocation.
- Keep personal VRChat credentials encrypted and independent from TGC Relay; never upload personal cookies.
- Preserve Shizuku access, background tracking, live log feeds, deduplication, local history, offline queue and Relay upload behavior while switching screens or opening/closing overlay.
- Preserve Android application ID, permanent signing certificate, incrementing versionCode, update manifest, SHA-256 and updater behavior.
- Verify current GitHub code and official/established API behavior before implementing. Distinguish source implementation, compilation, signed publication, real-device testing and live VRChat verification.
- Desktop Nodes, Staff Launcher, Main Launcher and Relay remain backward compatible.
