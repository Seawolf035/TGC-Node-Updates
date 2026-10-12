# TGC Staff Companion — Mini VRCX Android Roadmap
Updated: 2026-10-12. Planning document, not a feature-completion claim.

## Audience and purpose
Build a full-featured Android VRCX alternative for TGC staff who have no PC or limited PC access. The existing staff-only Mobile Node remains integrated, but personal Mini VRCX functions use each staff member's own VRChat account. Do not confuse staff authorization, personal VRChat authentication, or central inviter credentials.

## Confirmed baseline
Last signed and published release before this development pass: v0.15.0 (versionCode 26). v0.16.0 is under development until CI confirms signed publication. Verify repository/release state before future changes. Existing capabilities include personal VRChat sign-in/2FA, encrypted sessions, friends, profiles, browsing, notifications/invites, avatar favorites, VRC+ gated device-local Extended Favorites, status presets, VRChat user notes, verification-code reading, and Shizuku-backed Mobile Node tracking.

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

### v0.17 — Mobile Convenience and Reliability
- Persistent Android notifications for supported invites/requests/activity, with battery-conscious refresh.
- Unified search across friends, players, groups, worlds, avatars and local history.
- Import/export for local collections and history (exclude session credentials and secrets).
- Responsive native UI, accessible controls, pagination, caching, robust back navigation, offline states and error handling.

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
