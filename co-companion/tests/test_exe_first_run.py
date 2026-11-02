"""Owner-only Windows first-run install: offline source and data safety."""
import json
from pathlib import Path
import tempfile
import unittest

from tgc_cocompanion.exe_first_run import stage_first_install, is_installed_exe
from tgc_cocompanion.owner_updater import APPLICATION, PreviewUpdateError

SHA = "c" * 40


def sample(path):
    path.mkdir(parents=True)
    (path / APPLICATION).write_bytes(b"MZ sample binary")
    (path / "_internal").mkdir()
    (path / "_internal" / "payload.dat").write_bytes(b"tcl")
    (path / "build_info.json").write_text(json.dumps({
        "sha": SHA, "version": "0.5.0", "channel": "owner-preview",
        "run_id": 123
    }), encoding="utf-8")


class FirstRunInstallTests(unittest.TestCase):
    def test_installs_side_by_side_without_touching_old_install(self):
        with tempfile.TemporaryDirectory() as td:
            base = Path(td)
            source = base / "extracted-download"
            sample(source)
            owner = base / "installed_versions"
            old = owner / "python-old"
            old.mkdir(parents=True)
            (old / "config").write_text("do not change")
            result = stage_first_install(source, owner)
            self.assertEqual(result.name, APPLICATION)
            self.assertTrue(result.is_file())
            self.assertTrue(is_installed_exe(result, owner))
            self.assertFalse(is_installed_exe(source / APPLICATION, owner))
            self.assertEqual((old / "config").read_text(), "do not change")
            self.assertEqual(stage_first_install(source, owner), result)

    def test_bad_metadata_refused(self):
        with tempfile.TemporaryDirectory() as td:
            base = Path(td)
            source = base / "source"
            sample(source)
            (source / "build_info.json").write_text('{"version":"0.5.0","sha":"bad"}')
            with self.assertRaisesRegex(PreviewUpdateError, "incomplete"):
                stage_first_install(source, base / "installed_versions")
            self.assertFalse((base / "installed_versions").exists())

    def test_missing_resources_refused(self):
        with tempfile.TemporaryDirectory() as td:
            source = Path(td) / "source"
            sample(source)
            (source / "_internal" / "payload.dat").unlink()
            (source / "_internal").rmdir()
            with self.assertRaises(PreviewUpdateError):
                stage_first_install(source, Path(td) / "installed_versions")


if __name__ == "__main__":
    unittest.main()
