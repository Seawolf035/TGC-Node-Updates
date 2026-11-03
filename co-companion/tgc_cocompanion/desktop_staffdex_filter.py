"""StaffDex read-only roster filtering without altering scan/award state."""
from __future__ import annotations


def filter_staffdex_rows(rows, query="", rank="All ranks", progress="All"):
    term = str(query or "").strip().casefold()
    requested = str(rank or "All ranks").strip()
    progress = str(progress or "All").strip()
    results = []
    for row in rows:
        if not isinstance(row, (tuple, list)) or len(row) < 4:
            continue
        name,role,status,identifier = row[:4]
        if term and term not in str(name).casefold() and term not in str(identifier).casefold():
            continue
        if requested != "All ranks" and str(role) != requested:
            continue
        if progress == "Collected" and status != "Collected":
            continue
        if progress == "Missing" and status not in ("Missing", "No VRChat ID"):
            continue
        if progress == "Unscanned" and status != "Not scanned":
            continue
        results.append((str(name),str(role),str(status),str(identifier)))
    return results
