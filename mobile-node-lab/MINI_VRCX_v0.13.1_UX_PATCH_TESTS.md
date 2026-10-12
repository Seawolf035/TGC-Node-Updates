# Staff Companion v0.13.1 — UX refinement patch

This release incorporates the full v0.13.0 UX pass and final fixes discovered during its review.

## Final refinements
- **Avatar favorites:** Local name/creator search for all *already loaded* native VRChat favorite avatars, plus deduplication when paging and clear loading/refresh feedback. This filter does not perform a server-side search or load additional pages automatically.
- **Player profiles:** Move normal friend and instance interactions immediately below presence. They no longer require scrolling through bios, groups, badges, and activity.
- **Home:** Remove repeated Avatar/Status quick-access tiles; route the main Avatar shortcut to ordinary favorites so users without VRC+ can still open their saved avatars. VRC+ extended collections remain available inside the Avatar library and Explore.
- **Updater:** Bump versionCode and versionName above the previously published v0.13.0 so users already on that build can install this refinement normally.

## Device regression
1. Install over signed v0.12.0 or v0.13.0; confirm staff authorization, VRChat login, Node service and local avatar collections remain intact.
2. Normal avatar favorites: search for part of a name or creator; filtered count updates; Load more continues to work; clearing the text restores all loaded items.
3. Open a player profile with a long bio and many groups; friendship and invitation actions should appear near the top after presence.
4. Home should show **one** Avatar/Status quick-access pair, not duplicates. Normal Avatar Favorites must work without VRC+. Check that Extended Favorites still enforces subscription.
5. Run v0.13.0 regression checks for inbox action sheets, filters, search, status presets, tracking controls and profile URL copying.

Android compilation and signing verify APK integrity, but interactive UI behavior still requires actual phone testing.
