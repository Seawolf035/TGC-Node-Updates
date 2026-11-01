# TGC Co-Companion — Owner-only Windows Pilot 0.1

**This is an owner-only, deliberately limited test build, NOT a production staff release.**
There is **one existing TGC Relay**, not a test Relay. The owner can enable one
explicit Discord ID on that same Relay; the feature is OFF by default and
must be manually enabled after the compatible Relay update is installed.

## What this owner pilot can actually test

- A completely separate **Staff Launcher** one-time enrollment issued from
  your existing Main Launcher > Node Management > Deploy Nodes.
- Protected Windows DPAPI storage in
  `%LOCALAPPDATA%\\TGC\\CoCompanionOwnerPilot` (no plaintext Node key).
- The original required TGC Desktop Terms with separate owner-pilot acceptance.
- Existing-Relay `node_status` and the new owner-pilot `cocompanion_access`
  short-lived lease. The Co-Companion cannot issue or edit its own limit.
- Explicitly allowed **1–4 launch slots** with one VR and remaining Desktop;
  correct `launch.exe --osc=9001:127.0.0.1:9101` slot mappings, Windows
  `VRChat.exe` process discovery and local OSC chatbox to selected PID.
- Metadata-only VRChat desktop log readiness checks.

**Deliberately NOT enabled:** Invite Network Node collector or uploader,
automatic sightings, replay queues or original Node takeover; remote
phone/Quest/PCVR pairing and overlay; full social, friends and StaffDex
features. This avoids duplicate sightings and breaking legacy Node activity.
The new owner pilot uses its **own Node enrollment** separate from your old
Staff Launcher. Do not copy or delete keys or queue files between them.

## Before running

1. Ensure the compatible single-Relay code has been safely reconciled with
   your *actual currently deployed* Google Apps Script and updated on the
   **existing /exec deployment**. No new Relay, Sheet, URL or installation.
   The feature is off until explicitly enabled for your Discord ID.
2. Install owner-test **Main Launcher v2.2.32** (separate downloadable ZIP),
   preserving your existing settings and inviter accounts; go to
   **Node Management > Co-Companion**.
3. Refresh status, set your exact Discord ID, **Enable Pilot**, and choose
   your 1–4 instance limit. On the same Main Launcher use **Deploy Nodes**:
   select your owner Discord ID and **staff_launcher** client type to issue
   a fresh, one-use deployment link. Use a **new enrollment**, not another
   Node's live key. The relay staff roster sync must still authorize your
   Discord ID or the pilot lease will be denied.
4. On Windows 10/11, install Python 3.11 or later (with Windows `py`
   launcher), unzip only this owner-pilot artifact to a new folder.
   Double-click `RUN_OWNER_PILOT.bat`. A local `.venv` installs `psutil`
   and `pywin32` for this pilot, without modifying system Python.
5. **View and accept Desktop Terms**, paste the one-time deployment link,
   redeem it, and click **Verify Pilot Access**. The one-time link is cleared
   from the visible field after submitting. Configure your Steam VRChat
   `launch.exe` path before launching sessions.
6. Test a single instance first. If you grant yourself four, try
   the other three Desktop slots; use OSC controls only after selecting
   a **known running VRChat.exe PID** from the Launch tab.
7. To stop tests, exit Co-Companion. It does **not** automatically kill
   the VRChat clients you already launched. Return to Main Launcher >
   Node Management > Co-Companion and click **Disable Pilot**.
   You can separately revoke the dedicated test enrollment in Node
   Management if you no longer need it. Do not delete old Node credentials.

## Safety and deployment limitations

- This is a source-based developer PILOT with offline-tested authorization,
  not a signed Windows installer or feature-complete Companion.
- Download only from the official `Seawolf035/TGC-Node-Updates` GitHub
  Actions owner-pilot artifact on `feature/co-companion-foundation`.
- **Do not update the public Node/Staff Launcher manifest, APK, Relay
  production data model, or deploy staff download links.**
- **Do not paste the current owner Central Key into Co-Companion.** The
  Central Key stays in Main Launcher only; Co-Companion receives its
  own short-lived, owner-issued Node enrollment.
- If live Relay source differs from the archived v1.2.1 delegate baseline,
  **reconcile it first and back up the current Apps Script version**.
  Do not paste older script content over newer production features.
- Any hardware-specific VRChat start behavior, OSC delivery, account/rank
  and Quest/phone integration still require owner testing; passing CI
  does not prove real VRChat runtime compatibility.

## Automated verification

Nonpublishing GitHub Actions runs the Co-Companion Python suite on Linux
and Windows and creates an **owner-only ZIP artifact** without uploading
or deploying it to staff.
