# TGC Co-Companion v0.5.0 — Owner-only self-updating Windows EXE

This is **not a staff release** and does not alter the one live TGC Relay,
Nodes, Staff Launcher, Android/Quest Companion, Main Launcher, VRChat session
cookies, pilot verification or production update feed.

## Installation — without Python or batch files

1. Open the latest successful CI build of **Co-Companion foundation tests
   (nonpublishing)** on branch `feature/co-companion-foundation`.
2. Download `TGC-CoCompanion-OWNER-EXE-NOT-STAFF` from **Artifacts**.
   GitHub downloads an outer artifact ZIP; extract it to find
   `TGC_CoCompanion_OwnerPreview_v0.5_WINDOWS.zip`.
3. Extract that Windows ZIP **with its full `_internal` folder** and
   double-click `TGC_CoCompanion_OwnerPreview.exe`.
4. On first run click **Yes** to install for your Windows user. Co-Companion
   copies only its own EXE, build metadata, README and runtime files to:
   `%LOCALAPPDATA%\TGC\CoCompanionOwnerPilot\installed_versions\exe-0.5.0-<sha>`
   and runs an offline GUI smoke check **before** it switches.
5. An **all-users installation or administrator access is NOT needed**.
   A stable `TGC Co-Companion` entry appears in your Windows **Start Menu**.
   Pin that shortcut to Start or the taskbar. It is automatically retargeted
   when a later EXE passes validation and is installed.

Your previous v0.3/0.4 Python source folders and saved state are left intact.
You do **not** need to run `RUN_OWNER_PILOT.bat` again once installed.

If shortcut creation fails, use **Settings → Create / Repair Start Menu
Shortcut** in the running EXE.

## One-click EXE updates after the first installation

**Updates → Check for Updates → Download & Install EXE → Restart.**

A successful updater reads only `Seawolf035/TGC-Node-Updates` **public,
non-staff GitHub prereleases**, using the tag convention
`co-companion-owner-vMAJOR.MINOR.PATCH` and this exact published asset:

`TGC-CoCompanion-OwnerPreview-WINDOWS.zip`

The GitHub release must have:
- `draft=false`, `prerelease=true`.
- `target_commitish` set to the exact successful owner-preview CI commit
  (40 hex characters), NOT an arbitrary branch name.
- The exact asset name above and a GitHub `sha256:` asset digest.
- A Windows ZIP with `TGC_CoCompanion_OwnerPreview.exe`, `_internal` folder,
  and `build_info.json` containing matching `owner-preview` channel,
  version and commit.

The updater downloads the ZIP over HTTPS, checks SHA256, rejects zip-slip and
symlink archive paths, validates the bundled version/commit, stages the new
version alongside the running executable, and **runs `--smoke-check` before
starting it**. A bad download or failed startup keeps the old EXE available.
Settings and encrypted VRChat sessions remain in the existing owner AppData.

**Important owner publication limitation:** The EXE cannot automatically
download an Actions artifact without GitHub authentication. GitHub Releases
provides public downloads without a PAT. The repository is public, so owner
prerelease files there are *publicly downloadable*, even though the application
keeps its owner-only staff authorization. **Do not publish a release until the
owner chooses to make that preview public.** Merely uploading the Actions
artifact is NOT a release and will not enable one-click EXE updates. The
GitHub public release channel is independent of the existing staff manifest.

### To publish the first preview manually (maintainer action)

After verifying the completed CI run on a pinned v0.5.0 commit:

1. In GitHub, create a new **pre-release** using tag
   `co-companion-owner-v0.5.0` targeting that exact successful commit.
2. Attach the **inner Windows ZIP** from the successful Actions artifact,
   renamed to `TGC-CoCompanion-OwnerPreview-WINDOWS.zip`.
3. Publish the owner prerelease. Verify GitHub's release REST API reports the
   exact SHA target and `sha256:` digest, then use Check For Updates in EXE.

**No GitHub release has been published automatically by the ordinary CI job.**
Future releases need the same approved publication step.

## Recovery

Keep the last known working EXE; the updater never modifies it. To roll back,
launch an older version directly from `installed_versions\exe-...`.
The first successful run of an older EXE re-points the Start Menu link to it.
No stored owner sessions or production databases are deleted.

The earlier Python v0.4 crash (`ModuleNotFoundError: desktop_shell`) came
from the **old Python updater having a static source list**. The EXE now
bundles imported UI modules through PyInstaller, and the Windows CI verifies
real EXE GUI startup before packaging. The Python updater's future-file
discovery has also been corrected but is no longer the recommended path.
