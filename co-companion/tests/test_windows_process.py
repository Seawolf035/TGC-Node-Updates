"""Windows adapter tests with fake processes and fake subprocess spawn."""
from pathlib import Path
from tempfile import TemporaryDirectory
import unittest
from unittest.mock import Mock

from tgc_cocompanion.windows_process import (
    WindowsVRChatProcessFinder, WindowsVRChatSpawner,
    ProcessDiscoveryError, process_with_marker,
)


class FakeProcess:
    def __init__(self, pid: int, name: str, args: list[str]):
        self.pid = pid
        self._name = name
        self._args = args

    def name(self):
        return self._name

    def cmdline(self):
        return self._args


class WindowsAdaptersTests(unittest.TestCase):
    MARKER = "--osc=9003:127.0.0.1:9103"

    def test_exact_marker_and_correct_process_name(self):
        processes = [
            FakeProcess(1, "launch.exe", ["launch.exe", self.MARKER]),
            FakeProcess(2, "VRChat.exe", ["VRChat.exe", "--osc=9003:127.0.0.1:91030"]),
            FakeProcess(3, "vrchat.exe", ["VRChat.exe", self.MARKER]),
        ]
        self.assertEqual(process_with_marker(processes, self.MARKER), 3)

    def test_missing_marker_is_not_a_match(self):
        p = FakeProcess(4, "VRChat.exe", ["VRChat.exe", "--no-vr"])
        self.assertIsNone(process_with_marker([p], self.MARKER))

    def test_duplicate_matching_processes_fail_closed(self):
        with self.assertRaises(ProcessDiscoveryError):
            process_with_marker([
                FakeProcess(1, "VRChat.exe", [self.MARKER]),
                FakeProcess(2, "VRChat.exe", [self.MARKER]),
            ], self.MARKER)

    def test_invalid_marker_refused(self):
        with self.assertRaises(ValueError):
            process_with_marker([], "hello")

    def test_psutil_like_disappeared_process_ignored(self):
        class Vanished:
            def name(self):
                raise OSError("Process gone")
        self.assertIsNone(process_with_marker([Vanished()], self.MARKER))

    def test_finder_uses_injected_process_iterator(self):
        finder = WindowsVRChatProcessFinder(
            process_iter=lambda: [FakeProcess(99, "VRChat.exe", [self.MARKER])]
        )
        self.assertEqual(finder(3, self.MARKER), 99)

    def test_finder_rejects_invalid_slot(self):
        with self.assertRaises(ValueError):
            WindowsVRChatProcessFinder(process_iter=lambda: [])(0, self.MARKER)

    def test_spawner_uses_no_shell_and_exact_exe(self):
        with TemporaryDirectory() as td:
            exe = Path(td) / "launch.exe"
            exe.touch()
            mocked = Mock()
            spawner = WindowsVRChatSpawner(popen=mocked)
            argv = (str(exe), "--osc=9004:127.0.0.1:9104", "--no-vr")
            spawner(argv)
            self.assertEqual(mocked.call_count, 1)
            self.assertEqual(mocked.call_args.args[0], argv)
            self.assertEqual(mocked.call_args.kwargs["cwd"], str(exe.parent))
            self.assertNotIn("shell", mocked.call_args.kwargs)

    def test_spawner_rejects_extra_args(self):
        with TemporaryDirectory() as td:
            exe = Path(td) / "launch.exe"
            exe.touch()
            mocked = Mock()
            with self.assertRaises(ValueError):
                WindowsVRChatSpawner(mocked)(
                    (str(exe), self.MARKER, "--no-vr", "--other-flag")
                )
            mocked.assert_not_called()

    def test_spawner_rejects_arbitrary_program(self):
        with TemporaryDirectory() as td:
            exe = Path(td) / "powershell.exe"
            exe.touch()
            mocked = Mock()
            with self.assertRaises(ValueError):
                WindowsVRChatSpawner(mocked)((str(exe), self.MARKER))
            mocked.assert_not_called()


if __name__ == "__main__":
    unittest.main()
