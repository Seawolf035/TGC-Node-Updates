"""Offline writable personal VRChat API behavior, no Relay/real account."""
import tempfile
from pathlib import Path
import unittest
from unittest.mock import patch

from tgc_cocompanion.vrchat_personal import PersonalVRChat,VRChatApiError

SELF="usr_2a833076-03ac-4212-aeff-0708d8eab968"
OTHER="usr_b85631a8-5e23-4b88-a14b-4c8a2ae863c1"
WORLD="wrld_2a833076-03ac-4212-aeff-0708d8eab968"
GROUP="grp_2a833076-03ac-4212-aeff-0708d8eab968"


class ActionsTests(unittest.TestCase):
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        calls=self.calls=[]
        def call(method,route,cookie,authorization="",body=None):
            calls.append((method,route,cookie,authorization,body))
            if authorization:
                return {"id":SELF,"displayName":"Seawolf"},{"auth":"test_cookie"}
            if route.startswith("/profile/"):
                return {"displayName":"Profile","bio":"Public bio"},{}
            if route=="/auth/user":
                return {"id":SELF,"displayName":"Self"},{}
            if route.startswith("/users/") and method=="GET":
                return {"displayName":"Fallback","status":"active"},{}
            return {"ok":True},{}
        self.client=PersonalVRChat(Path(self.tmp.name),transport=call)
        self.enc=patch("tgc_cocompanion.vrchat_personal._encrypt_for_user",side_effect=lambda x:x.encode())
        self.enc.start();self.addCleanup(self.enc.stop)
        self.client.login("tester","password")

    def test_profile_prefers_modern_and_merges_fallback(self):
        data=self.client.profile(OTHER)
        self.assertEqual(data["displayName"],"Profile")
        self.assertEqual(data["status"],"active")

    def test_group_search_uses_vrchat_query_not_world_search_parameter(self):
        group={"id":GROUP,"name":"The Gamer Club","shortCode":"TGC"}
        with patch.object(self.client,"_transport",return_value=([group],{})) as transport:
            result=self.client.browse("search_groups",query="The Gamer Club")
            self.assertEqual(result,[group])
            self.assertEqual(transport.call_args.args[1],
                             "/groups?query=The+Gamer+Club&n=60&offset=0")
        with self.assertRaises(VRChatApiError):
            self.client.browse("search_groups",query="a")
        from tgc_cocompanion.desktop_explore import classify_result,merge_explore_results
        self.assertEqual(classify_result(group,"Groups")[0],"Group")
        self.assertEqual(merge_explore_results([("Groups",[group])])[0][3],GROUP)

    def test_fetch_world_and_group_detail(self):
        self.client.world(WORLD)
        self.client.group(GROUP)
        self.assertIn(("GET","/worlds/"+WORLD),[(c[0],c[1]) for c in self.calls])
        self.assertIn(("GET","/groups/"+GROUP),[(c[0],c[1]) for c in self.calls])

    def test_confirmable_friend_request_and_invite(self):
        self.client.send_friend_request(OTHER)
        self.client.request_invite(OTHER)
        self.assertIn(("POST","/user/"+OTHER+"/friendRequest"),[(c[0],c[1]) for c in self.calls])
        self.assertIn(("POST","/requestInvite/"+OTHER),[(c[0],c[1]) for c in self.calls])

    def test_own_account_cannot_request_friendship(self):
        with self.assertRaises(VRChatApiError):
            self.client.send_friend_request(SELF)
        with self.assertRaises(VRChatApiError):
            self.client.request_invite(SELF)

    def test_save_private_note_and_length_guard(self):
        self.client.save_note(OTHER,"Note goes only to VRChat")
        self.assertEqual(self.calls[-1][4]["targetUserId"],OTHER)
        with self.assertRaises(VRChatApiError):
            self.client.save_note(OTHER,"")

    def test_presence_updates_only_logged_in_personal_profile(self):
        self.client.update_presence("busy","Working on CoCompanion")
        self.assertEqual(self.calls[-1][0:2],("PUT","/users/"+SELF))
        self.assertEqual(self.calls[-1][4]["status"],"busy")
        with self.assertRaises(VRChatApiError):
            self.client.update_presence("admin","Not allowed")

    def test_wrong_id_cannot_inject_api_routes(self):
        for attack in ("usr_admin/../groups","https://evil","grp_nonexistent"):
            with self.assertRaises(VRChatApiError):
                self.client.profile(attack)

    def test_logout_blocks_mutations(self):
        self.client.logout()
        with self.assertRaises(VRChatApiError):
            self.client.send_friend_request(OTHER)


if __name__=="__main__":
    unittest.main()
