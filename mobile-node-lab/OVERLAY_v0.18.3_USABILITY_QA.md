# v0.18.3 — Floating tools confirmation & OSC-tag usability update

**Focus:** VRChat utility usability; no new staff management, Relay, invite-network or authorization capabilities.

## All floating-tool confirmations work as an over-button modal
A single shared modal is rendered in the floating service's root `FrameLayout`, **above** the existing buttons and content, not inside the scrollable Mini-VRCX list and not in a toolbar that shrinks the scrolling content.

- Dimmed in-panel scrim blocks accidental background actions.
- Centered prompt and adjacent **Cancel / Confirm** buttons; minimum 48dp button height.
- Scroll offset and current tool remain in place on Cancel and Confirm; opening a modal never calls `scrollToTop`.
- Opening a different floating tab or hiding the panel discards the prompt; authorization rechecked on Confirm.
- A second modal replaces the first; no accumulation of stale Confirm buttons at the bottom.
- Confirmation text for consequential actions remains explicit; selecting a switch/toggle is immediate.
- Common asynchronous feedback now uses immediate platform toast (no messages inserted at the page top or bottom).

This applies to every existing Mini-VRCX `confirm()` call (friend invites/requests/dismissals, self-invites, deleting chatbox/phrases/parameters, VRChat notes), and also the previously bespoke inline prompts for **avatar selection**, **status update**, **starting continuous Live Chatbox**, and the floating **stop-tracking** two-tap confirmation. Authentication and action semantics are unchanged.

## Official TGC tags are one-tap toggles
- OSC dashboard > **Official TGC tags** opens `mini_osc_tags` directly; nearby **Rotation settings** shortcut.
- Rotating chatboxes page has one **Manage official tags** shortcut instead of embedding twelve multi-button cards.
- Dedicated tag page has compact, large row touch targets and standard checked/unchecked Android `Switch` controls, grouped by entitlement tier.
- Current selection count is always visible and updates inline with each toggle; no list rebuild or scroll jumps.
- Unavailable higher-rank tags are shown disabled with a rank label.
- Toggle behavior remains bound to **authenticated** `StaffGate` rank from v0.18.1, and `TgcOfficialChatboxes` checks the entitlement again on every update and OSC rotation. Toggles do **not** start broadcasting.
- The Royalty tier remains exactly the three previously confirmed Discord IDs, via existing backend and Android authorization.

## Source / release checks
- Version `0.18.3` and `versionCode 46`.
- Existing Android package and persistent signing certificate unchanged; signed update can install over v0.18.2 without clearing preferences.
- Real-world QA still required: this CI cannot prove the exact screen layout or device-specific window-manager/keyboard behavior.

## Manual Android / Quest QA
1. Use compact, standard and expanded overlay dimensions; scroll near the bottom of long lists.
2. Trigger friend request, friend invite, self-invite, note save, phrase/chatbox/OSC parameter delete: modal overlays current buttons, Cancel leaves position unchanged, Confirm invokes existing API only once.
3. From avatar favorites and status page, verify previously inline confirmations now overlay the buttons and no confirm cards appear at the bottom.
4. Start Live Chatbox: confirm over panel; cancel must **not** start OSC. Once running, Stop retains its original direct control. Existing auto rotation and manual-chat pause unaffected.
5. Stop active Node tracking: see centered prompt, not 'tap again' state; cancel leaves tracking intact. Node sighting persistence and queued uploads unaffected.
6. Send/show errors, copy text, toggle saved OSC controls: no page-top/bottom appended confirmation messages, no forced scroll.
7. OSC tab > Official TGC tags: toggle any eligible tag with a single tap, verify selection counter; scroll partway down, toggle another tag, ensure scroll stays there.
8. Verify ranked locked tags disabled and unselected; demote/revoke staff and refresh authenticated rank: no higher tier gets transmitted.
9. Test navigation away while modal visible, bubble collapse/expand, virtual keyboard focus, rotation, resumed VRChat Mobile and full-screen Companion regression.
10. Verify no implicit VRChat invite, avatar update, chat send or staff privilege change from opening modal or toggling an official tag.
