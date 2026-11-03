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


def highest_role_template(eligible, rank):
    """Current authenticated rank only (never inferred from the roster/UI)."""
    tier=RANK_NAMES.get(str(rank).lower(),0)
    if tier < 1:
        return None
    return next((t for t in eligible if t.tier==tier),None)


def selection_preset(preset, eligible, rank):
    """Trusted server-authorized one-click selection; no invented role."""
    allowed=tuple(eligible)
    if preset=="common":
        return {t.id for t in allowed if t.tier==0}
    if preset=="suggested":
        return {t.id for t in allowed if t.suggested}
    if preset=="all":
        return {t.id for t in allowed}
    if preset=="rank":
        match=highest_role_template(allowed,rank)
        if match is None:
            raise PermissionError("Relay has not granted your rank-specific staff chatbox")
        return {match.id}
    if preset=="clear":
        return set()
    raise ValueError("Unknown official chatbox quick selection")


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

    def replace(self, user_id, selected_ids, eligible):
        if not isinstance(user_id,str) or not USER_ID.fullmatch(user_id):
            raise PermissionError("Sign in to personal VRChat first")
        allowed={item.id for item in eligible}
        selected=set(selected_ids)
        if not selected.issubset(allowed):
            raise PermissionError("Your verified staff rank does not permit those chatboxes")
        if user_id not in self.data and len(self.data)>=25:
            raise ValueError("Maximum user settings reached")
        self.data[user_id]=[t.id for t in CATALOG if t.id in selected]
        _atomic_bytes(self.path,json.dumps(self.data).encode("utf-8"))
        return selected

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
        quick=ttk.LabelFrame(panel,text="QUICK SELECT",padding=(10,7))
        quick.pack(fill="x",pady=(2,8))
        presets=ttk.Frame(quick);presets.pack(fill="x",pady=(0,5))
        for label,mode in (("My Rank Box","rank"),("Common Staff","common"),
                           ("Recommended","suggested"),("All Available","all"),
                           ("Clear All","clear")):
            ttk.Button(presets,text=label,
                command=lambda m=mode:self.quick_select(m)).pack(
                    side="left",padx=(0,7))
        self.role_actions=ttk.Frame(quick);self.role_actions.pack(fill="x")
        ttk.Label(quick,text="Double-click any unlocked row to check/uncheck it. "
                  "Role buttons are based on the live Relay rank.",
                  style="PageSubtitle.TLabel",wraplength=730).pack(
                      anchor="w",pady=(5,0))
        frame=ttk.Frame(panel);frame.pack(fill="both",expand=True)
        self.table=ttk.Treeview(frame,columns=("checked","role","message","state"),
                  show="headings",height=11,selectmode="browse")
        for col,label,size in (
            ("checked","Use",48),("role","Required rank",138),
            ("message","Official VRChat message",315),("state","Status",86)):
            self.table.heading(col,text=label)
            self.table.column(col,width=size,stretch=(col=="message"),
                              anchor="center" if col=="checked" else "w")
        self.table.pack(side="left",fill="both",expand=True)
        scroll=ttk.Scrollbar(frame,command=self.table.yview,orient="vertical")
        scroll.pack(side="right",fill="y")
        self.table.configure(yscrollcommand=scroll.set)
        self.table.bind("<Double-1>",self._toggle_double_click)
        buttons=ttk.Frame(panel);buttons.pack(fill="x",pady=(10,6))
        ttk.Button(buttons,text="Toggle Highlighted",
                   command=self.toggle).pack(side="left",padx=(0,7))
        ttk.Button(buttons,text="Send Highlighted",style="Accent.TButton",
                   command=self.send).pack(side="left",padx=(0,7))
        ttk.Button(buttons,text="Send My Rank Now",
                   command=self.send_rank).pack(side="left",padx=(0,7))
        ttk.Button(buttons,text="Start Rotation",
                   command=self.start).pack(side="left",padx=(0,7))
        ttk.Button(buttons,text="Stop",command=self.stop).pack(side="left")
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
        permitted=self._permitted()
        eligible={t.id for t in permitted}
        user=self.pages.account.user_id if self.pages.account.signed_in else ""
        selected=self.selection.get(user)
        focus=self.table.selection()
        focus=focus[0] if focus else None
        self.table.delete(*self.table.get_children())
        for template in CATALOG:
            enabled=template.id in eligible
            label=("Selected" if template.id in selected and enabled else
                   "Available" if enabled else "Locked")
            self.table.insert("",tk.END,iid=template.id,
                values=("✓" if template.id in selected and enabled else
                        "×" if not enabled else "○",
                        template.role,template.text,label))
        if focus in self.table.get_children():
            self.table.selection_set(focus)
        # One-click role buttons cannot create privileges; lower-rank boxes
        # remain available according to the server-provided tier hierarchy.
        for widget in self.role_actions.winfo_children():
            widget.destroy()
        ttk.Label(self.role_actions,text="ROLE BOXES:",
                  style="SideHint.TLabel").pack(side="left",padx=(0,7))
        for template in CATALOG:
            if template.tier==0:
                continue
            enabled=template.id in eligible
            ttk.Button(self.role_actions,text=template.role,
                state="normal" if enabled else "disabled",
                command=lambda ident=template.id:self.choose_role(ident)
                ).pack(side="left",padx=(0,4))
        tier=self.owner.lease.rank() if self.owner.lease and eligible else ""
        available_count=len(selected.intersection(eligible))
        if eligible:
            self.status.set(f"{available_count} checked / {len(eligible)} allowed • "
                "Verified rank: "+(tier or "not supplied (common only)")+
                " • Click any preset or double-click a row.")
        else:
            self.status.set("Staff messages locked pending verified Relay access. "
                "Personal OSC Studio remains available.")

    def _toggle_double_click(self,event):
        # Treeview row identification ensures header clicks never change prefs.
        item=self.table.identify_row(event.y)
        if item in BY_ID:
            self.table.selection_set(item)
            self.toggle()

    def quick_select(self,mode):
        try:
            self.pages._eligible(personal=True)
            current=self._permitted()
            if not current:
                raise PermissionError("Verify TGC staff access to select official chatboxes")
            rank=self.owner.lease.rank() if self.owner.lease else ""
            desired=selection_preset(mode,current,rank)
            self.selection.replace(self.pages.account.user_id,desired,current)
            self._selection_changed()
            self.status.set(f"Checked {len(desired)} official chatbox(es) using "
                            f"{mode} preset.")
        except Exception as exc:
            self.status.set(str(exc))

    def choose_role(self,template_id):
        try:
            self.pages._eligible(personal=True)
            current=self._permitted()
            template=BY_ID.get(template_id)
            if template is None or template not in current or template.tier==0:
                raise PermissionError("This role box is not permitted by the current Relay rank")
            self.selection.replace(self.pages.account.user_id,{template_id},current)
            self._selection_changed()
            self.table.selection_set(template_id)
            self.status.set("Selected "+template.role+" chatbox for your next send/rotation.")
        except Exception as exc:
            self.status.set(str(exc))

    def _selection_changed(self):
        # Rotations are stopped before switching selected phrases. This
        # prevents outdated messages from broadcasting until next tick.
        if self._official_active:
            self.stop()
        self.render()

    def send_rank(self):
        try:
            self.pages._eligible(personal=True)
            permitted=self._permitted()
            rank=self.owner.lease.rank() if self.owner.lease else ""
            template=highest_role_template(permitted,rank)
            if template is None:
                raise PermissionError("Your current verified Relay rank does not "
                                      "provide a rank-specific chatbox")
            ctrl,pid=self.pages._active_osc()
            ctrl.send_chat(pid,template.text)
            self.status.set(f"Sent {template.role} chatbox to VRChat PID {pid}")
        except Exception as exc:
            self.status.set(str(exc))

    def refresh(self):
        self.render()

    def toggle(self):
        try:
            item=self._selected()
            if not item:raise ValueError("Select an official message first")
            self.pages._eligible(personal=True)
            self.selection.toggle(self.pages.account.user_id,item.id,self._permitted())
            self._selection_changed()
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
            chosen=[t for t in CATALOG
                    if t.id in eligible and t.id in self.selection.get(self.pages.account.user_id)]
            if not chosen:
                raise ValueError("Select at least one verified eligible official chatbox")
            ctrl,pid=self.pages._active_osc()
            chosen=chosen[:8]
            ctrl.set_rotation(pid,[t.text for t in chosen],int(self.pages.osc_interval.get()))
            self._rotation_ids=tuple(t.id for t in chosen)
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
            or not set(getattr(self,"_rotation_ids",())).issubset(selected.intersection(eligible))
            or not self.pages.account.signed_in):
            self.stop()
            self.status.set("Official rotation stopped: access/rank/account changed")
