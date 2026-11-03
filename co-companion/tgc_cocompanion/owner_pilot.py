"""Explicit owner-test Windows UI for ONE existing TGC Relay.

Run ONLY from feature-branch source using:
    python -m tgc_cocompanion.owner_pilot

Separate pilot enrollment, no sighting uploader, no cross-process Node takeover,
no existing Node settings, no automatic launches, no public release. This is
an early PCVR/desktop proof of integration, NOT full Companion feature parity.
"""
from __future__ import annotations

from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
from threading import Thread
import tkinter as tk
from tkinter import filedialog, messagebox, ttk

from .launch_controller import ManagedVRChatLauncher
from .log_diagnostics import scan_log_readiness
from .osc import chatbox, LocalOscSender
from .pilot_enrollment import PilotEnrollment, PilotEnrollmentError, get_pilot_dir
from .staff_lease import RelayStaffLease
from .windows_process import WindowsVRChatSpawner, WindowsVRChatProcessFinder


TERMS_VERSION = "2026-10-26.1"
_TERMS_FILE = Path(__file__).resolve().parent.parent / "TGC_DESKTOP_TERMS.txt"


def terms_digest() -> str:
    contents = _TERMS_FILE.read_bytes()
    if len(contents) < 10000 or b"Agreement version: 2026-10-26.1" not in contents:
        raise RuntimeError("The official TGC Desktop Terms are missing or invalid")
    return hashlib.sha256(contents).hexdigest()


class OwnerPilotApp:
    def __init__(self, root: tk.Tk):
        if os.name != "nt":
            raise RuntimeError("TGC Co-Companion owner pilot runs only on Windows")
        self.root = root
        from .desktop_theme import apply_desktop_theme
        apply_desktop_theme(self.root)
        self.root.title("TGC Co-Companion — Owner Preview 0.6.0 (NOT A STAFF RELEASE)")
        self.root.geometry("1120x650")
        self.root.minsize(900, 520)
        self.enrollment = PilotEnrollment()
        self.lease: RelayStaffLease | None = None
        self.launcher: ManagedVRChatLauncher | None = None
        self._busy = False
        self._stop = False
        self.terms_dir = get_pilot_dir()
        from .desktop_staff_colors import DesktopThemeChoice
        self.theme_choice = DesktopThemeChoice(self.terms_dir)
        self.terms_file = self.terms_dir / "pilot_terms_acceptance.json"

        self.status_var = tk.StringVar(value="Owner pilot locked — verify account and accept desktop terms")
        self.relay_var = tk.StringVar(value=self.enrollment.discord_id or "Not enrolled")
        self.deployment_var = tk.StringVar()
        self.program_var = tk.StringVar(value=r"C:\Program Files (x86)\Steam\steamapps\common\VRChat\launch.exe")
        self.usage_var = tk.StringVar(value="0/0 instances authorized")
        self.vr_slot_var = tk.IntVar(value=1)
        self.chat_var = tk.StringVar()
        self.pid_var = tk.StringVar()
        self.log_health_var = tk.StringVar(value="Not checked")
        self.control_buttons = []
        self._build()
        from .desktop_staff_colors import apply_desktop_palette
        apply_desktop_palette(self.root,self.theme_choice.load())
        self._refresh_buttons()
        self.root.protocol("WM_DELETE_WINDOW", self._close)
        self.root.after(10000, self._tick)

    def _build(self):
        from .desktop_shell import DesktopShell
        self.shell = DesktopShell(self.root, self.terms_dir, self.status_var)
        self.page_select = self.shell.page_select
        self.page_picker = self.shell.page_picker
        notebook = self.shell.notebook
        self._main_notebook = notebook
        verify = ttk.Frame(notebook, padding=15)
        notebook.add(verify, text="Verification")
        ttk.Label(verify, text="Verification requires a one-time Staff Launcher deployment link created"
                  " for your Discord ID in the Main Launcher. The pilot stores a DIFFERENT"
                  " enrollment in this Windows user's profile, without modifying old Node files.",
                  wraplength=790).pack(anchor="w", pady=(0, 10))
        ttk.Label(verify, text="Current enrollment:").pack(anchor="w")
        ttk.Label(verify, textvariable=self.relay_var).pack(anchor="w", pady=(0, 9))
        ttk.Label(verify, text="One-time deployment link:").pack(anchor="w")
        ttk.Entry(verify, textvariable=self.deployment_var, width=90, show="•").pack(fill="x", pady=(0, 7))
        bar = ttk.Frame(verify)
        bar.pack(anchor="w")
        self.enroll_btn = ttk.Button(bar, text="Redeem One-Time Link", command=self._redeem)
        self.enroll_btn.pack(side="left", padx=(0, 10))
        self.check_btn = ttk.Button(bar, text="Verify Pilot Access", command=self._verify)
        self.check_btn.pack(side="left", padx=(0, 10))
        ttk.Button(bar, text="View / Accept Desktop Terms",
                   command=self._terms_dialog).pack(side="left")
        ttk.Label(verify,text="If the Relay has not yet been updated or your account is not the enabled pilot, verification will fail safely.",
                  wraplength=750).pack(anchor="w", pady=(12, 0))

        launch = ttk.Frame(notebook, padding=15)
        notebook.add(launch, text="VRChat Launcher")
        ttk.Label(launch,text="VRChat launch.exe location:").pack(anchor="w")
        row = ttk.Frame(launch);row.pack(fill="x")
        ttk.Entry(row,textvariable=self.program_var,width=80).pack(side="left",fill="x",expand=True)
        ttk.Button(row,text="Browse",command=self._browse).pack(side="left",padx=6)
        ttk.Label(launch,text="Designated VR slot (other slots are Desktop):").pack(anchor="w",pady=(12,0))
        ttk.Spinbox(launch,from_=0,to=4,textvariable=self.vr_slot_var,width=4).pack(anchor="w")
        ttk.Label(launch,text="Set your executable and VR slot BEFORE your first launch. Restart the pilot to change them safely.",
                  wraplength=750).pack(anchor="w",pady=(5,10))
        ttk.Label(launch,textvariable=self.usage_var).pack(anchor="w")
        self.launch_rows = {}
        for idx in range(1,5):
            ln=ttk.Frame(launch);ln.pack(anchor="w",pady=6)
            ttk.Label(ln,text=f"VRChat slot {idx}",width=18).pack(side="left")
            indicator=tk.StringVar(value="Not checked")
            ttk.Label(ln,textvariable=indicator,width=28).pack(side="left")
            button=ttk.Button(ln,text=f"Launch Slot {idx}",command=lambda i=idx:self._launch(i))
            button.pack(side="left")
            self.launch_rows[idx]=(indicator,button)
            self.control_buttons.append(button)
        ttk.Label(launch,text="This pilot never starts the legacy Node uploader or terminates running VRChat sessions.",
                  wraplength=750).pack(anchor="w",pady=10)

        osc=ttk.Frame(notebook,padding=15);notebook.add(osc,text="OSC")
        ttk.Label(osc,text="Send an OSC chatbox message to an explicitly selected running PC VRChat instance.").pack(anchor="w")
        ttk.Label(osc,text="VRChat PID:").pack(anchor="w",pady=(12,0))
        ttk.Entry(osc,textvariable=self.pid_var,width=16).pack(anchor="w")
        ttk.Label(osc,text="Message:").pack(anchor="w",pady=(12,0))
        ttk.Entry(osc,textvariable=self.chat_var,width=75).pack(anchor="w")
        send=ttk.Button(osc,text="Send To Selected VRChat PID",command=self._send_chat)
        send.pack(anchor="w",pady=9);self.control_buttons.append(send)
        ttk.Label(osc,text="No remote phone/Quest pairing is enabled in this owner test.",wraplength=750).pack(anchor="w")

        tracking=ttk.Frame(notebook,padding=15);notebook.add(tracking,text="Tracking Diagnostics")
        ttk.Label(tracking,text="Read-only local log health. This prototype does NOT upload sightings or start an embedded Node.",
                  wraplength=740).pack(anchor="w",pady=(0,10))
        diagnostics=ttk.Button(tracking,text="Check PC VRChat Log Health",command=self._diagnose)
        diagnostics.pack(anchor="w");self.control_buttons.append(diagnostics)
        ttk.Label(tracking,textvariable=self.log_health_var,wraplength=740).pack(anchor="w",pady=(10,0))
        # Mobile Companion parity pages share the same pilot permission lease,
        # but personal VRChat cookies NEVER pass through TGC Relay or Node.
        from .desktop_pages import DesktopPages
        self.features=DesktopPages(self,notebook)
        from .desktop_update_page import OwnerUpdatePage
        self.updates=OwnerUpdatePage(self,notebook)
        from .desktop_settings import DesktopSettings
        self.settings=DesktopSettings(self,notebook)
        self.shell.register_pages()
        self._page_names = list(self.shell.page_names)

    def _choose_page(self,event=None):
        title=self.page_select.get()
        try:
            index=self._page_names.index(title)
            self._main_notebook.select(index)
        except (ValueError,AttributeError):
            return

    def _page_changed(self,event=None):
        try:
            index=self._main_notebook.index(self._main_notebook.select())
            if 0 <= index < len(self._page_names):
                self.page_select.set(self._page_names[index])
        except (ValueError,AttributeError):
            pass

    def _terms_valid(self) -> bool:
        try:
            record=json.loads(self.terms_file.read_text(encoding="utf-8"))
            return (record.get("version")==TERMS_VERSION and
                    record.get("sha256")==terms_digest() and
                    record.get("accepted") is True)
        except (OSError,ValueError,RuntimeError):
            return False

    def _terms_dialog(self):
        try: text = _TERMS_FILE.read_text(encoding="utf-8"); digest=terms_digest()
        except (OSError,RuntimeError) as exc:
            messagebox.showerror("TGC Desktop Terms",str(exc));return
        window=tk.Toplevel(self.root);window.title("TGC Desktop Terms — Owner Pilot")
        window.geometry("790x600");window.transient(self.root);window.grab_set()
        ttk.Label(window,text="Official Desktop Terms, for this separate owner-pilot install",
                  font=("Segoe UI",12,"bold")).pack(anchor="w",padx=10,pady=7)
        body=ttk.Frame(window);body.pack(fill="both",expand=True,padx=8)
        scrollbar=ttk.Scrollbar(body);scrollbar.pack(side="right",fill="y")
        box=tk.Text(body,wrap="word",yscrollcommand=scrollbar.set)
        box.pack(fill="both",expand=True)
        scrollbar.config(command=box.yview)
        box.insert("1.0",text);box.config(state="disabled")
        bar=ttk.Frame(window);bar.pack(fill="x",padx=9,pady=9)
        ttk.Button(bar,text="Decline",command=window.destroy).pack(side="right",padx=6)
        def accept():
            self.terms_dir.mkdir(parents=True,exist_ok=True)
            record={"version":TERMS_VERSION,"sha256":digest,"accepted":True,
                    "accepted_at":datetime.now(timezone.utc).isoformat()}
            temp=self.terms_file.with_suffix(".tmp")
            temp.write_text(json.dumps(record,indent=2),encoding="utf-8")
            temp.replace(self.terms_file)
            window.destroy();self._refresh_buttons()
        ttk.Button(bar,text="I Agree",command=accept).pack(side="right",padx=6)

    def _run_async(self, operation, done, on_error=None):
        if self._busy:return
        self._busy=True;self._refresh_buttons()
        def job():
            try: outcome=operation();failure=None
            except Exception as exc: outcome=None;failure=str(exc)
            self.root.after(0,lambda:finish(outcome,failure))
        def finish(outcome,failure):
            self._busy=False
            if failure:
                self.status_var.set("Error: "+failure)
                if on_error:
                    on_error(failure)
            else:
                done(outcome)
            self._refresh_buttons()
        Thread(target=job,name="CoCompanionOwnerPilotRPC",daemon=True).start()

    def _redeem(self):
        if not self._terms_valid():
            messagebox.showerror("Terms required","View and accept the TGC Desktop Terms first.");return
        if not messagebox.askyesno("Owner pilot enrollment","Redeem this one-time Staff Launcher code into a SEPARATE Co-Companion owner-pilot profile?"):return
        link=self.deployment_var.get().strip()
        self.deployment_var.set("")
        def done(staff):
            self.relay_var.set(staff)
            self.status_var.set("One-time code enrolled. Click Verify Pilot Access.")
        self._run_async(lambda:self.enrollment.redeem(link),done)

    def _verify(self):
        if not self._terms_valid():
            messagebox.showerror("Terms required","View and accept the TGC Desktop Terms first.");return
        if not self.enrollment.enrolled:
            self.status_var.set("Not enrolled; paste a fresh owner-issued Staff Launcher deployment link.");return
        def operation():
            # A failed Node recheck must immediately invalidate any previous
            # cached lease, not leave old extra-instance permissions alive.
            if self.lease is not None:
                self.lease.invalidate()
            self.enrollment.verify_node()
            if self.lease is None:
                self.lease=RelayStaffLease(self.enrollment.access_rpc,
                    enrolled_discord_id=self.enrollment.discord_id,
                    enrolled_node_id=self.enrollment.node_id)
            return self.lease.refresh()
        def done(grant):
            self.status_var.set(f"Verified staff {grant.discord_id} — {grant.max_instances}/4 slots, short-lived server lease")
            self.relay_var.set(grant.discord_id)
            if self.launcher is None:
                try:
                    selected=int(self.vr_slot_var.get())
                    self.launcher=ManagedVRChatLauncher(
                        self.lease.current,self._terms_valid,
                        WindowsVRChatSpawner(),WindowsVRChatProcessFinder(),
                        executable=self.program_var.get().strip(),vr_slot=selected)
                except Exception as exc:
                    self.status_var.set("Launcher configuration invalid: "+str(exc))
        self._run_async(operation,done)

    def _refresh_buttons(self):
        lease=self.lease.current() if self.lease is not None else None
        allowed=lease.valid_limit(__import__("time").time()) if lease and self._terms_valid() else 0
        self.usage_var.set(f"0/{allowed} instances authorized • maximum 4")
        for idx,(state,button) in self.launch_rows.items():
            button.configure(state="normal" if allowed>=idx and not self._busy else "disabled")
        for btn in self.control_buttons[4:]:
            btn.configure(state="normal" if allowed and not self._busy else "disabled")
        self.enroll_btn.configure(state="normal" if not self._busy and not self.enrollment.enrolled else "disabled")
        self.check_btn.configure(state="normal" if not self._busy else "disabled")

    def _browse(self):
        selected=filedialog.askopenfilename(title="Choose VRChat launch.exe",filetypes=[("VRChat Launcher","launch.exe"),("Executables","*.exe")])
        if selected:self.program_var.set(selected)

    def _launch(self, slot:int):
        if not self._terms_valid() or not self.lease or not self.lease.current():return
        try:
            if self.launcher is None:raise RuntimeError("Verify the owner pilot first")
            spec=self.launcher.launch(slot)
            self.status_var.set(f"VRChat slot {slot} requested ({spec.mode}). Awaiting actual VRChat.exe PID.")
        except Exception as exc:self.status_var.set("Launch blocked: "+str(exc))
        self._refresh_buttons()

    def _send_chat(self):
        if not self._terms_valid() or not self.lease or not self.lease.current():return
        try:
            if self.launcher is None:raise RuntimeError("Verify first")
            target=self.launcher.router.target(int(self.pid_var.get()))
            LocalOscSender().send(chatbox(self.chat_var.get()),port=target.osc_port)
            self.status_var.set(f"Sent chatbox OSC to selected local VRChat PID {target.pid}")
        except Exception as exc:self.status_var.set("OSC blocked: "+str(exc))

    def _diagnose(self):
        if not self._terms_valid() or not self.lease or not self.lease.current():return
        result=scan_log_readiness(process_running=None)
        self.log_health_var.set(result.message+f" ({result.files_found} files, {result.recent_nonempty_files} recent)")
        if not result.ready:
            self.status_var.set("VRChat output logs are missing or stale")

    def _tick(self):
        if self._stop:return
        if self.launcher is not None and not self._busy:
            try:
                statuses=self.launcher.refresh()
                active=sum(1 for x in statuses if x.pid is not None)
                limit=self.lease.current().valid_limit(__import__("time").time()) if self.lease and self.lease.current() else 0
                self.usage_var.set(f"{active}/{limit} instances running • maximum 4")
                for x in statuses:
                    state,button=self.launch_rows[x.slot]
                    state.set(f"{x.state} ({x.mode})"+(f" PID {x.pid}" if x.pid else ""))
            except Exception as exc:
                self.status_var.set("VRChat process status unavailable: "+str(exc))
        if self.lease and self.lease.current() and not self._busy:
            exp=self.lease.current().expires_at
            if exp-__import__("time").time()<45:
                self._verify()
        self._refresh_buttons()
        self.root.after(10000,self._tick)

    def _close(self):
        self._stop=True
        # Stop rotation and guarantee held PTT release on application exit.
        try:
            if hasattr(self,"features"):
                self.features.close()
        finally:
            self.root.destroy()


def main():
    root=tk.Tk()
    OwnerPilotApp(root)
    root.mainloop()


if __name__ == "__main__":
    main()
