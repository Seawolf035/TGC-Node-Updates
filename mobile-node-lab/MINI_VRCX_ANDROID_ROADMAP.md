# TGC Staff Companion — Mini VRCX Android Roadmap
Updated: 2026-10-12. Planning document, not a feature-completion claim.

## Audience and purpose
Build a full-featured Android VRCX alternative for TGC staff who have no PC or limited PC access. The existing staff-only Mobile Node remains integrated, but personal Mini VRCX functions use each staff member's own VRChat account. Do not confuse staff authorization, personal VRChat authentication, or central inviter credentials.

## Confirmed baseline
Last confirmed Android release: v0.13.1 (versionCode 23). Verify repository/release state before development. Existing capabilities include personal VRChat sign-in/2FA, encrypted sessions, friends, profiles, browsing, notifications/invites, avatar favorites, VRC+ gated device-local Extended Favorites, status presets, VRChat user notes, verification-code reading, and Shizuku-backed Mobile Node tracking.

**Already tested in early development:** live VRChat Mobile log feeds via Shizuku, background monitoring, player encounters, SQLite offline queue, and Relay uploads. Do not reopen basic log-access feasibility as an untested assumption. Check specific log event coverage when implementing each new feature.

## Planned releases (tentative versions, not promises)
### v0.14 — Friends Locations, Activity Feed, and Floating Overlay
- Group friends by visible world/instance, showing who's together; respect hidden/private locations.
- Persistent, locally observed friend activity feed: online/offline, location and status changes where accessible; timestamps and deduplication.
- Optional draggable Android chat-bubble overlay above VRChat Mobile, expanding into a compact Mini VRCX panel.
- Overlay shortcuts: detected current-instance players, live log events, encounter history, world/instance info, friends, notifications, tracking status, profile lookup and notes.
- Reuse the **existing** live log reader/tracking service and database; no second log reader. Overlay dismissal must not stop tracking.
- Android overlay permission, foreground-service requirements, security gates, battery/performance and VRChat foreground detection.

### v0.15 — History and Collections
- Searchable player encounter history with first/last seen, worlds, repeated encounters and observed name changes.
- World and instance visit history from locally observed logs; clarify when history cannot be reconstructed.
- Local custom friend categories and favorites.
- Device-local extended world favorites with categories, search and sorting.
- Avatar selection/history only where observable; improved existing avatar manager and local collections.

### v0.16 — Complete Account and Group Management
- Supported instance creation and instance detail actions.
- Profile editing and existing status preset improvements.
- Invitation manager: send, request, accept/decline where supported, with confirmations.
- Group browsing, membership information, announcements, events and permission-aware management where supported.
- User search, mutual friends where API permits, and friend management.

### v0.17 — Mobile Convenience and Reliability
- Persistent Android notifications for supported invites/requests/activity, with battery-conscious refresh.
- Unified search across friends, players, groups, worlds, avatars and local history.
- Import/export for local collections and history (exclude session credentials and secrets).
- Responsive native UI, accessible controls, pagination, caching, robust back navigation, offline states and error handling.

### Later / optional
- Friend activity statistics and heatmaps based on actual observed history.
- Advanced avatar/world collections and device migration.
- Profile customization and customizable home dashboard.
- Additional group and staff investigation views, subject to API permissions and TGC authorization.

## Engineering invariants
- Do not rebuild the Companion or rewrite the existing Node.
- Keep staff Quick Deployment and periodic authorization checks, including revocation.
- Keep personal VRChat credentials encrypted and independent from TGC Relay; never upload personal cookies.
- Preserve Shizuku access, background tracking, live log feeds, deduplication, local history, offline queue and Relay upload behavior while switching screens or opening/closing overlay.
- Preserve Android application ID, permanent signing certificate, incrementing versionCode, update manifest, SHA-256 and updater behavior.
- Verify current GitHub code and official/established API behavior before implementing. Distinguish source implementation, compilation, signed publication, real-device testing and live VRChat verification.
- Desktop Nodes, Staff Launcher, Main Launcher and Relay remain backward compatible.
