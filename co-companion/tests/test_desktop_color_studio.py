"""Offline 10-channel UI colors / VRChat palette importer / staff gate tests."""
import json
from pathlib import Path
import tempfile
import unittest
from types import SimpleNamespace
from unittest.mock import MagicMock

from tgc_cocompanion.desktop_color_studio import (
    CHANNELS, VRCHAT_CHANNELS, normalize_hex, validate_palette, blend,
    import_vrchat_colors, export_vrchat_colors, built_in_ten_colors,
    LocalPalettePresets, DesktopColorStudio)
from tgc_cocompanion.desktop_staff_colors import PALETTES

OWNER="197820946608947201"
OTHER="344278828014895104"
VERIFIED="unrelated"  # no local role strings can grant this permission


class ThemeModelTests(unittest.TestCase):
    def test_all_ten_channels_and_android_vrchat_order(self):
        self.assertEqual(len(CHANNELS),10)
        self.assertEqual(VRCHAT_CHANNELS,(4,6,7,0,8,9))
        colors=import_vrchat_colors(
            "#123456,654321,ABCDEF,091827,FFEEDD,8899AA")
        self.assertEqual(len(colors),10)
        self.assertEqual(colors[0],"#091827")
        self.assertEqual(colors[4],"#123456")
        self.assertEqual(colors[6],"#654321")
        self.assertEqual(colors[7],"#ABCDEF")
        self.assertEqual(colors[8],"#FFEEDD")
        self.assertEqual(colors[9],"#8899AA")
        self.assertEqual(
            export_vrchat_colors(colors),
            "#123456,#654321,#ABCDEF,#091827,#FFEEDD,#8899AA")
        self.assertEqual(colors[1],blend("#091827","#654321",.14))
        self.assertEqual(colors[2],blend("#091827","#ABCDEF",.27))
        self.assertEqual(colors[3],blend("#091827","#654321",.19))
        self.assertEqual(colors[5],blend("#091827","#123456",.38))

    def test_invalid_hex_and_bad_clipboards_do_not_import(self):
        for bad in ("#123", "#00112233", "GGGGGG", "", "#00000K", None, 44):
            with self.subTest(bad=bad):
                with self.assertRaises(ValueError):
                    normalize_hex(bad)
        for bad in ("red,green,blue", "#000000", "#000000,"*7,
                    "#aabbcc"*50):
            with self.subTest(bad=bad):
                with self.assertRaises(ValueError):
                    import_vrchat_colors(bad)
        with self.assertRaises(ValueError):
            validate_palette(["#000000"]*9)
        with self.assertRaises(ValueError):
            validate_palette(["#000000"]*9+["bad"])
        self.assertEqual(normalize_hex("abcdef"),"#ABCDEF")

    def test_every_built_in_palette_maps_to_ten_channels(self):
        self.assertEqual(len(PALETTES),12)
        for name in PALETTES:
            with self.subTest(name=name):
                colors=built_in_ten_colors(name)
                self.assertEqual(len(colors),10)
                self.assertTrue(export_vrchat_colors(colors).startswith("#"))


class PresetStorageTests(unittest.TestCase):
    def test_account_isolation_and_active_restart(self):
        with tempfile.TemporaryDirectory() as td:
            store=LocalPalettePresets(Path(td))
            colors=built_in_ten_colors("Deep Ocean")
            store.save(OWNER,"Ocean",colors)
            store.activate(OWNER,"Ocean")
            self.assertEqual(store.active(OWNER),("Ocean",colors))
            self.assertEqual(store.list(OTHER),())
            self.assertEqual(store.active(OTHER),(None,None))
            again=LocalPalettePresets(Path(td))
            self.assertEqual(again.load(OWNER,"Ocean"),colors)
            self.assertEqual(again.active(OWNER),("Ocean",colors))
            again.activate(OWNER,None)
            self.assertEqual(again.active(OWNER),(None,None))
            self.assertEqual(again.load(OWNER,"Ocean"),colors)

    def test_reject_duplicate_save_without_overwrite_and_25_max(self):
        with tempfile.TemporaryDirectory() as td:
            store=LocalPalettePresets(Path(td))
            colors=built_in_ten_colors("TGC Default")
            store.save(OWNER,"Ocean",colors)
            with self.assertRaises(ValueError):
                store.save(OWNER,"ocean",colors)
            store.save(OWNER,"ocean",colors,overwrite=True)
            self.assertEqual(store.list(OWNER),("Ocean",))
            for n in range(24):
                store.save(OWNER,f"theme {n}",colors)
            with self.assertRaisesRegex(ValueError,"Maximum"):
                store.save(OWNER,"over limit",colors)
            with self.assertRaises(PermissionError):
                store.save("unverified","Fake",colors)
            store.activate(OWNER,"Ocean")
            store.delete(OWNER,"Ocean")
            self.assertEqual(store.active(OWNER),(None,None))

    def test_corrupt_file_is_not_entitlement_and_no_tokens_stored(self):
        with tempfile.TemporaryDirectory() as td:
            store=LocalPalettePresets(Path(td))
            store.path.write_text("{broken JSON",encoding="utf-8")
            self.assertEqual(store.list(OWNER),())
            store.save(OWNER,"Simple",built_in_ten_colors("Graphite"))
            raw=store.path.read_text("utf-8")
            self.assertEqual(json.loads(raw)["schema"],1)
            self.assertNotIn("rank",raw)
            self.assertNotIn("token",raw)
            self.assertNotIn("auth",raw.lower())


class StaffDexFilteringTests(unittest.TestCase):
    def test_filters_search_role_and_collection_without_mutation(self):
        from tgc_cocompanion.desktop_staffdex_filter import filter_staffdex_rows
        rows=[
            ("Seawolf","The Royalty","Collected","usr_2a833076-03ac-4212-aeff-0708d8eab968"),
            ("Staff B","Admin","Missing","usr_b85631a8-5e23-4b88-a14b-4c8a2ae863c1"),
            ("Staff C","Moderator","Not scanned",""),
        ]
        original=tuple(rows)
        self.assertEqual(len(filter_staffdex_rows(rows)),3)
        self.assertEqual(len(filter_staffdex_rows(rows,"seawolf")),1)
        self.assertEqual(len(filter_staffdex_rows(rows,rank="Admin")),1)
        self.assertEqual(len(filter_staffdex_rows(rows,progress="Missing")),1)
        self.assertEqual(len(filter_staffdex_rows(rows,progress="Collected")),1)
        self.assertEqual(len(filter_staffdex_rows(rows,progress="Unscanned")),1)
        self.assertEqual(len(filter_staffdex_rows(rows,rank="Admin",progress="Collected")),0)
        self.assertEqual(tuple(rows),original)


class ServerGateTests(unittest.TestCase):
    def access(self,role,valid=True,terms=True,match=True):
        import time
        lease=SimpleNamespace(discord_id=OWNER if match else OTHER,
               valid_limit=lambda _time: 3 if valid else 0)
        gateway=SimpleNamespace(
            current=lambda:lease,rank=lambda:role)
        owner=SimpleNamespace(lease=gateway,
            enrollment=SimpleNamespace(discord_id=OWNER),
            _terms_valid=lambda:terms)
        studio=DesktopColorStudio.__new__(DesktopColorStudio)
        studio.owner=owner
        return studio

    def test_requires_active_matching_admin_plus_rank(self):
        for role in ("Admin","Head Admin","Owner","The Royalty"):
            self.assertEqual(self.access(role)._access(),OWNER)
        for role in ("", "Trial Moderator","Moderator","Head Moderator", "self-declared owner"):
            with self.subTest(role=role), self.assertRaises(PermissionError):
                self.access(role)._access()
        for kwargs in ({"valid":False},{"terms":False},{"match":False}):
            with self.subTest(**kwargs), self.assertRaises(PermissionError):
                self.access("The Royalty",**kwargs)._access()


if __name__=="__main__":
    unittest.main()
