"""Local-only navigation preferences and safe page search for Co-Companion.

Stores ONLY UI choices (never VRChat credentials, session IDs or tokens).
"""
from __future__ import annotations
import json
import os
from pathlib import Path
import tempfile

MAX_RECENT = 6
MAX_PINNED = 8
MAX_BYTES = 8192


def search_pages(names, query):
    """Case-insensitive token matching, preserving original page order."""
    tokens = tuple(query.casefold().strip().split())[:8]
    return [name for name in names
            if all(token in name.casefold() for token in tokens)]


class NavigationState:
    def __init__(self, directory: Path):
        self.path = Path(directory) / "co_companion_navigation.json"
        self.pinned = ["Home", "Social", "VRChat Launcher", "Updates"]
        self.recent = []
        self.last_page = "Home"
        self.compact = False
        self.open_at_home = False
        self.load()

    def load(self):
        try:
            if self.path.stat().st_size > MAX_BYTES:
                return
            data = json.loads(self.path.read_text(encoding="utf-8"))
            if not isinstance(data, dict):
                return
            for field, size in (("pinned", MAX_PINNED), ("recent", MAX_RECENT)):
                value = data.get(field)
                if isinstance(value, list):
                    cleaned = []
                    for entry in value:
                        if (isinstance(entry, str) and 1 <= len(entry) <= 80
                                and entry not in cleaned):
                            cleaned.append(entry)
                    setattr(self, field, cleaned[:size])
            if isinstance(data.get("last_page"), str) and len(data["last_page"]) < 81:
                self.last_page = data["last_page"]
            if type(data.get("compact")) is bool:
                self.compact = data["compact"]
            if type(data.get("open_at_home")) is bool:
                self.open_at_home = data["open_at_home"]
        except (OSError, ValueError, UnicodeError):
            pass

    def restrict(self, valid_names):
        allowed = set(valid_names)
        self.pinned = [name for name in self.pinned if name in allowed][:MAX_PINNED]
        self.recent = [name for name in self.recent if name in allowed][:MAX_RECENT]
        if self.last_page not in allowed:
            self.last_page = "Home" if "Home" in allowed else (next(iter(valid_names), ""))

    def visit(self, name):
        if name != self.last_page or not self.recent:
            self.recent = [name] + [x for x in self.recent if x != name]
            self.recent = self.recent[:MAX_RECENT]
        self.last_page = name
        self.save()

    def toggle_pin(self, name):
        if name in self.pinned:
            self.pinned.remove(name)
        else:
            if len(self.pinned) >= MAX_PINNED:
                raise ValueError("Maximum eight pinned pages")
            self.pinned.append(name)
        self.save()

    def save(self):
        """Atomic replace; UI state is optional, so failure must never stop app."""
        data = {"pinned": self.pinned, "recent": self.recent,
                "last_page": self.last_page, "compact": self.compact,
                "open_at_home": self.open_at_home}
        temp_name = None
        try:
            self.path.parent.mkdir(parents=True, exist_ok=True)
            with tempfile.NamedTemporaryFile(mode="w", encoding="utf-8",
                    prefix=".nav-", suffix=".tmp", dir=self.path.parent,
                    delete=False) as file:
                temp_name = file.name
                json.dump(data, file, ensure_ascii=False)
            os.replace(temp_name, self.path)
        except OSError:
            if temp_name:
                try: os.unlink(temp_name)
                except OSError: pass
