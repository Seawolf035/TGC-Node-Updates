"""Owner-preview EXE updater via public GitHub pre-release assets.

A manual owner-only GitHub release workflow publishes this NON-STAFF channel.
No GitHub PAT or Apps Script credentials. Validates exact repository, release
tag/semver, GitHub asset SHA256, version/commit build metadata and ZIP paths.
Installs side-by-side; never overwrites an executable while running.
"""
from __future__ import annotations

from dataclasses import dataclass
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tempfile
from urllib.request import Request, urlopen
from zipfile import ZipFile, BadZipFile

from .owner_updater import (
    REPOSITORY, APPLICATION, PreviewUpdateError, _extract, update_root,
    bundled_build_info,
)

API = "https://api.github.com/repos/" + REPOSITORY
DOWNLOAD = "https://github.com/" + REPOSITORY + "/releases/download/"
ASSET = "TGC-CoCompanion-OwnerPreview-WINDOWS.zip"
TAG_PREFIX = "co-companion-owner-v"
MAX_DOWNLOAD = 90_000_000
MAX_RELEASES = 25
VERSION = re.compile(r"(0|[1-9][0-9]*)[.](0|[1-9][0-9]*)[.](0|[1-9][0-9]*)\Z")
SHA = re.compile(r"[0-9a-f]{40}\Z")
DIGEST = re.compile(r"sha256:([0-9a-f]{64})\Z")


@dataclass(frozen=True)
class ExeRelease:
    version: str
    sha: str
    url: str
    digest: str
    page: str

    @property
    def version_tuple(self):
        return tuple(int(x) for x in self.version.split("."))


def version_tuple(value):
    """Accept installed old builds reporting e.g. 0.4 or 0.5.0."""
    value = str(value or "")
    if re.fullmatch(r"[0-9]+[.][0-9]+", value):
        value += ".0"
    if not VERSION.fullmatch(value):
        return (0, 0, 0)
    return tuple(int(x) for x in value.split("."))


def _release_json():
    url = API + "/releases?per_page=" + str(MAX_RELEASES)
    req = Request(url, headers={
        "Accept": "application/vnd.github+json",
        "User-Agent": "TGC-CoCompanion-OwnerPreview/0.5",
    })
    try:
        with urlopen(req, timeout=20) as response:
            raw = response.read(800_001)
    except Exception as exc:
        raise PreviewUpdateError("Cannot reach official owner-preview release channel") from exc
    if len(raw) > 800_000:
        raise PreviewUpdateError("GitHub response is unexpectedly large")
    try:
        result = json.loads(raw)
    except (ValueError, UnicodeError) as exc:
        raise PreviewUpdateError("GitHub returned invalid release metadata") from exc
    if not isinstance(result, list):
        raise PreviewUpdateError("GitHub release channel is unavailable")
    return result


def latest_exe_release(fetch=_release_json) -> ExeRelease:
    releases = fetch()
    if not isinstance(releases, list):
        raise PreviewUpdateError("Invalid owner-preview channel response")
    candidates = []
    for rel in releases:
        if not isinstance(rel, dict) or rel.get("draft") is not False or rel.get("prerelease") is not True:
            continue
        tag = rel.get("tag_name")
        if not isinstance(tag, str) or not tag.startswith(TAG_PREFIX):
            continue
        version = tag[len(TAG_PREFIX):]
        sha = rel.get("target_commitish")
        if not VERSION.fullmatch(version) or not isinstance(sha, str) or not SHA.fullmatch(sha):
            continue
        if rel.get("html_url") != "https://github.com/" + REPOSITORY + "/releases/tag/" + tag:
            continue
        for asset in rel.get("assets", []):
            if not isinstance(asset, dict) or asset.get("name") != ASSET:
                continue
            expected = DOWNLOAD + tag + "/" + ASSET
            if asset.get("browser_download_url") != expected:
                continue
            if not isinstance(asset.get("size"), int) or not (1_000_000 <= asset["size"] <= MAX_DOWNLOAD):
                continue
            digest = asset.get("digest")
            if not isinstance(digest, str) or not DIGEST.fullmatch(digest):
                continue
            candidates.append(ExeRelease(version, sha, expected, digest, rel["html_url"]))
    if not candidates:
        raise PreviewUpdateError(
            "No published Co-Companion EXE owner preview yet. "
            "The maintainer must run the manual owner release workflow first.")
    return max(candidates, key=lambda x: x.version_tuple)


def has_newer_exe_release(current, release):
    # A later release must be strictly newer, not just a different commit.
    return release.version_tuple > version_tuple(current.get("version"))


def _verified_existing(folder: Path, release: ExeRelease) -> bool:
    try:
        info = json.loads((folder / "build_info.json").read_text(encoding="utf-8-sig"))
        return (info.get("channel") == "owner-preview"
                and info.get("sha") == release.sha
                and version_tuple(info.get("version")) == release.version_tuple
                and (folder / APPLICATION).is_file()
                and (folder / "_internal").is_dir())
    except (ValueError, OSError, TypeError):
        return False


def _download_asset(release, dest, opener=urlopen):
    digest = hashlib.sha256()
    total = 0
    req = Request(release.url, headers={"User-Agent": "TGC-CoCompanion-OwnerPreview/0.5"})
    try:
        with opener(req, timeout=90) as response, dest.open("xb") as out:
            while True:
                chunk = response.read(1024 * 1024)
                if not chunk:
                    break
                total += len(chunk)
                if total > MAX_DOWNLOAD:
                    raise PreviewUpdateError("Owner-preview download exceeded maximum size")
                digest.update(chunk)
                out.write(chunk)
    except PreviewUpdateError:
        raise
    except Exception as exc:
        raise PreviewUpdateError("Could not download official Co-Companion Windows ZIP") from exc
    if "sha256:" + digest.hexdigest() != release.digest:
        raise PreviewUpdateError("GitHub release checksum mismatch; update rejected")


def stage_exe_release(release: ExeRelease, root: Path | None = None,
                      opener=urlopen) -> Path:
    """Download verified release in NEW folder; do not run or update shortcuts."""
    if not isinstance(release, ExeRelease) or not SHA.fullmatch(release.sha):
        raise PreviewUpdateError("Invalid owner-preview release")
    if not release.url.startswith(DOWNLOAD) or not DIGEST.fullmatch(release.digest):
        raise PreviewUpdateError("Release URL/checksum is invalid")
    dest_root = Path(root) if root is not None else update_root()
    dest = dest_root / ("exe-" + release.version + "-" + release.sha[:12])
    exe = dest / APPLICATION
    if dest.exists():
        if _verified_existing(dest, release):
            return exe
        raise PreviewUpdateError("Existing EXE update is invalid; refusing overwrite")
    dest_root.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix=".exe-preview-", dir=dest_root) as work:
        tmp = Path(work)
        archive = tmp / "release.zip"
        _download_asset(release, archive, opener)
        stage = tmp / "unpacked"
        stage.mkdir()
        try:
            with ZipFile(archive) as zipped:
                _extract(zipped, stage)
        except (BadZipFile, OSError) as exc:
            raise PreviewUpdateError("Invalid EXE release archive") from exc
        if not _verified_existing(stage, release):
            raise PreviewUpdateError("EXE release build identity or runtime resources missing")
        # Rename into a dedicated version directory; no running files touched.
        os.replace(stage, dest)
    return exe


def smoke_check_exe(exe: Path, timeout=45, runner=subprocess.run) -> None:
    if not exe.is_file() or exe.name != APPLICATION:
        raise PreviewUpdateError("Staged Windows executable is missing")
    try:
        result = runner([str(exe), "--smoke-check"], cwd=str(exe.parent),
                        capture_output=True, text=True, timeout=timeout, check=False)
    except (OSError, subprocess.TimeoutExpired) as exc:
        raise PreviewUpdateError("Updated EXE could not pass startup checks") from exc
    if result.returncode:
        error = (result.stderr or result.stdout or "Startup failed")[-1200:]
        raise PreviewUpdateError("New EXE failed startup verification: " + error)


def start_menu_link() -> Path:
    appdata = os.environ.get("APPDATA")
    if not appdata:
        raise PreviewUpdateError("Windows Start Menu directory is unavailable")
    return (Path(appdata) / "Microsoft" / "Windows" / "Start Menu" /
            "Programs" / "TGC Co-Companion.lnk")


def update_start_menu_shortcut(exe: Path, runner=subprocess.run) -> Path:
    """Create/update one stable Start Menu shortcut for current Windows user."""
    if os.name != "nt" or not exe.is_file() or exe.name != APPLICATION:
        raise PreviewUpdateError("Start Menu shortcuts require the Windows EXE")
    powershell = shutil.which("powershell.exe")
    if not powershell:
        raise PreviewUpdateError("Windows PowerShell was not found")
    link = start_menu_link()
    link.parent.mkdir(parents=True, exist_ok=True)
    script = (
        "$ws = New-Object -ComObject WScript.Shell; "
        "$lnk = $ws.CreateShortcut($env:TGC_SHORTCUT); "
        "$lnk.TargetPath = $env:TGC_EXE; "
        "$lnk.WorkingDirectory = $env:TGC_EXE_DIR; "
        "$lnk.IconLocation = $env:TGC_EXE + ',0'; "
        "$lnk.Description = 'TGC Co-Companion Owner Preview'; $lnk.Save()"
    )
    env = os.environ.copy()
    env.update({"TGC_SHORTCUT": str(link), "TGC_EXE": str(exe),
                "TGC_EXE_DIR": str(exe.parent)})
    try:
        result = runner([powershell, "-NoProfile", "-NonInteractive",
                         "-Command", script], env=env, timeout=20,
                         capture_output=True, check=False)
    except (OSError, subprocess.TimeoutExpired) as exc:
        raise PreviewUpdateError("Could not create Start Menu shortcut") from exc
    if result.returncode:
        raise PreviewUpdateError("Windows could not save the Start Menu shortcut")
    return link


def maybe_create_shortcut_for_running_exe() -> None:
    """Best effort at startup; never run in source preview or CI smoke tests."""
    if os.name != "nt" or not getattr(sys, "frozen", False) or os.environ.get(
            "TGC_COCOMPANION_SMOKE") == "1":
        return
    exe = Path(sys.executable).resolve()
    try:
        update_start_menu_shortcut(exe)
    except PreviewUpdateError:
        # Manual shortcut can still be created using Settings. Never block app.
        pass
