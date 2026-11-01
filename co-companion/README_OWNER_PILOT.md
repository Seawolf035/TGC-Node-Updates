# TGC Co-Companion — Windows Owner Preview v0.2

**Owner-only development release. NOT a staff-wide release or production update.**

The original v0.1 owner pilot's **1–4 instance authorization, separate Node
enrollment, mandatory desktop Terms and Relay are preserved**. This is still
the ONE existing TGC Invite Relay and the existing Main Launcher v2.2.32
owner-pilot permissions panel. The new social features talk **directly to
the personal VRChat API**, not through TGC Relay.

## Installing v0.2 over your working v0.1 pilot

1. **Close the old Co-Companion window.** Running VRChat clients do not get
   automatically closed, so you may leave VRChat open.
2. Download the `TGC-CoCompanion-v0.2-OWNER-EXE-NOT-STAFF` artifact from
   the latest successful GitHub Actions run on
   `Seawolf035/TGC-Node-Updates` branch
   `feature/co-companion-foundation`. Extract its inner ZIP into a **NEW**
   folder, *including* the `_internal` directory and EXE.
3. Run `TGC_CoCompanion_OwnerPreview_v0.2.exe`. **No Python, .venv,
   pip, or BAT is required.** The unsigned preview may trigger Windows
   SmartScreen. Verify you downloaded it from your own official GitHub
   Actions run before choosing to run it.
4. Your existing *separate* v0.1 enrollment remains in
   `%LOCALAPPDATA%\\TGC\\CoCompanionOwnerPilot`. The new application
   reads it under the same Windows user. **Do not delete your old Node
   queue, DPAPI key or profile.** You should generally be able to click
   **Verify Pilot Access** without generating another one-time deployment.
5. The existing Main Launcher still controls owner-only pilot enablement
   and 1–4 instance grants. If already enabled, **you do not need to
   redeploy the Relay or reinstall the Main Launcher for v0.2**.
6. In Co-Companion, open the **VRChat Account** tab, sign into your own
   VRChat account (complete TOTP/email 2FA if requested), then use the
   **Social**, **StaffDex** and **OSC Studio** tabs. Test social READ
   operations first, then local OSC with a selected running VRChat PID.
7. Alternative for debugging: download the
   `TGC-CoCompanion-v0.2-OWNER-SOURCE-NOT-STAFF` artifact, unzip in a
   new folder, and run `RUN_OWNER_PILOT.bat` with Python 3.13. The BAT
   uses your system Python; **no virtual environment is created**.

## Windows features added in v0.2

- **Home:** live staff lease, personal VRChat identity, running-instance
  count and StaffDex progress, with quick navigation.
- **VRChat Account:** personal username/password HTTPS login, TOTP/email
  2FA, current profile and logout. Passwords are **never saved**; personal
  VRChat cookies are stored under this Windows user's DPAPI key in a
  separate encrypted file, never in TGC Node data or Relay.
- **Social:** online/offline friends, account's favorite/recent worlds,
  user/world search, groups, modern inbox, explicit paginated loading and
  external VRChat web profile links. This first pass is **READ ONLY**
  for safety; it does not send friend requests/invites or alter groups.
- **StaffDex:** exact public official TGC roster source used by the
  Android/Quest Companion, full online + offline VRChat friends scan,
  rank/order/collection progress, 5 Gamer Music achievements and 11
  leadership achievements. Hidden community awards remain **hidden
  until earned**, with the dynamic non-staff Wesman check. The last
  complete scan is stored encrypted and scoped to BOTH immutable
  Discord staff ID and personal VRChat account. No partial scan counts.
- **OSC Studio:** personal-account-separated saved chatbox messages (up
  to 24), selectable VRChat PID, rotating messages (5s minimum), avatar
  height buttons (0.8m, 2.0m and custom), bool/int/float avatar
  parameters and push-to-talk hold/release. Rotation stops on access
  loss; held microphone is released on button-up, exit or after 300s.
- **Windows style:** navy/blue appearance, Home dashboard and dedicated
  feature tabs without requiring a Quest, Android phone, or extra app.
- **Retained:** original 1–4 owner-granted VRChat instance slots, one VR
  and remaining Desktop, local log-health diagnostics, and the separate
  desktop agreement/Relay enrollment.

## Security, permissions and limitations

- Your **Co-Companion owner pilot still DOES NOT start the old Node sighting
  collector/uploader or create another uploader.** Continue using your
  existing Node / Staff Launcher for production sightings until the
  approved in-place migration has been engineered and tested.
- There is **one existing Relay**, and Co-Companion remains restricted to
  the single owner-approved pilot Discord account. Its staff lease
  expires promptly without refresh; it cannot grant itself slots.
- The existing owner Main Launcher Central Key is never requested by
  Co-Companion and personal VRChat cookies are never sent to TGC Relay.
- The social API routes and all StaffDex achievements originate from
  the actual Android/Quest Mobile Companion source. VRChat imposes API
  rate limits; do not spam refresh buttons.
- This preview uses standard desktop Tk widgets, **not** a Quest floating
  overlay. Native PCVR display and optional secure mobile/Quest pairing
  remain later milestones.
- **Still in development:** exact Staff Launcher Node migration and
  single-uploader locking, friends/inbox write actions, avatars/favorites
  management, deep groups and instance creation, detailed profiles/notes,
  full themes, all Mobile achievements/notifications, secure phone pairing,
  first-party updater, signed official staff installer. There is still
  more work before this replaces your existing desktop software.
- No signed production release, APK, Relay code or staff rollout was
  triggered by the v0.2 build.

## Tests

Nonpublishing CI compiles all modules and runs Python unit tests on Linux
and Windows 3.13, including headless/networkless Windows Tk creation.
The Windows job builds *both* the source ZIP and a bundled EXE ZIP after
tests pass. This does not prove real VRChat network, real 2FA, OSC output
or physical Quest/PCVR compatibility; the owner pilot is meant to test
those safely on your own Windows PC.
