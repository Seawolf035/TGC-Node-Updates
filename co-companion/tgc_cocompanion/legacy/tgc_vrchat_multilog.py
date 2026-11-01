#!/usr/bin/env python3
"""Shared multi-instance VRChat output-log watcher for TGC Invite Network.

Standard-library only. One watcher instance automatically discovers every currently
active output_log_*.txt on the PC, tails them independently, and reports joins.
Designed to be reusable from the standalone staff node, the central inviter, or
another Python application.
"""
import os
import csv
import subprocess
import re
import threading
import time
from dataclasses import dataclass, field
from datetime import datetime, timezone
from typing import Callable, Dict, Optional, Set

JOIN_RE = re.compile(r"OnPlayerJoined\s+(.+?)\s+\((usr_[0-9a-fA-F-]{36})\)")
LEAVE_RE = re.compile(r"OnPlayerLeft\s+(.+?)\s+\((usr_[0-9a-fA-F-]{36})\)")
TIMESTAMP_RE = re.compile(r"(\d{4}\.\d{2}\.\d{2} \d{2}:\d{2}:\d{2})")


@dataclass
class LogState:
    path: str
    position: int = 0
    last_size: int = 0
    last_activity: float = field(default_factory=time.time)
    active_players: Set[str] = field(default_factory=set)
    initialized: bool = False


class MultiVRChatLogWatcher:
    """Tail all active VRChat output logs on a Windows PC.

    on_join(user_id, display_name, seen_at_iso, source_log)
    on_status(summary_dict)

    summary_dict keys:
      running, active_logs, tracked_logs, active_players, log_names, message
    """

    def __init__(
        self,
        on_join: Callable[[str, str, str, str], None],
        on_status: Optional[Callable[[dict], None]] = None,
        log_dir: Optional[str] = None,
        discover_interval: float = 2.0,
        fresh_window_seconds: int = 15 * 60,
        retire_after_seconds: int = 15 * 60,
        initial_scan_current_instance: bool = True,
    ):
        self.on_join = on_join
        self.on_status = on_status
        self._log_dir = log_dir
        self.discover_interval = max(0.5, float(discover_interval))
        self.fresh_window_seconds = max(30, int(fresh_window_seconds))
        self.retire_after_seconds = max(self.fresh_window_seconds, int(retire_after_seconds))
        self.initial_scan_current_instance = bool(initial_scan_current_instance)

        self.stop_event = threading.Event()
        self.thread = None
        self.lock = threading.RLock()
        self.logs: Dict[str, LogState] = {}
        self._last_summary = None
        self._logging_health_last_check = 0.0
        self._logging_empty_since = None
        self._logging_warning = False

    @staticmethod
    def default_log_dir():
        home = os.path.expanduser("~")
        return os.path.join(home, "AppData", "LocalLow", "VRChat", "VRChat")

    @property
    def log_dir(self):
        return self._log_dir or self.default_log_dir()

    def start(self):
        if self.thread and self.thread.is_alive():
            return False
        self.stop_event.clear()
        self.thread = threading.Thread(target=self._run, name="TGC-MultiVRChatLogWatcher", daemon=True)
        self.thread.start()
        return True

    def stop(self, join_timeout=2.0):
        self.stop_event.set()
        if self.thread and self.thread.is_alive():
            self.thread.join(timeout=join_timeout)

    def is_running(self):
        return bool(self.thread and self.thread.is_alive() and not self.stop_event.is_set())

    def snapshot(self):
        now = time.time()
        with self.lock:
            active = [s for s in self.logs.values() if (now - s.last_activity) <= self.retire_after_seconds]
            return {
                "running": self.is_running(),
                "active_logs": len(active),
                "tracked_logs": len(self.logs),
                "active_players": sum(len(s.active_players) for s in active),
                "log_names": [os.path.basename(s.path) for s in active],
                "message": self._summary_message(len(active)),
                "logging_warning": self._logging_warning,
            }

    @staticmethod
    def _vrchat_process_running():
        """Read-only VRChat process probe; no psutil dependency for standalone Nodes."""
        if os.name != "nt":
            return False
        try:
            result = subprocess.run(
                ["tasklist.exe", "/FI", "IMAGENAME eq VRChat.exe", "/FO", "CSV", "/NH"],
                stdout=subprocess.PIPE, stderr=subprocess.DEVNULL,
                text=True, timeout=5,
                creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0))
            if result.returncode:
                return False
            return any(row and row[0].strip().lower()=="vrchat.exe"
                       for row in csv.reader(result.stdout.splitlines()))
        except (OSError, subprocess.TimeoutExpired, csv.Error):
            return False

    def _check_logging_health(self, now):
        if now - self._logging_health_last_check < 15.0:
            return
        self._logging_health_last_check = now
        if not self._vrchat_process_running():
            self._logging_empty_since = None
            self._logging_warning = False
            return

        recent_output = False
        try:
            with os.scandir(self.log_dir) as entries:
                for entry in entries:
                    if not (entry.name.startswith("output_log_") and entry.name.endswith(".txt")):
                        continue
                    try:
                        stat = entry.stat()
                    except OSError:
                        continue
                    if stat.st_size > 0 and now-stat.st_mtime <= 300:
                        recent_output = True
                        break
        except OSError:
            pass

        if recent_output:
            self._logging_empty_since = None
            self._logging_warning = False
        else:
            if self._logging_empty_since is None:
                self._logging_empty_since = now
            self._logging_warning = (now-self._logging_empty_since) >= 60.0

    def _summary_message(self, active_count):
        if self._logging_warning:
            return 'WARNING: VRChat logging appears disabled (no recent log data). Enable VRChat output logging in VRChat settings and restart VRChat.'
        if not os.path.isdir(self.log_dir):
            return "VRChat log directory not found"
        if active_count == 0:
            return "Waiting for active VRChat instances..."
        if active_count == 1:
            return "Watching 1 active VRChat instance"
        return f"Watching {active_count} active VRChat instances"

    def _emit_status(self, force=False):
        if not self.on_status:
            return
        summary = self.snapshot()
        compact = (summary["running"], summary["active_logs"], summary["tracked_logs"], summary["active_players"], tuple(summary["log_names"]), summary["logging_warning"])
        if force or compact != self._last_summary:
            self._last_summary = compact
            try:
                self.on_status(summary)
            except Exception:
                pass

    @staticmethod
    def _seen_at(line):
        m = TIMESTAMP_RE.search(line)
        if m:
            try:
                # VRChat timestamps are local machine time without an explicit offset.
                dt = datetime.strptime(m.group(1), "%Y.%m.%d %H:%M:%S").astimezone()
                return dt.astimezone(timezone.utc).isoformat()
            except Exception:
                pass
        return datetime.now(timezone.utc).isoformat()

    def _process_line(self, state: LogState, line: str, emit_join=True):
        if "Joining" in line and "Room" in line:
            state.active_players.clear()

        m = JOIN_RE.search(line)
        if m:
            display_name = m.group(1).strip()
            user_id = m.group(2).strip()
            state.active_players.add(user_id)
            if emit_join:
                try:
                    self.on_join(user_id, display_name, self._seen_at(line), state.path)
                except Exception:
                    pass
            return

        m = LEAVE_RE.search(line)
        if m:
            state.active_players.discard(m.group(2).strip())

    def _scan_current_instance(self, state: LogState):
        """Process joins after the most recent room switch, then tail from EOF."""
        try:
            with open(state.path, "r", encoding="utf-8", errors="ignore") as f:
                lines = f.readlines()
            start = 0
            for i, line in enumerate(lines):
                if "Joining" in line and "Room" in line:
                    start = i
            state.active_players.clear()
            for line in lines[start:]:
                self._process_line(state, line, emit_join=True)
            state.position = os.path.getsize(state.path)
            state.last_size = state.position
        except Exception:
            try:
                state.position = os.path.getsize(state.path)
                state.last_size = state.position
            except Exception:
                state.position = 0
                state.last_size = 0

    def _initialize_log(self, path, now):
        try:
            mtime = os.path.getmtime(path)
            size = os.path.getsize(path)
        except OSError:
            return

        state = LogState(path=path, position=0, last_size=size, last_activity=mtime, initialized=True)

        # Only replay the current instance for a log that looks live/recent. Historical
        # sessions are positioned at EOF and ignored unless they begin growing again.
        if self.initial_scan_current_instance and (now - mtime) <= self.fresh_window_seconds:
            self._scan_current_instance(state)
        else:
            state.position = size

        with self.lock:
            self.logs[path] = state

    def _discover(self, now):
        directory = self.log_dir
        if not os.path.isdir(directory):
            return
        try:
            names = [n for n in os.listdir(directory) if n.startswith("output_log_") and n.endswith(".txt")]
        except OSError:
            return

        for name in names:
            path = os.path.join(directory, name)
            with self.lock:
                known = path in self.logs
            if known:
                continue
            try:
                mtime = os.path.getmtime(path)
            except OSError:
                continue
            # Track recent logs immediately. Older logs can be ignored; if they somehow
            # begin growing later, their mtime becomes recent and discovery will pick them up.
            if (now - mtime) <= self.fresh_window_seconds:
                self._initialize_log(path, now)

    def _poll_log(self, state: LogState, now):
        path = state.path
        try:
            size = os.path.getsize(path)
            mtime = os.path.getmtime(path)
        except OSError:
            return False

        if size < state.position:
            state.position = 0

        if size > state.position:
            try:
                with open(path, "r", encoding="utf-8", errors="ignore") as f:
                    f.seek(state.position)
                    for line in f:
                        self._process_line(state, line, emit_join=True)
                    state.position = f.tell()
                state.last_size = size
                state.last_activity = now
            except Exception:
                pass
        elif mtime > state.last_activity:
            state.last_activity = mtime

        return True

    def _retire_stale(self, now):
        # Keep a small bounded amount of state. Retired files are removed after the
        # inactivity window; if a file becomes active again it will be rediscovered.
        with self.lock:
            stale = [p for p, s in self.logs.items() if (now - s.last_activity) > self.retire_after_seconds]
            for path in stale:
                self.logs.pop(path, None)

    def _run(self):
        self._emit_status(force=True)
        while not self.stop_event.is_set():
            now = time.time()
            self._discover(now)
            with self.lock:
                states = list(self.logs.values())
            for state in states:
                self._poll_log(state, now)
            self._retire_stale(now)
            self._check_logging_health(now)
            self._emit_status()
            self.stop_event.wait(self.discover_interval)
        self._emit_status(force=True)
