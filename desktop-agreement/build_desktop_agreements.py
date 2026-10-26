#!/usr/bin/env python3
"""Build three fail-closed desktop TGC Terms releases from the official, already
published ZIPs. No network, live credentials, sessions, or deployment changes.
Always start with the exact known archives; CI audits ZIPs before publication.
"""
import argparse
import ast
import hashlib
import json
import os
import re
import sys
import zipfile
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
TERMS = ROOT / "desktop-agreement" / "TGC_DESKTOP_TERMS_2026-10-26.1.txt"
GATE = ROOT / "desktop-agreement" / "runtime_agreement.pyinc"

PACKAGES = {
    "standalone": dict(
        old="updates/standalone/TGC_Invite_Node_v1.1.10_Required_Update.zip",
        new="updates/standalone/TGC_Invite_Node_v1.1.11_Mandatory_Terms_Update.zip",
        py="tgc_invite_node.py", version="1.1.11",
    ),
    "plugin": dict(
        old="updates/plugin/TGC_Invite_Node_Plugin_v1.1.10-plugin_Required_Update.zip",
        new="updates/plugin/TGC_Invite_Node_Plugin_v1.1.11-plugin_Mandatory_Terms_Update.zip",
        py="plugins/distributed_invite_node.py", version="1.1.11-plugin",
    ),
    "staff_launcher": dict(
        old="updates/staff-launcher/TGC_Staff_Launcher_v1.1.5_Required_Logging_Warning.zip",
        new="updates/staff-launcher/TGC_Staff_Launcher_v1.1.6_Mandatory_Terms_Update.zip",
        py="TGC_Staff_Launcher_Package/TGC_Staff_Launcher.py",
        embedded="TGC_Staff_Launcher_Package/tgc_staff_node.py",
        version="1.1.6",
    ),
}


def replace_once(text, old, new, label):
    if text.count(old) != 1:
        raise AssertionError(f"{label}: expected exactly one source anchor, got {text.count(old)}")
    return text.replace(old, new, 1)


def apply_gate(kind, code, gate):
    if kind == "standalone":
        code = replace_once(code, 'APP_VERSION = "1.1.10"', 'APP_VERSION = "1.1.11"', "node version")
        code = replace_once(code, 'class TGCInviteNode:', gate + "\n\nclass TGCInviteNode:", "node gate")
        code = replace_once(code,
            '    TGCInviteNode(root)\n    root.mainloop()',
            '    if not tgc_terms_require(root, "standalone"):\n'
            '        root.destroy()\n        return\n'
            '    TGCInviteNode(root)\n    root.mainloop()', "node pre-init gate")
        code = replace_once(code, '    def start(self):\n        self.save_from_ui_silent()',
            '    def start(self):\n'
            '        if not tgc_terms_accepted("standalone"):\n'
            '            raise PermissionError("TGC Terms of Use not accepted.")\n'
            '        self.save_from_ui_silent()', "node collection guard")
        code = replace_once(code, '    def _sync_once(self):',
            '    def _sync_once(self):\n'
            '        if not tgc_terms_accepted("standalone"):\n'
            '            return', "node upload guard")
        code = replace_once(code, '    def redeem_deployment(self):',
            '    def redeem_deployment(self):\n'
            '        if not tgc_terms_accepted("standalone"):\n'
            '            raise PermissionError("TGC Terms of Use not accepted.")', "node deployment guard")
        code = replace_once(code,
            '        frame.pack(fill=tk.BOTH, expand=True, padx=12, pady=12)',
            '        frame.pack(fill=tk.BOTH, expand=True, padx=12, pady=12)\n'
            '        ttk.Button(frame, text="View Terms of Use",\n'
            '            command=lambda: tgc_terms_view(self.root)).pack(anchor=tk.W)',
            "node view terms")
    elif kind == "plugin":
        code = replace_once(code, 'NODE_VERSION = "1.1.10-plugin"',
            'NODE_VERSION = "1.1.11-plugin"', "plugin version")
        code = replace_once(code, 'class DistributedInviteNodePlugin(Plugin):',
            gate + "\n\nclass DistributedInviteNodePlugin(Plugin):", "plugin gate")
        code = replace_once(code,
            '    def initialize(self):\n        if self.app is not None and hasattr(self.app, "notebook"):\n            self.create_tab(self.app.notebook)',
            '    def initialize(self):\n'
            '        if self.app is not None and hasattr(self.app, "notebook"):\n'
            '            self.create_tab(self.app.notebook)\n'
            '            # The host application remains usable if the TGC plugin is declined.\n'
            '            if not tgc_terms_require(self.app.root, "plugin"):\n'
            '                return', "plugin init gate")
        code = replace_once(code, '    def start_node(self):',
            '    def start_node(self):\n'
            '        if not tgc_terms_accepted("plugin"):\n'
            '            if self.app is not None:\n'
            '                tgc_terms_require(self.app.root, "plugin")\n'
            '            if not tgc_terms_accepted("plugin"):\n'
            '                return', "plugin start gate")
        code = replace_once(code, '    def _sync_once(self):',
            '    def _sync_once(self):\n'
            '        if not tgc_terms_accepted("plugin"):\n'
            '            return', "plugin upload gate")
        code = replace_once(code, '    def redeem_deployment(self):',
            '    def redeem_deployment(self):\n'
            '        if not tgc_terms_accepted("plugin"):\n'
            '            if self.app is None or not tgc_terms_require(self.app.root, "plugin"):\n'
            '                return', "plugin deployment gate")
        code = replace_once(code,
            '        container.pack(fill=tk.BOTH, expand=True, padx=12, pady=12)',
            '        container.pack(fill=tk.BOTH, expand=True, padx=12, pady=12)\n'
            '        ttk.Button(container, text="View / Accept TGC Terms",\n'
            '            command=lambda: tgc_terms_require(self.app.root, "plugin") '
            'if not tgc_terms_accepted("plugin") else tgc_terms_view(self.app.root))'
            '.pack(anchor=tk.W)', "plugin manual gate access")
    elif kind == "staff_launcher":
        code = replace_once(code, 'APP_VERSION = "1.1.5"',
            'APP_VERSION = "1.1.6"', "staff version")
        code = replace_once(code, 'class StaffLauncher:',
            gate + "\n\nclass StaffLauncher:", "staff gate")
        code = replace_once(code,
            '    StaffLauncher(root)\n    root.mainloop()',
            '    if not tgc_terms_require(root, "staff_launcher"):\n'
            '        root.destroy()\n'
            '    else:\n'
            '        StaffLauncher(root)\n        root.mainloop()', "staff pre-init gate")
        code = replace_once(code,
            '    def activate_verified_launcher(self, data: dict):',
            '    def activate_verified_launcher(self, data: dict):\n'
            '        if not tgc_terms_accepted("staff_launcher"):\n'
            '            self.verification_failed("TGC Terms of Use not accepted.")\n'
            '            return', "staff embedded Node gate")
        code = replace_once(code,
            '    def build_settings_tab(self, tab):',
            '    def build_settings_tab(self, tab):\n'
            '        ttk.Button(tab, text="View Terms of Use",\n'
            '            command=lambda: tgc_terms_view(self.root)).pack(anchor="w", pady=5)',
            "staff view terms")
    else:
        raise AssertionError(f"Unknown scope: {kind}")
    ast.parse(code)
    for expected in ('tgc_terms_require', 'tgc_terms_accepted', 'tgc_terms_save_acceptance'):
        assert expected in code
    return code


def safe_source_zip(zf):
    names = [x.filename.replace("\\", "/") for x in zf.infolist()]
    assert len(names) == len(set(names)), "Duplicate entries in previous official ZIP"
    for name in names:
        assert not name.startswith("/") and ".." not in name.split("/"), f"Unsafe path {name}"


def make_one(kind, settings, terms, gate):
    old_path = ROOT / settings["old"]
    assert old_path.is_file(), old_path
    with zipfile.ZipFile(old_path) as zf:
        safe_source_zip(zf)
        entries = {info.filename: zf.read(info.filename) for info in zf.infolist()
                   if not info.is_dir()}
    # Approved old ZIPs contain runtime sources and examples but NO actual
    # deployment keys/cookies. Never include local user files or production state.
    path = settings["py"]
    assert path in entries
    source = entries[path].decode("utf-8")
    assert "tgc_terms_require" not in source
    entries[path] = apply_gate(kind, source, gate).encode("utf-8")
    if kind == "staff_launcher":
        p = settings["embedded"]
        src = entries[p].decode("utf-8")
        src = replace_once(src, 'NODE_VERSION = "1.1.10-staff"',
                           'NODE_VERSION = "1.1.11-staff"', "staff embedded version")
        ast.parse(src)
        entries[p] = src.encode("utf-8")
    if kind == "plugin":
        # The previous 1.1.10 updater requires this legacy diagnostic filename
        # for ZIP validation; it only installs the two pre-existing runtime files.
        # Keeping the agreement embedded in the main plugin file makes old
        # updaters compatible. The diagnostic is already embedded in that file.
        entries["tgc_node_diagnostics.py"] = (
            '"""Legacy update ZIP compatibility marker: read-only Node diagnostic\n'
            'logic is embedded in distributed_invite_node.py. No runtime imports."""\n'
        ).encode("utf-8")
    doc_path = ("TGC_Staff_Launcher_Package/" if kind == "staff_launcher" else "") + "TGC_DESKTOP_TERMS.txt"
    entries[doc_path] = terms.encode("utf-8")
    release_note = (
        f"REQUIRED TGC Desktop Terms of Use update\n"
        f"Channel: {kind}\nVersion: {settings['version']}\n\n"
        "All operators must read the displayed agreement, check the acceptance box,\n"
        "and click I Agree before operating the official TGC Node/Staff Launcher.\n"
        "Decline closes/blocks only the relevant TGC program; an optional plugin\n"
        "host may still run without the TGC Node. No existing account data, DPAPI\n"
        "keys, saved settings or SQLite sighting queue is deleted by this update.\n"
        "If the old updater copies only its known Python file(s), the full terms\n"
        "and fail-closed consent UI remain embedded in the updated entrypoint.\n"
    )
    entries[("TGC_Staff_Launcher_Package/" if kind == "staff_launcher" else "")+
        "REQUIRED_TERMS_UPDATE.txt"] = release_note.encode("utf-8")
    # Immutable output archive with original runtime files preserved, except
    # explicitly audited entrypoint changes and version marker.
    import io
    buf = io.BytesIO()
    with zipfile.ZipFile(buf, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as output:
        for name, data in entries.items():
            item = zipfile.ZipInfo(name, date_time=(2026, 10, 26, 0, 0, 0))
            item.compress_type = zipfile.ZIP_DEFLATED
            item.external_attr = 0o644 << 16
            output.writestr(item, data)
    archive = buf.getvalue()
    with zipfile.ZipFile(io.BytesIO(archive)) as verify:
        assert verify.testzip() is None
        assert verify.read(path) == entries[path]
        assert verify.read(doc_path).decode("utf-8") == terms
    return archive, entries


def build(dest, manifest_path=None):
    text = TERMS.read_text(encoding="utf-8")
    assert "Agreement version: 2026-10-26.1" in text and len(text)>9000
    raw_gate = GATE.read_text(encoding="utf-8")
    assert raw_gate.count("__TGC_TERMS_LITERAL__") == 1
    gate = raw_gate.replace("__TGC_TERMS_LITERAL__", repr(text))
    ast.parse(gate)
    dest.mkdir(parents=True, exist_ok=True)
    summary = {}
    for kind, settings in PACKAGES.items():
        archive, entries = make_one(kind, settings, text, gate)
        new = dest / settings["new"]
        new.parent.mkdir(parents=True, exist_ok=True)
        new.write_bytes(archive)
        sha = hashlib.sha256(archive).hexdigest()
        summary[kind] = dict(version=settings["version"], sha256=sha,
                             bytes=len(archive), path=settings["new"],
                             source_entrypoint=settings["py"],
                             entry_count=len(entries))
        print(f"PASS {kind}: {settings['version']} ZIP CRC/source/embedded agreement; {sha} bytes={len(archive)}")
    if manifest_path:
        manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
        for kind, item in summary.items():
            manifest["releases"][kind] = {
                "channel": kind,
                "version": item["version"],
                "url": "https://raw.githubusercontent.com/Seawolf035/TGC-Node-Updates/main/" + item["path"],
                "sha256": item["sha256"],
                "policy": "required",
                "notes": "Mandatory update: read and accept the TGC Desktop Terms of Use before using the official Node or Staff Launcher. Existing credentials, queues and settings are preserved.",
                "published_at": datetime.now(timezone.utc).isoformat(timespec="seconds").replace("+00:00", "Z"),
            }
        manifest_path.write_text(json.dumps(manifest, indent=2)+"\n", encoding="utf-8")
    (dest / "desktop_agreement_build_summary.json").write_text(
        json.dumps(summary, indent=2)+"\n", encoding="utf-8")
    return summary


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--dest", type=Path, required=True)
    parser.add_argument("--manifest", type=Path)
    args = parser.parse_args()
    build(args.dest.resolve(), args.manifest)
