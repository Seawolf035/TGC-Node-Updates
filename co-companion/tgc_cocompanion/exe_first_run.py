"""One-time per-user installation for the owner-only Windows EXE.

Called from the frozen EXE when run from an extracted download folder.
Prompts for consent, copies the full onedir bundle into the existing owner's
AppData version directory, tests the new EXE, adds Start Menu shortcut, and
launches the installed copy. No Python, admin rights, production changes.
"""
from __future__ import annotations

import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tempfile

from .owner_updater import APPLICATION, PreviewUpdateError, bundled_build_info, update_root
from .exe_release_updater import smoke_check_exe, update_start_menu_shortcut, version_tuple


def is_installed_exe(executable: Path, install_root: Path | None = None) -> bool:
    root = (Path(install_root) if install_root is not None else update_root()).resolve()
    try:
        return executable.resolve().is_relative_to(root)
    except (OSError, ValueError):
        return False


def _confirmed_build(source: Path) -> tuple[str, str]:
    try:
        info = json.loads((source / "build_info.json").read_text(encoding="utf-8-sig"))
        sha = info["sha"]
        version = str(info["version"])
        if (info.get("channel") != "owner-preview"
                or not isinstance(sha, str)
                or not re.fullmatch(r"[a-f0-9]{40}", sha)
                or version_tuple(version) <= (0, 0, 0)
                or not (source / APPLICATION).is_file()
                or not (source / "_internal").is_dir()):
            raise PreviewUpdateError("Owner-preview Windows bundle is incomplete")
        return version, sha
    except (OSError, ValueError, KeyError, TypeError) as exc:
        raise PreviewUpdateError(
            "This EXE is missing its release resources; extract the complete ZIP first.") from exc


def stage_first_install(source: Path, install_root: Path | None = None) -> Path:
    """Copy only trusted extracted app tree; never touch user settings/Node."""
    source = Path(source).resolve()
    version, sha = _confirmed_build(source)
    install_root = Path(install_root) if install_root else update_root()
    destination = install_root / ("exe-" + version + "-" + sha[:12])
    target = destination / APPLICATION
    if destination.exists():
        if _confirmed_build(destination) == (version, sha):
            return target
        raise PreviewUpdateError("Existing version folder is damaged; refusing to overwrite")
    if any(path.is_symlink() for path in source.rglob("*")):
        raise PreviewUpdateError("Installer source contains unsupported symbolic links")
    install_root.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix=".first-install-", dir=install_root) as temp:
        staged = Path(temp) / "app"
        staged.mkdir()
        # Never copy arbitrary files (including possible credentials or DBs)
        # accidentally placed alongside the downloaded EXE.
        shutil.copytree(source / "_internal", staged / "_internal")
        for filename in (APPLICATION, "build_info.json", "README_OWNER_PILOT.md"):
            candidate = source / filename
            if candidate.is_file():
                shutil.copy2(candidate, staged / filename)
        if _confirmed_build(staged) != (version, sha):
            raise PreviewUpdateError("Copied Co-Companion bundle failed integrity checks")
        os.replace(staged, destination)
    return target


def first_run_install_dialog() -> bool:
    """Return True only if an installed copy was successfully started."""
    if not getattr(sys, "frozen", False) or os.name != "nt":
        return False
    if "--smoke-check" in sys.argv or os.environ.get("TGC_COCOMPANION_SMOKE") == "1":
        return False
    source_exe = Path(sys.executable).resolve()
    if is_installed_exe(source_exe):
        return False
    import tkinter as tk
    from tkinter import messagebox
    root = tk.Tk()
    root.withdraw()
    try:
        if not messagebox.askyesno(
                "Install TGC Co-Companion",
                "Install Co-Companion for this Windows account and add it to "
                "the Start Menu?\\n\\n"
                "You will launch it like a normal Windows app. Future "
                "updates will install from inside Co-Companion.\\n\\n"
                "Existing pilot verification, VRChat sessions, other Nodes, "
                "and the single TGC Relay will not be changed.",
                parent=root):
            return False
        try:
            installed = stage_first_install(source_exe.parent)
            smoke_check_exe(installed)
            update_start_menu_shortcut(installed)
            subprocess.Popen([str(installed)], cwd=str(installed.parent), close_fds=True)
            return True
        except (PreviewUpdateError, OSError) as exc:
            messagebox.showerror("Co-Companion installation failed",
                                 str(exc)[-1500:] +
                                 "\\n\\nThe original download is unchanged.",
                                 parent=root)
            return False
    finally:
        root.destroy()
