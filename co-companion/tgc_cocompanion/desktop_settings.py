"""v0.4 local owner UI preferences and safe diagnostics / recovery panel.

No network, no account secrets, no production or staff configuration mutation.
"""
from __future__ import annotations

import os
import sys
from pathlib import Path
import tkinter as tk
from tkinter import messagebox, ttk

from .desktop_navigation import MAX_PINNED
from .owner_updater import bundled_build_info


class DesktopSettings:
    def __init__(self, owner, notebook):
        self.owner = owner
        self.shell = owner.shell
        panel = ttk.Frame(notebook, padding=(20, 16))
        notebook.add(panel, text="Settings")
        ttk.Label(panel, text="DESKTOP PREFERENCES",
                  style="PageTitle.TLabel").pack(anchor="w")
        ttk.Label(panel, text="Personal UI choices only. Your VRChat accounts and staff settings are not changed.",
                  style="PageSubtitle.TLabel").pack(anchor="w", pady=(4, 16))

        nav = ttk.LabelFrame(panel, text="Navigation & Accessibility", padding=(15, 12))
        nav.pack(fill="x", pady=(0, 14))
        self.compact_label = tk.StringVar()
        self._sync_compact()
        ttk.Button(nav, textvariable=self.compact_label,
                   command=self._toggle_compact).pack(anchor="w", pady=3)
        self.home_on_start = tk.BooleanVar(value=self.shell.state.open_at_home)
        ttk.Checkbutton(nav, text="Always open Home when Co-Companion starts",
                        variable=self.home_on_start,
                        command=self._home_toggle).pack(anchor="w", pady=(8, 3))
        ttk.Button(nav, text="Reset Pinned Pages & Navigation History",
                   command=self._reset_navigation).pack(anchor="w", pady=5)
        ttk.Label(nav, text=(
            "Ctrl+K: Find any page    •    Ctrl+F: Filter navigation    •    "
            "Ctrl+1–9: quick page shortcuts    •    Alt+Left: previous page"
        ), wraplength=800).pack(anchor="w", pady=(8, 0))

        from .desktop_display import SCALES
        display=ttk.LabelFrame(panel,text="Display & Screen Scaling",
                               padding=(15,10))
        display.pack(fill="x",pady=(0,12))
        ttk.Label(display,text="Designed for 720p through 4K. Narrow windows automatically "
                  "collapse the navigation; change scale for smaller or larger text.",
                  wraplength=780).pack(anchor="w")
        row=ttk.Frame(display)
        row.pack(fill="x",pady=(8,2))
        ttk.Label(row,text="UI scale:").pack(side="left",padx=(0,8))
        self.display_scale=tk.StringVar(value=str(owner.display_choice.load())+"%")
        ttk.Combobox(row,textvariable=self.display_scale,state="readonly",
                     width=9,values=[str(x)+"%" for x in SCALES]
                     ).pack(side="left",padx=(0,9))
        ttk.Button(row,text="Save Scale (Restart Required)",
                   command=self._save_scale).pack(side="left")
        ttk.Label(display,text="100% uses the scale Windows recommends for your monitor. "
                  "Only UI size changes; account settings are unaffected.",
                  style="PageSubtitle.TLabel",wraplength=780).pack(anchor="w",pady=(5,0))

        from .desktop_staff_colors import PALETTES
        theme_box=ttk.LabelFrame(panel,text="Desktop UI Colors • Mobile Companion presets",
                                padding=(15,10))
        theme_box.pack(fill="x",pady=(0,12))
        ttk.Label(theme_box,text="Choose a built-in theme (appearance only, never staff permissions).",
                  wraplength=800).pack(anchor="w")
        colors=ttk.Frame(theme_box)
        colors.pack(fill="x",pady=(7,0))
        self.theme_name=tk.StringVar(value=owner.theme_choice.load()
                                   if hasattr(owner,"theme_choice") else "TGC Default")
        ttk.Combobox(colors,textvariable=self.theme_name,
                     values=list(PALETTES),state="readonly",width=27).pack(
                         side="left",padx=(0,10))
        ttk.Button(colors,text="Apply UI Colors",command=self._apply_theme
                   ).pack(side="left",padx=(0,10))
        ttk.Button(colors,text="Open 10-Channel Color Studio",
                   command=lambda:self.shell.open_page("UI Color Studio")
                   ).pack(side="left")
        ttk.Label(theme_box,text=(
            "Custom ten-channel editing and VRChat palette import require "
            "an authenticated Admin+ staff rank; built-in themes "
            "are cosmetic and do not grant roles."
        ),wraplength=790).pack(anchor="w",pady=(7,0))
        build = bundled_build_info()
        info = ttk.LabelFrame(panel, text="Installed Co-Companion Preview", padding=(15, 12))
        info.pack(fill="x", pady=(0, 14))
        ttk.Label(info, text=(
            "Version: " + str(build.get("version") or "legacy") +
            "     •     Commit: " + str(build.get("sha") or "not pinned")[:12] +
            "     •     Channel: owner-preview"
        ), wraplength=800).pack(anchor="w")
        bar = ttk.Frame(info)
        bar.pack(anchor="w", pady=(9, 0))
        ttk.Button(bar, text="Check for Updates",
                   command=lambda: self.shell.open_page("Updates")
                   ).pack(side="left", padx=(0, 9))
        ttk.Button(bar, text="Open Installed Versions Folder",
                   command=self._open_versions).pack(side="left")

        tools = ttk.LabelFrame(panel, text="Troubleshooting", padding=(15, 12))
        tools.pack(fill="x")
        ttk.Button(tools, text="Copy Safe Diagnostics",
                   command=lambda: owner.features._copy_safe_diagnostics()
                   ).pack(anchor="w")
        ttk.Button(tools, text="Create / Repair Start Menu Shortcut",
                   command=self._repair_shortcut).pack(anchor="w", pady=(8, 0))
        ttk.Label(tools, text=(
            "Only the update folder is opened; credentials, encrypted VRChat sessions, "
            "Node keys and databases are never copied, reset or deleted by Settings. "
            "To roll back, launch an earlier verified EXE (or legacy Python folder)."
        ), wraplength=800).pack(anchor="w", pady=(8, 0))

    def _save_scale(self):
        try:
            selected=int(self.display_scale.get().rstrip("%"))
            self.owner.display_choice.save(selected)
            messagebox.showinfo("Display Scale Saved",
                "The UI will use "+str(selected)+"% scale the next time "
                "Co-Companion opens. Restart to apply this setting.",
                parent=self.owner.root)
        except (ValueError,OSError) as exc:
            messagebox.showerror("Display Scale",str(exc),parent=self.owner.root)

    def _apply_theme(self):
        try:
            from .desktop_staff_colors import apply_desktop_palette
            name=self.theme_name.get()
            self.owner.theme_choice.save(name)
            # Selecting a public built-in theme also clears the active
            # custom theme for this account (no permission escalation).
            identity=self.owner.enrollment.discord_id
            if identity:
                self.owner.color_studio.store.activate(identity,None)
            apply_desktop_palette(self.owner.root,name)
            if hasattr(self.owner,"features") and hasattr(self.owner.features,"official_chatboxes"):
                self.owner.features.official_chatboxes.render()
        except Exception as exc:
            messagebox.showerror("UI Colors",str(exc),parent=self.owner.root)

    def _sync_compact(self):
        self.compact_label.set("Expand sidebar" if self.shell.state.compact
                               else "Use compact sidebar")

    def _toggle_compact(self):
        self.shell.toggle_compact()
        self._sync_compact()

    def _home_toggle(self):
        self.shell.state.open_at_home = bool(self.home_on_start.get())
        self.shell.state.save()

    def _reset_navigation(self):
        if not messagebox.askyesno("Reset navigation preferences",
                "Reset sidebar layout, pinned pages and recent navigation?\n"
                "This will not affect any VRChat account, Node or TGC configuration.",
                parent=self.owner.root):
            return
        state = self.shell.state
        state.pinned = ["Home", "Social", "VRChat Launcher", "Updates"]
        state.recent = []
        state.compact = False
        state.open_at_home = False
        state.last_page = "Home"
        state.restrict(self.shell.page_names)
        state.save()
        self.home_on_start.set(False)
        self.shell._apply_compact()
        self._sync_compact()
        self.shell.open_page("Home")

    def _repair_shortcut(self):
        if os.name != "nt" or not getattr(sys, "frozen", False):
            messagebox.showinfo("Shortcut", "This option is available in the installed EXE.",
                                parent=self.owner.root)
            return
        try:
            from .exe_release_updater import update_start_menu_shortcut
            path = update_start_menu_shortcut(Path(sys.executable))
            messagebox.showinfo("Shortcut", "Start Menu shortcut is ready:\\n" + str(path),
                                parent=self.owner.root)
        except Exception as exc:
            messagebox.showerror("Shortcut", str(exc), parent=self.owner.root)

    def _open_versions(self):
        from .owner_updater import update_root
        path = update_root()
        path.mkdir(parents=True, exist_ok=True)
        if os.name != "nt":
            messagebox.showinfo("Installed Versions", str(path),
                                parent=self.owner.root)
            return
        try:
            os.startfile(str(path))
        except OSError as exc:
            messagebox.showerror("Open installed versions", str(exc),
                                 parent=self.owner.root)
