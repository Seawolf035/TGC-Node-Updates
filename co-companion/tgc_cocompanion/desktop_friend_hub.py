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
from .desktop_friend_favorites import FavoriteFriendIndex
from .desktop_pinned_friend_alerts import PinnedFriendAlerts, MIN_INTERVAL_SECONDS
from .desktop_windows_notifications import WindowsFriendNotifier
import time
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
        self.favorite_index=FavoriteFriendIndex(self.owner.terms_dir)
        self.alerts=PinnedFriendAlerts(self.owner.terms_dir)
        self.native_notifier=WindowsFriendNotifier(self.owner.root)
        self.windows_alerts=tk.BooleanVar(value=False)
        self.favorite_ids=frozenset()
        self._account_loaded=None
        self._alert_polling=False
        self.friends=()
        self.groups=()
        self.friend_rows={}
        self.group_rows={}
        self._history_events=()
        self._history_account=None
        self._history_ids={}
        self.status=tk.StringVar(value="Sign in to VRChat, then Load Friends. No background tracking.")
        self.search=tk.StringVar()
        panel=ttk.Frame(book,padding=(13,10))
        book.add(panel,text="Friends Hub")
        ttk.Label(panel,text="FRIENDS HUB",style="PageTitle.TLabel").pack(anchor="w")
        ttk.Label(panel,text="Mobile Companion-style pinned friends, shared instance grouping "
                  "and pinned groups. All actions are local shortcuts; VRChat friendships "
                  "and groups are never modified.",wraplength=810).pack(anchor="w",pady=(2,8))
        # Avoid one oversized row of actions on 760px / compact windows.
        toolbar=ttk.Frame(panel);toolbar.pack(fill="x",pady=(0,5))
        ttk.Button(toolbar,text="Load All Friends",style="Accent.TButton",
                   command=self.load_friends).pack(side="left",padx=(0,8))
        ttk.Button(toolbar,text="Load My Groups",command=self.load_groups
                   ).pack(side="left",padx=(0,8))
        ttk.Button(toolbar,text="Load VRC Favorites",command=self.load_favorites
                   ).pack(side="left")
        filters=ttk.Frame(panel);filters.pack(fill="x",pady=(0,7))
        ttk.Button(filters,text="Refresh Pins",command=self.refresh
                   ).pack(side="left",padx=(0,10))
        ttk.Label(filters,text="Find:").pack(side="left")
        self.search=tk.StringVar()
        ttk.Entry(filters,textvariable=self.search).pack(side="left",fill="x",expand=True,padx=(6,0))
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
        alerttab=ttk.Frame(columns,padding=(3,7))
        columns.add(alerttab,text="Pinned Alerts")
        ttk.Label(alerttab,text="Optional in-app notifications when your PINNED VRChat "
                  "friends change from offline to online. Checks every 15 minutes while "
                  "this Co-Companion window is running. No system service, Relay calls, "
                  "tracking Node or automatic friend requests.",wraplength=780).pack(
                      anchor="w",pady=(0,8))
        self.alert_status=tk.StringVar(value="Alerts off by default. First check is silent.")
        ttk.Label(alerttab,textvariable=self.alert_status,wraplength=770).pack(
                      anchor="w",pady=(0,9))
        row=ttk.Frame(alerttab);row.pack(anchor="w",pady=(0,9))
        ttk.Button(row,text="Enable Pinned Alerts",style="Accent.TButton",
                   command=self.enable_alerts).pack(side="left",padx=(0,8))
        ttk.Button(row,text="Disable Alerts",command=self.disable_alerts).pack(side="left",padx=(0,8))
        ttk.Button(row,text="Check Now",command=lambda:self.check_alerts(force=True)
                   ).pack(side="left",padx=(0,9))
        ttk.Checkbutton(alerttab,text="Also show Windows notifications (optional)",
            variable=self.windows_alerts,command=self._toggle_windows_notifications
            ).pack(anchor="w",pady=(2,5))
        ttk.Button(alerttab,text="Test Windows Notification",
                   command=self.test_windows_notification).pack(anchor="w",pady=(1,6))
        ttk.Label(alerttab,text="Windows may suppress system alerts under Do Not Disturb. "
                  "The in-app feed remains available either way.",
                  wraplength=760).pack(anchor="w",pady=(0,5))
        ttk.Label(alerttab,text="Notifications from this session (not stored):").pack(anchor="w")
        self.alert_feed=tk.Listbox(alerttab,height=11)
        self.alert_feed.pack(fill="both",expand=True,pady=(5,0))
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
        from .desktop_friend_activity import ACTIVITY_KINDS
        filters=ttk.Frame(activity);filters.pack(fill="x",pady=(0,7))
        ttk.Label(filters,text="Search history").pack(side="left",padx=(0,7))
        self.activity_query=tk.StringVar()
        ttk.Entry(filters,textvariable=self.activity_query).pack(
            side="left",fill="x",expand=True,padx=(0,10))
        self.activity_kind=tk.StringVar(value="All events")
        ttk.Combobox(filters,textvariable=self.activity_kind,state="readonly",
            values=ACTIVITY_KINDS,width=24).pack(side="left")
        self.activity_query.trace_add("write",lambda *_:self.render_history())
        self.activity_kind.trace_add("write",lambda *_:self.render_history())
        self.activity_count=tk.StringVar(value="Capture a complete friend snapshot to begin.")
        ttk.Label(activity,textvariable=self.activity_count,
                  style="PageSubtitle.TLabel").pack(anchor="w",pady=(0,6))
        table_frame=ttk.Frame(activity);table_frame.pack(fill="both",expand=True)
        self.activity_table=ttk.Treeview(table_frame,
            columns=("at","name","kind","previous"),show="headings",height=12)
        for key,label,width in (
            ("at","Time",155),("name","Friend",180),("kind","Change",165),
            ("previous","Previous value",300)):
            self.activity_table.heading(key,text=label)
            self.activity_table.column(key,width=width,stretch=(key=="previous"))
        activity_scroll=ttk.Scrollbar(table_frame,orient="vertical",
                                      command=self.activity_table.yview)
        self.activity_table.configure(yscrollcommand=activity_scroll.set)
        self.activity_table.pack(side="left",fill="both",expand=True)
        activity_scroll.pack(side="right",fill="y")
        ttk.Button(activity,text="Open Selected Friend Profile",
            command=self.open_history_profile).pack(anchor="w",pady=(7,2))
        self.activity_table.bind("<Double-1>",lambda _:self.open_history_profile())
        self.friend_tree.bind("<Double-1>",lambda e:self._friend_double(e))
        self.group_tree.bind("<Double-1>",lambda e:self.open_group())
        self.refresh()
        self.owner.root.after(60_000,self._alert_tick)

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
        account=self.pages.account.user_id if self.pages.account.signed_in else None
        if self._account_loaded!=account:
            # Never show stale people from a different personal VRChat account.
            self._account_loaded=account
            self.friends=()
            self.groups=()
            self._history_events=()
            self._history_account=None
            self._history_ids={}
            self.activity_table.delete(*self.activity_table.get_children())
            self.activity_count.set("No history loaded for this VRChat account.")
            self.favorite_ids=self.favorite_index.saved(account) if account else frozenset()
            self.alert_feed.delete(0,tk.END)
            try:
                self.windows_alerts.set(self.alerts.windows_notifications(account)
                                        if account else False)
            except ValueError:
                self.windows_alerts.set(False)
            self.alert_status.set("Pinned alerts are off unless explicitly enabled "
                                  "for the signed-in personal VRChat account.")
        if not account:
            self.status.set("Sign in to personal VRChat to use account-specific shortcuts.")
            self.alert_status.set("Sign in to personal VRChat first.")
            return
        friendpins=self.shortcuts.list(account,"friends")
        pinned={row["id"] for row in friendpins}
        if friendpins:
            parent=self.friend_tree.insert("",tk.END,text=f"★ PINNED · {len(friendpins)}",open=True)
            for row in friendpins:
                self._friend_row(parent,{"id":row["id"],"displayName":row["name"],
                                  "location":"Pinned shortcut"},"★ ")
        grouped=group_friends((p for p in self.friends if user_id(p) not in pinned),
                              self.favorite_ids)
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
            f"{len(self.favorite_ids)} actual VRChat favorite-friend IDs. "
            "Manual refresh; alerts only if explicitly enabled.")

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
        try: user=self._personal()
        except Exception as e:self.status.set(str(e));return
        def work():
            rows=complete_friend_list(self.pages.account)
            if not self.pages.account.signed_in or self.pages.account.user_id!=user:
                raise PermissionError("VRChat account changed while loading friends")
            return rows
        self.owner._run_async(work,
            lambda rows:self._loaded_friends(rows) if self.pages.account.signed_in
            and self.pages.account.user_id==user else None,
            on_error=lambda e:self.status.set("Friends load failed; prior list kept: "+e))

    def _loaded_friends(self,rows):
        self.friends=rows
        self.refresh()

    def load_favorites(self):
        self.status.set("Checking read-only VRChat favorite-friend groups…")
        try: user=self._personal()
        except Exception as e:self.status.set(str(e));return
        def work():
            return self.favorite_index.refresh(self.pages.account)
        def done(result):
            if not self.pages.account.signed_in or self.pages.account.user_id!=user:
                return
            self.favorite_ids,came_from_api=result
            self.refresh()
            self.status.set(f"{len(self.favorite_ids)} real VRChat favorite-friend IDs "
                + ("loaded from VRChat." if came_from_api else "loaded from 15-minute cache."))
        self.owner._run_async(work,done,on_error=lambda error:self.status.set(
            "Favorite group refresh failed; previous cache preserved: "+error))

    def _toggle_windows_notifications(self):
        checked=self.windows_alerts.get()
        try:
            account=self._personal(require_staff=True)
            self.alerts.set_windows_notifications(account,checked)
            self.alert_status.set("Windows notifications "+("enabled" if checked
                    else "disabled")+". Your in-app feed still works.")
        except Exception as error:
            self.windows_alerts.set(not checked)
            self.alert_status.set("Could not change Windows notification preference: "+
                                  str(error))

    def test_windows_notification(self):
        try:
            account=self._personal(require_staff=True)
            if not self.alerts.enabled(account):
                raise PermissionError("Enable pinned alerts first")
            if not self.alerts.windows_notifications(account):
                raise PermissionError("Enable Windows notifications first")
            if self.native_notifier.show_test():
                self.alert_status.set("Windows notification test sent. Check your "
                    "notification settings if it did not appear.")
            else:
                self.alert_status.set("Windows system notifications are unavailable. "
                    "In-app pinned alerts will continue working.")
        except Exception as error:
            self.alert_status.set(str(error))

    def close(self):
        self._alert_polling=False
        self.native_notifier.close()

    def enable_alerts(self):
        try:
            account=self._personal(require_staff=True)
            self.alerts.set_enabled(account,True)
            self.alert_status.set("Enabled. Initial observation is silent; no notifications "
                                  "for friends who are already online.")
            self.check_alerts(force=True)
        except Exception as error:self.alert_status.set(str(error))

    def disable_alerts(self):
        try:
            account=self._personal()
            self.alerts.set_enabled(account,False)
            self.alert_status.set("Alerts disabled for this VRChat account.")
        except Exception as error:self.alert_status.set(str(error))

    def _alert_tick(self):
        if not self.owner.root.winfo_exists():
            return
        try:
            self.check_alerts(force=False)
        finally:
            self.owner.root.after(60_000,self._alert_tick)

    def check_alerts(self,force=False):
        if self._alert_polling or self.owner._busy or not self.pages.account.signed_in:
            return
        try:
            account=self._personal(require_staff=True)
            if not self.alerts.enabled(account):
                return
            pins=self.shortcuts.list(account,"friends")
            if not pins:
                self.alert_status.set("Pin at least one friend before checking alerts.")
                return
            elapsed=time.time()-self.alerts.last_check(account)
            if not force and elapsed<MIN_INTERVAL_SECONDS:
                return
        except Exception as error:
            self.alert_status.set("Alert check paused: "+str(error))
            return
        self._alert_polling=True
        self.alert_status.set("Checking complete personal friend list for pinned alerts…")
        def work():
            # Validate current account and staff at request start, and never
            # treat partial online/offline friend pages as a complete snapshot.
            if self._personal(require_staff=True)!=account:
                raise PermissionError("Personal VRChat account changed")
            complete=complete_friend_list(self.pages.account)
            if self.pages.account.user_id!=account:
                raise PermissionError("Personal VRChat account changed during check")
            notices=self.alerts.observe(account,pins,complete)
            return complete,notices
        def done(result):
            self._alert_polling=False
            if self.pages.account.user_id!=account or not self.pages.account.signed_in:
                return
            try:
                self._personal(require_staff=True)
                if not self.alerts.enabled(account):
                    return
            except Exception:
                self.alert_status.set("Alert verification expired; notifications suppressed.")
                return
            friends,notices=result
            self.friends=friends
            self.refresh()
            if notices:
                from datetime import datetime
                now=datetime.now().strftime("%H:%M")
                for notice in notices:
                    self.alert_feed.insert(0,f"{now} • {notice['name']} is online")
                while self.alert_feed.size()>25:
                    self.alert_feed.delete(tk.END)
                native=0
                if self.windows_alerts.get() and self.alerts.windows_notifications(account):
                    for notice in notices[:5]:
                        if self.native_notifier.show(notice["name"]):
                            native+=1
                if native==0:
                    try:
                        import winsound
                        winsound.MessageBeep()
                    except (ImportError,RuntimeError,OSError):
                        pass
                self.alert_status.set(f"{len(notices)} pinned friend(s) came online." +
                    (" Windows notification sent." if native else
                     " In-app alert only; Windows notifications may be unavailable."))
            else:
                self.alert_status.set("Checked pinned friends. No new online transitions.")
        def failed(error):
            self._alert_polling=False
            self.alert_status.set("Pinned alerts check failed; previous states kept: "+error)
        if not self.owner._run_async(work,done,on_error=failed):
            self._alert_polling=False
            self.alert_status.set("App is busy; retry pinned alert check shortly.")

    def load_groups(self):
        self.status.set("Loading your personal VRChat groups…")
        try: user=self._personal()
        except Exception as e:self.status.set(str(e));return
        def work():
            rows=group_shortcuts(self.pages.account.browse("groups"))
            if not self.pages.account.signed_in or self.pages.account.user_id!=user:
                raise PermissionError("VRChat account changed during group refresh")
            return rows
        self.owner._run_async(work,
            lambda rows:self._loaded_groups(rows) if self.pages.account.signed_in
            and self.pages.account.user_id==user else None,
            on_error=lambda e:self.status.set("Groups load failed; prior list kept: "+e))

    def _loaded_groups(self,rows):
        self.groups=tuple(rows)
        self.refresh()

    def capture_history(self):
        self.status.set("Capturing full friend snapshot; no partial history will be saved…")
        try: user=self._personal()
        except Exception as e:self.status.set(str(e));return
        def work():
            complete=complete_friend_list(self.pages.account)
            if not self.pages.account.signed_in or self.pages.account.user_id!=user:
                raise PermissionError("VRChat account changed during history capture")
            changes=self.history.observe(user,complete)
            return complete,changes
        def done(result):
            if not self.pages.account.signed_in or self.pages.account.user_id!=user:
                return
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
            self._history_account=user
            self._history_events=tuple(events)
            self.render_history()
            self.status.set(f"{len(events)} locally encrypted friend history events. "
                "No background tracking or Relay uploads.")
        except Exception as error:
            self.status.set("Cannot display friend history: "+str(error))

    def render_history(self):
        """Presentation-only filter over existing encrypted, account-bound events."""
        from .desktop_friend_activity import filter_activity_events
        account=(self.pages.account.user_id if self.pages.account.signed_in else None)
        self.activity_table.delete(*self.activity_table.get_children())
        self._history_ids={}
        if not account or account!=self._history_account:
            self.activity_count.set("No history loaded for this VRChat account.")
            return
        try:
            matches=filter_activity_events(self._history_events,
                 self.activity_query.get(),self.activity_kind.get())
        except ValueError as error:
            self.activity_count.set(str(error));return
        from datetime import datetime
        for event in matches:
            try:
                stamp=datetime.fromtimestamp(int(event.get("time",0))).strftime(
                    "%Y-%m-%d %H:%M")
            except (ValueError,TypeError,OverflowError,OSError):
                stamp="Unknown"
            iid=self.activity_table.insert("",tk.END,values=(
                stamp,str(event.get("name",""))[:90],
                str(event.get("type",""))[:50],
                str(event.get("previous",""))[:160]))
            ident=str(event.get("userId") or "")
            if USER_ID.fullmatch(ident):
                self._history_ids[iid]=ident
        self.activity_count.set(f"{len(matches)} shown / {len(self._history_events)} stored events"
            " • Manual snapshots only • Local to this Windows account")

    def open_history_profile(self):
        try:
            account=self._personal()
            if account!=self._history_account:
                raise PermissionError("Load history for your current VRChat account first")
            selected=self.activity_table.selection()
            ident=self._history_ids.get(selected[0]) if selected else None
            if not ident or not USER_ID.fullmatch(ident):
                raise ValueError("Select a history entry with a valid friend ID")
            self.pages.profile_tools.set_selected(ident)
        except Exception as error:
            self.status.set("Cannot open history profile: "+str(error))

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
