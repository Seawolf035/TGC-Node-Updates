# TGC Staff Companion v0.13.0 — Usability & workflow pass

This release improves common interactions **across the existing Mini VRCX** instead of adding new account permissions or touching the TGC Node/relay.

## Improvements
- **Notifications & invites:** Replace cycling filter button with explicit `All / Invites / Friends` tabs; show sender message together with functional native action buttons. Previous AlertDialog used `setMessage()` and `setItems()`, which could suppress selectable actions on Android. Rebuild filtered rows deterministically; disable repeated load requests while loading.
- **Browsing and search:** Local instant text filtering on loaded online/offline friends, worlds, groups, and encountered players. Counter clearly distinguishes matching rows from total loaded rows. Searching VRChat now works from keyboard Search/Enter as well as Search button; hide keyboard when submitting. Preserve previous results until a refreshed page successfully arrives. Apply per-query request pacing (not a blanket delay for every distinct search), and allow immediate retry after failures.
- **Extended avatar library:** Replace hidden/unreliable list dialog with a real action sheet where avatar details and `Use / Move / Remove` actions are simultaneously visible. Paste avatar ID from clipboard, including a VRChat avatar link containing `avtr_...`; no manually copying just the ID.
- **Presence/status:** Display all five presence choices on the page, highlight the selected choice, update when loading saved presets; no separate `Choose status` dialog required. Applying still requires confirmation.
- **Tracking screen:** Start button reflects running state; start/stop disabled when inappropriate, and status feedback isn't overwritten by periodic dashboard refresh. Existing Shizuku service and relay unchanged.
- **Home:** Show staff authorization and tracking activity as **separate** indicators; add status and avatar-library quick shortcuts.
- **Profile headers:** Copy a shareable VRChat user/world/group URL directly from the profile header.

## Regression checks on Android
1. Install v0.13.0 over signed v0.12.0. Verify TGC staff access, personal VRChat session, mobile Node and relay queue persist unchanged.
2. Open inbox, switch among All/Invites/Friends without closing the screen. Tap a notification; confirm **message AND action buttons** display. Open invited instance, mark as read, accept a friend request when intended, or dismiss a test item with confirmation.
3. Open online/offline friends with many users. Type a partial name: filtered results update instantly without network access. Clear filter to restore loaded list, then Load more and repeat.
4. Enter a VRChat search and submit using keyboard Search. Change query immediately; new query should be allowed without unrelated 12-second throttling. Refresh an existing list during a network interruption; old content should remain.
5. Extended Avatar Favorites (VRC+): open a saved avatar; confirm Use/Move/Remove are visible with the description. Test each with confirmation. Paste an avatar URL containing `avtr_...` into the Save dialog.
6. Status & presets: confirm five visible statuses, tap each, load saved preset, then apply with confirmation. Verify live VRChat changes only on Apply.
7. Tracking controls: verify Start is disabled during active tracking, Stop is disabled when tracking is paused, and status action feedback isn't immediately erased.
8. Open a user/world/group profile and Copy VRChat link. Verify the URL is usable.
9. Leave Node tracking running through all screens; verify encounter capture and sync stay stable.

## Still requiring device feedback
The UI and builds are validated by Android CI, **not** by a live VRChat account. VRChat-side account changes require explicit user confirmation. Don't declare the specific workflows fully tested until a device test reports success.

## Next usability areas
Profile editing; improved large-list navigation and persistent sort preferences; friend activity/recent presence; export/backup of the on-device avatar library; group tools with role checks; account-safe notifications. They will be added only after testing and cleanup of earlier workflows.
