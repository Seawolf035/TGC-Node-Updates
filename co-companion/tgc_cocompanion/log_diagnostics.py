"""Windows VRChat log readiness without reading or disclosing player activity.

Intended for future verified staff troubleshooting. Only filenames, sizes and
modification times are examined. The optional process-running probe is provided
by the established legacy watcher and remains read-only.
"""
from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
import time

from .legacy.tgc_vrchat_multilog import MultiVRChatLogWatcher


@dataclass(frozen=True)
class LogReadiness:
    directory_found: bool
    files_found: int
    recent_nonempty_files: int
    vrchat_running: bool | None
    message: str

    @property
    def ready(self) -> bool:
        return self.directory_found and self.recent_nonempty_files > 0


def scan_log_readiness(
    log_dir: str | Path | None = None,
    *,
    now: float | None = None,
    max_age_seconds: int = 300,
    process_running: bool | None = None,
) -> LogReadiness:
    if not isinstance(max_age_seconds, int) or max_age_seconds < 1:
        raise ValueError("max_age_seconds must be a positive integer")
    directory = Path(log_dir if log_dir is not None
                     else MultiVRChatLogWatcher.default_log_dir())
    moment = time.time() if now is None else float(now)
    vr_running = process_running

    if not directory.is_dir():
        return LogReadiness(False, 0, 0, vr_running,
                            "VRChat output log folder not found")

    found = recent = 0
    try:
        entries = directory.iterdir()
        for candidate in entries:
            if not candidate.name.startswith("output_log_") or not candidate.name.endswith(".txt"):
                continue
            try:
                stat = candidate.stat()
                if not candidate.is_file():
                    continue
            except OSError:
                continue
            found += 1
            if stat.st_size > 0 and 0 <= moment - stat.st_mtime <= max_age_seconds:
                recent += 1
    except OSError:
        return LogReadiness(True, 0, 0, vr_running,
                            "Cannot inspect VRChat output logs")

    if recent:
        message = "Recent VRChat output logs found"
    elif found:
        message = "VRChat logs exist, but none contain recent output"
    else:
        message = "No VRChat output logs found"

    return LogReadiness(True, found, recent, vr_running, message)
