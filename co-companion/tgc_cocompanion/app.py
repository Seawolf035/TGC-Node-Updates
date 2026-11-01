"""Non-operational Co-Companion Windows interface scaffold.

There is no login, backend entitlement adapter, log watcher, VRChat launcher,
OSC transmitter or pairing endpoint wired into this preview. Every action is
disabled until implemented, tested, and authorized. Not a staff release.
"""
from __future__ import annotations

import tkinter as tk
from tkinter import ttk

from . import __version__


TABS = (
    "Home", "Social", "OSC", "Tracking", "VRChat Launcher",
    "StaffDex", "Devices", "Settings",
)
DESCRIPTIONS = {
    "Home": "Connection status, verified account and quick controls",
    "Social": "Friends, profiles, groups, worlds, inbox and bookmarks",
    "OSC": "Chatbox, rotating messages, avatar parameters, height and microphone",
    "Tracking": "Local PC VRChat logs, Node health and Invite Network status",
    "VRChat Launcher": "One default VRChat slot; extra slots require owner approval (up to 4)",
    "StaffDex": "Existing achievements and unlock policies shared across Companion",
    "Devices": "Secure future pairing with Quest or Android Companion",
    "Settings": "Appearance, account, permissions, updates, terms and diagnostics",
}


def launch_preview() -> None:
    root = tk.Tk()
    root.title(f"TGC Co-Companion — Windows Foundation {__version__} (NOT RELEASED)")
    root.geometry("1050x640")
    root.minsize(800, 560)

    top = ttk.Frame(root, padding=(16, 14))
    top.pack(fill="x")
    ttk.Label(
        top, text="TGC Co-Companion", font=("Segoe UI", 21, "bold")
    ).pack(anchor="w")
    ttk.Label(
        top,
        text="DEVELOPMENT PREVIEW ONLY — no staff authorization or live connections",
        foreground="#ad5320"
    ).pack(anchor="w", pady=(3, 0))

    pages = ttk.Notebook(root)
    pages.pack(fill="both", expand=True, padx=12, pady=8)

    for label in TABS:
        page = ttk.Frame(pages, padding=(22, 20))
        pages.add(page, text=label)
        ttk.Label(
            page, text=label, font=("Segoe UI", 18, "bold")
        ).pack(anchor="w")
        ttk.Label(page, text=DESCRIPTIONS[label], wraplength=650).pack(
            anchor="w", pady=(12, 12)
        )
        status = (
            "Access: not verified. Features are disabled."
            if label != "VRChat Launcher"
            else "Authorized: 0/4 slots (no backend adapter). All launch buttons disabled."
        )
        ttk.Label(page, text=status).pack(anchor="w", pady=(6, 18))
        ttk.Button(page, text="Not available in foundation preview", state="disabled").pack(
            anchor="w"
        )

    footer = ttk.Frame(root, padding=(16, 10))
    footer.pack(fill="x")
    ttk.Label(
        footer,
        text="Plugin Node, Android Companion, published Node and Staff Launcher are unchanged.",
    ).pack(anchor="w")

    root.mainloop()


if __name__ == "__main__":
    launch_preview()
