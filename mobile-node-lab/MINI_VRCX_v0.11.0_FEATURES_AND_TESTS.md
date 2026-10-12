# Staff Companion v0.11.0 — Private VRChat Notes & Paging

## Features
- **VRChat account notes:** Browse private notes via `GET /userNotes`. Open an individual player's profile → **View or edit note**. New/edited notes are saved through `POST /userNotes` with explicit confirmation, on that staff member's **personal VRChat session**, not TGC's recruitment Relay. These are VRChat account notes, not local-only notes.
- **Existing note protection:** A targeted player-note editor checks notes in batches before showing a blank editor. If the notes list has older pages, the editor offers **Check older notes**; it does not silently overwrite an existing note if it has not finished searching.
- **Notes navigation:** Explore → Private VRChat notes, Settings → Your VRChat notes, and personal account → My private VRChat notes. Notes never go to the TGC relay, Google Sheets, Discord or background Node.
- **Paged native lists:** Online friends, offline friends, favorite worlds, recently visited worlds, and player/world searches now use VRChat's official `offset` + `n=60` pagination with a manual **Load more** control. They deduplicate duplicate IDs and avoid unnecessary requests; no more arbitrary first-page cap.
- **Joined groups:** Existing endpoint returns a membership list without documented pagination. The app keeps displaying every group provided; it does not append invented offset parameters.
- **Unchanged:** v0.10.0 personal VRChat login/2FA, foreground Shizuku Node tracking, VRChat notifications/invites, friend requests, verified invite-code extraction, avatar actions, staff-only deployment, relay communication, permanent signing.

## Smoke tests (real device)
1. Install signed v0.11.0 **over** v0.10.0. Do not uninstall. Confirm existing staff deployment, personal VRChat session, and tracking continue.
2. Explore → Private VRChat notes. Confirm saved notes from VRChat are listed, including owner name and note text. Tap a note; confirm its original text loads and can be edited.
3. From a player profile tap View or edit note. Verify existing note text loads; if there are more than 100 notes, use Check older notes until that player is found or the list ends.
4. Type a new note and cancel confirmation: nothing is submitted. Save a test note: confirm it appears on the same account elsewhere in VRChat.
5. Switch VRChat accounts: each person's notes are scoped to the signed-in account. No staff backend sends or stores note contents.
6. Open Online/Offline Friends lists with more than 60 results. Use Load more; verify pages extend without duplicates and offline users aren't shown under Online.
7. Search for a common name and use Load more. Test refresh, rate-limit handling, and preserving already loaded results if page request fails.
8. Favorite worlds with >60 items: Load more to browse beyond the first page. Check groups with >100 memberships still work in one list.
9. Background Node keeps collecting and uploading player sightings while navigating these screens. Staff verification refresh should remain silent.

## Limitations
- Notes are stored on the user's *VRChat account* and thus are **not** device-only encrypted memos. Treat as personal VRChat notes, not shared staff moderation records.
- All profile-changing actions require explicit consent and VRChat permission; API errors are surfaced without discarding entered text.
- Live VRChat API tests cannot run in GitHub Actions without a user session. VRChat may change API behavior or impose rate limits. The source is tested by CI compilation, not live-account integration.
