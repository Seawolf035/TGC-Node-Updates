"""Offline tests for personal VRChat session and staff collections: no network."""
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

from tgc_cocompanion.vrchat_personal import (
    PersonalVRChat, VRChatApiError, _cookies_from_headers, _valid_route,
)
from tgc_cocompanion.staffdex_desktop import (
    parse_roster, DexSnapshot, StaffDex, GM, StaffEntry,
)

USER = "usr_2a833076-03ac-4212-aeff-0708d8eab968"
FRIEND = "usr_b85631a8-5e23-4b88-a14b-4c8a2ae863c1"
DISCORD = "123456789012345678"


class FakeTransport:
    def __init__(self):
        self.calls = []
        self.needs2fa = False
        self.pages = {}
        self.invalid = False

    def __call__(self, method, route, cookie, authorization="", body=None):
        self.calls.append((method,route,cookie,authorization,body))
        if route == "/auth/user" and authorization:
            if self.needs2fa:
                return {"requiresTwoFactorAuth":["totp"]},{"auth":"secret"}
            return {"id":USER,"displayName":"Tester"},{"auth":"secret"}
        if route.endswith("/totp/verify"):
            return {"verified":True},{"twoFactorAuth":"twofactor"}
        if route == "/auth/user":
            return {"id":USER,"displayName":"Tester"},{}
        if self.invalid:
            raise VRChatApiError("simulated API error")
        return self.pages.get(route, []),{}


class VRChatPersonalTests(unittest.TestCase):
    def setUp(self):
        self.dir = tempfile.TemporaryDirectory()
        self.addCleanup(self.dir.cleanup)
        self.fake = FakeTransport()
        self.crypto = patch.multiple(
            "tgc_cocompanion.vrchat_personal",
            _encrypt_for_user=lambda data: ("DPAPI:" + data).encode(),
            _decrypt_for_user=lambda data: data.decode().removeprefix("DPAPI:"),
        )
        self.crypto.start()
        self.addCleanup(self.crypto.stop)
        self.account = PersonalVRChat(Path(self.dir.name), transport=self.fake)

    def test_login_saves_encrypted_cookies_but_not_password(self):
        self.assertEqual(self.account.login("email@example.com","secretpassword"),[])
        self.assertTrue(self.account.signed_in)
        saved = (Path(self.dir.name) / "personal_vrchat_session.dpapi").read_bytes()
        self.assertNotIn(b"secretpassword",saved)
        self.assertNotIn(b"email@example.com",saved)
        self.assertEqual(self.account.user_id,USER)
        self.assertEqual(self.account.current_user()["id"],USER)

    def test_2fa_totp_then_session(self):
        self.fake.needs2fa=True
        self.assertEqual(self.account.login("Tester","pass"),["totp"])
        self.assertFalse(self.account.signed_in)
        self.assertEqual(self.account.verify_2fa("123456","totp"),"Tester")
        self.assertTrue(self.account.signed_in)
        self.assertTrue(any("twoFactorAuth=twofactor" in x[2]
                            for x in self.fake.calls if x[1]=="/auth/user" and not x[3]))

    def test_2fa_invalid_code(self):
        self.fake.needs2fa=True
        self.account.login("Tester","pass")
        with self.assertRaises(VRChatApiError):
            self.account.verify_2fa("../bad")
        self.assertFalse(self.account.signed_in)

    def test_friends_pagination(self):
        self.account.login("Tester","pass")
        for category in ("online","offline"):
            self.fake.pages[f"/auth/user/friends?n=60&offline={'true' if category=='offline' else 'false'}&offset=0"]=[{"id":FRIEND}]
        self.assertEqual(self.account.friend_snapshot(),{FRIEND})
        self.assertEqual(self.account.browse("online"),[{"id":FRIEND}])

    def test_incomplete_friend_list_refused(self):
        self.account.login("Tester","pass")
        self.fake.pages["/auth/user/friends?n=60&offline=false&offset=0"]=[{"id":FRIEND}]*60
        self.fake.invalid=True
        with self.assertRaises(VRChatApiError):
            self.account.friend_snapshot()

    def test_query_and_offset_validation(self):
        self.account.login("Tester","pass")
        for mode,args in (("search_users",{"query":"a"}),
                          ("online",{"offset":7}),
                          ("unknown",{})):
            with self.assertRaises(VRChatApiError):
                self.account.browse(mode,**args)

    def test_offline_session_is_locked(self):
        with self.assertRaises(VRChatApiError):
            self.account.browse("online")
        self.account.login("Tester","pass")
        self.account.logout()
        self.assertFalse(self.account.signed_in)
        self.assertFalse((Path(self.dir.name) / "personal_vrchat_session.dpapi").exists())

    def test_route_and_cookie_header_safety(self):
        with self.assertRaises(VRChatApiError):
            _valid_route("https://evil")
        with self.assertRaises(VRChatApiError):
            _valid_route("/../admin")
        self.assertEqual(_cookies_from_headers([
            ("Set-Cookie","auth=private; Secure"),("Set-Cookie","other=bad")
        ]),{"auth":"private"})


class StaffDexTests(unittest.TestCase):
    def setUp(self):
        self.raw={"ok":True,"members":[
            {"discordId":DISCORD,"vrchatUserId":FRIEND,"username":"Lily","role":"Owner"},
            {"discordId":"123456789012345679","vrchatUserId":USER,
             "username":"Seawolf","role":"Admin","isRoyalty":True},
            {"discordId":"123456789012345680","vrchatUrl":"https://vrchat.com/home/user/"+list(GM.values())[0],
             "username":"Gamer Music","role":"Moderator"},
            {"discordId":"bad","username":"Imposter","role":"Owner"},
        ]}

    def test_roster_rank_sort_and_identity_validation(self):
        entries=parse_roster(self.raw)
        self.assertEqual(len(entries),3)
        self.assertEqual(entries[0].rank,"The Royalty")
        self.assertEqual(entries[1].rank,"Owner")
        self.assertEqual(entries[2].vrchat_id,list(GM.values())[0])

    def test_own_account_and_verified_friend_collection(self):
        entries=parse_roster(self.raw)
        snap=DexSnapshot(DISCORD,USER,entries,frozenset([FRIEND]),123)
        self.assertTrue(snap.collected(USER))
        self.assertTrue(snap.collected(FRIEND))
        self.assertEqual(snap.total,2)
        self.assertEqual(snap.earned,1)

    def test_hide_unearned_hidden_awards(self):
        entries=parse_roster(self.raw)
        snap=DexSnapshot(DISCORD,USER,entries,frozenset(),123)
        self.assertFalse(any(k.startswith("Community Encounter") for k in snap.visible_rewards()))

    def test_incomplete_friend_scan_keeps_old_snapshot(self):
        class FakeAccount:
            signed_in=True
            user_id=USER
            def friend_snapshot(self):
                raise VRChatApiError("incomplete")
        with tempfile.TemporaryDirectory() as td:
            dex=StaffDex(Path(td),roster_fetch=lambda:parse_roster(self.raw))
            with self.assertRaises(VRChatApiError):
                dex.refresh(FakeAccount(),DISCORD)
            self.assertFalse(dex.path.exists())

    def test_wrong_identity_denied(self):
        class FakeAccount:
            signed_in=True
            user_id=USER
            def friend_snapshot(self):
                return {FRIEND}
        with tempfile.TemporaryDirectory() as td:
            dex=StaffDex(Path(td),roster_fetch=lambda:parse_roster(self.raw))
            with self.assertRaises(PermissionError):
                dex.refresh(FakeAccount(),"unverified")

if __name__ == "__main__":
    unittest.main()
