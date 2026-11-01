"""Windows Avatar Favorites, personal World Bookmarks and inbox actions.

World bookmarks are local, per-VRChat-account, not friend data and never
uploaded to Relay. Avatar selection and inbox mutation require confirmation
from the signed-in user. Favorite Avatars use actual VRChat API permissions.
"""
from __future__ import annotations

import json
from pathlib import Path
import re
import tkinter as tk
from tkinter import ttk,messagebox
from urllib.parse import quote
import webbrowser

from .pilot_enrollment import _atomic_bytes
from .vrchat_personal import USER_ID

WORLD_ID=re.compile(r"wrld_[0-9a-fA-F-]{36}\Z")
AVATAR_ID=re.compile(r"avtr_[0-9a-fA-F-]{36}\Z")


class WorldBookmarks:
    def __init__(self,directory:Path):
        self.path=directory/"personal_world_bookmarks.json"
        try:
            data=json.loads(self.path.read_text(encoding="utf-8"))
            self.data=data if isinstance(data,dict) else {}
        except (OSError,ValueError):
            self.data={}

    def list(self,account:str)->list[dict]:
        if not USER_ID.fullmatch(account):
            raise PermissionError("Personal VRChat account required")
        entries=self.data.get(account,[])
        if not isinstance(entries,list):return []
        return [e for e in entries[:100] if isinstance(e,dict)
                and WORLD_ID.fullmatch(str(e.get("id") or ""))]

    def save(self,account:str,world_id:str,name:str):
        if not WORLD_ID.fullmatch(str(world_id)):
            raise ValueError("Choose a valid VRChat world ID")
        if not isinstance(name,str) or not 1<=len(name.strip())<=100:
            raise ValueError("World label must be 1–100 characters")
        items=[e for e in self.list(account) if e["id"]!=world_id]
        if len(items)>=100:raise ValueError("Maximum 100 personal world bookmarks")
        items.insert(0,{"id":world_id,"name":name.strip()})
        self.data[account]=items
        self._save()

    def remove(self,account:str,world_id:str):
        self.data[account]=[e for e in self.list(account) if e["id"]!=world_id]
        self._save()

    def _save(self):
        _atomic_bytes(self.path,json.dumps(self.data,ensure_ascii=False,indent=2).encode("utf-8"))


class CollectionPages:
    def __init__(self,pages,notebook):
        self.pages=pages
        self.owner=pages.owner
        self.account=pages.account
        self.bookmarks=WorldBookmarks(self.owner.terms_dir)
        self._avatars=[]
        self._inbox=[]
        self._build_avatars(notebook)
        self._build_bookmarks(notebook)
        self._build_inbox(notebook)

    def _eligible(self):
        self.pages._eligible(personal=True)

    def _error(self,ex):
        self.pages._notice(ex)

    def _build_avatars(self,book):
        tab=ttk.Frame(book,padding=13);book.add(tab,text="Avatars")
        ttk.Label(tab,text="VRChat Avatar Favorites",font=("Segoe UI",15,"bold")).pack(anchor="w")
        ttk.Label(tab,text="Browse your personal account's favorite avatars. Select Avatar requires confirmation.",
                  wraplength=820).pack(anchor="w",pady=(4,8))
        toolbar=ttk.Frame(tab);toolbar.pack(anchor="w",pady=8)
        ttk.Button(toolbar,text="Load Favorite Avatars",command=self.load_avatars).pack(side="left",padx=(0,8))
        ttk.Button(toolbar,text="Load Avatar Groups",command=self.load_avatar_groups).pack(side="left",padx=(0,8))
        ttk.Button(toolbar,text="View Selected Avatar",command=self.avatar_details).pack(side="left",padx=(0,8))
        ttk.Button(toolbar,text="Wear Selected Avatar",command=self.wear_avatar).pack(side="left")
        self.avatar_status=tk.StringVar(value="Sign in to your personal VRChat account")
        ttk.Label(tab,textvariable=self.avatar_status,wraplength=830).pack(anchor="w")
        columns=("name","author","id")
        self.avatar_table=ttk.Treeview(tab,columns=columns,show="headings",height=11)
        for col,width in (("name",270),("author",200),("id",320)):
            self.avatar_table.heading(col,text=col.title());self.avatar_table.column(col,width=width)
        self.avatar_table.pack(fill="both",expand=True,pady=8)
        self.avatar_detail=tk.Text(tab,height=6,wrap="word",state="disabled",
                                   bg="#16243A",fg="#E7F1FF")
        self.avatar_detail.pack(fill="both",expand=True,pady=6)

    def _avatar_id(self):
        selected=self.avatar_table.selection()
        if not selected:raise ValueError("Select an avatar first")
        value=self.avatar_table.item(selected[0],"values")
        if len(value)<3 or not AVATAR_ID.fullmatch(value[2]):
            raise ValueError("Selected row is not a real VRChat avatar")
        return value[2]

    def load_avatars(self):
        def work():
            self._eligible()
            result=[]
            for offset in range(0,601,60):
                page=self.account.avatar_favorites(offset)
                result.extend(page)
                if len(page)<60:break
            return result
        def done(items):
            self._avatars=items
            self.avatar_table.delete(*self.avatar_table.get_children())
            for item in items:
                self.avatar_table.insert("",tk.END,values=(
                    str(item.get("name") or item.get("avatarName") or "Unnamed")[:100],
                    str(item.get("authorName") or "")[:90],
                    str(item.get("id") or "")[:80]))
            self.avatar_status.set(f"{len(items)} favorite avatar entries loaded")
        self.owner._run_async(work,done)

    def load_avatar_groups(self):
        def work():
            self._eligible()
            return self.account.avatar_groups()
        def done(groups):
            self.avatar_status.set("Favorite avatar groups: "+", ".join(str(x.get("displayName") or x.get("name") or "?")[:40] for x in groups)[:400])
        self.owner._run_async(work,done)

    def avatar_details(self):
        try:ident=self._avatar_id()
        except Exception as exc:self._error(exc);return
        def work():
            self._eligible()
            return self.account.avatar(ident)
        def done(data):
            result="\n".join(f"{key}: {str(data[key])[:250]}"
                             for key in ("name","authorName","id","description","releaseStatus","version")
                             if key in data and data[key] not in ("",None))
            self.avatar_detail.config(state="normal")
            self.avatar_detail.delete("1.0",tk.END)
            self.avatar_detail.insert("1.0",result)
            self.avatar_detail.config(state="disabled")
        self.owner._run_async(work,done)

    def wear_avatar(self):
        try:self._eligible(); ident=self._avatar_id()
        except Exception as exc:self._error(exc);return
        if not messagebox.askyesno("Switch VRChat Avatar",
                "Apply this avatar to your PERSONAL VRChat account?\n"+ident):
            return
        self.owner._run_async(lambda:self.account.select_avatar(ident),
            lambda _:self.avatar_status.set("VRChat avatar selection requested"))

    def _build_bookmarks(self,book):
        tab=ttk.Frame(book,padding=14);book.add(tab,text="Bookmarks")
        ttk.Label(tab,text="Saved World Bookmarks",font=("Segoe UI",15,"bold")).pack(anchor="w")
        ttk.Label(tab,text="Personal local bookmarks, not VRChat favorites. Stored separately for each VRChat account.",
                  wraplength=830).pack(anchor="w",pady=(4,8))
        controls=ttk.Frame(tab);controls.pack(fill="x",pady=8)
        self.bookmark_world=tk.StringVar()
        self.bookmark_name=tk.StringVar()
        ttk.Entry(controls,textvariable=self.bookmark_world,width=43).pack(side="left",padx=(0,8))
        ttk.Entry(controls,textvariable=self.bookmark_name,width=26).pack(side="left",padx=(0,8))
        ttk.Button(controls,text="Save World",command=self.save_bookmark).pack(side="left")
        self.bookmark_table=ttk.Treeview(tab,columns=("name","id"),show="headings",height=13)
        for col,width in (("name",270),("id",430)):
            self.bookmark_table.heading(col,text=col.title());self.bookmark_table.column(col,width=width)
        self.bookmark_table.pack(fill="both",expand=True,pady=8)
        actions=ttk.Frame(tab);actions.pack(anchor="w",pady=8)
        ttk.Button(actions,text="Refresh",command=self.refresh_bookmarks).pack(side="left",padx=(0,8))
        ttk.Button(actions,text="Open World in Browser",command=self.open_bookmark).pack(side="left",padx=(0,8))
        ttk.Button(actions,text="Remove Bookmark",command=self.remove_bookmark).pack(side="left")

    def bookmark_from_social(self,world_id,name):
        self.bookmark_world.set(world_id)
        self.bookmark_name.set(name)
        self.save_bookmark()

    def save_bookmark(self):
        try:
            self._eligible()
            self.bookmarks.save(self.account.user_id,self.bookmark_world.get().strip(),
                                self.bookmark_name.get().strip())
            self.refresh_bookmarks()
        except Exception as exc:self._error(exc)

    def refresh_bookmarks(self):
        try:
            self._eligible()
            items=self.bookmarks.list(self.account.user_id)
            self.bookmark_table.delete(*self.bookmark_table.get_children())
            for row in items:self.bookmark_table.insert("",tk.END,values=(row["name"],row["id"]))
        except Exception as exc:self._error(exc)

    def _bookmark_selected(self):
        selected=self.bookmark_table.selection()
        if not selected:raise ValueError("Select a world bookmark")
        return self.bookmark_table.item(selected[0],"values")[1]

    def open_bookmark(self):
        try:
            self._eligible()
            ident=self._bookmark_selected()
            webbrowser.open("https://vrchat.com/home/world/"+ident)
        except Exception as exc:self._error(exc)

    def remove_bookmark(self):
        try:
            self._eligible()
            ident=self._bookmark_selected()
            if not messagebox.askyesno("Remove Local Bookmark","Remove this saved world from Co-Companion?"):return
            self.bookmarks.remove(self.account.user_id,ident)
            self.refresh_bookmarks()
        except Exception as exc:self._error(exc)

    def _build_inbox(self,book):
        tab=ttk.Frame(book,padding=14);book.add(tab,text="Inbox")
        ttk.Label(tab,text="VRChat Inbox",font=("Segoe UI",15,"bold")).pack(anchor="w")
        ttk.Label(tab,text="Your personal VRChat notifications. Mark Seen and Dismiss affect only your personal VRChat inbox.",
                  wraplength=830).pack(anchor="w",pady=(4,8))
        bar=ttk.Frame(tab);bar.pack(anchor="w",pady=7)
        ttk.Button(bar,text="Refresh Inbox",command=self.load_inbox).pack(side="left",padx=(0,8))
        ttk.Button(bar,text="Mark Selected Seen",command=self.seen).pack(side="left",padx=(0,8))
        ttk.Button(bar,text="Dismiss Selected",command=self.dismiss).pack(side="left")
        self.inbox_status=tk.StringVar(value="No notifications loaded")
        ttk.Label(tab,textvariable=self.inbox_status,wraplength=830).pack(anchor="w")
        self.inbox_table=ttk.Treeview(tab,columns=("type","message","id"),show="headings",height=15)
        for col,width in (("type",170),("message",440),("id",270)):
            self.inbox_table.heading(col,text=col.title());self.inbox_table.column(col,width=width)
        self.inbox_table.pack(fill="both",expand=True,pady=9)

    def load_inbox(self):
        def work():
            self._eligible()
            return self.account.browse("inbox")
        def done(rows):
            self._inbox=rows
            self.inbox_table.delete(*self.inbox_table.get_children())
            for row in rows:self.inbox_table.insert("",tk.END,values=(
                str(row.get("type") or "")[:70],
                str(row.get("message") or row.get("senderUsername") or "")[:170],
                str(row.get("id") or "")[:110]))
            self.inbox_status.set(f"{len(rows)} inbox records loaded")
        self.owner._run_async(work,done)

    def _notification_id(self):
        selected=self.inbox_table.selection()
        if not selected:raise ValueError("Select a notification first")
        return self.inbox_table.item(selected[0],"values")[2]

    def seen(self):
        try:self._eligible();identifier=self._notification_id()
        except Exception as exc:self._error(exc);return
        self.owner._run_async(lambda:self.account.notification_seen(identifier),
                              lambda _:self.inbox_status.set("Notification marked seen"))

    def dismiss(self):
        try:self._eligible();identifier=self._notification_id()
        except Exception as exc:self._error(exc);return
        if not messagebox.askyesno("Dismiss VRChat Notification",
                "Remove this selected notification from your personal VRChat inbox?"):return
        self.owner._run_async(lambda:self.account.dismiss_notification(identifier),
                              lambda _:self.load_inbox())
