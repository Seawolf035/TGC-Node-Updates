"""Desktop Friend Hub: account-scoped pins, ordering and shared instance groups.

Manual refresh only. No polling, automatic friend requests, invite requests,
remote Relay calls, or tracking uploads. Mobile FriendPinStore/GroupToolkitStore
and FriendOrdering semantics are preserved.
"""
from __future__ import annotations
import tkinter as tk
from tkinter import ttk
import webbrowser

from .desktop_social_shortcuts import SocialShortcuts, GROUP_ID
from .desktop_friend_ordering import group_friends,user_id,display_name
from .desktop_friend_activity import FriendActivityStore
from .vrchat_personal import USER_ID

MAX_PAGES=167


def complete_friend_list(account):
    """Both lists must finish before the UI adopts a fresh snapshot."""
    found={}
    for category in ("online","offline"):
        done=False
        for step in range(MAX_PAGES):
            batch=account.browse(category,offset=step*60)
            for person in batch:
                ident=person.get("id")
                if isinstance(ident,str) and USER_ID.fullmatch(ident):
                    found[ident]=person
            if len(batch)<60:
                done=True
                break
        if not done:
            raise ValueError("Friend list exceeded safe page limit; nothing was replaced")
    return tuple(found.values())


def group_shortcuts(rows):
    """Different VRChat group shapes all use the canonical grp_ identifier."""
    result=[]
    found=set()
    for row in rows:
        if not isinstance(row,dict):continue
        item=row.get("group") if isinstance(row.get("group"),dict) else row
        ident=item.get("id") or row.get("groupId")
        if not isinstance(ident,str) or not GROUP_ID.fullmatch(ident) or ident in found:
            continue
        found.add(ident)
        result.append({"id":ident,"name":str(item.get("name") or row.get("name") or ident)[:90]})
    return result


class FriendHubPage:
    def __init__(self,pages,book):
        self.pages=pages
        self.owner=pages.owner
        self.shortcuts=SocialShortcuts(self.owner.terms_dir)
        self.history=FriendActivityStore(self.owner.terms_dir)
        self.friends=()
        self.groups=()
        self.friend_rows={}
        self.group_rows={}
        self.status=tk.StringVar(value="Sign in to VRChat, then Load Friends. No background tracking.")
        self.search=tk.StringVar()
        panel=ttk.Frame(book,padding=(13,10))
        book.add(panel,text="Friends Hub")
        ttk.Label(panel,text="FRIENDS HUB",style="PageTitle.TLabel").pack(anchor="w")
        ttk.Label(panel,text="Mobile Companion-style pinned friends, shared instance grouping "
                  "and pinned groups. All actions are local shortcuts; VRChat friendships "
                  "and groups are never modified.",wraplength=810).pack(anchor="w",pady=(2,8))
        toolbar=ttk.Frame(panel);toolbar.pack(fill="x",pady=(0,5))
        ttk.Button(toolbar,text="Load All Friends",style="Accent.TButton",
                   command=self.load_friends).pack(side="left",padx=(0,8))
        ttk.Button(toolbar,text="Load My Groups",command=self.load_groups).pack(side="left",padx=(0,8))
        ttk.Button(toolbar,text="Refresh Local Pins",command=self.refresh).pack(side="left",padx=(0,10))
        ttk.Label(toolbar,text="Filter:").pack(side="left")
        ttk.Entry(toolbar,textvariable=self.search,width=19).pack(
            side="left",fill="x",expand=True,padx=(4,0))
        self.search.trace_add("write",lambda *_:self.refresh())
        ttk.Label(panel,textvariable=self.status,wraplength=830).pack(anchor="w",pady=(2,5))
        columns=ttk.Notebook(panel)
        columns.pack(fill="both",expand=True)
        friends=ttk.Frame(columns,padding=(2,6))
        groups=ttk.Frame(columns,padding=(2,6))
        activity=ttk.Frame(columns,padding=(2,6))
        columns.add(friends,text="Friends & Pins")
        columns.add(groups,text="Pinned Groups")
        columns.add(activity,text="Friend Activity")
        ttk.Label(friends,text="Pinned friends first; remaining friends grouped by VRChat "
                  "favorites, shared instance, solo location, hidden, then offline.",
                  wraplength=820).pack(anchor="w",pady=(0,5))
        self.friend_tree=ttk.Treeview(friends,columns=("location","id"),
                                     show="tree headings",height=12)
        self.friend_tree.heading("#0",text="Friend / section")
        self.friend_tree.heading("location",text="Location / status")
        self.friend_tree.heading("id",text="VRChat user ID")
        self.friend_tree.column("#0",width=220,stretch=True)
        self.friend_tree.column("location",width=215)
        self.friend_tree.column("id",width=310)
        self.friend_tree.pack(fill="both",expand=True)
        actions=ttk.Frame(friends);actions.pack(fill="x",pady=(8,0))
        for label,method in (
            ("Pin / Unpin Friend",self.toggle_friend),
            ("Move Pin ↑",lambda:self.move(-1)),
            ("Move Pin ↓",lambda:self.move(1)),
            ("Open Profile",self.open_friend),
        ):
            ttk.Button(actions,text=label,command=method).pack(side="left",padx=(0,6))
        ttk.Label(groups,text="Pinned groups (up to 16). These are only local shortcuts.",
                  wraplength=820).pack(anchor="w",pady=(0,5))
        self.group_tree=ttk.Treeview(groups,columns=("id",),show="tree headings",height=13)
        self.group_tree.heading("#0",text="Group")
        self.group_tree.heading("id",text="VRChat group ID")
        self.group_tree.column("#0",width=290,stretch=True)
        self.group_tree.column("id",width=420)
        self.group_tree.pack(fill="both",expand=True)
        gactions=ttk.Frame(groups);gactions.pack(anchor="w",pady=(8,0))
        ttk.Button(gactions,text="Pin / Unpin Group",command=self.toggle_group).pack(
            side="left",padx=(0,8))
        ttk.Button(gactions,text="Open Group",command=self.open_group).pack(side="left")
        ttk.Label(activity,text="Manual, private history of changes to your own "
            "VRChat friend list. First capture creates a baseline, not invented history. "
            "Nothing is uploaded or checked in the background.",wraplength=820).pack(
            anchor="w",pady=(0,8))
        bar=ttk.Frame(activity);bar.pack(fill="x",pady=(0,7))
        ttk.Button(bar,text="Capture Friend Changes",style="Accent.TButton",
                   command=self.capture_history).pack(side="left",padx=(0,8))
        ttk.Button(bar,text="View Saved History",command=self.load_history
                   ).pack(side="left")
        self.activity_table=ttk.Treeview(activity,
            columns=("at","name","kind","previous"),show="headings",height=12)
        for key,label,width in (
            ("at","Time",155),("name","Friend",180),("kind","Change",165),
            ("previous","Previous value",300)):
            self.activity_table.heading(key,text=label)
            self.activity_table.column(key,width=width,stretch=(key=="previous"))
        self.activity_table.pack(fill="both",expand=True)
        self.friend_tree.bind("<Double-1>",lambda e:self._friend_double(e))
        self.group_tree.bind("<Double-1>",lambda e:self.open_group())
        self.refresh()

    def _personal(self,require_staff=False):
        self.pages._eligible(personal=True)
        if require_staff:
            # Mobile FriendPinStore requires verified staff. GroupToolkitStore
            # requires only personal login; do not silently escalate either.
            self.pages._eligible()
        return self.pages.account.user_id

    def _filter(self,name,ident):
        term=self.search.get().strip().casefold()
        return not term or term in name.casefold() or term in ident.casefold()

    def refresh(self):
        self.friend_tree.delete(*self.friend_tree.get_children())
        self.group_tree.delete(*self.group_tree.get_children())
        self.friend_rows={}
        self.group_rows={}
        if not self.pages.account.signed_in:
            self.status.set("Sign in to personal VRChat to use account-specific shortcuts.")
            return
        account=self.pages.account.user_id
        friendpins=self.shortcuts.list(account,"friends")
        pinned={row["id"] for row in friendpins}
        if friendpins:
            parent=self.friend_tree.insert("",tk.END,text=f"★ PINNED · {len(friendpins)}",open=True)
            for row in friendpins:
                self._friend_row(parent,{"id":row["id"],"displayName":row["name"],
                                  "location":"Pinned shortcut"},"★ ")
        grouped=group_friends((p for p in self.friends if user_id(p) not in pinned),
                              set())
        for key,label,members in grouped:
            filtered=[p for p in members if self._filter(display_name(p),user_id(p))]
            if not filtered:continue
            parent=self.friend_tree.insert("",tk.END,text=f"{label} · {len(filtered)}",open=True)
            for person in filtered:
                self._friend_row(parent,person)
        group_pins=self.shortcuts.list(account,"groups")
        pins={row["id"] for row in group_pins}
        if group_pins:
            parent=self.group_tree.insert("",tk.END,text=f"★ PINNED · {len(group_pins)}",open=True)
            for row in group_pins:self._group_row(parent,row,"★ ")
        others=[row for row in self.groups if row["id"] not in pins and
                self._filter(row["name"],row["id"])]
        if others:
            parent=self.group_tree.insert("",tk.END,text=f"MY GROUPS · {len(others)}",open=True)
            for row in others:self._group_row(parent,row)
        self.status.set(f"{len(self.friends)} loaded friends • {len(friendpins)}/12 pinned "
            f"• {len(self.groups)} loaded groups • {len(group_pins)}/16 pinned. "
            "Use Load to refresh VRChat; no background requests.")

    def _friend_row(self,parent,person,prefix=""):
        uid=user_id(person); name=display_name(person)
        if not USER_ID.fullmatch(uid) or not self._filter(name,uid):return
        loc=str(person.get("location") or person.get("status") or "Unknown")[:90]
        iid=self.friend_tree.insert(parent,tk.END,text=prefix+name,
                                    values=(loc,uid))
        self.friend_rows[iid]=(uid,name)

    def _group_row(self,parent,row,prefix=""):
        if not self._filter(row["name"],row["id"]):return
        iid=self.group_tree.insert(parent,tk.END,text=prefix+row["name"],
                                   values=(row["id"],))
        self.group_rows[iid]=row

    def load_friends(self):
        self.status.set("Loading complete online and offline VRChat friend lists…")
        def work():
            self._personal()
            return complete_friend_list(self.pages.account)
        self.owner._run_async(work,self._loaded_friends,
            on_error=lambda e:self.status.set("Friends load failed; prior list kept: "+e))

    def _loaded_friends(self,rows):
        self.friends=rows
        self.refresh()

    def load_groups(self):
        self.status.set("Loading your personal VRChat groups…")
        def work():
            self._personal()
            return group_shortcuts(self.pages.account.browse("groups"))
        self.owner._run_async(work,self._loaded_groups,
            on_error=lambda e:self.status.set("Groups load failed; prior list kept: "+e))

    def _loaded_groups(self,rows):
        self.groups=tuple(rows)
        self.refresh()

    def capture_history(self):
        self.status.set("Capturing full friend snapshot; no partial history will be saved…")
        def work():
            user=self._personal()
            complete=complete_friend_list(self.pages.account)
            changes=self.history.observe(user,complete)
            return complete,changes
        def done(result):
            self.friends,changes=result
            self.refresh()
            self.load_history()
            self.status.set(f"Complete friend snapshot captured. {changes} new changes "
                "recorded locally (first capture creates a baseline).")
        self.owner._run_async(work,done,on_error=lambda error:self.status.set(
            "Friend history was not changed: "+error))

    def load_history(self):
        try:
            user=self._personal()
            events=self.history.events(user)
            from datetime import datetime
            self.activity_table.delete(*self.activity_table.get_children())
            for event in reversed(events):
                try:
                    stamp=datetime.fromtimestamp(int(event.get("time",0))).strftime(
                        "%Y-%m-%d %H:%M")
                except (ValueError,OverflowError,OSError):
                    stamp="Unknown"
                self.activity_table.insert("",tk.END,values=(
                    stamp,str(event.get("name",""))[:90],
                    str(event.get("type",""))[:50],
                    str(event.get("previous",""))[:160]))
            self.status.set(f"{len(events)} locally encrypted friend history events. "
                "No background tracking or Relay uploads.")
        except Exception as error:
            self.status.set("Cannot display friend history: "+str(error))

    def _selected_friend(self):
        selected=self.friend_tree.selection()
        if not selected or selected[0] not in self.friend_rows:
            raise ValueError("Choose a friend row, not a category header")
        return self.friend_rows[selected[0]]

    def _selected_group(self):
        selected=self.group_tree.selection()
        if not selected or selected[0] not in self.group_rows:
            raise ValueError("Choose a group row, not a category header")
        return self.group_rows[selected[0]]

    def toggle_friend(self):
        try:
            account=self._personal(require_staff=True)
            uid,name=self._selected_friend()
            current={r["id"] for r in self.shortcuts.list(account,"friends")}
            self.shortcuts.set_pinned(account,"friends",uid,name,uid not in current)
            self.refresh()
        except Exception as e:self.status.set(str(e))

    def move(self,direction):
        try:
            account=self._personal(require_staff=True)
            uid,_name=self._selected_friend()
            self.shortcuts.move_friend(account,uid,direction)
            self.refresh()
        except Exception as e:self.status.set(str(e))

    def toggle_group(self):
        try:
            account=self._personal()
            row=self._selected_group()
            current={r["id"] for r in self.shortcuts.list(account,"groups")}
            self.shortcuts.set_pinned(account,"groups",row["id"],row["name"],
                                      row["id"] not in current)
            self.refresh()
        except Exception as e:self.status.set(str(e))

    def open_friend(self):
        try:
            self._personal()
            uid,_name=self._selected_friend()
            self.pages.profile_tools.set_selected(uid)
        except Exception as e:self.status.set(str(e))

    def _friend_double(self,event):
        if self.friend_tree.identify_row(event.y) in self.friend_rows:
            self.open_friend()

    def open_group(self):
        try:
            self._personal()
            row=self._selected_group()
            self.pages.profile_tools.set_selected(row["id"])
        except Exception as e:self.status.set(str(e))
