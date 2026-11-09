"""Opt-in, read-only local PCVR Recent Players: no persistent player history.

Only reads bounded tails of output_log_*.txt when the owner clicks Scan.
Never calls Relay, never uploads sightings, never starts a background watcher.
This is a personal VRCX-like viewer, NOT the future staff Invite Node.
"""
from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime, timedelta
from pathlib import Path
import os
import re
import tkinter as tk
from tkinter import ttk, messagebox
import webbrowser

from .legacy.tgc_vrchat_multilog import MultiVRChatLogWatcher, JOIN_RE, TIMESTAMP_RE
from .vrchat_personal import USER_ID

MAX_LOGS = 8
MAX_TAIL_BYTES = 2_000_000
MAX_PEOPLE = 500
MAX_AGE_DAYS = 14
MAX_LINE = 4096
_LOG_NAME = re.compile(r"output_log_[^/\\]{1,160}[.]txt\Z")


@dataclass(frozen=True)
class RecentPlayer:
    user_id: str
    display_name: str
    seen_at: str
    source: str


def _log_line(line: str, source: str, fallback: str) -> RecentPlayer | None:
    if len(line) > MAX_LINE or "OnPlayerJoined" not in line:
        return None
    match = JOIN_RE.search(line)
    if not match:
        return None
    name = match.group(1).strip()
    uid = match.group(2)
    if not USER_ID.fullmatch(uid) or not 1 <= len(name) <= 96:
        return None
    stamp = TIMESTAMP_RE.search(line)
    seen = stamp.group(1) if stamp else fallback
    return RecentPlayer(uid, name, seen, source)


def scan_recent_players(log_dir: Path | str | None = None,
                        *, now: datetime | None = None,
                        exclude_user_id: str | None = None) -> tuple[RecentPlayer, ...]:
    """Latest unique joins only. No writes, no watcher and no API calls."""
    base = Path(log_dir) if log_dir else Path(MultiVRChatLogWatcher.default_log_dir())
    if not base.is_dir():
        return ()
    moment = now or datetime.now()
    cutoff = (moment - timedelta(days=MAX_AGE_DAYS)).timestamp()
    files = []
    try:
        for file in base.iterdir():
            if not _LOG_NAME.fullmatch(file.name) or file.is_symlink():
                continue
            try:
                info = file.stat()
                if not file.is_file() or info.st_size == 0 or info.st_mtime < cutoff:
                    continue
                files.append((info.st_mtime, file, info.st_size))
            except OSError:
                continue
    except OSError:
        return ()
    files.sort(key=lambda x: x[0], reverse=True)
    found: dict[str, RecentPlayer] = {}
    # Read at most two MB from each of eight recent logs. The initial partial
    # line is discarded, so partial IDs at the seek boundary never count.
    for mtime, path, size in files[:MAX_LOGS]:
        try:
            fallback = datetime.fromtimestamp(mtime).strftime("%Y.%m.%d %H:%M:%S")
            with path.open("rb") as stream:
                start = max(0, size - MAX_TAIL_BYTES)
                stream.seek(start)
                raw = stream.read(MAX_TAIL_BYTES)
            if start:
                raw = raw.partition(b"\n")[2]
            text = raw.decode("utf-8-sig", "replace")
        except OSError:
            continue
        for line in reversed(text.splitlines()):
            item = _log_line(line, path.name, fallback)
            if item and item.user_id != exclude_user_id and item.user_id not in found:
                found[item.user_id] = item
                if len(found) == MAX_PEOPLE:
                    break
        if len(found) == MAX_PEOPLE:
            break
    return tuple(found.values())


def filter_recent_players(rows, query: str):
    term = query.strip().casefold()
    return [r for r in rows if term in r.display_name.casefold()
            or term in r.user_id.casefold()]


class RecentPlayersPage:
    def __init__(self, pages, notebook):
        self.pages = pages
        self.owner = pages.owner
        self.rows: tuple[RecentPlayer, ...] = ()
        self.ids: dict[str, RecentPlayer] = {}
        self.query = tk.StringVar()
        self.status = tk.StringVar(value="No scan run. Scanning is manual and local-only.")
        tab = ttk.Frame(notebook, padding=(16, 12))
        notebook.add(tab, text="Recent Players")
        ttk.Label(tab, text="RECENT PLAYERS", style="PageTitle.TLabel").pack(anchor="w")
        ttk.Label(tab, text=(
            "See people you encountered in PCVR from this PC's VRChat output logs. "
            "Only scan when you choose; nothing is saved, uploaded, or sent to the Invite Network."
        ), wraplength=790).pack(anchor="w", pady=(3, 12))
        tools=ttk.LabelFrame(tab,text="Local PCVR history",padding=(12,9))
        tools.pack(fill="x",pady=(0,9))
        toolbar=ttk.Frame(tools);toolbar.pack(fill="x",pady=(0,8))
        ttk.Button(toolbar,text="Scan PCVR Logs",style="Accent.TButton",
                   command=self.scan).pack(side="left",padx=(0,9))
        ttk.Button(toolbar,text="Clear Results",command=self.clear).pack(side="left")
        search=ttk.Frame(tools);search.pack(fill="x")
        ttk.Label(search,text="Find player").pack(side="left",padx=(0,9))
        ttk.Entry(search,textvariable=self.query).pack(
            side="left",fill="x",expand=True)
        self.query.trace_add("write", lambda *_: self.render())
        ttk.Label(tab, textvariable=self.status, wraplength=800).pack(
            anchor="w", pady=(3, 8))
        pane = ttk.Frame(tab)
        pane.pack(fill="both", expand=True)
        columns = ("seen", "name", "id", "source")
        self.table = ttk.Treeview(pane, columns=columns, show="headings",
                                  selectmode="browse", height=12)
        for title, width in (("seen",155),("name",195),("id",290),("source",160)):
            self.table.heading(title,text={
                "seen":"Last seen (local)", "name":"Display name",
                "id":"VRChat ID", "source":"Log file"}[title])
            self.table.column(title,width=width,stretch=(title=="name"))
        scroll=ttk.Scrollbar(pane,orient="vertical",command=self.table.yview)
        self.table.configure(yscrollcommand=scroll.set)
        self.table.pack(side="left",fill="both",expand=True)
        scroll.pack(side="right",fill="y")
        row=ttk.Frame(tab)
        row.pack(anchor="w",pady=(10,0))
        ttk.Button(row,text="Open Profile",command=self.open_profile).pack(
            side="left",padx=(0,9))
        ttk.Button(row,text="Open on VRChat.com",command=self.open_browser).pack(
            side="left",padx=(0,9))
        ttk.Button(row,text="Copy User ID",command=self.copy_id).pack(side="left")
        self.table.bind("<Double-1>",lambda _: self.open_profile())

    def scan(self):
        if not self.owner._terms_valid():
            self.status.set("Accept the Desktop Terms before inspecting local player history.")
            return
        self.status.set("Reading recent PCVR log tails (local only)…")
        exclude=self.pages.account.user_id if self.pages.account.signed_in else None
        def work():
            return scan_recent_players(exclude_user_id=exclude)
        def done(rows):
            self.rows=rows
            self.render()
        self.owner._run_async(work,done,on_error=lambda e:
                              self.status.set("Could not read PCVR logs: "+e))

    def render(self):
        selected = self._selected_id()
        self.table.delete(*self.table.get_children())
        self.ids.clear()
        for row in filter_recent_players(self.rows,self.query.get()):
            iid=self.table.insert("",tk.END,values=(
                row.seen_at,row.display_name,row.user_id,row.source))
            self.ids[iid]=row
            if row.user_id==selected:
                self.table.selection_set(iid)
        count=len(self.ids)
        self.status.set(f"{count} displayed / {len(self.rows)} unique recent players. "
            "Read-only; not stored or uploaded. Scan again to refresh.")

    def _selected_id(self):
        selection=self.table.selection()
        return self.ids[selection[0]].user_id if selection and selection[0] in self.ids else None

    def _selection(self):
        uid=self._selected_id()
        if not uid:
            raise ValueError("Select a player first")
        return uid

    def clear(self):
        self.rows=()
        self.query.set("")
        self.render()

    def open_profile(self):
        try:
            self.pages.profile_tools.set_selected(self._selection())
        except Exception as exc:
            self.status.set(str(exc))

    def open_browser(self):
        try: webbrowser.open("https://vrchat.com/home/user/"+self._selection())
        except Exception as exc:self.status.set(str(exc))

    def copy_id(self):
        try:
            uid=self._selection()
            self.owner.root.clipboard_clear()
            self.owner.root.clipboard_append(uid)
            self.status.set("Selected VRChat user ID copied.")
        except Exception as exc:self.status.set(str(exc))
