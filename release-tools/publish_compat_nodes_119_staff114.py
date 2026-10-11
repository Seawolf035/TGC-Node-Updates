#!/usr/bin/env python3
"""Re-publish 1.1.9 as a backwards-compatible required hotfix, plus Staff Launcher 1.1.4."""
from pathlib import Path
from datetime import datetime, timezone
import hashlib
import json
import re
import tempfile
import zipfile

ROOT = Path(__file__).resolve().parents[1]
UPDATES = ROOT / "updates"

def once(data, before, after, label):
    count = data.count(before)
    if count != 1:
        raise RuntimeError(f"{label}: expected one match, found {count}")
    return data.replace(before, after, 1)

def read_zip(path):
    with zipfile.ZipFile(path) as archive:
        return {n: archive.read(n) for n in archive.namelist() if not n.endswith("/")}

def write_zip(path, files):
    with zipfile.ZipFile(path, "w", zipfile.ZIP_DEFLATED) as archive:
        for name, payload in sorted(files.items()):
            archive.writestr(name, payload)
    with zipfile.ZipFile(path) as archive:
        assert archive.testzip() is None
    return hashlib.sha256(path.read_bytes()).hexdigest()

diag = (ROOT / "release-tools/tgc_node_diagnostics_v119.py").read_text("utf-8")
for original, replacement in (
    ("JOIN_RE", "DIAG_JOIN_RE"), ("LEAVE_RE", "DIAG_LEAVE_RE"),
    ("TAIL_BYTES", "DIAG_TAIL_BYTES"), ("_inspect_log", "_diagnostic_inspect_log"),
):
    diag = re.sub(r"\b" + re.escape(original) + r"\b", replacement, diag)
diag = "\n\n# Integrated diagnostic: compatible with fixed-file legacy updaters.\n" + diag + "\n\n"
compile(diag, "embedded_tracking_diagnostic.py", "exec")
manifest_path = ROOT / "manifest.json"
readme_path = ROOT / "README.md"
manifest = json.loads(manifest_path.read_text("utf-8"))
readme = readme_path.read_text("utf-8")
published = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")

for channel, name, entry, version, prefix in (
    ("standalone", "TGC_Invite_Node_v1.1.9_Required_Update.zip",
     "tgc_invite_node.py", "1.1.9", "class _DATA_BLOB("),
    ("plugin", "TGC_Invite_Node_Plugin_v1.1.9-plugin_Required_Update.zip",
     "plugins/distributed_invite_node.py", "1.1.9-plugin", "class NodeLocalStore("),
):
    archive_path = UPDATES / channel / name
    files = read_zip(archive_path)
    source = files[entry].decode("utf-8")
    source = once(source, "from tgc_node_diagnostics import run_tracking_diagnostic\n",
                  "", f"{channel} import")
    source = once(source, "HOTFIX_REVISION = 0", "HOTFIX_REVISION = 1",
                  f"{channel} revision")
    source = once(source, prefix, diag + prefix, f"{channel} integration")
    # Python clears exception targets after an except block. Capture the type before UI scheduling.
    if "f\"Could not generate report: {type(error).__name__}\"" in source:
        source = once(source, "            except Exception as error:\n                def failed():",
                      "            except Exception as error:\n                failure_kind = type(error).__name__\n                def failed():",
                      f"{channel} async error capture")
        source = source.replace('f"Could not generate report: {type(error).__name__}"',
                                'f"Could not generate report: {failure_kind}"')
    if "f\"Could not generate report: {type(exc).__name__}\"" in source:
        source = once(source, "            except Exception as exc:\n                def failed():",
                      "            except Exception as exc:\n                failure_kind = type(exc).__name__\n                def failed():",
                      f"{channel} async error capture")
        source = source.replace('f"Could not generate report: {type(exc).__name__}"',
                                'f"Could not generate report: {failure_kind}"')
    compile(source, entry, "exec")
    assert "def run_tracking_diagnostic(" in source and "Diagnose Tracking" in source
    assert "from tgc_node_diagnostics import" not in source
    assert "def _release_available" in source
    files[entry] = source.encode("utf-8")
    files.pop("tgc_node_diagnostics.py", None)
    checksum = write_zip(archive_path, files)
    with zipfile.ZipFile(archive_path) as testzip:
        names = set(testzip.namelist())
        if channel == "standalone":
            assert {"tgc_invite_node.py", "tgc_vrchat_multilog.py", "tgc_node_update_helper.py"}.issubset(names)
        else:
            assert {"plugins/distributed_invite_node.py", "tgc_vrchat_multilog.py"} == names
    release = manifest["releases"][channel]
    release.update(
        version=version,
        sha256=checksum,
        hotfix_revision=1,
        policy="required",
        published_at=published,
        notes=("Required v1.1.9 compatibility hotfix: embedded Diagnose Tracking directly in the "
               "existing Node runtime so the original v1.1.8 updater installs it correctly. "
               "Quick Deployment, encrypted keys, invite sightings and queue are unchanged."),
    )
    heading = "### Standalone Node" if channel == "standalone" else "### Plugin Node"
    start = readme.index(heading)
    end = readme.find("\n### ", start + 4)
    if end == -1:
        end = readme.index("\nThe plugin package", start)
    section = readme[start:end]
    section = re.sub(r"(?m)^- SHA-256:.*$", f"- SHA-256: \x60{checksum}\x60", section)
    section += ("\n- REQUIRED 1.1.9 compatibility hotfix (revision 1): "
                "diagnostic is embedded in the previously supported runtime files, "
                "so 1.1.8 installations can self-update normally. A 1.1.9 "
                "installation that already failed to start may require manual repair.\n")
    readme = readme[:start] + section + readme[end:]
    print("PASS", channel, version, checksum)

staff_archive = UPDATES / "staff-launcher" / "TGC_Staff_Launcher_v1.1.3_Auto_Install_Python_Windows.zip"
staff_files = read_zip(staff_archive)
folder = "TGC_Staff_Launcher_Package/"
launcher_name = folder + "TGC_Staff_Launcher.py"
node_name = folder + "tgc_staff_node.py"
launcher = staff_files[launcher_name].decode("utf-8")
node = staff_files[node_name].decode("utf-8")
launcher = once(launcher, 'APP_VERSION = "1.1.3"',
                'APP_VERSION = "1.1.4"', "staff launcher version")
node = once(node, 'NODE_VERSION = "1.1.7-staff"',
            'NODE_VERSION = "1.1.9-staff"', "embedded Node version")
node = once(node, "class _DATA_BLOB(", diag + "class _DATA_BLOB(", "staff diagnostics")
node = once(node, "    def sync_now(self):\n",
'''    def tracking_diagnostic(self, output_dir=None, sample_seconds=10):
        """Sample logs without interrupting the embedded Invite Node."""
        snap = self.watcher.snapshot()
        _total, pending, _submitted = self.store.counts()
        stats = dict(seen=self.session_seen, new=self.session_new,
                     duplicates=self.session_duplicates, pending=pending)
        return run_tracking_diagnostic(
            log_dir=self.watcher.log_dir, output_dir=output_dir,
            sample_seconds=sample_seconds, watcher_snapshot=snap,
            session_stats=stats)

    def sync_now(self):
''', "staff service wrapper")
launcher = once(
    launcher,
    '        ttk.Button(actions, text="Recheck Verification", command=self.recheck_from_main_ui).pack(side="left", padx=6)\n',
    '        ttk.Button(actions, text="Recheck Verification", command=self.recheck_from_main_ui).pack(side="left", padx=6)\n'
    '        self.tracking_diagnostic_button = ttk.Button(actions, text="Diagnose Tracking", command=self.diagnose_tracking)\n'
    '        self.tracking_diagnostic_button.pack(side="left", padx=6)\n',
    "staff UI button",
)
launcher = once(
    launcher, "    def recheck_from_main_ui(self):\n",
'''    def diagnose_tracking(self):
        """Create a private tracking summary without blocking the window."""
        self.tracking_diagnostic_button.configure(state="disabled")
        self.log("Embedded Node tracking diagnostic started (10-second sample).")

        def worker():
            try:
                report, diagnosis = self.node_service.tracking_diagnostic(
                    output_dir=Path.home() / "Downloads", sample_seconds=10)
            except Exception as exc:
                error_type = type(exc).__name__
                def failed():
                    self.tracking_diagnostic_button.configure(state="normal")
                    self.log(f"Tracking diagnostic failed: {error_type}")
                    messagebox.showerror(APP_NAME, f"Tracking diagnostic failed: {error_type}")
                self.root.after(0, failed)
                return

            def completed():
                self.tracking_diagnostic_button.configure(state="normal")
                self.log("Embedded Node tracking report saved locally.")
                if messagebox.askyesno(
                    APP_NAME, f"{diagnosis}\\n\\nSaved report:\\n{report}\\n\\nOpen it now?"):
                    try:
                        os.startfile(report)
                    except (AttributeError, OSError):
                        pass
            self.root.after(0, completed)

        threading.Thread(target=worker, daemon=True, name="StaffTrackingDiagnostic").start()

    def recheck_from_main_ui(self):
''', "staff UI callback")
compile(launcher, launcher_name, "exec")
compile(node, node_name, "exec")
assert "def run_tracking_diagnostic(" in node and "Diagnose Tracking" in launcher
staff_files[launcher_name] = launcher.encode("utf-8")
staff_files[node_name] = node.encode("utf-8")
# The original v1.1.3 update helper only copies a fixed allowlist. Never add a new runtime module.
assert "tgc_node_diagnostics.py" not in staff_files
new_staff_name = "TGC_Staff_Launcher_v1.1.4_Required_Node_Diagnostics.zip"
new_staff_path = UPDATES / "staff-launcher" / new_staff_name
staff_sha = write_zip(new_staff_path, staff_files)
manifest["releases"]["staff_launcher"].update(
    version="1.1.4", policy="required", published_at=published,
    sha256=staff_sha,
    url=f"https://raw.githubusercontent.com/Seawolf035/TGC-Node-Updates/main/updates/staff-launcher/{new_staff_name}",
    notes=("Required Staff Launcher v1.1.4: embedded Invite Node v1.1.9-staff "
           "gains a Diagnose Tracking button that saves a privacy-safe report "
           "to Downloads. No new runtime module. Staff settings, DPAPI keys, "
           "queue, verification, and VR/OSC slots are preserved."),
)
start = readme.index("### Staff Launcher")
end = readme.find("\n### ", start + 4)
if end == -1:
    end = readme.index("\nThe plugin package", start)
section = readme[start:end]
section = section.replace("Staff Launcher 1.1.3", "Staff Launcher 1.1.4")
section = section.replace("TGC_Staff_Launcher_v1.1.3_Auto_Install_Python_Windows.zip", new_staff_name)
section = re.sub(r"(?m)^- SHA-256:.*$", f"- SHA-256: \x60{staff_sha}\x60", section)
section += ("\n- REQUIRED v1.1.4: the embedded Invite Node gains Diagnose Tracking; "
            "the v1.1.3 updater's fixed-file allowlist is fully supported.\n")
readme = readme[:start] + section + readme[end:]
manifest_path.write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
readme_path.write_text(readme, encoding="utf-8")
print("PASS staff launcher 1.1.4", staff_sha)
print("PASS release manifest required policies and source compilation")
