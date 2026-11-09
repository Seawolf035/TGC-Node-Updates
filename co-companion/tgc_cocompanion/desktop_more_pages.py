"""Windows profile, notes, world/group detail and personal presence pages.

Every write requires explicit user action/confirmation, an active owner pilot
and a separate personal VRChat login. API calls are personal VRChat only:
NEVER affect TGC Invite Network, group moderation or Relay.
"""
from __future__ import annotations

import tkinter as tk
from tkinter import messagebox, ttk
from urllib.parse import urlsplit

from .vrchat_personal import USER_ID, VRChatApiError


class ProfileAndPresence:
    def __init__(self, pages, book):
        self.pages=pages
        self.owner=pages.owner
        self.book=book
        self._details=None
        self._build_details(book)
        self._build_presence(book)

    def _build_details(self,book):
        from .desktop_profile_view import VisualProfile
        tab=ttk.Frame(book,padding=(10,8))
        book.add(tab,text="Profiles")
        self.profile_status=tk.StringVar(value="Choose a profile from Explore, Social, StaffDex or enter a VRChat ID.")
        bar=ttk.Frame(tab)
        bar.pack(fill="x",pady=(0,8))
        ttk.Label(bar,text="VRChat profile ID:").pack(side="left",padx=(0,8))
        self.profile_id=tk.StringVar()
        entry=ttk.Entry(bar,textvariable=self.profile_id)
        entry.pack(side="left",fill="x",expand=True,padx=(0,7))
        entry.bind("<Return>",lambda _event:self.load())
        ttk.Button(bar,text="Load Profile",style="Accent.TButton",
                   command=self.load).pack(side="left",padx=(0,6))
        ttk.Button(bar,text="Copy ID",command=self.copy_profile_id).pack(side="left")
        self.person_actions=ttk.Frame(tab)
        self.person_actions.pack(fill="x",pady=(0,7))
        for title,action in (
            ("Check Friendship",self.friend_status),
            ("Send Friend Request",self.friend_request),
            ("Request Invite",self.request_invite),
        ):
            ttk.Button(self.person_actions,text=title,command=action).pack(
                side="left",padx=(0,7),pady=1)
        ttk.Label(tab,textvariable=self.profile_status,
                  style="PageSubtitle.TLabel",wraplength=850).pack(
                      fill="x",pady=(0,5))
        self.note_frame=ttk.Frame(tab)
        self.note_frame.pack(fill="x",side="bottom",pady=(8,0))
        header=ttk.Frame(self.note_frame)
        header.pack(fill="x",pady=(0,4))
        ttk.Label(header,text="PRIVATE VRCHAT NOTE  •  1–2000 characters",
                  style="PageSubtitle.TLabel").pack(side="left")
        ttk.Button(header,text="Save Note",command=self.save_note
                   ).pack(side="right")
        self.note=tk.Text(self.note_frame,height=3,wrap="word",relief="flat",
                          background="#11243B",foreground="#EAF5FF",
                          insertbackground="#EAF5FF",padx=9,pady=6)
        self.note.pack(fill="x")
        self.visual=VisualProfile(self.owner,tab)
        self.details=self.visual.bio
        self._active_profile_id=None

    def copy_profile_id(self):
        identifier=self.profile_id.get().strip()
        if not identifier:
            self.profile_status.set("No VRChat profile ID to copy.")
            return
        self.owner.root.clipboard_clear()
        self.owner.root.clipboard_append(identifier)
        self.profile_status.set("Profile ID copied.")

    def _build_presence(self,book):
        tab=ttk.Frame(book,padding=16)
        book.add(tab,text="Presence")
        ttk.Label(tab,text="Your VRChat Presence",font=("Segoe UI",15,"bold")).pack(anchor="w")
        ttk.Label(tab,text="Updates your signed-in personal VRChat account, not TGC staff status. "
                  "This page never edits other users or the TGC Relay.",
                  wraplength=820).pack(anchor="w",pady=(3,16))
        self.presence=tk.StringVar(value="active")
        ttk.Label(tab,text="Presence status").pack(anchor="w")
        ttk.Combobox(tab,textvariable=self.presence,width=24,state="readonly",
                     values=("active","ask me","busy","join me")).pack(anchor="w",pady=7)
        self.message=tk.StringVar()
        ttk.Label(tab,text="Status message (up to 32 characters)").pack(anchor="w")
        ttk.Entry(tab,textvariable=self.message,width=50).pack(anchor="w",pady=8)
        ttk.Button(tab,text="Update My VRChat Presence",
                   command=self.update_presence).pack(anchor="w",pady=8)
        ttk.Button(tab,text="Reload My Profile",command=self.refresh_self).pack(anchor="w")
        self.presence_status=tk.StringVar(value="Sign in and verify your owner pilot to update presence.")
        ttk.Label(tab,textvariable=self.presence_status,wraplength=830).pack(anchor="w",pady=13)

    def _require_user(self):
        self.pages._eligible(personal=True)
        ident=self.profile_id.get().strip()
        if not USER_ID.fullmatch(ident):
            raise ValueError("Select a valid VRChat user ID")
        return ident

    def set_selected(self,identifier):
        self.profile_id.set(str(identifier))
        self.book.select(next(tab for tab in self.book.tabs()
                              if self.book.tab(tab,"text")=="Profiles"))
        self.load()

    def _show(self,record):
        from .desktop_profile_view import profile_kind
        self.visual.update(record,profile_kind(record.get("id") or self.profile_id.get().strip()))

    def load(self):
        from .desktop_profile_view import profile_kind
        identifier=self.profile_id.get().strip()
        if not identifier:
            self.profile_status.set("Enter a VRChat user, world, or group ID.")
            return
        current_kind=profile_kind(identifier)
        if not identifier.startswith(("usr_","wrld_","grp_")):
            self.profile_status.set("Use a valid usr_, wrld_, or grp_ VRChat ID.")
            return
        self.profile_status.set("Loading VRChat profile and artwork…")
        def work():
            self.pages._eligible(personal=True)
            if identifier.startswith("usr_"):
                return self.pages.account.profile(identifier)
            if identifier.startswith("wrld_"):
                return self.pages.account.world(identifier)
            return self.pages.account.group(identifier)
        def done(result):
            if self.profile_id.get().strip()!=identifier:
                return
            self._active_profile_id=identifier
            self._details=result
            self.visual.update(result,current_kind)
            if current_kind=="user":
                if not self.person_actions.winfo_manager():
                    self.person_actions.pack(fill="x",before=self.visual.container,pady=(0,7))
                if not self.note_frame.winfo_manager():
                    self.note_frame.pack(fill="x",side="bottom",pady=(8,0))
            else:
                self.person_actions.pack_forget()
                self.note_frame.pack_forget()
            self.note.delete("1.0",tk.END)
            title=result.get("displayName") or result.get("name") or identifier
            self.profile_status.set("Loaded "+str(title)[:130]+
                                    " • Artwork loads when available from VRChat.")
        def failure(message):
            if self.profile_id.get().strip()==identifier:
                self.profile_status.set("Could not load profile: "+str(message))
        self.owner._run_async(work,done,on_error=failure)

    def friend_status(self):
        try:ident=self._require_user()
        except Exception as exc:self.pages._notice(exc);return
        def done(value):
            self.profile_status.set("VRChat friendship state: "+str(value)[:380])
        self.owner._run_async(lambda:self.pages.account.friend_status(ident),done)

    def friend_request(self):
        try:ident=self._require_user()
        except Exception as exc:self.pages._notice(exc);return
        if not messagebox.askyesno("Confirm Friend Request",
                "Send a personal VRChat friend request to this exact account?\n"+ident):
            return
        def done(_):
            self.profile_status.set("Personal VRChat friend request submitted to "+ident)
        self.owner._run_async(lambda:self.pages.account.send_friend_request(ident),done)

    def request_invite(self):
        try:ident=self._require_user()
        except Exception as exc:self.pages._notice(exc);return
        if not messagebox.askyesno("Confirm Invite Request",
                "Ask this VRChat user to invite your personal account?\n"+ident):
            return
        self.owner._run_async(lambda:self.pages.account.request_invite(ident),
                              lambda _:self.profile_status.set("Invite requested from "+ident))

    def save_note(self):
        try:ident=self._require_user()
        except Exception as exc:self.pages._notice(exc);return
        note=self.note.get("1.0","end-1c").strip()
        if not 1<=len(note)<=2000:
            self.pages._notice("VRChat note requires 1–2000 characters")
            return
        if not messagebox.askyesno("Save Personal VRChat Note",
                "Save this private note to your PERSONAL VRChat account for\n"+ident+"?"):
            return
        self.owner._run_async(lambda:self.pages.account.save_note(ident,note),
                              lambda _:self.profile_status.set("Private VRChat note saved"))

    def update_presence(self):
        try:self.pages._eligible(personal=True)
        except Exception as exc:self.pages._notice(exc);return
        state=self.presence.get()
        message=self.message.get()
        if len(message)>32:
            self.pages._notice("Status message must be at most 32 characters")
            return
        if not messagebox.askyesno("Update VRChat Presence",
                "Update your PERSONAL VRChat profile status to "+state+"?"):
            return
        self.owner._run_async(lambda:self.pages.account.update_presence(state,message),
                              lambda _:self.presence_status.set("Personal VRChat presence updated"))

    def refresh_self(self):
        def work():
            self.pages._eligible(personal=True)
            return self.pages.account.current_user()
        def done(profile):
            self.presence.set(str(profile.get("status") or "active"))
            self.message.set(str(profile.get("statusDescription") or "")[:32])
            self.presence_status.set("Loaded your personal VRChat profile status.")
        self.owner._run_async(work,done)
