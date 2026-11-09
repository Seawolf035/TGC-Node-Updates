"""Per-user Windows display preferences; never touch VRChat or staff state."""
from __future__ import annotations
import json
from pathlib import Path
from .pilot_enrollment import _atomic_bytes

SCALES=(85,100,115,130,150)


class DisplayPreferences:
    def __init__(self,directory):
        self.path=Path(directory)/"co_companion_display.json"

    def load(self):
        try:
            if self.path.stat().st_size>2048:return 100
            data=json.loads(self.path.read_text(encoding="utf-8"))
            value=data.get("scale") if isinstance(data,dict) else None
            return value if type(value) is int and value in SCALES else 100
        except (OSError,ValueError,UnicodeError):
            return 100

    def save(self,percent):
        if type(percent) is not int or percent not in SCALES:
            raise ValueError("Choose an available display scale")
        _atomic_bytes(self.path,json.dumps({"scale":percent}).encode("utf-8"))


def apply_scale(root,percent):
    """Use OS-detected Tk DPI as base, never hardcode pixel density."""
    if percent not in SCALES:
        raise ValueError("Unsupported scale")
    actual=float(root.tk.call("tk","scaling"))
    # Called exactly once before first ttk widget is created.
    if percent!=100:
        root.tk.call("tk","scaling",actual*percent/100)
    return float(root.tk.call("tk","scaling"))
