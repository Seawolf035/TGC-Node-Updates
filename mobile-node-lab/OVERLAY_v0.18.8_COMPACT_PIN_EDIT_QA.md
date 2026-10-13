# Android Staff Companion v0.18.8 — Compact Pinned Friends and Edit Mode

## Changes
- Floating Social > Pinned Friends displays compact **32dp** single-line entries while browsing (down from 43dp in v0.18.7).
- No up/down controls appear in normal mode. Tap a name to open its Mini-VRCX profile as before.
- A single small **Edit order** button enables editing and changes to **Done**. While editing, rows are **38dp** tall with two **36dp** move buttons; tapping Done hides the arrows.
- Editing is a temporary UI mode, never persisted. Collapsing and reopening the panel starts with compact viewing mode.
- Each move uses existing `FriendPinStore.move`: ordering persists immediately per personal VRChat account, without replacing saved pins.
- Reordering redraws only the small pinned list, not the whole Social page or its scroll offset.
- Compact 36dp footer buttons replace the separate large **Online alerts** button, descriptive paragraph, and two large Friends/Invitations buttons. Options remain **Alerts ON/OFF**, **Friends**, **Invites**. Enabling and disabling online alerts retains existing permission and opt-in safeguards.
- Existing v0.18.7 online alerts, friend-cache freshness labeling, OSC PTT, role-locked chatboxes, Node, Shizuku and all other utility code remain unchanged.

## Manual device checks
1. Install v0.18.8 on top of v0.18.7 without uninstalling. Verify VRChat login, saved pins/order and opted-in alert settings persist.
2. Open Floating Tools > Social. Check that the list fits substantially more names on screen, arrows are absent, and friend names open profiles.
3. Tap Edit order. Check arrows appear immediately and the button changes to Done. Use the arrows to reorder several pinned people (including the first and last). Test disabled boundary arrows.
4. Tap Done. Verify no arrow buttons remain; profile taps work normally; order remains exactly as edited.
5. Collapse and reopen floating panel, navigate to other tabs and back, restart Companion. Edit mode is off; saved pin order remains.
6. Test 1, 5, and 12 pins; long Unicode names; online and offline labels; stale presence status; compact/standard/expanded panels; screen rotation.
7. Confirm moving items does **not** jump scroll position or trigger friend notifications.
8. Toggle Alerts ON/OFF from the new footer, including Android 13+ notification-permission handling. Check the separate background checks remain opt-in and respect account boundaries.
9. Confirm Friends and Invites footer shortcuts still work.
10. Regression-test sidecar PTT press/release, official-only OSC rotations, saved chatbox quick send, Mini-VRCX and Node tracking.

CI compilation verifies Java structure but not the on-device screenshot layout or actual VRChat presence delivery.
