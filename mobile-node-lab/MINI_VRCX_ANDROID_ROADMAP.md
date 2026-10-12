# TGC Staff Companion — Mini VRCX Android Roadmap
Updated: 2026-10-12. Planning document, not a feature-completion claim.

## Audience and purpose
Build a full-featured Android VRCX alternative for TGC staff who have no PC or limited PC access. The existing staff-only Mobile Node remains integrated, but personal Mini VRCX functions use each staff member's own VRChat account. Do not confuse staff authorization, personal VRChat authentication, or central inviter credentials.

## Confirmed baseline
Last confirmed published version before this pass: v0.17.1 (versionCode 34). v0.17.2 remains under development until the signed Android CI pipeline succeeds. Verify actual GitHub state before future work. Existing capabilities include personal VRChat sign-in/2FA, encrypted sessions, friends, profiles, browsing, notifications/invites, avatar favorites, VRC+ gated device-local Extended Favorites, status presets, VRChat user notes, verification-code reading, and Shizuku-backed Mobile Node tracking.

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

### v0.17.3 — Floating Panel Customization
- Resizable/compact overlay, customizable pinned shortcuts, persistent arrangement and improved navigation. Avoid new overlays/log collectors.

### v0.18 — Remaining Convenience and Reliability
- Improve inbox retry explanations, pagination, accessibility, rate-limit backoff and safe optional caching; expand the interactive settings and overlay controls. User-controlled overlay sizing and positioning, stronger offline data handling, and backup/export remain ahead.
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
