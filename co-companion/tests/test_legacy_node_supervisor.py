"""Offline embedded Node supervisor tests. Never touch Relay/DPAPI/SQLite."""
import unittest
from unittest.mock import Mock

from tgc_cocompanion.legacy_node_supervisor import (
    LegacyStaffNodeSupervisor, UnsafeNodeTakeover,
)
from tgc_cocompanion.permissions import VerifiedAccess


class FakeNode:
    def __init__(self):
        self.verified_session = True
        self.verified_data = {
            "client_type": "staff_launcher",
            "verification_method": "deployment_code",
            "discord_id": "123456789012345678",
        }
        self.starts = 0
        self.stops = 0

    def start(self):
        self.starts += 1

    def stop(self):
        self.stops += 1


class NodeSupervisorTests(unittest.TestCase):
    def setUp(self):
        self.flags = {
            "staff": VerifiedAccess("123456789012345678", True, 1, 10000),
            "terms": True,
            "migrated": True,
            "old_stopped": True,
            "now": 1000,
        }
        self.node = FakeNode()
        self.factory = Mock(return_value=self.node)
        self.supervisor = LegacyStaffNodeSupervisor(
            current_staff=lambda: self.flags["staff"],
            terms_accepted=lambda: self.flags["terms"],
            migration_approved=lambda: self.flags["migrated"],
            old_node_stopped=lambda: self.flags["old_stopped"],
            node_factory=self.factory,
            clock=lambda: self.flags["now"],
        )

    def test_missing_migration_approval_never_loads_credentials(self):
        self.flags["migrated"] = False
        with self.assertRaises(UnsafeNodeTakeover):
            self.supervisor.start()
        self.factory.assert_not_called()

    def test_old_node_still_running_denies(self):
        self.flags["old_stopped"] = False
        with self.assertRaises(UnsafeNodeTakeover):
            self.supervisor.start()
        self.factory.assert_not_called()

    def test_no_terms_or_staff_denies(self):
        self.flags["terms"] = False
        with self.assertRaises(UnsafeNodeTakeover):
            self.supervisor.start()
        self.flags["terms"] = True
        self.flags["staff"] = None
        with self.assertRaises(UnsafeNodeTakeover):
            self.supervisor.start()
        self.factory.assert_not_called()

    def test_server_grant_expired_denies(self):
        self.flags["now"] = 11000
        with self.assertRaises(UnsafeNodeTakeover):
            self.supervisor.start()
        self.factory.assert_not_called()

    def test_legacy_verification_must_be_valid(self):
        self.node.verified_session = False
        with self.assertRaises(UnsafeNodeTakeover):
            self.supervisor.start()
        self.assertEqual(self.node.starts, 0)

    def test_mismatched_staff_identity_denied(self):
        self.node.verified_data["discord_id"] = "123456789012345677"
        with self.assertRaises(UnsafeNodeTakeover):
            self.supervisor.start()
        self.assertEqual(self.node.starts, 0)

    def test_wrong_client_type_denied(self):
        self.node.verified_data["client_type"] = "standalone"
        with self.assertRaises(UnsafeNodeTakeover):
            self.supervisor.start()
        self.assertEqual(self.node.starts, 0)

    def test_wrong_verification_method_denied(self):
        self.node.verified_data["verification_method"] = "other"
        with self.assertRaises(UnsafeNodeTakeover):
            self.supervisor.start()
        self.assertEqual(self.node.starts, 0)

    def test_authorized_start_one_only_and_stop(self):
        self.assertTrue(self.supervisor.start())
        self.assertFalse(self.supervisor.start())
        self.factory.assert_called_once()
        self.assertEqual(self.node.starts, 1)
        self.assertTrue(self.supervisor.stop())
        self.assertEqual(self.node.stops, 1)
        self.assertFalse(self.supervisor.stop())
        self.assertFalse(self.supervisor.running)

    def test_revocation_stops_existing_node(self):
        self.supervisor.start()
        self.flags["staff"] = None
        self.assertFalse(self.supervisor.check_access())
        self.assertEqual(self.node.stops, 1)
        self.assertFalse(self.supervisor.running)

    def test_failed_start_not_marked_running(self):
        self.node.start = Mock(side_effect=RuntimeError("synthetic worker failure"))
        with self.assertRaises(RuntimeError):
            self.supervisor.start()
        self.assertFalse(self.supervisor.running)

    def test_stuck_legacy_uploader_blocks_stopped_state(self):
        self.supervisor.start()
        thread = Mock()
        thread.is_alive.return_value = True
        self.node.sync_thread = thread
        with self.assertRaises(UnsafeNodeTakeover):
            self.supervisor.stop()
        self.assertTrue(self.supervisor.running)
        thread.join.assert_called_once_with(timeout=10.0)

    def test_running_legacy_log_watcher_blocks_stopped_state(self):
        self.supervisor.start()
        watcher = Mock()
        watcher.is_running.return_value = True
        self.node.watcher = watcher
        with self.assertRaises(UnsafeNodeTakeover):
            self.supervisor.stop()
        self.assertTrue(self.supervisor.running)

    def test_failed_stop_never_claims_stopped(self):
        self.supervisor.start()
        self.node.stop = Mock(side_effect=RuntimeError("synthetic stop failure"))
        with self.assertRaises(RuntimeError):
            self.supervisor.stop()
        self.assertTrue(self.supervisor.running)


if __name__ == "__main__":
    unittest.main()
