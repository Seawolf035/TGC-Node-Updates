"""Responsive visual profile card for user, world and group details.

All content is sourced from the authenticated personal VRChat profile API;
image loading is unauthenticated, bounded, opt-in only by navigating to a
profile. No external webviews, client secrets or Relay operations.
"""
from __future__ import annotations

from concurrent.futures import ThreadPoolExecutor
import tkinter as tk
from tkinter import ttk

from .desktop_profile_images import ProfileImageCache, image_sources, render_image

DETAILS = {
    "user": (
        ("Member since",("date_joined","dateJoined")),
        ("VRChat status",("status",)),
        ("Location",("location",)),
        ("Pronouns",("pronouns",)),
        ("Trust level",("tags",)),
        ("Age verified",("ageVerificationStatus",)),
    ),
    "world": (
        ("Creator",("authorName",)),
        ("Visits",("visits",)),
        ("Favorites",("favorites",)),
        ("Capacity",("capacity",)),
        ("Updated",("updated_at","updatedAt")),
        ("Release",("releaseStatus",)),
    ),
    "group": (
        ("Members",("memberCount",)),
        ("Owner",("ownerDisplayName","ownerId")),
        ("Visibility",("joinState",)),
        ("Type",("accessType",)),
        ("Created",("created_at",)),
        ("Language",("languages",)),
    ),
}
IMAGE_POOL=ThreadPoolExecutor(max_workers=2,thread_name_prefix="CoCompanionProfileArt")
CACHE=ProfileImageCache()


def profile_kind(identifier):
    if isinstance(identifier,str):
        for prefix,name in (("usr_","user"),("wrld_","world"),("grp_","group")):
            if identifier.startswith(prefix):
                return name
    return "user"


def readable(value,max_length=180):
    if value is None or value=="":
        return "Not provided"
    if isinstance(value,bool):
        return "Yes" if value else "No"
    if isinstance(value,(list,tuple)):
        return ", ".join(str(x) for x in value[:8])[:max_length] or "Not provided"
    if isinstance(value,dict):
        return "Available" if value else "Not provided"
    return str(value).strip()[:max_length] or "Not provided"


def profile_summary(record,kind):
    if not isinstance(record,dict):
        return "Unknown", "Not available", [], ""
    label=(record.get("displayName") if kind=="user" else record.get("name"))
    title=readable(label or record.get("name") or record.get("displayName") or record.get("id"))
    subtitle=(record.get("statusDescription") if kind=="user" else
              record.get("authorName") if kind=="world" else record.get("shortCode"))
    cards=[]
    for heading,keys in DETAILS.get(kind,DETAILS["user"]):
        val=next((record.get(k) for k in keys if record.get(k) not in (None,"")),None)
        cards.append((heading,readable(val,140)))
    about=record.get("bio") if kind=="user" else record.get("description")
    return title,readable(subtitle,140),cards,readable(about,10000)


class ScrollableCard(ttk.Frame):
    """Canvas scroll with auto width, no hard minheight and pointer-aware wheel."""
    def __init__(self,parent):
        super().__init__(parent)
        self.canvas=tk.Canvas(self,highlightthickness=0,borderwidth=0,
                              bg="#081320")
        self.scroll=ttk.Scrollbar(self,orient="vertical",command=self.canvas.yview)
        self.canvas.configure(yscrollcommand=self.scroll.set)
        self.scroll.pack(side="right",fill="y")
        self.canvas.pack(side="left",fill="both",expand=True)
        self.content=ttk.Frame(self.canvas)
        self.window=self.canvas.create_window((0,0),window=self.content,anchor="nw")
        self.content.bind("<Configure>",self._content_changed)
        self.canvas.bind("<Configure>",self._resize)
        # Mouse-wheel events go to the actual label/card under the pointer,
        # not automatically to its parent Canvas. Route them here only if
        # the pointer is over this visible ScrollableCard (not the sidebar).
        self.canvas.bind_all("<MouseWheel>",self._wheel_if_inside,add="+")
        self.canvas.bind_all("<Button-4>",self._wheel_if_inside,add="+")
        self.canvas.bind_all("<Button-5>",self._wheel_if_inside,add="+")

    def _content_changed(self,_event=None):
        self.canvas.configure(scrollregion=self.canvas.bbox("all"))

    def _resize(self,event):
        self.canvas.itemconfigure(self.window,width=max(1,event.width))

    def _wheel_if_inside(self,event):
        if not self.winfo_exists() or not self.winfo_ismapped():
            return
        node=event.widget
        while node is not None:
            if node is self:
                steps=(-2 if getattr(event,"num",0)==4 else
                       2 if getattr(event,"num",0)==5 else
                       -int(getattr(event,"delta",0)/120)*2)
                if steps:self.canvas.yview_scroll(steps,"units")
                return "break"
            node=getattr(node,"master",None)

    def top(self):
        self.canvas.yview_moveto(0)


class VisualProfile:
    def __init__(self,owner,parent):
        self.owner=owner
        self.generation=0
        self.kind="user"
        self.data=None
        self.picture=None
        self.banner=None
        self._banner_bytes=None
        self._banner_width=0
        self._banner_timer=None
        self._image_url=None
        self._banner_url=None
        self.container=ScrollableCard(parent)
        self.container.pack(fill="both",expand=True)
        base=self.container.content
        self.banner_widget=tk.Label(base,background="#1D3754",height=7,
                                    borderwidth=0,text="",
                                    font=("Segoe UI",10))
        self.banner_widget.pack(fill="x",padx=2,pady=(2,0))
        identity=ttk.Frame(base,padding=(15,9))
        identity.pack(fill="x")
        self.avatar_widget=tk.Label(identity,background="#182C42",
                                    foreground="#9FB6CA",text="VRChat",
                                    font=("Segoe UI",10,"bold"),width=14,height=6)
        self.avatar_widget.pack(side="left",padx=(0,18))
        identity_text=ttk.Frame(identity)
        identity_text.pack(side="left",fill="both",expand=True)
        self.type=tk.StringVar(value="PERSONAL VRCHAT PROFILE")
        self.title=tk.StringVar(value="Select a profile")
        self.subtitle=tk.StringVar(value="Search by VRChat ID above")
        ttk.Label(identity_text,textvariable=self.type,
                  style="PageSubtitle.TLabel").pack(anchor="w",pady=(4,6))
        self.title_widget=ttk.Label(identity_text,textvariable=self.title,
                                   font=("Segoe UI Semibold",21))
        self.title_widget.pack(anchor="w")
        self.subtitle_widget=ttk.Label(identity_text,textvariable=self.subtitle,
                                      style="PageSubtitle.TLabel",wraplength=520)
        self.subtitle_widget.pack(anchor="w",pady=(4,0))
        self.identity_id=tk.StringVar(value="")
        ttk.Label(identity_text,textvariable=self.identity_id,
                  style="PageSubtitle.TLabel",wraplength=570).pack(anchor="w",pady=(8,0))
        self.meta=ttk.Frame(base,padding=(13,8))
        self.meta.pack(fill="x")
        self.meta_labels=[]
        for i in range(6):
            card=ttk.Frame(self.meta,style="Card.TFrame",padding=(12,11))
            card.grid(row=i//3,column=i%3,sticky="nsew",padx=4,pady=4)
            heading=tk.StringVar()
            val=tk.StringVar()
            ttk.Label(card,textvariable=heading,style="CardLabel.TLabel").pack(anchor="w")
            info=ttk.Label(card,textvariable=val,style="CardValue.TLabel",
                           wraplength=220)
            info.pack(anchor="w",pady=(5,1))
            self.meta_labels.append((heading,val,info))
        for i in range(3):self.meta.columnconfigure(i,weight=1)
        about=ttk.LabelFrame(base,text="ABOUT",padding=(15,10))
        about.pack(fill="x",padx=16,pady=(11,6))
        self.bio=tk.Text(about,height=6,wrap="word",relief="flat",
                         borderwidth=0,highlightthickness=0,
                         background="#11243B",foreground="#EAF5FF",
                         font=("Segoe UI",10),cursor="arrow")
        self.bio.pack(fill="x")
        self.bio.configure(state="disabled")
        self.container.canvas.bind("<Configure>",self._responsive,add="+")
        self.update(None,"user")

    def _responsive(self,event):
        w=max(1,event.width)
        narrow=w<690
        columns=1 if w<510 else 2 if narrow else 3
        for i,(heading,val,label) in enumerate(self.meta_labels):
            widget=label.master
            widget.grid_forget()
            widget.grid(row=i//columns,column=i%columns,sticky="nsew",
                        padx=4,pady=4)
            label.configure(wraplength=max(110,int(w/columns)-65))
        for i in range(3):
            self.meta.columnconfigure(i,weight=1 if i<columns else 0)
        self.title_widget.configure(font=("Segoe UI Semibold",16 if narrow else 21),
                                    wraplength=max(170,w-230))
        self.subtitle_widget.configure(wraplength=max(170,w-230))
        self.banner_widget.configure(height=5 if narrow else 7)
        if self._banner_bytes is not None and abs(self._banner_width-w)>90:
            if self._banner_timer is not None:
                self.container.after_cancel(self._banner_timer)
            self._banner_timer=self.container.after(200,self._resize_banner)

    def _resize_banner(self):
        self._banner_timer=None
        if not self._banner_bytes or not self.container.winfo_exists():
            return
        try:
            from PIL import ImageTk
            target=min(1800,max(320,self.container.canvas.winfo_width()-10))
            art=render_image(self._banner_bytes,(target,176))
            picture=ImageTk.PhotoImage(art,master=self.container)
            self.banner=picture
            self._banner_width=target
            self.banner_widget.configure(image=picture,text="")
        except (tk.TclError,RuntimeError,ValueError,OSError):
            pass

    def _set_bio(self,text):
        self.bio.configure(state="normal")
        self.bio.delete("1.0",tk.END)
        self.bio.insert("1.0",text)
        self.bio.configure(state="disabled")
        lines=min(12,max(3,len(text)//100+2))
        self.bio.configure(height=lines)

    def update(self,record,kind):
        self.generation+=1
        self.kind=kind
        self.data=record
        self.picture=None
        self.banner=None
        self._banner_bytes=None
        self._banner_width=0
        if self._banner_timer is not None:
            self.container.after_cancel(self._banner_timer)
            self._banner_timer=None
        self._image_url=None
        self._banner_url=None
        self.banner_widget.configure(image="",text="")
        self.avatar_widget.configure(image="",text="No artwork")
        title,subtitle,meta,bio=profile_summary(record,kind)
        self.type.set({"user":"VRCHAT USER","world":"VRCHAT WORLD",
                       "group":"VRCHAT GROUP"}.get(kind,"VRCHAT PROFILE"))
        self.title.set(title)
        self.subtitle.set(subtitle)
        self.identity_id.set(readable((record or {}).get("id"),110))
        for (label,val,_), (heading,text) in zip(self.meta_labels,meta):
            label.set(heading.upper())
            val.set(text)
        self._set_bio(bio)
        self.container.top()
        if record:
            self._load_art(record,kind,self.generation)

    def _load_art(self,record,kind,version):
        profile,banner=image_sources(record,kind)
        self._image_url,self._banner_url=profile,banner
        for url,target,size,circle in (
            (banner,"banner",(1000,176),False),
            (profile,"avatar",(112,112),True)):
            if not url:continue
            def work(u=url,t=target,sz=size,rounded=circle):
                try:
                    payload=CACHE.fetch(u)
                    art=render_image(payload,sz,circle=rounded)
                except Exception:
                    return
                def apply():
                    if self.generation!=version or not self.container.winfo_exists():
                        return
                    try:
                        from PIL import ImageTk
                        photo=ImageTk.PhotoImage(art,master=self.container)
                        if t=="banner":
                            self.banner=photo
                            self._banner_bytes=payload
                            self._banner_width=sz[0]
                            self.banner_widget.configure(image=photo,text="")
                            if abs(self.container.canvas.winfo_width()-sz[0])>90:
                                self._resize_banner()
                        else:
                            self.picture=photo
                            self.avatar_widget.configure(image=photo,text="")
                    except (tk.TclError,RuntimeError):
                        pass
                try:self.owner.root.after(0,apply)
                except (tk.TclError,RuntimeError):pass
            IMAGE_POOL.submit(work)
