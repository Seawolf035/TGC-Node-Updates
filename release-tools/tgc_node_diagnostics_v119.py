#!/usr/bin/env python3
"""TGC Node v1.1.9 read-only, privacy-safe VRChat tracking report."""
import os
from pathlib import Path
import re
import time
from datetime import datetime

JOIN_RE = re.compile(r"OnPlayerJoined\s+(.+?)\s+\((usr_[0-9a-fA-F-]{36})\)")
LEAVE_RE = re.compile(r"OnPlayerLeft\s+(.+?)\s+\((usr_[0-9a-fA-F-]{36})\)")
TAIL_BYTES = 12 * 1024 * 1024


def _inspect_log(path, now):
    stat = path.stat()
    size = stat.st_size
    item = dict(size=size, age_s=max(0, int(now - stat.st_mtime)),
                joins=0, joins_after_room=0, unmatched=0,
                leaves=0, transitions=0, truncated=size > TAIL_BYTES)
    with path.open("rb") as f:
        if item["truncated"]:
            f.seek(-TAIL_BYTES, os.SEEK_END)
            f.readline()
        lines = f.read().decode("utf-8", errors="replace").splitlines()
    latest_room = 0
    for i, line in enumerate(lines):
        if "Joining" in line and "Room" in line:
            latest_room = i
            item["transitions"] += 1
        if "OnPlayerJoined" in line:
            if JOIN_RE.search(line):
                item["joins"] += 1
            else:
                item["unmatched"] += 1
        if LEAVE_RE.search(line):
            item["leaves"] += 1
    item["joins_after_room"] = sum(bool(JOIN_RE.search(line)) for line in lines[latest_room:])
    return item


def run_tracking_diagnostic(log_dir=None, output_dir=None, sample_seconds=10,
                            watcher_snapshot=None, session_stats=None):
    """Sample log files without stopping the live Node, then export counts only."""
    log_path = Path(log_dir) if log_dir else Path.home() / "AppData" / "LocalLow" / "VRChat" / "VRChat"
    destination = Path(output_dir) if output_dir else Path.home() / "Downloads"
    if not destination.is_dir():
        destination = Path.home()
    seconds = max(0, min(45, int(sample_seconds)))
    report = [
        "TGC Invite Node — VRChat Tracking Diagnostic",
        f'Generated: {datetime.now().astimezone().isoformat(timespec="seconds")}',
        "Read-only. Contains no player names, user IDs, log text, or Node credentials.",
        "This report is not uploaded automatically.",
        "",
        f"VRChat log directory found: {log_path.is_dir()}",
    ]
    if isinstance(watcher_snapshot, dict):
        report.extend([
            "Live watcher status:",
            f'  Running: {bool(watcher_snapshot.get("running"))}',
            f'  Active logs: {int(watcher_snapshot.get("active_logs") or 0)}',
            f'  Tracked logs: {int(watcher_snapshot.get("tracked_logs") or 0)}',
            f'  Tracked players: {int(watcher_snapshot.get("active_players") or 0)}',
        ])
    if isinstance(session_stats, dict):
        report.extend([
            "Current session (counts only):",
            f'  Seen: {int(session_stats.get("seen") or 0)}',
            f'  New: {int(session_stats.get("new") or 0)}',
            f'  Local duplicates: {int(session_stats.get("duplicates") or 0)}',
            f'  Pending uploads: {int(session_stats.get("pending") or 0)}',
        ])
    recent = []
    selected = []
    inspected = {}
    failures = 0
    if log_path.is_dir():
        try:
            logs = sorted(log_path.glob("output_log_*.txt"), key=lambda x: x.stat().st_mtime, reverse=True)
        except OSError as exc:
            logs = []
            failures += 1
            report.append(f"Cannot list VRChat logs: {type(exc).__name__}; details omitted.")
        report.append(f"Logs found: {len(logs)}")
        now = time.time()
        for path in logs:
            try:
                if now - path.stat().st_mtime <= 900:
                    recent.append(path)
            except OSError:
                failures += 1
        report.append(f"Logs modified within 15 minutes: {len(recent)}")
        selected = logs[:5]
        for i, path in enumerate(selected, 1):
            try:
                item = _inspect_log(path, now)
                inspected[path] = item
                report.extend([
                    f'Log #{i}: size {item["size"]} bytes; last modified {item["age_s"]}s ago',
                    f'  Recent enough: {item["age_s"] <= 900}',
                    f'  Recognized joins: {item["joins"]}; after last room: {item["joins_after_room"]}',
                    f'  Unrecognized join lines: {item["unmatched"]}; leaves: {item["leaves"]}',
                    f'  Room entries: {item["transitions"]}; tail limited to 12MiB: {item["truncated"]}',
                ])
            except (OSError, UnicodeError, ValueError) as exc:
                failures += 1
                report.append(f"Log #{i}: READ ERROR {type(exc).__name__}; details omitted.")
    if log_path.is_dir() and selected and seconds:
        report.append(f"Watching for appended logs for {seconds}s without pausing Node.")
        time.sleep(seconds)
    growth = 0
    new_joins = 0
    for i, path in enumerate(selected, 1):
        if path not in inspected:
            continue
        try:
            item = _inspect_log(path, time.time())
            previous = inspected[path]
            delta = max(0, item["size"] - previous["size"])
            join_delta = max(0, item["joins"] - previous["joins"])
            growth += delta
            new_joins += join_delta
            report.append(f"Log #{i} during sample: +{delta} bytes, +{join_delta} joins")
        except (OSError, UnicodeError, ValueError) as exc:
            failures += 1
            report.append(f"Log #{i}: SECOND READ ERROR {type(exc).__name__}; details omitted.")

    if not log_path.is_dir():
        diagnosis = "VRChat logs were not found for this Windows profile."
    elif failures:
        diagnosis = "Log access errors; check file permissions and access."
    elif not recent:
        diagnosis = "No recently updated VRChat logs; check running VRChat and Windows profile."
    elif any(item["unmatched"] for item in inspected.values()):
        diagnosis = "Unrecognized OnPlayerJoined lines; the watcher parser may need updating."
    elif not any(item["joins"] for item in inspected.values()):
        diagnosis = "No OnPlayerJoined entries detected; join a populated instance and try again."
    elif isinstance(watcher_snapshot, dict) and int(watcher_snapshot.get("active_players") or 0) == 0:
        diagnosis = "VRChat join entries found, but live watcher tracks zero players. Investigate watcher/callback."
    elif not growth:
        diagnosis = "Join entries present but no new log activity during sample."
    else:
        diagnosis = "VRChat log activity present; compare Seen/New counters for callback behavior."
    report.extend(["", f"Appended: {growth} bytes; new joins: {new_joins}",
                   f"DIAGNOSIS: {diagnosis}",
                   "This diagnostic does not alter tracking, queues, log files, or credentials."])
    output = destination / ("TGC_Node_Tracking_Diagnostic_" + datetime.now().strftime("%Y%m%d_%H%M%S") + ".txt")
    output.write_text("\n".join(report) + "\n", encoding="utf-8")
    return str(output), diagnosis
