"""No-network v0.4 navigation persistence, search and Windows shell checks."""
import json
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

from tgc_cocompanion.desktop_navigation import NavigationState, search_pages


class NavigationStateTests(unittest.TestCase):
    def test_search_matches_multiple_terms_case_insensitively(self):
        pages = ["Home", "VRChat Account", "VRChat Launcher", "StaffDex", "OSC Studio"]
        self.assertEqual(search_pages(pages, "vrchat"), ["VRChat Account", "VRChat Launcher"])
        self.assertEqual(search_pages(pages, "vRChAt LaUn"), ["VRChat Launcher"])
        self.assertEqual(search_pages(pages, "missing"), [])
        self.assertEqual(search_pages(pages, ""), pages)

    def test_pins_recent_history_and_last_page_survive_restart(self):
        with tempfile.TemporaryDirectory() as td:
            state = NavigationState(Path(td))
            state.restrict(["Home", "Social", "Updates", "StaffDex", "VRChat Launcher"])
            state.toggle_pin("StaffDex")
            state.visit("Social")
            state.visit("Updates")
            state.compact = True
            state.save()
            again = NavigationState(Path(td))
            again.restrict(["Home", "Social", "Updates", "StaffDex", "VRChat Launcher"])
            self.assertEqual(again.last_page, "Updates")
            self.assertEqual(again.recent[:2], ["Updates", "Social"])
            self.assertIn("StaffDex", again.pinned)
            self.assertTrue(again.compact)
            self.assertTrue(again.path.is_file())

    def test_corrupt_file_falls_back_without_crashing(self):
        with tempfile.TemporaryDirectory() as td:
            path = Path(td) / "co_companion_navigation.json"
            path.write_text("{broken", encoding="utf-8")
            state = NavigationState(Path(td))
            self.assertEqual(state.last_page, "Home")
            path.write_text(json.dumps({"last_page": "Absent", "recent": [34, "Social"],
                        "pinned": ["Secret", "Home"], "compact": 1}), encoding="utf-8")
            state = NavigationState(Path(td))
            state.restrict(["Home", "Social"])
            self.assertEqual(state.last_page, "Home")
            self.assertEqual(state.pinned, ["Home"])
            self.assertEqual(state.recent, ["Social"])
            self.assertFalse(state.compact)

    def test_pinned_limit_enforced(self):
        with tempfile.TemporaryDirectory() as td:
            state = NavigationState(Path(td))
            state.pinned = [str(n) for n in range(8)]
            with self.assertRaises(ValueError):
                state.toggle_pin("another")
            self.assertEqual(len(state.pinned), 8)


@unittest.skipUnless(os.name == "nt", "Windows GUI only")
class NavigationShellWindowsTests(unittest.TestCase):
    def test_shell_navigation_palette_and_compact_at_720p(self):
        import tkinter as tk
        from tgc_cocompanion.owner_pilot import OwnerPilotApp
        with tempfile.TemporaryDirectory() as td:
            with patch.dict(os.environ, {"LOCALAPPDATA": td}):
                root = tk.Tk()
                root.withdraw()
                try:
                    root.geometry("1280x720")
                    ui = OwnerPilotApp(root)
                    root.update_idletasks()
                    self.assertIn("Updates", ui.shell.page_names)
                    self.assertTrue(ui.shell.open_page("StaffDex"))
                    self.assertEqual(ui.page_select.get(), "StaffDex")
                    ui.shell.toggle_pin()
                    self.assertIn("StaffDex", ui.shell.state.pinned)
                    self.assertTrue(ui.shell.shortcut_page(0))
                    self.assertEqual(ui.page_select.get(), "Home")
                    ui.shell.toggle_compact()
                    self.assertTrue(ui.shell.state.compact)
                    ui.shell.show_palette()
                    self.assertTrue(ui.shell._palette.winfo_exists())
                    ui.shell._palette.destroy()
                    self.assertEqual(ui.features.home_dex.get(), "No scan available")
                finally:
                    root.destroy()


if __name__ == "__main__":
    unittest.main()
