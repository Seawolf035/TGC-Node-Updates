"""Offline tests of the non-staff self-updating Windows EXE channel."""
from io import BytesIO
import hashlib
import json
from pathlib import Path
import tempfile
import unittest
from zipfile import ZipFile, ZIP_DEFLATED

from tgc_cocompanion.exe_release_updater import (
    ExeRelease, latest_exe_release, stage_exe_release, has_newer_exe_release,
    smoke_check_exe, ASSET, DOWNLOAD, TAG_PREFIX,
)
from tgc_cocompanion.owner_updater import APPLICATION, PreviewUpdateError

SHA = "a" * 40
TAG = TAG_PREFIX + "0.5.0"
URL = DOWNLOAD + TAG + "/" + ASSET
PAGE = "https://github.com/Seawolf035/TGC-Node-Updates/releases/tag/" + TAG


def archive(values):
    data = BytesIO()
    with ZipFile(data, "w", ZIP_DEFLATED) as zipped:
        for path, body in values.items():
            zipped.writestr(path, body)
    return data.getvalue()


def build_package(*, bad_identity=False, traversal=False):
    info = {"channel": "owner-preview", "version": "0.5.0",
            "sha": "b" * 40 if bad_identity else SHA, "run_id": 123}
    body = {APPLICATION: b"MZ synthetic smoke sample",
            "_internal/tcl/tcl8.6/init.tcl": b"mock",
            "build_info.json": json.dumps(info).encode("utf-8")}
    if traversal:
        body["../overwrite.txt"] = b"malicious"
    data = archive(body)
    return data, "sha256:" + hashlib.sha256(data).hexdigest()


class ExeUpdateTests(unittest.TestCase):
    def _release_meta(self, version="0.5.0", sha=SHA, digest=None):
        tag = TAG_PREFIX + version
        return {"draft": False, "prerelease": True, "tag_name": tag,
                "target_commitish": sha,
                "html_url": "https://github.com/Seawolf035/TGC-Node-Updates/releases/tag/" + tag,
                "assets": [{"name": ASSET, "size": 13_000_000,
                            "browser_download_url": DOWNLOAD + tag + "/" + ASSET,
                            "digest": digest or "sha256:" + "d" * 64}]}

    def test_requires_approved_channel_and_semver(self):
        valid = self._release_meta()
        bad = self._release_meta()
        bad["prerelease"] = False
        self.assertEqual(latest_exe_release(lambda: [bad, valid]).sha, SHA)
        self.assertFalse(has_newer_exe_release({"version": "0.5.0"},
                         latest_exe_release(lambda: [valid])))
        self.assertTrue(has_newer_exe_release({"version": "0.4"},
                        latest_exe_release(lambda: [valid])))
        with self.assertRaises(PreviewUpdateError):
            latest_exe_release(lambda: [bad])
        with self.assertRaises(PreviewUpdateError):
            latest_exe_release(lambda: [])

    def test_latest_semver_beats_listing_order(self):
        newer = self._release_meta("0.6.0", sha="b"*40)
        older = self._release_meta("0.5.0")
        release = latest_exe_release(lambda: [older, newer])
        self.assertEqual(release.version, "0.6.0")

    def test_source_must_be_expected_repository_url(self):
        bad = self._release_meta()
        bad["assets"][0]["browser_download_url"] = "https://example.com/fake.zip"
        with self.assertRaises(PreviewUpdateError):
            latest_exe_release(lambda: [bad])

    def test_stages_without_overwriting_previous_version(self):
        payload, digest = build_package()
        release = ExeRelease("0.5.0", SHA, URL, digest, PAGE)
        def opener(request, timeout=90):
            self.assertEqual(request.full_url, URL)
            return BytesIO(payload)
        with tempfile.TemporaryDirectory() as td:
            base = Path(td)
            old = base / "python-old"
            old.mkdir()
            (old / "data.txt").write_text("keep")
            exe = stage_exe_release(release, base, opener)
            self.assertTrue(exe.is_file())
            self.assertTrue((exe.parent / "_internal").is_dir())
            self.assertEqual((old / "data.txt").read_text(), "keep")
            self.assertEqual(stage_exe_release(release, base, opener), exe)

    def test_blocks_modified_download_and_wrong_commit(self):
        payload, digest = build_package()
        release = ExeRelease("0.5.0", SHA, URL, digest, PAGE)
        with tempfile.TemporaryDirectory() as td:
            with self.assertRaisesRegex(PreviewUpdateError, "checksum"):
                stage_exe_release(release, Path(td), lambda *_args, **_kwargs:
                                  BytesIO(payload + b"modified"))
            self.assertFalse(any(Path(td).glob("exe-*")))
        wrong, checksum = build_package(bad_identity=True)
        with tempfile.TemporaryDirectory() as td:
            with self.assertRaisesRegex(PreviewUpdateError, "identity"):
                stage_exe_release(ExeRelease("0.5.0", SHA, URL, checksum, PAGE),
                                  Path(td), lambda *_args, **_kwargs: BytesIO(wrong))

    def test_zip_traversal_rejected(self):
        payload, checksum = build_package(traversal=True)
        with tempfile.TemporaryDirectory() as td:
            with self.assertRaisesRegex(PreviewUpdateError, "Unsafe archive path"):
                stage_exe_release(ExeRelease("0.5.0", SHA, URL, checksum, PAGE),
                                  Path(td), lambda *_args, **_kwargs: BytesIO(payload))
            self.assertFalse((Path(td) / "overwrite.txt").exists())

    def test_smoke_check_fail_closed(self):
        class Outcome:
            returncode=1
            stderr="ModuleNotFoundError: missing module"
            stdout=""
        with tempfile.TemporaryDirectory() as td:
            target=Path(td)/APPLICATION
            target.write_bytes(b"MZ")
            with self.assertRaisesRegex(PreviewUpdateError, "missing module"):
                smoke_check_exe(target, runner=lambda *_args,**_kwargs:Outcome())


if __name__ == "__main__":
    unittest.main()
