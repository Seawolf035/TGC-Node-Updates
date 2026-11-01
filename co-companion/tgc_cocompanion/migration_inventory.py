"""Read-only inventory of an existing Staff Launcher installation.

This does NOT read configuration values, decrypt Node keys, open its SQLite
queue, start its watchers, or trigger registration/Relay requests. It is safe
to run while an old launcher is installed, but NOT sufficient to authorize a
migration or conclude that the old launcher has stopped.
"""
from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path


REQUIRED_STAFF_FILES = (
    "TGC_Staff_Launcher.py",
    "tgc_staff_node.py",
    "tgc_vrchat_multilog.py",
)
RUNTIME_FILES = (
    "tgc_staff_launcher_config.json",
    "tgc_staff_node_config.json",
    "tgc_staff_node_key.dpapi",
    "tgc_staff_node_queue.db",
)


@dataclass(frozen=True)
class LegacyInstallInventory:
    installation_found: bool
    source_files_found: tuple[str, ...]
    source_files_missing: tuple[str, ...]
    runtime_files_found: tuple[str, ...]
    runtime_files_missing: tuple[str, ...]
    queue_sidecars_found: tuple[str, ...]
    read_errors: tuple[str, ...]
    migration_authorized: bool = False

    @property
    def summary(self) -> str:
        if not self.installation_found:
            return "Staff Launcher folder not found"
        if self.read_errors:
            return "Cannot inspect all Staff Launcher files"
        if self.source_files_missing:
            return "Staff Launcher installation is incomplete"
        return "Legacy Staff Launcher files identified — migration not approved"


def inspect_staff_installation(directory: str | Path) -> LegacyInstallInventory:
    root = Path(directory)
    if not root.is_dir():
        return LegacyInstallInventory(False, (), REQUIRED_STAFF_FILES, (),
                                      RUNTIME_FILES, (), ())
    source_found, source_missing = [], []
    runtime_found, runtime_missing = [], []
    sidecars, errors = [], []

    for name in REQUIRED_STAFF_FILES + RUNTIME_FILES + (
        "tgc_staff_node_queue.db-wal", "tgc_staff_node_queue.db-shm",
    ):
        try:
            candidate = root / name
            exists = candidate.is_file() and not candidate.is_symlink()
        except OSError:
            exists = False
            errors.append(name)
        if name in REQUIRED_STAFF_FILES:
            (source_found if exists else source_missing).append(name)
        elif name in RUNTIME_FILES:
            (runtime_found if exists else runtime_missing).append(name)
        elif exists:
            sidecars.append(name)
    # No content, paths, node IDs, keys, tokens, queue rows, usernames or
    # private settings ever leave this process. Never signal migration ready:
    # duplicate Node/Relay uploader detection and safe backup are not built.
    return LegacyInstallInventory(
        True, tuple(source_found), tuple(source_missing),
        tuple(runtime_found), tuple(runtime_missing), tuple(sidecars),
        tuple(errors), False,
    )
