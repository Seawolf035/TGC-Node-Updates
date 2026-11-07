"""Offline Mobile-parity favorite-friend index and opt-in pinned online alerts."""
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

from tgc_cocompanion.desktop_friend_favorites import (
    fetch_favorite_friend_ids,FavoriteFriendIndex,TTL_SECONDS)
from tgc_cocompanion.desktop_pinned_friend_alerts import PinnedFriendAlerts
from tgc_cocompanion.vrchat_personal import VRChatApiError

ME="usr_2a833076-03ac-4212-aeff-0708d8eab968"
A="usr_b85631a8-5e23-4b88-a14b-4c8a2ae863c1"
B="usr_8b44ca38-7411-428c-ae1f-65ad00b10350"


class FakeAccount:
    def __init__(self):
        self.user_id=ME
        self.signed_in=True
        self.fail=False
        self.calls=[]
    def get(self,path):
        self.calls.append(path)
        if path=="/favorites/groups/friend":
            return {"favoriteGroups":[{"name":"group_0"},{"name":"group_1"}]}
        if path.endswith("group_0"):
            return {"favorites":[{"favoriteId":A}]}
        if path.endswith("group_1"):
            if self.fail:raise VRChatApiError("VRChat 503 on group_1")
            return {"favorites":[{"user":{"id":B}},{"favoriteId":A}]}
        raise AssertionError("Unexpected endpoint "+path)


class FavoriteTests(unittest.TestCase):
    def setUp(self):
        p=patch.multiple("tgc_cocompanion.desktop_friend_favorites",
            _encrypt_for_user=lambda s:("DPAPI-"+s).encode(),
            _decrypt_for_user=lambda b:b.decode().removeprefix("DPAPI-"))
        p.start()
        self.addCleanup(p.stop)

    def test_exact_android_friend_group_endpoint_and_cached_ttl(self):
        with tempfile.TemporaryDirectory() as td:
            a=FakeAccount()
            idx=FavoriteFriendIndex(td)
            ids,changed=idx.refresh(a,at=1000)
            self.assertTrue(changed)
            self.assertEqual(ids,{A,B})
            self.assertEqual(a.calls,[
                "/favorites/groups/friend",
                "/favorites/groups/friend/group_0",
                "/favorites/groups/friend/group_1"])
            self.assertTrue(idx.fresh(ME,at=1000+TTL_SECONDS-1))
            self.assertFalse(idx.fresh(ME,at=1000+TTL_SECONDS))
            self.assertEqual(idx.refresh(a,at=1010),(frozenset({A,B}),False))
            self.assertEqual(len(a.calls),3)
            self.assertTrue(idx._path(ME).read_bytes().startswith(b"DPAPI-"))

    def test_failed_group_preserves_cached_complete_favorites(self):
        with tempfile.TemporaryDirectory() as td:
            a=FakeAccount()
            idx=FavoriteFriendIndex(td)
            idx.refresh(a,at=100)
            old=idx._path(ME).read_bytes()
            a.fail=True
            with self.assertRaisesRegex(VRChatApiError,"503"):
                idx.refresh(a,force=True,at=200)
            self.assertEqual(idx._path(ME).read_bytes(),old)
            self.assertEqual(idx.saved(ME),{A,B})

    def test_malformed_group_list_and_switched_account_rejected(self):
        class Fake:
            user_id=ME
            signed_in=True
            def get(self,path):
                if path=="/favorites/groups/friend":
                    return {"favoriteGroups":[{"name":"bad name!"}]}
                return {"invalid":"missing favoriteGroups on fallback"}
        with self.assertRaises(VRChatApiError):
            fetch_favorite_friend_ids(Fake())
        class Switch(FakeAccount):
            def get(self,path):
                value=super().get(path)
                self.user_id=B
                return value
        with tempfile.TemporaryDirectory() as td:
            idx=FavoriteFriendIndex(td)
            with self.assertRaises(PermissionError):
                idx.refresh(Switch(),at=1111)
            self.assertFalse(idx._path(ME).exists())


class AlertsTests(unittest.TestCase):
    def setUp(self):
        p=patch.multiple("tgc_cocompanion.desktop_pinned_friend_alerts",
            _encrypt_for_user=lambda s:("DPAPI-"+s).encode(),
            _decrypt_for_user=lambda b:b.decode().removeprefix("DPAPI-"))
        p.start()
        self.addCleanup(p.stop)

    def test_explicit_opt_in_and_first_observation_silent(self):
        with tempfile.TemporaryDirectory() as td:
            mgr=PinnedFriendAlerts(td)
            pin=[{"id":A,"name":"Friend"}]
            offline=[{"id":A,"displayName":"Friend","isOnline":False}]
            online=[{"id":A,"displayName":"Friend","isOnline":True}]
            self.assertFalse(mgr.enabled(ME))
            self.assertEqual(mgr.observe(ME,pin,online,at=100),())
            mgr.set_enabled(ME,True)
            self.assertEqual(mgr.observe(ME,pin,online,at=101),())
            self.assertEqual(mgr.observe(ME,pin,offline,at=102),())
            self.assertEqual(mgr.observe(ME,pin,online,at=103),
                             ({"id":A,"name":"Friend"},))
            self.assertEqual(mgr.observe(ME,pin,online,at=104),())
            self.assertEqual(mgr.last_check(ME),104)
            self.assertTrue(mgr._path(ME).read_bytes().startswith(b"DPAPI-"))
            mgr.set_enabled(ME,False)
            self.assertEqual(mgr.observe(ME,pin,offline,at=110),())
            self.assertFalse(mgr.enabled(ME))

    def test_missing_and_newly_pinned_do_not_create_false_alerts(self):
        with tempfile.TemporaryDirectory() as td:
            mgr=PinnedFriendAlerts(td)
            pin_a=[{"id":A,"name":"A"}]
            pin_b=[{"id":B,"name":"B"}]
            mgr.set_enabled(ME,True)
            mgr.observe(ME,pin_a,[{"id":A,"isOnline":False}],at=1)
            self.assertEqual(mgr.observe(ME,pin_a,[],at=2),())
            self.assertEqual(mgr.observe(ME,pin_a,[{"id":A,"isOnline":True}],at=3),())
            self.assertEqual(mgr.observe(ME,pin_a,[{"id":A,"isOnline":False}],at=4),())
            self.assertEqual(mgr.observe(ME,pin_a,[{"id":A,"isOnline":True}],at=5),
                             ({"id":A,"name":"A"},))
            self.assertEqual(mgr.observe(ME,pin_b,[{"id":B,"isOnline":True}],at=6),())
            self.assertEqual(mgr.observe(ME,pin_b,[{"id":B,"isOnline":False}],at=5),())
            self.assertEqual(mgr.observe(ME,pin_b,[{"id":B,"isOnline":True}],at=6),
                             ({"id":B,"name":"B"},))
            self.assertFalse(mgr.enabled(A))

    def test_corrupt_or_unwritable_alert_store_cannot_notify(self):
        with tempfile.TemporaryDirectory() as td:
            mgr=PinnedFriendAlerts(td)
            mgr._path(ME).write_bytes(b"corrupt-state")
            with self.assertRaisesRegex(ValueError,"unreadable"):
                mgr.observe(ME,[{"id":A}],[{"id":A,"isOnline":True}])
            self.assertEqual(mgr._path(ME).read_bytes(),b"corrupt-state")
            with self.assertRaises(PermissionError):
                mgr.set_enabled("wrong",True)


if __name__=="__main__":
    unittest.main()
