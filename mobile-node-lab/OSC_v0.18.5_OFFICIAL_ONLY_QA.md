# v0.18.5 — Official-only TGC chatbox mode

## Intended result
A verified staff member can disable the default personal Live Chatbox line and optionally run **only** authorized TGC official tags in Live Chatbox rotation.

- **Live Chatbox > Rotating chatboxes > Official tags only: ON** excludes the default personal status and all named saved personal chatboxes from the live cycle; existing personal settings remain saved.
- **Official tags only: OFF** restores ordinary mixed rotation. The separate **Default personal status INCLUDED/EXCLUDED** control determines whether the default occupies a slot.
- **Live Chatbox > Clear personal status & use official tags only** (confirmation overlay) clears the saved default text, time, and battery fields and excludes the default, but does not delete personal named chatboxes or official template selections. Requires at least one *currently authorized* selected official tag.
- Existing builds default to mixed mode with personal default included; all settings are scoped to personal VRChat account.

## Correct routing of active OSC messages
- When rotation is on, live slots contain only enabled sources: default personal status if included, saved personal rows unless official-only, then the selected rank-authorized official templates.
- When one official tag is selected and official-only mode is on, a single valid tag is repeatedly sent on the ordinary 5/10/15/30-second Live Chatbox refresh cadence. No empty personal line is inserted.
- When all official tags are removed or become unauthorized after a role downgrade/expiry, `liveSlotCount` becomes zero in official-only mode; the live sender STOPS rather than defaulting to a stale or unauthorized personal message.
- Live Chatbox broadcasting remains opt-in; no broadcast on saving, selecting, clearing, or switching modes. OSC packet delivery still isn't a VRChat receipt.
- The v0.18.4 experimental Android `/input/Voice` toggle and fast release are unchanged.

## Manual QA
1. Upgrade v0.18.4 to v0.18.5 in place; normal personal Live Chatbox previously configured still works.
2. Select one eligible official role tag. Turn on Official tags only, start Live Chatbox, verify the role phrase appears and the previous personal status/time/battery never appears.
3. Select several tags including lower ranks; confirm normal cycle interval switches them. Turn off official-only, confirm mixed rotation restores preserved personal status and named chatboxes.
4. Disable only Default personal status, leave named personal chatboxes on: only named personal + official messages cycle.
5. From Live Chatbox page tap Clear personal status & use official tags only, confirm overlay, then verify text blank, Time OFF, Battery OFF, saved default excluded, official tags still selected and transmitting.
6. Tap Cancel on clear confirmation; no settings change.
7. Verify clearing fails safely with an explanation if NO eligible official tags are selected.
8. Remove all eligible tags while live official-only mode is running. It should stop, not fall back to personal output. Simulate rank expiry/demotion (including Royalty revocation) with the same result.
9. Test account switch: official-only and include-default settings remain isolated per personal VRChat login. A second account should inherit safe default choices, not the previous account's exclusive mode.
10. Re-test v0.18.4 mic hold/release, automatic safety release, quick-release bar, Mini-VRCX floating modal confirmations, world bookmarks, node tracking, staff gating and signed in-place updates.

## Important
A CI build only validates compilation, not VRChat Mobile delivery. This change does not add VRChat API moderation or expand rank authority.
