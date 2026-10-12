# TGC Staff Companion v0.16.0 — Homepage + In-Overlay Group Tools
Prepared 2026-10-12. This checklist describes implementation and remaining device verification; it is not a real-device test report.

## Homepage refresh
- Native **Command Center** replaces the long repetitive landing page, preserving the TGC aqua/dark theme and existing app icon/header.
- Staff-access state, live Mobile Node heartbeat, current bubble running status and its enable/hide action appear in the first card.
- At-a-glance metrics: locally captured players, queued uploads, recently observed online friends (only when a complete friend snapshot was recorded within the past five minutes), and floating overlay state.
- Recent player encounter names are displayed from the existing local SQLite retained log history. No personal session credentials or new remote endpoints needed.
- Shortcuts emphasize Friends, Player Log, Inbox, Profile, Worlds, Avatars, Activity, Friend Log, Group Tools and verification code.
- Metrics read via a worker thread rather than blocking the UI with database access. Refresh while the Home screen is visible; no new Shizuku reader or Relay process.

## Mini VRCX overlay upgrades
- **Group member directory**: GET `/groups/{groupId}/members` in bounded 60-member pages, and a server-side group member-name search (3+ characters). Tap a member for their existing in-overlay profile.
- **Group instances**: GET `/groups/{groupId}/instances`; list accessible instances, copy the location, show instance details and request a self-invite (explicit confirmation).
- **Group bans**: permission-respecting read-only list of visible bans using GET `/groups/{groupId}/bans`; pagination if provided. No changes to bans, no ability to bypass the user's VRChat group permissions.
- Group navigation retains the selected group ID when drilling into a member profile or instance so the internal Back button can return to the proper group.
- All new group screens are rendered within the floating window and use the same personal VRChat session as Mini VRCX, **not** TGC Relay credentials.

## Real-device acceptance (must be performed before claiming behavior verified)
1. Upgrade the permanently signed v0.15.0 APK to v0.16.0; staff setup, personal session, Mobile Node queue and avatar collections persist.
2. On the homepage verify the circular group icon, friendly greeting, status, prominent bubble toggle, and staff-access label.
3. Check Node active/paused and overlay enabled/disabled badges update while the underlying services actually start/stop. Force-stop overlay and confirm the toggle recovers when its heartbeat expires.
4. Confirm captured-player count and queue match the Node control screen, and recent encounters match captured Mobile Node history (not friend history).
5. Stop friend monitoring for over five minutes; homepage must show unknown online count instead of claiming cached friends are currently online.
6. Verify Home stays responsive with large local histories, internet offline, missing personal VRChat session and revocation. Scroll and check readable metric text on narrow phones.
7. While staying in VRChat Mobile, open overlay → Social/Tools → My groups → group details → Members. Test pages and typed member search, profile drilldown, Back navigation and keyboard focus.
8. Open Group Instances: test visible/hidden locations, copy, details, and confirmed self-invite. No full Companion Activity should launch for ordinary group actions.
9. Open Read-only Group Bans: verify permission-denied displays an error, and no banned records appear without valid API authorization.
10. Confirm Shizuku live log monitoring, uploads and personal VRChat sessions remain independent when the overlay is collapsed, stopped, reopened or the display rotates.
11. Check GitHub Actions debug and stable-key release build, Android signing certificate continuity, strictly increasing versionCode **27**, v0.16.0 manifest and SHA-256, updater URL and in-place upgrade.

## Intentional limits
- API-permitted **read-only** group member, group instance and ban inspection are included. Staff ban/unban or permission-changing actions are intentionally not added until permission scopes, confirmation safeguards and audit behavior are tested.
- Friend counts are based on observed snapshots, not a push-based always-live friend feed.
- Android overlay keyboard/UX behavior still needs VRChat-Mobile in-game testing on real devices.
