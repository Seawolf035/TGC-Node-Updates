# v0.18.7 — Manually ordered pinned friends and online notifications

## Compact pinned friends
- Social > Pinned friends now shows all twelve available pins as single-line 43dp entries, not large stacked action buttons.
- Tap a friend name to open the Mini-VRCX profile.
- Tap ↑ / ↓ to move that friend exactly one position; the visible list updates in place and doesn't reset the page scroll position.
- Order persists locally per signed-in personal VRChat account, independent of remote VRChat favorites.
- Latest cached presence can be shown where fresh; stale snapshots are marked as last-seen, not as verified current presence.
- Existing friends list ☆ / ★ pin shortcuts remain.

## Opt-in friend-online alerts
- Separate from inbox alerts, off by default. Enable from Social or the main Companion's account/notification settings.
- Permission `POST_NOTIFICATIONS` is required on supported Android versions; Companion can request it explicitly.
- When floating tools are running, reuse the **existing, complete** friend list fetch every 2.5–5 minutes. No additional polling stream.
- When floating tools are closed, Android WorkManager provides background checks at a **minimum 15-minute cadence**. Actual checks can be delayed or skipped by battery rules, network conditions and authorization.
- Only pinned friends with a previously observed offline baseline that subsequently appear online in a full snapshot can generate an alert.
- Initial opt-in observation, new pins and account changes seed silently; merely pinning an online friend never generates an alert. No alerts on failed/partial friend fetches.
- One account-scoped state store is shared by overlay and WorkManager, with synchronized observation and a committed baseline before notification to prevent duplicate alerts.
- Notification channel `Pinned VRChat friends online` is distinct and Android-private on the lockscreen. Notifications open the Companion's Friend Hub; no TGC Relay contact is needed.
- Online notifications concern the VRChat presence feed, not whether someone is physically present in a specific world.
- On logout, authorization loss or notification revocation, no alerts. No staff rank changes or permissions are granted by pinning.
- Your v0.18.6 sidecar PTT, immediate OSC controls, official role tag rotation, Shizuku player tracking and Main Launcher/Relay are not changed.

## Device acceptance tests
1. Install v0.18.7 over signed v0.18.6; saved pins, OSC presets, selected role tags and account sessions survive.
2. Pin at least six friends; open Social, use ↑/↓ repeatedly to reorder. Confirm the exact order survives navigation, bubble collapse, app restart and subsequent update.
3. Set panel Compact/Standard/Expanded and scroll. Ensure each row is compact and reordering doesn't jump the entire page.
4. Enable online alerts via Companion > account > Pinned friend online alerts; request permission if needed. Leave an already-online pinned friend online: **no notification** on enable.
5. Have a pinned friend start offline, observe that status through a successful complete polling cycle, then come online. Expect one Android notification (not duplicates).
6. Pin a currently online friend while alerts are enabled; no historical/onboarding notification.
7. Turn alert OFF; no further notifications. Re-enable: silent baseline again.
8. Test simultaneously running WorkManager and floating monitor; verify only one notification per actual transition.
9. Disable overlay: background alerts should still work opportunistically via WorkManager, but may be delayed 15+ minutes.
10. Switch personal VRChat accounts; pins, manual order, baselines and enablement remain account scoped.
11. Deny notification permission or sign out: no alerts. Confirm none leak on the Android lockscreen.
12. Verify the original PTT hold/release quick button and v0.18.5 role-only OSC rotation continue to work.

CI checks compile and source invariants, not physical VRChat presence transitions or Android scheduler timing.
