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
    ("World Search","search_worlds"),("Inbox","inbox"),
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
        self._build_staffdex(notebook)
        self._build_osc(notebook)
        from .desktop_more_pages import ProfileAndPresence
        self.profile_tools=ProfileAndPresence(self,notebook)
        from .desktop_collection_pages import CollectionPages
        self.collections=CollectionPages(self,notebook)
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
            ("Social & Friends", "Social"), ("Find Profiles", "Profiles"),
            ("OSC Studio", "OSC Studio"), ("Saved Worlds", "Bookmarks"),
            ("Inbox", "Inbox"), ("Updates", "Updates"),
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
                "TGC Co-Companion owner preview v0.4",
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
        search=ttk.Frame(panel);search.pack(fill="x",pady=7)
        self.category=tk.StringVar(value="Online Friends")
        ttk.Combobox(search,textvariable=self.category,
                     values=[x[0] for x in CATEGORIES],state="readonly",width=23).pack(side="left",padx=(0,7))
        self.search=tk.StringVar()
        ttk.Entry(search,textvariable=self.search,width=36).pack(side="left",fill="x",expand=True,padx=(0,7))
        ttk.Button(search,text="Load / Search",command=lambda:self._load_social(reset=True)).pack(side="left")
        ttk.Button(search,text="More",command=lambda:self._load_social(reset=False)).pack(side="left",padx=6)
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
        ttk.Label(buttons,text="Read-only in this owner pilot; no friend requests or group moderation.",
                  wraplength=500).pack(side="left",padx=14)

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
        if cat in ("search_users","search_worlds") and len(text)<2:
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
        ttk.Button(panel,text="Refresh Complete StaffDex Scan",command=self._scan_dex).pack(anchor="w",pady=(0,6))
        self.dex_status=tk.StringVar(value="Sign in to personal VRChat and verify owner pilot first")
        ttk.Label(panel,textvariable=self.dex_status,wraplength=830).pack(anchor="w",pady=(0,8))
        split=ttk.Panedwindow(panel,orient="horizontal");split.pack(fill="both",expand=True)
        left=ttk.Frame(split);right=ttk.Frame(split)
        split.add(left,weight=3);split.add(right,weight=2)
        cols=("name","rank","status")
        self.dex_table=ttk.Treeview(left,columns=cols,show="headings",height=17)
        for k,w in (("name",185),("rank",130),("status",80)):
            self.dex_table.heading(k,text=k.title())
            self.dex_table.column(k,width=w,stretch=True)
        self.dex_table.pack(fill="both",expand=True,padx=(0,6))
        ttk.Label(right,text="Achievements",font=("Segoe UI",12,"bold")).pack(anchor="w")
        self.rewards=tk.Listbox(right,height=18,bg="#16243A",fg="#E7F1FF",
                                selectbackground="#25456A",selectforeground="#36D3FF",
                                borderwidth=0,highlightthickness=0)
        self.rewards.pack(fill="both",expand=True)
        self._cached_dex()

    def _cached_dex(self):
        if not self.account.signed_in or not self.owner.enrollment.discord_id:return
        try:self._render_dex(self.dex.cached(self.owner.enrollment.discord_id,self.account.user_id))
        except Exception:pass

    def _scan_dex(self):
        def work():
            self._eligible()
            self._eligible(personal=True)
            return self.dex.refresh(self.account,self.owner.enrollment.discord_id)
        def done(snapshot):
            self._render_dex(snapshot)
            self._notice(f"StaffDex complete: {snapshot.earned}/{snapshot.total} collected")
        self.dex_status.set("Scanning roster and all friend pages…")
        self.owner._run_async(work,done)

    def _render_dex(self,snapshot):
        if snapshot is None:return
        self._snapshot=snapshot
        self.dex_table.delete(*self.dex_table.get_children())
        self.rewards.delete(0,tk.END)
        for member in snapshot.members:
            status="Collected" if snapshot.collected(member.vrchat_id) and member.vrchat_id else "Missing"
            if not member.vrchat_id:status="No VRChat ID"
            self.dex_table.insert("",tk.END,values=(member.name,member.rank,status))
        for title,earned in snapshot.visible_rewards().items():
            # The visible Mobile catalog includes its 16 normal awards;
            # hidden community rewards are returned ONLY after collection.
            self.rewards.insert(tk.END,("✓ " if earned else "○ ")+title)
        stamp=datetime.fromtimestamp(snapshot.captured_at).strftime("%Y-%m-%d %H:%M")
        self.dex_status.set(f"{snapshot.earned}/{snapshot.total} staff collected • Complete scan at {stamp} • "+
                            f"{len(snapshot.members)} staff in roster")

    def _build_osc(self,book):
        panel=self._account_panel(book,"OSC Studio")
        ttk.Label(panel,text="OSC Studio — Personal Chatbox & Avatar Controls",
                  font=("Segoe UI",15,"bold")).pack(anchor="w")
        ttk.Label(panel,text="Select the actual VRChat PID from the Launcher. Saved phrases are separate for each signed-in personal VRChat account.",
                  wraplength=820).pack(anchor="w",pady=(2,9))
        top=ttk.Frame(panel);top.pack(fill="x")
        ttk.Label(top,text="VRChat PID").pack(side="left")
        self.osc_pid=tk.StringVar()
        ttk.Entry(top,textvariable=self.osc_pid,width=12).pack(side="left",padx=(8,20))
        ttk.Label(top,text="Text").pack(side="left")
        self.osc_text=tk.StringVar()
        ttk.Entry(top,textvariable=self.osc_text,width=47).pack(side="left",fill="x",expand=True,padx=6)
        ttk.Button(top,text="Send",command=self._osc_send).pack(side="left")
        self.osc_note=tk.StringVar(value="No OSC commands sent")
        ttk.Label(panel,textvariable=self.osc_note,wraplength=810).pack(anchor="w",pady=7)
        presets=ttk.LabelFrame(panel,text="Saved Chatbox Phrases",padding=9);presets.pack(fill="x",pady=6)
        row=ttk.Frame(presets);row.pack(fill="x")
        self.phrase_name=tk.StringVar()
        ttk.Entry(row,textvariable=self.phrase_name,width=24).pack(side="left",padx=(0,7))
        ttk.Button(row,text="Save Current Text",command=self._save_phrase).pack(side="left",padx=(0,7))
        ttk.Button(row,text="Delete Selected",command=self._delete_phrase).pack(side="left")
        self.phrases=tk.Listbox(presets,height=4,exportselection=False,
                                bg="#16243A",fg="#E7F1FF",selectbackground="#25456A",
                                selectforeground="#36D3FF",borderwidth=0,highlightthickness=0)
        self.phrases.pack(fill="x",pady=5)
        self.phrases.bind("<<ListboxSelect>>",self._choose_phrase)
        rotate=ttk.Frame(presets);rotate.pack(fill="x")
        self.osc_interval=tk.IntVar(value=10)
        ttk.Label(rotate,text="Rotate every (sec)").pack(side="left")
        ttk.Spinbox(rotate,from_=5,to=3600,textvariable=self.osc_interval,width=6).pack(side="left",padx=(5,10))
        ttk.Button(rotate,text="Start Saved Phrase Rotation",command=self._rotation_start).pack(side="left",padx=(0,9))
        ttk.Button(rotate,text="Stop Rotation",command=self._rotation_stop).pack(side="left")
        height=ttk.LabelFrame(panel,text="Avatar Eye Height",padding=9);height.pack(fill="x",pady=6)
        ttk.Button(height,text="0.8 m",command=lambda:self._height(0.8)).pack(side="left",padx=5)
        ttk.Button(height,text="2.0 m",command=lambda:self._height(2.0)).pack(side="left",padx=5)
        self.osc_height=tk.StringVar(value="1.65")
        ttk.Entry(height,textvariable=self.osc_height,width=8).pack(side="left",padx=8)
        ttk.Button(height,text="Set Custom Height",command=self._custom_height).pack(side="left")
        ttk.Button(height,text="Save My Height",
                   command=self._save_height).pack(side="left",padx=(8,0))
        params=ttk.Frame(panel);params.pack(fill="x",pady=7)
        ttk.Label(params,text="Avatar parameter").pack(side="left")
        self.param_name=tk.StringVar()
        ttk.Entry(params,textvariable=self.param_name,width=20).pack(side="left",padx=(6,10))
        self.param_type=tk.StringVar(value="bool")
        ttk.Combobox(params,textvariable=self.param_type,values=("bool","int","float"),
                     state="readonly",width=8).pack(side="left")
        self.param_value=tk.StringVar(value="true")
        ttk.Entry(params,textvariable=self.param_value,width=8).pack(side="left",padx=6)
        ttk.Button(params,text="Send Parameter",command=self._parameter).pack(side="left")
        mic=ttk.Frame(panel);mic.pack(anchor="w",pady=5)
        ttk.Label(mic,text="Push-to-talk:").pack(side="left")
        ptt=ttk.Button(mic,text="HOLD TO TALK")
        ptt.pack(side="left",padx=6)
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
            self._eligible(personal=True)
            ctrl,pid=self._active_osc()
            phrases=[text for name,text in self.presets.list(self.account.user_id)][:8]
            ctrl.set_rotation(pid,phrases,int(self.osc_interval.get()))
            self.osc_note.set("Rotating approved saved chatboxes on the selected VRChat PID")
        except Exception as exc:self._notice(exc)

    def _rotation_stop(self):
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
                self.controls.release_if_overdue(300)
                self.controls.rotate_tick()
            except Exception as exc:
                self._notice("OSC safety action: "+str(exc))
        self.root.after(1000,self._osc_tick)

    def close(self):
        self._closed=True
        if self.controls is not None:
            self.controls.shutdown()
