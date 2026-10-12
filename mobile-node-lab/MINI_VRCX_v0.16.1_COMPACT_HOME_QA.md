# TGC Staff Companion v0.16.1 — Compact Home & Swipe Navigation

This is an implementation/checklist document, **not** a claim of real-device testing.

## What's changed
- The original homepage greeting, repeated verification chip, "Mobile Companion" title and long promotional description have been replaced with one short welcome line.
- A compact status bar shows Tracking on/off and Floating tools on/off, with **Show/Hide floating tools** and **Tracking** controls.
- **Quick Access now comes before statistics**, with six compact shortcuts in a 2-column layout (Friends, Players, Inbox, Profile, Worlds, Avatars). Removed the oversized extra enclosing card and reduced tile height from 132dp to 90dp.
- Detailed counts (players seen, waiting to send, recently observed online friends, floating tools) and recent activity are below Quick Access and remain available by normal vertical scrolling.
- The four primary pages, **Home ↔ Explore ↔ Tracking ↔ Settings**, support deliberate horizontal swipes across scrollable content while retaining the bottom tab bar. A swipe needs at least 78dp horizontal displacement, exceeding vertical movement by 1.5×, to reduce accidental changes while scrolling.
- Plain-language copy replaces prominent "Mini VRCX", "Mobile Node", "Relay" and other technical labels. Diagnostic terminology remains available in advanced troubleshooting.
- Replaced the platform-dependent yellow emoji-style profile symbol with a monochrome UI glyph.

## Testing on Android
1. Upgrade a signed 0.16.0 install directly to 0.16.1. Keep existing staff Quick Deployment credentials, personal VRChat session, outbox, friend history, Node tracking, and app ID.
2. On a typical Samsung-sized display, open Home. The "QUICK ACCESS" section and first shortcuts should be on screen **without scrolling**. The compact header, welcome and status controls should not cause text clipping.
3. Tap every Quick Access card; verify correct destination and access checks.
4. Swipe left Home → Explore → Tracking → Settings; right in the reverse order. Verify the selected bottom-navigation tab updates. Do not wrap at the ends.
5. Verify ordinary vertical scroll still works, taps do not accidentally change pages, and slight diagonal movements do not switch tabs. Swipe on blank areas, cards and within longer pages.
6. Check header/staff status and text scaling at large Android accessibility font sizes; verify buttons remain tappable and labels legible.
7. Start/stop floating tools, and verify Home updates the status and toggle text. Stopping floating tools must **not** stop player tracking.
8. Start/stop tracking, verify Home status and counts; recent activity and queued-outbox values must remain local and consistent with Tracking.
9. Log out of VRChat personal session, lose network access, revoke staff access, and reopen the app. No guessed friend counts or unauthorized screen contents should appear.
10. Confirm v0.16.1 (versionCode 28) stable-key certificate matches v0.16.0; updated manifest SHA-256 and upgrade URL are published.

## Scope and limitations
- Swipe navigation is implemented in the main Companion screen, not inside the existing VRChat game overlay; floating tools retain their own Home/Social/Tools tabs and internal Back.
- Group member/instance/ban API features from v0.16.0 remain unchanged.
- Friend statistics represent observed snapshots only, with a five-minute freshness check. An em dash means unknown, not zero.
