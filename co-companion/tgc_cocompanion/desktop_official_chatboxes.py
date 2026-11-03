"""Official TGC VRChat chatboxes imported from Mobile Companion TgcOfficialChatboxes.

Texts/IDs are exact. Higher-rank labels require an ACTIVE authenticated
Relay lease with server-derived rank; roster data and local UI NEVER grant
the right to claim an official rank. Personal OSC phrases remain unrestricted.
"""
from __future__ import annotations
from dataclasses import dataclass
import json
from pathlib import Path
import tkinter as tk
from tkinter import ttk

from .pilot_enrollment import _atomic_bytes
from .vrchat_personal import USER_ID

RANKS = ("", "Trial Moderator", "Moderator", "Head Moderator",
         "Admin", "Head Admin", "Owner", "The Royalty")
RANK_NAMES = {r.lower(): i for i, r in enumerate(RANKS) if r}
ROYALTY_IDS = frozenset({
    "197820946608947201","344278828014895104","1385600758010876024",
})


def normalize_trusted_rank(raw, discord_id: str) -> str:
    """Caller must pass an authenticated Relay response, NEVER a local roster."""
    if not isinstance(raw, str):
        return ""
    role=raw.strip().lower().replace("_"," ").replace("-"," ")
    aliases={"head mod":"head moderator","trial mod":"trial moderator",
             "head administrator":"head admin","administrator":"admin",
             "co owner":"owner","royalty":"the royalty"}
    role=aliases.get(role,role)
    if role=="the royalty" and str(discord_id) not in ROYALTY_IDS:
        role="owner"
    return RANKS[RANK_NAMES[role]] if role in RANK_NAMES else ""


@dataclass(frozen=True)
class Template:
    id: str
    role: str
    text: str
    tier: int
    suggested: bool = False


CATALOG=(
    Template("common_discord","All Staff","『Discord.gg/TheGamerClub』",0,True),
    Template("common_staff","All Staff","『Gamer Club Staff』",0),
    Template("common_welcome","All Staff","『Welcome to The Gamer Club』",0,True),
    Template("common_instance","All Staff","『Instance Staff』",0,True),
    Template("common_do_not_mute","All Staff","『Do not mute/block me』",0,True),
    Template("trial_mod","Trial Moderator","『Gamer Club Trial Mod』",1),
    Template("moderator","Moderator","『Gamer Club Moderator』",2),
    Template("head_mod","Head Moderator","『Gamer Club Head Mod』",3),
    Template("admin","Admin","『Gamer Club Admin』",4),
    Template("head_admin","Head Admin","『Gamer Club Head Admin』",5),
    Template("owner","Owner","『Gamer Club Owner』",6),
    Template("royalty","The Royalty","『Gamer Club Royalty』",7),
)
BY_ID={item.id:item for item in CATALOG}


def allowed_catalog(verified: bool, server_rank: str):
    if verified is not True:
        return ()
    level=RANK_NAMES.get(str(server_rank).lower(),0)
    return tuple(item for item in CATALOG if item.tier<=level)


class OfficialSelection:
    """Only selected template identifiers persisted per personal VRChat user."""
    def __init__(self, directory: Path):
        self.path=Path(directory)/"official_chatboxes.json"
        try:
            if self.path.stat().st_size>16000:
                raise ValueError("Oversized settings")
            raw=json.loads(self.path.read_text(encoding="utf-8"))
            self.data=raw if isinstance(raw,dict) else {}
        except (OSError,ValueError,UnicodeError):
            self.data={}

    def get(self, user_id):
        if not isinstance(user_id,str) or not USER_ID.fullmatch(user_id):
            return set()
        values=self.data.get(user_id,[])
        return {str(v) for v in values if str(v) in BY_ID} if isinstance(values,list) else set()

    def toggle(self, user_id, template_id, eligible):
        if not isinstance(user_id,str) or not USER_ID.fullmatch(user_id):
            raise PermissionError("Sign in to personal VRChat first")
        if template_id not in BY_ID:
            raise ValueError("Unknown official TGC chatbox")
        selected=self.get(user_id)
        if template_id in selected:
            selected.remove(template_id)
        else:
            if template_id not in {item.id for item in eligible}:
                raise PermissionError("Verified staff rank does not allow that official chatbox")
            selected.add(template_id)
        if user_id not in self.data and len(self.data)>=25:
            raise ValueError("Maximum user settings reached")
        self.data[user_id]=[item.id for item in CATALOG if item.id in selected]
        _atomic_bytes(self.path,json.dumps(self.data).encode("utf-8"))
        return selected


class OfficialChatboxPage:
    def __init__(self,pages,book):
        self.pages=pages
        self.owner=pages.owner
        self.selection=OfficialSelection(self.owner.terms_dir)
        self.status=tk.StringVar(value="Verify your TGC owner pilot and sign in to personal VRChat.")
        self._official_active=False
        self._last_rank=""
        panel=ttk.Frame(book,padding=(16,12))
        book.add(panel,text="Staff Chatboxes")
        ttk.Label(panel,text="TGC OFFICIAL STAFF CHATBOXES",
                  style="PageTitle.TLabel").pack(anchor="w")
        ttk.Label(panel,text=(
            "Official messages match the Mobile Companion. Role-locked labels require "
            "your rank from the verified TGC Relay—not the public staff roster. "
            "Personal chatboxes stay available in OSC Studio."
        ),wraplength=790).pack(anchor="w",pady=(4,10))
        ttk.Label(panel,textvariable=self.status,wraplength=810).pack(
            anchor="w",pady=(0,8))
        target=ttk.Frame(panel)
        target.pack(fill="x",pady=(0,7))
        ttk.Label(target,text="Target VRChat PID:").pack(side="left")
        ttk.Entry(target,textvariable=pages.osc_pid,width=16).pack(side="left",padx=(7,13))
        ttk.Button(target,text="Refresh Staff Access",
                   command=self.refresh).pack(side="left",padx=(0,8))
        ttk.Button(target,text="Open OSC Studio",
                   command=lambda:self.owner.shell.open_page("OSC Studio")).pack(side="left")
        frame=ttk.Frame(panel);frame.pack(fill="both",expand=True)
        self.table=ttk.Treeview(frame,columns=("role","message","state"),
                  show="headings",height=13,selectmode="browse")
        for col,label,size in (
            ("role","Required rank",150),("message","Official VRChat message",310),
            ("state","Status",100)):
            self.table.heading(col,text=label)
            self.table.column(col,width=size,stretch=(col=="message"))
        self.table.pack(side="left",fill="both",expand=True)
        scroll=ttk.Scrollbar(frame,command=self.table.yview,orient="vertical")
        scroll.pack(side="right",fill="y")
        self.table.configure(yscrollcommand=scroll.set)
        buttons=ttk.Frame(panel);buttons.pack(fill="x",pady=(10,6))
        ttk.Button(buttons,text="Select / Deselect",
                   command=self.toggle).pack(side="left",padx=(0,7))
        ttk.Button(buttons,text="Send Selected Now",style="Accent.TButton",
                   command=self.send).pack(side="left",padx=(0,7))
        ttk.Button(buttons,text="Start Official Rotation",
                   command=self.start).pack(side="left",padx=(0,7))
        ttk.Button(buttons,text="Stop Rotation",command=self.stop).pack(side="left")
        ttk.Label(panel,text=(
            "Rotation uses the OSC Studio interval and pauses on revocation, "
            "rank downgrade or account change. Never broadcasts without a click."
        ),wraplength=790).pack(anchor="w")
        self.render()

    def _permitted(self):
        lease=self.owner.lease.current() if self.owner.lease else None
        verified=bool(lease and lease.valid_limit(__import__("time").time())
                      and self.owner._terms_valid()
                      and self.owner.enrollment.discord_id == lease.discord_id)
        rank=self.owner.lease.rank() if verified else ""
        return allowed_catalog(verified,rank)

    def _selected(self):
        row=self.table.selection()
        if not row:return None
        return BY_ID.get(row[0])

    def render(self):
        eligible={t.id for t in self._permitted()}
        user=self.pages.account.user_id if self.pages.account.signed_in else ""
        selected=self.selection.get(user)
        for item in self.table.get_children():
            self.table.delete(item)
        for template in CATALOG:
            label=("Selected" if template.id in selected and template.id in eligible else
                   "Available" if template.id in eligible else "Locked")
            self.table.insert("",tk.END,iid=template.id,
                              values=(template.role,template.text,label))
        tier=self.owner.lease.rank() if self.owner.lease else ""
        if eligible:
            self.status.set("Verified owner preview • official rank: "+(tier or
                "not supplied by Relay (common templates only)")+
                " • personal chatboxes stay separate.")
        else:
            self.status.set("Official staff templates locked until live staff verification; "
                            "personal OSC Studio remains available.")

    def refresh(self):
        self.render()

    def toggle(self):
        try:
            item=self._selected()
            if not item:raise ValueError("Select an official message first")
            self.pages._eligible(personal=True)
            self.selection.toggle(self.pages.account.user_id,item.id,self._permitted())
            self.render()
            self.table.selection_set(item.id)
        except Exception as exc:self.status.set(str(exc))

    def send(self):
        try:
            item=self._selected()
            if not item:raise ValueError("Select an official message first")
            if item not in self._permitted():
                raise PermissionError("This official label is not authorized by the current staff lease")
            self.pages._eligible(personal=True)
            ctrl,pid=self.pages._active_osc()
            ctrl.send_chat(pid,item.text)
            self.status.set(f"Official chatbox sent locally to VRChat PID {pid}")
        except Exception as exc:self.status.set(str(exc))

    def start(self):
        try:
            self.pages._eligible(personal=True)
            eligible={t.id for t in self._permitted()}
            templates=[t.text for t in CATALOG
                       if t.id in eligible and t.id in self.selection.get(self.pages.account.user_id)]
            if not templates:
                raise ValueError("Select at least one verified eligible official chatbox")
            ctrl,pid=self.pages._active_osc()
            ctrl.set_rotation(pid,templates[:8],int(self.pages.osc_interval.get()))
            self._official_active=True
            self._account=self.pages.account.user_id
            self._last_rank=self.owner.lease.rank() if self.owner.lease else ""
            self.status.set(f"Official rotation started on VRChat PID {pid}")
        except Exception as exc:self.status.set(str(exc))

    def stop(self):
        self._official_active=False
        if self.pages.controls:
            self.pages.controls.stop_rotation()
        self.status.set("Official chatbox rotation stopped")

    def verify_rotation(self):
        if not self._official_active:return
        if not self.pages.controls or not self.pages.controls.rotating:
            self._official_active=False
            return
        rank=self.owner.lease.rank() if self.owner.lease else ""
        eligible={t.id for t in self._permitted()}
        selected=self.selection.get(self.pages.account.user_id)
        if (self.pages.account.user_id!=self._account or rank!=self._last_rank
            or not selected.intersection(eligible)
            or not self.pages.account.signed_in):
            self.stop()
            self.status.set("Official rotation stopped: access/rank/account changed")
