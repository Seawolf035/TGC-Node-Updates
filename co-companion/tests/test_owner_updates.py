"""No-network owner updater SHA256 and staged rollback safety tests."""
import hashlib
import io
import json
from pathlib import Path
import tempfile
import unittest
import zipfile

from tgc_cocompanion.owner_updater import (
    PreviewBuild, PreviewUpdateError, APPLICATION, ARTIFACT_NAME, BRANCH,
    latest_preview, has_newer_build, stage_download,
)

SHA="a"*40
RUN=123456
URL="https://github.com/Seawolf035/TGC-Node-Updates/actions/runs/"+str(RUN)


def _zip_file(data:dict[str,bytes])->bytes:
    out=io.BytesIO()
    with zipfile.ZipFile(out,"w",zipfile.ZIP_DEFLATED) as arc:
        for name,blob in data.items():arc.writestr(name,blob)
    return out.getvalue()


class UpdateTests(unittest.TestCase):
    def _package(self,td,*,corrupt=False,evil=None):
        meta=json.dumps({"sha":("b"*40 if corrupt else SHA),"version":"0.3",
                         "channel":"owner-preview","run_id":RUN}).encode()
        inner=_zip_file({"build_info.json":meta,APPLICATION:b"MZ fake test executable",
                         "_internal/tcl/tcl8.6/init.tcl":b"test"} if evil is None else
                        {"../evil.exe":b"untrusted","build_info.json":meta})
        outer=_zip_file({"TGC_CoCompanion_OwnerPreview_v0.3_WINDOWS.zip":inner})
        path=Path(td)/"artifact.zip"
        path.write_bytes(outer)
        digest="sha256:"+hashlib.sha256(outer).hexdigest()
        return path,PreviewBuild(SHA,RUN,URL,77,digest,ARTIFACT_NAME)

    def test_update_lookup_is_exact_owner_branch_and_artifact(self):
        def api(url):
            if "workflows" in url:return {"workflow_runs":[{
                "conclusion":"success","head_sha":SHA,"id":RUN,
                "head_branch":BRANCH}]}
            return {"artifacts":[{
                "name":ARTIFACT_NAME,"id":77,"expired":False,"digest":"sha256:"+"0"*64}]}
        result=latest_preview(api)
        self.assertEqual(result.sha,SHA)
        self.assertEqual(result.artifact_name,ARTIFACT_NAME)
        self.assertEqual(result.run_url,URL)

    def test_other_branch_never_produces_update(self):
        def api(_):
            return {"workflow_runs":[{"conclusion":"success",
                    "head_sha":SHA,"id":RUN,"head_branch":"main"}]}
        with self.assertRaises(PreviewUpdateError):
            latest_preview(api)

    def test_compare_current_sha(self):
        candidate=PreviewBuild(SHA,RUN,URL,77,"sha256:"+"0"*64,ARTIFACT_NAME)
        self.assertFalse(has_newer_build({"sha":SHA},candidate))
        self.assertTrue(has_newer_build({"sha":"b"*40},candidate))

    def test_verified_outer_zip_staged_in_new_folder(self):
        with tempfile.TemporaryDirectory() as td:
            downloaded,build=self._package(td)
            output=Path(td)/"installed_versions"
            exe=stage_download(downloaded,build,output)
            self.assertTrue(exe.is_file())
            self.assertEqual(exe.parent.name,"owner-"+SHA[:16])
            self.assertEqual(stage_download(downloaded,build,output),exe)
            self.assertEqual(len(list(output.iterdir())),1)

    def test_rejects_tampered_package_without_writing(self):
        with tempfile.TemporaryDirectory() as td:
            downloaded,build=self._package(td)
            downloaded.write_bytes(downloaded.read_bytes()+b"tampered")
            with self.assertRaisesRegex(PreviewUpdateError,"digest"):
                stage_download(downloaded,build,Path(td)/"installed_versions")

    def test_rejects_wrong_embedded_commit(self):
        with tempfile.TemporaryDirectory() as td:
            downloaded,build=self._package(td,corrupt=True)
            with self.assertRaisesRegex(PreviewUpdateError,"metadata"):
                stage_download(downloaded,build,Path(td)/"installed_versions")

    def test_rejects_zip_traversal(self):
        with tempfile.TemporaryDirectory() as td:
            downloaded,build=self._package(td,evil=True)
            with self.assertRaisesRegex(PreviewUpdateError,"Unsafe archive path"):
                stage_download(downloaded,build,Path(td)/"installed_versions")
            self.assertFalse((Path(td)/"evil.exe").exists())

    def test_rejects_missing_checksum(self):
        with tempfile.TemporaryDirectory() as td:
            downloaded,build=self._package(td)
            empty=PreviewBuild(SHA,RUN,URL,77,"",ARTIFACT_NAME)
            with self.assertRaisesRegex(PreviewUpdateError,"checksum"):
                stage_download(downloaded,empty,Path(td)/"installed_versions")

    def test_rejects_extracted_inner_zip(self):
        with tempfile.TemporaryDirectory() as td:
            downloaded,build=self._package(td)
            with zipfile.ZipFile(downloaded) as archive:
                name=archive.namelist()[0]
                downloaded.write_bytes(archive.read(name))
            with self.assertRaises(PreviewUpdateError):
                stage_download(downloaded,build,Path(td)/"installed_versions")


if __name__=="__main__":
    unittest.main()
