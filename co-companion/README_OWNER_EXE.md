# Co-Companion v0.5.2+ — ACTUAL IN-APP EXE UPDATES

**Update channel fixed.** Previously CI uploaded EXEs only as GitHub Actions
artifacts, while the installed v0.5.0/v0.5.1 app checked only GitHub Releases.
The app therefore could not discover new builds. Now a dedicated
`owner-exe-release` GitHub Actions job runs **only after both Windows and Linux
tests pass** on the owner-preview branch. It publishes the exact verified
Windows ZIP as a separate GitHub **pre-release** named
`co-companion-owner-v<version>`, using the version in
`co-companion/OWNER_VERSION.txt`. Publishing is idempotent for each version:
after the first successful release, additional branch commits require a
version bump to publish a different version. A failed build is never released.

If you're running v0.5.0 or v0.5.1, you do **not** need another installer:
open **Updates → Check for Updates → Download & Install EXE** after v0.5.2
appears in GitHub Releases. The app verifies the release checksum, stages the
EXE alongside your current installation, runs a GUI startup smoke check, and
restarts into the updated version. It also retargets the Start Menu shortcut.
Old installs and saved VRChat/DPAPI settings remain unchanged.

**Distribution/privacy:** the owner-preview ZIP is publicly downloadable
from this existing public GitHub repository. It is NOT a staff release and does
not grant staff authorization. Publishing does not update Nodes, Relay,
Main Launcher, Android/Quest apps, or any production manifest. Do not add
credentials, personal data, account tokens or proprietary private-repo source
to this public source/build artifact.

---

# TGC Co-Companion — Owner-only self-updating Windows EXE

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

**Owner-release publishing:** GitHub Actions artifacts are still retained
for testing, but the separate owner EXE release job now copies the tested
Windows archive into GitHub Releases automatically on the first passing CI run
for each version. The release asset is publicly downloadable (not a staff
release). `co-companion/OWNER_VERSION.txt` is the only version marker the
workflow uses to decide whether a new release needs publishing.
The updater does not require a GitHub PAT and it never touches the staff
update channel.

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
