"""Python-first owner-preview Updates tab: Check -> Install -> Restart.

No GitHub login/PAT or EXE zip needed. Only a successful official owner CI
commit is eligible. Downloaded Python files are verified against immutable
GitHub Git blob hashes and installed to a NEW folder for easy rollback.
"""
from __future__ import annotations

from pathlib import Path
import subprocess
import tkinter as tk
from tkinter import messagebox, ttk
import webbrowser

from .owner_updater import bundled_build_info, PreviewUpdateError
from .python_source_updater import (
    latest_python_preview, has_newer_python_build, stage_python_update,
    python_launch_command,
)


class OwnerUpdatePage:
    def __init__(self, owner, notebook):
        self.owner = owner
        self.root = owner.root
        self.current = bundled_build_info()
        self.available = None
        self.staged = None
        tab = ttk.Frame(notebook, padding=18)
        notebook.add(tab, text="Updates")
        ttk.Label(tab, text="Co-Companion Python Updates",
                  font=("Segoe UI", 16, "bold")).pack(anchor="w")
        ttk.Label(tab, text=(
            "Owner-preview channel only. No EXE downloads, no GitHub login, "
            "no extra Relay, and no updates to staff, Nodes or Main Launcher."
        ), wraplength=830).pack(anchor="w", pady=(5, 13))
        installed = str(self.current.get("sha") or "")
        ttk.Label(tab, text="Running: v" + str(self.current.get("version", "unknown"))
                  + (f"  ·  commit {installed[:12]}" if installed else "  ·  legacy preview")
                  ).pack(anchor="w", pady=4)
        self.status = tk.StringVar(value="Click Check For Updates to find the latest tested Python build.")
        ttk.Label(tab, textvariable=self.status, wraplength=830).pack(anchor="w", pady=(5, 12))
        row = ttk.Frame(tab)
        row.pack(anchor="w", pady=(5, 8))
        self.check_button = ttk.Button(row, text="Check For Updates", command=self.check)
        self.check_button.pack(side="left", padx=(0, 9))
        self.install_button = ttk.Button(row, text="Install Python Update", command=self.install)
        self.install_button.pack(side="left", padx=(0, 9))
        self.install_button.configure(state="disabled")
        ttk.Button(row, text="Open Build on GitHub", command=self.open_build).pack(side="left")
        ttk.Separator(tab, orient="horizontal").pack(fill="x", pady=16)
        ttk.Label(tab, text="How the built-in updater works",
                  font=("Segoe UI", 12, "bold")).pack(anchor="w")
        ttk.Label(tab, text=(
            "1. Find the latest successful CI run on the owner development branch.\n"
            "2. Download only the exact Python files from that successful commit.\n"
            "3. Verify every file against GitHub's immutable Git blob hash.\n"
            "4. Install into a new version folder, never over the running app.\n"
            "5. Confirm restart into the Python update. The previous version remains for rollback."
        ), justify="left", wraplength=830).pack(anchor="w", pady=(7, 12))
        ttk.Label(tab, text=(
            "Pilot authorization, encrypted VRChat sessions, StaffDex saves and other "
            "owner settings remain under LOCALAPPDATA/TGC/CoCompanionOwnerPilot. "
            "No staff application, Node uploader, production database or Relay is changed. "
            "Updates are manual and cannot start without your approval."
        ), wraplength=830).pack(anchor="w")
        self.last_installed = tk.StringVar(value="")
        ttk.Label(tab, textvariable=self.last_installed,
                  wraplength=830).pack(anchor="w", pady=(10, 4))

    def check(self):
        self.available = None
        self.install_button.configure(state="disabled")
        self.status.set("Checking successful official Python preview builds…")

        def work():
            try:
                return latest_python_preview(), None
            except Exception as exc:
                return None, str(exc)

        def done(result):
            build, error = result
            if error:
                self.status.set("Update check failed: " + error)
                return
            self.available = build
            if has_newer_python_build(self.current, build):
                self.status.set(f"Verified Python preview available: {build.short} "
                                f"(successful GitHub Actions run {build.run_id}).")
                self.install_button.configure(state="normal")
            else:
                self.status.set(f"Already running the latest tested Python preview ({build.short}).")

        self.owner._run_async(work, done)

    def open_build(self):
        if self.available:
            webbrowser.open(self.available.run_url)
        else:
            self.status.set("Check for updates before opening the latest build.")

    def install(self):
        build = self.available
        if not build or not has_newer_python_build(self.current, build):
            messagebox.showinfo("No update", "Check for a newer Python update first.")
            return
        if not messagebox.askyesno("Install owner-preview Python update",
                "Download and verify this tested Python update from GitHub?\n\n"
                "Co-Companion will install it alongside your existing version. "
                "All saved owner settings and production TGC systems remain unchanged."):
            return
        self.install_button.configure(state="disabled")
        self.status.set(f"Downloading and verifying Python sources at {build.short}…")

        def work():
            try:
                return stage_python_update(build), None
            except Exception as exc:
                return None, str(exc)

        def done(result):
            entry, error = result
            if error:
                self.status.set("Python update failed safely: " + error)
                self.install_button.configure(state="normal")
                return
            self.staged = Path(entry)
            self.last_installed.set(f"Installed verified Python update: {self.staged.parent}")
            self.status.set("Update installed. Restart when you are ready.")
            if messagebox.askyesno("Restart Into Python Co-Companion",
                    "The Python update is ready. Restart into the new version now?\n\n"
                    "Your old install remains available for rollback."):
                self._restart(self.staged)

        self.owner._run_async(work, done)

    def _restart(self, entry: Path):
        try:
            command = python_launch_command(entry)
            subprocess.Popen(command, cwd=str(entry.parent), close_fds=True)
        except (OSError, PreviewUpdateError) as exc:
            self.status.set("Update installed, but restart failed: " + str(exc))
            self.last_installed.set("Run RUN_OWNER_PILOT.bat in " + str(entry.parent))
            return
        self.owner._close()
