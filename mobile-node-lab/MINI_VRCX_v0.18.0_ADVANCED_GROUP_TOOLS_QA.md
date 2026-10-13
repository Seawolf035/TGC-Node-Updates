# TGC Staff Companion v0.18.0 — Mini-VRCX Group & Instance Toolkit

Prepared October 12, 2026. **This is a feature branch QA specification, not proof of a shipped release or live-device testing.**

## Scope and existing behavior preserved

The existing Mini-VRCX features (friends, notifications, users/worlds/groups search, profiles, avatars, OSC chat, saved rotating chatboxes, private notes, local Shizuku history, code reader and Relay upload queues) are retained. This increment adds practical tools to **existing** group and instance browsing rather than creating new authentication flows or scraping hidden data.

## New functionality

- A local, **per personal VRChat account** group favorites list, capped at 16. Users can pin/unpin from the floating group dashboard or the full-screen group detail. No group credentials or role permissions are cached.
- The floating Mini-VRCX **My Groups** view shows pinned group shortcuts first, then all groups already returned by VRChat. The full-screen groups browser marks pinned entries with a star.
- The enhanced group overview shows online/member counts, join/visibility state and, where VRChat returns them, the current member's roles and membership status.
- The group dashboard still provides member browsing and server-side member search, visible group instances, and a read-only ban list. It does **not** grant moderation authority.
- The **full-screen native group profile** now opens a dedicated staff-gated group tools page with searchable/paged members, named member roles where available, visible group instances (population sort/filter, details, copy location, explicitly confirmed self-invite), and permission-scoped read-only bans. This also works when the floating overlay is not running.
- Visible group instances can be filtered to populated ones, sorted by reported occupancy, refreshed and opened for additional details. Each retains existing copy-location and **explicitly confirmed** self-invite options. VRChat permission and server API checks remain authoritative.
- No Main Launcher, Relay or desktop Node protocol changes. Nothing is uploaded to the Relay, and no TGC server-side role granting or ban mutation is added.

## Build and release checks

1. Verify source branch `feature/mobile-v0.18.0-mini-vrcx-advanced-tools`, baseline v0.17.9.
2. Debug Android compilation and static checks; proposed v0.18.0 / versionCode 43.
3. **Only after passing CI** promote to `experiment/mobile-node-lab`, whose existing workflow automatically signs and publishes the APK. Verify signer matches the v0.17.9 release, update manifest and exact published SHA256.
4. Ensure the permanent signing key is only used through the existing GitHub secrets and is never embedded in source.

## Device QA after release

1. Install signed v0.18.0 over signed v0.17.9 **without uninstalling**. Ensure VRChat credentials, Staff Gate, Relay credentials and Node outbox, saved chatboxes and Shizuku settings survive.
2. Floating Tools → Social → My VRChat groups: inspect joined groups, pin two. Return to the list: both appear first. Unpin one; ensure it disappears from pinned section and remains in all groups.
3. Full Companion → Explore → My Groups → group detail: pin/unpin a group. Confirm its star appears in the full-screen directory after refresh.
4. Sign into a different personal VRChat account: pinned shortcuts must not leak into the second account. Switch back: original pins remain.
5. On a group with roles, check current-account group role names and membership information; verify missing/hidden API fields don't crash the page.
6. Group members: page forward/back and server-side search in both the floating and native group-tools views. Open a member's personal profile. Verify permissions and error handling.
7. Group instances: test default sorting, populated-only filter, empty-group behavior, refreshing and displayed occupancy/capacity in both floating and native views (VRChat fields can vary). Verify no false invite action for a malformed location.
8. Copy a visible instance location, open instance details, request and **confirm** a personal self-invite; verify the instance is accessible to the signed-in account. Decline confirm: no request sent.
9. The read-only group-ban view stays read-only even for group moderators; no ban or kick action is introduced.
10. Verify staff revocation and Quest overlay restrictions still stop/deny the floating service. Verify other Core/OSC features and offline Relay queues.

## Known limits and next phase

- This increment improves group discovery and navigation but is **not yet full VRCX feature parity**. It does not implement VRChat group joins, join-request approval, role editing, member bans, instance creation, or mass moderation.
- Permission-aware moderation actions require a separate API and audit design. Do not infer authorization from a locally cached role label or the user's TGC staff rank.
- Occupancy fields may be absent on some group-instance API responses. An absent field is not proof that an instance is empty; real-device/API QA should confirm the shape for TGC groups.
