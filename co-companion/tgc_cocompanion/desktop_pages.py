"""Feature-complete *pages*, progressively backed by the Mobile Companion API.

Adds real personal VRChat login/2FA, social browsing and search, complete
StaffDex scans, and a Windows OSC studio to the existing owner pilot.
The old embedded Node uploader remains OFF. These widgets never receive
TGC owner Central Key or DPAPI Node key.
"""
from __future__ import annotations

from datetime import datetime
import tkinter as tk
from tkinter import ttk
import webbrowser

from .vrchat_personal import PersonalVRChat, USER_ID
from .staffdex_desktop import StaffDex
from .desktop_osc_tools import PersonalOscPresets, PersonalHeightPreset, WindowsOscControls

CATEGORIES = (
    ("Online Friends","online"),("Offline Friends","offline"),
    ("Favorite Worlds","worlds"),("Recent Worlds","recent"),
    ("My Groups","groups"),("User Search","search_users"),
    ("World Search","search_worlds"),("Group Search","search_groups"),
    ("Inbox","inbox"),
)
_CATEGORY_BY_NAME = dict(CATEGORIES)


class DesktopPages:
    def __init__(self, owner, notebook: ttk.Notebook):
        self.owner=owner
        self.root=owner.root
        self.account=PersonalVRChat(owner.terms_dir)
        self.dex=StaffDex(owner.terms_dir)
        self.presets=PersonalOscPresets(owner.terms_dir)
        self.heights=PersonalHeightPreset(owner.terms_dir)
        self.controls: WindowsOscControls | None=None
        self._closed=False
        self._social_rows: list[dict]=[]
        self._social_offset=0
        self._social_category="online"
        self._snapshot=None
        self._build_account(notebook)
        self._build_social(notebook)
        from .desktop_friend_hub import FriendHubPage
        self.friend_hub=FriendHubPage(self,notebook)
        self._build_staffdex(notebook)
        self._build_osc(notebook)
        from .desktop_more_pages import ProfileAndPresence
        self.profile_tools=ProfileAndPresence(self,notebook)
        from .desktop_collection_pages import CollectionPages
        self.collections=CollectionPages(self,notebook)
        from .desktop_explore import ExplorePage
        self.explore=ExplorePage(self,notebook)
        from .desktop_recent_players import RecentPlayersPage
        self.recent_players=RecentPlayersPage(self,notebook)
        from .desktop_official_chatboxes import OfficialChatboxPage
        self.official_chatboxes=OfficialChatboxPage(self,notebook)
        self._build_home(notebook)
        self._account_state()
        self._load_presets()
        self.root.after(1000,self._osc_tick)

    def _build_home(self, notebook):
        """Responsive v0.4 dashboard built from existing live owner pilot state."""
        self.home = ttk.Frame(notebook, padding=(16, 13))
        notebook.insert(0, self.home, text="Home")
        ttk.Label(self.home, text="YOUR TGC COMMAND CENTER",
                  style="PageTitle.TLabel").pack(anchor="w")
        ttk.Label(self.home, text=(
            "Jump between VRChat, StaffDex, OSC and owner tools without losing your place."
        ), style="PageSubtitle.TLabel").pack(anchor="w", pady=(2, 9))

        self.home_staff = tk.StringVar(value="Awaiting owner verification")
        self.home_vrc = tk.StringVar(value="Not signed in")
        self.home_instance = tk.StringVar(value="No sessions detected")
        self.home_dex = tk.StringVar(value="No scan available")
        grid = ttk.Frame(self.home)
        grid.pack(fill="x", pady=(3, 8))
        for col in range(2):
            grid.columnconfigure(col, weight=1, uniform="homecards")
        cards = (
            ("STAFF ACCESS", self.home_staff, "Verification", 0, 0),
            ("PERSONAL VRCHAT", self.home_vrc, "VRChat Account", 0, 1),
            ("VRCHAT INSTANCES", self.home_instance, "VRChat Launcher", 1, 0),
            ("STAFFDEX", self.home_dex, "StaffDex", 1, 1),
        )
        for title, variable, destination, row, col in cards:
            card = ttk.Frame(grid, style="Card.TFrame", padding=(12, 10))
            card.grid(row=row, column=col, sticky="nsew", padx=(0 if col == 0 else 5,
                      5 if col == 0 else 0), pady=4)
            ttk.Label(card, text=title, style="CardLabel.TLabel").pack(anchor="w")
            ttk.Label(card, textvariable=variable, style="CardValue.TLabel",
                      wraplength=300).pack(anchor="w", pady=(6, 7))
            ttk.Button(card, text="Open " + destination,
                       command=lambda name=destination:self._select_tab(notebook,name)
                       ).pack(anchor="w")

        tools = ttk.Frame(self.home)
        tools.pack(fill="x", pady=(5, 0))
        ttk.Label(tools, text="QUICK ACTIONS",
                  style="PageTitle.TLabel").pack(anchor="w", pady=(0, 6))
        actions = ttk.Frame(tools)
        actions.pack(fill="x")
        for col in range(3):
            actions.columnconfigure(col, weight=1, uniform="homeactions")
        destinations = (
            ("Friends Hub", "Friends Hub"), ("Find Profiles", "Profiles"),
            ("OSC Studio", "OSC Studio"), ("Saved Worlds", "Bookmarks"),
            ("Inbox", "Inbox"), ("Updates", "Updates"),
            ("Explore VRChat", "Explore"), ("Recent Players", "Recent Players"),
            ("Staff Chatboxes", "Staff Chatboxes"),
        )
        for index,(label, destination) in enumerate(destinations):
            ttk.Button(actions, text=label,
                       command=lambda name=destination:self._select_tab(notebook,name)
                       ).grid(row=index//3, column=index%3, sticky="ew", padx=4, pady=3)
        lower = ttk.Frame(self.home)
        lower.pack(fill="x", pady=(9, 0))
        self.home_recent_var = tk.StringVar(value="Recently opened pages: Home")
        ttk.Label(lower, textvariable=self.home_recent_var,
                  style="PageSubtitle.TLabel").pack(side="left")
        ttk.Button(lower, text="Copy Safe Diagnostics",
                   command=self._copy_safe_diagnostics).pack(side="right")
        ttk.Label(self.home, text=(
            "Owner-only preview • No Invite Network uploader • No phone/Quest pairing yet."
        ), style="PageSubtitle.TLabel").pack(anchor="w", pady=(10, 0))
        self.root.after(2000, self._home_tick)

    def _copy_safe_diagnostics(self):
        """Shareable troubleshooting details without secrets or VRChat identifiers."""
        try:
            access = self.owner.lease.current() if self.owner.lease else None
            running = 0
            if self.owner.launcher:
                running = sum(s.pid is not None for s in self.owner.launcher.statuses())
            report = "\n".join((
                "TGC Co-Companion owner preview v0.11.1",
                f"Owner verified: {bool(access and self.owner._terms_valid())}",
                f"Personal VRChat connected: {bool(self.account.signed_in)}",
                f"Running managed VRChat instances: {running}",
                f"StaffDex snapshot available: {self._snapshot is not None}",
                "Legacy Node sightings uploader: NOT ENABLED in Co-Companion",
                "Production Relay configuration: UNCHANGED",
                "No tokens, passwords, session cookies, account IDs, or paths included.",
            ))
            self.root.clipboard_clear()
            self.root.clipboard_append(report)
            self._notice("Safe Co-Companion diagnostics copied to clipboard.")
        except Exception as exc:
            self._notice("Could not copy diagnostics: " + str(exc))

    @staticmethod
    def _select_tab(notebook,title):
        for tab in notebook.tabs():
            if notebook.tab(tab,"text")==title:
                notebook.select(tab)
                return

    def _home_tick(self):
        if self._closed:return
        try:
            access=self.owner.lease.current() if self.owner.lease else None
            self.home_staff.set(
                "Authorized: "+self.owner.enrollment.discord_id+
                f"  •  {access.max_instances} managed instance slots"
                if access else "Staff access: verify in the Verification tab"
            )
            self.home_vrc.set(
                self.account.display_name+" • "+self.account.user_id
                if self.account.signed_in else "Sign in to personal VRChat to use Social & StaffDex"
            )
            if self.owner.launcher:
                statuses=self.owner.launcher.statuses()
                active=sum(x.pid is not None for x in statuses)
                limit=access.max_instances if access else 0
                self.home_instance.set(f"{active}/{limit} authorized processes running")
            else:self.home_instance.set("Configure launcher after verification")
            self.home_dex.set(
                f"{self._snapshot.earned}/{self._snapshot.total} collected"
                if self._snapshot else "Run a complete roster and friends scan to unlock progress"
            )
        except Exception:
            self.home_instance.set("VRChat process discovery unavailable")
        if hasattr(self.owner, "shell"):
            self.home_recent_var.set("Recently opened: " +
                                     "  /  ".join(self.owner.shell.state.recent[:4]))
        self.root.after(10000,self._home_tick)

    def _eligible(self,personal=False):
        if not self.owner._terms_valid():
            raise PermissionError("Accept the Co-Companion Desktop Terms first")
        if personal:
            if not self.account.signed_in:
                raise PermissionError("Sign in to personal VRChat first")
            return
        lease=self.owner.lease.current() if self.owner.lease else None
        if not lease or not lease.valid_limit(__import__("time").time()):
            raise PermissionError("Verify your TGC staff pilot first")

    def _notice(self,error):
        self.owner.status_var.set(str(error))

    def _account_state(self):
        self.account_status.set(
            f"Connected: {self.account.display_name} ({self.account.user_id})"
            if self.account.signed_in else "Personal VRChat account not connected"
        )

    def _account_panel(self,parent,title):
        holder=ttk.Frame(parent,padding=(16,14))
        parent.add(holder,text=title)
        return holder

    def _build_account(self,book):
        panel=self._account_panel(book,"VRChat Account")
        ttk.Label(panel,text="PERSONAL VRCHAT",style="PageTitle.TLabel").pack(anchor="w")
        ttk.Label(panel,text="Your VRChat account is separate from TGC staff verification. "
                  "Your password is never saved, and your session is Windows DPAPI encrypted.",
                  wraplength=760).pack(anchor="w",pady=(5,10))
        status=ttk.LabelFrame(panel,text="Connection",padding=(13,10))
        status.pack(fill="x",pady=(0,12))
        self.account_status=tk.StringVar()
        self.account_feedback=tk.StringVar(value="")
        ttk.Label(status,textvariable=self.account_status,wraplength=750).pack(anchor="w")
        ttk.Label(status,textvariable=self.account_feedback,wraplength=750).pack(anchor="w",pady=(5,0))
        form=ttk.LabelFrame(panel,text="Sign in securely",padding=(14,12))
        form.pack(fill="x")
        ttk.Label(form,text="Email or username").pack(anchor="w")
        self.username=tk.StringVar()
        ttk.Entry(form,textvariable=self.username,width=54).pack(fill="x",pady=(3,9))
        ttk.Label(form,text="Password").pack(anchor="w")
        self.password=tk.StringVar()
        password_entry=ttk.Entry(form,textvariable=self.password,show="•",width=54)
        password_entry.pack(fill="x",pady=(3,10))
        password_entry.bind("<Return>",lambda _event:self._login())
        bar=ttk.Frame(form);bar.pack(anchor="w",pady=(2,0))
        ttk.Button(bar,text="Sign in",style="Accent.TButton",
                   command=self._login).pack(side="left",padx=(0,9))
        ttk.Button(bar,text="Check session",
                   command=self._profile).pack(side="left",padx=(0,9))
        ttk.Button(bar,text="Sign out",command=self._logout).pack(side="left")
        self.two_factor_panel=ttk.LabelFrame(panel,text="Complete verification",padding=(14,12))
        self.verification_hint=tk.StringVar()
        ttk.Label(self.two_factor_panel,textvariable=self.verification_hint,
                  wraplength=750).pack(anchor="w",pady=(0,7))
        self.two_factor=tk.StringVar()
        row=ttk.Frame(self.two_factor_panel);row.pack(anchor="w")
        code_entry=ttk.Entry(row,textvariable=self.two_factor,width=24,show="•")
        code_entry.pack(side="left",padx=(0,9))
        self.two_factor_entry=code_entry
        code_entry.bind("<Return>",lambda _event:self._verify_2fa())
        ttk.Button(row,text="Verify code",style="Accent.TButton",
                   command=self._verify_2fa).pack(side="left")
        self.method_actions=ttk.Frame(self.two_factor_panel)
        self.method_actions.pack(anchor="w",pady=(7,0))
        ttk.Label(panel,text="Login and verification codes go only to VRChat HTTPS. "
                  "Nothing is sent to the TGC Relay.",wraplength=760).pack(anchor="w",pady=13)
        self._account_state()
        if self.account.signed_in:
            self.account_feedback.set("Checking saved VRChat session…")
            self.root.after(1700,self._restore_personal_session)

    def _show_pending_2fa(self):
        methods=self.account.pending_methods
        for widget in self.method_actions.winfo_children():
            widget.destroy()
        if not methods:
            self.two_factor_panel.pack_forget()
            return
        if not self.two_factor_panel.winfo_manager():
            self.two_factor_panel.pack(fill="x",pady=(12,0))
        if "emailotp" in methods and "totp" not in methods:
            self.verification_hint.set("Enter the code VRChat sent to your email.")
        elif "totp" in methods:
            self.verification_hint.set("Enter your 6-digit authenticator code.")
        else:
            self.verification_hint.set("Enter a VRChat recovery code.")
        if "otp" in methods and len(methods)>1:
            ttk.Button(self.method_actions,text="Use recovery code",
                       command=lambda:self._choose_2fa("otp")).pack(side="left",padx=(0,8))
        if "totp" in methods and "otp" in methods and "emailotp" not in methods:
            ttk.Button(self.method_actions,text="Use authenticator",
                       command=lambda:self._choose_2fa("totp")).pack(side="left")
        if "emailotp" in methods and "totp" in methods:
            ttk.Button(self.method_actions,text="Use email code",
                       command=lambda:self._choose_2fa("emailotp")).pack(side="left",padx=(0,8))
            ttk.Button(self.method_actions,text="Use authenticator",
                       command=lambda:self._choose_2fa("totp")).pack(side="left")
        self.two_factor_entry.focus_set()

    def _choose_2fa(self,mode):
        try:
            self.account.prefer_2fa(mode)
            names={"otp":"recovery", "totp":"authenticator", "emailotp":"email"}
            self.verification_hint.set("Enter your "+names[mode]+" code.")
            self.two_factor.set("")
            self.two_factor_entry.focus_set()
        except Exception as exc:self.account_feedback.set(str(exc))

    def _login(self):
        if self.account.signed_in:
            self.account_feedback.set("Already connected. Sign out to change accounts.")
            return
        if not self.owner._terms_valid():
            self.account_feedback.set("Accept the Desktop Terms in Verification first.")
            return
        user=self.username.get().strip()
        pwd=self.password.get()
        self.password.set("")
        self.account_feedback.set("Signing in to VRChat…")
        def done(methods):
            self._account_state()
            self._show_pending_2fa()
            if methods:
                self.account_feedback.set("Password accepted. Enter your verification code.")
            else:
                self.account_feedback.set("Connected. Session saved securely.")
                self._load_presets()
        self.owner._run_async(lambda:self.account.login(user,pwd),done,
            on_error=lambda error:self.account_feedback.set("Sign-in failed: "+error))

    def _verify_2fa(self):
        code=self.two_factor.get().strip()
        self.two_factor.set("")
        self.account_feedback.set("Checking verification code…")
        def done(name):
            self._account_state()
            self._show_pending_2fa()
            self._load_presets()
            self.account_feedback.set("Welcome, "+name+". You're connected.")
        self.owner._run_async(lambda:self.account.verify_2fa(code),done,
            on_error=lambda error:self.account_feedback.set("Verification: "+error))

    def _restore_personal_session(self):
        if not self.account.signed_in:return
        if self.owner._busy:
            self.root.after(2200,self._restore_personal_session)
            return
        self.owner._run_async(self.account.current_user,
            lambda _profile:self.account_feedback.set("Saved VRChat session verified."),
            on_error=lambda error:self.account_feedback.set(
                "Saved session not verified: "+error+". Try Check session."))

    def _profile(self):
        if not self.account.signed_in:
            self.account_feedback.set("Sign in to VRChat first.")
            return
        self.account_feedback.set("Checking VRChat session…")
        def done(profile):
            self._account_state()
            self.account_feedback.set("Connected: "+str(profile.get("displayName") or "VRChat user"))
        self.owner._run_async(self.account.current_user,done,
            on_error=lambda error:self.account_feedback.set("Session check: "+error))

    def _logout(self):
        try:
            if self.controls is not None:
                self.controls.shutdown()
            self.account.logout()
            self._snapshot=None
            self._social_rows=[]
            self.social_table.delete(*self.social_table.get_children())
            self.dex_table.delete(*self.dex_table.get_children())
            self.rewards.delete(0,tk.END)
            self.dex_status.set("Personal VRChat disconnected; collection not available")
            self.dex_count.set("Not scanned")
            self.dex_roster_count.set("Not loaded")
            self.collections.clear_private_views()
            self.explore.clear_results()
            self._load_presets()
            self._account_state()
            self._show_pending_2fa()
            self.account_feedback.set("Signed out of personal VRChat.")
            self._notice("Personal VRChat session signed out.")
        except Exception as exc:self._notice(exc)

    def _build_social(self,book):
        panel=self._account_panel(book,"Social")
        ttk.Label(panel,text="Friends, Worlds, Groups & Inbox",
                  font=("Segoe UI",15,"bold")).pack(anchor="w")
        # Two rows prevent the search toolbar from crushing the results at 720p.
        browse=ttk.LabelFrame(panel,text="Browse personal VRChat",padding=(12,10))
        browse.pack(fill="x",pady=(5,9))
        filters=ttk.Frame(browse);filters.pack(fill="x")
        self.category=tk.StringVar(value="Online Friends")
        ttk.Combobox(filters,textvariable=self.category,
            values=[x[0] for x in CATEGORIES],state="readonly",width=23).pack(
            side="left",padx=(0,9))
        self.search=tk.StringVar()
        ttk.Entry(filters,textvariable=self.search).pack(side="left",fill="x",expand=True)
        commands=ttk.Frame(browse);commands.pack(fill="x",pady=(9,0))
        ttk.Button(commands,text="Load / Search",style="Accent.TButton",
            command=lambda:self._load_social(reset=True)).pack(side="left",padx=(0,8))
        ttk.Button(commands,text="Load More",command=lambda:self._load_social(reset=False)
            ).pack(side="left")
        self.social_status=tk.StringVar(value="Sign in to your personal VRChat account to browse.")
        ttk.Label(panel,textvariable=self.social_status,wraplength=830).pack(anchor="w",pady=(0,6))
        body=ttk.Frame(panel);body.pack(fill="both",expand=True)
        cols=("name","detail","id")
        self.social_table=ttk.Treeview(body,columns=cols,show="headings",selectmode="browse",height=15)
        for label,width in (("name",220),("detail",325),("id",315)):
            self.social_table.heading(label,text=label.title())
            self.social_table.column(label,width=width,stretch=(label!="id"))
        scroll=ttk.Scrollbar(body,orient="vertical",command=self.social_table.yview)
        self.social_table.configure(yscrollcommand=scroll.set)
        self.social_table.pack(side="left",fill="both",expand=True)
        scroll.pack(side="right",fill="y")
        buttons=ttk.Frame(panel);buttons.pack(anchor="w",pady=8)
        ttk.Button(buttons,text="View Details / Actions",command=self._detail_selected).pack(side="left",padx=(0,9))
        ttk.Button(buttons,text="Bookmark Selected World",command=self._bookmark_selected_world).pack(side="left",padx=(0,9))
        ttk.Button(buttons,text="Open Selected in VRChat Website",command=self._open_selected).pack(side="left")
        ttk.Label(panel,text="Profile details provide only actions allowed by your personal VRChat session; no group moderation is performed here.",
                  style="PageSubtitle.TLabel",wraplength=780).pack(anchor="w")

    @staticmethod
    def _row(row):
        name = str(row.get("displayName") or row.get("name") or row.get("worldName") or
                   row.get("senderUsername") or row.get("type") or "Unknown")
        detail = str(row.get("status") or row.get("location") or row.get("description") or
                     row.get("message") or row.get("type") or "")
        identifier = str(row.get("id") or "")
        return name[:105],detail[:160],identifier[:128]

    def _load_social(self,reset):
        label=self.category.get()
        cat=_CATEGORY_BY_NAME.get(label)
        if cat is None:return
        text=self.search.get().strip()
        if cat in ("search_users","search_worlds","search_groups") and len(text)<2:
            self._notice("Enter at least two characters to search VRChat.");return
        old_cat=self._social_category
        if not reset and cat!=old_cat:
            self._notice("Choose Load / Search when switching categories.");return
        offset=0 if reset else self._social_offset
        def work():
            self._eligible(personal=True)
            return self.account.browse(cat,offset=offset,query=text)
        def done(rows):
            if reset:
                self._social_rows=[]
                self.social_table.delete(*self.social_table.get_children())
            self._social_rows.extend(rows)
            for row in rows:
                self.social_table.insert("",tk.END,values=self._row(row))
            self._social_offset=offset+60
            self._social_category=cat
            self.social_status.set(f"{label}: {len(self._social_rows)} entries loaded"+
                                   (" • More available" if len(rows)==60 else " • End of current results"))
            self._notice(f"Loaded {len(rows)} personal VRChat {label.lower()} records")
        self.owner._run_async(work,done)

    def _bookmark_selected_world(self):
        selected=self.social_table.selection()
        if not selected:
            self._notice("Select a world from Social first")
            return
        record=self.social_table.item(selected[0],"values")
        if len(record)<3 or not str(record[2]).startswith("wrld_"):
            self._notice("Select a VRChat world to bookmark")
            return
        try:
            self.collections.bookmark_from_social(str(record[2]),str(record[0])[:100])
            self._notice("Saved world to your personal Co-Companion bookmarks")
        except Exception as exc:self._notice(exc)

    def _detail_selected(self):
        selected=self.social_table.selection()
        if not selected:
            self._notice("Select a VRChat entry first")
            return
        record=self.social_table.item(selected[0],"values")
        if len(record)<3:
            self._notice("Selected entry has no profile")
            return
        try:
            self.profile_tools.set_selected(str(record[2]))
        except Exception as exc:
            self._notice(exc)

    def _open_selected(self):
        item=self.social_table.selection()
        if not item:return
        record=self.social_table.item(item[0], "values")
        if len(record)<3:return
        uid=str(record[2])
        if USER_ID.fullmatch(uid):
            url="https://vrchat.com/home/user/"+uid
        elif uid.startswith("wrld_") and len(uid)<90:
            url="https://vrchat.com/home/world/"+uid
        elif uid.startswith("grp_") and len(uid)<90:
            url="https://vrchat.com/home/group/"+uid
        else:
            self._notice("No supported public profile page for this entry");return
        webbrowser.open(url)

    def _build_staffdex(self,book):
        panel=self._account_panel(book,"StaffDex")
        ttk.Label(panel,text="StaffDex — TGC Staff Collection",
                  font=("Segoe UI",15,"bold")).pack(anchor="w")
        ttk.Label(panel,text="Uses the same official TGC roster and full VRChat friendship scan as Mobile. Unverified or incomplete scans never award progress.",
                  wraplength=820).pack(anchor="w",pady=(3,6))
        actions=ttk.Frame(panel)
        actions.pack(anchor="w",pady=(0,6))
        ttk.Button(actions,text="Load Staff Directory",
                   command=self._directory_dex).pack(side="left",padx=(0,9))
        ttk.Button(actions,text="Scan My Friendships",style="Accent.TButton",
                   command=self._scan_dex).pack(side="left")
        self.dex_status=tk.StringVar(value="Load the public staff roster, or sign in and scan your VRChat friendships.")
        ttk.Label(panel,textvariable=self.dex_status,wraplength=830).pack(anchor="w",pady=(0,8))
        controls=ttk.Frame(panel)
        controls.pack(fill="x",pady=(1,8))
        self.dex_query=tk.StringVar()
        self.dex_rank=tk.StringVar(value="All ranks")
        self.dex_progress=tk.StringVar(value="All")
        self.dex_rows=[]
        ttk.Label(controls,text="Find:").pack(side="left")
        ttk.Entry(controls,textvariable=self.dex_query,width=22).pack(
            side="left",padx=(5,10),fill="x",expand=True)
        ttk.Combobox(controls,textvariable=self.dex_rank,state="readonly",width=18,
            values=("All ranks","The Royalty","Owner","Head Admin",
                    "Admin","Head Moderator","Moderator","Trial Moderator")
            ).pack(side="left",padx=(0,10))
        ttk.Combobox(controls,textvariable=self.dex_progress,state="readonly",width=12,
            values=("All","Collected","Missing","Unscanned")
            ).pack(side="left")
        for value in (self.dex_query,self.dex_rank,self.dex_progress):
            value.trace_add("write",lambda *_:self._filter_dex())
        # Full-width directory and achievements are separate tabs: no 720p squeeze.
        summary=ttk.Frame(panel);summary.pack(fill="x",pady=(0,8))
        self.dex_count=tk.StringVar(value="Not scanned")
        self.dex_roster_count=tk.StringVar(value="Not loaded")
        for label,variable in (("FRIENDSHIP COLLECTION",self.dex_count),
                               ("OFFICIAL ROSTER",self.dex_roster_count)):
            tile=ttk.Frame(summary,style="Card.TFrame",padding=(13,10))
            tile.pack(side="left",fill="x",expand=True,padx=(0,8))
            ttk.Label(tile,text=label,style="CardLabel.TLabel").pack(anchor="w")
            ttk.Label(tile,textvariable=variable,style="CardAccent.TLabel").pack(anchor="w",pady=(5,0))
        sections=ttk.Notebook(panel)
        sections.pack(fill="both",expand=True)
        directory=ttk.Frame(sections,padding=(2,7))
        achievements=ttk.Frame(sections,padding=(9,10))
        sections.add(directory,text="Staff Directory")
        sections.add(achievements,text="Achievements")
        cols=("name","rank","status")
        list_frame=ttk.Frame(directory);list_frame.pack(fill="both",expand=True)
        self.dex_table=ttk.Treeview(list_frame,columns=cols,show="headings",height=13)
        for k,w in (("name",185),("rank",130),("status",115)):
            self.dex_table.heading(k,text=k.title())
            self.dex_table.column(k,width=w,stretch=True)
        from .desktop_staff_colors import RANK_COLORS
        for role,color in RANK_COLORS.items():
            self.dex_table.tag_configure(role,foreground=color)
        self.dex_ids={}
        scroll=ttk.Scrollbar(list_frame,orient="vertical",command=self.dex_table.yview)
        self.dex_table.configure(yscrollcommand=scroll.set)
        self.dex_table.pack(side="left",fill="both",expand=True)
        scroll.pack(side="right",fill="y")
        self.dex_table.bind("<Double-1>",lambda _e:self._dex_profile())
        ttk.Button(directory,text="Open Selected Staff Profile",
                   command=self._dex_profile).pack(anchor="w",pady=(7,2))
        ttk.Label(achievements,text="COLLECTION ACHIEVEMENTS",
                  style="PageTitle.TLabel").pack(anchor="w",pady=(0,4))
        ttk.Label(achievements,text="Complete friend scans determine collection progress. Hidden awards appear only when earned.",
                  style="PageSubtitle.TLabel",wraplength=680).pack(anchor="w",pady=(0,9))
        reward_box=ttk.Frame(achievements);reward_box.pack(fill="both",expand=True)
        self.rewards=tk.Listbox(reward_box,height=14,bg="#16243A",fg="#E7F1FF",
                                selectbackground="#25456A",selectforeground="#36D3FF",
                                borderwidth=0,highlightthickness=0)
        reward_scroll=ttk.Scrollbar(reward_box,orient="vertical",command=self.rewards.yview)
        self.rewards.configure(yscrollcommand=reward_scroll.set)
        self.rewards.pack(side="left",fill="both",expand=True)
        reward_scroll.pack(side="right",fill="y")
        self._cached_dex()

    def _cached_dex(self):
        if not self.account.signed_in or not self.owner.enrollment.discord_id:
            return
        snapshot=self.dex.cached(self.owner.enrollment.discord_id,self.account.user_id)
        if snapshot is not None:
            self._render_dex(snapshot)
            self.dex_status.set(self.dex_status.get()+" • Saved completed snapshot.")

    def _directory_dex(self):
        self.dex_status.set("Loading official TGC staff directory…")
        def work():
            if not self.owner._terms_valid():
                raise PermissionError("Accept Desktop Terms first")
            return self.dex.roster_directory()
        def done(members):
            self._snapshot=None
            self.dex_rows=[(m.name,m.rank,
                "Not scanned" if m.vrchat_id else "No VRChat ID",m.vrchat_id)
                for m in members]
            self._filter_dex()
            self.rewards.delete(0,tk.END)
            self.dex_count.set("Awaiting scan")
            self.dex_roster_count.set(f"{len(members)} members")
            self.dex_status.set(f"Loaded {len(members)} staff from the official roster. "
                  "Friendship progress requires a complete personal VRChat scan.")
        self.owner._run_async(work,done,
              on_error=lambda error:self.dex_status.set(
              "Could not load official staff roster: "+error+
              ". Previous completed snapshots are not deleted."))

    def _scan_dex(self):
        self.dex_status.set("Loading authoritative roster + every online/offline VRChat friend page…")
        def work():
            # StaffDex awards are LOCAL collection progress; never authorize
            # staff tools. Enrolled Discord ID scopes DPAPI snapshots, while
            # a verified lease still gates any privileged staff action.
            self._eligible(personal=True)
            if not self.owner.enrollment.enrolled:
                raise PermissionError("Enroll the TGC owner pilot to scope your StaffDex")
            return self.dex.refresh(self.account,self.owner.enrollment.discord_id)
        def done(snapshot):
            self._render_dex(snapshot)
            self._notice(f"StaffDex complete: {snapshot.earned}/{snapshot.total} collected")
        self.owner._run_async(work,done,
              on_error=lambda error:self.dex_status.set(
                  "StaffDex scan could not complete: "+error+
                  ". No partial friendships were saved or counted. Retry when VRChat is available."))

    def _filter_dex(self):
        from .desktop_staffdex_filter import filter_staffdex_rows
        self.dex_table.delete(*self.dex_table.get_children())
        self.dex_ids={}
        for name,rank,status,identifier in filter_staffdex_rows(
                self.dex_rows,self.dex_query.get(),self.dex_rank.get(),
                self.dex_progress.get()):
            iid=self.dex_table.insert("",tk.END,values=(name,rank,status),
                                      tags=(rank,))
            self.dex_ids[iid]=identifier

    def _dex_profile(self):
        selection=self.dex_table.selection()
        identifier=self.dex_ids.get(selection[0]) if selection else None
        if not identifier or not USER_ID.fullmatch(identifier):
            self.dex_status.set("Select a staff member with a valid VRChat ID first.")
            return
        try:
            self.profile_tools.set_selected(identifier)
        except Exception as exc:
            self.dex_status.set("Could not open staff profile: "+str(exc))

    def _render_dex(self,snapshot):
        if snapshot is None:return
        self._snapshot=snapshot
        self.rewards.delete(0,tk.END)
        self.dex_rows=[]
        for member in snapshot.members:
            status="Collected" if snapshot.collected(member.vrchat_id) and member.vrchat_id else "Missing"
            if not member.vrchat_id:
                status="No VRChat ID"
            self.dex_rows.append((member.name,member.rank,status,member.vrchat_id))
        self._filter_dex()
        self.dex_count.set(f"{snapshot.earned} / {snapshot.total} collected")
        self.dex_roster_count.set(f"{len(snapshot.members)} members")
        for title,earned in snapshot.visible_rewards().items():
            # The visible Mobile catalog includes its 16 normal awards;
            # hidden community rewards are returned ONLY after collection.
            self.rewards.insert(tk.END,("✓ " if earned else "○ ")+title)
        stamp=datetime.fromtimestamp(snapshot.captured_at).strftime("%Y-%m-%d %H:%M")
        self.dex_status.set(f"{snapshot.earned}/{snapshot.total} staff collected • Complete scan at {stamp} • "+
                            f"{len(snapshot.members)} staff in roster")

    def _build_osc(self,book):
        panel=self._account_panel(book,"OSC Studio")
        ttk.Label(panel,text="OSC STUDIO",style="PageTitle.TLabel").pack(anchor="w")
        ttk.Label(panel,text="Control an authorized running VRChat instance. OSC routes locally to your selected PID.",
                  style="PageSubtitle.TLabel",wraplength=760).pack(anchor="w",pady=(3,11))
        target=ttk.Frame(panel,style="Card.TFrame",padding=(12,10))
        target.pack(fill="x",pady=(0,8))
        ttk.Label(target,text="TARGET VRCHAT PID",style="CardLabel.TLabel").pack(side="left")
        self.osc_pid=tk.StringVar()
        ttk.Entry(target,textvariable=self.osc_pid,width=14).pack(side="left",padx=(12,8))
        ttk.Label(target,text="From your running Launcher",style="CardLabel.TLabel").pack(side="left")
        self.osc_note=tk.StringVar(value="No OSC commands sent")
        ttk.Label(panel,textvariable=self.osc_note,wraplength=770).pack(anchor="w",pady=(1,8))
        sections=ttk.Notebook(panel)
        sections.pack(fill="both",expand=True)
        chat=ttk.Frame(sections,padding=(9,12))
        avatar=ttk.Frame(sections,padding=(9,12))
        voice=ttk.Frame(sections,padding=(9,12))
        sections.add(chat,text="Chatbox & Rotation")
        sections.add(avatar,text="Avatar & Parameters")
        sections.add(voice,text="Voice")
        ttk.Label(chat,text="PERSONAL CHATBOX",style="PageTitle.TLabel").pack(anchor="w")
        ttk.Label(chat,text="Send a message or reuse a phrase saved for your personal VRChat account.",
                  style="PageSubtitle.TLabel").pack(anchor="w",pady=(3,10))
        composer=ttk.Frame(chat);composer.pack(fill="x")
        self.osc_text=tk.StringVar()
        ttk.Entry(composer,textvariable=self.osc_text).pack(side="left",fill="x",expand=True,padx=(0,9))
        ttk.Button(composer,text="Send Chatbox",style="Accent.TButton",
                   command=self._osc_send).pack(side="left")
        presets=ttk.LabelFrame(chat,text="Saved chatbox phrases",padding=(11,10))
        presets.pack(fill="both",expand=True,pady=(14,0))
        row=ttk.Frame(presets);row.pack(fill="x")
        self.phrase_name=tk.StringVar()
        ttk.Entry(row,textvariable=self.phrase_name,width=24).pack(side="left",padx=(0,7))
        ttk.Button(row,text="Save Current Text",command=self._save_phrase).pack(side="left",padx=(0,7))
        ttk.Button(row,text="Delete Selected",command=self._delete_phrase).pack(side="left")
        self.phrases=tk.Listbox(presets,height=4,exportselection=False,
                                bg="#16243A",fg="#E7F1FF",selectbackground="#25456A",
                                selectforeground="#36D3FF",borderwidth=0,highlightthickness=0)
        self.phrases.pack(fill="both",expand=True,pady=(8,9))
        self.phrases.bind("<<ListboxSelect>>",self._choose_phrase)
        rotate=ttk.Frame(presets);rotate.pack(fill="x")
        self.osc_interval=tk.IntVar(value=10)
        ttk.Label(rotate,text="Rotate every (sec)").pack(side="left")
        ttk.Spinbox(rotate,from_=5,to=3600,textvariable=self.osc_interval,width=6
                    ).pack(side="left",padx=(5,10))
        ttk.Button(rotate,text="Start Rotation",command=self._rotation_start).pack(side="left",padx=(0,9))
        ttk.Button(rotate,text="Stop",command=self._rotation_stop).pack(side="left")

        ttk.Label(avatar,text="AVATAR CONTROLS",style="PageTitle.TLabel").pack(anchor="w")
        ttk.Label(avatar,text="Height presets and typed OSC parameters for the selected instance.",
                  style="PageSubtitle.TLabel").pack(anchor="w",pady=(3,12))
        height=ttk.LabelFrame(avatar,text="Avatar eye height",padding=(11,11))
        height.pack(fill="x",pady=(0,13))
        ttk.Button(height,text="0.8 m",command=lambda:self._height(0.8)).pack(side="left",padx=(0,8))
        ttk.Button(height,text="2.0 m",command=lambda:self._height(2.0)).pack(side="left",padx=(0,8))
        self.osc_height=tk.StringVar(value="1.65")
        ttk.Entry(height,textvariable=self.osc_height,width=9).pack(side="left",padx=(0,8))
        ttk.Button(height,text="Set Height",command=self._custom_height).pack(side="left",padx=(0,8))
        ttk.Button(height,text="Save Preset",command=self._save_height).pack(side="left")
        params=ttk.LabelFrame(avatar,text="Avatar parameter",padding=(11,11))
        params.pack(fill="x")
        fields=ttk.Frame(params);fields.pack(fill="x",pady=(0,9))
        self.param_name=tk.StringVar()
        ttk.Entry(fields,textvariable=self.param_name,width=23).pack(side="left",padx=(0,8))
        self.param_type=tk.StringVar(value="bool")
        ttk.Combobox(fields,textvariable=self.param_type,values=("bool","int","float"),
                     state="readonly",width=8).pack(side="left",padx=(0,8))
        self.param_value=tk.StringVar(value="true")
        ttk.Entry(fields,textvariable=self.param_value,width=12).pack(side="left")
        ttk.Button(params,text="Send Parameter",style="Accent.TButton",
                   command=self._parameter).pack(anchor="w")

        ttk.Label(voice,text="VOICE CONTROLS",style="PageTitle.TLabel").pack(anchor="w")
        ttk.Label(voice,text="Hold to talk on the selected session. Releasing the button or leaving it releases voice.",
                  style="PageSubtitle.TLabel",wraplength=680).pack(anchor="w",pady=(3,15))
        ptt=ttk.Button(voice,text="HOLD TO TALK",style="Accent.TButton")
        ptt.pack(anchor="w",pady=(3,9))
        ptt.bind("<ButtonPress-1>",lambda e:self._ptt_press())
        ptt.bind("<ButtonRelease-1>",lambda e:self._ptt_release())
        ptt.bind("<Leave>",lambda e:self._ptt_release())

    def _active_osc(self):
        self._eligible()
        if self.owner.launcher is None:
            raise PermissionError("Verify pilot and initialize VRChat Launcher first")
        if self.controls is None or self.controls._router is not self.owner.launcher.router:
            if self.controls is not None:self.controls.shutdown()
            self.controls=WindowsOscControls(
                self.owner.launcher.router,
                lambda: bool(self.owner.lease and self.owner.lease.current() and self.owner._terms_valid()))
        pid=int(self.osc_pid.get())
        if pid<=0:raise ValueError("Select a valid running VRChat PID")
        self.owner.launcher.refresh()
        self.owner.launcher.router.target(pid)
        return self.controls,pid

    def _osc_send(self):
        try:
            ctrl,pid=self._active_osc()
            ctrl.send_chat(pid,self.osc_text.get())
            self.osc_note.set(f"OSC chatbox sent locally to PID {pid} (UDP does not confirm display)")
        except Exception as exc:self._notice(exc)

    def _save_height(self):
        try:
            self._eligible(personal=True)
            value=self.heights.save(self.account.user_id,float(self.osc_height.get()))
            self.osc_note.set(f"Saved your personal {value:g} m height preset")
        except Exception as exc:self.osc_note.set("Could not save height: "+str(exc))

    def _load_presets(self):
        self.phrases.delete(0,tk.END)
        if hasattr(self,"official_chatboxes"):
            self.official_chatboxes.render()
        if hasattr(self,"friend_hub"):
            self.friend_hub.refresh()
        self._cached_dex()
        if not self.account.signed_in:return
        saved=self.heights.get(self.account.user_id)
        if saved is not None:
            self.osc_height.set(str(saved))
        for name,text in self.presets.list(self.account.user_id):
            self.phrases.insert(tk.END,name)

    def _choose_phrase(self,event=None):
        selection=self.phrases.curselection()
        if not selection or not self.account.signed_in:return
        phrases=self.presets.list(self.account.user_id)
        if selection[0]<len(phrases):
            name,text=phrases[selection[0]]
            self.phrase_name.set(name)
            self.osc_text.set(text)

    def _save_phrase(self):
        try:
            self._eligible(personal=True)
            self.presets.save(self.account.user_id,self.phrase_name.get(),self.osc_text.get())
            self._load_presets();self.osc_note.set("Saved personal OSC phrase")
        except Exception as exc:self._notice(exc)

    def _delete_phrase(self):
        try:
            self._eligible(personal=True)
            self.presets.delete(self.account.user_id,self.phrase_name.get())
            self._load_presets();self.osc_note.set("Removed saved OSC phrase")
        except Exception as exc:self._notice(exc)

    def _rotation_start(self):
        try:
            if hasattr(self,"official_chatboxes"):
                self.official_chatboxes._official_active=False
            self._eligible(personal=True)
            ctrl,pid=self._active_osc()
            phrases=[text for name,text in self.presets.list(self.account.user_id)][:8]
            ctrl.set_rotation(pid,phrases,int(self.osc_interval.get()))
            self.osc_note.set("Rotating approved saved chatboxes on the selected VRChat PID")
        except Exception as exc:self._notice(exc)

    def _rotation_stop(self):
        if hasattr(self,"official_chatboxes"):
            self.official_chatboxes._official_active=False
        if self.controls is not None:self.controls.stop_rotation()
        self.osc_note.set("OSC rotation stopped")

    def _height(self,value):
        try:
            ctrl,pid=self._active_osc()
            ctrl.send_height(pid,float(value))
            self.osc_note.set(f"Set avatar eye height to {value}m on PID {pid}")
        except Exception as exc:self._notice(exc)

    def _custom_height(self):
        try:self._height(float(self.osc_height.get()))
        except Exception as exc:self._notice(exc)

    def _parameter(self):
        try:
            kind=self.param_type.get()
            raw=self.param_value.get().strip()
            if kind=="bool":
                if raw.lower() not in ("true","false"):raise ValueError("Use true or false")
                value=raw.lower()=="true"
            elif kind=="int":value=int(raw)
            elif kind=="float":value=float(raw)
            else:raise ValueError("Choose a parameter type")
            ctrl,pid=self._active_osc()
            ctrl.send_parameter(pid,self.param_name.get(),value)
            self.osc_note.set(f"Avatar parameter sent to PID {pid}")
        except Exception as exc:self._notice(exc)

    def _ptt_press(self):
        try:
            ctrl,pid=self._active_osc()
            ctrl.set_voice(pid,True)
            self.osc_note.set("PTT held — release mouse to stop")
        except Exception as exc:self._notice(exc)

    def _ptt_release(self):
        if self.controls is None:return
        try:
            self.controls.release_voice()
            self.osc_note.set("PTT released")
        except Exception as exc:self._notice(exc)

    def _osc_tick(self):
        if self._closed:return
        if self.controls is not None:
            try:
                if hasattr(self,"official_chatboxes"):
                    self.official_chatboxes.verify_rotation()
                self.controls.release_if_overdue(300)
                self.controls.rotate_tick()
            except Exception as exc:
                self._notice("OSC safety action: "+str(exc))
        self.root.after(1000,self._osc_tick)

    def close(self):
        self._closed=True
        if hasattr(self,"friend_hub"):
            self.friend_hub.close()
        if self.controls is not None:
            self.controls.shutdown()
