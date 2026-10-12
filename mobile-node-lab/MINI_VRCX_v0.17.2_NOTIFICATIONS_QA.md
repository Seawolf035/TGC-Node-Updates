# TGC Staff Companion v0.17.2 — Reliable Inbox and Opt-In Alerts

Created 2026-10-12. This is a feature implementation and **real-device QA plan**, not a claim that live VRChat notification delivery has been fully tested.

## User-facing features
- Full Companion Inbox includes **All / Unread / Invites / Friends** filters, displays the number of unread items, and updates while the inbox is foregrounded (roughly every 90 seconds).
- Floating Mini VRCX inbox uses the **same** dual VRChat inbox merge/deduplication as the full Companion, with an **Unread** quick filter and refresh while it is visible (roughly every 90 seconds).
- Legacy and modern VRChat notification endpoints are combined without duplicating identical IDs or events. The source that supports accepting a legacy friend request is preferred when available.
- On an API outage, the full screen and floating panel retain the last **loaded in-memory** notifications, visibly reporting stale/partial results. The app does **not** persist raw notification messages or invite locations to an unencrypted cache.
- Full Inbox supports view instance / sender profile, mark read, accept supported legacy friend request, dismiss with confirmation, **copy instance location**, and **request self-invite with confirmation**. The floating panel retains its established equivalent actions.
- **Settings → VRChat notifications** adds a user-controlled **Enable / Turn off inbox alerts** preference. On Android 13+, notification permission is requested only on explicit opt-in.
- Background checks use Android **WorkManager** periodic scheduling at a 15-minute minimum interval with a network constraint. They do not require the overlay to be running, Shizuku, VRChat Mobile, or the Mobile Node. Android battery policies can defer delivery.
- Background alerts are **generic** (counts of new notifications/invites/friend updates), without names, message bodies or invite locations on the lock screen. Clicking the alert opens the staff-gated native Inbox filtered to Unread.
- First sync / signed-in VRChat account switch establishes a **non-alerting baseline**; only subsequently observed new unread items trigger a notification. The old/modern endpoints have separate baselines, so partial availability doesn't create alert floods.
- The deduplication store holds only SHA-256 hashes of account and notification IDs in bounded SharedPreferences (500 hashes). No plaintext cookies, private message bodies or VRChat passwords are stored for alert history.
- Each cold background run validates staff status using the existing TGC grant flow before contacting personal VRChat. A revoked/stale grant cannot use the inbox.
- Existing Shizuku player tracking, local join/leave histories, personal session encryption, Relay uploads, floating presence dot and verification-code reader remain unchanged.

## Real-device and system QA
1. Install v0.17.2 signed APK over v0.17.1 (versionCode increases from 34 to **35**). Confirm the same certificate, Node enrollment, unsent outbox, personal VRChat account and overlay position survive.
2. With Shizuku stopped and without VRChat Mobile installed, open personal Inbox and use its All, Unread, Invites and Friends filters. The personal VRChat API login should be sufficient.
3. Verify native and overlay Inbox show consistent notification counts, deduplicate common legacy/v2 entries, keep actionable legacy friend requests, and handle an empty inbox.
4. Disconnect Wi-Fi after an Inbox loads. Refresh and verify previous contents remain on screen with a connection warning. Reconnect and check the status/last-checked time changes.
5. Have another user send a real invite and friend request. Validate sender profile, copy location, confirmed self-invite, mark seen, accept request and dismiss. No mutation may occur without an explicit tap or required confirmation.
6. In Android Settings, decline notification permission: periodic notifications must remain off. Grant permission and enable: confirm WorkManager has the periodic job and initial seed. Turning off must cancel work.
7. Opt in with existing pending requests: no alert flood on first sync. Send a genuinely *new* invite/request; confirm a generic new-item notification after the next WorkManager execution. Test screen lock, notification channel and Android 13–16 behavior.
8. Leave the floating overlay off and the Companion closed, wait for the next background check. Confirm periodic work runs after connectivity returns and respects Android battery throttling.
9. Test one inbox endpoint returning errors/rate limits while the other succeeds. The UI must indicate a partial result and the alert worker must seed a recovered endpoint before notifying, rather than mistaking existing invites for new ones.
10. Switch the logged-in personal VRChat account and then sign out. Cached ID hashes from the prior account must not create notifications or leak data to another account.
11. Revoke TGC staff access. Background jobs must not fetch VRChat inbox data unless the existing staff authorization can be freshly approved. The native Inbox and overlay must lock.
12. Confirm Shizuku reader count and join-only Relay payloads are unchanged, including during notification checks. Notifications must never start a second log reader.
13. Verify v0.17.2 updater JSON, SHA-256, permanently signed release APK, certificate continuity and one-step upgrade; source compilation alone is not sufficient to claim behavioral QA passed.

## Known limits
- VRChat does not provide a guaranteed push channel to this Android app; background alerts are scheduled polling, **not real-time delivery**. WorkManager's minimum periodic interval is 15 minutes and delivery can be delayed further.
- A VRChat API notification with unavailable legacy/v2 fields cannot be fully reconstructed; failed actions display a useful error.
- Notification history is intentionally not stored long-term in plaintext. Only the current screen's loaded inbox survives a temporary network failure; full offline notification archives are future work.
- Quest log-source support remains separate feasibility research, not a feature delivered here.
