#!/usr/bin/env python3
"""Build required automatic VRChat logging warnings for all three TGC Node clients."""
import csv
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import tempfile
from datetime import datetime,timezone
import zipfile

ROOT=Path(__file__).resolve().parents[1]
UPDATES=ROOT/"updates"
NOW=datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")

def replace_once(s,old,new,label):
    n=s.count(old)
    if n!=1:
        raise RuntimeError(f"{label}: expected one exact occurrence, found {n}")
    return s.replace(old,new,1)

HEALTH_METHODS='''    @staticmethod
    def _vrchat_process_running():
        """Read-only VRChat process probe; no psutil dependency for standalone Nodes."""
        if os.name != "nt":
            return False
        try:
            result = subprocess.run(
                ["tasklist.exe", "/FI", "IMAGENAME eq VRChat.exe", "/FO", "CSV", "/NH"],
                stdout=subprocess.PIPE, stderr=subprocess.DEVNULL,
                text=True, timeout=5,
                creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0))
            if result.returncode:
                return False
            return any(row and row[0].strip().lower()=="vrchat.exe"
                       for row in csv.reader(result.stdout.splitlines()))
        except (OSError, subprocess.TimeoutExpired, csv.Error):
            return False

    def _check_logging_health(self, now):
        if now - self._logging_health_last_check < 15.0:
            return
        self._logging_health_last_check = now
        if not self._vrchat_process_running():
            self._logging_empty_since = None
            self._logging_warning = False
            return

        recent_output = False
        try:
            with os.scandir(self.log_dir) as entries:
                for entry in entries:
                    if not (entry.name.startswith("output_log_") and entry.name.endswith(".txt")):
                        continue
                    try:
                        stat = entry.stat()
                    except OSError:
                        continue
                    if stat.st_size > 0 and now-stat.st_mtime <= 300:
                        recent_output = True
                        break
        except OSError:
            pass

        if recent_output:
            self._logging_empty_since = None
            self._logging_warning = False
        else:
            if self._logging_empty_since is None:
                self._logging_empty_since = now
            self._logging_warning = (now-self._logging_empty_since) >= 60.0

'''
WARNING="WARNING: VRChat logging appears disabled (no recent log data). Enable VRChat output logging in VRChat settings and restart VRChat."

def patch_watcher(raw):
    s=raw.decode("utf-8")
    s=replace_once(s,"import os\n","import os\nimport csv\nimport subprocess\n","watcher imports")
    s=replace_once(s,"        self._last_summary = None\n",
        "        self._last_summary = None\n        self._logging_health_last_check = 0.0\n        self._logging_empty_since = None\n        self._logging_warning = False\n","watcher health state")
    s=replace_once(s,'                "message": self._summary_message(len(active)),\n',
        '                "message": self._summary_message(len(active)),\n                "logging_warning": self._logging_warning,\n',"watcher snapshot")
    s=replace_once(s,"    def _summary_message(self, active_count",
        HEALTH_METHODS+"    def _summary_message(self, active_count","watcher health method")
    s=replace_once(s,'        if not os.path.isdir(self.log_dir):\n            return "VRChat log directory not found"\n',
        '        if self._logging_warning:\n            return '+repr(WARNING)+'\n        if not os.path.isdir(self.log_dir):\n            return "VRChat log directory not found"\n',"watcher status warning")
    compact='        compact = (summary["running"], summary["active_logs"], summary["tracked_logs"], summary["active_players"], tuple(summary["log_names"]))'
    if compact in s:
        s=replace_once(s,compact,
            '        compact = (summary["running"], summary["active_logs"], summary["tracked_logs"], summary["active_players"], tuple(summary["log_names"]), summary["logging_warning"])',
            "standalone status change")
    else:
        s=replace_once(s,'            tuple(summary["log_names"]),\n',
            '            tuple(summary["log_names"]),\n            summary["logging_warning"],\n',
            "plugin status change")
    s=replace_once(s,"            self._retire_stale(now)\n            self._emit_status()\n",
       "            self._retire_stale(now)\n            self._check_logging_health(now)\n            self._emit_status()\n",
       "worker health check")
    compile(s,"tgc_vrchat_multilog.py","exec")
    return s.encode("utf-8")

def test_watcher(code):
    env={"__name__":"test_tgc_watcher"}
    exec(compile(code,"tgc_vrchat_multilog.py","exec"),env)
    cls=env["MultiVRChatLogWatcher"]
    with tempfile.TemporaryDirectory() as folder:
        node=cls(lambda *_:None,log_dir=folder)
        node._vrchat_process_running=lambda:False
        for t in (100,120,175,190):node._check_logging_health(t)
        assert not node.snapshot()["logging_warning"]
        node._vrchat_process_running=lambda:True
        for t in (210,225,240,255,270):node._check_logging_health(t)
        assert node.snapshot()["logging_warning"]
        assert "Enable VRChat" in node.snapshot()["message"]
        p=Path(folder)/"output_log_test.txt"
        p.write_text("VRChat started\n")
        os.utime(p,(285,285))
        node._check_logging_health(285)
        assert not node.snapshot()["logging_warning"]
        p.write_text("")
        os.utime(p,(300,300))
        for t in (300,315,330,345,360):node._check_logging_health(t)
        assert node.snapshot()["logging_warning"]
        node._vrchat_process_running=lambda:False
        node._check_logging_health(375)
        assert not node.snapshot()["logging_warning"]
        print("PASS: warning triggered, debounced, cleared, and skipped without process")

def patch_callback(source,method,logmethod):
    marker=f"    def {method}(self, summary):\n"
    replacement=f'''    def {method}(self, summary):
        if isinstance(summary, dict):
            active = bool(summary.get("logging_warning"))
            if active != getattr(self, "_last_logging_warning", False):
                self._last_logging_warning = active
                self.{logmethod}({repr(WARNING)} if active else
                                 "VRChat logging resumed; warning cleared.")
'''
    return replace_once(source,marker,replacement,method)

def get_zip(path):
    with zipfile.ZipFile(path) as arc:
        return {n:arc.read(n) for n in arc.namelist() if not n.endswith("/")}

def output_zip(path,files):
    with zipfile.ZipFile(path,"w",zipfile.ZIP_DEFLATED) as arc:
        for name,payload in sorted(files.items()):
            arc.writestr(name,payload)
    with zipfile.ZipFile(path) as arc:
        assert arc.testzip() is None
    return hashlib.sha256(path.read_bytes()).hexdigest()

manifest_file=ROOT/"manifest.json"
readme_file=ROOT/"README.md"
manifest=json.loads(manifest_file.read_text())
readme=readme_file.read_text()

for channel,old,new,source,verkey,oldver,newver,method,logger in [
    ("standalone","TGC_Invite_Node_v1.1.9_Required_Update.zip",
     "TGC_Invite_Node_v1.1.10_Required_Update.zip","tgc_invite_node.py",
     "APP_VERSION","1.1.9","1.1.10","_set_watcher_status","_log_recent"),
    ("plugin","TGC_Invite_Node_Plugin_v1.1.9-plugin_Required_Update.zip",
     "TGC_Invite_Node_Plugin_v1.1.10-plugin_Required_Update.zip",
     "plugins/distributed_invite_node.py","NODE_VERSION","1.1.9-plugin",
     "1.1.10-plugin","_on_watcher_status","_activity")]:
    files=get_zip(UPDATES/channel/old)
    watcher=patch_watcher(files["tgc_vrchat_multilog.py"])
    test_watcher(watcher)
    files["tgc_vrchat_multilog.py"]=watcher
    program=files[source].decode("utf-8")
    program=replace_once(program,f'{verkey} = "{oldver}"',f'{verkey} = "{newver}"',f"{channel} version")
    program=replace_once(program,'HOTFIX_REVISION = 1','HOTFIX_REVISION = 0',f"{channel} hotfix revision")
    program=patch_callback(program,method,logger)
    compile(program,source,"exec")
    assert "text=\"Link Discord\"" not in program
    files[source]=program.encode("utf-8")
    if "README_NODE.txt" in files:
        files["README_NODE.txt"]+=b"\nREQUIRED v1.1.10: automatic VRChat logging warning; clears after logs resume.\n"
    dest=UPDATES/channel/new
    checksum=output_zip(dest,files)
    manifest["releases"][channel].update(
        version=newver,sha256=checksum,policy="required",hotfix_revision=0,
        published_at=NOW,
        url=f"https://raw.githubusercontent.com/Seawolf035/TGC-Node-Updates/main/updates/{channel}/{new}",
        notes="Required update: detects VRChat.exe running without usable VRChat output logs; warns after a 60-second grace period and clears on recovery. No changes to tracking, deployment, queue, or credentials.")
    start=readme.index("### Standalone Node" if channel=="standalone" else "### Plugin Node")
    end=readme.find("\n### ",start+4)
    if end<0: end=readme.index("\nThe plugin package",start)
    section=readme[start:end].replace(oldver,newver).replace(old,new)
    section=re.sub(r"(?m)^- SHA-256:.*$",f"- SHA-256: "+chr(96)+checksum+chr(96),section)
    section+="\n- REQUIRED: automatic VRChat logging-disabled warning (60-second grace; clears on recovery).\n"
    readme=readme[:start]+section+readme[end:]
    print("PASS",channel,newver,checksum)

staffdir=UPDATES/"staff-launcher"
stafffiles=get_zip(staffdir/"TGC_Staff_Launcher_v1.1.4_Required_Node_Diagnostics.zip")
watcher=patch_watcher(stafffiles["TGC_Staff_Launcher_Package/tgc_vrchat_multilog.py"])
test_watcher(watcher)
stafffiles["TGC_Staff_Launcher_Package/tgc_vrchat_multilog.py"]=watcher
nodepath="TGC_Staff_Launcher_Package/tgc_staff_node.py"
node=stafffiles[nodepath].decode("utf-8")
node=replace_once(node,'NODE_VERSION = "1.1.9-staff"','NODE_VERSION = "1.1.10-staff"',"staff Node version")
node=patch_callback(node,"_on_watcher_status","_log")
compile(node,nodepath,"exec")
stafffiles[nodepath]=node.encode("utf-8")
apppath="TGC_Staff_Launcher_Package/TGC_Staff_Launcher.py"
app=stafffiles[apppath].decode("utf-8")
app=replace_once(app,'APP_VERSION = "1.1.4"','APP_VERSION = "1.1.5"',"staff Launcher version")
oldline='                self.node_logs_var.set(f"{status.get(\'active_logs\', 0)} active VRChat log(s) • {status.get(\'active_players\', 0)} tracked player(s)")'
newlines='''                watcher_message = str(status.get("watcher") or "")
                if watcher_message.startswith("WARNING: VRChat logging"):
                    self.node_logs_var.set(watcher_message)
                else:
                    self.node_logs_var.set(f"{status.get('active_logs', 0)} active VRChat log(s) • {status.get('active_players', 0)} tracked player(s)")'''
app=replace_once(app,oldline,newlines,"staff warning display")
compile(app,apppath,"exec")
stafffiles[apppath]=app.encode("utf-8")
name="TGC_Staff_Launcher_v1.1.5_Required_Logging_Warning.zip"
stafffiles["TGC_Staff_Launcher_Package/V1_1_5_LOGGING_WARNING.txt"]=(b"Required Staff Launcher 1.1.5: automatic VRChat logging check while game is running; 60-second grace; clears after logging resumes.\n")
checksum=output_zip(staffdir/name,stafffiles)
manifest["releases"]["staff_launcher"].update(
    version="1.1.5",sha256=checksum,policy="required",published_at=NOW,
    url=f"https://raw.githubusercontent.com/Seawolf035/TGC-Node-Updates/main/updates/staff-launcher/{name}",
    notes="Required Staff Launcher 1.1.5: embedded Invite Node v1.1.10-staff warns when VRChat logs are missing/empty despite VRChat running, and clears warning on recovery. Preserves existing verification, credentials, queue and VR slots.")
start=readme.index("### Staff Launcher")
end=readme.find("\n### ",start+4)
if end<0:end=readme.index("\nThe plugin package",start)
section=readme[start:end].replace("Staff Launcher 1.1.4","Staff Launcher 1.1.5").replace("TGC_Staff_Launcher_v1.1.4_Required_Node_Diagnostics.zip",name)
section=re.sub(r"(?m)^- SHA-256:.*$",f"- SHA-256: "+chr(96)+checksum+chr(96),section)
section+="\n- REQUIRED: embedded Node automatically warns when VRChat output logging appears disabled.\n"
readme=readme[:start]+section+readme[end:]
manifest_file.write_text(json.dumps(manifest,indent=2)+"\n")
readme_file.write_text(readme)
assert all(manifest["releases"][x]["policy"]=="required" for x in ("standalone","plugin","staff_launcher"))
print("PASS staff launcher 1.1.5",checksum)
print("PASS all checks and required manifest updates")
