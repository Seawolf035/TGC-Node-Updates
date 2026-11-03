"""Official TGC rank colors and non-privileged desktop theme presets.

Source: official TGC roster color palette and Mobile ThemePreferences.
These values are DISPLAY ONLY; never authorize staff or official OSC labels.
"""
from __future__ import annotations
import json
from pathlib import Path
import re

from .pilot_enrollment import _atomic_bytes

RANK_COLORS = {
    "The Royalty": "#D4AF37", "Owner": "#E40303",
    "Head Admin": "#FF8C00", "Admin": "#FFED00",
    "Head Moderator": "#008026", "Moderator": "#004CFF",
    "Trial Moderator": "#732982",
}
# Mobile Companion names (excluding its exclusively delegated private theme).
PALETTES = {
    "TGC Default": ("#090F1D","#142338","#1C3048","#62D6F2","#F1F8FF","#B8CADE"),
    "Deep Ocean": ("#080D1F","#141936","#20294B","#8B9CFF","#F1F8FF","#B8CADE"),
    "Rose Quartz": ("#170E20","#291A36","#342345","#FF8CC3","#F1F8FF","#B8CADE"),
    "Emerald": ("#080F0D","#152B25","#1E3D32","#68E4B0","#F1F8FF","#B8CADE"),
    "Royal Gold": ("#151110","#2A211B","#3B2B20","#E7B75D","#F1F8FF","#B8CADE"),
    "Midnight Violet": ("#100F26","#252143","#312858","#C2A3FF","#F1F8FF","#B8CADE"),
    "Crimson": ("#1C1017","#301C29","#452433","#FF778D","#F1F8FF","#B8CADE"),
    "Sunset Ember": ("#1B1211","#30211C","#463025","#FFB878","#F1F8FF","#B8CADE"),
    "Arctic Ice": ("#081B28","#142D3C","#1F3E4E","#8CE2FF","#F1F8FF","#B8CADE"),
    "Graphite": ("#131719","#222A2D","#303B40","#BDD3DC","#F1F8FF","#B8CADE"),
    "Sakura": ("#180F1E","#322139","#462E50","#FFACDA","#F1F8FF","#B8CADE"),
    "Lime Grove": ("#101B16","#22352B","#2C4938","#E1F783","#F1F8FF","#B8CADE"),
}
HEX = re.compile(r"#[0-9A-Fa-f]{6}\Z")


def palette_for(name):
    if name not in PALETTES:
        raise ValueError("Select a supported desktop theme")
    return PALETTES[name]


class DesktopThemeChoice:
    def __init__(self, directory: Path):
        self.path = Path(directory) / "co_companion_theme.json"

    def load(self) -> str:
        try:
            if self.path.stat().st_size > 4096:
                return "TGC Default"
            data = json.loads(self.path.read_text(encoding="utf-8"))
            name = data.get("theme") if isinstance(data, dict) else None
            return name if name in PALETTES else "TGC Default"
        except (OSError, ValueError, UnicodeError):
            return "TGC Default"

    def save(self, name: str):
        palette_for(name)
        _atomic_bytes(self.path, json.dumps({"theme":name}).encode("utf-8"))


def apply_desktop_palette(root, name):
    """Repaint Tk+ttk safely; UI-only and leaves all auth/OSC state untouched."""
    import tkinter as tk
    from tkinter import ttk
    # Built-in palettes retain their historical six-color schema; custom
    # Mobile-matched palettes supply all ten independent channels.
    if isinstance(name, str):
        bg,surface,raised,accent,fg,sub = palette_for(name)
        field,border,icon,button=surface,raised,accent,accent
    else:
        from .desktop_color_studio import validate_palette
        (bg,surface,raised,field,accent,border,icon,button,fg,sub) = validate_palette(name)
    styles=ttk.Style(root)
    for key,color in {
        ".":bg, "TFrame":bg,"Shell.TFrame":bg,
        "Header.TFrame":surface,"Footer.TFrame":surface,
        "Sidebar.TFrame":surface,"Card.TFrame":surface,
    }.items():
        styles.configure(key,background=color)
    styles.configure("TLabel",background=bg,foreground=fg)
    for key in ("PageTitle.TLabel",):
        styles.configure(key,background=bg,foreground=fg)
    styles.configure("PageSubtitle.TLabel",background=bg,foreground=sub)
    for key in ("Brand.TLabel",):
        styles.configure(key,background=surface,foreground=accent)
    for key in ("BrandHint.TLabel","SideHint.TLabel","FooterHint.TLabel"):
        styles.configure(key,background=surface,foreground=sub)
    styles.configure("FooterStatus.TLabel",background=surface,foreground=fg)
    styles.configure("CardLabel.TLabel",background=surface,foreground=sub)
    styles.configure("CardValue.TLabel",background=surface,foreground=fg)
    styles.configure("CardAccent.TLabel",background=surface,foreground=accent)
    styles.configure("TButton",background=button,foreground=bg)
    styles.configure("Accent.TButton",background=button,foreground=bg)
    styles.configure("Nav.TButton",background=surface,foreground=sub)
    styles.configure("NavSelected.TButton",background=raised,foreground=accent)
    styles.configure("Rail.TButton",background=surface,foreground=sub)
    styles.configure("RailActive.TButton",background=raised,foreground=accent)
    styles.configure("Treeview",background=surface,fieldbackground=surface,foreground=fg)
    styles.configure("Treeview.Heading",background=raised,foreground=fg)
    styles.configure("TNotebook.Tab",background=surface,foreground=sub)
    styles.configure("TLabelframe.Label",foreground=accent,background=bg)
    styles.configure("Treeview.Heading",background=raised,foreground=fg)
    styles.configure("Vertical.TScrollbar",background=raised,troughcolor=surface,
                     arrowcolor=icon)
    styles.configure("TEntry",fieldbackground=field,foreground=fg,insertcolor=fg)
    styles.configure("TCombobox",fieldbackground=field,foreground=fg)
    styles.configure("TLabelframe",background=bg,foreground=fg,bordercolor=border)
    styles.configure("TSeparator",background=border)
    styles.map("TNotebook.Tab",background=[("selected",raised),("active",raised)],
               foreground=[("selected",accent)])
    styles.map("Nav.TButton",background=[("active",raised)])
    styles.map("TCombobox",fieldbackground=[("readonly",field)],
               foreground=[("readonly",fg)])
    root.configure(background=bg)
    def repaint(widget):
        try:
            if isinstance(widget, tk.Canvas):
                widget.configure(background=surface)
            elif isinstance(widget, tk.Listbox):
                widget.configure(background=surface,foreground=fg,
                                 selectbackground=raised,selectforeground=accent)
            elif isinstance(widget, tk.Text):
                widget.configure(background=surface,foreground=fg,
                                 insertbackground=accent)
            for child in widget.winfo_children():
                repaint(child)
        except tk.TclError:
            pass
    repaint(root)
