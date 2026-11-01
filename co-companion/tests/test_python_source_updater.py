"""Offline Git blob-pinned Python self-update, safe staging and rollback tests."""
import hashlib
import json
from pathlib import Path
import tempfile
import unittest

from tgc_cocompanion.python_source_updater import (
    SOURCE_FILES, SourceBuild, PreviewUpdateError, _blob_sha, source_manifest,
    latest_python_preview, stage_python_update, has_newer_python_build,
    INSTALL_RECORD,
)

COMMIT = "a" * 40
TREE_ROOT = "b" * 40
TREE_PROJECT = "c" * 40
TREE_PACKAGE = "d" * 40
TREE_LEGACY = "e" * 40
BRANCH = "feature/co-companion-foundation"
BUILD = SourceBuild(COMMIT, 23456, "https://github.com/Seawolf035/TGC-Node-Updates/actions/runs/23456")


class FakeGit:
    def __init__(self, *, tamper=None, missing=None):
        self.blobs = {p: ("# " + p + "\n").encode("utf-8") for p in SOURCE_FILES}
        self.tamper = tamper
        self.missing = missing
        self.paths = []
        self.trees = {}
        for t in (TREE_ROOT, TREE_PROJECT, TREE_PACKAGE, TREE_LEGACY):
            self.trees[t] = {}
        self.add(TREE_ROOT, "co-companion", TREE_PROJECT, "tree")
        self.add(TREE_PROJECT, "tgc_cocompanion", TREE_PACKAGE, "tree")
        self.add(TREE_PACKAGE, "legacy", TREE_LEGACY, "tree")
        for path, body in self.blobs.items():
            if path == self.missing:
                continue
            if "/" not in path:
                tree = TREE_PROJECT
            elif path.startswith("tgc_cocompanion/legacy/"):
                tree = TREE_LEGACY
            else:
                tree = TREE_PACKAGE
            self.add(tree, path.split("/")[-1], _blob_sha(body), "blob", len(body))

    def add(self, tree, path, sha, typ, size=None):
        item = {"path": path, "sha": sha, "type": typ}
        if size is not None:
            item["size"] = size
        self.trees[tree][path] = item

    def api(self, url):
        self.paths.append(url)
        if "/git/commits/" in url:
            return {"tree": {"sha": TREE_ROOT}}
        if "/git/trees/" in url:
            sha = url.rsplit("/", 1)[-1]
            return {"tree": list(self.trees[sha].values()), "truncated": False}
        if "/actions/workflows/" in url:
            return {"workflow_runs": [{
                "head_branch": BRANCH, "head_sha": COMMIT, "id": BUILD.run_id,
                "conclusion": "success",
            }]}
        raise AssertionError("Unexpected API call: " + url)

    def raw(self, url, limit):
        self.paths.append(url)
        path = url.split("/co-companion/", 1)[1]
        body = self.blobs[path]
        if path == self.tamper:
            return body + b"tampered"
        return body


class PythonUpdateTests(unittest.TestCase):
    def test_only_successful_exact_owner_branch_qualifies(self):
        source = FakeGit()
        build = latest_python_preview(source.api)
        self.assertEqual(build.sha, COMMIT)
        self.assertFalse(has_newer_python_build({"sha": COMMIT}, build))
        self.assertTrue(has_newer_python_build({"sha": "f" * 40}, build))
        def wrong(_):
            return {"workflow_runs": [{"head_branch": "main", "head_sha": COMMIT,
                                       "id": 5, "conclusion": "success"}]}
        with self.assertRaises(PreviewUpdateError):
            latest_python_preview(wrong)

    def test_manifest_uses_immutable_tree_not_bulk_zipball(self):
        git = FakeGit()
        manifest = source_manifest(COMMIT, git.api)
        self.assertEqual(set(manifest), set(SOURCE_FILES))
        self.assertEqual(manifest["owner_pilot_entry.py"], _blob_sha(git.blobs["owner_pilot_entry.py"]))
        self.assertFalse(any("zipball" in url for url in git.paths))

    def test_installs_pinned_python_source_and_reuses_clean_install(self):
        git = FakeGit()
        with tempfile.TemporaryDirectory() as td:
            folder = Path(td)
            old = folder / "old-v02"
            old.mkdir()
            (old / "marker.txt").write_text("unchanged")
            entry = stage_python_update(BUILD, folder, git.api, git.raw)
            self.assertTrue(entry.is_file())
            self.assertEqual(entry.parent.name, "python-" + COMMIT[:16])
            self.assertTrue((entry.parent / INSTALL_RECORD).is_file())
            info = json.loads((entry.parent / "build_info.json").read_text())
            self.assertEqual(info["source_format"], "python")
            self.assertEqual(info["run_id"], BUILD.run_id)
            self.assertEqual((old / "marker.txt").read_text(), "unchanged")
            git.paths.clear()
            self.assertEqual(stage_python_update(BUILD, folder, git.api, git.raw), entry)
            self.assertFalse(git.paths, "No extra API calls when clean build already exists")

    def test_tampered_blob_cannot_replace_old_version(self):
        git = FakeGit(tamper="tgc_cocompanion/owner_pilot.py")
        with tempfile.TemporaryDirectory() as td:
            with self.assertRaisesRegex(PreviewUpdateError, "integrity mismatch"):
                stage_python_update(BUILD, Path(td), git.api, git.raw)
            self.assertFalse((Path(td) / ("python-" + COMMIT[:16])).exists())

    def test_missing_required_file_rejected_before_download(self):
        git = FakeGit(missing="tgc_cocompanion/desktop_update_page.py")
        with tempfile.TemporaryDirectory() as td:
            with self.assertRaisesRegex(PreviewUpdateError, "Missing owner-preview file"):
                stage_python_update(BUILD, Path(td), git.api, git.raw)

    def test_tampered_local_install_is_never_trusted(self):
        git = FakeGit()
        with tempfile.TemporaryDirectory() as td:
            entry = stage_python_update(BUILD, Path(td), git.api, git.raw)
            entry.write_text("corrupted")
            with self.assertRaisesRegex(PreviewUpdateError, "integrity checks"):
                stage_python_update(BUILD, Path(td), git.api, git.raw)


if __name__ == "__main__":
    unittest.main()
