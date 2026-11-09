"""Profile layout, safe real VRChat art sources and responsive shell."""
from io import BytesIO
from pathlib import Path
import unittest
from unittest.mock import Mock
from tkinter import TclError
import tkinter as tk
from tkinter import ttk

from tgc_cocompanion.desktop_profile_view import (
    profile_kind,profile_summary,VisualProfile)
from tgc_cocompanion.desktop_profile_images import (
    safe_image_url,image_sources,ProfileImageCache,render_image)
from tgc_cocompanion.desktop_shell import responsive_shell

UID="usr_2a833076-03ac-4212-aeff-0708d8eab968"
WRLD="wrld_2a833076-03ac-4212-aeff-0708d8eab968"
GRP="grp_2a833076-03ac-4212-aeff-0708d8eab968"

class ModelTests(unittest.TestCase):
    def test_profile_kinds_and_world_metadata(self):
        self.assertEqual(profile_kind(UID),"user")
        self.assertEqual(profile_kind(WRLD),"world")
        self.assertEqual(profile_kind(GRP),"group")
        name,sub,meta,about=profile_summary({
            "id":WRLD,"name":"Ocean World","authorName":"Seawolf",
            "visits":120,"favorites":50,"capacity":25,
            "description":"A new VRChat world"},"world")
        self.assertEqual(name,"Ocean World")
        self.assertEqual(about,"A new VRChat world")
        self.assertEqual(meta[0],("Creator","Seawolf"))
        self.assertEqual(meta[1],("Visits","120"))
        name,_,_,bio=profile_summary({"id":UID,"displayName":"Ocean",
                                      "bio":"Test profile"},"user")
        self.assertEqual(name,"Ocean")
        self.assertEqual(bio,"Test profile")
        _,_,_,none=profile_summary({}, "group")
        self.assertEqual(none,"Not provided")

    def test_responsive_breakpoints_preserve_navigation(self):
        for width in (760,800,1024,1099):
            plan=responsive_shell(width)
            self.assertTrue(plan["compact_sidebar"])
            self.assertEqual(plan["sidebar_width"],74)
        for width in (1280,1366,1920,2560,3840):
            plan=responsive_shell(width)
            self.assertFalse(plan["compact_sidebar"])
            self.assertTrue(plan["show_page_picker"])
            self.assertEqual(plan["sidebar_width"],230)
        self.assertFalse(responsive_shell(800)["show_header_shortcuts"])
        self.assertTrue(responsive_shell(1280)["show_header_shortcuts"])

    def test_real_vrchat_image_priority_and_untrusted_hosts(self):
        image="https://images.vrchat.cloud/image/avatar.png"
        banner="https://api.vrchat.cloud/api/1/file/file_xyz/2"
        other="https://files.vrchat.cloud/file/xyz"
        self.assertEqual(image_sources({
            "userIcon":image,"profilePicOverride":banner},"user"),(image,banner))
        self.assertEqual(image_sources({
            "thumbnailImageUrl":image,"imageUrl":other},"world"),(image,other))
        self.assertEqual(image_sources({
            "iconUrl":image,"bannerUrl":banner},"group"),(image,banner))
        for bad in ("http://api.vrchat.cloud/img.jpg",
                    "https://api.vrchat.cloud.attacker.com/photo",
                    "https://evil.com/img","file:///etc/passwd",
                    "https://127.0.0.1/private", "https://user:pass@api.vrchat.cloud/x",
                    "https://api.vrchat.cloud:8080/proxy"):
            with self.subTest(bad=bad):
                self.assertIsNone(safe_image_url(bad))
        self.assertEqual(safe_image_url("https://api.vrchat.cloud/x"),
                         "https://api.vrchat.cloud/x")

    def test_profile_image_cache_respects_limit_and_reuses_bytes(self):
        try:
            from PIL import Image
        except ImportError:
            self.skipTest("Pillow is installed in Windows EXE build")
        img=Image.new("RGB",(32,32),(2,60,170))
        memory=BytesIO()
        img.save(memory,format="PNG")
        payload=memory.getvalue()
        class Response:
            headers=Mock(get_content_type=lambda:"image/png")
            def __enter__(self):return self
            def __exit__(self,*_):pass
            def geturl(self):return "https://images.vrchat.cloud/avatar.png"
            def read(self,n):return payload[:n]
        cache=ProfileImageCache(clock=lambda:100)
        opener=Mock(open=Mock(return_value=Response()))
        cache._opener=opener
        url="https://images.vrchat.cloud/avatar.png"
        self.assertEqual(cache.fetch(url),payload)
        self.assertEqual(cache.fetch(url),payload)
        self.assertEqual(opener.open.call_count,1)
        self.assertEqual(render_image(payload,(112,112),circle=True).size,
                         (112,112))

class DisplayTests(unittest.TestCase):
    def test_per_user_scale_is_validated_and_atomic(self):
        import tempfile
        from tgc_cocompanion.desktop_display import SCALES,DisplayPreferences
        self.assertEqual(SCALES,(85,100,115,130,150))
        with tempfile.TemporaryDirectory() as td:
            prefs=DisplayPreferences(Path(td))
            self.assertEqual(prefs.load(),100)
            prefs.save(130)
            self.assertEqual(DisplayPreferences(Path(td)).load(),130)
            for bad in (0,200,True,"150",1.5):
                with self.subTest(bad=bad),self.assertRaises(ValueError):
                    prefs.save(bad)
            prefs.path.write_text("{broken JSON",encoding="utf-8")
            self.assertEqual(prefs.load(),100)


class TkProfileTests(unittest.TestCase):
    def setUp(self):
        try:
            self.root=tk.Tk()
            self.root.withdraw()
            style=ttk.Style(self.root);style.theme_use("clam")
        except TclError:
            self.skipTest("No graphical display")
        self.addCleanup(self.root.destroy)

    def test_real_visual_profile_at_multiple_layout_widths(self):
        owner=Mock(root=self.root)
        container=ttk.Frame(self.root)
        container.pack(fill="both",expand=True)
        visual=VisualProfile(owner,container)
        self.assertEqual(len(visual.meta_labels),6)
        for width in (410,660,800,1100):
            visual._responsive(Mock(width=width))
            columns=1 if width<510 else 2 if width<690 else 3
            self.assertEqual(visual.meta_labels[4][2].master.grid_info()["column"],4%columns)
        record={"id":WRLD,"name":"Underwater World",
                "description":"Test description",
                "visits":120,"authorName":"World Creator"}
        visual.update(record,"world")
        self.assertEqual(visual.title.get(),"Underwater World")
        self.assertEqual(visual.meta_labels[0][1].get(),"World Creator")
        visual.update({"id":UID,"displayName":"Sea Wolf","bio":"Hi"},"user")
        self.assertEqual(visual.title.get(),"Sea Wolf")
        self.assertIn("Hi",visual.bio.get("1.0","end-1c"))


if __name__=="__main__":
    unittest.main()
