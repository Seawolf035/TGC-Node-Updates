"""DPAPI-scoped manual friend activity: no false first events or partial history."""
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

from tgc_cocompanion.desktop_friend_activity import (
    FriendActivityStore,detect_friend_changes,normalize_friends,MIN_POLL_SECONDS)

ACCOUNT="usr_2a833076-03ac-4212-aeff-0708d8eab968"
OTHER="usr_b85631a8-5e23-4b88-a14b-4c8a2ae863c1"
A={"id":ACCOUNT,"displayName":"A","isOnline":True,"location":"private",
   "status":"active","statusDescription":"hi"}
B={"id":OTHER,"displayName":"B","isOnline":False,"location":"unknown",
   "status":"offline"}


class ActivityModelTests(unittest.TestCase):
    def setUp(self):
        # Simulate the Windows-user DPAPI boundary without Windows.
        encrypt=lambda s: ("dpapi:"+s).encode("utf-8")
        decrypt=lambda b: b.decode("utf-8").removeprefix("dpapi:")
        p=patch.multiple("tgc_cocompanion.desktop_friend_activity",
                         _encrypt_for_user=encrypt,_decrypt_for_user=decrypt)
        p.start()
        self.addCleanup(p.stop)

    def test_first_snapshot_seeds_without_inventing_events(self):
        with tempfile.TemporaryDirectory() as td:
            store=FriendActivityStore(Path(td))
            self.assertEqual(store.observe(ACCOUNT,[A,B],now=120),0)
            self.assertEqual(store.events(ACCOUNT),())
            self.assertTrue(store._path(ACCOUNT).read_bytes().startswith(b"dpapi:"))
            self.assertEqual(store.observe(ACCOUNT,[A,B],now=180),0)

    def test_friends_changes_and_account_isolation(self):
        with tempfile.TemporaryDirectory() as td:
            store=FriendActivityStore(Path(td))
            store.observe(ACCOUNT,[A],now=100)
            changed={**A,"location":"wrld_2a833076-03ac-4212-aeff-0708d8eab968:1",
                     "isOnline":False,"statusDescription":"away"}
            self.assertEqual(store.observe(ACCOUNT,[changed,B],now=161),4)
            events=store.events(ACCOUNT)
            self.assertEqual([e["type"] for e in events],
                             ["offline","location","statusDescription","friend_added"])
            self.assertEqual(store.events(OTHER),())
            store.observe(ACCOUNT,[B],now=222)
            self.assertIn("friend_removed",[e["type"] for e in store.events(ACCOUNT)])
            self.assertEqual(len(store.events(ACCOUNT)),5)

    def test_minimum_one_minute_between_manual_captures(self):
        with tempfile.TemporaryDirectory() as td:
            store=FriendActivityStore(Path(td))
            store.observe(ACCOUNT,[A],now=1000)
            with self.assertRaisesRegex(ValueError,"once per minute"):
                store.observe(ACCOUNT,[B],now=1001)
            self.assertEqual(store.events(ACCOUNT),())
            store.observe(ACCOUNT,[B],now=1060)
            self.assertEqual({e["type"] for e in store.events(ACCOUNT)},
                             {"friend_added","friend_removed"})

    def test_corrupt_dpapi_does_not_overwrite_history(self):
        with tempfile.TemporaryDirectory() as td:
            store=FriendActivityStore(Path(td))
            store._path(ACCOUNT).write_bytes(b"dpapi:not-json")
            with self.assertRaisesRegex(ValueError,"unreadable"):
                store.observe(ACCOUNT,[A],now=1000)
            self.assertEqual(store._path(ACCOUNT).read_bytes(),b"dpapi:not-json")
            with self.assertRaises(PermissionError):
                store.events("fake")

    def test_invalid_friend_ids_ignored(self):
        data=normalize_friends([{"id":"invalid","displayName":"Bad"},A,A])
        self.assertEqual(len(data),1)
        self.assertIn(ACCOUNT,data)


if __name__=="__main__":
    unittest.main()
