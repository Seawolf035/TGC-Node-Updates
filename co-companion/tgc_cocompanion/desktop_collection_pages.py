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
        tab=ttk.Frame(book,padding=(13,10));book.add(tab,text="Avatars")
        ttk.Label(tab,text="AVATAR COLLECTION",style="PageTitle.TLabel").pack(anchor="w")
        ttk.Label(tab,text="Browse your favorite VRChat avatars or inspect their details. Changing your avatar always asks for confirmation.",
                  style="PageSubtitle.TLabel",wraplength=780).pack(anchor="w",pady=(3,9))
        loads=ttk.Frame(tab);loads.pack(fill="x",pady=(0,6))
        ttk.Button(loads,text="Load Favorite Avatars",style="Accent.TButton",
                   command=self.load_avatars).pack(side="left",padx=(0,9))
        ttk.Button(loads,text="Load Avatar Groups",command=self.load_avatar_groups).pack(side="left")
        actions=ttk.Frame(tab);actions.pack(fill="x",pady=(0,9))
        ttk.Button(actions,text="View Selected Avatar",command=self.avatar_details
                   ).pack(side="left",padx=(0,9))
        ttk.Button(actions,text="Wear Selected Avatar",command=self.wear_avatar).pack(side="left")
        self.avatar_status=tk.StringVar(value="Sign in to your personal VRChat account")
        ttk.Label(tab,textvariable=self.avatar_status,wraplength=780).pack(anchor="w",pady=(0,7))
        listing=ttk.Frame(tab);listing.pack(fill="both",expand=True)
        columns=("name","author","id")
        self.avatar_table=ttk.Treeview(listing,columns=columns,show="headings",height=9)
        for col,width in (("name",230),("author",160),("id",260)):
            self.avatar_table.heading(col,text=col.title())
            self.avatar_table.column(col,width=width,stretch=(col!="id"))
        avatar_scroll=ttk.Scrollbar(listing,orient="vertical",command=self.avatar_table.yview)
        self.avatar_table.configure(yscrollcommand=avatar_scroll.set)
        self.avatar_table.pack(side="left",fill="both",expand=True)
        avatar_scroll.pack(side="right",fill="y")
        details=ttk.LabelFrame(tab,text="Selected avatar",padding=(12,8))
        details.pack(fill="x",pady=(8,0))
        self.avatar_title_var=tk.StringVar(value="Select an avatar, then choose View Selected Avatar")
        self.avatar_meta_var=tk.StringVar(value="")
        ttk.Label(details,textvariable=self.avatar_title_var,style="PageTitle.TLabel",
                  wraplength=700).pack(anchor="w")
        ttk.Label(details,textvariable=self.avatar_meta_var,
                  style="PageSubtitle.TLabel",wraplength=700).pack(anchor="w",pady=(3,5))
        self.avatar_detail=tk.Text(details,height=3,wrap="word",state="disabled",
                                   bg="#16243A",fg="#E7F1FF",borderwidth=0,
                                   relief="flat",font=("Segoe UI",10))
        self.avatar_detail.pack(fill="x")

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
            if not isinstance(data,dict):
                self.avatar_status.set("Avatar details were unavailable; previous selection preserved.")
                return
            name=str(data.get("name") or "Unnamed avatar")[:150]
            author=str(data.get("authorName") or "Unknown author")[:100]
            release=str(data.get("releaseStatus") or "Not supplied")[:60]
            version=str(data.get("version") or "Not supplied")[:40]
            ident=str(data.get("id") or "")[:90]
            description=str(data.get("description") or "No description provided.")[:3000]
            self.avatar_title_var.set(name)
            self.avatar_meta_var.set(
                f"By {author}  •  Release: {release}  •  Version: {version}\n{ident}")
            self.avatar_detail.config(state="normal")
            self.avatar_detail.delete("1.0",tk.END)
            self.avatar_detail.insert("1.0",description)
            self.avatar_detail.config(state="disabled")
            self.avatar_status.set("Avatar details loaded from your personal VRChat session.")
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
        filter_row=ttk.Frame(tab);filter_row.pack(fill="x",pady=(4,0))
        ttk.Label(filter_row,text="Filter saved worlds:").pack(side="left",padx=(0,8))
        self.bookmark_filter=tk.StringVar()
        ttk.Entry(filter_row,textvariable=self.bookmark_filter,width=35).pack(side="left")
        self.bookmark_filter.trace_add("write",lambda *_:self.refresh_bookmarks())
        self.bookmark_count=tk.StringVar(value="No saved worlds loaded")
        ttk.Label(filter_row,textvariable=self.bookmark_count).pack(side="right")
        self.bookmark_table=ttk.Treeview(tab,columns=("name","id"),show="headings",height=13)
        for col,width in (("name",270),("id",430)):
            self.bookmark_table.heading(col,text=col.title());self.bookmark_table.column(col,width=width)
        self.bookmark_table.pack(fill="both",expand=True,pady=8)
        actions=ttk.Frame(tab);actions.pack(anchor="w",pady=8)
        ttk.Button(actions,text="Refresh",command=self.refresh_bookmarks).pack(side="left",padx=(0,8))
        ttk.Button(actions,text="Open World in Browser",command=self.open_bookmark).pack(side="left",padx=(0,8))
        ttk.Button(actions,text="Copy World Link",command=self.copy_bookmark_link).pack(side="left",padx=(0,8))
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
            needle=self.bookmark_filter.get().casefold().strip()
            visible=sorted((row for row in items if needle in row["name"].casefold() or
                            needle in row["id"].casefold()),
                           key=lambda row:row["name"].casefold())
            self.bookmark_table.delete(*self.bookmark_table.get_children())
            for row in visible:self.bookmark_table.insert("",tk.END,values=(row["name"],row["id"]))
            self.bookmark_count.set(f"{len(visible)} shown / {len(items)} saved")
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

    def copy_bookmark_link(self):
        try:
            self._eligible()
            ident=self._bookmark_selected()
            url="https://vrchat.com/home/world/"+ident
            self.owner.root.clipboard_clear()
            self.owner.root.clipboard_append(url)
            self._error("VRChat world link copied to clipboard")
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
