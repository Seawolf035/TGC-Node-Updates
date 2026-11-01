"""Offline remote OSC bridge tests; all outgoing UDP is mocked."""
import unittest
from unittest.mock import MagicMock

from tgc_cocompanion.pcvr_bridge import (
    OscRequest, PairedDevice, PcvrOscBridge, RemoteCommandDenied,
)
from tgc_cocompanion.permissions import VerifiedAccess
from tgc_cocompanion.sessions import SessionRouter, VRChatSession, TargetUnclear


class PcvrOscBridgeTests(unittest.TestCase):
    NOW = 5000.0

    def setUp(self):
        self.status = {
            "grant": VerifiedAccess("123456789012345678", True, 1, 6000),
            "terms": True,
            "pcvr": True,
            "device": PairedDevice("quest_device_01", "123456789012345678", 6000),
        }
        self.sessions = SessionRouter()
        self.sessions.replace([
            VRChatSession(201, 9000, "vr"),
            VRChatSession(202, 9001, "desktop"),
        ])
        self.sent = MagicMock()
        self.now = [self.NOW]
        self.bridge = PcvrOscBridge(
            lambda: self.status["grant"],
            lambda: self.status["terms"],
            lambda: self.status["device"],
            lambda: self.status["pcvr"],
            self.sessions,
            sender=self.sent,
            clock=lambda: self.now[0],
        )

    def command(self, seq=1, action="chatbox", target=201, value="Hello", param=None, at=None):
        return OscRequest(action=action, target_pid=target, sequence=seq,
                          sent_at=self.NOW if at is None else at,
                          value=value, parameter=param)

    def test_paired_chatbox_sent_only_to_selected_local_port(self):
        self.bridge.dispatch(self.command())
        self.sent.send.assert_called_once()
        args, kwargs = self.sent.send.call_args
        self.assertEqual(kwargs["port"], 9000)
        self.assertIn(b"/chatbox/input", args[0])

    def test_second_instance_routes_to_second_port(self):
        self.bridge.dispatch(self.command(target=202, action="eye_height", value=0.8))
        self.assertEqual(self.sent.send.call_args.kwargs["port"], 9001)

    def test_unverified_staff_denied(self):
        self.status["grant"] = None
        with self.assertRaises(RemoteCommandDenied):
            self.bridge.dispatch(self.command())
        self.sent.send.assert_not_called()

    def test_staff_revoked_denied(self):
        self.status["grant"] = VerifiedAccess("123456789012345678", False, 1, 6000)
        with self.assertRaises(RemoteCommandDenied):
            self.bridge.dispatch(self.command())

    def test_invalid_slot_grant_denied(self):
        self.status["grant"] = VerifiedAccess("123456789012345678", True, 5, 6000)
        with self.assertRaises(RemoteCommandDenied):
            self.bridge.dispatch(self.command())

    def test_missing_terms_denied(self):
        self.status["terms"] = False
        with self.assertRaises(RemoteCommandDenied):
            self.bridge.dispatch(self.command())

    def test_standalone_quest_mode_denied(self):
        self.status["pcvr"] = False
        with self.assertRaises(RemoteCommandDenied):
            self.bridge.dispatch(self.command())

    def test_device_for_another_staff_denied(self):
        self.status["device"] = PairedDevice("quest_device_01", "99999999999", 6000)
        with self.assertRaises(RemoteCommandDenied):
            self.bridge.dispatch(self.command())

    def test_device_expired_denied(self):
        self.status["device"] = PairedDevice("quest_device_01", "123456789012345678", 5000)
        with self.assertRaises(RemoteCommandDenied):
            self.bridge.dispatch(self.command())

    def test_missing_pairing_denied(self):
        self.status["device"] = None
        with self.assertRaises(RemoteCommandDenied):
            self.bridge.dispatch(self.command())

    def test_replay_and_out_of_order_denied(self):
        self.bridge.dispatch(self.command())
        for seq in (1, 0):
            with self.assertRaises(RemoteCommandDenied):
                self.bridge.dispatch(self.command(seq=seq, action="eye_height", value=0.8))
        self.assertEqual(self.sent.send.call_count, 1)

    def test_expired_message_denied(self):
        with self.assertRaises(RemoteCommandDenied):
            self.bridge.dispatch(self.command(at=self.NOW - 31))

    def test_unknown_process_denied(self):
        with self.assertRaises(TargetUnclear):
            self.bridge.dispatch(self.command(target=9000))
        self.sent.send.assert_not_called()

    def test_no_automatic_ambiguous_process(self):
        with self.assertRaises(RemoteCommandDenied):
            self.bridge.dispatch(self.command(target=None))

    def test_unsupported_raw_osc_denied(self):
        with self.assertRaises(RemoteCommandDenied):
            self.bridge.dispatch(self.command(action="raw_packet", value="/chatbox/input"))
        self.sent.send.assert_not_called()

    def test_chatbox_rate_limit(self):
        self.bridge.dispatch(self.command())
        self.now[0] += 1
        with self.assertRaises(RemoteCommandDenied):
            self.bridge.dispatch(self.command(seq=2, at=self.now[0]))
        self.now[0] += 5
        self.bridge.dispatch(self.command(seq=2, at=self.now[0]))
        self.assertEqual(self.sent.send.call_count, 2)

    def test_failed_send_not_consumed(self):
        self.sent.send.side_effect = OSError("offline")
        with self.assertRaises(OSError):
            self.bridge.dispatch(self.command(action="eye_height", value=0.8))
        self.sent.send.side_effect = None
        self.bridge.dispatch(self.command(action="eye_height", value=0.8))
        self.assertEqual(self.sent.send.call_count, 2)

    def test_parameter_validation(self):
        with self.assertRaises(ValueError):
            self.bridge.dispatch(self.command(action="avatar_parameter",
                                              value=1000, param="Height"))
        self.sent.send.assert_not_called()

    def test_unknown_identity_and_pairing_provider_failure_denied(self):
        self.status["device"] = "untrusted"
        with self.assertRaises(RemoteCommandDenied):
            self.bridge.dispatch(self.command())


if __name__ == "__main__":
    unittest.main()
