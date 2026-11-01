"""Offline unit tests: never contact VRChat, TGC Relay or real OSC sockets."""
import struct
import unittest
from unittest.mock import MagicMock, patch

from tgc_cocompanion.permissions import AccessDenied, InstanceGate, VerifiedAccess
from tgc_cocompanion.osc import (
    OscError, LocalOscSender, avatar_parameter, chatbox, eye_height,
)
from tgc_cocompanion.sessions import SessionRouter, TargetUnclear, VRChatSession


class PermissionTests(unittest.TestCase):
    NOW = 1000.0

    @staticmethod
    def gate(limit=1, verified=True, expires=2000.0):
        return InstanceGate(
            lambda: VerifiedAccess("1478976686656847973", verified, limit, expires)
        )

    def test_missing_authority_denied(self):
        with self.assertRaises(AccessDenied):
            InstanceGate(lambda: None).reserve(0, now=self.NOW)

    def test_expired_denied(self):
        with self.assertRaises(AccessDenied):
            self.gate(expires=self.NOW).reserve(0, now=self.NOW)

    def test_unverified_denied(self):
        with self.assertRaises(AccessDenied):
            self.gate(verified=False).reserve(0, now=self.NOW)

    def test_invalid_or_excessive_grant_denied(self):
        for limit in (0, -1, 5, 100):
            with self.subTest(limit=limit), self.assertRaises(AccessDenied):
                self.gate(limit=limit).reserve(0, now=self.NOW)

    def test_invalid_discord_identity_denied(self):
        gate = InstanceGate(lambda: VerifiedAccess("not-an-id", True, 4, 2000))
        with self.assertRaises(AccessDenied):
            gate.reserve(0, now=self.NOW)

    def test_single_slot_default(self):
        gate = self.gate()
        claim = gate.reserve(0, now=self.NOW)
        self.assertEqual(gate.pending_count, 1)
        with self.assertRaises(AccessDenied):
            gate.reserve(0, now=self.NOW)
        gate.release(claim)
        self.assertEqual(gate.pending_count, 0)

    def test_four_slots_max_and_pending_claims(self):
        gate = self.gate(limit=4)
        claims = [gate.reserve(0, now=self.NOW) for _ in range(4)]
        with self.assertRaises(AccessDenied):
            gate.reserve(0, now=self.NOW)
        for claim in claims:
            gate.release(claim)

    def test_existing_count_and_pending_claims(self):
        gate = self.gate(limit=3)
        claim = gate.reserve(2, now=self.NOW)
        with self.assertRaises(AccessDenied):
            gate.reserve(2, now=self.NOW)
        gate.release(claim)
        with self.assertRaises(AccessDenied):
            gate.reserve(-1, now=self.NOW)

    def test_revocation_denies_new_reservations(self):
        access = {"value": VerifiedAccess("1478976686656847973", True, 4, 2000)}
        gate = InstanceGate(lambda: access["value"])
        claim = gate.reserve(0, now=self.NOW)
        access["value"] = VerifiedAccess("1478976686656847973", True, 1, 2000)
        with self.assertRaises(AccessDenied):
            gate.reserve(1, now=self.NOW)
        gate.release(claim)


class SessionTests(unittest.TestCase):
    def test_no_client_denied(self):
        with self.assertRaises(TargetUnclear):
            SessionRouter().target()

    def test_one_client_auto_targets(self):
        router = SessionRouter()
        router.replace([VRChatSession(9876, 9000, "vr")])
        self.assertEqual(router.target().osc_port, 9000)

    def test_multiple_clients_need_selection(self):
        router = SessionRouter()
        router.replace([VRChatSession(100, 9000), VRChatSession(101, 9001)])
        with self.assertRaises(TargetUnclear):
            router.target()
        self.assertEqual(router.target(101).osc_port, 9001)

    def test_stale_pid_denied(self):
        router = SessionRouter()
        router.replace([VRChatSession(100, 9000)])
        with self.assertRaises(TargetUnclear):
            router.target(101)

    def test_missing_port_denied(self):
        router = SessionRouter()
        router.replace([VRChatSession(100, None)])
        with self.assertRaises(TargetUnclear):
            router.target()

    def test_duplicate_pid_denied(self):
        router = SessionRouter()
        with self.assertRaises(ValueError):
            router.replace([VRChatSession(100, 9000), VRChatSession(100, 9001)])


class OscTests(unittest.TestCase):
    def test_height_packet_address_and_value(self):
        msg = eye_height(0.8)
        self.assertTrue(msg.startswith(b"/avatar/eyeheight\x00"))
        self.assertEqual(msg[-4:], struct.pack(">f", 0.8))

    def test_invalid_height_denied(self):
        for value in (float("nan"), float("inf"), -1, 0, 101):
            with self.subTest(value=value), self.assertRaises(OscError):
                eye_height(value)

    def test_chatbox_typetags(self):
        msg = chatbox("hello", send_immediately=True, sound=False)
        self.assertIn(b",sTF\x00", msg)
        self.assertTrue(msg.endswith(b"hello\x00\x00\x00"))

    def test_oversized_chatbox_denied(self):
        with self.assertRaises(OscError):
            chatbox("H" * 145)

    def test_parameter_types(self):
        self.assertIn(b",T\x00", avatar_parameter("Jump", True))
        self.assertEqual(avatar_parameter("Speed", 1)[-4:], struct.pack(">i", 1))
        self.assertEqual(avatar_parameter("Float", 0.75)[-4:], struct.pack(">f", .75))

    def test_invalid_parameter_denied(self):
        with self.assertRaises(OscError):
            avatar_parameter("../bad", True)
        with self.assertRaises(OscError):
            avatar_parameter("Big", 128)

    def test_sender_only_localhost(self):
        sock = MagicMock()
        sock.__enter__.return_value = sock
        with patch("tgc_cocompanion.osc.socket.socket", return_value=sock):
            LocalOscSender().send(b"test", port=9000)
        sock.sendto.assert_called_once_with(b"test", ("127.0.0.1", 9000))

    def test_sender_rejects_invalid_port(self):
        with self.assertRaises(OscError):
            LocalOscSender().send(b"test", port=0)


if __name__ == "__main__":
    unittest.main()
