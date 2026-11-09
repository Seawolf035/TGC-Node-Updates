"""Personal VRChat Explore: discover people/worlds in one search.

Only uses VRChat's existing personal-account API. No Relay/Node calls, no
automated friend requests, no invites, no group moderation, no saved searches.
"""
from __future__ import annotations

import re
import tkinter as tk
from tkinter import ttk
import webbrowser

from .vrchat_personal import USER_ID

WORLD_ID = re.compile(r"wrld_[0-9a-fA-F-]{36}\Z")
GROUP_ID = re.compile(r"grp_[0-9a-fA-F-]{36}\Z")
QUICK = {
    "Recent Worlds": "recent",
    "Favorite Worlds": "worlds",
    "Online Friends": "online",
    "My Groups": "groups",
}


def classify_result(row, kind):
    if not isinstance(row, dict):
        return None
    identifier=row.get("id")
    if not isinstance(identifier, str):
        return None
    if USER_ID.fullmatch(identifier):
        category="Person"
    elif WORLD_ID.fullmatch(identifier):
        category="World"
    elif GROUP_ID.fullmatch(identifier):
        category="Group"
    else:
        # Some group membership API rows nest their identifiers.
        nested=row.get("group")
        if isinstance(nested, dict) and GROUP_ID.fullmatch(str(nested.get("id",""))):
            identifier=nested["id"]
            row={**nested,**row}
            category="Group"
        else:
            return None
    label=str(row.get("displayName") or row.get("name") or row.get("worldName")
              or identifier).strip()[:96]
    detail=str(row.get("status") or row.get("description")
               or row.get("location") or row.get("memberCount") or "")[:140]
    return (category,label,detail,identifier)


def merge_explore_results(items):
    """Deduplicate by VRChat ID, prefer one row per person/world."""
    distinct={}
    for category, rows in items:
        for row in rows:
            normalized=classify_result(row,category)
            if normalized and normalized[3] not in distinct:
                distinct[normalized[3]]=normalized
    return list(distinct.values())


class ExplorePage:
    def __init__(self, pages, notebook):
        self.pages=pages
        self.owner=pages.owner
        self.query=tk.StringVar()
        self.status=tk.StringVar(value="Sign in to your personal VRChat account to explore.")
        self.results={}
        self._all_results=()
        self._result_account=None
        self.result_filter=tk.StringVar()
        self.type_filter=tk.StringVar(value="All types")
        tab=ttk.Frame(notebook,padding=(16,12))
        notebook.add(tab,text="Explore")
        ttk.Label(tab,text="EXPLORE VRCHAT",style="PageTitle.TLabel").pack(anchor="w")
        ttk.Label(tab,text="Search people and worlds together, browse your recent worlds "
                  "and favorites, and open results in Profiles or Bookmarks. "
                  "This page uses your personal VRChat session only.",
                  wraplength=790).pack(anchor="w",pady=(3,12))
        search_card=ttk.LabelFrame(tab,text="Discover on VRChat",padding=(12,9))
        search_card.pack(fill="x",pady=(0,8))
        search=ttk.Frame(search_card);search.pack(fill="x")
        ttk.Label(search,text="Find").pack(side="left",padx=(0,8))
        entry=ttk.Entry(search,textvariable=self.query)
        entry.pack(side="left",fill="x",expand=True)
        entry.bind("<Return>",lambda _event:self.search_all())
        ttk.Button(search_card,text="Search People + Worlds",style="Accent.TButton",
                   command=self.search_all).pack(anchor="w",pady=(9,0))
        quick=ttk.LabelFrame(tab,text="Quick browse",padding=(10,7))
        quick.pack(fill="x",pady=(0,8))
        for col in range(2):quick.columnconfigure(col,weight=1)
        for index,(name,category) in enumerate(QUICK.items()):
            ttk.Button(quick,text=name,command=lambda c=category,n=name:
                       self.load_category(c,n)).grid(row=index//2,column=index%2,
                                                    sticky="ew",padx=4,pady=3)
        filterbar=ttk.Frame(tab);filterbar.pack(fill="x",pady=(0,7))
        ttk.Label(filterbar,text="Filter results").pack(side="left",padx=(0,8))
        ttk.Entry(filterbar,textvariable=self.result_filter).pack(
            side="left",fill="x",expand=True,padx=(0,9))
        ttk.Combobox(filterbar,textvariable=self.type_filter,state="readonly",
                     values=("All types","Person","World","Group"),
                     width=12).pack(side="left")
        self.result_filter.trace_add("write",lambda *_:self._render_results())
        self.type_filter.trace_add("write",lambda *_:self._render_results())
        ttk.Label(tab,textvariable=self.status,wraplength=810).pack(
            anchor="w",pady=(0,7))
        body=ttk.Frame(tab)
        body.pack(fill="both",expand=True)
        self.table=ttk.Treeview(body,columns=("type","name","details","id"),
                                show="headings",selectmode="browse",height=12)
        for key,label,width in (
            ("type","Type",85),("name","Name",220),
            ("details","Details",290),("id","VRChat ID",275)):
            self.table.heading(key,text=label)
            self.table.column(key,width=width,stretch=(key in ("name","details")))
        scroller=ttk.Scrollbar(body,orient="vertical",command=self.table.yview)
        self.table.configure(yscrollcommand=scroller.set)
        self.table.pack(side="left",fill="both",expand=True)
        scroller.pack(side="right",fill="y")
        buttons=ttk.Frame(tab)
        buttons.pack(fill="x",pady=(9,0))
        ttk.Button(buttons,text="View Details",command=self.view_details
                   ).pack(side="left",padx=(0,8))
        ttk.Button(buttons,text="Bookmark World",command=self.bookmark_world
                   ).pack(side="left",padx=(0,8))
        ttk.Button(buttons,text="Open on VRChat.com",command=self.open_browser
                   ).pack(side="left")
        self.table.bind("<Double-1>",lambda _:self.view_details())

    def _run(self, operation, label):
        self.status.set("Loading "+label+" from your personal VRChat account…")
        account=self.pages.account.user_id if self.pages.account.signed_in else None
        def work():
            self.pages._eligible(personal=True)
            if self.pages.account.user_id!=account:
                raise PermissionError("VRChat account changed during request")
            return operation()
        def done(rows):
            if not self.pages.account.signed_in or self.pages.account.user_id!=account:
                self.clear_results()
                return
            self._show(rows,label)
        self.owner._run_async(work,done,
               on_error=lambda error:self.status.set(label+": "+error))

    def search_all(self):
        query=self.query.get().strip()
        if not 2<=len(query)<=80:
            self.status.set("Enter 2–80 characters to search people and worlds.")
            return
        def operation():
            # If one endpoint fails, preserve whichever endpoint succeeded,
            # rather than losing both pages. Do not blindly retry rate limits.
            results=[]
            errors=[]
            for label,category in (("People","search_users"),("Worlds","search_worlds")):
                try:
                    results.append((label,self.pages.account.browse(
                        category,offset=0,query=query)))
                except Exception as exc:
                    errors.append(label+": "+str(exc))
            if not results:
                raise RuntimeError("; ".join(errors))
            return merge_explore_results(results),errors
        self._run(operation,"people and worlds")

    def load_category(self, category, label):
        def operation():
            rows=self.pages.account.browse(category,offset=0)
            return merge_explore_results([(label,rows)]),[]
        self._run(operation,label)

    def clear_results(self):
        self._all_results=()
        self._result_account=None
        self.results={}
        self.table.delete(*self.table.get_children())
        self.status.set("Personal VRChat account changed. Browse again to load results.")

    def _show(self,result,label):
        rows,errors=result
        self._result_account=self.pages.account.user_id
        self._all_results=tuple(rows)
        count=self._render_results()
        self.status.set(f"{count} / {len(rows)} {label} result(s) shown. " +
                        ("Partial results: "+"; ".join(errors) if errors else
                         "Double-click a result to inspect it."))

    def _render_results(self):
        self.table.delete(*self.table.get_children())
        self.results={}
        if not self.pages.account.signed_in or self.pages.account.user_id!=self._result_account:
            return 0
        needle=self.result_filter.get().strip().casefold()[:120]
        category=self.type_filter.get()
        for row in self._all_results:
            if category!="All types" and row[0]!=category:
                continue
            if needle and not any(needle in str(field).casefold() for field in row):
                continue
            iid=self.table.insert("",tk.END,values=row)
            self.results[iid]=row
        return len(self.results)

    def _selected(self):
        selected=self.table.selection()
        if not selected or selected[0] not in self.results:
            raise ValueError("Select a person or world first")
        if not self.pages.account.signed_in or self.pages.account.user_id!=self._result_account:
            raise PermissionError("Browse using your current VRChat account first")
        return self.results[selected[0]]

    def view_details(self):
        try:
            row=self._selected()
            self.pages.profile_tools.set_selected(row[3])
        except Exception as exc:self.status.set(str(exc))

    def bookmark_world(self):
        try:
            kind,name,_,identifier=self._selected()
            if kind!="World" or not WORLD_ID.fullmatch(identifier):
                raise ValueError("Select a world to bookmark")
            self.pages._eligible(personal=True)
            self.pages.collections.bookmark_from_social(identifier,name)
            self.status.set("Saved to your personal World Bookmarks.")
        except Exception as exc:self.status.set(str(exc))

    def open_browser(self):
        try:
            kind,_,_,identifier=self._selected()
            suffix={"Person":"user","World":"world","Group":"group"}[kind]
            webbrowser.open("https://vrchat.com/home/"+suffix+"/"+identifier)
        except Exception as exc:self.status.set(str(exc))
