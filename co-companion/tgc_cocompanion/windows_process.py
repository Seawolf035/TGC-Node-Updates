"""Concrete Windows adapters for the Co-Companion VRChat launch controller.

Called only by the eventually verified Windows UI. These adapters do not
start on import and neither downloads nor authenticates nor uploads anything.
Do not use a launcher PID as the actual VRChat client: search VRChat.exe and
its exact `--osc=...` argument, preserving legacy 1.1.6 slot assignment.
"""
from __future__ import annotations

import os
from pathlib import Path
import subprocess
from typing import Callable, Iterable, Any


class ProcessDiscoveryError(RuntimeError):
    pass


def process_with_marker(processes: Iterable[Any], marker: str) -> int | None:
    """Find an unambiguous VRChat.exe process with an exact OSC marker.

    `processes` can contain psutil.Process objects or tests' fake equivalents.
    Explicitly refuse duplicate matching clients; never pick a random one.
    AccessDenied/NoSuchProcess are ignored because processes can disappear.
    """
    if not isinstance(marker, str) or not marker.startswith("--osc="):
        raise ValueError("Expected an exact VRChat OSC marker")
    matches = set()
    for proc in processes:
        try:
            # psutil exposes name()/cmdline(), whereas efficient fake process
            # adapters may expose the corresponding plain properties.
            name_attr = getattr(proc, "name", None)
            name = name_attr() if callable(name_attr) else name_attr
            if str(name or "").lower() != "vrchat.exe":
                continue
            args_attr = getattr(proc, "cmdline", None)
            args = args_attr() if callable(args_attr) else args_attr
            if not isinstance(args, (list, tuple)):
                continue
            if marker not in args:
                continue
            pid = getattr(proc, "pid", None)
            if type(pid) is not int or pid <= 0:
                continue
            matches.add(pid)
        except (OSError, RuntimeError):
            # The process may have quit or become inaccessible.
            continue
        except Exception as exc:
            if exc.__class__.__name__ in ("AccessDenied", "NoSuchProcess", "ZombieProcess"):
                continue
            raise
    if len(matches) > 1:
        raise ProcessDiscoveryError("Multiple VRChat clients have the same OSC marker")
    return next(iter(matches)) if matches else None


class WindowsVRChatProcessFinder:
    def __init__(self, process_iter: Callable[[], Iterable[Any]] | None = None):
        self._iter = process_iter

    def __call__(self, slot: int, marker: str) -> int | None:
        if not 1 <= slot <= 4:
            raise ValueError("Invalid instance slot")
        if self._iter is not None:
            processes = self._iter()
        else:
            if os.name != "nt":
                raise ProcessDiscoveryError("This adapter requires Windows")
            try:
                import psutil
            except ImportError as exc:
                raise ProcessDiscoveryError("The psutil Windows dependency is missing") from exc
            processes = psutil.process_iter()
        return process_with_marker(processes, marker)


class WindowsVRChatSpawner:
    """Trusted local launch adapter. Only accepts the structured legacy form."""

    def __init__(self, popen: Callable[..., object] = subprocess.Popen):
        self._popen = popen

    def __call__(self, argv: tuple[str, ...]) -> object:
        if not isinstance(argv, tuple) or len(argv) not in (2, 3):
            raise ValueError("Expected VRChat launch command")
        executable = Path(argv[0])
        if executable.name.lower() != "launch.exe" or not executable.is_file():
            raise ValueError("Expected an installed VRChat launch.exe")
        if not argv[1].startswith("--osc=") or argv[1].count(":") != 2:
            raise ValueError("Invalid VRChat OSC argument")
        if len(argv) == 3 and argv[2] != "--no-vr":
            raise ValueError("Unsupported VRChat launch option")
        # No shell, remote commands, extra process flags, or arbitrary paths
        # from paired mobile devices. Windows GUI app supplies this locally.
        return self._popen(argv, stdout=subprocess.DEVNULL,
                           stderr=subprocess.DEVNULL, cwd=str(executable.parent))
