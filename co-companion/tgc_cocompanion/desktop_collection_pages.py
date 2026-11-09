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
        self._avatar_account=None
        self._inbox=[]
        self._inbox_account=None
        self._inbox_rows={}
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
        if not self.account.signed_in or self.account.user_id!=self._avatar_account:
            raise PermissionError("Load avatars for your current VRChat account")
        selected=self.avatar_table.selection()
        if not selected:raise ValueError("Select an avatar first")
        value=self.avatar_table.item(selected[0],"values")
        if len(value)<3 or not AVATAR_ID.fullmatch(value[2]):
            raise ValueError("Selected row is not a real VRChat avatar")
        return value[2]

    def load_avatars(self):
        account=self.account.user_id if self.account.signed_in else None
        def work():
            self._eligible()
            if self.account.user_id!=account:
                raise PermissionError("Personal VRChat account changed during avatar load")
            result=[]
            for offset in range(0,601,60):
                page=self.account.avatar_favorites(offset)
                result.extend(page)
                if len(page)<60:break
            return result
        def done(items):
            if not self.account.signed_in or self.account.user_id!=account:
                return
            self._avatars=items
            self._avatar_account=account
            self.avatar_table.delete(*self.avatar_table.get_children())
            for item in items:
                self.avatar_table.insert("",tk.END,values=(
                    str(item.get("name") or item.get("avatarName") or "Unnamed")[:100],
                    str(item.get("authorName") or "")[:90],
                    str(item.get("id") or "")[:80]))
            self.avatar_status.set(f"{len(items)} favorite avatar entries loaded")
        self.owner._run_async(work,done)

    def load_avatar_groups(self):
        account=self.account.user_id if self.account.signed_in else None
        def work():
            self._eligible()
            if self.account.user_id!=account:
                raise PermissionError("Personal VRChat account changed during group load")
            return self.account.avatar_groups()
        def done(groups):
            if not self.account.signed_in or self.account.user_id!=account:return
            self.avatar_status.set("Favorite avatar groups: "+", ".join(str(x.get("displayName") or x.get("name") or "?")[:40] for x in groups)[:400])
        self.owner._run_async(work,done)

    def avatar_details(self):
        try:ident=self._avatar_id()
        except Exception as exc:self._error(exc);return
        account=self.account.user_id
        def work():
            self._eligible()
            if self.account.user_id!=account:
                raise PermissionError("Personal VRChat account changed during avatar details")
            return self.account.avatar(ident)
        def done(data):
            if not self.account.signed_in or self.account.user_id!=account:return
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
        tab=ttk.Frame(book,padding=(13,10));book.add(tab,text="Bookmarks")
        ttk.Label(tab,text="SAVED VRCHAT WORLDS",style="PageTitle.TLabel").pack(anchor="w")
        ttk.Label(tab,text="Local bookmarks belong to your personal VRChat account, not your official VRChat favorites or TGC Relay.",
                  style="PageSubtitle.TLabel",wraplength=780).pack(anchor="w",pady=(3,9))
        form=ttk.LabelFrame(tab,text="Save a world",padding=(12,10))
        form.pack(fill="x",pady=(0,9))
        self.bookmark_world=tk.StringVar()
        self.bookmark_name=tk.StringVar()
        form.columnconfigure(0,weight=3)
        form.columnconfigure(1,weight=2)
        ttk.Label(form,text="VRChat World ID").grid(row=0,column=0,sticky="w",padx=(0,10))
        ttk.Label(form,text="Display name").grid(row=0,column=1,sticky="w")
        ttk.Entry(form,textvariable=self.bookmark_world).grid(row=1,column=0,sticky="ew",padx=(0,10),pady=(3,7))
        ttk.Entry(form,textvariable=self.bookmark_name).grid(row=1,column=1,sticky="ew",pady=(3,7))
        ttk.Button(form,text="Save World",style="Accent.TButton",command=self.save_bookmark
                   ).grid(row=2,column=0,sticky="w")
        filter_row=ttk.Frame(tab);filter_row.pack(fill="x",pady=(0,8))
        ttk.Label(filter_row,text="Find saved world").pack(side="left",padx=(0,8))
        self.bookmark_filter=tk.StringVar()
        ttk.Entry(filter_row,textvariable=self.bookmark_filter).pack(
            side="left",fill="x",expand=True,padx=(0,10))
        self.bookmark_filter.trace_add("write",lambda *_:self.refresh_bookmarks())
        self.bookmark_count=tk.StringVar(value="No saved worlds loaded")
        ttk.Label(filter_row,textvariable=self.bookmark_count).pack(side="right")
        body=ttk.Frame(tab);body.pack(fill="both",expand=True)
        self.bookmark_table=ttk.Treeview(body,columns=("name","id"),show="headings",height=12)
        for col,width in (("name",265),("id",350)):
            self.bookmark_table.heading(col,text=col.title())
            self.bookmark_table.column(col,width=width,stretch=(col=="name"))
        bookmark_scroll=ttk.Scrollbar(body,orient="vertical",
                                      command=self.bookmark_table.yview)
        self.bookmark_table.configure(yscrollcommand=bookmark_scroll.set)
        self.bookmark_table.pack(side="left",fill="both",expand=True)
        bookmark_scroll.pack(side="right",fill="y")
        actions=ttk.Frame(tab);actions.pack(fill="x",pady=(9,3))
        ttk.Button(actions,text="Refresh",command=self.refresh_bookmarks
                   ).pack(side="left",padx=(0,8))
        ttk.Button(actions,text="Open World",command=self.open_bookmark
                   ).pack(side="left",padx=(0,8))
        ttk.Button(actions,text="Copy World Link",command=self.copy_bookmark_link
                   ).pack(side="left",padx=(0,8))
        ttk.Button(actions,text="Remove Bookmark",command=self.remove_bookmark
                   ).pack(side="left")

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
        tab=ttk.Frame(book,padding=(13,10));book.add(tab,text="Inbox")
        ttk.Label(tab,text="PERSONAL VRCHAT INBOX",style="PageTitle.TLabel").pack(anchor="w")
        ttk.Label(tab,text="Browse notifications from your personal VRChat session. Mark Seen and Dismiss are explicit VRChat account actions.",
                  style="PageSubtitle.TLabel",wraplength=780).pack(anchor="w",pady=(3,9))
        ttk.Button(tab,text="Refresh Inbox",style="Accent.TButton",
                   command=self.load_inbox).pack(anchor="w",pady=(0,9))
        filters=ttk.Frame(tab);filters.pack(fill="x",pady=(0,8))
        ttk.Label(filters,text="Find notification").pack(side="left",padx=(0,8))
        self.inbox_query=tk.StringVar()
        ttk.Entry(filters,textvariable=self.inbox_query).pack(
            side="left",fill="x",expand=True,padx=(0,9))
        self.inbox_type=tk.StringVar(value="All types")
        self.inbox_type_picker=ttk.Combobox(filters,textvariable=self.inbox_type,
                                           values=("All types",),state="readonly",width=19)
        self.inbox_type_picker.pack(side="left")
        self.inbox_query.trace_add("write",lambda *_:self.render_inbox())
        self.inbox_type.trace_add("write",lambda *_:self.render_inbox())
        self.inbox_status=tk.StringVar(value="No notifications loaded")
        ttk.Label(tab,textvariable=self.inbox_status,wraplength=780).pack(anchor="w",pady=(0,6))
        body=ttk.Frame(tab);body.pack(fill="both",expand=True)
        self.inbox_table=ttk.Treeview(body,columns=("type","message","id"),
                                     show="headings",height=12,selectmode="browse")
        for col,width in (("type",145),("message",360),("id",230)):
            self.inbox_table.heading(col,text=col.title())
            self.inbox_table.column(col,width=width,stretch=(col=="message"))
        inbox_scroll=ttk.Scrollbar(body,orient="vertical",command=self.inbox_table.yview)
        self.inbox_table.configure(yscrollcommand=inbox_scroll.set)
        self.inbox_table.pack(side="left",fill="both",expand=True)
        inbox_scroll.pack(side="right",fill="y")
        self.inbox_selected=tk.StringVar(value="Select a notification for details.")
        ttk.Label(tab,textvariable=self.inbox_selected,wraplength=780,
                  style="PageSubtitle.TLabel").pack(anchor="w",pady=(8,5))
        self.inbox_table.bind("<<TreeviewSelect>>",lambda _:self._selected_inbox_detail())
        actions=ttk.Frame(tab);actions.pack(fill="x",pady=(4,0))
        ttk.Button(actions,text="Mark Selected Seen",command=self.seen
                   ).pack(side="left",padx=(0,9))
        ttk.Button(actions,text="Dismiss Selected",command=self.dismiss).pack(side="left")

    def load_inbox(self):
        account=self.account.user_id if self.account.signed_in else None
        def work():
            self._eligible()
            if self.account.user_id!=account:
                raise PermissionError("Personal VRChat account changed during inbox load")
            return self.account.browse("inbox")
        def done(rows):
            if not self.account.signed_in or self.account.user_id!=account:
                self.clear_inbox()
                return
            self._inbox=list(rows)
            self._inbox_account=account
            types=sorted({str(row.get("type") or "Other")[:70] for row in rows
                          if isinstance(row,dict)})
            self.inbox_type_picker.configure(values=("All types",*types))
            if self.inbox_type.get() not in ("All types",*types):
                self.inbox_type.set("All types")
            self.render_inbox()
        self.owner._run_async(work,done)

    def clear_inbox(self):
        self._inbox=[]
        self._inbox_account=None
        self._inbox_rows={}
        self.inbox_table.delete(*self.inbox_table.get_children())
        self.inbox_type_picker.configure(values=("All types",))
        self.inbox_type.set("All types")
        self.inbox_selected.set("Select a notification for details.")
        self.inbox_status.set("No notifications loaded for this account.")

    def render_inbox(self):
        self.inbox_table.delete(*self.inbox_table.get_children())
        self._inbox_rows={}
        self.inbox_selected.set("Select a notification for details.")
        if not self.account.signed_in or self.account.user_id!=self._inbox_account:
            self.inbox_status.set("Sign in and refresh to load your personal inbox.")
            return
        query=self.inbox_query.get().strip().casefold()[:120]
        selected_type=self.inbox_type.get()
        for row in self._inbox:
            if not isinstance(row,dict):continue
            kind=str(row.get("type") or "Other")[:70]
            name=str(row.get("senderUsername") or row.get("senderUserId") or "")[:120]
            message=str(row.get("message") or name)[:500]
            ident=str(row.get("id") or "")[:110]
            if selected_type!="All types" and kind!=selected_type:continue
            if query and not any(query in value.casefold() for value in (kind,name,message,ident)):
                continue
            iid=self.inbox_table.insert("",tk.END,values=(kind,message[:170],ident))
            self._inbox_rows[iid]=row
        self.inbox_status.set(f"{len(self._inbox_rows)} shown / {len(self._inbox)} "
                              "personal VRChat inbox records (manual refresh).")

    def _selected_inbox_detail(self):
        selected=self.inbox_table.selection()
        row=self._inbox_rows.get(selected[0]) if selected else None
        if not row:
            self.inbox_selected.set("Select a notification for details.");return
        sender=str(row.get("senderUsername") or "Unknown sender")[:100]
        msg=str(row.get("message") or "No message provided")[:360]
        self.inbox_selected.set(f"From: {sender} • {msg}")

    def clear_private_views(self):
        """Clear personal result rows on VRChat sign-out without deleting data."""
        self._avatars=[]
        self._avatar_account=None
        self.avatar_table.delete(*self.avatar_table.get_children())
        self.avatar_title_var.set("Select an avatar, then choose View Selected Avatar")
        self.avatar_meta_var.set("")
        self.avatar_detail.config(state="normal")
        self.avatar_detail.delete("1.0",tk.END)
        self.avatar_detail.config(state="disabled")
        self.avatar_status.set("Sign in to your personal VRChat account")
        self.bookmark_table.delete(*self.bookmark_table.get_children())
        self.bookmark_count.set("Sign in to view your saved worlds")
        self.clear_inbox()

    def _notification_id(self):
        if not self.account.signed_in or self.account.user_id!=self._inbox_account:
            raise PermissionError("Refresh inbox for your current VRChat account")
        selected=self.inbox_table.selection()
        if not selected or selected[0] not in self._inbox_rows:
            raise ValueError("Select a notification first")
        return str(self._inbox_rows[selected[0]].get("id") or "")

    def seen(self):
        try:self._eligible();identifier=self._notification_id()
        except Exception as exc:self._error(exc);return
        self.owner._run_async(lambda:self.account.notification_seen(identifier),
                              lambda _:self.load_inbox())

    def dismiss(self):
        try:self._eligible();identifier=self._notification_id()
        except Exception as exc:self._error(exc);return
        if not messagebox.askyesno("Dismiss VRChat Notification",
                "Remove this selected notification from your personal VRChat inbox?"):return
        self.owner._run_async(lambda:self.account.dismiss_notification(identifier),
                              lambda _:self.load_inbox())
