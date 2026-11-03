"""v0.6 PCVR parity: official chatboxes, cosmetic colors and local exploration."""
from datetime import datetime
from pathlib import Path
import os
import tempfile
import time
import unittest
from unittest.mock import patch

from tgc_cocompanion.desktop_official_chatboxes import (
    CATALOG, BY_ID, OfficialSelection, normalize_trusted_rank, allowed_catalog)
from tgc_cocompanion.desktop_staff_colors import (
    PALETTES, RANK_COLORS, DesktopThemeChoice, palette_for)
from tgc_cocompanion.desktop_recent_players import (
    scan_recent_players, filter_recent_players, MAX_TAIL_BYTES)
from tgc_cocompanion.desktop_explore import (
    classify_result, merge_explore_results)
from tgc_cocompanion.staffdex_desktop import (
    StaffDex, StaffEntry, parse_roster)

A="usr_2a833076-03ac-4212-aeff-0708d8eab968"
B="usr_b85631a8-5e23-4b88-a14b-4c8a2ae863c1"
WORLD="wrld_2a833076-03ac-4212-aeff-0708d8eab968"
DISCORD="197820946608947201"


class StaffParityTests(unittest.TestCase):
    def test_exact_official_mobile_phrases_and_tiers(self):
        self.assertEqual(len(CATALOG),12)
        self.assertEqual([x.tier for x in CATALOG],[0]*5+list(range(1,8)))
        self.assertEqual(BY_ID["common_welcome"].text,"『Welcome to The Gamer Club』")
        self.assertEqual(BY_ID["common_discord"].text,"『Discord.gg/TheGamerClub』")
        self.assertEqual(BY_ID["royalty"].text,"『Gamer Club Royalty』")
        self.assertEqual(len(allowed_catalog(False,"The Royalty")),0)
        self.assertEqual(len(allowed_catalog(True,"")),5)
        self.assertEqual(len(allowed_catalog(True,"Admin")),9)
        self.assertEqual(len(allowed_catalog(True,"The Royalty")),12)

    def test_trusted_rank_royalty_requires_exact_override(self):
        self.assertEqual(normalize_trusted_rank("royalty",DISCORD),"The Royalty")
        self.assertEqual(normalize_trusted_rank("royalty","123456789012345678"),"Owner")
        self.assertEqual(normalize_trusted_rank("head mod",DISCORD),"Head Moderator")
        self.assertEqual(normalize_trusted_rank("",DISCORD),"")
        self.assertEqual(normalize_trusted_rank("admin++",DISCORD),"")
        self.assertEqual(normalize_trusted_rank(None,DISCORD),"")

    def test_locked_official_selection_cannot_be_forged(self):
        with tempfile.TemporaryDirectory() as td:
            store=OfficialSelection(Path(td))
            common=allowed_catalog(True,"")
            store.toggle(A,"common_welcome",common)
            self.assertEqual(store.get(A),{"common_welcome"})
            with self.assertRaises(PermissionError):
                store.toggle(A,"owner",common)
            self.assertEqual(OfficialSelection(Path(td)).get(A),{"common_welcome"})
            self.assertEqual(store.get(B),set())
            store.toggle(A,"common_welcome",())
            self.assertEqual(store.get(A),set())

    def test_theme_persistence_and_palette_map(self):
        self.assertEqual(len(PALETTES),12)
        self.assertEqual(RANK_COLORS["The Royalty"].lower(),"#d4af37")
        self.assertEqual(RANK_COLORS["Moderator"].lower(),"#004cff")
        self.assertTrue(all(len(palette_for(x))==6 for x in PALETTES))
        with tempfile.TemporaryDirectory() as td:
            theme=DesktopThemeChoice(Path(td))
            self.assertEqual(theme.load(),"TGC Default")
            theme.save("Deep Ocean")
            self.assertEqual(DesktopThemeChoice(Path(td)).load(),"Deep Ocean")
            with self.assertRaises(ValueError):
                theme.save("fake privileged gold")

    def test_directory_does_not_need_auth_or_scan_friends(self):
        member=StaffEntry(DISCORD,A,"Staff One","Owner")
        dex=StaffDex(Path(tempfile.gettempdir()),roster_fetch=lambda:(member,))
        self.assertEqual(dex.roster_directory(),(member,))
        # Roster parsing uses exact Mobile Companion schema.
        self.assertEqual(parse_roster({"ok":True,"members":[{
          "discordId":DISCORD,"role":"owner","username":"Staff One",
          "vrchatUserId":A}]}),(member,))


class ExploreAndLocalHistoryTests(unittest.TestCase):
    def test_combined_search_rejects_unknown_ids_and_deduplicates(self):
        user={"id":A,"displayName":"Seawolf"}
        world={"id":WORLD,"name":"Ocean World"}
        rows=merge_explore_results([
            ("People",[user,{"id":"not-an-id"}]),
            ("Worlds",[world,user])])
        self.assertEqual(len(rows),2)
        self.assertEqual({r[0] for r in rows},{"Person","World"})
        self.assertIsNone(classify_result({"id":"invalid"},"People"))

    def test_on_demand_log_scan_never_persists_or_uploads(self):
        with tempfile.TemporaryDirectory() as td:
            folder=Path(td)
            log=folder/"output_log_2026-11-03_02-00-00.txt"
            log.write_text(
                "[2026.11.03 02:00:00] OnPlayerJoined Sailor ("+A+")\n"
                "[2026.11.03 02:01:00] OnPlayerJoined Sailor ("+A+")\n"
                "[2026.11.03 02:02:00] OnPlayerJoined Ocean Wolf ("+B+")\n",
                encoding="utf-8")
            moment=datetime(2026,11,3,3,0,0)
            stamp=moment.timestamp()
            os.utime(log,(stamp,stamp))
            before=set(folder.iterdir())
            data=scan_recent_players(folder,now=moment,exclude_user_id=B)
            self.assertEqual(len(data),1)
            self.assertEqual(data[0].user_id,A)
            self.assertEqual(len(filter_recent_players(data,"SAILOR")),1)
            self.assertEqual(before,set(folder.iterdir()))

    def test_oversized_names_and_old_logs_are_ignored(self):
        with tempfile.TemporaryDirectory() as td:
            folder=Path(td)
            p=folder/"output_log_old.txt"
            p.write_text("[2026.11.03 02:00:00] OnPlayerJoined "+"x"*5000+
                         " ("+A+")\n",encoding="utf-8")
            moment=datetime(2026,11,3,3,0,0)
            os.utime(p,(moment.timestamp(),moment.timestamp()))
            self.assertEqual(scan_recent_players(folder,now=moment),())


if __name__=="__main__":
    unittest.main()
