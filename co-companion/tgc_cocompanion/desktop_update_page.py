"""In-app owner-preview updates page, GitHub Actions source only.

No token is stored. GitHub login/download happens in your regular browser.
The original outer artifact ZIP is verified against the successful GitHub
workflow artifact SHA256 and staged outside the running app. Previous version
is preserved; owner explicitly confirms relaunch. No staff updater touched.
"""
from __future__ import annotations

import json
import os
from pathlib import Path
import subprocess
import sys
import tkinter as tk
from tkinter import filedialog, messagebox, ttk
import webbrowser

from .owner_updater import (
    bundled_build_info, latest_preview, has_newer_build, stage_download,
    update_root, PreviewUpdateError,
)


class OwnerUpdatePage:
    def __init__(self, owner, notebook):
        self.owner=owner
        self.root=owner.root
        self.current=bundled_build_info()
        self.available=None
        self.staged=None
        tab=ttk.Frame(notebook,padding=18)
        notebook.add(tab,text="Updates")
        ttk.Label(tab,text="Co-Companion Updates",
                  font=("Segoe UI",16,"bold")).pack(anchor="w")
        ttk.Label(tab,text="Owner-preview channel only. Does not update any staff install, Node, Main Launcher or Relay.",
                  wraplength=850).pack(anchor="w",pady=(5,13))
        installed=str(self.current.get("sha") or "")
        ttk.Label(tab,text="Installed preview: v"+str(self.current.get("version","unknown"))+
                  (f" · {installed[:12]}" if installed else " · Legacy owner preview")).pack(anchor="w",pady=4)
        self.status=tk.StringVar(value="Click Check For Updates to query official owner-preview builds.")
        ttk.Label(tab,textvariable=self.status,wraplength=850).pack(anchor="w",pady=(5,12))
        row=ttk.Frame(tab);row.pack(anchor="w",pady=(5,8))
        ttk.Button(row,text="Check For Updates",command=self.check).pack(side="left",padx=(0,9))
        ttk.Button(row,text="Open Latest Build in GitHub",command=self.open_download).pack(side="left",padx=(0,9))
        ttk.Button(row,text="Install Downloaded Update ZIP",command=self.install_zip).pack(side="left")
        ttk.Separator(tab,orient="horizontal").pack(fill="x",pady=16)
        ttk.Label(tab,text="How updates work",font=("Segoe UI",12,"bold")).pack(anchor="w")
        text=(
            "1. Check for the newest completed owner-preview Windows build.\n"
            "2. Open its official GitHub Actions page and download the artifact named "
            "'TGC-CoCompanion-v0.3-OWNER-EXE-NOT-STAFF'.\n"
            "3. Select the ORIGINAL downloaded GitHub artifact ZIP in this page. "
            "Don't extract it first.\n"
            "4. Co-Companion checks the SHA256 against GitHub and confirms the build "
            "commit, then installs alongside the current version.\n"
            "5. Confirm Restart Into Update. Your current version stays untouched "
            "for rollback, and your separate AppData enrollment remains intact."
        )
        ttk.Label(tab,text=text,wraplength=830,justify="left").pack(anchor="w",pady=(5,13))
        ttk.Label(tab,text="Note: A direct automatic download requires GitHub Actions authentication. "
                  "Your GitHub session stays in the browser, not inside Co-Companion. "
                  "No personal access token or hidden owner key is needed.",
                  wraplength=830).pack(anchor="w")
        self.last_installed=tk.StringVar(value="")
        ttk.Label(tab,textvariable=self.last_installed,wraplength=830).pack(anchor="w",pady=(10,4))

    def check(self):
        def done(build):
            self.available=build
            if has_newer_build(self.current,build):
                self.status.set(f"New owner-preview ready: commit {build.short} "
                                f"• GitHub Actions run {build.run_id}. Download the artifact, then install here.")
            else:self.status.set(f"Up to date with the owner-preview build {build.short}.")
        self.status.set("Checking completed owner-preview builds…")
        self.owner._run_async(latest_preview,done)

    def open_download(self):
        if self.available is None:
            self.check()
            return
        webbrowser.open(self.available.run_url)

    def install_zip(self):
        if self.available is None:
            messagebox.showinfo("Check first","Click Check For Updates to verify the official build first.")
            return
        if not has_newer_build(self.current,self.available):
            messagebox.showinfo("Already updated","This build is already installed.")
            return
        file=filedialog.askopenfilename(
            parent=self.root,title="Choose ORIGINAL GitHub Actions artifact ZIP",
            filetypes=[("Owner-preview GitHub artifact ZIP","*.zip")])
        if not file:return
        candidate=self.available
        def work():
            return stage_download(Path(file),candidate,update_root())
        def done(executable):
            self.staged=executable
            self.last_installed.set(f"Verified and staged build {candidate.short}: {executable.parent}")
            if not messagebox.askyesno("Restart Into New Co-Companion",
                    "Update verified. Restart Co-Companion into the NEW version?\n"
                    "Your existing executable, Node key, Relay account, VRChat sessions "
                    "and owner pilot profile will not be overwritten."):
                self.status.set("Update staged; current version is unchanged.")
                return
            self._relaunch(executable)
        self.status.set("Verifying GitHub artifact and staging new version…")
        self.owner._run_async(work,done)

    def _relaunch(self,executable:Path):
        if not executable.is_file():
            raise PreviewUpdateError("The installed update executable is missing")
        # Use no shell. The new EXE lives in an independent folder,
        # so the current EXE remains usable in case rollback is needed.
        try:
            subprocess.Popen([str(executable)],cwd=str(executable.parent),
                             close_fds=True)
        except OSError as exc:
            self.status.set("Update staged but Windows could not start it: "+str(exc))
            return
        self.owner._close()
