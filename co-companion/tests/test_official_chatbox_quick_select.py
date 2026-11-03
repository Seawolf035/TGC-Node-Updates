"""Fast official staff OSC selection: no manual role impersonation."""
from pathlib import Path
import tempfile
import unittest

from tgc_cocompanion.desktop_official_chatboxes import (
    CATALOG, OfficialSelection, allowed_catalog,
    highest_role_template, selection_preset,
)

USER="usr_2a833076-03ac-4212-aeff-0708d8eab968"
OTHER="usr_b85631a8-5e23-4b88-a14b-4c8a2ae863c1"


class QuickSelectTests(unittest.TestCase):
    def test_my_rank_matches_current_authenticated_rank(self):
        for rank,expected in (
            ("Trial Moderator","trial_mod"),("Moderator","moderator"),
            ("Head Moderator","head_mod"),("Admin","admin"),
            ("Head Admin","head_admin"),("Owner","owner"),
            ("The Royalty","royalty")):
            with self.subTest(rank=rank):
                allowed=allowed_catalog(True,rank)
                self.assertEqual(highest_role_template(allowed,rank).id,expected)
                self.assertEqual(selection_preset("rank",allowed,rank),{expected})
        with self.assertRaises(PermissionError):
            selection_preset("rank",allowed_catalog(True,""),"")
        with self.assertRaises(PermissionError):
            selection_preset("rank",allowed_catalog(False,"The Royalty"),"The Royalty")

    def test_common_suggested_all_clear_are_only_authorized(self):
        common=allowed_catalog(True,"")
        self.assertEqual(len(common),5)
        self.assertEqual(len(selection_preset("common",common,"")),5)
        self.assertEqual(len(selection_preset("suggested",common,"")),4)
        self.assertEqual(len(selection_preset("all",common,"")),5)
        self.assertEqual(selection_preset("clear",common,""),set())
        full=allowed_catalog(True,"The Royalty")
        self.assertEqual(len(selection_preset("all",full,"The Royalty")),12)
        self.assertEqual(len(selection_preset("common",full,"The Royalty")),5)
        self.assertEqual(selection_preset("rank",full,"The Royalty"),{"royalty"})
        with self.assertRaises(ValueError):
            selection_preset("pretend owner",common,"")

    def test_atomic_replace_is_per_account_and_rejects_rank_escalation(self):
        with tempfile.TemporaryDirectory() as td:
            store=OfficialSelection(Path(td))
            common=allowed_catalog(True,"")
            store.replace(USER,selection_preset("suggested",common,""),common)
            before=store.get(USER)
            self.assertEqual(len(before),4)
            with self.assertRaises(PermissionError):
                store.replace(USER,{"owner"},common)
            self.assertEqual(store.get(USER),before)
            self.assertEqual(store.get(OTHER),set())
            store.replace(USER,{"common_staff"},common)
            self.assertEqual(store.get(USER),{"common_staff"})
            store.replace(USER,set(),common)
            self.assertEqual(OfficialSelection(Path(td)).get(USER),set())
            with self.assertRaises(PermissionError):
                store.replace("not-a-valid-vrchat-id",set(),common)

    def test_buttons_never_derive_permissions_from_public_roster(self):
        # A forged role string alone is not authorization. The eligible
        # collection, obtained from current validated lease, is definitive.
        permitted=allowed_catalog(True,"")
        self.assertIsNone(highest_role_template(permitted,"Owner"))
        with self.assertRaises(PermissionError):
            selection_preset("rank",permitted,"Owner")
        self.assertEqual(selection_preset("all",permitted,"Owner"),
                         {t.id for t in permitted})


if __name__=="__main__":
    unittest.main()
