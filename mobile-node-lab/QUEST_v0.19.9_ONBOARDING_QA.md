# v0.19.9 — One-time Full logging setup

On a fresh, unlinked device, opening **Tracking controls** launches the
one-time tracking guide. This is a user instruction/acknowledgement, not a
programmatic confirmation of VRChat's current logging mode.

Guide:
1. VRChat → Settings → Debug → Logging → **FULL**
2. Quest: grant read-only access to Documents/Logs. Phone: authorize Shizuku
3. Start Companion tracking, then join an instance or wait for new joins

- **I understand · Continue** records local acknowledgement, and the guide
  does not appear again on routine visits.
- **Not yet** does not record acknowledgement. Start tracking reopens the guide.
- Existing linked installations are grandfathered during the first v0.19.9
  migration to avoid an unexpected modal on upgrade.
- Advanced tools → **Reopen first-time tracking setup guide** is always
  available for troubleshooting. The link remains collapsed normally.
- The persistent Full logging panels are removed from the Tracking tab,
  Tracking controls, Quest Tools, and the Start tracking status message.
- The setting belongs to VRChat. The Companion cannot set or verify it.
- This does not affect phone Shizuku requirements, Quest folder permissions,
  OSC, Relay, durable outbox, login, or existing Quest navigation.

## Test cases
1. Clean Quest install: opening Tracking controls immediately presents
   the guide. Dismiss **Not yet**, then attempt Start: it must reopen.
2. Acknowledge; confirm the guide is no longer shown automatically after
   leaving/reopening tracking controls and after restarting the app.
3. Clean Android phone install: the guide identifies Shizuku, not Quest
   folder permission.
4. Existing linked v0.19.8 installation: upgrade should preserve working
   tracking without an unexpected modal.
5. Advanced tools can still reopen the guide; it must not be visible while
   Advanced tools is collapsed.
6. Confirm the daily Tracking, Quest Tools and Tracking controls screens
   don't repeat the Full logging reminder.
7. Check Quest logging data with a live join. No old joins are replayed.
8. Confirm signed upgrade identity from v0.19.8 and update feed 0.19.9 / 61.
