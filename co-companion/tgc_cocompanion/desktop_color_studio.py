"""Admin+ local ten-channel desktop theme editor with VRChat color-code import.

Exact 10 channel names from Android ThemePreferences, plus its six-color
VRChat wire order. Rank permission never comes from local settings or the
public roster; only an active authenticated staff lease may enable writing.
"""
from __future__ import annotations

import json
from pathlib import Path
import re
import tkinter as tk
from tkinter import ttk, messagebox

from .pilot_enrollment import _atomic_bytes

CHANNELS = (
    "Background", "Card surface", "Raised panels", "Input fields",
    "Highlight / accent", "Borders", "Icons", "Buttons",
    "Fonts / primary text", "Subtext / secondary text",
)
BG, SURFACE, RAISED, FIELD, ACCENT, BORDER, ICON, BUTTON, TEXT, SUBTEXT = range(10)
VRCHAT_CHANNELS = (ACCENT, ICON, BUTTON, BG, TEXT, SUBTEXT)
HEX = re.compile(r"#[0-9A-Fa-f]{6}\Z")
DISCORD = re.compile(r"[0-9]{15,22}\Z")
NAME = re.compile(r"[^\x00-\x1f\x7f]{1,40}\Z")
MAX_PRESETS = 25


def normalize_hex(value):
    if not isinstance(value, str):
        raise ValueError("Colors must be six-digit hexadecimal strings")
    value = value.strip()
    if value.startswith("#"):
        value = value[1:]
    if not re.fullmatch(r"[0-9A-Fa-f]{6}", value):
        raise ValueError("Use a six-digit hex color, such as #295094")
    return "#" + value.upper()


def validate_palette(colors):
    if not isinstance(colors, (list, tuple)) or len(colors) != len(CHANNELS):
        raise ValueError("Exactly ten UI colors are required")
    return tuple(normalize_hex(c) for c in colors)


def blend(first, second, strength):
    a = bytes.fromhex(normalize_hex(first)[1:])
    b = bytes.fromhex(normalize_hex(second)[1:])
    values = [round(a[i] * (1 - strength) + b[i] * strength) for i in range(3)]
    return "#" + "".join(f"{n:02X}" for n in values)


def import_vrchat_colors(text):
    if not isinstance(text, str) or len(text) > 256:
        raise ValueError("VRChat palette must be six comma-separated colors")
    parts = re.split(r"[,;\r\n]+", text.strip())
    if len(parts) != 6:
        raise ValueError(
            "Paste exactly six VRChat colors: Highlight, Icons, Buttons, "
            "Background, Fonts, Subtext")
    color = [normalize_hex(x) for x in parts]
    highlight, icon, button, bg, fonts, subtext = color
    # Match Mobile ThemePreferences.importVrchatPalette surface derivation.
    return validate_palette((
        bg, blend(bg, icon, 0.14), blend(bg, button, 0.27),
        blend(bg, icon, 0.19), highlight, blend(bg, highlight, 0.38),
        icon, button, fonts, subtext
    ))


def export_vrchat_colors(colors):
    colors = validate_palette(colors)
    return ",".join(colors[i] for i in VRCHAT_CHANNELS)


def built_in_ten_colors(name):
    from .desktop_staff_colors import palette_for
    bg,surface,raised,accent,fg,sub=palette_for(name)
    return validate_palette((bg,surface,raised,blend(bg,surface,.65),
                            accent,blend(bg,accent,.38),accent,accent,fg,sub))


class LocalPalettePresets:
    """An account-scoped, *non-authoritative* UI preference store.

    Access checks belong to the caller, which must check the live Relay rank
    for save/load/activate/delete. Data never grants the authority to edit.
    """
    def __init__(self, directory):
        self.path = Path(directory) / "co_companion_custom_palettes.json"

    def _read(self):
        try:
            if self.path.stat().st_size > 100_000:
                raise ValueError("Stored color preferences are oversized")
            raw=json.loads(self.path.read_text(encoding="utf-8"))
            if not isinstance(raw,dict) or raw.get("schema") != 1:
                return {}
            data=raw.get("accounts")
            return data if isinstance(data,dict) and len(data)<=100 else {}
        except (OSError, ValueError, UnicodeError):
            return {}

    def _write(self, data):
        _atomic_bytes(self.path,json.dumps(
            {"schema":1,"accounts":data},separators=(",",":")).encode("utf-8"))

    def _user(self, discord_id):
        if not isinstance(discord_id,str) or not DISCORD.fullmatch(discord_id):
            raise PermissionError("A verified TGC Discord account is required")
        return discord_id

    def _presets(self, discord_id):
        data=self._read()
        item=data.get(self._user(discord_id),{})
        return item.get("presets",{}) if isinstance(item,dict) and isinstance(
            item.get("presets"),dict) else {}

    def list(self, discord_id):
        return tuple(sorted(name for name in self._presets(discord_id)
                            if NAME.fullmatch(name)))

    def load(self, discord_id, name):
        if name not in self._presets(discord_id):
            raise KeyError("Custom color preset was not found")
        return validate_palette(self._presets(discord_id)[name])

    def save(self, discord_id, name, colors, *, overwrite=False):
        user=self._user(discord_id)
        if not isinstance(name,str) or not NAME.fullmatch(name.strip()):
            raise ValueError("Choose a printable preset name (1–40 characters)")
        name=name.strip()
        palette=validate_palette(colors)
        data=self._read()
        entry=data.setdefault(user,{"presets":{},"active":None})
        presets=entry.setdefault("presets",{})
        matches=[n for n in presets if n.casefold()==name.casefold()]
        if matches:
            if not overwrite:
                raise ValueError("A preset with that name already exists")
            name=matches[0]
        elif len(presets)>=MAX_PRESETS:
            raise ValueError("Maximum 25 saved UI palettes reached")
        presets[name]=list(palette)
        self._write(data)
        return name

    def delete(self, discord_id, name):
        user=self._user(discord_id)
        data=self._read()
        entry=data.get(user,{})
        if name not in entry.get("presets",{}):
            raise KeyError("Saved palette was not found")
        del entry["presets"][name]
        if entry.get("active")==name:
            entry["active"]=None
        self._write(data)

    def activate(self, discord_id, name=None):
        user=self._user(discord_id)
        data=self._read()
        entry=data.setdefault(user,{"presets":{},"active":None})
        if name is not None and name not in entry.get("presets",{}):
            raise KeyError("Choose an existing saved palette")
        entry["active"]=name
        self._write(data)

    def active(self, discord_id):
        user=self._user(discord_id)
        data=self._read()
        entry=data.get(user,{})
        name=entry.get("active") if isinstance(entry,dict) else None
        try:
            return (name,self.load(user,name)) if name else (None,None)
        except (ValueError,KeyError,TypeError):
            return (None,None)


class DesktopColorStudio:
    def __init__(self,owner,notebook):
        self.owner=owner
        self.store=LocalPalettePresets(owner.terms_dir)
        self.notice=tk.StringVar(value="Built-in themes are in Settings. "
              "Custom colors require a current authenticated Admin+ staff rank.")
        self.selected=tk.StringVar()
        self.entries=[tk.StringVar() for _ in CHANNELS]
        panel=ttk.Frame(notebook,padding=(16,12))
        notebook.add(panel,text="UI Color Studio")
        ttk.Label(panel,text="UI COLOR STUDIO",
                  style="PageTitle.TLabel").pack(anchor="w")
        ttk.Label(panel,text="Ten-channel custom editor + six-color VRChat import/export. "
                  "Your palettes stay on this PC, separate for each verified Discord account.",
                  wraplength=830).pack(anchor="w",pady=(4,9))
        ttk.Label(panel,textvariable=self.notice,wraplength=820).pack(anchor="w")
        grid=ttk.Frame(panel)
        grid.pack(fill="x",pady=(10,8))
        for i,name in enumerate(CHANNELS):
            col=i//5
            row=i%5
            cell=ttk.Frame(grid)
            cell.grid(row=row,column=col,sticky="ew",padx=(0,20),pady=3)
            ttk.Label(cell,text=name,width=23).pack(side="left")
            ttk.Entry(cell,textvariable=self.entries[i],width=12).pack(side="left")
        grid.grid_columnconfigure(0,weight=1)
        grid.grid_columnconfigure(1,weight=1)
        bar=ttk.Frame(panel)
        bar.pack(fill="x",pady=(5,6))
        ttk.Label(bar,text="Preset name:").pack(side="left")
        ttk.Entry(bar,textvariable=self.selected,width=26).pack(side="left",padx=(6,8))
        ttk.Button(bar,text="Refresh Names",command=self.refresh).pack(
            side="left",padx=(0,5))
        ttk.Button(bar,text="Load",command=self.load).pack(
            side="left",padx=(0,5))
        ttk.Button(bar,text="Save / Replace",command=self.save).pack(
            side="left",padx=(0,5))
        ttk.Button(bar,text="Delete",command=self.delete).pack(side="left")
        self.picker=ttk.Combobox(panel,textvariable=self.selected,values=[],
                                state="readonly",width=33)
        self.picker.pack(anchor="w",pady=(1,10))
        commands=ttk.Frame(panel)
        commands.pack(fill="x")
        ttk.Button(commands,text="Apply This Palette",style="Accent.TButton",
                   command=self.apply).pack(side="left",padx=(0,8))
        ttk.Button(commands,text="Import VRChat 6 Colors",
                   command=self.import_clipboard).pack(side="left",padx=(0,8))
        ttk.Button(commands,text="Copy VRChat 6 Colors",
                   command=self.copy_vrchat).pack(side="left",padx=(0,8))
        ttk.Button(commands,text="Restore Built-in Theme",
                   command=self.restore).pack(side="left")
        ttk.Label(panel,text="VRChat order: Highlight, Icons, Buttons, Background, Fonts, Subtext. "
                  "Import replaces the editor only; click Save / Replace and Apply to persist. "
                  "No changes to OSC, VRChat accounts or staff permissions.",
                  wraplength=820).pack(anchor="w",pady=(12,0))
        self.set_colors(built_in_ten_colors(owner.theme_choice.load()))

    def _access(self):
        from .desktop_official_chatboxes import RANK_NAMES
        lease=self.owner.lease.current() if self.owner.lease else None
        if not (self.owner._terms_valid() and lease and lease.valid_limit(
                __import__("time").time()) and self.owner.enrollment.discord_id==
                lease.discord_id):
            raise PermissionError("Verify your TGC owner pilot staff access first")
        rank=self.owner.lease.rank()
        if RANK_NAMES.get(rank.lower(),0)<4:
            raise PermissionError("Custom UI colors require a verified Admin+ rank "
                                  "from the TGC Relay; your Relay has not granted it")
        return lease.discord_id

    def set_colors(self,colors):
        for field,value in zip(self.entries,validate_palette(colors)):
            field.set(value)

    def colors(self):
        return validate_palette([x.get() for x in self.entries])

    def _do(self,action):
        try:
            return action(self._access())
        except Exception as exc:
            self.notice.set(str(exc))
            return None

    def refresh(self):
        def action(user):
            names=self.store.list(user)
            self.picker.configure(values=names)
            self.notice.set(f"{len(names)}/{MAX_PRESETS} locally saved custom palettes.")
        self._do(action)

    def save(self):
        def action(user):
            name=self.selected.get().strip()
            exists=any(x.casefold()==name.casefold() for x in self.store.list(user))
            if exists and not messagebox.askyesno("Replace saved UI colors",
                    f"Overwrite '{name}' for this staff account?",
                    parent=self.owner.root):
                return
            self.store.save(user,name,self.colors(),overwrite=exists)
            self.refresh()
            self.notice.set("Saved locally. Click Apply to activate.")
        self._do(action)

    def load(self):
        def action(user):
            name=self.selected.get()
            self.set_colors(self.store.load(user,name))
            self.notice.set("Loaded '"+name+"'. Apply to change the desktop UI.")
        self._do(action)

    def apply(self):
        def action(user):
            from .desktop_staff_colors import apply_desktop_palette
            colors=self.colors()
            name=self.selected.get()
            if name not in self.store.list(user):
                raise ValueError("Save your current palette before applying")
            if self.store.load(user,name)!=colors:
                raise ValueError("Unsaved color edits: Save / Replace before Apply")
            apply_desktop_palette(self.owner.root,colors)
            self.store.activate(user,name)
            self.notice.set("Custom desktop colors active. No staff permissions changed.")
        self._do(action)

    def delete(self):
        def action(user):
            self.store.delete(user,self.selected.get())
            self.refresh()
            self.notice.set("Saved color preset deleted; current UI unchanged.")
        self._do(action)

    def import_clipboard(self):
        def action(user):
            raw=self.owner.root.clipboard_get()
            self.set_colors(import_vrchat_colors(raw))
            self.notice.set("Imported six VRChat colors into the 10-channel editor; "
                            "save a preset before applying.")
        self._do(action)

    def copy_vrchat(self):
        def action(user):
            data=export_vrchat_colors(self.colors())
            self.owner.root.clipboard_clear()
            self.owner.root.clipboard_append(data)
            self.notice.set("Copied your six VRChat UI colors in VRChat's order.")
        self._do(action)

    def restore(self):
        def action(user):
            from .desktop_staff_colors import apply_desktop_palette
            self.store.activate(user,None)
            name=self.owner.theme_choice.load()
            self.set_colors(built_in_ten_colors(name))
            apply_desktop_palette(self.owner.root,name)
            self.notice.set("Restored built-in theme "+name+".")
        self._do(action)

    def after_staff_verification(self):
        """Run only after the existing authenticated Relay lease succeeds."""
        try:
            user=self._access()
            name, colors=self.store.active(user)
            if colors:
                from .desktop_staff_colors import apply_desktop_palette
                self.selected.set(name)
                self.set_colors(colors)
                apply_desktop_palette(self.owner.root,colors)
            self.refresh()
        except PermissionError:
            pass
