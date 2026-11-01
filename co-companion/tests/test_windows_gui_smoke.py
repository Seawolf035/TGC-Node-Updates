"""Windows GUI owner-pilot smoke: create the actual Tk Notebook without network."""
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

@unittest.skipUnless(os.name=="nt","Windows Tk desktop smoke only")
class WindowsGuiSmokeTests(unittest.TestCase):
    def test_mobile_parity_tabs_create_without_network(self):
        import tkinter as tk
        from tkinter import ttk
        from tgc_cocompanion.owner_pilot import OwnerPilotApp
        with tempfile.TemporaryDirectory() as temp:
            with patch.dict(os.environ,{"LOCALAPPDATA":temp}):
                root=tk.Tk()
                root.withdraw()
                try:
                    ui=OwnerPilotApp(root)
                    tabs=[]
                    def walk(widget):
                        if isinstance(widget,ttk.Notebook):
                            tabs.extend(widget.tab(t,"text") for t in widget.tabs())
                        for child in widget.winfo_children():
                            walk(child)
                    walk(root)
                    for expected in ("Home","Verification","VRChat Launcher",
                                     "OSC","Tracking Diagnostics","VRChat Account",
                                     "Social","StaffDex","OSC Studio",
                                     "Profiles","Presence","Avatars","Bookmarks",
                                     "Inbox","Updates"):
                        self.assertIn(expected,tabs)
                    self.assertIsNone(ui.lease)
                    self.assertFalse(ui.enrollment.enrolled)
                    self.assertFalse(ui.features.account.signed_in)
                finally:
                    root.destroy()


if __name__=="__main__":
    unittest.main()
