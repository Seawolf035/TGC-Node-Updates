"""Owner-preview Updates: verified one-click EXE releases; Python legacy fallback.

EXE mode checks a separate GitHub owner prerelease channel, downloads a verified
Windows bundle, smoke-checks it before restart, and retargets the user's single
Start Menu shortcut. No PAT, no production Node/Relay/staff manifest updates.
"""
from __future__ import annotations
from pathlib import Path
import subprocess
import sys
import tkinter as tk
from tkinter import messagebox, ttk
import webbrowser

from .owner_updater import bundled_build_info, PreviewUpdateError
from .exe_release_updater import (
    latest_exe_release, has_newer_exe_release, stage_exe_release,
    smoke_check_exe, update_start_menu_shortcut,
)
from .python_source_updater import (
    latest_python_preview, has_newer_python_build, stage_python_update,
    python_launch_command,
)


class OwnerUpdatePage:
    def __init__(self, owner, notebook):
        self.owner = owner
        self.root = owner.root
        self.is_exe = bool(getattr(sys, "frozen", False))
        self.current = bundled_build_info()
        self.available = None
        self.staged = None
        tab = ttk.Frame(notebook, padding=18)
        notebook.add(tab, text="Updates")
        ttk.Label(tab, text="Co-Companion EXE Updates" if self.is_exe else
                  "Co-Companion Python Updates",
                  font=("Segoe UI", 16, "bold")).pack(anchor="w")
        ttk.Label(tab, text=(
            "Owner-preview only. Updates never change any installed staff Node, "
            "the single Relay, Main Launcher or production update manifests."
        ), wraplength=800).pack(anchor="w", pady=(5, 13))
        installed = str(self.current.get("sha") or "")
        ttk.Label(tab, text="Running v" + str(self.current.get("version", "unknown"))
                  + (f" · {installed[:12]}" if installed else " · legacy preview")
                  ).pack(anchor="w", pady=4)
        self.status = tk.StringVar(value="Check the official owner preview for updates.")
        ttk.Label(tab, textvariable=self.status, wraplength=820).pack(anchor="w", pady=(5, 12))
        bar = ttk.Frame(tab)
        bar.pack(anchor="w", pady=6)
        ttk.Button(bar, text="Check for Updates", command=self.check).pack(side="left", padx=(0, 8))
        self.install_button = ttk.Button(
            bar, text="Download & Install EXE" if self.is_exe else "Install Python Update",
            command=self.install, state="disabled")
        self.install_button.pack(side="left", padx=(0, 8))
        ttk.Button(bar, text="View Release on GitHub", command=self.open_build
                   ).pack(side="left")
        ttk.Separator(tab, orient="horizontal").pack(fill="x", pady=17)
        ttk.Label(tab, text="A single application, a single shortcut",
                  font=("Segoe UI", 12, "bold")).pack(anchor="w")
        description = (
            "1. Check for an approved owner-preview release.\\n"
            "2. Download the Windows EXE package automatically from GitHub.\\n"
            "3. Verify GitHub's SHA256 and the exact bundled build identity.\\n"
            "4. Install alongside the old version and test startup before switching.\\n"
            "5. Update your TGC Co-Companion Start Menu shortcut and restart.\\n"
            "The old version stays intact for rollback; no Python or BAT is needed."
            if self.is_exe else
            "This is the old Python preview updater. The next owner-preview version "
            "uses a standalone Windows EXE and its own self-update channel. "
            "Your saved settings are shared between both."
        )
        ttk.Label(tab, text=description, justify="left",
                  wraplength=820).pack(anchor="w", pady=(8, 13))
        self.last_installed = tk.StringVar(value="")
        ttk.Label(tab, textvariable=self.last_installed,
                  wraplength=820).pack(anchor="w")
        if self.is_exe:
            # Nonblocking version check shortly after opening the app.
            self.root.after(4500, self.check)

    def check(self):
        self.available = None
        self.install_button.configure(state="disabled")
        self.status.set("Checking official owner preview…")
        def work():
            try:
                return (latest_exe_release() if self.is_exe else
                        latest_python_preview()), None
            except Exception as exc:
                return None, str(exc)
        def done(result):
            build, error = result
            if error:
                self.status.set("Update check: " + error)
                return
            self.available = build
            newer = (has_newer_exe_release(self.current, build) if self.is_exe else
                     has_newer_python_build(self.current, build))
            if newer:
                self.install_button.configure(state="normal")
                label = "v" + build.version if self.is_exe else build.short
                self.status.set("New verified owner-preview ready: " + label)
            else:
                self.status.set("You're up to date on this owner-preview channel.")
        self.owner._run_async(work, done)

    def open_build(self):
        if self.available:
            webbrowser.open(self.available.page if self.is_exe else self.available.run_url)
        else:
            webbrowser.open(
                "https://github.com/Seawolf035/TGC-Node-Updates/releases"
                if self.is_exe else
                "https://github.com/Seawolf035/TGC-Node-Updates/actions")

    def install(self):
        build = self.available
        if not build:
            messagebox.showinfo("Update", "Check for an available update first.")
            return
        newer = (has_newer_exe_release(self.current, build) if self.is_exe else
                 has_newer_python_build(self.current, build))
        if not newer:
            messagebox.showinfo("Up to date", "No newer owner preview is available.")
            return
        if not messagebox.askyesno("Update Co-Companion",
                "Download and install this verified owner-preview update?\\n\\n"
                "The current version and all AppData account settings will remain untouched.",
                parent=self.root):
            return
        self.install_button.configure(state="disabled")
        self.status.set("Downloading and verifying owner-preview update…")
        def work():
            try:
                return (stage_exe_release(build) if self.is_exe else
                        stage_python_update(build)), None
            except Exception as exc:
                return None, str(exc)
        def done(result):
            executable, error = result
            if error:
                self.status.set("Update refused safely: " + error)
                self.install_button.configure(state="normal")
                return
            self.staged = Path(executable)
            self.last_installed.set("Installed verified preview at " + str(self.staged.parent))
            self.status.set("Update staged; current app remains intact.")
            if messagebox.askyesno("Restart Co-Companion",
                    "The update is ready. Check startup and restart into the new version?",
                    parent=self.root):
                self._restart(self.staged)
        self.owner._run_async(work, done)

    def _restart(self, entry: Path):
        try:
            if self.is_exe:
                smoke_check_exe(entry)
                command = [str(entry)]
            else:
                command = python_launch_command(entry)
                result = subprocess.run(command + ["--smoke-check"],
                        cwd=str(entry.parent), capture_output=True, text=True,
                        timeout=45, check=False)
                if result.returncode:
                    raise PreviewUpdateError("New Python GUI did not start: " +
                                             (result.stderr or result.stdout)[-1200:])
            subprocess.Popen(command, cwd=str(entry.parent), close_fds=True)
            if self.is_exe:
                # Only retarget shortcut after new EXE passes smoke and starts.
                try:
                    update_start_menu_shortcut(entry)
                except PreviewUpdateError as exc:
                    self.last_installed.set("Updated, but shortcut needs repair: " + str(exc))
        except (OSError, PreviewUpdateError, subprocess.TimeoutExpired) as exc:
            self.status.set("Update did not launch; old app is unchanged.")
            messagebox.showerror("Cannot start update", str(exc)[-1800:],
                                 parent=self.root)
            return
        self.owner._close()
