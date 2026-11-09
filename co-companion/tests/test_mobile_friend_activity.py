"""DPAPI-scoped manual friend activity: no false first events or partial history."""
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

from tgc_cocompanion.desktop_friend_activity import (
    FriendActivityStore,detect_friend_changes,normalize_friends,MIN_POLL_SECONDS,
    filter_activity_events, ACTIVITY_KINDS)

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

    def test_local_history_filter_respects_event_type_and_newest_first(self):
        events=(
            {"time":100,"userId":ACCOUNT,"name":"Alpha","type":"friend_added","status":"online"},
            {"time":101,"userId":OTHER,"name":"Beta","type":"location","location":"wrld_example"},
            {"time":102,"userId":ACCOUNT,"name":"Alpha","type":"offline","previous":"active"},
            {"time":103,"userId":OTHER,"name":"Beta","type":"statusDescription","previous":"BRB"},
        )
        self.assertEqual([r["time"] for r in filter_activity_events(events)], [103,102,101,100])
        self.assertEqual([r["time"] for r in filter_activity_events(
            events,kind="Online/offline")],[102])
        self.assertEqual([r["time"] for r in filter_activity_events(
            events,kind="World/location changes")],[101])
        self.assertEqual([r["time"] for r in filter_activity_events(
            events,kind="Status changes")],[103])
        self.assertEqual([r["time"] for r in filter_activity_events(
            events,kind="Friends added/removed")],[100])

    def test_local_history_search_matches_name_id_and_old_values(self):
        events=(
            {"type":"location","userId":ACCOUNT,"name":"Blue Shark",
             "location":"private","previous":"wrld_old"},
            {"type":"offline","userId":OTHER,"name":"Seawolf Friend",
             "status":"offline","previous":"active"},
        )
        self.assertEqual(len(filter_activity_events(events,"shark")),1)
        self.assertEqual(filter_activity_events(events,"WRLD_OLD")[0]["userId"],ACCOUNT)
        self.assertEqual(filter_activity_events(events,"seawolf friend")[0]["userId"],OTHER)
        self.assertEqual(len(filter_activity_events(events,"___not_found___")),0)
        self.assertEqual(len(ACTIVITY_KINDS),5)
        with self.assertRaises(ValueError):
            filter_activity_events(events,kind="Admin override")

    def test_filtered_history_never_mutates_saved_events(self):
        events=[{"type":"online","name":"A","userId":ACCOUNT},
                {"type":"offline","name":"B","userId":OTHER}]
        before=[dict(x) for x in events]
        result=filter_activity_events(events,"a")
        self.assertEqual(len(result),1)
        self.assertEqual(events,before)

    def test_invalid_friend_ids_ignored(self):
        data=normalize_friends([{"id":"invalid","displayName":"Bad"},A,A])
        self.assertEqual(len(data),1)
        self.assertIn(ACCOUNT,data)


if __name__=="__main__":
    unittest.main()
