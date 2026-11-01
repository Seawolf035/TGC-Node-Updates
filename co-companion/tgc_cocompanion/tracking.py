"""Opt-in, locally counted PCVR tracking built on the established TGC log watcher.

This intentionally NEVER calls the Invite Relay, stores no encountered
identities, and cannot replace an enrolled Node or upload invite sightings.
The real Co-Companion authorization/terms adapters have NOT been built yet;
only test code can currently provide the required approval predicates.
"""
from __future__ import annotations

from dataclasses import dataclass
from threading import RLock
from typing import Callable

from .legacy.tgc_vrchat_multilog import MultiVRChatLogWatcher


class TrackingNotAuthorized(PermissionError):
    pass


@dataclass(frozen=True)
class TrackingStatus:
    running: bool
    log_count: int
    join_count: int
    message: str
    logging_warning: bool


class LocalTrackingService:
    """Local read-only tracking with explicit staff and Terms gates.

    Does not store player names/IDs, create a SQLite queue, manage Node keys,
    make network requests or launch VRChat. New watcher instances start at the
    end of each log (not replaying earlier joins). Stop when access expires.
    """

    def __init__(
        self,
        staff_authorized: Callable[[], bool],
        terms_accepted: Callable[[], bool],
        *,
        log_dir: str | None = None,
        watcher_factory: Callable[..., MultiVRChatLogWatcher] = MultiVRChatLogWatcher,
    ):
        self._staff_authorized = staff_authorized
        self._terms_accepted = terms_accepted
        self._log_dir = log_dir
        self._factory = watcher_factory
        self._watcher: MultiVRChatLogWatcher | None = None
        self._lock = RLock()
        self._joins = 0
        self._status: dict = {}
        self._generation = 0

    def _authorized(self) -> bool:
        try:
            return self._staff_authorized() is True and self._terms_accepted() is True
        except Exception:
            return False

    def start(self) -> bool:
        """Start monitoring with consent, *never* restart a running watcher."""
        with self._lock:
            if not self._authorized():
                raise TrackingNotAuthorized("Verified staff authorization and desktop Terms required")
            if self._watcher is not None:
                if self._watcher.is_running():
                    return False
                # Retired worker references may be replaced after full stop.
                self._watcher = None
            self._generation += 1
            generation = self._generation
            self._joins = 0
            self._status = {}
            watcher = self._factory(
                on_join=lambda uid, name, when, source: self._observe_join(generation),
                on_status=lambda summary: self._observe_status(generation, summary),
                log_dir=self._log_dir,
                initial_scan_current_instance=False,
            )
            self._watcher = watcher
            try:
                started = watcher.start()
            except BaseException:
                self._watcher = None
                self._generation += 1
                raise
            if not started:
                self._watcher = None
                self._generation += 1
                raise RuntimeError("Log watcher could not start")
            return True

    def _observe_join(self, generation: int) -> None:
        # Never persist or forward player user IDs, display names or source logs.
        with self._lock:
            if generation == self._generation and self._authorized():
                self._joins += 1

    def _observe_status(self, generation: int, summary: dict) -> None:
        # No player names or log file names appear in the public status model.
        with self._lock:
            if generation == self._generation and self._authorized():
                self._status = {
                    "active_logs": max(0, int(summary.get("active_logs") or 0)),
                    "logging_warning": bool(summary.get("logging_warning")),
                    "message": self._safe_message(str(summary.get("message", ""))),
                }

    @staticmethod
    def _safe_message(message: str) -> str:
        # Prefer fixed human-readable status values; never echo arbitrary raw
        # log lines / paths from third-party watcher messages.
        if message.startswith("WARNING: VRChat logging appears disabled"):
            return "VRChat is running without recent output logs. Check its logging settings."
        if message == "VRChat log directory not found":
            return message
        if message.startswith("Watching ") and message.endswith(" active VRChat instances"):
            return "Watching PC VRChat logs"
        if message == "Watching 1 active VRChat instance":
            return "Watching PC VRChat logs"
        return "Waiting for PC VRChat logs"

    def stop(self) -> None:
        with self._lock:
            self._generation += 1
            watcher = self._watcher
            self._watcher = None
            self._status = {}
        if watcher is not None:
            watcher.stop()

    def check_authorization(self) -> bool:
        """Called periodically by eventual application scheduler; fail closed."""
        if self._authorized():
            return True
        self.stop()
        return False

    def snapshot(self) -> TrackingStatus:
        if not self._authorized():
            self.stop()
            return TrackingStatus(False, 0, 0, "Staff verification required", False)
        with self._lock:
            watcher = self._watcher
            running = bool(watcher and watcher.is_running())
            return TrackingStatus(
                running=running,
                log_count=self._status.get("active_logs", 0) if running else 0,
                join_count=self._joins if running else 0,
                message=self._status.get("message", "Tracking stopped") if running else "Tracking stopped",
                logging_warning=self._status.get("logging_warning", False) if running else False,
            )
