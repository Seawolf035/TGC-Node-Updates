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
        tab=ttk.Frame(book,padding=14)
        book.add(tab,text="Profiles")
        ttk.Label(tab,text="VRChat Profiles & Details",font=("Segoe UI",15,"bold")).pack(anchor="w")
        ttk.Label(tab,text="View a selected user's profile, world or group from Social. "
                  "Personal friendship actions require explicit confirmation.",
                  wraplength=850).pack(anchor="w",pady=(4,8))
        line=ttk.Frame(tab);line.pack(fill="x",pady=3)
        ttk.Label(line,text="VRChat ID").pack(side="left",padx=(0,7))
        self.profile_id=tk.StringVar()
        ttk.Entry(line,textvariable=self.profile_id,width=48).pack(side="left",fill="x",expand=True)
        ttk.Button(line,text="Load Details",command=self.load).pack(side="left",padx=7)
        actions=ttk.Frame(tab);actions.pack(anchor="w",pady=9)
        ttk.Button(actions,text="Send Friend Request",command=self.friend_request).pack(side="left",padx=(0,8))
        ttk.Button(actions,text="Request Invite",command=self.request_invite).pack(side="left",padx=(0,8))
        ttk.Button(actions,text="Check Friendship",command=self.friend_status).pack(side="left")
        self.profile_status=tk.StringVar(value="Choose an entry in Social or paste a VRChat ID.")
        ttk.Label(tab,textvariable=self.profile_status,wraplength=860).pack(anchor="w",pady=5)
        self.details=tk.Text(tab,height=10,wrap="word",state="disabled",
                             bg="#16243A",fg="#E7F1FF",insertbackground="#E7F1FF")
        self.details.pack(fill="both",expand=True,pady=5)
        row=ttk.Frame(tab);row.pack(fill="x",pady=6)
        ttk.Label(row,text="Private VRChat note (1–2000 chars):").pack(side="left")
        ttk.Button(row,text="Save Note to VRChat",command=self.save_note).pack(side="right")
        self.note=tk.Text(tab,height=4,wrap="word",bg="#16243A",fg="#E7F1FF",
                          insertbackground="#E7F1FF")
        self.note.pack(fill="x")

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
        # Display public/profile fields without exposing cookies, auth or API tokens.
        lines=[]
        for key in ("displayName","name","id","bio","status","statusDescription",
                    "location","date_joined","dateJoined","visits","favorites",
                    "capacity","authorName","shortCode","memberCount",
                    "description","tags"):
            value=record.get(key)
            if isinstance(value,(str,int,float,bool)) and value not in ("",None):
                lines.append(f"{key}: {str(value)[:1600]}")
        self.details.config(state="normal")
        self.details.delete("1.0",tk.END)
        self.details.insert("1.0","\n".join(lines) or "No public fields returned")
        self.details.config(state="disabled")

    def load(self):
        identifier=self.profile_id.get().strip()
        def work():
            self.pages._eligible(personal=True)
            if identifier.startswith("usr_"):return self.pages.account.profile(identifier)
            if identifier.startswith("wrld_"):return self.pages.account.world(identifier)
            if identifier.startswith("grp_"):return self.pages.account.group(identifier)
            raise VRChatApiError("Profile ID must begin usr_, wrld_ or grp_")
        def done(result):
            self._details=result
            self._show(result)
            self.profile_status.set(f"Loaded {result.get('name') or result.get('displayName') or identifier}")
        self.owner._run_async(work,done)

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
