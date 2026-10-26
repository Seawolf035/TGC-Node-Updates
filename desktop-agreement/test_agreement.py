#!/usr/bin/env python3
"""Offline regression tests for the three mandatory desktop terms packages."""
import ast
import hashlib
import json
import os
import re
import tempfile
import zipfile
from pathlib import Path
from unittest import mock

from build_desktop_agreements import PACKAGES, ROOT, GATE, TERMS, build

def test():
    with tempfile.TemporaryDirectory() as td:
        work = Path(td)
        expected = json.loads((ROOT/"manifest.json").read_text())
        original_hashes = {kind: hashlib.sha256((ROOT/p["old"]).read_bytes()).hexdigest()
                           for kind,p in PACKAGES.items()}
        assert all(expected["releases"][kind]["sha256"] == sha
                   for kind,sha in original_hashes.items())
        report=build(work)
        assert set(report)==set(PACKAGES)
        for kind,p in PACKAGES.items():
            original=ROOT/p["old"]
            built=work/p["new"]
            assert built.stat().st_size>original.stat().st_size
            with zipfile.ZipFile(original) as prev, zipfile.ZipFile(built) as new:
                assert new.testzip() is None
                old_names={n for n in prev.namelist() if not n.endswith("/")}
                new_names=set(new.namelist())
                assert old_names.issubset(new_names)
                for name in old_names:
                    if name in (p["py"],p.get("embedded")):continue
                    assert prev.read(name)==new.read(name), (kind,name)
                code=new.read(p["py"]).decode("utf-8")
                ast.parse(code)
                assert "tgc_terms_require" in code
                assert 'TGC_DESKTOP_TERMS_VERSION = "2026-10-26.1"' in code
                assert 'TGC_DESKTOP_TERMS_SHA256' in code
                assert 'tgc_terms_save_acceptance' in code
                if kind=="standalone":
                    assert 'if not tgc_terms_require(root, "standalone")' in code
                    assert 'if not tgc_terms_accepted("standalone")' in code
                    assert 'APP_VERSION = "1.1.11"' in code
                elif kind=="plugin":
                    assert 'if not tgc_terms_require(self.app.root, "plugin")' in code
                    assert 'if not tgc_terms_accepted("plugin")' in code
                    assert "self.create_tab(self.app.notebook)" in code
                    assert 'NODE_VERSION = "1.1.11-plugin"' in code
                    # Existing v1.1.10-plugin download path validates this file!
                    assert "tgc_node_diagnostics.py" in new_names
                    assert "plugins/distributed_invite_node.py" in new_names
                    assert "tgc_vrchat_multilog.py" in new_names
                else:
                    assert 'if not tgc_terms_require(root, "staff_launcher")' in code
                    assert 'if not tgc_terms_accepted("staff_launcher")' in code
                    assert 'APP_VERSION = "1.1.6"' in code
                    assert 'NODE_VERSION = "1.1.11-staff"' in new.read(
                        p["embedded"]).decode("utf-8")
                doc = ("TGC_Staff_Launcher_Package/" if kind=="staff_launcher" else "")+"TGC_DESKTOP_TERMS.txt"
                assert new.read(doc).decode("utf-8") == TERMS.read_text()
        # Exercise evidence persistence headlessly: no UI, VRChat, Relay, or install.
        ns={}
        raw=GATE.read_text().replace("__TGC_TERMS_LITERAL__",repr(TERMS.read_text()))
        with mock.patch.dict(os.environ,{"LOCALAPPDATA":str(work/"WindowsUserA")}):
            exec(compile(raw,"terms-runtime","exec"),ns)
            assert not ns["tgc_terms_accepted"]("standalone")
            assert not ns["tgc_terms_accepted"]("plugin")
            assert not ns["tgc_terms_accepted"]("staff_launcher")
            ns["tgc_terms_save_acceptance"]("standalone")
            assert ns["tgc_terms_accepted"]("standalone")
            assert not ns["tgc_terms_accepted"]("plugin")
            ns["tgc_terms_save_acceptance"]("plugin")
            ns["tgc_terms_save_acceptance"]("staff_launcher")
            assert ns["tgc_terms_accepted"]("plugin")
            assert ns["tgc_terms_accepted"]("staff_launcher")
            f=ns["tgc_terms_record_path"]("standalone")
            record=json.loads(f.read_text())
            assert record["agreement_version"]=="2026-10-26.1"
            assert record["agreement_sha256"]==hashlib.sha256(
                TERMS.read_bytes()).hexdigest()
            assert record["program"]=="standalone"
            assert "accepted_at_utc" in record
            assert not any(x in f.read_text().lower()
                   for x in ["node_key","password","cookie","relay_url"])
            # Corrupt/delete/bogus version/altered text MUST fail closed.
            f.write_text("{bad json")
            assert not ns["tgc_terms_accepted"]("standalone")
            ns["tgc_terms_save_acceptance"]("standalone")
            ns["TGC_DESKTOP_TERMS_VERSION"]="later-version"
            assert not ns["tgc_terms_accepted"]("standalone")
            ns["TGC_DESKTOP_TERMS_VERSION"]="2026-10-26.1"
            ns["TGC_DESKTOP_TERMS_SHA256"]="0"*64
            assert not ns["tgc_terms_accepted"]("standalone")
            ns["TGC_DESKTOP_TERMS_SHA256"]=record["agreement_sha256"]
            assert ns["tgc_terms_accepted"]("standalone")
            with mock.patch.object(ns["_tgc_terms_getpass"],"getuser",return_value="OtherPerson"):
                assert not ns["tgc_terms_accepted"]("standalone")
        with mock.patch.dict(os.environ,{"LOCALAPPDATA":str(work/"WindowsUserB")}):
            assert not ns["tgc_terms_accepted"]("standalone")
        # Manual view does not save consent; only checkbox + explicit affirm can call save.
        assert "if not agreed.get():" in raw
        assert "tgc_terms_save_acceptance(scope)" in raw
        assert "root.wait_window(dlg)" in raw
        assert "dlg.protocol(\"WM_DELETE_WINDOW\", decline)" in raw
        assert "dlg.grab_set()" in raw
        # Public metadata should be mandatory on all three channels.
        output=work/"manifest.json"
        output.write_text((ROOT/"manifest.json").read_text())
        built=build(work/"second",output)
        published=json.loads(output.read_text())
        for kind,info in built.items():
            release=published["releases"][kind]
            assert release["policy"]=="required"
            assert release["sha256"]==info["sha256"]
            assert release["version"]==info["version"]
            assert release["url"].endswith(info["path"])
            assert "Terms of Use" in release["notes"]
        assert all(hashlib.sha256((ROOT/p["old"]).read_bytes()).hexdigest()
                   == original_hashes[k] for k,p in PACKAGES.items())
        print("PASS: consent gating, local user isolation, 3 legacy-updater-compatible ZIPs, mandatory manifest, queue/key preservation")
if __name__=="__main__":test()
