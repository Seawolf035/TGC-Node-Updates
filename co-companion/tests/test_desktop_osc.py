"""Offline Windows OSC studio tests: all outgoing UDP is mocked."""
from pathlib import Path
import tempfile
import unittest
from unittest.mock import Mock

from tgc_cocompanion.desktop_osc_tools import PersonalOscPresets, WindowsOscControls
from tgc_cocompanion.osc import OscError
from tgc_cocompanion.sessions import SessionRouter, VRChatSession

USER="usr_2a833076-03ac-4212-aeff-0708d8eab968"
OTHER="usr_b85631a8-5e23-4b88-a14b-4c8a2ae863c1"

class DesktopOscTests(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.store=PersonalOscPresets(Path(self.temp.name))
        self.router=SessionRouter()
        self.router.replace([VRChatSession(100,9001),VRChatSession(101,9002)])
        self.sender=Mock()
        self.now=[200.0]
        self.ok=[True]
        self.ctrl=WindowsOscControls(
            self.router,lambda:self.ok[0],sender=self.sender,
            clock=lambda:self.now[0])

    def test_account_isolation_for_saved_phrases(self):
        self.store.save(USER,"Greeting","Hello VRChat")
        self.assertEqual(self.store.list(USER),(("Greeting","Hello VRChat"),))
        self.assertEqual(self.store.list(OTHER),())
        self.store.save(OTHER,"Greeting","Different")
        self.assertEqual(self.store.list(USER)[0][1],"Hello VRChat")

    def test_invalid_preset_rejected(self):
        with self.assertRaises(PermissionError):
            self.store.save("not a user","Label","hello")
        with self.assertRaises(ValueError):
            self.store.save(USER,"","hello")
        with self.assertRaises(OscError):
            self.store.save(USER,"x","X"*145)

    def test_chat_to_explicit_vrchat(self):
        port=self.ctrl.send_chat(101,"Hello")
        self.assertEqual(port,9002)
        self.assertEqual(self.sender.send.call_args.kwargs["port"],9002)

    def test_chat_rate_limit(self):
        self.ctrl.send_chat(100,"first")
        with self.assertRaises(OscError):
            self.ctrl.send_chat(100,"second")
        self.now[0]+=2
        self.ctrl.send_chat(100,"second")
        self.assertEqual(self.sender.send.call_count,2)

    def test_permission_revocation_blocks_new_chat(self):
        self.ok[0]=False
        with self.assertRaises(PermissionError):
            self.ctrl.send_chat(100,"No")
        self.sender.send.assert_not_called()

    def test_height_presets_and_custom(self):
        self.ctrl.send_height(100,0.8)
        self.ctrl.send_height(101,2.0)
        self.assertEqual(self.sender.send.call_args.kwargs["port"],9002)
        with self.assertRaises(OscError):
            self.ctrl.send_height(100,float("nan"))

    def test_avatar_parameter_sender(self):
        self.ctrl.send_parameter(101,"Blink",True)
        self.assertIn(b"/avatar/parameters/Blink",self.sender.send.call_args.args[0])

    def test_voice_release_after_revocation(self):
        self.ctrl.set_voice(100,True)
        self.ok[0]=False
        self.ctrl.release_voice()
        self.assertEqual(self.sender.send.call_count,2)
        self.assertEqual(self.sender.send.call_args.kwargs["port"],9001)
        self.assertIn(b"/input/Voice",self.sender.send.call_args.args[0])

    def test_voice_release_after_process_disappears(self):
        self.ctrl.set_voice(101,True)
        self.router.replace([])
        self.ctrl.shutdown()
        self.assertEqual(self.sender.send.call_args.kwargs["port"],9002)

    def test_rotation_stops_when_staff_access_expires(self):
        self.ctrl.set_rotation(100,["Hello","World"],interval_seconds=5)
        self.ctrl.rotate_tick()
        self.assertTrue(self.ctrl.rotating)
        self.ok[0]=False
        self.assertFalse(self.ctrl.rotate_tick())
        self.assertFalse(self.ctrl.rotating)

    def test_rotation_requires_valid_target_and_interval(self):
        with self.assertRaises(ValueError):
            self.ctrl.set_rotation(100,["hello"],interval_seconds=1)
        with self.assertRaises(Exception):
            self.ctrl.set_rotation(999,["Hello"],interval_seconds=5)

if __name__=="__main__":
    unittest.main()
