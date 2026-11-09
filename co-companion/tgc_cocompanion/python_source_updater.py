"""Verified Python-source self-update for the isolated Co-Companion owner preview.

Downloads only an allowlisted Co-Companion source snapshot from a successful
GitHub Actions commit. Each file is checked against its immutable Git blob SHA;
old installs, DPAPI account data, legacy Nodes, and the one Relay are untouched.
No GitHub PAT, extra Relay, EXE, staff rollout, or background auto-install.
"""
from __future__ import annotations

from dataclasses import dataclass
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import tempfile
from urllib.parse import quote
from urllib.request import Request, urlopen

from .owner_updater import (
    BRANCH, REPOSITORY, WORKFLOW, PreviewUpdateError, bundled_build_info,
    update_root,
)

API = "https://api.github.com/repos/" + REPOSITORY
RAW = "https://raw.githubusercontent.com/" + REPOSITORY + "/"
VERSION = "0.10.0"
SOURCE_FILES = (
    "owner_pilot_entry.py", "RUN_OWNER_PILOT.bat",
    "requirements_owner_pilot.txt", "TGC_DESKTOP_TERMS.txt",
    "README_OWNER_PILOT.md",
    "tgc_cocompanion/__init__.py",
    "tgc_cocompanion/permissions.py",
    "tgc_cocompanion/sessions.py",
    "tgc_cocompanion/osc.py",
    "tgc_cocompanion/staff_lease.py",
    "tgc_cocompanion/launch_controller.py",
    "tgc_cocompanion/windows_process.py",
    "tgc_cocompanion/log_diagnostics.py",
    "tgc_cocompanion/pilot_enrollment.py",
    "tgc_cocompanion/owner_pilot.py",
    "tgc_cocompanion/vrchat_personal.py",
    "tgc_cocompanion/staffdex_desktop.py",
    "tgc_cocompanion/desktop_osc_tools.py",
    "tgc_cocompanion/desktop_pages.py",
    "tgc_cocompanion/desktop_theme.py",
    "tgc_cocompanion/desktop_shell.py",
    "tgc_cocompanion/desktop_navigation.py",
    "tgc_cocompanion/desktop_settings.py",
    "tgc_cocompanion/exe_release_updater.py",
    "tgc_cocompanion/desktop_update_page.py",
    "tgc_cocompanion/owner_updater.py",
    "tgc_cocompanion/python_source_updater.py",
    "tgc_cocompanion/desktop_more_pages.py",
    "tgc_cocompanion/desktop_profile_view.py",
    "tgc_cocompanion/desktop_profile_images.py",
    "tgc_cocompanion/desktop_display.py",
    "tgc_cocompanion/desktop_collection_pages.py",
    "tgc_cocompanion/desktop_compact_nav.py",
    "tgc_cocompanion/desktop_explore.py",
    "tgc_cocompanion/desktop_recent_players.py",
    "tgc_cocompanion/desktop_official_chatboxes.py",
    "tgc_cocompanion/desktop_staff_colors.py",
    "tgc_cocompanion/desktop_color_studio.py",
    "tgc_cocompanion/desktop_staffdex_filter.py",
    "tgc_cocompanion/desktop_social_shortcuts.py",
    "tgc_cocompanion/desktop_friend_ordering.py",
    "tgc_cocompanion/desktop_friend_hub.py",
    "tgc_cocompanion/desktop_friend_activity.py",
    "tgc_cocompanion/desktop_friend_favorites.py",
    "tgc_cocompanion/desktop_pinned_friend_alerts.py",
    "tgc_cocompanion/desktop_windows_notifications.py",
    "tgc_cocompanion/legacy/__init__.py",
    "tgc_cocompanion/legacy/tgc_vrchat_multilog.py",
)
MAX_FILE = 2_000_000
MAX_TOTAL = 12_000_000
HEX = re.compile(r"[a-f0-9]{40}\Z")
INSTALL_RECORD = ".source_integrity.json"


@dataclass(frozen=True)
class SourceBuild:
    sha: str
    run_id: int
    run_url: str

    @property
    def short(self) -> str:
        return self.sha[:12]


def _json(url: str) -> dict:
    if not url.startswith(API + "/") or any(c in url for c in ("\r", "\n", "\\", "..")):
        raise PreviewUpdateError("Invalid official GitHub API URL")
    try:
        with urlopen(Request(url, headers={
                "Accept": "application/vnd.github+json",
                "User-Agent": "TGC-CoCompanion-PythonPreview/0.3",
            }), timeout=25) as response:
            data = response.read(800_001)
    except Exception as exc:
        raise PreviewUpdateError("Unable to reach official GitHub update channel") from exc
    if len(data) > 800_000:
        raise PreviewUpdateError("GitHub metadata response too large")
    try:
        value = json.loads(data)
    except (UnicodeError, ValueError) as exc:
        raise PreviewUpdateError("Invalid GitHub update metadata") from exc
    if not isinstance(value, dict):
        raise PreviewUpdateError("Unexpected GitHub response")
    return value


def _raw(url: str, limit: int) -> bytes:
    if not url.startswith(RAW) or any(c in url for c in ("\r", "\n", "\\", "..")):
        raise PreviewUpdateError("Untrusted owner-preview source URL")
    try:
        with urlopen(Request(url, headers={
                "User-Agent": "TGC-CoCompanion-PythonPreview/0.3",
            }), timeout=35) as response:
            data = response.read(limit + 1)
    except Exception as exc:
        raise PreviewUpdateError("Failed downloading signed-off source from GitHub") from exc
    if len(data) > limit:
        raise PreviewUpdateError("Owner-preview source file exceeds safe limit")
    return data


def latest_python_preview(api_get=_json) -> SourceBuild:
    """Only a SUCCESSFUL CI run of the exact owner development branch qualifies."""
    url = API + "/actions/workflows/" + WORKFLOW + "/runs?branch=" + (
        quote(BRANCH, safe="")) + "&status=success&per_page=10"
    result = api_get(url)
    runs = result.get("workflow_runs") if isinstance(result, dict) else None
    if not isinstance(runs, list):
        raise PreviewUpdateError("No GitHub owner-preview workflow runs found")
    for run in runs:
        if not isinstance(run, dict) or run.get("conclusion") != "success":
            continue
        sha, run_id = run.get("head_sha"), run.get("id")
        if (run.get("head_branch") != BRANCH or not isinstance(sha, str) or
                not HEX.fullmatch(sha) or type(run_id) is not int or run_id <= 0):
            continue
        return SourceBuild(sha, run_id,
                           f"https://github.com/{REPOSITORY}/actions/runs/{run_id}")
    raise PreviewUpdateError("No successful Python owner-preview build yet")


def has_newer_python_build(current: dict, build: SourceBuild) -> bool:
    return current.get("sha") != build.sha


def _tree(sha: str, api_get) -> dict:
    if not HEX.fullmatch(sha):
        raise PreviewUpdateError("Invalid Git object SHA")
    data = api_get(API + "/git/trees/" + sha)
    entries = data.get("tree") if isinstance(data, dict) else None
    if not isinstance(entries, list) or data.get("truncated"):
        raise PreviewUpdateError("Missing or incomplete GitHub source directory")
    result = {}
    for entry in entries:
        if not isinstance(entry, dict) or not isinstance(entry.get("path"), str):
            raise PreviewUpdateError("Invalid Git tree entry")
        if entry["path"] in result:
            raise PreviewUpdateError("Duplicate GitHub source entry")
        result[entry["path"]] = entry
    return result


def source_manifest(sha: str, api_get=_json) -> dict[str, str]:
    """Resolve official Git trees; no bulk repository archive or unverified files."""
    if not isinstance(sha, str) or not HEX.fullmatch(sha):
        raise PreviewUpdateError("Invalid owner-preview commit SHA")
    commit = api_get(API + "/git/commits/" + sha)
    tree_sha = commit.get("tree", {}).get("sha") if isinstance(commit, dict) else None
    if not isinstance(tree_sha, str) or not HEX.fullmatch(tree_sha):
        raise PreviewUpdateError("GitHub commit has no usable source tree")
    root_tree = _tree(tree_sha, api_get)
    project = root_tree.get("co-companion")
    if not project or project.get("type") != "tree" or not HEX.fullmatch(str(project.get("sha", ""))):
        raise PreviewUpdateError("Co-Companion project directory was not found")
    cache = {"": _tree(project["sha"], api_get)}
    manifest = {}
    total = 0
    # REQUIRED_FILES remains a minimum compatibility contract. Future screens
    # and feature modules are discovered from the pinned Git tree instead of
    # trusting a fixed list frozen into the PREVIOUS installed version.
    discovered = set(SOURCE_FILES)
    package = cache[""].get("tgc_cocompanion")
    if not package or package.get("type") != "tree":
        raise PreviewUpdateError("Co-Companion Python package missing")
    def collect(tree_sha, prefix, depth=0):
        if depth > 4:
            raise PreviewUpdateError("Owner-preview package nesting exceeds limit")
        for name, item in _tree(tree_sha, api_get).items():
            if not re.fullmatch(r"[A-Za-z_][A-Za-z0-9_]*", name) and not (
                    re.fullmatch(r"[A-Za-z_][A-Za-z0-9_]*[.](?:py|png|ico)", name)):
                continue
            relative = prefix + "/" + name
            if item.get("type") == "tree":
                collect(item["sha"], relative, depth + 1)
            elif item.get("type") == "blob" and re.fullmatch(
                    r"[A-Za-z_][A-Za-z0-9_]*[.](?:py|png|ico)", name):
                discovered.add(relative)
            if len(discovered) > 160:
                raise PreviewUpdateError("Too many owner-preview source files")
    collect(package["sha"], "tgc_cocompanion")
    for relative in sorted(discovered):
        if relative.startswith("/") or "\\" in relative or ".." in Path(relative).parts:
            raise PreviewUpdateError("Invalid allowlisted source path")
        directory = ""
        for segment in relative.split("/")[:-1]:
            parent = cache[directory]
            node = parent.get(segment)
            if not node or node.get("type") != "tree" or not HEX.fullmatch(str(node.get("sha", ""))):
                raise PreviewUpdateError("Required Co-Companion directory is absent")
            directory += ("/" if directory else "") + segment
            if directory not in cache:
                cache[directory] = _tree(node["sha"], api_get)
        item = cache[directory].get(relative.split("/")[-1])
        if not item or item.get("type") != "blob":
            raise PreviewUpdateError("Missing owner-preview file: " + relative)
        blob, size = item.get("sha"), item.get("size")
        if not isinstance(blob, str) or not HEX.fullmatch(blob):
            raise PreviewUpdateError("Missing immutable hash for " + relative)
        if type(size) is not int or size < 0 or size > MAX_FILE:
            raise PreviewUpdateError("Unexpected size for " + relative)
        total += size
        if total > MAX_TOTAL:
            raise PreviewUpdateError("Owner-preview source exceeds expected size")
        manifest[relative] = blob
    return manifest


def _blob_sha(data: bytes) -> str:
    return hashlib.sha1(b"blob " + str(len(data)).encode("ascii") + b"\0" + data).hexdigest()


def _valid_existing(folder: Path, build: SourceBuild) -> bool:
    try:
        info = json.loads((folder / "build_info.json").read_text(encoding="utf-8"))
        record = json.loads((folder / INSTALL_RECORD).read_text(encoding="utf-8"))
        if info.get("sha") != build.sha or info.get("channel") != "owner-preview":
            return False
        files = record.get("files")
        if record.get("sha") != build.sha or not isinstance(files, dict):
            return False
        if not set(SOURCE_FILES).issubset(files) or len(files) > 160:
            return False
        for relative, digest in files.items():
            if (not isinstance(relative, str) or not relative or relative.startswith("/")
                or chr(92) in relative or ".." in relative.split("/")
                or not isinstance(digest, str) or not re.fullmatch(r"[0-9a-f]{64}", digest)):
                return False
            file = folder.joinpath(*relative.split("/"))
            if not file.is_file() or hashlib.sha256(file.read_bytes()).hexdigest() != digest:
                return False
        return True
    except (OSError, ValueError, TypeError, AttributeError):
        return False


def stage_python_update(build: SourceBuild, install_root: Path | None = None,
                        api_get=_json, raw_get=_raw) -> Path:
    """Fetch, validate, and stage a NEW Python version. Does not execute it."""
    if (not isinstance(build, SourceBuild) or not HEX.fullmatch(build.sha) or
            type(build.run_id) is not int or build.run_id <= 0):
        raise PreviewUpdateError("Invalid successful owner-preview build")
    install_root = Path(install_root) if install_root is not None else update_root()
    target = install_root / ("python-" + build.sha[:16])
    entry = target / "owner_pilot_entry.py"
    if target.exists():
        if _valid_existing(target, build):
            return entry
        raise PreviewUpdateError("An existing Python update failed integrity checks; refusing overwrite")
    manifest = source_manifest(build.sha, api_get)
    install_root.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix=".python-source-", dir=install_root) as tmp:
        root = Path(tmp)
        digests = {}
        for relative, expected_blob in manifest.items():
            url = RAW + build.sha + "/co-companion/" + quote(relative, safe="/")
            payload = raw_get(url, MAX_FILE)
            if not isinstance(payload, bytes) or _blob_sha(payload) != expected_blob:
                raise PreviewUpdateError("GitHub source integrity mismatch: " + relative)
            dest = root.joinpath(*relative.split("/"))
            dest.parent.mkdir(parents=True, exist_ok=True)
            dest.write_bytes(payload)
            digests[relative] = hashlib.sha256(payload).hexdigest()
        (root / "build_info.json").write_text(json.dumps({
            "channel": "owner-preview", "version": VERSION, "sha": build.sha,
            "run_id": build.run_id, "source_format": "python",
        }, indent=2), encoding="utf-8")
        (root / INSTALL_RECORD).write_text(json.dumps({
            "sha": build.sha, "files": digests,
        }, sort_keys=True, indent=2), encoding="utf-8")
        os.replace(root, target)
    return entry


def python_launch_command(entry: Path) -> list[str]:
    """Launch via installed Python, or Windows py launcher from legacy frozen EXE."""
    import sys
    if not entry.is_file() or entry.name != "owner_pilot_entry.py":
        raise PreviewUpdateError("New Python owner-preview entrypoint missing")
    if getattr(sys, "frozen", False):
        import shutil as _shutil
        py = _shutil.which("py")
        if not py:
            raise PreviewUpdateError("Python launcher unavailable; install Python 3.11+")
        return [py, "-3", str(entry)]
    return [sys.executable, str(entry)]
