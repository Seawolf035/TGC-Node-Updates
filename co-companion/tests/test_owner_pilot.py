"""Offline owner pilot enrollment tests: no Windows DPAPI or network calls."""
from pathlib import Path
from tempfile import TemporaryDirectory
import unittest
from unittest.mock import patch

from tgc_cocompanion.pilot_enrollment import (
    PilotEnrollment, PilotEnrollmentError, parse_deployment_link,
)
from tgc_cocompanion.owner_pilot import terms_digest


RELAY = "https://script.google.com/macros/s/AKfycbxTESTtest001/exec"
CODE = "TGC-DEPLOY-ABCD-1234-5678-90AB-CDEF"
ID = "123456789012345678"


class OwnerPilotEnrollmentTests(unittest.TestCase):
    def test_exact_relay_deployment_link(self):
        self.assertEqual(parse_deployment_link(RELAY+"?deploy="+CODE),(RELAY,CODE))

    def test_disallow_unofficial_deployment_urls(self):
        for url in (
            "https://example.com/?deploy="+CODE,
            "http://script.google.com/macros/s/AKfycbxTESTtest001/exec?deploy="+CODE,
            RELAY+"?other="+CODE,
            RELAY+"?deploy=BAD",
            "https://script.google.com/macros/s/AKfycbxTESTtest001/dev?deploy="+CODE,
        ):
            with self.subTest(url=url),self.assertRaises(PilotEnrollmentError):
                parse_deployment_link(url)

    def test_initialization_does_not_request_relay_or_start_node(self):
        with TemporaryDirectory() as td:
            requests=[]
            en=PilotEnrollment(Path(td),post=lambda u,p:requests.append(p))
            self.assertFalse(en.enrolled)
            self.assertEqual(requests,[])
            self.assertFalse((Path(td)/"enrollment.json").exists())

    def test_redeem_persists_only_encrypted_key(self):
        with TemporaryDirectory() as td:
            calls=[]
            def post(url,body):
                calls.append(body)
                if body["action"] == "redeem_deployment":
                    return {
                        "ok":True,"verified":True,"node_id":"node-owner-test",
                        "node_key":"very-long-test-secret-key",
                        "discord_id":ID,"client_type":"staff_launcher"
                    }
                if body["action"] == "node_status":
                    return {
                        "ok":True,"verified":True,"node_id":"node-owner-test",
                        "discord_id":ID,"client_type":"staff_launcher",
                        "verification_method":"deployment_code"
                    }
                if body["action"] == "cocompanion_access":
                    return {"ok":True}
                raise AssertionError("Unexpected operation: "+body["action"])
            with patch("tgc_cocompanion.pilot_enrollment._encrypt_for_user",
                       side_effect=lambda key:b"test-DPAPI-cipher"),patch(
                       "tgc_cocompanion.pilot_enrollment._decrypt_for_user",
                       return_value="very-long-test-secret-key"):
                en=PilotEnrollment(Path(td),post=post)
                self.assertEqual(en.redeem(RELAY+"?deploy="+CODE),ID)
                self.assertTrue(en.enrolled)
                self.assertNotIn("very-long-test-secret-key",(Path(td)/"enrollment.json").read_text())
                self.assertEqual((Path(td)/"pilot_node_key.dpapi").read_bytes(),b"test-DPAPI-cipher")
                self.assertEqual(en.verify_node()["verified"],True)
                en.access_rpc()
                en2=PilotEnrollment(Path(td),post=post)
                self.assertTrue(en2.enrolled)
                self.assertEqual(en2.discord_id,ID)
                self.assertEqual([p["action"] for p in calls],
                                 ["redeem_deployment","node_status","cocompanion_access"])
                self.assertFalse(any(p["action"]=="submit_sightings" for p in calls))
                en.clear_local()
                self.assertFalse((Path(td)/"enrollment.json").exists())

    def test_wrong_enrollment_never_persists(self):
        with TemporaryDirectory() as td:
            for response in (
                {"ok":True,"verified":False,"node_id":"node","node_key":"long-secret-key1234","discord_id":ID,"client_type":"staff_launcher"},
                {"ok":True,"verified":True,"node_id":"node","node_key":"long-secret-key1234","discord_id":ID,"client_type":"mobile_companion"},
                {"ok":True,"verified":True,"node_id":"node","node_key":"short","discord_id":ID,"client_type":"staff_launcher"},
            ):
                en=PilotEnrollment(Path(td),post=lambda u,p,response=response:response)
                with self.subTest(response=response),self.assertRaises(PilotEnrollmentError):
                    en.redeem(RELAY+"?deploy="+CODE)
                self.assertFalse((Path(td)/"pilot_node_key.dpapi").exists())

    def test_terms_are_exact_source_with_version(self):
        self.assertEqual(len(terms_digest()),64)

if __name__ == "__main__":
    unittest.main()
