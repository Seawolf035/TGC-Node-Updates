#!/usr/bin/env python3
import ctypes
import json
import os
import re
import sqlite3
import threading
import time
import urllib.parse
import urllib.request
import uuid
from datetime import datetime, timezone
from pathlib import Path

from tgc_vrchat_multilog import MultiVRChatLogWatcher

NODE_VERSION = "1.1.11-staff"
CLIENT_TYPE = "staff_launcher"
DEPLOYMENT_RE = re.compile(r"TGC-DEPLOY(?:-[A-F0-9]{4}){5}")




# Integrated diagnostic: compatible with fixed-file legacy updaters.
#!/usr/bin/env python3
"""TGC Node v1.1.9 read-only, privacy-safe VRChat tracking report."""
import os
from pathlib import Path
import re
import time
from datetime import datetime

DIAG_JOIN_RE = re.compile(r"OnPlayerJoined\s+(.+?)\s+\((usr_[0-9a-fA-F-]{36})\)")
DIAG_LEAVE_RE = re.compile(r"OnPlayerLeft\s+(.+?)\s+\((usr_[0-9a-fA-F-]{36})\)")
DIAG_TAIL_BYTES = 12 * 1024 * 1024


def _diagnostic_inspect_log(path, now):
    stat = path.stat()
    size = stat.st_size
    item = dict(size=size, age_s=max(0, int(now - stat.st_mtime)),
                joins=0, joins_after_room=0, unmatched=0,
                leaves=0, transitions=0, truncated=size > DIAG_TAIL_BYTES)
    with path.open("rb") as f:
        if item["truncated"]:
            f.seek(-DIAG_TAIL_BYTES, os.SEEK_END)
            f.readline()
        lines = f.read().decode("utf-8", errors="replace").splitlines()
    latest_room = 0
    for i, line in enumerate(lines):
        if "Joining" in line and "Room" in line:
            latest_room = i
            item["transitions"] += 1
        if "OnPlayerJoined" in line:
            if DIAG_JOIN_RE.search(line):
                item["joins"] += 1
            else:
                item["unmatched"] += 1
        if DIAG_LEAVE_RE.search(line):
            item["leaves"] += 1
    item["joins_after_room"] = sum(bool(DIAG_JOIN_RE.search(line)) for line in lines[latest_room:])
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
                item = _diagnostic_inspect_log(path, now)
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
            item = _diagnostic_inspect_log(path, time.time())
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


class _DATA_BLOB(ctypes.Structure):
    _fields_ = [("cbData", ctypes.c_ulong), ("pbData", ctypes.POINTER(ctypes.c_char))]


def _dpapi_protect(raw: bytes, description="TGC Staff Launcher Node Key") -> bytes:
    if os.name != "nt":
        raise RuntimeError("Windows DPAPI is only available on Windows")
    in_buffer = ctypes.create_string_buffer(raw)
    in_blob = _DATA_BLOB(len(raw), ctypes.cast(in_buffer, ctypes.POINTER(ctypes.c_char)))
    out_blob = _DATA_BLOB()
    crypt32 = ctypes.windll.crypt32
    kernel32 = ctypes.windll.kernel32
    ok = crypt32.CryptProtectData(
        ctypes.byref(in_blob), ctypes.c_wchar_p(description), None, None, None, 0, ctypes.byref(out_blob)
    )
    if not ok:
        raise ctypes.WinError()
    try:
        return ctypes.string_at(out_blob.pbData, out_blob.cbData)
    finally:
        kernel32.LocalFree(out_blob.pbData)


def _dpapi_unprotect(blob: bytes) -> bytes:
    if os.name != "nt":
        raise RuntimeError("Windows DPAPI is only available on Windows")
    in_buffer = ctypes.create_string_buffer(blob)
    in_blob = _DATA_BLOB(len(blob), ctypes.cast(in_buffer, ctypes.POINTER(ctypes.c_char)))
    out_blob = _DATA_BLOB()
    crypt32 = ctypes.windll.crypt32
    kernel32 = ctypes.windll.kernel32
    ok = crypt32.CryptUnprotectData(
        ctypes.byref(in_blob), None, None, None, None, 0, ctypes.byref(out_blob)
    )
    if not ok:
        raise ctypes.WinError()
    try:
        return ctypes.string_at(out_blob.pbData, out_blob.cbData)
    finally:
        kernel32.LocalFree(out_blob.pbData)


class LocalStore:
    def __init__(self, path: Path):
        self.path = str(path)
        self.lock = threading.RLock()
        self._init()

    def _con(self):
        con = sqlite3.connect(self.path, timeout=30)
        con.row_factory = sqlite3.Row
        return con

    def _init(self):
        with self._con() as con:
            con.executescript(
                """
                PRAGMA journal_mode=WAL;
                CREATE TABLE IF NOT EXISTS sightings (
                    event_id TEXT PRIMARY KEY,
                    user_id TEXT NOT NULL,
                    display_name TEXT,
                    seen_at TEXT NOT NULL,
                    created_at TEXT NOT NULL,
                    status TEXT NOT NULL DEFAULT 'pending',
                    attempts INTEGER NOT NULL DEFAULT 0,
                    last_error TEXT
                );
                CREATE INDEX IF NOT EXISTS idx_staff_sightings_status
                    ON sightings(status, created_at);
                CREATE TABLE IF NOT EXISTS recent_users (
                    user_id TEXT PRIMARY KEY,
                    last_seen_at TEXT NOT NULL
                );
                """
            )

    def add_sighting(self, user_id, display_name, seen_at, dedupe_minutes=60):
        now = datetime.now(timezone.utc)
        with self.lock, self._con() as con:
            row = con.execute("SELECT last_seen_at FROM recent_users WHERE user_id=?", (user_id,)).fetchone()
            if row:
                try:
                    last = datetime.fromisoformat(str(row["last_seen_at"]).replace("Z", "+00:00"))
                    if (now - last).total_seconds() < max(0, int(dedupe_minutes)) * 60:
                        con.execute(
                            "UPDATE recent_users SET last_seen_at=? WHERE user_id=?",
                            (now.isoformat(), user_id),
                        )
                        return False
                except Exception:
                    pass
            event_id = "evt_" + uuid.uuid4().hex
            con.execute(
                "INSERT INTO sightings(event_id,user_id,display_name,seen_at,created_at,status) "
                "VALUES(?,?,?,?,?,'pending')",
                (event_id, user_id, display_name, seen_at, now.isoformat()),
            )
            con.execute(
                "INSERT INTO recent_users(user_id,last_seen_at) VALUES(?,?) "
                "ON CONFLICT(user_id) DO UPDATE SET last_seen_at=excluded.last_seen_at",
                (user_id, now.isoformat()),
            )
            return True

    def pending(self, limit=100):
        with self.lock, self._con() as con:
            rows = con.execute(
                "SELECT * FROM sightings WHERE status='pending' ORDER BY created_at ASC LIMIT ?",
                (int(limit),),
            ).fetchall()
            return [dict(r) for r in rows]

    def mark_submitted(self, event_ids):
        if not event_ids:
            return
        with self.lock, self._con() as con:
            con.executemany(
                "UPDATE sightings SET status='submitted', last_error=NULL WHERE event_id=?",
                [(x,) for x in event_ids],
            )

    def mark_attempt_error(self, event_ids, error):
        if not event_ids:
            return
        with self.lock, self._con() as con:
            con.executemany(
                "UPDATE sightings SET attempts=attempts+1, last_error=? WHERE event_id=?",
                [(str(error)[:1000], x) for x in event_ids],
            )

    def counts(self):
        with self.lock, self._con() as con:
            total = con.execute("SELECT COUNT(*) FROM sightings").fetchone()[0]
            pending = con.execute("SELECT COUNT(*) FROM sightings WHERE status='pending'").fetchone()[0]
            submitted = con.execute("SELECT COUNT(*) FROM sightings WHERE status='submitted'").fetchone()[0]
        return total, pending, submitted


class StaffNodeService:
    """Embedded TGC Invite Node used by the verified Staff Launcher.

    The service has no standalone UI and deliberately has no manual credential path.
    A Staff Launcher must be provisioned with an owner-issued deployment link, and the
    relay must affirm that the node is a verified staff_launcher node before the parent
    launcher can unlock.
    """

    def __init__(self, base_dir: Path, status_callback=None, log_callback=None):
        self.base_dir = Path(base_dir)
        self.config_path = self.base_dir / "tgc_staff_node_config.json"
        self.key_path = self.base_dir / "tgc_staff_node_key.dpapi"
        self.store = LocalStore(self.base_dir / "tgc_staff_node_queue.db")
        self.status_callback = status_callback
        self.log_callback = log_callback
        self.config = self._load_config()
        self.verified_session = False
        self.verified_data = {}
        self.sync_stop = threading.Event()
        self.sync_thread = None
        self.sync_lock = threading.Lock()
        self.watcher = MultiVRChatLogWatcher(self._on_join, self._on_watcher_status)
        self.last_relay_status = "Not verified"
        self.last_watcher_status = "Stopped"
        self.active_logs = 0
        self.active_players = 0
        self.session_seen = 0
        self.session_new = 0
        self.session_duplicates = 0
        self.session_submitted = 0

    def _emit_status(self):
        if not self.status_callback:
            return
        total, pending, submitted = self.store.counts()
        payload = {
            "node_version": NODE_VERSION,
            "node_id": str(self.config.get("node_id") or ""),
            "node_name": str(self.config.get("node_name") or ""),
            "discord_id": str(self.config.get("discord_id") or ""),
            "verified": bool(self.verified_session),
            "relay": self.last_relay_status,
            "watcher": self.last_watcher_status,
            "active_logs": self.active_logs,
            "active_players": self.active_players,
            "total": total,
            "pending": pending,
            "submitted": submitted,
            "session_seen": self.session_seen,
            "session_new": self.session_new,
            "session_duplicates": self.session_duplicates,
            "session_submitted": self.session_submitted,
        }
        try:
            self.status_callback(payload)
        except Exception:
            pass

    def _log(self, message):
        if self.log_callback:
            try:
                self.log_callback(f"Node: {message}")
            except Exception:
                pass

    def _default_config(self):
        return {
            "relay_url": "",
            "node_id": "",
            "node_name": "",
            "discord_id": "",
            "install_id": uuid.uuid4().hex,
            "sync_interval_seconds": 60,
            "batch_size": 100,
            "local_dedupe_minutes": 60,
        }

    def _load_key(self):
        if not self.key_path.exists():
            return ""
        try:
            return _dpapi_unprotect(self.key_path.read_bytes()).decode("utf-8")
        except Exception:
            return ""

    def _save_key(self, value):
        value = str(value or "")
        if not value:
            try:
                self.key_path.unlink(missing_ok=True)
            except Exception:
                pass
            return
        protected = _dpapi_protect(value.encode("utf-8"))
        tmp = self.key_path.with_suffix(".tmp")
        tmp.write_bytes(protected)
        tmp.replace(self.key_path)

    def _load_config(self):
        data = self._default_config()
        legacy_key = ""
        if self.config_path.exists():
            try:
                raw = json.loads(self.config_path.read_text(encoding="utf-8"))
                if isinstance(raw, dict):
                    legacy_key = str(raw.pop("node_key", "") or "")
                    raw.pop("node_key_runtime", None)
                    data.update(raw)
            except Exception:
                pass
        if not str(data.get("install_id") or "").strip():
            data["install_id"] = uuid.uuid4().hex
        data["node_key_runtime"] = self._load_key() or legacy_key
        if legacy_key and os.name == "nt":
            try:
                self._save_config(data)
            except Exception:
                pass
        return data

    def _save_config(self, data=None):
        if data is not None:
            self.config = dict(data)
        payload = dict(self.config)
        key = str(payload.pop("node_key_runtime", "") or "")
        payload.pop("node_key", None)
        tmp = self.config_path.with_suffix(".tmp")
        tmp.write_text(json.dumps(payload, indent=2), encoding="utf-8")
        tmp.replace(self.config_path)
        self._save_key(key)

    def has_credentials(self):
        return bool(
            str(self.config.get("relay_url") or "").startswith("https://")
            and str(self.config.get("node_id") or "").strip()
            and str(self.config.get("node_key_runtime") or "").strip()
        )

    def parse_deployment_input(self, value):
        value = str(value or "").strip()
        if not value:
            raise RuntimeError("Paste the one-time TGC Staff Launcher deployment link from an owner.")
        relay_url = str(self.config.get("relay_url") or "").strip()
        code = value.upper()
        if value.lower().startswith("https://"):
            parsed = urllib.parse.urlsplit(value)
            query = urllib.parse.parse_qs(parsed.query)
            code = str((query.get("deploy") or [""])[0]).strip().upper()
            relay_url = urllib.parse.urlunsplit((parsed.scheme, parsed.netloc, parsed.path, "", ""))
        if not DEPLOYMENT_RE.fullmatch(code):
            raise RuntimeError("That is not a valid TGC deployment link/code.")
        if not relay_url.lower().startswith("https://"):
            raise RuntimeError("A fresh Staff Launcher requires the full deployment link, not only the short code.")
        return relay_url, code

    @staticmethod
    def _post_to_url(url, payload, timeout=30):
        url = str(url or "").strip()
        if not url.lower().startswith("https://"):
            raise RuntimeError("Relay URL is missing or invalid.")
        raw = json.dumps(payload).encode("utf-8")
        req = urllib.request.Request(
            url,
            data=raw,
            headers={
                "Content-Type": "application/json",
                "User-Agent": f"TGC-Staff-Launcher-Node/{NODE_VERSION}",
            },
            method="POST",
        )
        try:
            with urllib.request.urlopen(req, timeout=timeout) as resp:
                body = resp.read().decode("utf-8", errors="replace")
        except Exception as exc:
            raise RuntimeError(f"Could not reach the TGC verification relay: {exc}") from exc
        try:
            result = json.loads(body)
        except Exception as exc:
            raise RuntimeError("The TGC verification relay returned an invalid response.") from exc
        if not result.get("ok"):
            raise RuntimeError(result.get("error") or "The TGC verification relay rejected this request.")
        return result

    def _auth_payload(self, action, **extra):
        if not self.has_credentials():
            raise RuntimeError("Staff Launcher node credentials are not configured.")
        payload = {
            "action": action,
            "node_id": str(self.config.get("node_id") or "").strip(),
            "node_key": str(self.config.get("node_key_runtime") or "").strip(),
            "version": NODE_VERSION,
            "client_type": CLIENT_TYPE,
        }
        payload.update(extra)
        return payload

    def _post(self, payload, timeout=30):
        return self._post_to_url(self.config.get("relay_url", ""), payload, timeout=timeout)

    def redeem_deployment(self, deployment_input):
        relay_url, code = self.parse_deployment_input(deployment_input)
        install_id = str(self.config.get("install_id") or "").strip() or uuid.uuid4().hex
        data = self._post_to_url(
            relay_url,
            {
                "action": "redeem_deployment",
                "code": code,
                "client_type": CLIENT_TYPE,
                "version": NODE_VERSION,
                "install_id": install_id,
            },
            timeout=30,
        )
        if not data.get("verified"):
            raise RuntimeError("The relay did not verify this Staff Launcher deployment.")
        if str(data.get("client_type") or "").strip().lower() != CLIENT_TYPE:
            raise RuntimeError("That deployment is not authorized for the TGC Staff Launcher.")
        node_id = str(data.get("node_id") or "").strip()
        node_key = str(data.get("node_key") or "").strip()
        discord_id = str(data.get("discord_id") or "").strip()
        if not node_id or not node_key or not discord_id:
            raise RuntimeError("The relay did not return complete verified Staff Launcher credentials.")
        self.config.update(
            {
                "relay_url": relay_url,
                "node_id": node_id,
                "node_key_runtime": node_key,
                "node_name": str(data.get("node_name") or "").strip(),
                "discord_id": discord_id,
                "install_id": install_id,
            }
        )
        self._save_config()
        verified = self.verify()
        self._log(f"deployment redeemed for Discord {discord_id} ({node_id})")
        return verified

    def verify(self):
        if not self.has_credentials():
            self.verified_session = False
            raise RuntimeError("This Staff Launcher has not been deployed yet.")
        data = self._post(self._auth_payload("node_status"), timeout=30)
        client_type = str(data.get("client_type") or "").strip().lower()
        discord_id = str(data.get("discord_id") or "").strip()
        method = str(data.get("verification_method") or "").strip().lower()
        if not data.get("verified"):
            self.verified_session = False
            raise RuntimeError("This Staff Launcher is not verified by TGC.")
        if client_type != CLIENT_TYPE:
            self.verified_session = False
            raise RuntimeError("This node is not authorized as a Staff Launcher.")
        if not discord_id or method != "deployment_code":
            self.verified_session = False
            raise RuntimeError("This Staff Launcher must be provisioned to a verified staff Discord account.")
        canonical_name = str(data.get("node_name") or "").strip()
        self.config["node_name"] = canonical_name
        self.config["discord_id"] = discord_id
        self._save_config()
        self.verified_session = True
        self.verified_data = dict(data)
        self.last_relay_status = f"Verified — {canonical_name or self.config.get('node_id', '')}"
        self._emit_status()
        return data

    def clear_credentials(self):
        self.stop()
        keep = self._default_config()
        keep["install_id"] = str(self.config.get("install_id") or uuid.uuid4().hex)
        self.config = keep
        self.verified_session = False
        self.verified_data = {}
        self._save_config()
        self.last_relay_status = "Not deployed"
        self._emit_status()

    def start(self):
        if not self.verified_session:
            raise RuntimeError("The embedded node cannot start until this Staff Launcher is verified.")
        if not self.watcher.is_running():
            self.watcher.start()
        if self.sync_thread and self.sync_thread.is_alive():
            self._emit_status()
            return
        self.sync_stop.clear()
        self.sync_thread = threading.Thread(target=self._sync_loop, name="TGC-Staff-Node-Sync", daemon=True)
        self.sync_thread.start()
        self._log(f"embedded node v{NODE_VERSION} started")
        self._emit_status()

    def stop(self):
        self.sync_stop.set()
        try:
            self.watcher.stop()
        except Exception:
            pass
        self.last_watcher_status = "Stopped"
        self._emit_status()

    def _on_watcher_status(self, summary):
        if isinstance(summary, dict):
            active = bool(summary.get("logging_warning"))
            if active != getattr(self, "_last_logging_warning", False):
                self._last_logging_warning = active
                self._log('WARNING: VRChat logging appears disabled (no recent log data). Enable VRChat output logging in VRChat settings and restart VRChat.' if active else
                                 "VRChat logging resumed; warning cleared.")
        if isinstance(summary, dict):
            self.last_watcher_status = str(summary.get("message") or "Watching VRChat logs")
            self.active_logs = int(summary.get("active_logs") or 0)
            self.active_players = int(summary.get("active_players") or 0)
        else:
            self.last_watcher_status = str(summary)
        self._emit_status()

    def _on_join(self, user_id, display_name, seen_at, source_log=None):
        if not self.verified_session:
            return
        self.session_seen += 1
        added = self.store.add_sighting(
            user_id,
            display_name,
            seen_at,
            int(self.config.get("local_dedupe_minutes", 60)),
        )
        if added:
            self.session_new += 1
            self._log(f"queued {display_name} ({user_id})")
        else:
            self.session_duplicates += 1
        self._emit_status()

    def tracking_diagnostic(self, output_dir=None, sample_seconds=10):
        """Sample logs without interrupting the embedded Invite Node."""
        snap = self.watcher.snapshot()
        _total, pending, _submitted = self.store.counts()
        stats = dict(seen=self.session_seen, new=self.session_new,
                     duplicates=self.session_duplicates, pending=pending)
        return run_tracking_diagnostic(
            log_dir=self.watcher.log_dir, output_dir=output_dir,
            sample_seconds=sample_seconds, watcher_snapshot=snap,
            session_stats=stats)

    def sync_now(self):
        threading.Thread(target=self._sync_once, name="TGC-Staff-Node-SyncNow", daemon=True).start()

    def _sync_loop(self):
        while not self.sync_stop.is_set():
            self._sync_once()
            interval = max(15, int(self.config.get("sync_interval_seconds", 60)))
            self.sync_stop.wait(interval)

    def _sync_once(self):
        if not self.verified_session or not self.has_credentials():
            return
        if not self.sync_lock.acquire(blocking=False):
            return
        try:
            batch = self.store.pending(max(1, min(250, int(self.config.get("batch_size", 100)))))
            if not batch:
                self.last_relay_status = "Verified — nothing queued"
                self._emit_status()
                return
            sightings = [
                {
                    "event_id": row["event_id"],
                    "user_id": row["user_id"],
                    "display_name": row.get("display_name") or "",
                    "seen_at": row["seen_at"],
                }
                for row in batch
            ]
            ids = [row["event_id"] for row in batch]
            try:
                result = self._post(self._auth_payload("submit_sightings", sightings=sightings), timeout=30)

                # Relay v1.1.9 returns both a numeric accepted count and the
                # accepted event IDs. Older node code treated the numeric count
                # as an iterable, causing "'int' object is not iterable" after
                # the relay had already stored the sightings.
                accepted_raw = result.get("accepted_event_ids")
                if accepted_raw is None:
                    accepted_raw = result.get("accepted")

                if isinstance(accepted_raw, (list, tuple, set)):
                    accepted = [str(event_id) for event_id in accepted_raw if str(event_id)]
                elif isinstance(accepted_raw, str):
                    accepted = [accepted_raw] if accepted_raw else []
                elif isinstance(accepted_raw, (int, float)):
                    accepted_count = max(0, int(accepted_raw))
                    accepted = list(ids) if accepted_count == len(ids) else []
                else:
                    accepted = []

                self.store.mark_submitted(accepted)
                self.session_submitted += len(accepted)
                rejected_count = result.get("rejected")
                if isinstance(rejected_count, (int, float)) and int(rejected_count) > 0:
                    self.last_relay_status = (
                        f"Verified — uploaded {len(accepted)}; relay rejected {int(rejected_count)}"
                    )
                else:
                    self.last_relay_status = f"Verified — uploaded {len(accepted)}"
            except Exception as exc:
                self.store.mark_attempt_error(ids, exc)
                self.last_relay_status = f"Relay retry pending: {exc}"
                self._log(f"sync error: {exc}")
            self._emit_status()
        finally:
            self.sync_lock.release()
