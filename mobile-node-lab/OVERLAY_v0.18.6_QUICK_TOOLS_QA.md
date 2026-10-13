# v0.18.6 — Floating Quick Tools

## Scope
Android Staff Companion only. Additive UI on top of the existing v0.18.5 signed build. No new server endpoints, Group permissions, staff ranks, staff management, Shizuku dependencies, Relay or Windows Launcher changes.

### 1. Quick PTT next to floating bubble
- The existing 60dp bubble remains draggable and opens the normal panel.
- An adjacent independent 45dp **PTT REL / PTT HOLD** button sends the same `/input/Voice` OSC press/release verified by the user in v0.18.4.
- Side button follows the bubble on drag and snap-to-edge; fits within phone display bounds; stays accessible while the panel is collapsed.
- State reflects the Companion's local OSC latch request, **not** verified VRChat mic state. Release is a one-tap operation, even during a pending press.
- Use a dedicated OSC voice worker so releases do not queue behind friend lookups, event-log fetches or chat sends. Five-minute hold expiration, quick-release strip, best-effort stop/exit release are preserved.
- No PTT press on app startup or service restore. If sidecar overlay fails to display, the existing bubble stays available.

### 2. Fast OSC chatbox actions
- Floating OSC tab: direct Start (with centered confirmation), Stop, Pause/Resume Live Chatbox, Official tags only quick selector, Edit status, Official tags, and four direct-send saved-phrase buttons.
- Pause suppresses sending without clearing rotation preferences or official role tag selections; resumes at next tick.
- All saved phrases are explicit manual OSC sends; local UDP delivery is not proof VRChat rendered the chatbox text. Existing rate limit applies.
- Role-locked presets and official-only authorization are delegated to existing `OscControlStore` / `TgcOfficialChatboxes`.

### 3. Pinned friends
- VRChat friends list has compact one-tap ☆/★ pin controls and a **Pinned only** filter; profiles have matching Pin/Unpin buttons for current friends.
- Floating Social > Pinned friends shows up to five local pinned profiles plus direct link to Invitations and friend management.
- Up to 12 pins are stored **per signed-in personal VRChat account** on the device; no relationship changes, friend requests, location inference, or server modifications from pinning.
- Profile and invites still use existing signed-in VRChat API and existing confirmation UI.

### 4. Persistent quick action bar
- Up to 3 actions shown directly under the floating Home / Social / OSC / Tools tabs, outside the scrolling region. Default: Friends, Chat, Status.
- Customize selection and order in Tools > Customize shortcuts > Persistent Quick Bar. Can hide by choosing none.
- Existing six pinned *Home* shortcuts remain independent and unchanged.

## Manual device checks
1. Install v0.18.6 over v0.18.5; assert existing signed certificate, data, bookmarks, official tag choices and Music Node remain intact.
2. With the floating panel collapsed, tap side mic button twice, verifying PTT HOLD/REL indicator and VRChat mic HUD. Drag the main bubble to both edges and corners: adjacent PTT button follows without blocking the main bubble.
3. While OSC PTT held, use panel and pinned quick bar; release via side control, emergency button and the existing quick-release strip. Hide bubble while held and check best-effort release; force-stop is *not* guaranteed to release.
4. From OSC page start Live Chatbox with confirmation, pause/resume (should not send new packets while paused), stop, then switch official-only on/off without erasing saved personal text.
5. Tap saved phrase Send, verify only the tapped phrase is sent, manual-chat pause is preserved and quick repeat triggers existing rate-limit.
6. Pin a friend in Mini-VRCX, confirm Social shortcut appears. Use pinned-only filter, unpin, verify it disappears from next rendering. Switch personal VRChat accounts and verify different pin sets.
7. Customize persistent quick actions, reorder and hide bar; verify it remains fixed while scrolling every tab. Home pinned actions are unchanged.
8. Test compact/standard/expanded panel, landscape/orientation change, Android keyboard, screen edge positions, accessibility tap targets, app service restart.
9. Verify staff revocation still prevents unauthorized actions, official-only rank gates remain enforced, live sending stays off by default.
10. Do not assert on-device behavior solely from successful CI compilation.
