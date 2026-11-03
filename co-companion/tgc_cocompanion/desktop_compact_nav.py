"""Compact sidebar: pin-focused icon rail with accessible hover labels."""
from __future__ import annotations
import tkinter as tk
from tkinter import ttk
from .desktop_theme import RAISED, TEXT

ICONS = {
    "Home":"⌂", "Verification":"✓", "VRChat Launcher":"▶",
    "Tracking Diagnostics":"◉", "VRChat Account":"◎",
    "Social":"◇", "Profiles":"◈", "Presence":"●",
    "Avatars":"✦", "Bookmarks":"☆", "Inbox":"✉",
    "StaffDex":"★", "OSC":"≋", "OSC Studio":"⌘",
    "Updates":"↻", "Settings":"⚙",
}


def compact_pages(pinned, current, valid):
    allowed = set(valid)
    found = []
    for name in list(pinned) + [current]:
        if name in allowed and name not in found:
            found.append(name)
    return found


class HoverHint:
    def __init__(self, widget, label):
        self.widget, self.label = widget, label
        self.after_id, self.popup = None, None
        widget.bind("<Enter>", self.enter, add="+")
        widget.bind("<Leave>", self.leave, add="+")
        widget.bind("<Destroy>", self.leave, add="+")

    def enter(self, event=None):
        self.leave()
        self.after_id = self.widget.after(400, self.show)

    def show(self):
        self.after_id = None
        try:
            self.popup = tk.Toplevel(self.widget)
            self.popup.overrideredirect(True)
            self.popup.wm_attributes("-topmost", True)
            ttk.Label(self.popup, text=self.label, background=RAISED,
                      foreground=TEXT, padding=(11, 7)).pack()
            self.popup.geometry("+%d+%d" % (
                self.widget.winfo_rootx()+self.widget.winfo_width()+8,
                self.widget.winfo_rooty()))
        except tk.TclError:
            self.popup = None

    def leave(self, event=None):
        if self.after_id:
            try: self.widget.after_cancel(self.after_id)
            except tk.TclError: pass
            self.after_id = None
        if self.popup:
            try: self.popup.destroy()
            except tk.TclError: pass
            self.popup = None


def build_rail(parent, pinned, current, valid, navigate, find_all):
    ttk.Label(parent, text="PINS", style="SideHint.TLabel",
              anchor="center").pack(fill="x", pady=(12, 6))
    for name in compact_pages(pinned, current, valid):
        btn = ttk.Button(parent, text=ICONS.get(name, "•"), width=3,
              style="RailActive.TButton" if name == current else "Rail.TButton",
              command=lambda page=name: navigate(page))
        btn.pack(pady=3, padx=8)
        HoverHint(btn, name)
    ttk.Separator(parent).pack(fill="x", padx=11, pady=(12, 8))
    btn = ttk.Button(parent, text="⌕", width=3, style="Rail.TButton",
                     command=find_all)
    btn.pack(pady=2, padx=8)
    HoverHint(btn, "Find any page (Ctrl+K)")
