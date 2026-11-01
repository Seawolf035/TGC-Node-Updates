"""Owner-only staged self-update from the ONE existing GitHub Actions channel.

No staff updater manifest, no production deployment, no extra server, no
GitHub PAT or Relay credentials. Browser handles GitHub Actions sign-in. This
app verifies the downloaded outer artifact ZIP against GitHub's public digest
and a pinned successful workflow run/commit, stages a NEW installation folder,
then launches it on explicit request. The current EXE and AppData enrollment
are untouched and the previous build remains available for rollback.
"""
from __future__ import annotations

from dataclasses import dataclass
import hashlib
import io
import json
import os
from pathlib import Path, PurePosixPath
import re
import shutil
import stat
import sys
import tempfile
from urllib.request import Request, urlopen
from zipfile import ZipFile, BadZipFile

REPOSITORY="Seawolf035/TGC-Node-Updates"
BRANCH="feature/co-companion-foundation"
WORKFLOW="co-companion-foundation.yml"
ARTIFACT_NAME="TGC-CoCompanion-v0.3-OWNER-EXE-NOT-STAFF"
APPLICATION="TGC_CoCompanion_OwnerPreview_v0.3.exe"
_MAX_ZIP=240_000_000
_MAX_OUTPUT=550_000_000
_MAX_FILES=3500
_HEX=re.compile(r"[a-f0-9]{40}\Z")
_API="https://api.github.com/repos/"+REPOSITORY


class PreviewUpdateError(RuntimeError):
    pass


@dataclass(frozen=True)
class PreviewBuild:
    sha: str
    run_id: int
    run_url: str
    artifact_id: int
    artifact_digest: str
    artifact_name: str

    @property
    def short(self):
        return self.sha[:12]


def _get_json(url: str) -> object:
    if not url.startswith(_API+"/") or "?" in url and any(x in url for x in ("..","\r","\n")):
        raise PreviewUpdateError("Untrusted update API location")
    req=Request(url,headers={"Accept":"application/vnd.github+json",
                             "User-Agent":"TGC-CoCompanion-OwnerPreview/0.3"})
    try:
        with urlopen(req,timeout=15) as response:
            raw=response.read(300_001)
    except Exception as exc:
        raise PreviewUpdateError("Could not contact official preview build channel") from exc
    if len(raw)>300_000:
        raise PreviewUpdateError("GitHub update response unexpectedly large")
    try:return json.loads(raw.decode("utf-8"))
    except (ValueError,UnicodeError) as exc:
        raise PreviewUpdateError("Invalid GitHub update response") from exc


def latest_preview(api_get=_get_json) -> PreviewBuild:
    endpoint=(_API+"/actions/workflows/"+WORKFLOW+
              "/runs?branch="+BRANCH+"&status=success&per_page=8")
    data=api_get(endpoint)
    if not isinstance(data,dict) or not isinstance(data.get("workflow_runs"),list):
        raise PreviewUpdateError("Unexpected GitHub Actions workflow response")
    for run in data["workflow_runs"]:
        if not isinstance(run,dict) or run.get("conclusion")!="success":
            continue
        sha=run.get("head_sha")
        rid=run.get("id")
        if not isinstance(sha,str) or not _HEX.fullmatch(sha) or type(rid) is not int or rid<=0:
            continue
        if run.get("head_branch")!=BRANCH:
            continue
        # Inspect ONLY the successful run's artifacts, never other repos.
        result=api_get(_API+f"/actions/runs/{rid}/artifacts?per_page=100")
        if not isinstance(result,dict) or not isinstance(result.get("artifacts"),list):
            continue
        for artifact in result["artifacts"]:
            if not isinstance(artifact,dict) or artifact.get("name")!=ARTIFACT_NAME:
                continue
            if artifact.get("expired") is not False:
                continue
            artifact_id=artifact.get("id")
            digest=artifact.get("digest") or ""
            if type(artifact_id) is not int or artifact_id<=0:
                continue
            if digest and not re.fullmatch(r"sha256:[a-f0-9]{64}",digest):
                raise PreviewUpdateError("Unrecognized artifact digest")
            return PreviewBuild(sha,rid,
                f"https://github.com/{REPOSITORY}/actions/runs/{rid}",
                artifact_id,digest,ARTIFACT_NAME)
    raise PreviewUpdateError("No completed owner-preview installer available")


def bundled_build_info() -> dict:
    bases=[]
    if hasattr(sys,"_MEIPASS"):
        bases.append(Path(sys._MEIPASS))
    bases.append(Path(__file__).resolve().parent.parent)
    for base in bases:
        candidate=base/"build_info.json"
        try:
            value=json.loads(candidate.read_text(encoding="utf-8"))
            if isinstance(value,dict) and value.get("channel")=="owner-preview" and (
                    isinstance(value.get("sha"),str)):
                return value
        except (OSError,ValueError):
            pass
    return {"version":"0.2","channel":"legacy-owner-preview","sha":""}


def has_newer_build(current: dict, candidate: PreviewBuild) -> bool:
    return current.get("sha")!=candidate.sha


def _safe_entries(archive:ZipFile) -> list:
    entries=archive.infolist()
    if not entries or len(entries)>_MAX_FILES:
        raise PreviewUpdateError("Unexpected owner-preview archive contents")
    seen=set()
    total=0
    for e in entries:
        raw=e.filename
        if not isinstance(raw,str) or "\\" in raw or raw.startswith("/") or "\0" in raw:
            raise PreviewUpdateError("Unsafe archive path")
        parts=PurePosixPath(raw).parts
        if not parts or any(p in (".","..") for p in parts) or ":" in parts[0]:
            raise PreviewUpdateError("Unsafe archive path")
        name="/".join(parts).casefold()
        if name in seen:
            raise PreviewUpdateError("Duplicate archive path")
        seen.add(name)
        if ((e.external_attr >> 16) & 0o170000)==stat.S_IFLNK:
            raise PreviewUpdateError("Archive symlink rejected")
        if e.file_size>_MAX_OUTPUT or e.compress_size>_MAX_ZIP:
            raise PreviewUpdateError("Oversized owner-preview archive member")
        total+=e.file_size
        if total>_MAX_OUTPUT:
            raise PreviewUpdateError("Owner-preview archive too large")
    return entries


def _extract(archive:ZipFile,destination:Path):
    for info in _safe_entries(archive):
        path=destination.joinpath(*PurePosixPath(info.filename).parts)
        if info.is_dir():
            path.mkdir(parents=True,exist_ok=True)
            continue
        path.parent.mkdir(parents=True,exist_ok=True)
        with archive.open(info) as input_stream,open(path,"xb") as output:
            shutil.copyfileobj(input_stream,output,1024*1024)


def stage_download(downloaded:Path,build:PreviewBuild,install_root:Path) -> Path:
    """Verify GitHub's OUTER artifact, inspect nested ZIP, then stage only.

    Caller may launch returned EXE after closing app; this function NEVER runs
    executable, overwrites the currently running app or touches the Node key.
    """
    if not isinstance(downloaded,Path) or not downloaded.is_file():
        raise PreviewUpdateError("Select the original downloaded GitHub artifact ZIP")
    if downloaded.stat().st_size>_MAX_ZIP:
        raise PreviewUpdateError("Downloaded archive exceeds safe size")
    digest=hashlib.sha256()
    with downloaded.open("rb") as stream:
        for chunk in iter(lambda:stream.read(1024*1024),b""):
            digest.update(chunk)
    if build.artifact_digest and "sha256:"+digest.hexdigest()!=build.artifact_digest:
        raise PreviewUpdateError(
            "The selected ZIP does not match GitHub's official artifact digest. "
            "Select the OUTER ZIP downloaded directly from GitHub Actions.")
    # When GitHub omits a digest, refuse silent installation rather than
    # trusting an arbitrary executable ZIP from Downloads.
    if not build.artifact_digest:
        raise PreviewUpdateError("GitHub did not provide an artifact checksum; installation disabled")
    install_root.mkdir(parents=True,exist_ok=True)
    target=install_root/("owner-"+build.sha[:16])
    if target.exists():
        exe=target/APPLICATION
        if exe.is_file() and _validated_build_file(target/"build_info.json",build):
            return exe
        raise PreviewUpdateError("Existing update directory failed integrity verification")
    with tempfile.TemporaryDirectory(prefix=".tgc-update-",dir=str(install_root)) as td:
        stage=Path(td)
        try:
            with ZipFile(downloaded) as outer:
                items=[z for z in _safe_entries(outer) if not z.is_dir()]
                if len(items)!=1 or not items[0].filename.endswith("_WINDOWS.zip"):
                    raise PreviewUpdateError("Select the untouched outer owner-preview GitHub artifact ZIP")
                if items[0].file_size>_MAX_ZIP:
                    raise PreviewUpdateError("Nested owner-preview archive too large")
                inner=outer.read(items[0])
            with ZipFile(io.BytesIO(inner)) as release:
                _extract(release,stage)
        except (BadZipFile,OSError) as exc:
            raise PreviewUpdateError("The selected owner-preview ZIP is damaged") from exc
        exe=stage/APPLICATION
        if not exe.is_file():
            raise PreviewUpdateError("The owner-preview Windows executable was missing")
        if not _validated_build_file(stage/"build_info.json",build):
            raise PreviewUpdateError("Build metadata does not match the verified GitHub commit")
        # Same volume atomic directory rename; no live application overwritten.
        os.replace(stage,target)
    return target/APPLICATION


def _validated_build_file(path:Path,build:PreviewBuild)->bool:
    try:
        data=json.loads(path.read_text(encoding="utf-8"))
    except (OSError,ValueError):
        return False
    return (isinstance(data,dict) and data.get("channel")=="owner-preview"
            and data.get("sha")==build.sha and data.get("version")=="0.3"
            and data.get("run_id")==build.run_id)


def update_root() -> Path:
    from .pilot_enrollment import get_pilot_dir
    return get_pilot_dir()/"installed_versions"
