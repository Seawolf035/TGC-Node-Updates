"""Offline per-account world collections and personal VRChat avatar/inbox test."""
import tempfile
from pathlib import Path
import unittest
from unittest.mock import patch
from tgc_cocompanion.desktop_collection_pages import WorldBookmarks
from tgc_cocompanion.vrchat_personal import PersonalVRChat,VRChatApiError

ME="usr_2a833076-03ac-4212-aeff-0708d8eab968"
OTHER="usr_b85631a8-5e23-4b88-a14b-4c8a2ae863c1"
WORLD="wrld_2a833076-03ac-4212-aeff-0708d8eab968"
AVATAR="avtr_2a833076-03ac-4212-aeff-0708d8eab968"
NOT="not_test1234"


class CollectionTests(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.path=Path(self.temp.name)

    def test_bookmarks_are_scoped_per_personal_account(self):
        store=WorldBookmarks(self.path)
        store.save(ME,WORLD,"My Test World")
        self.assertEqual(store.list(ME),[{"id":WORLD,"name":"My Test World"}])
        self.assertEqual(store.list(OTHER),[])
        self.assertEqual(WorldBookmarks(self.path).list(ME),store.list(ME))

    def test_bookmark_delete_does_not_modify_other_account(self):
        store=WorldBookmarks(self.path)
        store.save(ME,WORLD,"Me")
        store.save(OTHER,WORLD,"Other")
        store.remove(ME,WORLD)
        self.assertEqual(store.list(ME),[])
        self.assertEqual(len(store.list(OTHER)),1)

    def test_invalid_world_id_or_guest_disallowed(self):
        store=WorldBookmarks(self.path)
        with self.assertRaises(PermissionError):
            store.save("guest",WORLD,"No")
        with self.assertRaises(ValueError):
            store.save(ME,"wrld_wrong","No")
        with self.assertRaises(ValueError):
            store.save(ME,WORLD,"")

    def test_vrchat_avatar_mutation_routes_require_correct_id(self):
        calls=[]
        def transport(method,route,cookie,auth="",body=None):
            calls.append((method,route))
            if auth:return {"id":ME,"displayName":"Seawolf"},{"auth":"private"}
            if route.startswith("/avatars/favorites"):return [{"id":AVATAR}],{}
            return {"ok":True},{}
        with patch("tgc_cocompanion.vrchat_personal._encrypt_for_user",side_effect=lambda x:x.encode()):
            client=PersonalVRChat(self.path,transport)
            client.login("tester","password")
            self.assertEqual(client.avatar_favorites()[0]["id"],AVATAR)
            client.select_avatar(AVATAR)
            self.assertIn(("PUT","/avatars/"+AVATAR+"/select"),calls)
            with self.assertRaises(VRChatApiError):client.select_avatar("avtr_bad")

    def test_vrchat_inbox_mutations_validate_notification_id(self):
        calls=[]
        def transport(method,route,cookie,auth="",body=None):
            calls.append((method,route))
            if auth:return {"id":ME,"displayName":"Seawolf"},{"auth":"private"}
            return {"ok":True},{}
        with patch("tgc_cocompanion.vrchat_personal._encrypt_for_user",side_effect=lambda x:x.encode()):
            client=PersonalVRChat(self.path,transport)
            client.login("tester","password")
            client.notification_seen(NOT)
            client.dismiss_notification(NOT)
            self.assertIn(("POST","/notifications/"+NOT+"/see"),calls)
            self.assertIn(("DELETE","/notifications/"+NOT),calls)
            with self.assertRaises(VRChatApiError):client.dismiss_notification("../../danger")


if __name__=="__main__":
    unittest.main()
