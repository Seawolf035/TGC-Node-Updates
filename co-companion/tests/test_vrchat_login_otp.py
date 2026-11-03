"""Offline VRChat login challenge regression, no network or credentials."""
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

from tgc_cocompanion.vrchat_personal import PersonalVRChat, VRChatApiError
from tgc_cocompanion.desktop_compact_nav import compact_pages, ICONS

USER="usr_2a833076-03ac-4212-aeff-0708d8eab968"


class ChallengeTransport:
    def __init__(self, methods):
        self.methods = methods
        self.calls = []

    def __call__(self, method, route, cookie, authorization="", body=None):
        self.calls.append((method, route, cookie, authorization, body))
        if route == "/auth/user" and authorization:
            return {"requiresTwoFactorAuth": self.methods}, {"auth": "limited-session"}
        if route == "/auth/user":
            return {"id": USER, "displayName": "VRChat test user"}, {}
        if route.endswith("/verify"):
            return {"verified": True}, {"twoFactorAuth": "two-factor-ok"}
        raise VRChatApiError("Unexpected VRChat path")


class LoginChallengeTests(unittest.TestCase):
    def setUp(self):
        self.dir = tempfile.TemporaryDirectory()
        self.addCleanup(self.dir.cleanup)
        crypto = patch.multiple("tgc_cocompanion.vrchat_personal",
            _encrypt_for_user=lambda d: ("DPAPI:"+d).encode(),
            _decrypt_for_user=lambda b: b.decode().removeprefix("DPAPI:"))
        crypto.start()
        self.addCleanup(crypto.stop)

    def account(self, offered):
        wire=ChallengeTransport(offered)
        client=PersonalVRChat(Path(self.dir.name), transport=wire)
        return client,wire

    def test_emailotp_wire_casing_is_normalized_without_selector(self):
        client,wire=self.account(["emailOtp"])
        self.assertEqual(client.login("email@example.com","test-password"),["emailotp"])
        self.assertFalse(client.signed_in)
        self.assertEqual(client.verify_2fa("123456"),"VRChat test user")
        routes=[c[1] for c in wire.calls]
        self.assertIn("/auth/twofactorauth/emailotp/verify",routes)
        self.assertNotIn("/auth/twofactorauth/totp/verify",routes)
        self.assertTrue(client.signed_in)
        self.assertEqual(client.pending_methods,())

    def test_totp_and_recovery_use_different_endpoints(self):
        client,wire=self.account(["otp","totp"])
        client.login("user","pass")
        self.assertEqual(client.verify_2fa("123456"),"VRChat test user")
        self.assertIn("/auth/twofactorauth/totp/verify",[c[1] for c in wire.calls])
        self.assertEqual(len([c for c in wire.calls if c[1].endswith("/verify")]),1)
        second,other=self.account(["totp","otp"])
        second.login("user","pass")
        second.prefer_2fa("otp")
        second.verify_2fa("abcd-1234-efgh")
        self.assertIn("/auth/twofactorauth/otp/verify",[c[1] for c in other.calls])
        self.assertEqual(len([c for c in other.calls if c[1].endswith("/verify")]),1)

    def test_unoffered_method_is_refused_without_network_request(self):
        client,wire=self.account(["totp"])
        client.login("user","pass")
        with self.assertRaises(VRChatApiError):
            client.prefer_2fa("emailotp")
        with self.assertRaises(VRChatApiError):
            client.verify_2fa("123456",mode="emailotp")
        self.assertFalse(any(c[1].endswith("/verify") for c in wire.calls))
        self.assertFalse(client.signed_in)

    def test_same_length_email_and_totp_requires_intent(self):
        client,wire=self.account(["emailOtp","totp"])
        client.login("user","pass")
        with self.assertRaisesRegex(VRChatApiError,"both email and authenticator"):
            client.verify_2fa("123456")
        self.assertFalse(any(c[1].endswith("/verify") for c in wire.calls))
        client.prefer_2fa("emailotp")
        client.verify_2fa("123456")
        self.assertTrue(client.signed_in)

    def test_compact_nav_is_pins_plus_current_not_fifteen_abbreviations(self):
        all_pages=["Home","Social","VRChat Launcher","Settings","StaffDex","Inbox"]
        self.assertEqual(compact_pages(["Home","Social","Home"],"StaffDex",all_pages),
                         ["Home","Social","StaffDex"])
        self.assertTrue(all(p in ICONS for p in all_pages))
        self.assertEqual(compact_pages([],"Settings",all_pages),["Settings"])


if __name__=="__main__":
    unittest.main()
