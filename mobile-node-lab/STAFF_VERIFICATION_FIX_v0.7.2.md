# Staff authorization refresh fix — Companion v0.7.2

## Bug verified in v0.7.1
`StaffGate.verifyOnline()` called `lock()` **before** its network request, instantly revoking the previous successful approval on every refresh. The authorization lasted only 90 seconds; an HTTPS delay, Apps Script throttle, switching screens, or an ordinary timeout could briefly lock the entire Companion and cause users to see a repeated lock/unlock cycle.

## Fixes
- Preserve an already-verified grant during every new relay check. Revalidate silently; do not redraw the dashboard upon each successful check.
- Extend validated grants to at most **12 minutes since a successful relay approval** to tolerate temporary connectivity interruptions. Initial/cold app launch must still obtain a genuine online approval. This is not offline login or indefinite caching.
- **Explicit negative authorization responses, absent/unreadable deployment credentials, or confirmed revoked/invalid staff Nodes lock access immediately**. A network timeout alone does not equal staff revocation.
- Distinguish actual denial from temporary connection errors in the UI.
- Reduce main dashboard verification frequency to every 90 seconds, saving Apps Script calls.
- Keep native player/world/group/account/diagnostics screens verified with a silent 90-second refresh while displayed.
- Use the already-running background Mobile Node's authenticated `node_status` responses to keep the *same* staff grant alive while VRChat Mobile is foregrounded; no extra API call or extra account credentials needed.
- No changes to personal VRChat cookies, login, Shizuku watcher parsing, GM4 eligibility, or GM1 invite behavior.
- v0.7.2 uses the same permanent release signing key as v0.7.1: install as an in-place update without unlinking/redeploying.

## Device test
1. Keep the background Mobile Node running and linked. Download the release-signed (not Preview/debug) v0.7.2 APK; use Companion updater if offered. Do not uninstall v0.7.1.
2. Verify saved Quick Deployment and personal VRChat account remain in place.
3. Leave the home screen open >3 minutes: should not flash a locked screen every 90 seconds or reset scroll.
4. Leave a friend/world/group detail screen open >5 minutes and navigate back; should stay open.
5. Use VRChat Mobile >15 minutes with the background Node running. Return to Companion; staff login should remain active.
6. For a temporary network interruption, the last *recently verified* grant remains active for no more than 12 minutes, then fails closed if no successful relay verification occurs.
7. If the owner actually revokes this Node, the next explicit relay rejection must lock the Companion; this does not require wiping personal VRChat credentials.
8. Confirm live sighting queue and relay accepted counts keep increasing.

Do not share screenshots containing one-time deployment codes or credentials.
