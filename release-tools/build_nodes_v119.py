#!/usr/bin/env python3
"""Publish v1.1.9 Node ZIPs from the shipped v1.1.8 source archives."""
from pathlib import Path
from datetime import datetime, timezone
import ast
import hashlib
import importlib.util
import json
import os
import re
import tempfile
import zipfile

ROOT = Path(__file__).resolve().parents[1]
DIAGNOSTIC_SOURCE = ROOT / "release-tools" / "tgc_node_diagnostics_v119.py"

def one(s, before, after, label):
    n = s.count(before)
    if n != 1:
        raise RuntimeError(f"{label}: expected one match, got {n}")
    return s.replace(before, after, 1)

STANDALONE_METHOD = '''    def diagnose_tracking(self):
        """Run the built-in privacy-safe diagnostic without blocking the window."""
        self.diagnostic_btn.config(state=tk.DISABLED)
        self._log_recent("Tracking diagnostic started (10-second sample)")
        snapshot = self.watcher.snapshot()
        _total, pending, _submitted = self.store.counts()
        stats = dict(seen=self.session_seen, new=self.session_new,
                     duplicates=self.session_duplicates, pending=pending)

        def worker():
            try:
                report, diagnosis = run_tracking_diagnostic(
                    log_dir=self.watcher.log_dir,
                    output_dir=os.path.join(os.path.expanduser("~"), "Downloads"),
                    sample_seconds=10, watcher_snapshot=snapshot, session_stats=stats)
            except Exception as error:
                error_type = type(error).__name__
                def failed():
                    self.diagnostic_btn.config(state=tk.NORMAL)
                    self._log_recent("Tracking diagnostic failed")
                    messagebox.showerror("Tracking Diagnostic",
                                         f"Could not generate report: {error_type}")
                self.root.after(0, failed)
                return

            def completed():
                self.diagnostic_btn.config(state=tk.NORMAL)
                self._log_recent("Tracking diagnostic complete — report saved")
                if messagebox.askyesno(
                    "Tracking Diagnostic",
                    f"{diagnosis}\\n\\nReport saved to:\\n{report}\\n\\nOpen report now?"
                ):
                    try:
                        os.startfile(report)
                    except (AttributeError, OSError):
                        pass
            self.root.after(0, completed)

        threading.Thread(target=worker, daemon=True,
                         name="NodeTrackingDiagnostic").start()

'''

PLUGIN_METHOD = '''    def diagnose_tracking(self):
        """Run the built-in privacy-safe diagnostic in the background."""
        self.diagnostic_btn.config(state=tk.DISABLED)
        self._activity("Tracking diagnostic started (10-second sample)")
        snapshot = self.watcher.snapshot()
        _total, pending, _submitted = self.store.counts()
        stats = dict(seen=self.session_seen, new=self.session_new,
                     duplicates=self.session_duplicates, pending=pending)

        def worker():
            try:
                report, diagnosis = run_tracking_diagnostic(
                    log_dir=self.watcher.log_dir,
                    output_dir=os.path.join(os.path.expanduser("~"), "Downloads"),
                    sample_seconds=10, watcher_snapshot=snapshot, session_stats=stats)
            except Exception as error:
                error_type = type(error).__name__
                def failed():
                    self.diagnostic_btn.config(state=tk.NORMAL)
                    self._activity("Tracking diagnostic failed")
                    messagebox.showerror("Tracking Diagnostic",
                                         f"Could not generate report: {error_type}")
                self._ui(failed)
                return

            def completed():
                self.diagnostic_btn.config(state=tk.NORMAL)
                self._activity("Tracking diagnostic complete — report saved")
                if messagebox.askyesno(
                    "Tracking Diagnostic",
                    f"{diagnosis}\\n\\nReport saved to:\\n{report}\\n\\nOpen report now?"
                ):
                    try:
                        os.startfile(report)
                    except (AttributeError, OSError):
                        pass
            self._ui(completed)

        threading.Thread(target=worker, daemon=True,
                         name="PluginNodeTrackingDiagnostic").start()

'''

def validate_diagnostic(diag_source):
    compile(diag_source, "tgc_node_diagnostics.py", "exec")
    spec = importlib.util.spec_from_file_location("tgc_node_diagnostics_v119", DIAGNOSTIC_SOURCE)
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    with tempfile.TemporaryDirectory() as folder:
        logdir = Path(folder) / "logs"
        reportdir = Path(folder) / "reports"
        logdir.mkdir()
        reportdir.mkdir()
        (logdir / "output_log_2026-10-11_10-00-00.txt").write_text(
            "2026.10.11 10:00:00 Joining Room\n"
            "2026.10.11 10:00:01 OnPlayerJoined SecretName (usr_11111111-1111-1111-1111-111111111111)\n"
        )
        report, diagnosis = mod.run_tracking_diagnostic(
            log_dir=logdir, output_dir=reportdir, sample_seconds=0,
            watcher_snapshot={"running": True, "active_logs": 1, "active_players": 0},
            session_stats={"seen": 0, "new": 0, "pending": 0})
        body = Path(report).read_text()
        assert "SecretName" not in body and "usr_111" not in body
        assert "Recognized joins: 1" in body
        assert "zero players" in diagnosis.lower()
    print("PASS: diagnostic runs and excludes player names/IDs")

def main():
    diag_source = DIAGNOSTIC_SOURCE.read_text(encoding="utf-8")
    validate_diagnostic(diag_source)
    versions = [
        dict(channel="standalone", old="1.1.8", new="1.1.9",
             oldzip="TGC_Invite_Node_v1.1.8_Required_Update.zip",
             zip="TGC_Invite_Node_v1.1.9_Required_Update.zip",
             source="tgc_invite_node.py", const="APP_VERSION"),
        dict(channel="plugin", old="1.1.8-plugin", new="1.1.9-plugin",
             oldzip="TGC_Invite_Node_Plugin_v1.1.8-plugin_Required_Update.zip",
             zip="TGC_Invite_Node_Plugin_v1.1.9-plugin_Required_Update.zip",
             source="plugins/distributed_invite_node.py", const="NODE_VERSION"),
    ]
    manifest_path = ROOT / "manifest.json"
    readme_path = ROOT / "README.md"
    manifest = json.loads(manifest_path.read_text())
    readme = readme_path.read_text()
    for item in versions:
        channel = item["channel"]
        folder = ROOT / "updates" / channel
        with zipfile.ZipFile(folder / item["oldzip"]) as z:
            files = {n: z.read(n) for n in z.namelist() if not n.endswith("/")}
        source = item["source"]
        s = files[source].decode("utf-8")
        s = one(s, 'from tgc_vrchat_multilog import MultiVRChatLogWatcher\n',
                'from tgc_vrchat_multilog import MultiVRChatLogWatcher\n'
                'from tgc_node_diagnostics import run_tracking_diagnostic\n',
                f"{channel} diagnostics import")
        s = one(s, f'{item["const"]} = "{item["old"]}"',
                f'{item["const"]} = "{item["new"]}"',
                f"{channel} version")
        if channel == "standalone":
            s = one(s, '        self.stop_btn.pack(side=tk.LEFT, padx=6)\n',
                    '        self.stop_btn.pack(side=tk.LEFT, padx=6)\n'
                    '        self.diagnostic_btn = ttk.Button(actions, text="Diagnose Tracking", command=self.diagnose_tracking)\n'
                    '        self.diagnostic_btn.pack(side=tk.LEFT, padx=6)\n',
                    "standalone button")
            s = one(s, "    def save_from_ui(self):\n",
                    STANDALONE_METHOD + "    def save_from_ui(self):\n",
                    "standalone method")
            s = one(s,
                    '"tgc_invite_node.py", "tgc_vrchat_multilog.py", "tgc_node_update_helper.py",',
                    '"tgc_invite_node.py", "tgc_vrchat_multilog.py", "tgc_node_diagnostics.py", "tgc_node_update_helper.py",',
                    "standalone staged file allowlist")
            if "README_NODE.txt" in files:
                files["README_NODE.txt"] += (
                    "\nNODE v1.1.9 (REQUIRED)\n"
                    "- Diagnose Tracking button: 10-second read-only log inspection.\n"
                    "- Saves a report to Downloads, with no player names or account IDs.\n"
                    "- Quick Deployment and existing settings/queues are preserved.\n"
                ).encode("utf-8")
            if "START_HERE.txt" in files:
                files["START_HERE.txt"] = files["START_HERE.txt"].replace(b"1.1.8", b"1.1.9")
        else:
            s = one(s, '        self.stop_btn.pack(side=tk.LEFT, padx=6)\n',
                    '        self.stop_btn.pack(side=tk.LEFT, padx=6)\n'
                    '        self.diagnostic_btn = ttk.Button(action_frame, text="Diagnose Tracking", command=self.diagnose_tracking)\n'
                    '        self.diagnostic_btn.pack(side=tk.LEFT, padx=6)\n',
                    "plugin button")
            s = one(s, "    def _ui(self, function, *args, **kwargs):\n",
                    PLUGIN_METHOD + "    def _ui(self, function, *args, **kwargs):\n",
                    "plugin method")
            s = one(s,
                    'wanted = {"plugins/distributed_invite_node.py", "tgc_vrchat_multilog.py"}',
                    'wanted = {"plugins/distributed_invite_node.py", "tgc_vrchat_multilog.py", "tgc_node_diagnostics.py"}',
                    "plugin staged file allowlist")
            s = s.replace("Update ZIP does not contain both plugin runtime files.",
                          "Update ZIP does not contain all plugin runtime files.")
            s = s.replace('"Generate one-time Discord verification codes through the relay",',
                          '"Run privacy-safe tracking diagnostics directly from the Node tab",')
        compile(s, source, "exec")
        assert 'text="Link Discord"' not in s
        assert "def _release_available" in s and "hotfix_revision" in s
        assert "Diagnose Tracking" in s
        assert f'{item["const"]} = "{item["new"]}"' in s
        files[source] = s.encode("utf-8")
        files["tgc_node_diagnostics.py"] = diag_source.encode("utf-8")
        archive = folder / item["zip"]
        with zipfile.ZipFile(archive, "w", zipfile.ZIP_DEFLATED) as z:
            for name, payload in sorted(files.items()):
                z.writestr(name, payload)
        with zipfile.ZipFile(archive) as z:
            assert z.testzip() is None
            assert z.read(source) == files[source]
            assert z.read("tgc_node_diagnostics.py") == files["tgc_node_diagnostics.py"]
        sha = hashlib.sha256(archive.read_bytes()).hexdigest()
        manifest["releases"][channel].update({
            "version": item["new"],
            "url": f'https://raw.githubusercontent.com/Seawolf035/TGC-Node-Updates/main/updates/{channel}/{item["zip"]}',
            "sha256": sha,
            "policy": "required",
            "hotfix_revision": 0,
            "notes": "Required v1.1.9 update: adds a built-in Diagnose Tracking button that saves a privacy-safe VRChat log report to Downloads. Tracking, Quick Deployment, attribution, encrypted keys, and buffered queues remain unchanged.",
            "published_at": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
        })
        heading = "### Standalone Node" if channel == "standalone" else "### Plugin Node"
        a = readme.index(heading)
        b = readme.find("\n### ", a + 4)
        if b < 0:
            b = readme.index("\nThe plugin package", a)
        section = readme[a:b]
        section = section.replace(item["old"], item["new"]).replace(item["oldzip"], item["zip"])
        section = re.sub(r"(?m)^- SHA-256:.*$", f"- SHA-256: \x60{sha}\x60", section)
        section += "\n- REQUIRED v1.1.9: built-in Diagnose Tracking report, launched in background, with privacy-safe summary saved to Downloads.\n"
        readme = readme[:a] + section + readme[b:]
        print(f"{channel} {item['new']} sha256={sha}")
    manifest_path.write_text(json.dumps(manifest, indent=2) + "\n")
    readme_path.write_text(readme)
    assert manifest["releases"]["standalone"]["policy"] == "required"
    assert manifest["releases"]["plugin"]["policy"] == "required"
    print("PASS: required manifest entries, archives, parser syntax, diagnostic.")

if __name__ == "__main__":
    main()
