"""Native Windows pinned notifications: opt-in and no background service."""
import ctypes
from pathlib import Path
import tempfile
import unittest
from types import SimpleNamespace
from unittest.mock import patch

from tgc_cocompanion import desktop_windows_notifications as native
from tgc_cocompanion.desktop_pinned_friend_alerts import PinnedFriendAlerts

USER="usr_2a833076-03ac-4212-aeff-0708d8eab968"
OTHER="usr_b85631a8-5e23-4b88-a14b-4c8a2ae863c1"


class WindowsNotificationTests(unittest.TestCase):
    def test_windows_api_struct_has_explicit_size_unicode_capacity(self):
        record=native.NOTIFYICONDATAW()
        record.cbSize=ctypes.sizeof(native.NOTIFYICONDATAW)
        self.assertEqual(record.cbSize,ctypes.sizeof(native.NOTIFYICONDATAW))
        self.assertTrue(hasattr(record,"szInfoTitle"))
        self.assertTrue(hasattr(record,"szInfo"))
        self.assertEqual(native.safe_label("A\x00\nB"*50,70),
                         ("A B"*50)[:70])
        self.assertLessEqual(len(native.safe_label("x"*900,70)),70)

    def test_in_app_does_not_call_win32_when_not_windows(self):
        with patch.object(native,"os",SimpleNamespace(name="posix")):
            notifier=native.WindowsFriendNotifier(SimpleNamespace(winfo_id=lambda:123))
            self.assertFalse(notifier.show("Test Friend"))
            self.assertFalse(notifier._registered)
            notifier.close()

    def test_windows_balloon_register_modify_delete_lifecycle(self):
        root=SimpleNamespace(winfo_id=lambda:9876)
        notify=native.WindowsFriendNotifier(root)
        sent=[]
        def shell(command,data):
            sent.append((command,data.hWnd,data.uFlags,data.szInfo))
            return True
        notify._shell=shell
        fake_icon=SimpleNamespace(LoadIconW=lambda _a,_b:456)
        # Native Windows ctypes loader is mocked so this test works on Linux.
        with patch.object(native,"os",SimpleNamespace(name="nt")),patch.object(
                native.ctypes,"windll",SimpleNamespace(user32=fake_icon),create=True):
            self.assertTrue(notify.show("Ocean Wolf"))
            self.assertTrue(notify.show("Second"))
            self.assertEqual([e[0] for e in sent],
                             [native.NIM_ADD,native.NIM_MODIFY,native.NIM_MODIFY])
            self.assertIn("Ocean Wolf",sent[1][3])
            notify.close()
            self.assertEqual(sent[-1][0],native.NIM_DELETE)
            self.assertFalse(notify._registered)

    def test_failed_registration_does_not_create_icon(self):
        notify=native.WindowsFriendNotifier(SimpleNamespace(winfo_id=lambda:1234))
        notify._shell=lambda _command,_record:False
        fake_icon=SimpleNamespace(LoadIconW=lambda _a,_b:456)
        with patch.object(native,"os",SimpleNamespace(name="nt")),patch.object(
                native.ctypes,"windll",SimpleNamespace(user32=fake_icon),create=True):
            self.assertFalse(notify.show("Test"))
            self.assertFalse(notify._registered)


class AlertPreferencesTests(unittest.TestCase):
    def setUp(self):
        crypt=patch.multiple("tgc_cocompanion.desktop_pinned_friend_alerts",
            _encrypt_for_user=lambda s:("DPAPI:"+s).encode(),
            _decrypt_for_user=lambda b:b.decode().removeprefix("DPAPI:"))
        crypt.start()
        self.addCleanup(crypt.stop)

    def test_windows_is_separate_opt_in_and_account_scoped(self):
        with tempfile.TemporaryDirectory() as td:
            alert=PinnedFriendAlerts(Path(td))
            self.assertFalse(alert.enabled(USER))
            self.assertFalse(alert.windows_notifications(USER))
            alert.set_windows_notifications(USER,True)
            self.assertTrue(alert.windows_notifications(USER))
            self.assertFalse(alert.enabled(USER))
            alert.set_enabled(USER,True)
            self.assertTrue(alert.windows_notifications(USER))
            self.assertTrue(alert.enabled(USER))
            alert.set_enabled(USER,False)
            self.assertTrue(alert.windows_notifications(USER))
            self.assertFalse(alert.enabled(USER))
            self.assertFalse(alert.windows_notifications(OTHER))
            again=PinnedFriendAlerts(Path(td))
            self.assertTrue(again.windows_notifications(USER))
            self.assertFalse(again.enabled(USER))

    def test_bad_preference_type_and_corrupt_store_fail_closed(self):
        with tempfile.TemporaryDirectory() as td:
            alert=PinnedFriendAlerts(Path(td))
            with self.assertRaises(ValueError):
                alert.set_windows_notifications(USER,"yes")
            with self.assertRaises(PermissionError):
                alert.set_windows_notifications("invalid",True)
            alert._path(USER).write_bytes(b"bad")
            with self.assertRaises(ValueError):
                alert.set_windows_notifications(USER,True)
            self.assertEqual(alert._path(USER).read_bytes(),b"bad")


if __name__=="__main__":
    unittest.main()
