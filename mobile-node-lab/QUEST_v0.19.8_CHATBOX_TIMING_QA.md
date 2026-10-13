# TGC Staff Companion v0.19.8 — Quest Tools and chatbox timing QA

## Quest navigation cleanup

- Quest Home has one **Quest Tools** button in the main action row, launching the
  existing native Quest toolkit.
- Removed the duplicate **Quest native tools** card below Home and the duplicate
  **Open Quest native tools** launcher in Explore.
- Native toolkit heading reads **TGC STAFF COMPANION** over **Quest Tools**
  (or Quest Tools · current subpage) without repeating the feature name.
- Quest diagnostics/Settings sections use **Quest Tools**, never **Quest System**.
- Android phone users still see **Show floating tools** and existing floater
  controls. Quest floater remains hard disabled; Quest OSC tracking and phone
  overlay service behavior otherwise unchanged.

## Expanded independent chatbox intervals

**Live Chatbox refresh (seconds):**
5, 10, 15, 20, 30, 45, 60, 90, 120

**Rotation / switch to next chatbox (seconds):**
10, 15, 20, 30, 45, 60, 90, 120, 180, 300

- Both timing selectors are arranged as 3-column button rows on Quest and
  Android phone floating tools.
- The store and both Quest and phone sending services use the same allowed
  timing catalogs. Existing saved values (5/10/15/30 refresh,
  15/30/60/120 rotation) remain valid without migration.
- Minimum Live Chatbox refresh remains 5 seconds. We intentionally do not
  add 1- or 2-second background broadcasts because the OSC sender has local
  pacing and manual chat should retain priority.
- A rotation change is applied **on the next Live Chatbox send**; selecting a
  switch interval shorter than the refresh interval cannot force additional
  chatbox packets. Set refresh to an equal or shorter interval to see each
  scheduled rotation.
- Longer refresh intervals may allow VRChat's displayed chatbox to fade
  between sends, depending on VRChat's behavior.
- No change to the VRChat OSC sender transport, saved chatboxes, the
  account-separation rules, staff authorization or TGC invite/Relay pipeline.

## Manual headset and phone tests

1. Install over v0.19.7 and confirm staff login, saved settings and
   bookmarks remain intact.
2. On Quest, Home shows **Quest Tools** (one launcher); Explore has no
   duplicate launcher; tapping the Home action opens the same Quest OSC,
   Social, and shortcut pages.
3. Verify Quest settings and background status call it **Quest Tools**.
4. In Quest Tools → OSC → Live Chatbox, select each refresh speed, start,
   inspect the foreground service, and confirm there is no silent 10-second
   fallback for 20, 45, 60, 90 or 120 seconds.
5. In Rotating Chatboxes, verify new switch intervals of 10, 20, 45, 90,
   180 and 300 seconds. Confirm existing saved 15, 30, 60 and 120-second
   values are retained.
6. Repeat on a phone with the floating tools. Phone floater can still start,
   stop, and recover under its previous rules; Quest cannot run it.
7. Test update to official-tag-only mode and manually send a message while
   Live Chatbox runs. Manual sending still has priority and pauses Live.
8. Ensure a headset/phone without an active VRChat account does not leak
   OSC settings between accounts. No regular production services are touched.
