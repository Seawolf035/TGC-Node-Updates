"""Mobile Friends Hub parity, without network, uploads or VRChat mutations."""
import json
from pathlib import Path
import tempfile
import unittest

from tgc_cocompanion.desktop_social_shortcuts import SocialShortcuts, GROUP_ID
from tgc_cocompanion.desktop_friend_ordering import (
    FAVORITES,UNKNOWN,OFFLINE,section,group_friends)
from tgc_cocompanion.desktop_friend_hub import (
    complete_friend_list,group_shortcuts)

A="usr_2a833076-03ac-4212-aeff-0708d8eab968"
B="usr_b85631a8-5e23-4b88-a14b-4c8a2ae863c1"
C="usr_8b44ca38-7411-428c-ae1f-65ad00b10350"
D="usr_3b09e31e-232a-4b7c-81a2-02db7cfa7984"
GROUP="grp_2a833076-03ac-4212-aeff-0708d8eab968"


class ShortcutsTests(unittest.TestCase):
    def test_pins_are_per_personal_account_and_orderable(self):
        with tempfile.TemporaryDirectory() as td:
            s=SocialShortcuts(Path(td))
            s.set_pinned(A,"friends",B,"Beta")
            s.set_pinned(A,"friends",C,"Charlie")
            self.assertEqual([r["id"] for r in s.list(A,"friends")],[C,B])
            s.move_friend(A,B,-1)
            self.assertEqual([r["id"] for r in s.list(A,"friends")],[B,C])
            s.set_pinned(A,"friends",C,"changed name")
            self.assertEqual([r["id"] for r in s.list(A,"friends")],[C,B])
            self.assertEqual(s.list(A,"friends")[0]["name"],"changed name")
            self.assertEqual(s.list(B,"friends"),[])
            self.assertEqual(SocialShortcuts(Path(td)).list(A,"friends"),s.list(A,"friends"))
            s.set_pinned(A,"friends",B,"",False)
            self.assertEqual([r["id"] for r in s.list(A,"friends")],[C])
            with self.assertRaises(ValueError):
                s.move_friend(A,C,2)

    def test_max_pins_and_group_limits(self):
        with tempfile.TemporaryDirectory() as td:
            s=SocialShortcuts(Path(td))
            import uuid
            for i in range(12):
                s.set_pinned(A,"friends","usr_"+str(uuid.UUID(int=i+1)),"Friend")
            with self.assertRaisesRegex(ValueError,"12"):
                s.set_pinned(A,"friends","usr_"+str(uuid.UUID(int=30)),"Extra")
            for i in range(16):
                s.set_pinned(A,"groups","grp_"+str(uuid.UUID(int=i+1)),"Group")
            with self.assertRaisesRegex(ValueError,"16"):
                s.set_pinned(A,"groups","grp_"+str(uuid.UUID(int=30)),"Extra")
            with self.assertRaises(PermissionError):
                s.set_pinned("unknown","friends",B,"No")
            with self.assertRaises(ValueError):
                s.set_pinned(A,"groups",B,"Not a group")
            text=s.path.read_text(encoding="utf-8")
            self.assertNotIn("token",text)
            self.assertNotIn("staff_rank",text)
            self.assertNotIn("auth",text.lower())

    def test_malformed_file_cannot_create_phantom_accounts_or_pins(self):
        with tempfile.TemporaryDirectory() as td:
            s=SocialShortcuts(Path(td))
            s.path.write_text("{broken",encoding="utf-8")
            self.assertEqual(s.list(A,"friends"),[])
            s.path.write_text(json.dumps({"schema":1,"accounts":{A:{"friends":[
                {"id":B,"name":"valid"},{"id":B,"name":"duplicate"},
                {"id":"some-invalid-id","name":"forged"}]}}}),encoding="utf-8")
            self.assertEqual(s.list(A,"friends"),[{"id":B,"name":"valid"}])


class FriendOrderingTests(unittest.TestCase):
    def test_same_section_order_as_mobile(self):
        location="wrld_2a833076-03ac-4212-aeff-0708d8eab968:12345"
        friends=[
            {"id":A,"displayName":"Fav","isOnline":True,"location":"private"},
            {"id":B,"displayName":"Shared Two","isOnline":True,"location":location},
            {"id":C,"displayName":"Shared One","isOnline":True,"location":location},
            {"id":D,"displayName":"Hidden","isOnline":True,"location":"private"},
            {"id":"usr_03b56225-dc3f-43a0-b5bc-0251d7190ca2",
             "displayName":"Offline","isOnline":False,"location":location},
        ]
        categories=group_friends(friends,{A})
        self.assertEqual([x[0] for x in categories],[FAVORITES,location,UNKNOWN,OFFLINE])
        self.assertEqual([x["displayName"] for x in categories[1][2]],
                         ["Shared One","Shared Two"])
        self.assertIn("2 friends",categories[1][1])

    def test_unknown_offline_and_online_boolean(self):
        self.assertEqual(section({"id":A,"isOnline":False,"location":"private"}),OFFLINE)
        self.assertEqual(section({"id":A,"online":True,"location":"private"}),UNKNOWN)


class FullFriendSnapshotTests(unittest.TestCase):
    def test_complete_online_offline_and_dedup(self):
        class Fake:
            calls=[]
            def browse(self,category,offset=0):
                self.calls.append((category,offset))
                return ([{"id":A,"displayName":"A"},
                         {"id":B,"displayName":"B"}] if category=="online"
                        else [{"id":A,"displayName":"A updated"},
                              {"id":C,"displayName":"C"}])
        fake=Fake()
        rows=complete_friend_list(fake)
        self.assertEqual(len(rows),3)
        self.assertEqual({r["id"] for r in rows},{A,B,C})
        self.assertEqual(fake.calls,[("online",0),("offline",0)])

    def test_failed_second_category_never_returns_partial_snapshot(self):
        class Fake:
            def browse(self,category,offset=0):
                if category=="offline":raise RuntimeError("429 VRChat rate limit")
                return [{"id":A,"displayName":"Partial"}]
        with self.assertRaisesRegex(RuntimeError,"429"):
            complete_friend_list(Fake())

    def test_groups_support_nested_and_groupid_shapes(self):
        result=group_shortcuts([
            {"id":GROUP,"name":"TGC"},
            {"group":{"id":GROUP,"name":"duplicate"}},
            {"groupId":"grp_b85631a8-5e23-4b88-a14b-4c8a2ae863c1","name":"Other"},
            {"id":"not-a-real-group","name":"bad"}])
        self.assertEqual(len(result),2)
        self.assertEqual(result[0]["name"],"TGC")


if __name__=="__main__":
    unittest.main()
