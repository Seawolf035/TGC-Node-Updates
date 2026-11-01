"""Offline Co-Companion tracking regression tests. No live VRChat/Relay/network."""
import os
from pathlib import Path
import tempfile
import time
import unittest
from unittest.mock import MagicMock

from tgc_cocompanion.legacy.tgc_vrchat_multilog import MultiVRChatLogWatcher
from tgc_cocompanion.log_diagnostics import scan_log_readiness
from tgc_cocompanion.tracking import (
    LocalTrackingService, TrackingNotAuthorized,
)


USER_A = "usr_00000000-0000-4000-8000-000000000001"
USER_B = "usr_00000000-0000-4000-8000-000000000002"


class FakeWatcher:
    def __init__(self, on_join, on_status=None, **options):
        self.join = on_join
        self.status = on_status
        self.options = options
        self.running = False
        self.stopped = False

    def start(self):
        self.running = True
        return True

    def stop(self):
        self.stopped = True
        self.running = False

    def is_running(self):
        return self.running


class TrackingServiceTests(unittest.TestCase):
    def test_requires_staff_and_terms(self):
        with self.assertRaises(TrackingNotAuthorized):
            LocalTrackingService(lambda: False, lambda: True).start()
        with self.assertRaises(TrackingNotAuthorized):
            LocalTrackingService(lambda: True, lambda: False).start()

    def test_errors_fail_closed(self):
        def broken():
            raise RuntimeError("No server")
        with self.assertRaises(TrackingNotAuthorized):
            LocalTrackingService(broken, lambda: True).start()

    def test_authorized_local_counts_without_identity(self):
        a = {"staff": True, "terms": True}
        service = LocalTrackingService(
            lambda: a["staff"], lambda: a["terms"], watcher_factory=FakeWatcher
        )
        self.assertTrue(service.start())
        self.assertFalse(service.start())  # no duplicate active collectors
        watcher = service._watcher
        self.assertFalse(watcher.options["initial_scan_current_instance"])
        watcher.join(USER_A, "Person A", "now", "/sensitive/path/log.txt")
        watcher.status({
            "running": True, "active_logs": 2, "logging_warning": False,
            "log_names": ["/private/foo.txt"], "message": "Watching 2 active VRChat instances",
        })
        snap = service.snapshot()
        self.assertTrue(snap.running)
        self.assertEqual(snap.join_count, 1)
        self.assertEqual(snap.log_count, 2)
        self.assertNotIn(USER_A, repr(snap))
        self.assertNotIn("Person A", repr(snap))
        self.assertNotIn("private", repr(snap))
        service.stop()
        self.assertTrue(watcher.stopped)

    def test_revoked_staff_stops_and_blocks_callbacks(self):
        a = {"staff": True}
        service = LocalTrackingService(lambda: a["staff"], lambda: True,
                                       watcher_factory=FakeWatcher)
        service.start()
        old = service._watcher
        old.join(USER_A, "A", "now", "local")
        a["staff"] = False
        self.assertFalse(service.check_authorization())
        self.assertTrue(old.stopped)
        old.join(USER_B, "B", "now", "local")
        self.assertEqual(service.snapshot().join_count, 0)
        with self.assertRaises(TrackingNotAuthorized):
            service.start()

    def test_invalid_message_is_not_echoed(self):
        a = LocalTrackingService(lambda: True, lambda: True,
                                 watcher_factory=FakeWatcher)
        a.start()
        a._watcher.status({"message": "secret /abc usr_...", "active_logs": 1})
        self.assertEqual(a.snapshot().message, "Waiting for PC VRChat logs")
        a.stop()


class LogReadinessTests(unittest.TestCase):
    def test_missing_directory(self):
        with tempfile.TemporaryDirectory() as td:
            result = scan_log_readiness(Path(td) / "missing", process_running=True)
            self.assertFalse(result.directory_found)
            self.assertFalse(result.ready)

    def test_recent_and_empty_files(self):
        with tempfile.TemporaryDirectory() as td:
            p = Path(td)
            (p / "notes.txt").write_text("ignored")
            (p / "output_log_empty.txt").touch()
            (p / "output_log_recent.txt").write_text("hello")
            result = scan_log_readiness(p, now=time.time(), process_running=True)
            self.assertEqual(result.files_found, 2)
            self.assertEqual(result.recent_nonempty_files, 1)
            self.assertTrue(result.ready)
            self.assertNotIn(td, repr(result))

    def test_stale_file_warning(self):
        with tempfile.TemporaryDirectory() as td:
            path = Path(td) / "output_log_old.txt"
            path.write_text("some log")
            old = time.time() - 900
            os.utime(path, (old, old))
            result = scan_log_readiness(td, now=time.time())
            self.assertFalse(result.ready)
            self.assertIn("none contain recent", result.message)

    def test_negative_age_rejected(self):
        with self.assertRaises(ValueError):
            scan_log_readiness(".", max_age_seconds=0)


class LegacyMultiLogTests(unittest.TestCase):
    def test_two_files_join_leave_without_old_replay(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            first = root / "output_log_A.txt"
            second = root / "output_log_B.txt"
            first.write_text(
                "2026.11.01 09:00:00 Joining Room: example\n"
                + f"2026.11.01 09:00:01 OnPlayerJoined Old ({USER_A})\n",
                encoding="utf-8"
            )
            second.write_text("2026.11.01 09:00:01 Joining Room: example\n", encoding="utf-8")
            events = []
            watcher = MultiVRChatLogWatcher(
                lambda uid, name, when, source: events.append((uid, name, source)),
                log_dir=td, initial_scan_current_instance=False,
            )
            watcher._discover(time.time())
            self.assertEqual(len(watcher.logs), 2)
            self.assertEqual(events, [])
            with first.open("a", encoding="utf-8") as f:
                f.write(f"2026.11.01 09:00:02 OnPlayerJoined Alice ({USER_A})\n")
            with second.open("a", encoding="utf-8") as f:
                f.write(f"2026.11.01 09:00:02 OnPlayerJoined Bob ({USER_B})\n")
            for state in watcher.logs.values():
                watcher._poll_log(state, time.time())
            self.assertEqual({e[0] for e in events}, {USER_A, USER_B})
            self.assertEqual(len(events), 2)
            for state in watcher.logs.values():
                watcher._poll_log(state, time.time())
            self.assertEqual(len(events), 2)
            with first.open("a", encoding="utf-8") as f:
                f.write(f"2026.11.01 09:00:04 OnPlayerLeft Alice ({USER_A})\n")
            for state in watcher.logs.values():
                watcher._poll_log(state, time.time())
            a = next(s for s in watcher.logs.values() if s.path == str(first))
            self.assertNotIn(USER_A, a.active_players)
            b = next(s for s in watcher.logs.values() if s.path == str(second))
            self.assertIn(USER_B, b.active_players)


if __name__ == "__main__":
    unittest.main()
