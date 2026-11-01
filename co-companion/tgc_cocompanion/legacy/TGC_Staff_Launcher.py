import hashlib
import json
import math
import os
import shutil
import sys
import tempfile
import socket
import struct
import subprocess
import threading
import time
import tkinter as tk
import urllib.error
import urllib.request
import zipfile
from pathlib import Path
from tkinter import filedialog, messagebox, ttk

import psutil
import win32con
import win32gui
import win32process

from tgc_staff_node import StaffNodeService, NODE_VERSION

APP_NAME = "TGC Staff Launcher"
APP_VERSION = "1.1.6"
INSTANCE_COUNT = 3
BASE_DIR = Path(__file__).resolve().parent
CONFIG_FILE = BASE_DIR / "tgc_staff_launcher_config.json"

UPDATE_MANIFEST_URL = "https://raw.githubusercontent.com/Seawolf035/TGC-Node-Updates/main/manifest.json"
UPDATE_CHANNEL = "staff_launcher"
UPDATE_MAX_BYTES = 100 * 1024 * 1024
UPDATE_DIR = BASE_DIR / "staff_launcher_updates"
UPDATE_HELPER = BASE_DIR / "staff_launcher_update_helper.py"

DEFAULT_CONFIG = {
    "program_path": r"C:\Program Files (x86)\Steam\steamapps\common\VRChat\launch.exe",
    "launch_delay_seconds": 45,
    "auto_tile": True,
    "vr_instance": 0,
    "autostart_repeater": True,
    "repeater_listen_ip": "0.0.0.0",
    "repeater_listen_port": 9000,
    "base_instance_in_port": 9001,
    "base_instance_out_port": 9101,
    "osc_ip": "127.0.0.1",
}


def load_config() -> dict:
    data = DEFAULT_CONFIG.copy()
    if CONFIG_FILE.exists():
        try:
            raw = json.loads(CONFIG_FILE.read_text(encoding="utf-8"))
            if isinstance(raw, dict):
                data.update(raw)
        except Exception:
            pass
    return data


def save_config(data: dict) -> None:
    temp = CONFIG_FILE.with_suffix(".json.tmp")
    temp.write_text(json.dumps(data, indent=2), encoding="utf-8")
    temp.replace(CONFIG_FILE)


def pad_osc_string(value: str) -> bytes:
    data = value.encode("utf-8") + b"\0"
    while len(data) % 4:
        data += b"\0"
    return data


def make_float_packet(address: str, value: float) -> bytes:
    return pad_osc_string(address) + pad_osc_string(",f") + struct.pack(">f", float(value))


def disable_udp_connreset(sock: socket.socket) -> None:
    try:
        sock.ioctl(0x9800000C, False)
    except Exception:
        pass


# Shared template inserted into each desktop entrypoint by the release builder.
# Pure Python 3.8+; no external dependencies, no Relay call.
import getpass as _tgc_terms_getpass
import hashlib as _tgc_terms_hashlib
import json as _tgc_terms_json
import os as _tgc_terms_os
from pathlib import Path as _TgcTermsPath
from datetime import datetime as _TgcTermsDatetime, timezone as _TgcTermsTimezone
from tkinter import ttk as _tgc_terms_ttk, messagebox as _tgc_terms_messagebox
import tkinter as _tgc_terms_tk

TGC_DESKTOP_TERMS_VERSION = "2026-10-26.1"
TGC_DESKTOP_TERMS_TEXT = 'THE GAMER CLUB\nDESKTOP NODES AND STAFF LAUNCHER\nTERMS OF USE AND LIMITED SOFTWARE LICENSE\n\nAgreement version: 2026-10-26.1\nEffective date: October 26, 2026\nCreator: Seawolf (public creator name)\nCopyright © 2026 Seawolf for original material lawfully owned by Seawolf.\nThird-party materials remain owned and licensed by their respective rights holders.\n\nPLEASE READ THIS AGREEMENT BEFORE USING THE OFFICIAL SOFTWARE.\n\nThese terms govern the official TGC Standalone Invite Node, TGC Invite Node Plugin, TGC Staff Launcher, their embedded Nodes, updates, and the related The Gamer Club (TGC) invitation network services ("Desktop Software"). The Desktop Software is distinct from the TGC Staff Companion Android/Quest application, which has its own agreement. This agreement does not govern unrelated VRChat applications, third-party plugin hosts, or independent uses of third-party components under their own licenses.\n\nBy affirmatively selecting "I Agree" in the Desktop Software, you accept these terms for your own authorized use of the applicable TGC program. If you decline, close the terms window, or cannot load the agreement, the relevant TGC Node or Staff Launcher feature must not start or upload information. The host application of an optional Node plugin may continue operating without the TGC plugin.\n\n1. SCOPE AND PERMISSION\n\n1.1. Seawolf licenses original protectable TGC Desktop Software components only to the extent Seawolf has the legal authority to do so.\n1.2. Subject to this Agreement, applicable law, and any current TGC staff authorization, you receive a limited, personal, nonexclusive, nontransferable, revocable permission to install and operate official Desktop Software for approved TGC duties or testing.\n1.3. Agreement acceptance does not grant membership, staff rank, Node enrollment, moderation power, a Relay key, permission to operate central inviters, or rights to another person\'s VRChat credentials.\n1.4. TGC may limit or revoke access to TGC-controlled services as permitted by law and applicable policies.\n1.5. The permission is not a transfer of ownership and does not cover another party\'s intellectual property beyond rights given by that party.\n\n2. OWNERSHIP AND THIRD-PARTY COMPONENTS\n\n2.1. Seawolf retains the original rights Seawolf lawfully holds in original TGC source code, interface art, documentation, brand assets and other contributions.\n2.2. Other contributors and upstream projects retain their own intellectual-property rights. The Node Plugin integrates with a third-party VRChat automation host; it does not change that host\'s license or claim exclusive ownership of its code.\n2.3. Any applicable open-source license, hosting-service right, mandatory legal exception, right of interoperability, fair use or independently developed work is preserved. Public availability of a ZIP or repository does not automatically grant an unrestricted license to unrelated proprietary TGC components.\n2.4. TGC trademarks, staff badges, service access, names and verification identities may not be used to impersonate an official product or person.\n2.5. Nothing here grants ownership of VRChat, other users\' avatars, third-party tools, plugins, assets, data or services.\n\n3. OFFICIAL USE AND SECURITY\n\n3.1. Use only your own assigned TGC staff identity, accounts and deployment links. Do not transfer or disclose active Relay keys, private setup URLs, Windows DPAPI material, passwords, cookies or authentication tokens.\n3.2. Do not forge collected sightings, staff identities, account relationships, verification responses, invitation credits, remote commands or Relay events.\n3.3. Do not bypass revocation, an enrollment gate, required updates, rate limits, logging authorization or other TGC service access controls.\n3.4. A Node is a collector, not an official moderation system or independent authority to issue group invitations. It does not authorize its operator to scrape unrelated data, make unauthorized VRChat API requests, or bypass platform security.\n3.5. Report suspected leaked credentials, compromised devices, fake staff access or misuse to TGC management using the usual official staff channels.\n\n4. LOCAL LOG COLLECTION AND NETWORK DATA\n\n4.1. When voluntarily enabled and authorized, the Node reads relevant VRChat desktop output logs on your local machine and detects player-join observations.\n4.2. The Node may record VRChat user IDs, display names, observation timestamps, and local Node attribution; it may send selected sightings and related operational records to TGC\'s configured Relay for the invitation network. It does not need to transmit full output logs as part of normal collection.\n4.3. Observations can remain in the device\'s local retry queue while the internet or Relay is unavailable. Once uploaded, central TGC systems may store received observations and use them for authorized invite processing. A fixed central deletion/retention period is NOT promised by this Agreement.\n4.4. The operator must follow TGC privacy and staff rules and applicable law. Your acceptance is for your own operation of this tool; it does not purport to consent on behalf of other VRChat users whose activities are observed.\n4.5. The Software may send limited device/node status, staff attribution, version and verification information needed for network security and maintenance. It must not be used as a general-purpose surveillance service.\n4.6. Protect any data visible in staff dashboards, logs and local SQLite databases. Do not publish identifiable player histories, export stored identities for unrelated purposes, or distribute private group activity without authorization.\n\n5. PERMISSIONS, ACCOUNTS AND DEVICES\n\n5.1. The authorized TGC staff/Relay enrollment process remains separate from this license. A deployment link or valid token does not override these terms, and acceptance alone does not enroll a Node.\n5.2. You are responsible for keeping a Windows account and local TGC data directories reasonably secure and for using the tools only from systems you are permitted to operate.\n5.3. Acceptance is recorded locally for the current Windows user and applicable program type with the agreement version and content hash. The local record is not a VRChat credential or proof of continuing staff status.\n5.4. Each individual operator must affirmatively agree before using these official TGC features. Do not share an already-unlocked user session with someone else as a substitute for their own acceptance.\n5.5. On an amended agreement version, changed agreement content, new Windows user, or deleted/invalid acceptance record, the gate may require new affirmative acceptance.\n\n6. STAFF LAUNCHER AND OSC\n\n6.1. Staff Launcher provides limited authorized staff access to local VRChat instance management, optional OSC controls and an embedded Node.\n6.2. VRChat controls, OS processes, ports, microphone input, OSC chatbox changes and avatar scaling may be constrained by the operating system, the client, worlds, network conditions and third-party limits.\n6.3. Follow all TGC staff role policies, instance limits, age/safety rules and moderation procedures. Cosmetic features, a valid agreement or access to an OSC button do not grant moderation privilege.\n6.4. Do not use automated VRChat features to evade platform restrictions or interfere with other members.\n\n7. SOFTWARE AND UPDATE INTEGRITY\n\n7.1. Use the official TGC release sources and normal update channels. Validate update provenance and expected package integrity.\n7.2. Important software updates can be marked REQUIRED. When required, the supported installer may pause ordinary network activity until the official update is installed.\n7.3. Back up user-owned data before replacing program files. Keep local node configuration, DPAPI-protected keys, saved settings, SQLite queues, verification records, and other account-specific data. Do not copy another staff member\'s credentials.\n7.4. Local technical troubleshooting and authorized security testing remain allowed where permission or law provides it. Do not use test builds to impersonate official production releases.\n7.5. TGC may discontinue or change services, subject to any applicable legal obligations; continued compatibility with future VRChat or Windows releases is not guaranteed.\n\n8. THIRD-PARTY RULES\n\n8.1. You remain responsible for complying with VRChat\'s applicable Terms, Community Guidelines, API rules, and any separate terms governing Steam, Windows, Discord, GitHub and other integrated services.\n8.2. These TGC terms cannot confer permissions or exceptions prohibited by a third-party provider. Nothing here supersedes valid mandatory legal protections.\n\n9. SAFE OPERATION AND USER CONDUCT\n\n9.1. Use reasonable care to prevent account compromise, accidental disclosure, unauthorized deployments, misuse of personal data and disruption of TGC events.\n9.2. Do not access other people\'s private files, impersonate members, send unauthorized invitations or attempt to interfere with staff verification.\n9.3. Stop using the affected TGC network function if your authorization is revoked, a system explicitly requires a new version, or you discover an active compromise.\n\n10. SERVICE DISCLAIMERS\n\n10.1. To the extent permitted by applicable law, official Desktop Software is provided "AS IS" and "AS AVAILABLE" without guarantees of uninterrupted operation, fitness for a particular purpose or successful delivery of every log event.\n10.2. A local queue record or Relay acknowledgment is not proof that any invitation was sent or received.\n10.3. Connectivity, VRChat log output, Windows and antivirus rules, account access, API policies and third-party application changes may interrupt operation.\n\n11. LIABILITY\n\n11.1. To the extent applicable law permits, the rights holders are not responsible for indirect or consequential losses from interruption, third-party outages, user misconfiguration or unauthorized actions.\n11.2. Nothing excludes liability that cannot lawfully be excluded, restricts statutory protections that cannot be waived, or imposes an unlawful forfeiture of rights.\n\n12. TERMINATION AND DECLINING\n\n12.1. You may decline by selecting "Decline" or closing the agreement dialog. The TGC program should remain inactive until you later choose to agree.\n12.2. If TGC terminates your staff or Node authorization, this agreement does not override that revocation.\n12.3. Removing local acceptance is not the same as deleting enrolled keys, accounts or user data. Existing local operational records should remain available for authorized recovery and deletion procedures.\n\n13. CHANGES TO THESE TERMS\n\n13.1. A materially changed agreement must display its updated content and obtain a new affirmative acceptance before the affected TGC program resumes use.\n13.2. Merely visiting a repository, viewing source, downloading an archive, or using a third-party plugin host does not by itself constitute assent to this click-through Agreement.\n13.3. Acceptance evidence is stored locally, not automatically sent as a new personal-data event to the Relay.\n\n14. GOVERNING LAW AND SEVERABILITY\n\n14.1. Rights and obligations are subject to applicable law and mandatory protections. No mandatory right is waived by an overbroad clause.\n14.2. If one provision is invalid or unenforceable, the remaining lawful provisions continue to the extent permitted.\n14.3. This document does not invent an arbitration obligation, waive class-action rights, or select a jurisdiction without additional notice and agreement.\n\n15. QUESTIONS\n\n15.1. For authorization, operation or agreement questions, contact the appropriate TGC owner or designated staff administrator using the established TGC staff support channels.\n15.2. Do not include passwords, session cookies, private deployment links or other active secrets in routine support messages.\n\nBY CHECKING "I HAVE READ AND AGREE" AND PRESSING "I AGREE", YOU ACKNOWLEDGE THIS AGREEMENT AND AFFIRM YOUR OWN ACCEPTANCE FOR THE SELECTED TGC PROGRAM.\n'
TGC_DESKTOP_TERMS_SHA256 = _tgc_terms_hashlib.sha256(
    TGC_DESKTOP_TERMS_TEXT.encode("utf-8")
).hexdigest()
_TGC_TERMS_SCOPES = frozenset(("standalone", "plugin", "staff_launcher"))


def tgc_terms_record_path(scope):
    if scope not in _TGC_TERMS_SCOPES:
        raise ValueError("Unrecognized TGC terms scope.")
    # LOCALAPPDATA belongs to this Windows user; not the installation or Node key.
    # Per-scope evidence is independent for Node, plugin, and Staff Launcher.
    local = _tgc_terms_os.environ.get("LOCALAPPDATA")
    base = _TgcTermsPath(local) if local else _TgcTermsPath.home() / ".local" / "share"
    return base / "TheGamerClub" / "DesktopAgreements" / (scope + ".json")


def tgc_terms_accepted(scope):
    try:
        record = _tgc_terms_json.loads(
            tgc_terms_record_path(scope).read_text(encoding="utf-8")
        )
        return (
            isinstance(record, dict)
            and record.get("agreement_version") == TGC_DESKTOP_TERMS_VERSION
            and record.get("agreement_sha256") == TGC_DESKTOP_TERMS_SHA256
            and record.get("program") == scope
            and record.get("windows_user") == _tgc_terms_getpass.getuser()
            and bool(record.get("accepted_at_utc"))
        )
    except (OSError, ValueError, TypeError):
        return False


def tgc_terms_save_acceptance(scope):
    target = tgc_terms_record_path(scope)
    target.parent.mkdir(parents=True, exist_ok=True)
    payload = {
        "agreement_version": TGC_DESKTOP_TERMS_VERSION,
        "agreement_sha256": TGC_DESKTOP_TERMS_SHA256,
        "program": scope,
        "windows_user": _tgc_terms_getpass.getuser(),
        "accepted_at_utc": _TgcTermsDatetime.now(_TgcTermsTimezone.utc).isoformat(),
    }
    # Atomic commit so a cancelled or interrupted write never grants acceptance.
    import tempfile as _tgc_terms_tempfile
    with _tgc_terms_tempfile.NamedTemporaryFile(
        "w", encoding="utf-8", dir=str(target.parent), suffix=".tmp", delete=False
    ) as tmp:
        temp_name = tmp.name
        _tgc_terms_json.dump(payload, tmp, indent=2)
        tmp.write("\n")
        tmp.flush()
        _tgc_terms_os.fsync(tmp.fileno())
    try:
        _tgc_terms_os.replace(temp_name, target)
    finally:
        if _tgc_terms_os.path.exists(temp_name):
            _tgc_terms_os.remove(temp_name)
    if not tgc_terms_accepted(scope):
        raise RuntimeError("Could not verify saved agreement acceptance.")


def _tgc_terms_dialog(root, scope, required):
    if not isinstance(TGC_DESKTOP_TERMS_TEXT, str) or len(TGC_DESKTOP_TERMS_TEXT) < 2500:
        _tgc_terms_messagebox.showerror("TGC agreement error",
            "The agreement is missing or incomplete. TGC features cannot start.")
        return False
    dlg = _tgc_terms_tk.Toplevel(root)
    dlg.title("The Gamer Club - Terms of Use" if required else "TGC Terms of Use")
    dlg.geometry("800x610")
    dlg.minsize(600, 420)
    dlg.configure(bg="#142338")
    accepted = {"value": False}
    def decline():
        accepted["value"] = False
        dlg.destroy()
    dlg.protocol("WM_DELETE_WINDOW", decline)
    outer = _tgc_terms_ttk.Frame(dlg, padding=15)
    outer.pack(fill="both", expand=True)
    _tgc_terms_ttk.Label(
        outer, text="TGC Desktop Software Agreement", font=("Segoe UI", 17, "bold")
    ).pack(anchor="w", pady=(0, 5))
    _tgc_terms_ttk.Label(
        outer, text="Read the terms below before enabling this TGC program."
        if required else "Current terms for this TGC desktop program.",
        wraplength=730,
    ).pack(anchor="w", pady=(0, 8))
    area = _tgc_terms_ttk.Frame(outer)
    area.pack(fill="both", expand=True)
    scroller = _tgc_terms_ttk.Scrollbar(area, orient="vertical")
    readout = _tgc_terms_tk.Text(
        area, wrap="word", height=22, width=90,
        font=("Segoe UI", 10), padx=10, pady=10,
        yscrollcommand=scroller.set
    )
    scroller.configure(command=readout.yview)
    scroller.pack(side="right", fill="y")
    readout.pack(side="left", fill="both", expand=True)
    readout.insert("1.0", TGC_DESKTOP_TERMS_TEXT)
    readout.configure(state="disabled")
    if required:
        agreed = _tgc_terms_tk.BooleanVar(value=False)
        check = _tgc_terms_ttk.Checkbutton(
            outer, text="I have read and agree to the TGC Desktop Software Terms of Use",
            variable=agreed
        )
        check.pack(anchor="w", pady=(12, 7))
        _tgc_terms_ttk.Label(
            outer, text="Declining will leave this TGC software inactive. "
            "Your existing credentials, settings and queued sightings are preserved.",
            wraplength=710,
        ).pack(anchor="w", pady=(0, 5))
    buttons = _tgc_terms_ttk.Frame(outer)
    buttons.pack(fill="x", pady=(6, 0))
    _tgc_terms_ttk.Button(
        buttons, text="Decline" if required else "Close",
        command=decline
    ).pack(side="right", padx=(8, 0))
    if required:
        def affirm():
            if not agreed.get():
                _tgc_terms_messagebox.showwarning(
                    "Please confirm acceptance",
                    "You must check the agreement box before pressing I Agree.",
                    parent=dlg,
                )
                return
            try:
                tgc_terms_save_acceptance(scope)
            except Exception as exc:
                _tgc_terms_messagebox.showerror(
                    "Cannot save agreement",
                    "The agreement could not be saved. TGC features remain inactive.\n"
                    + str(exc), parent=dlg
                )
                return
            accepted["value"] = True
            dlg.destroy()
        _tgc_terms_ttk.Button(
            buttons, text="I Agree", command=affirm
        ).pack(side="right")
    dlg.lift()
    dlg.focus_force()
    dlg.grab_set()
    root.wait_window(dlg)
    return bool(accepted["value"] and tgc_terms_accepted(scope)) if required else False


def tgc_terms_require(root, scope):
    if tgc_terms_accepted(scope):
        return True
    return _tgc_terms_dialog(root, scope, required=True)


def tgc_terms_view(root):
    return _tgc_terms_dialog(root, "standalone", required=False)


class StaffLauncher:
    def __init__(self, root: tk.Tk):
        self.root = root
        self.config = load_config()
        self.processes: dict[int, psutil.Process] = {}
        self.repeater_running = False
        self.repeater_socket: socket.socket | None = None
        self.send_socket = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        disable_udp_connreset(self.send_socket)
        self.pulse_running = False
        self.status_labels: dict[int, ttk.Label] = {}
        self.pid_labels: dict[int, ttk.Label] = {}
        self.mode_labels: dict[int, ttk.Label] = {}

        root.title(f"{APP_NAME} v{APP_VERSION}")
        root.geometry("940x760")
        root.minsize(840, 650)
        root.protocol("WM_DELETE_WINDOW", self.on_close)
        root.withdraw()

        self.ui_built = False
        self.setup_window = None
        self.verification_status_var = None
        self.deployment_var = None
        self.node_status_snapshot = {}
        self.pre_ui_logs = []
        self.latest_update = None
        self.update_check_in_progress = False
        self.update_install_in_progress = False
        self.verified_access = False
        self.node_service = StaffNodeService(
            BASE_DIR,
            status_callback=self.on_node_status,
            log_callback=self.log,
        )

        self.program_path = tk.StringVar(value=str(self.config["program_path"]))
        self.delay_seconds = tk.IntVar(value=int(self.config["launch_delay_seconds"]))
        self.auto_tile = tk.BooleanVar(value=bool(self.config["auto_tile"]))
        try:
            configured_vr_instance = int(self.config.get("vr_instance", 0))
        except Exception:
            configured_vr_instance = 0
        if configured_vr_instance not in range(0, INSTANCE_COUNT + 1):
            configured_vr_instance = 0
        self.vr_instance_index = configured_vr_instance
        self.vr_instance = tk.IntVar(value=configured_vr_instance)
        self.autostart_repeater = tk.BooleanVar(value=bool(self.config["autostart_repeater"]))
        self.current_height = tk.DoubleVar(value=1.60)
        self.pulse_min = tk.DoubleVar(value=1.20)
        self.pulse_max = tk.DoubleVar(value=2.20)
        self.pulse_speed = tk.DoubleVar(value=0.45)
        self.forward_chatbox = tk.BooleanVar(value=True)
        self.packet_count = 0

        self.build_verification_gate()
        if self.node_service.has_credentials():
            self.root.after(500, self.verify_existing_installation)
        else:
            self.set_verification_status("Not deployed — paste the one-time Staff Launcher setup link from a TGC owner.")

    def build_verification_gate(self, reason: str = ""):
        self.verified_access = False

        try:
            if self.setup_window and self.setup_window.winfo_exists():
                if reason:
                    self.set_verification_status(reason)
                self.setup_window.deiconify()
                self.setup_window.lift()
                return
        except Exception:
            pass

        win = tk.Toplevel(self.root)
        self.setup_window = win
        win.title(f"{APP_NAME} — TGC Verification")
        win.geometry("700x430")
        win.resizable(False, False)
        win.protocol("WM_DELETE_WINDOW", self.on_close)

        frame = ttk.Frame(win, padding=18)
        frame.pack(fill="both", expand=True)
        ttk.Label(frame, text="TGC Staff Launcher Verification", font=("Segoe UI", 18, "bold")).pack(anchor="w")
        ttk.Label(
            frame,
            text=(
                f"Staff Launcher v{APP_VERSION} • Embedded Invite Node v{NODE_VERSION}\n\n"
                "This launcher is locked until it is verified by the TGC relay. New installs must use the one-time "
                "Staff Launcher deployment link issued from the main launcher's Deploy Nodes tab. There is no manual "
                "Node ID/Key bypass."
            ),
            wraplength=650,
            justify="left",
        ).pack(anchor="w", pady=(8, 14))

        deploy_box = ttk.LabelFrame(frame, text="One-Time Deployment Link", padding=10)
        deploy_box.pack(fill="x")
        self.deployment_var = tk.StringVar()
        ttk.Entry(deploy_box, textvariable=self.deployment_var).pack(fill="x", pady=(0, 8))
        buttons = ttk.Frame(deploy_box)
        buttons.pack(fill="x")
        ttk.Button(buttons, text="Verify Existing Installation", command=self.verify_existing_installation).pack(side="left", padx=(0, 6))
        ttk.Button(buttons, text="Deploy & Verify", command=self.redeem_staff_deployment).pack(side="left", padx=6)
        ttk.Button(buttons, text="Clear Deployment", command=self.clear_staff_deployment).pack(side="right")

        self.verification_status_var = tk.StringVar(value=reason or "Waiting for verification…")
        ttk.Label(
            frame,
            textvariable=self.verification_status_var,
            wraplength=650,
            justify="left",
            font=("Segoe UI", 10, "bold"),
        ).pack(anchor="w", pady=(16, 6))
        ttk.Label(
            frame,
            text="If this PC's node is revoked or disabled centrally, the Staff Launcher will not unlock on its next verification check.",
            wraplength=650,
            justify="left",
        ).pack(anchor="w")

    def set_verification_status(self, text: str):
        if self.verification_status_var is not None:
            try:
                self.verification_status_var.set(text)
            except Exception:
                pass

    def verify_existing_installation(self):
        self.set_verification_status("Checking TGC verification…")
        self.background(self._verify_existing_worker)

    def _verify_existing_worker(self):
        try:
            data = self.node_service.verify()
            self.root.after(0, lambda d=data: self.activate_verified_launcher(d))
        except Exception as error:
            self.root.after(0, lambda e=error: self.verification_failed(e))

    def redeem_staff_deployment(self):
        value = str(self.deployment_var.get() if self.deployment_var is not None else "").strip()
        if not value:
            messagebox.showerror(APP_NAME, "Paste the full one-time Staff Launcher deployment link from a TGC owner.", parent=self.setup_window)
            return
        self.set_verification_status("Redeeming deployment and verifying staff access…")
        self.background(self._redeem_staff_deployment_worker, value)

    def _redeem_staff_deployment_worker(self, value: str):
        try:
            data = self.node_service.redeem_deployment(value)
            self.root.after(0, lambda d=data: self.activate_verified_launcher(d))
        except Exception as error:
            self.root.after(0, lambda e=error: self.verification_failed(e))

    def verification_failed(self, error):
        self.verified_access = False
        self.root.withdraw()
        self.build_verification_gate(f"Verification failed: {error}")
        self.set_verification_status(f"Verification failed: {error}")

    def clear_staff_deployment(self):
        if not messagebox.askyesno(
            APP_NAME,
            "Clear this PC's local Staff Launcher deployment credentials?\n\nThis does not revoke the node centrally. A new owner-issued deployment link will be required to unlock it again.",
            parent=self.setup_window,
        ):
            return
        try:
            self.verified_access = False
            self.node_service.clear_credentials()
            if self.deployment_var is not None:
                self.deployment_var.set("")
            self.set_verification_status("Local deployment cleared. Paste a new Staff Launcher deployment link.")
        except Exception as error:
            messagebox.showerror(APP_NAME, f"Could not clear deployment:\n\n{error}", parent=self.setup_window)

    def activate_verified_launcher(self, data: dict):
        if not tgc_terms_accepted("staff_launcher"):
            self.verification_failed("TGC Terms of Use not accepted.")
            return
        if not data.get("verified"):
            self.verification_failed("Relay did not return verified status.")
            return
        if str(data.get("client_type") or "").strip().lower() != "staff_launcher":
            self.verification_failed("This deployment is not a Staff Launcher deployment.")
            return
        if not str(data.get("discord_id") or "").strip():
            self.verification_failed("No verified staff Discord account is attached to this deployment.")
            return

        self.verified_access = True

        try:
            if self.setup_window and self.setup_window.winfo_exists():
                self.setup_window.destroy()
        except Exception:
            pass
        self.setup_window = None
        self.verification_status_var = None

        if not self.ui_built:
            self.build_ui()
            self.ui_built = True
            self.refresh_status()
            self.root.after(2500, self.status_loop)
            if self.autostart_repeater.get():
                self.root.after(500, self.start_repeater)

        self.root.deiconify()
        self.root.lift()
        try:
            self.node_service.start()
        except Exception as error:
            self.verification_failed(error)
            return
        node_name = str(data.get("node_name") or self.node_service.config.get("node_name") or "Verified Staff Node")
        discord_id = str(data.get("discord_id") or self.node_service.config.get("discord_id") or "")
        self.root.title(f"{APP_NAME} v{APP_VERSION} — {node_name}")
        self.log(f"Verified Staff Launcher access for Discord {discord_id}; embedded node v{NODE_VERSION} started.")
        self.on_node_status(self.node_status_snapshot or {})
        self.root.after(10 * 60 * 1000, self.periodic_verification_check)
        self.root.after(1500, lambda: self.check_for_updates(silent=True))

    def periodic_verification_check(self):
        if not self.ui_built:
            return
        self.background(self._periodic_verification_worker)

    def _periodic_verification_worker(self):
        try:
            self.node_service.verify()
            self.root.after(10 * 60 * 1000, self.periodic_verification_check)
        except Exception as error:
            def lock():
                self.verified_access = False
                try:
                    self.node_service.stop()
                except Exception:
                    pass
                if self.repeater_running:
                    self.stop_repeater()
                self.root.withdraw()
                self.build_verification_gate(f"Verification lost: {error}")
            self.root.after(0, lock)

    def on_node_status(self, status: dict):
        self.node_status_snapshot = dict(status or {})
        if not self.ui_built:
            return
        def update():
            if hasattr(self, "node_identity_var"):
                name = status.get("node_name") or status.get("node_id") or "—"
                self.node_identity_var.set(str(name))
                self.node_version_var.set(str(status.get("node_version") or NODE_VERSION))
                self.node_discord_var.set(str(status.get("discord_id") or "—"))
                self.node_verification_var.set("Verified" if status.get("verified") else "NOT VERIFIED")
                self.node_relay_var.set(str(status.get("relay") or "—"))
                watcher_message = str(status.get("watcher") or "")
                if watcher_message.startswith("WARNING: VRChat logging"):
                    self.node_logs_var.set(watcher_message)
                else:
                    self.node_logs_var.set(f"{status.get('active_logs', 0)} active VRChat log(s) • {status.get('active_players', 0)} tracked player(s)")
                self.node_queue_var.set(
                    f"Queued: {status.get('pending', 0)} • Submitted: {status.get('submitted', 0)} • Total: {status.get('total', 0)}"
                )
        self.root.after(0, update)

    def build_node_tab(self, tab):
        ttk.Label(tab, text="Embedded Invite Node", font=("Segoe UI", 14, "bold")).pack(anchor="w")
        ttk.Label(
            tab,
            text=(
                "This node watches all active VRChat output logs on this PC and submits sightings to the TGC relay. "
                "Its permanent key is protected with Windows DPAPI and is never shown in this launcher."
            ),
            wraplength=760,
            justify="left",
        ).pack(anchor="w", pady=(0, 12))

        info = ttk.LabelFrame(tab, text="Verified Node Identity", padding=10)
        info.pack(fill="x")
        self.node_identity_var = tk.StringVar(value="—")
        self.node_version_var = tk.StringVar(value=NODE_VERSION)
        self.node_discord_var = tk.StringVar(value="—")
        self.node_verification_var = tk.StringVar(value="Verified")
        self.node_relay_var = tk.StringVar(value="—")
        self.node_logs_var = tk.StringVar(value="Waiting for VRChat logs…")
        self.node_queue_var = tk.StringVar(value="Queued: 0 • Submitted: 0 • Total: 0")
        rows = [
            ("Node:", self.node_identity_var),
            ("Node version:", self.node_version_var),
            ("Staff Discord ID:", self.node_discord_var),
            ("Verification:", self.node_verification_var),
            ("Relay:", self.node_relay_var),
            ("VRChat logs:", self.node_logs_var),
            ("Queue:", self.node_queue_var),
        ]
        for i, (label, var) in enumerate(rows):
            ttk.Label(info, text=label, width=18).grid(row=i, column=0, sticky="nw", padx=(0, 8), pady=3)
            ttk.Label(info, textvariable=var, wraplength=620, justify="left").grid(row=i, column=1, sticky="w", pady=3)
        info.columnconfigure(1, weight=1)

        actions = ttk.Frame(tab)
        actions.pack(fill="x", pady=(12, 0))
        ttk.Button(actions, text="Sync Now", command=self.node_service.sync_now).pack(side="left", padx=(0, 6))
        ttk.Button(actions, text="Recheck Verification", command=self.recheck_from_main_ui).pack(side="left", padx=6)
        self.tracking_diagnostic_button = ttk.Button(actions, text="Diagnose Tracking", command=self.diagnose_tracking)
        self.tracking_diagnostic_button.pack(side="left", padx=6)
        ttk.Label(actions, text="The node starts automatically after verification.").pack(side="right")
        self.on_node_status(self.node_status_snapshot)

    def diagnose_tracking(self):
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
                    APP_NAME, f"{diagnosis}\n\nSaved report:\n{report}\n\nOpen it now?"):
                    try:
                        os.startfile(report)
                    except (AttributeError, OSError):
                        pass
            self.root.after(0, completed)

        threading.Thread(target=worker, daemon=True, name="StaffTrackingDiagnostic").start()

    def recheck_from_main_ui(self):
        self.log("Rechecking Staff Launcher verification…")
        self.background(self._recheck_from_main_worker)

    def _recheck_from_main_worker(self):
        try:
            data = self.node_service.verify()
            self.root.after(0, lambda: messagebox.showinfo(APP_NAME, f"Verification confirmed for Discord {data.get('discord_id', '')}."))
        except Exception as error:
            self.root.after(0, lambda e=error: self.verification_failed(e))

    @staticmethod
    def _version_key(value: str):
        text = str(value or "").strip().lower().lstrip("v")
        base = text.split("-", 1)[0]
        parts = []
        for item in base.split(".")[:4]:
            try:
                parts.append(int(item))
            except Exception:
                parts.append(0)
        while len(parts) < 4:
            parts.append(0)
        return tuple(parts)

    def _set_update_ui(self, *, latest=None, policy=None, status=None, notes=None, can_install=None):
        def apply():
            if hasattr(self, "update_latest_var") and latest is not None:
                self.update_latest_var.set(str(latest))
            if hasattr(self, "update_policy_var") and policy is not None:
                self.update_policy_var.set(str(policy).title())
            if hasattr(self, "update_status_var") and status is not None:
                self.update_status_var.set(str(status))
            if hasattr(self, "update_notes_var") and notes is not None:
                self.update_notes_var.set(str(notes))
            if hasattr(self, "update_install_button") and can_install is not None:
                self.update_install_button.configure(state=("normal" if can_install else "disabled"))
        try:
            self.root.after(0, apply)
        except Exception:
            pass

    def _fetch_staff_update_release(self):
        req = urllib.request.Request(
            UPDATE_MANIFEST_URL,
            headers={"User-Agent": f"TGC-Staff-Launcher/{APP_VERSION}"},
            method="GET",
        )
        try:
            with urllib.request.urlopen(req, timeout=20) as response:
                length = response.headers.get("Content-Length")
                if length and int(length) > 2 * 1024 * 1024:
                    raise RuntimeError("GitHub update manifest is unexpectedly large.")
                raw = response.read(2 * 1024 * 1024 + 1)
        except Exception as exc:
            raise RuntimeError(f"Could not check GitHub for Staff Launcher updates: {exc}") from exc
        if len(raw) > 2 * 1024 * 1024:
            raise RuntimeError("GitHub update manifest is unexpectedly large.")
        try:
            manifest = json.loads(raw.decode("utf-8"))
            release = dict((manifest.get("releases") or {}).get(UPDATE_CHANNEL) or {})
        except Exception as exc:
            raise RuntimeError("GitHub returned an invalid Staff Launcher update manifest.") from exc
        version = str(release.get("version") or "").strip()
        url = str(release.get("url") or "").strip()
        sha256 = str(release.get("sha256") or "").strip().lower()
        policy = str(release.get("policy") or "optional").strip().lower()
        notes = str(release.get("notes") or "").strip()
        if not version:
            raise RuntimeError("The GitHub manifest does not currently define a Staff Launcher release.")
        if not url.lower().startswith("https://"):
            raise RuntimeError("The Staff Launcher update URL is not HTTPS.")
        if len(sha256) != 64 or any(ch not in "0123456789abcdef" for ch in sha256):
            raise RuntimeError("The Staff Launcher update SHA-256 in GitHub is invalid.")
        if policy not in {"optional", "recommended", "required"}:
            policy = "optional"
        return {
            "version": version,
            "url": url,
            "sha256": sha256,
            "policy": policy,
            "notes": notes,
        }

    def check_for_updates(self, silent=False):
        if not self.verified_access or not self.node_service.verified_session:
            if not silent:
                messagebox.showerror(APP_NAME, "Staff Launcher updates are only available after TGC verification.")
            return
        if self.update_check_in_progress:
            return
        self.update_check_in_progress = True
        self._set_update_ui(status="Checking GitHub…", can_install=False)
        self.background(self._check_for_updates_worker, bool(silent))

    def _check_for_updates_worker(self, silent=False):
        try:
            release = self._fetch_staff_update_release()
            self.latest_update = release
            current = self._version_key(APP_VERSION)
            newest = self._version_key(release["version"])
            available = newest > current
            if available:
                status = f"Update available: v{release['version']}"
                if release["policy"] == "required":
                    status += " — REQUIRED"
                elif release["policy"] == "recommended":
                    status += " — Recommended"
                self._set_update_ui(
                    latest=release["version"],
                    policy=release["policy"],
                    status=status,
                    notes=release["notes"] or "No release notes.",
                    can_install=True,
                )
                self.log(f"Staff Launcher update available: v{APP_VERSION} → v{release['version']} ({release['policy']}).")
                if release["policy"] == "required" and not silent:
                    self.root.after(0, lambda: messagebox.showwarning(APP_NAME, status))
            else:
                self._set_update_ui(
                    latest=release["version"],
                    policy=release["policy"],
                    status=f"Current — v{APP_VERSION}",
                    notes=release["notes"] or "No release notes.",
                    can_install=False,
                )
                if not silent:
                    self.root.after(0, lambda: messagebox.showinfo(APP_NAME, f"TGC Staff Launcher v{APP_VERSION} is current."))
        except Exception as error:
            self.latest_update = None
            self._set_update_ui(status=f"Update check failed: {error}", can_install=False)
            self.log(f"Staff Launcher update check failed: {error}")
            if not silent:
                self.root.after(0, lambda e=error: messagebox.showerror(APP_NAME, f"Could not check for updates:\n\n{e}"))
        finally:
            self.update_check_in_progress = False

    @staticmethod
    def _safe_zip_members(archive: zipfile.ZipFile):
        members = []
        for info in archive.infolist():
            name = info.filename.replace("\\", "/")
            path = Path(name)
            if not name or name.startswith("/") or path.is_absolute() or ".." in path.parts:
                raise RuntimeError(f"Unsafe path in update ZIP: {info.filename}")
            unix_mode = (info.external_attr >> 16) & 0o170000
            if unix_mode == 0o120000:
                raise RuntimeError(f"Symlinks are not allowed in the update ZIP: {info.filename}")
            members.append(info)
        return members

    @staticmethod
    def _find_update_package_root(extract_root: Path) -> Path:
        direct = extract_root / "TGC_Staff_Launcher.py"
        wrapped = extract_root / "TGC_Staff_Launcher_Package" / "TGC_Staff_Launcher.py"
        if direct.is_file():
            return extract_root
        if wrapped.is_file():
            return wrapped.parent
        matches = list(extract_root.rglob("TGC_Staff_Launcher.py"))
        if len(matches) == 1:
            return matches[0].parent
        raise RuntimeError("The update ZIP does not contain exactly one Staff Launcher package.")

    def install_staff_update(self):
        if not self.verified_access or not self.node_service.verified_session:
            messagebox.showerror(APP_NAME, "The Staff Launcher must be verified before it can update itself.")
            return
        if self.update_install_in_progress:
            return
        release = dict(self.latest_update or {})
        if not release or self._version_key(release.get("version")) <= self._version_key(APP_VERSION):
            messagebox.showinfo(APP_NAME, "No newer Staff Launcher update is currently loaded. Check GitHub first.")
            return
        if not messagebox.askyesno(
            APP_NAME,
            f"Install TGC Staff Launcher v{release['version']} now?\n\n"
            "The launcher will close, update its program files, and restart. "
            "Your TGC verification, DPAPI Node Key, node queue, and launcher settings will be preserved.",
        ):
            return
        self.update_install_in_progress = True
        self._set_update_ui(status=f"Downloading v{release['version']}…", can_install=False)
        self.background(self._install_staff_update_worker, release)

    def _install_staff_update_worker(self, release):
        try:
            if not UPDATE_HELPER.is_file():
                raise RuntimeError("staff_launcher_update_helper.py is missing from this installation.")
            UPDATE_DIR.mkdir(parents=True, exist_ok=True)
            work = Path(tempfile.mkdtemp(prefix="stage_", dir=str(UPDATE_DIR)))
            zip_path = work / "update.zip"
            req = urllib.request.Request(
                release["url"],
                headers={"User-Agent": f"TGC-Staff-Launcher/{APP_VERSION}"},
                method="GET",
            )
            digest = hashlib.sha256()
            total = 0
            try:
                with urllib.request.urlopen(req, timeout=45) as response, zip_path.open("wb") as out:
                    length = response.headers.get("Content-Length")
                    if length and int(length) > UPDATE_MAX_BYTES:
                        raise RuntimeError("The Staff Launcher update exceeds the 100 MB safety limit.")
                    while True:
                        chunk = response.read(1024 * 1024)
                        if not chunk:
                            break
                        total += len(chunk)
                        if total > UPDATE_MAX_BYTES:
                            raise RuntimeError("The Staff Launcher update exceeds the 100 MB safety limit.")
                        digest.update(chunk)
                        out.write(chunk)
            except Exception:
                raise
            actual_hash = digest.hexdigest().lower()
            if actual_hash != release["sha256"].lower():
                raise RuntimeError(
                    "Downloaded update failed SHA-256 verification. Nothing was installed.\n"
                    f"Expected: {release['sha256']}\nActual:   {actual_hash}"
                )
            extract_root = work / "extracted"
            extract_root.mkdir()
            with zipfile.ZipFile(zip_path, "r") as archive:
                members = self._safe_zip_members(archive)
                archive.extractall(extract_root, members=members)
            source_root = self._find_update_package_root(extract_root)
            required = [
                "TGC_Staff_Launcher.py",
                "tgc_staff_node.py",
                "tgc_vrchat_multilog.py",
                "staff_launcher_update_helper.py",
            ]
            missing = [name for name in required if not (source_root / name).is_file()]
            if missing:
                raise RuntimeError("Update ZIP is incomplete: missing " + ", ".join(missing))
            metadata = {
                "from_version": APP_VERSION,
                "to_version": release["version"],
                "sha256": actual_hash,
                "source_url": release["url"],
                "staged_at": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
            }
            (work / "update_metadata.json").write_text(json.dumps(metadata, indent=2), encoding="utf-8")
            cmd = [
                sys.executable,
                str(UPDATE_HELPER),
                "--pid", str(os.getpid()),
                "--source", str(source_root),
                "--target", str(BASE_DIR),
                "--restart", str(BASE_DIR / "TGC_Staff_Launcher.py"),
                "--version", str(release["version"]),
            ]
            creationflags = 0
            if os.name == "nt":
                creationflags = getattr(subprocess, "CREATE_NEW_PROCESS_GROUP", 0) | getattr(subprocess, "DETACHED_PROCESS", 0)
            subprocess.Popen(cmd, cwd=str(BASE_DIR), close_fds=(os.name != "nt"), creationflags=creationflags)
            self.log(f"Staff Launcher v{release['version']} staged and handed to updater helper.")
            self.root.after(0, self.on_close)
        except Exception as error:
            self.update_install_in_progress = False
            self._set_update_ui(status=f"Update failed: {error}", can_install=True)
            self.log(f"Staff Launcher update failed: {error}")
            self.root.after(0, lambda e=error: messagebox.showerror(APP_NAME, f"Update failed:\n\n{e}"))

    def build_update_tab(self, tab):
        ttk.Label(tab, text="Staff Launcher Updates", font=("Segoe UI", 14, "bold")).pack(anchor="w")
        ttk.Label(
            tab,
            text=(
                "Updates come directly from Seawolf035/TGC-Node-Updates on GitHub. "
                "A Staff Launcher update replaces the launcher and its embedded Invite Node together, while preserving "
                "this PC's verification credentials, DPAPI Node Key, queue database, and launcher settings."
            ),
            wraplength=760,
            justify="left",
        ).pack(anchor="w", pady=(0, 12))

        info = ttk.LabelFrame(tab, text="GitHub Release Status", padding=10)
        info.pack(fill="x")
        self.update_current_var = tk.StringVar(value=APP_VERSION)
        self.update_latest_var = tk.StringVar(value="Not checked")
        self.update_policy_var = tk.StringVar(value="—")
        self.update_status_var = tk.StringVar(value="Waiting for automatic GitHub check…")
        self.update_notes_var = tk.StringVar(value="—")
        rows = [
            ("Installed:", self.update_current_var),
            ("Latest:", self.update_latest_var),
            ("Policy:", self.update_policy_var),
            ("Status:", self.update_status_var),
            ("Release notes:", self.update_notes_var),
        ]
        for i, (label, var) in enumerate(rows):
            ttk.Label(info, text=label, width=18).grid(row=i, column=0, sticky="nw", padx=(0, 8), pady=3)
            ttk.Label(info, textvariable=var, wraplength=610, justify="left").grid(row=i, column=1, sticky="w", pady=3)
        info.columnconfigure(1, weight=1)

        actions = ttk.Frame(tab)
        actions.pack(fill="x", pady=(12, 0))
        ttk.Button(actions, text="Check GitHub Now", command=lambda: self.check_for_updates(silent=False)).pack(side="left", padx=(0, 6))
        self.update_install_button = ttk.Button(actions, text="Install Update", command=self.install_staff_update, state="disabled")
        self.update_install_button.pack(side="left", padx=6)
        ttk.Label(actions, text="TGC verification is required before updates can be installed.").pack(side="right")

    def build_ui(self):
        outer = ttk.Frame(self.root, padding=12)
        outer.pack(fill="both", expand=True)

        ttk.Label(outer, text=APP_NAME, font=("Segoe UI", 20, "bold")).pack(anchor="w")
        ttk.Label(
            outer,
            text=f"Verified staff edition • 3 local instances • 1 selectable VR client • Embedded Invite Node v{NODE_VERSION}",
        ).pack(anchor="w", pady=(0, 10))

        notebook = ttk.Notebook(outer)
        notebook.pack(fill="both", expand=True)
        launcher_tab = ttk.Frame(notebook, padding=12)
        osc_tab = ttk.Frame(notebook, padding=12)
        node_tab = ttk.Frame(notebook, padding=12)
        update_tab = ttk.Frame(notebook, padding=12)
        settings_tab = ttk.Frame(notebook, padding=12)
        log_tab = ttk.Frame(notebook, padding=12)
        notebook.add(launcher_tab, text="Instances")
        notebook.add(osc_tab, text="OSC Control")
        notebook.add(node_tab, text="Invite Node")
        notebook.add(update_tab, text="Updates")
        notebook.add(settings_tab, text="Settings")
        notebook.add(log_tab, text="Log")

        self.build_launcher_tab(launcher_tab)
        self.build_osc_tab(osc_tab)
        self.build_node_tab(node_tab)
        self.build_update_tab(update_tab)
        self.build_settings_tab(settings_tab)
        self.build_log_tab(log_tab)

    def build_launcher_tab(self, tab):
        ttk.Label(tab, text="VRChat Instances", font=("Segoe UI", 14, "bold")).pack(anchor="w")
        ttk.Label(
            tab,
            text=(
                "Each instance uses its own OSC input/output ports. Select at most one instance for VR; "
                "every other instance is explicitly forced into Desktop mode."
            ),
            wraplength=780,
            justify="left",
        ).pack(anchor="w", pady=(0, 10))

        rows = ttk.Frame(tab)
        rows.pack(fill="x")
        for index in range(1, INSTANCE_COUNT + 1):
            row = ttk.LabelFrame(rows, text=f"Instance {index}", padding=10)
            row.pack(fill="x", pady=5)
            status = ttk.Label(row, text="Stopped", width=16)
            status.grid(row=0, column=0, padx=(0, 10))
            pid = ttk.Label(row, text="PID: —", width=16)
            pid.grid(row=0, column=1, padx=(0, 10))
            mode = ttk.Label(row, text="Next: Desktop", width=14)
            mode.grid(row=0, column=2, padx=(0, 8))
            ttk.Radiobutton(
                row,
                text="Use VR",
                variable=self.vr_instance,
                value=index,
                command=self.on_vr_instance_changed,
            ).grid(row=0, column=3, padx=4)
            ttk.Button(row, text="Start", command=lambda i=index: self.background(self.launch_instance, i)).grid(row=0, column=4, padx=3)
            ttk.Button(row, text="Restart", command=lambda i=index: self.background(self.restart_instance, i)).grid(row=0, column=5, padx=3)
            ttk.Button(row, text="Stop", command=lambda i=index: self.background(self.stop_instance, i)).grid(row=0, column=6, padx=3)
            self.status_labels[index] = status
            self.pid_labels[index] = pid
            self.mode_labels[index] = mode

        actions = ttk.LabelFrame(tab, text="All Instances", padding=10)
        actions.pack(fill="x", pady=(15, 0))
        ttk.Button(actions, text="Start All", command=lambda: self.background(self.start_all)).pack(side="left", padx=4)
        ttk.Button(actions, text="Restart All", command=lambda: self.background(self.restart_all)).pack(side="left", padx=4)
        ttk.Button(actions, text="Stop All", command=lambda: self.background(self.stop_all)).pack(side="left", padx=4)
        ttk.Button(actions, text="Tile Windows", command=lambda: self.background(self.tile_windows)).pack(side="left", padx=4)
        ttk.Button(actions, text="Refresh", command=self.refresh_status).pack(side="left", padx=4)
        ttk.Radiobutton(
            actions,
            text="Desktop Only",
            variable=self.vr_instance,
            value=0,
            command=self.on_vr_instance_changed,
        ).pack(side="right", padx=4)

        self.update_mode_labels()
        self.main_status = ttk.Label(tab, text="Ready", anchor="w")
        self.main_status.pack(fill="x", pady=(14, 0))

    def build_osc_tab(self, tab):
        repeater = ttk.LabelFrame(tab, text="OSC Repeater", padding=10)
        repeater.pack(fill="x", pady=(0, 10))
        self.repeater_button = ttk.Button(repeater, text="Start Repeater", command=self.toggle_repeater)
        self.repeater_button.pack(side="left", padx=(0, 8))
        ttk.Checkbutton(repeater, text="Forward chatbox packets", variable=self.forward_chatbox).pack(side="left")
        self.repeater_status = ttk.Label(repeater, text="Stopped")
        self.repeater_status.pack(side="right")

        scale = ttk.LabelFrame(tab, text="Avatar Eye Height / Scale", padding=10)
        scale.pack(fill="x", pady=10)
        self.height_label = ttk.Label(scale, text="1.60 m", font=("Segoe UI", 12, "bold"))
        self.height_label.pack(anchor="w")
        slider = ttk.Scale(scale, from_=0.10, to=10.0, variable=self.current_height, command=self.height_slider_changed)
        slider.pack(fill="x", pady=6)
        preset_row = ttk.Frame(scale)
        preset_row.pack(fill="x")
        for text, value in [("Tiny 0.25", 0.25), ("Small 0.50", 0.50), ("Normal 1.60", 1.60), ("Large 2.50", 2.50), ("Huge 5.00", 5.00), ("Max 10.00", 10.0)]:
            ttk.Button(preset_row, text=text, command=lambda v=value: self.set_height(v)).pack(side="left", padx=2)
        smooth_row = ttk.Frame(scale)
        smooth_row.pack(fill="x", pady=(8, 0))
        ttk.Button(smooth_row, text="Smooth Shrink", command=lambda: self.smooth_height(0.50)).pack(side="left", padx=2)
        ttk.Button(smooth_row, text="Smooth Reset", command=lambda: self.smooth_height(1.60)).pack(side="left", padx=2)
        ttk.Button(smooth_row, text="Smooth Grow", command=lambda: self.smooth_height(2.50)).pack(side="left", padx=2)
        ttk.Button(smooth_row, text="Emergency Reset", command=self.emergency_reset).pack(side="right", padx=2)

        pulse = ttk.LabelFrame(tab, text="Variable Pulse", padding=10)
        pulse.pack(fill="x", pady=10)
        self.make_slider(pulse, "Minimum", self.pulse_min, 0.10, 10.0)
        self.make_slider(pulse, "Maximum", self.pulse_max, 0.10, 10.0)
        self.make_slider(pulse, "Speed", self.pulse_speed, 0.05, 1.50)
        pulse_buttons = ttk.Frame(pulse)
        pulse_buttons.pack(fill="x", pady=(8, 0))
        ttk.Button(pulse_buttons, text="Start Pulse", command=self.start_pulse).pack(side="left", padx=2)
        ttk.Button(pulse_buttons, text="Stop Pulse", command=self.stop_pulse).pack(side="left", padx=2)
        ttk.Button(pulse_buttons, text="Chill", command=lambda: self.load_pulse(1.45, 1.80, 0.20)).pack(side="left", padx=8)
        ttk.Button(pulse_buttons, text="Music", command=lambda: self.load_pulse(1.35, 2.00, 0.45)).pack(side="left", padx=2)
        ttk.Button(pulse_buttons, text="Boss", command=lambda: self.load_pulse(3.00, 6.00, 0.75)).pack(side="left", padx=2)

    def make_slider(self, parent, label, variable, low, high):
        frame = ttk.Frame(parent)
        frame.pack(fill="x", pady=2)
        ttk.Label(frame, text=label, width=10).pack(side="left")
        value_label = ttk.Label(frame, text=f"{variable.get():.2f}", width=7)
        value_label.pack(side="right")
        ttk.Scale(frame, from_=low, to=high, variable=variable, command=lambda _v, var=variable, out=value_label: out.config(text=f"{var.get():.2f}")).pack(side="left", fill="x", expand=True)

    def build_settings_tab(self, tab):
        ttk.Button(tab, text="View Terms of Use",
            command=lambda: tgc_terms_view(self.root)).pack(anchor="w", pady=5)
        ttk.Label(tab, text="Launcher Settings", font=("Segoe UI", 14, "bold")).pack(anchor="w", pady=(0, 10))
        path_frame = ttk.LabelFrame(tab, text="VRChat launch.exe", padding=10)
        path_frame.pack(fill="x", pady=5)
        ttk.Entry(path_frame, textvariable=self.program_path).pack(side="left", fill="x", expand=True)
        ttk.Button(path_frame, text="Browse", command=self.browse_program).pack(side="left", padx=(8, 0))

        options = ttk.LabelFrame(tab, text="Options", padding=10)
        options.pack(fill="x", pady=10)
        ttk.Label(options, text="Delay between Start All / Restart All launches:").grid(row=0, column=0, sticky="w")
        ttk.Spinbox(options, from_=5, to=180, textvariable=self.delay_seconds, width=8).grid(row=0, column=1, sticky="w", padx=8)
        ttk.Label(options, text="seconds").grid(row=0, column=2, sticky="w")
        ttk.Checkbutton(options, text="Automatically tile windows after launches", variable=self.auto_tile).grid(row=1, column=0, columnspan=3, sticky="w", pady=(8, 0))
        ttk.Checkbutton(options, text="Start OSC repeater automatically", variable=self.autostart_repeater).grid(row=2, column=0, columnspan=3, sticky="w")
        ttk.Label(
            options,
            text="VR instance is selected on the Instances tab. Only one local client can be VR-enabled at a time.",
        ).grid(row=3, column=0, columnspan=3, sticky="w", pady=(8, 0))
        ttk.Button(tab, text="Save Settings", command=self.save_settings).pack(anchor="w", pady=8)

        note = (
            "This edition is intentionally limited to three local VRChat instances. One of those three can be selected for VR; "
            "the other clients are launched with VRChat's --no-vr option so they remain Desktop clients. "
            "It contains no Discord bot, VRChat API login, group-world creation, or owner controls. "
            "The embedded Invite Node uses only its owner-issued relay credential and will not unlock the launcher unless TGC verification succeeds."
        )
        ttk.Label(tab, text=note, wraplength=720, justify="left").pack(anchor="w", pady=(15, 0))

    def build_log_tab(self, tab):
        self.log_box = tk.Text(tab, wrap="word", state="disabled", font=("Consolas", 9))
        self.log_box.pack(fill="both", expand=True)
        ttk.Button(tab, text="Clear Log", command=self.clear_log).pack(anchor="e", pady=(8, 0))
        if self.pre_ui_logs:
            self.log_box.configure(state="normal")
            for line in self.pre_ui_logs:
                self.log_box.insert("end", line + "\n")
            self.log_box.see("end")
            self.log_box.configure(state="disabled")
            self.pre_ui_logs.clear()

    def log(self, message: str):
        stamp = time.strftime("%H:%M:%S")
        line = f"[{stamp}] {message}"
        if not hasattr(self, "log_box"):
            self.pre_ui_logs.append(line)
            return
        def write():
            if not hasattr(self, "log_box"):
                self.pre_ui_logs.append(line)
                return
            self.log_box.configure(state="normal")
            self.log_box.insert("end", line + "\n")
            self.log_box.see("end")
            self.log_box.configure(state="disabled")
        self.root.after(0, write)

    def clear_log(self):
        self.log_box.configure(state="normal")
        self.log_box.delete("1.0", "end")
        self.log_box.configure(state="disabled")

    def background(self, function, *args):
        threading.Thread(target=function, args=args, daemon=True).start()

    def browse_program(self):
        chosen = filedialog.askopenfilename(title="Select VRChat launch.exe", filetypes=[("Executable", "*.exe"), ("All files", "*.*")])
        if chosen:
            self.program_path.set(chosen)

    def save_settings(self):
        self.config.update({
            "program_path": self.program_path.get().strip(),
            "launch_delay_seconds": max(5, int(self.delay_seconds.get())),
            "auto_tile": bool(self.auto_tile.get()),
            "vr_instance": int(self.vr_instance.get()),
            "autostart_repeater": bool(self.autostart_repeater.get()),
        })
        try:
            save_config(self.config)
            self.log("Settings saved.")
            messagebox.showinfo(APP_NAME, "Settings saved.")
        except Exception as error:
            messagebox.showerror(APP_NAME, f"Could not save settings:\n\n{error}")

    def on_vr_instance_changed(self):
        try:
            selected = int(self.vr_instance.get())
        except Exception:
            selected = 0
        if selected not in range(0, INSTANCE_COUNT + 1):
            selected = 0
            self.vr_instance.set(0)

        self.vr_instance_index = selected
        self.config["vr_instance"] = selected
        try:
            save_config(self.config)
        except Exception as error:
            self.log(f"Could not save VR instance selection: {error}")

        self.update_mode_labels()
        if selected:
            text = (
                f"Instance {selected} selected for VR. The other two clients will be forced Desktop. "
                "Restart any already-running client for a mode change to take effect."
            )
        else:
            text = (
                "Desktop Only selected. All three clients will be forced Desktop. "
                "Restart any already-running VR client for the change to take effect."
            )
        if hasattr(self, "main_status"):
            self.set_main_status(text)
        else:
            self.log(text)

    def update_mode_labels(self):
        selected = int(getattr(self, "vr_instance_index", 0) or 0)
        for index, label in self.mode_labels.items():
            try:
                label.config(text="Next: VR" if selected == index else "Next: Desktop")
            except Exception:
                pass

    def is_vr_instance(self, index: int) -> bool:
        return int(getattr(self, "vr_instance_index", 0) or 0) == int(index)

    def launch_arguments(self, index: int) -> list[str]:
        args = [self.program_path.get().strip(), self.osc_arg(index)]
        if not self.is_vr_instance(index):
            args.append("--no-vr")
        return args

    def running_mode(self, proc: psutil.Process | None) -> str:
        if not proc:
            return "Unknown"
        try:
            cmdline = [str(part).lower() for part in (proc.cmdline() or [])]
            return "Desktop" if "--no-vr" in cmdline else "VR"
        except (psutil.NoSuchProcess, psutil.AccessDenied, psutil.ZombieProcess):
            return "Unknown"

    def osc_arg(self, index: int) -> str:
        in_port = int(self.config["base_instance_in_port"]) + index - 1
        out_port = int(self.config["base_instance_out_port"]) + index - 1
        return f"--osc={in_port}:{self.config['osc_ip']}:{out_port}"

    def find_process(self, index: int) -> psutil.Process | None:
        marker = self.osc_arg(index)
        for proc in psutil.process_iter(["pid", "cmdline"]):
            try:
                if marker in " ".join(proc.info.get("cmdline") or []):
                    return proc
            except (psutil.NoSuchProcess, psutil.AccessDenied, psutil.ZombieProcess):
                pass
        return None

    def launch_instance(self, index: int):
        if self.find_process(index):
            self.set_main_status(f"Instance {index} is already running.")
            return
        path = self.program_path.get().strip()
        if not os.path.isfile(path):
            self.root.after(0, lambda: messagebox.showerror(APP_NAME, "VRChat launch.exe was not found. Set the correct path under Settings."))
            return
        try:
            args = self.launch_arguments(index)
            subprocess.Popen(args, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            mode = "VR" if self.is_vr_instance(index) else "Desktop"
            self.log(f"Launched instance {index} in {mode} mode with {self.osc_arg(index)}")
            self.set_row(index, f"Launching ({mode})", None)
            self.wait_for_process(index)
        except Exception as error:
            self.log(f"Instance {index} launch failed: {error}")
            self.root.after(0, lambda e=error: messagebox.showerror(APP_NAME, f"Could not launch instance {index}:\n\n{e}"))

    def wait_for_process(self, index: int):
        for _ in range(90):
            proc = self.find_process(index)
            if proc:
                self.processes[index] = proc
                mode = self.running_mode(proc)
                self.set_row(index, f"Running ({mode})", proc.pid)
                if self.auto_tile.get():
                    time.sleep(5)
                    self.tile_windows()
                return
            time.sleep(1)
        self.set_row(index, "Unknown", None)

    def stop_instance(self, index: int):
        proc = self.find_process(index)
        if not proc:
            self.set_row(index, "Stopped", None)
            return
        try:
            children = proc.children(recursive=True)
            proc.terminate()
            gone, alive = psutil.wait_procs([proc] + children, timeout=10)
            for item in alive:
                try:
                    item.kill()
                except Exception:
                    pass
            self.processes.pop(index, None)
            self.set_row(index, "Stopped", None)
            self.log(f"Stopped instance {index}.")
        except Exception as error:
            self.log(f"Could not stop instance {index}: {error}")

    def restart_instance(self, index: int):
        self.stop_instance(index)
        time.sleep(2)
        self.launch_instance(index)

    def start_all(self):
        for index in range(1, INSTANCE_COUNT + 1):
            self.launch_instance(index)
            if index < INSTANCE_COUNT:
                time.sleep(max(5, int(self.delay_seconds.get())))
        if self.auto_tile.get():
            time.sleep(5)
            self.tile_windows()

    def restart_all(self):
        self.stop_all()
        time.sleep(3)
        self.start_all()

    def stop_all(self):
        threads = [threading.Thread(target=self.stop_instance, args=(i,), daemon=True) for i in range(1, INSTANCE_COUNT + 1)]
        for thread in threads:
            thread.start()
        for thread in threads:
            thread.join(timeout=15)

    def refresh_status(self):
        for index in range(1, INSTANCE_COUNT + 1):
            proc = self.find_process(index)
            if proc and proc.is_running():
                self.processes[index] = proc
                mode = self.running_mode(proc)
                self.set_row(index, f"Running ({mode})", proc.pid)
            else:
                self.processes.pop(index, None)
                self.set_row(index, "Stopped", None)

    def status_loop(self):
        self.background(self.refresh_status)
        self.root.after(5000, self.status_loop)

    def set_row(self, index: int, status: str, pid):
        self.root.after(0, lambda: self.status_labels[index].config(text=status))
        self.root.after(0, lambda: self.pid_labels[index].config(text=f"PID: {pid if pid else '—'}"))

    def set_main_status(self, text: str):
        self.root.after(0, lambda: self.main_status.config(text=text))
        self.log(text)

    def tile_windows(self):
        windows = []
        def callback(hwnd, _):
            if not win32gui.IsWindowVisible(hwnd):
                return True
            try:
                _, pid = win32process.GetWindowThreadProcessId(hwnd)
                proc = psutil.Process(pid)
                if proc.name().lower() in {"vrchat.exe", "launch.exe"}:
                    title = win32gui.GetWindowText(hwnd)
                    if title:
                        windows.append(hwnd)
            except Exception:
                pass
            return True
        win32gui.EnumWindows(callback, None)
        windows = windows[:INSTANCE_COUNT]
        if not windows:
            self.log("No visible VRChat windows were found to tile.")
            return
        width = win32api_get_screen_width()
        height = win32api_get_work_height()
        columns = 3 if len(windows) >= 3 else len(windows)
        cell_w = max(480, width // max(1, columns))
        cell_h = max(360, height)
        for position, hwnd in enumerate(windows):
            try:
                win32gui.ShowWindow(hwnd, win32con.SW_RESTORE)
                win32gui.SetWindowPos(hwnd, win32con.HWND_TOP, position * cell_w, 0, cell_w, cell_h, win32con.SWP_SHOWWINDOW)
            except Exception as error:
                self.log(f"Could not tile a VRChat window: {error}")
        self.log(f"Tiled {len(windows)} VRChat window(s).")

    def toggle_repeater(self):
        self.stop_repeater() if self.repeater_running else self.start_repeater()

    def start_repeater(self):
        if self.repeater_running:
            return
        try:
            sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
            disable_udp_connreset(sock)
            sock.bind((str(self.config["repeater_listen_ip"]), int(self.config["repeater_listen_port"])))
            self.repeater_socket = sock
            self.repeater_running = True
            self.repeater_button.config(text="Stop Repeater")
            self.repeater_status.config(text=f"Listening on :{self.config['repeater_listen_port']}")
            self.background(self.repeater_loop)
            self.log("OSC repeater started.")
        except Exception as error:
            messagebox.showerror(APP_NAME, f"Could not start OSC repeater:\n\n{error}")

    def stop_repeater(self):
        self.repeater_running = False
        if self.repeater_socket:
            try:
                self.repeater_socket.close()
            except Exception:
                pass
        self.repeater_socket = None
        self.repeater_button.config(text="Start Repeater")
        self.repeater_status.config(text="Stopped")
        self.log("OSC repeater stopped.")

    def repeater_loop(self):
        targets = [("127.0.0.1", int(self.config["base_instance_in_port"]) + i) for i in range(INSTANCE_COUNT)]
        while self.repeater_running and self.repeater_socket:
            try:
                data, _addr = self.repeater_socket.recvfrom(65535)
                if data.startswith(b"/chatbox/") and not self.forward_chatbox.get():
                    continue
                for target in targets:
                    self.send_socket.sendto(data, target)
                self.packet_count += 1
                self.root.after(0, lambda: self.repeater_status.config(text=f"Running • {self.packet_count} packets"))
            except OSError:
                break
            except Exception as error:
                self.log(f"OSC repeater error: {error}")
                time.sleep(0.5)

    def send_float(self, address: str, value: float):
        packet = make_float_packet(address, value)
        target = ("127.0.0.1", int(self.config["repeater_listen_port"]))
        try:
            self.send_socket.sendto(packet, target)
        except Exception as error:
            self.log(f"OSC send failed: {error}")

    def height_slider_changed(self, _value=None):
        value = self.current_height.get()
        self.height_label.config(text=f"{value:.2f} m")
        self.send_float("/avatar/eyeheight", value)

    def set_height(self, value: float):
        self.stop_pulse()
        self.current_height.set(value)
        self.height_label.config(text=f"{value:.2f} m")
        self.send_float("/avatar/eyeheight", value)
        self.log(f"Eye height set to {value:.2f} m.")

    def smooth_height(self, target: float, duration: float = 2.0):
        def worker():
            self.stop_pulse()
            start = self.current_height.get()
            for step in range(21):
                value = start + (target - start) * step / 20
                self.root.after(0, lambda v=value: self.current_height.set(v))
                self.root.after(0, lambda v=value: self.height_label.config(text=f"{v:.2f} m"))
                self.send_float("/avatar/eyeheight", value)
                time.sleep(duration / 20)
        self.background(worker)

    def emergency_reset(self):
        self.stop_pulse()
        self.set_height(1.60)

    def load_pulse(self, low: float, high: float, speed: float):
        self.pulse_min.set(low)
        self.pulse_max.set(high)
        self.pulse_speed.set(speed)
        self.log(f"Pulse preset loaded: {low:.2f}–{high:.2f} m at {speed:.2f}x.")

    def start_pulse(self):
        if self.pulse_running:
            return
        self.pulse_running = True
        def loop():
            phase = 0.0
            interval = 0.10
            self.log("Pulse started.")
            while self.pulse_running:
                low, high = sorted((self.pulse_min.get(), self.pulse_max.get()))
                phase += self.pulse_speed.get() * interval * 4.0
                value = low + (high - low) * ((math.sin(phase) + 1.0) / 2.0)
                self.root.after(0, lambda v=value: self.current_height.set(v))
                self.root.after(0, lambda v=value: self.height_label.config(text=f"{v:.2f} m"))
                self.send_float("/avatar/eyeheight", value)
                time.sleep(interval)
            self.log("Pulse stopped.")
        self.background(loop)

    def stop_pulse(self):
        self.pulse_running = False

    def on_close(self):
        self.stop_pulse()
        try:
            self.node_service.stop()
        except Exception:
            pass
        if self.repeater_running:
            self.stop_repeater()
        try:
            self.send_socket.close()
        except Exception:
            pass
        try:
            if self.setup_window and self.setup_window.winfo_exists():
                self.setup_window.destroy()
        except Exception:
            pass
        self.root.destroy()


def win32api_get_screen_width() -> int:
    try:
        import win32api
        return int(win32api.GetSystemMetrics(0))
    except Exception:
        return 1920


def win32api_get_work_height() -> int:
    try:
        import win32api
        return int(win32api.GetSystemMetrics(1) - 40)
    except Exception:
        return 1040


if __name__ == "__main__":
    root = tk.Tk()
    try:
        ttk.Style().theme_use("vista")
    except Exception:
        pass
    if not tgc_terms_require(root, "staff_launcher"):
        root.destroy()
    else:
        StaffLauncher(root)
        root.mainloop()
