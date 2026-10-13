# Official TGC staff chatbox library — v0.18.1 development proposal

## Basis
The owner-provided `StatusItems_12.json` (Magic Chatbox export, Version 2) contains 12 exact message strings. Preserve their Unicode brackets and case. The source has two groups (`main` and `Default`), plus `UseInCycle` flags. This is an import seed, **not** an authorization source.

## Official common templates — available to all approved active staff
- `『Discord.gg/TheGamerClub』` (source UseInCycle=true)
- `『Gamer Club Staff』` (false)
- `『Welcome to The Gamer Club』` (true)
- `『Instance Staff』` (true)
- `『Do not mute/block me』` (true)

## Official rank-specific templates (lowest -> highest)
- Trial Moderator (tier 1): `『Gamer Club Trial Mod』`
- Moderator (tier 2): `『Gamer Club Moderator』`
- Head Moderator (tier 3): `『Gamer Club Head Mod』`
- Admin (tier 4): `『Gamer Club Admin』`
- Head Admin (tier 5): `『Gamer Club Head Admin』`
- Owner (tier 6): `『Gamer Club Owner』`
- The Royalty (tier 7): `『Gamer Club Royalty』`

All rank-specific seed templates have source UseInCycle=false. Unlock union of common templates + rank tiers <= authenticated highest rank.

## Authorization and compatibility (MUST implement before shipping as role-locked)
Existing `StaffGate.verifyOnline()` verifies active staff via Relay `node_status` but does not receive a rank.
Current Main Launcher v2.2.7 sends an ID-only `sync_mobile_staff` roster to Relay v1.1.11; Relay `MobileStaff` stores `DiscordID, SyncedAt, LeaseExpiresAt`. The Android app has **no trustworthy source for rank today**.

Coordinate three **backwards-compatible** changes before enabling restricted presets:
1. Main Launcher -> Relay: sync (DiscordID, normalized rank) using the *actual roster record* and existing central authenticated endpoint. Preserve staff leases, empty-roster fail-closed behavior, all desktop Node contracts, and the existing role hierarchy. Never accept rank from an Android client.
2. Relay: retain the old ID-only sync for previous launcher versions without elevating privileges. Store rank and lease server side and return rank only for an authenticated `mobile_companion` `node_status` whose DiscordID matches the approved deployment and whose lease is valid. Unknown/expired rank -> common-only or locked until refresh; staff gate itself should preserve its pre-existing behavior unless authorization fails.
3. Android: use only the verified rank from StaffGate's authenticated response. Bound its validity to the same verified deployment and expiry/grace as StaffGate. Never trust Android SharedPreferences, editable JSON, Discord app display-role cache or the personal VRChat group membership for entitlement. On demotion or revocation, instantly drop newly forbidden selections from the active rotation and fail closed. Do not persist a rank as authority.

**Important:** TGC's `The Royalty` is a cosmetic distinction for designated owner-level staff, and roster data can still classify such staff as `Owner`. A specific trusted owner-maintained Royalty designation must be supplied before issuing tier 7. Do **not** assign tier 7 just because a staff member is Owner or edits a profile. Ensure trials' eligibility remains aligned with the existing mobile staff allowlist.

## User experience
- New section `TGC Official Chatboxes` alongside `My Chatboxes`.
- Show common templates, entitled tiers, and optionally higher locked tiers (display-only).
- Allow selecting official presets for rotation with a per-account opt-in selection map. Store only selected template IDs (not fake tier metadata).
- Keep existing default chatbox, eight additional personal custom chatboxes, independent refresh/rotation schedules, manual message priority, explicit Start/Stop, local OSC and stop-on-service-death behavior.
- Official templates are read-only. Copying an official line to a personal free-form chatbox must **not** be described as technically prevented; OSC text is inherently editable and not DRM. Rank locking applies to the official catalog and its eligible scheduled presets, not to arbitrary user-authored text.
- No Relay upload of text, chat logs, or presets; no silent broadcasts.
- Use versioned stable template IDs; backfill current UseInCycle flags as initial recommendations only, not as a forced change to existing personal rotation.

## Release gates
- Verify the actual roster's role field, exact normalization, and Royalty override source.
- Offline unit tests: every tier, downgrade, unknown rank, stale lease, disconnected Main Launcher, old Relay response, revoked deployment, account switch, and unauthorized status mutation.
- Real-device verification: full and floating OSC screens, 144-char bound, selected + personal rotation, manual chat pause, overlay/service stop and restart.
- DO NOT publish the Android 0.18.1 update manifest or change production Apps Script until backend permissions are verified and owner approves the coordinated rollout.
