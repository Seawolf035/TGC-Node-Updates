"""Offline integration tests for preserved Staff Launcher --osc slot behavior.

Never invokes launch.exe, VRChat, TGC Relay or real system process discovery.
"""
from pathlib import Path
from tempfile import TemporaryDirectory
import unittest

from tgc_cocompanion.launch_controller import (
    build_launch_spec, ManagedVRChatLauncher, LaunchDenied,
)
from tgc_cocompanion.permissions import AccessDenied, VerifiedAccess
from tgc_cocompanion.sessions import TargetUnclear


class LaunchControllerTests(unittest.TestCase):
    NOW = 1000.0

    def setUp(self):
        self.temp = TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.exe = Path(self.temp.name) / "launch.exe"
        self.exe.write_bytes(b"synthetic placeholder; never executed")
        self.allowed = {"staff": VerifiedAccess("123456789012345678", True, 1, 2000),
                        "terms": True}
        self.observed = {}
        self.spawned = []
        self.clock = [self.NOW]
        self.controller = self.make()

    def make(self, vr_slot=1, spawn=None):
        return ManagedVRChatLauncher(
            lambda: self.allowed["staff"],
            lambda: self.allowed["terms"],
            (lambda args: self.spawned.append(args)) if spawn is None else spawn,
            lambda slot, marker: self.observed.get(slot),
            executable=self.exe,
            vr_slot=vr_slot,
            clock=lambda: self.clock[0],
        )

    def test_legacy_osc_args_and_modes(self):
        spec = build_launch_spec(1, executable=self.exe, vr_slot=1)
        self.assertEqual(spec.argv, (str(self.exe), "--osc=9001:127.0.0.1:9101"))
        spec = build_launch_spec(4, executable=self.exe, vr_slot=1)
        self.assertEqual(spec.argv, (str(self.exe), "--osc=9004:127.0.0.1:9104", "--no-vr"))

    def test_reject_wrong_executable(self):
        path = Path(self.temp.name) / "cmd.exe"
        path.write_bytes(b"x")
        with self.assertRaises(LaunchDenied):
            build_launch_spec(1, executable=path, vr_slot=1)

    def test_reject_invalid_ports_and_slot(self):
        for s in (0, 5, -1, True):
            with self.subTest(slot=s), self.assertRaises(ValueError):
                build_launch_spec(s, executable=self.exe)
        with self.assertRaises(ValueError):
            build_launch_spec(1, executable=self.exe, base_in=9000, base_out=9000)
        with self.assertRaises(ValueError):
            build_launch_spec(4, executable=self.exe, base_in=65535)

    def test_no_grant_denied_before_spawn(self):
        self.allowed["staff"] = None
        with self.assertRaises(AccessDenied):
            self.controller.launch(1)
        self.assertEqual(self.spawned, [])

    def test_expired_grant_denied(self):
        self.allowed["staff"] = VerifiedAccess("123456789012345678", True, 4, 999)
        with self.assertRaises(AccessDenied):
            self.controller.launch(1)
        self.assertEqual(self.spawned, [])

    def test_terms_required(self):
        self.allowed["terms"] = False
        with self.assertRaises(LaunchDenied):
            self.controller.launch(1)
        self.assertEqual(self.spawned, [])

    def test_default_one_slot_with_in_flight_reservation(self):
        self.controller.launch(1)
        with self.assertRaises(LaunchDenied):
            self.controller.launch(1)
        with self.assertRaises(AccessDenied):
            self.controller.launch(2)
        self.assertEqual(len(self.spawned), 1)

    def test_must_discover_actual_vrchat_pid_not_spawn_process(self):
        self.controller.launch(1)
        self.assertEqual(self.controller.statuses()[0].state, "launching")
        self.assertEqual(self.controller._gate.pending_count, 1)
        self.observed[1] = 4321
        status = self.controller.refresh()
        self.assertEqual(status[0].pid, 4321)
        self.assertEqual(self.controller._gate.pending_count, 0)
        self.assertEqual(self.controller.router.target().osc_port, 9001)
        with self.assertRaises(LaunchDenied):
            self.controller.launch(1)

    def test_four_slots_allowed_by_owner_grant(self):
        self.allowed["staff"] = VerifiedAccess("123456789012345678", True, 4, 2000)
        for s in range(1, 5):
            spec = self.controller.launch(s)
            self.assertEqual(spec.mode, "vr" if s == 1 else "desktop")
        self.assertEqual(len(self.spawned), 4)
        self.assertEqual(self.controller._gate.pending_count, 4)
        with self.assertRaises(LaunchDenied):
            self.controller.launch(4)

    def test_revoke_blocks_new_launch_but_keeps_running(self):
        self.allowed["staff"] = VerifiedAccess("123456789012345678", True, 4, 2000)
        self.controller.launch(1)
        self.observed[1] = 701
        self.controller.refresh()
        self.allowed["staff"] = VerifiedAccess("123456789012345678", True, 1, 2000)
        with self.assertRaises(AccessDenied):
            self.controller.launch(2)
        self.assertEqual(self.controller.statuses()[0].pid, 701)

    def test_external_running_slot_consumes_quota(self):
        self.observed[3] = 9901
        with self.assertRaises(AccessDenied):
            self.controller.launch(1)
        self.assertEqual(self.spawned, [])

    def test_no_auto_kill_on_close(self):
        self.controller.launch(1)
        self.observed[1] = 900
        self.controller.refresh()
        self.controller.close()
        self.assertEqual(self.observed[1], 900)
        self.assertEqual(self.controller.statuses()[0].pid, 900)

    def test_two_running_sessions_demand_explicit_osc_selection(self):
        self.allowed["staff"] = VerifiedAccess("123456789012345678", True, 4, 2000)
        self.observed.update({1: 101, 2: 102})
        self.controller.refresh()
        with self.assertRaises(TargetUnclear):
            self.controller.router.target()
        self.assertEqual(self.controller.router.target(102).osc_port, 9002)

    def test_spawn_failure_releases_slot(self):
        fail = self.make(spawn=lambda argv: (_ for _ in ()).throw(OSError("synthetic failure")))
        with self.assertRaises(OSError):
            fail.launch(1)
        self.assertEqual(fail._gate.pending_count, 0)
        self.assertEqual(len(fail._pending), 0)

    def test_unresolved_spawn_stays_reserved_until_discovered(self):
        self.controller.launch(1)
        self.clock[0] += 600
        with self.assertRaises(AccessDenied):
            self.controller.launch(2)
        self.assertEqual(self.controller.statuses()[0].state, "launching")

    def test_duplicate_pid_process_finder_rejected(self):
        self.observed.update({1: 1, 2: 1})
        with self.assertRaises(RuntimeError):
            self.controller.refresh()

    def test_no_vr_selected_defaults_to_desktop(self):
        ctrl = self.make(vr_slot=0)
        spec = ctrl.launch(1)
        self.assertEqual(spec.mode, "desktop")
        self.assertIn("--no-vr", spec.argv)


if __name__ == "__main__":
    unittest.main()
