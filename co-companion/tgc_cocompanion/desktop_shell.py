"""v0.4 owner-only desktop navigation shell for the existing Co-Companion pages.

No VRChat/Relay calls. Rehosts existing tabs without changing their behavior.
1280x720-friendly sidebar, command palette (Ctrl+K), page search, pinning,
keyboard shortcuts, status rail and optional compact mode.
"""
from __future__ import annotations
import tkinter as tk
from tkinter import ttk, messagebox

from .desktop_navigation import NavigationState, search_pages
from .desktop_theme import BG, SURFACE, RAISED, ACCENT, TEXT, MUTED

GROUPS = (
    ("OVERVIEW", ("Home",)),
    ("TGC & PCVR", ("Verification", "VRChat Launcher", "Tracking Diagnostics")),
    ("SOCIAL & STAFFDEX", (
        "VRChat Account", "Explore", "Friends Hub", "Social", "Recent Players",
        "Profiles", "Presence", "Avatars", "Bookmarks", "Inbox", "StaffDex")),
    ("STUDIO & SETTINGS", ("OSC", "OSC Studio", "Staff Chatboxes", "UI Color Studio", "Updates", "Settings")),
)
DESCRIPTIONS = {
    "Home": "Your launchpad and live account overview",
    "Verification": "Owner pilot and TGC access",
    "VRChat Launcher": "Manage your authorized VRChat sessions",
    "Tracking Diagnostics": "Read-only local log health",
    "VRChat Account": "Personal account login and authentication",
    "Social": "Friends, groups, worlds and search",
    "Friends Hub": "Pinned friends, shared instances and group shortcuts",
    "Explore": "Discover VRChat people, worlds and groups",
    "Recent Players": "Locally scan recent PCVR encounters on demand",
    "Staff Chatboxes": "Official Mobile Companion staff OSC phrases",
    "Profiles": "People, world and group details",
    "Presence": "Your VRChat status",
    "Avatars": "Your avatars and favorites",
    "Bookmarks": "Saved worlds on this Windows account",
    "Inbox": "Personal VRChat notifications",
    "StaffDex": "Friendship collection and achievements",
    "OSC": "Targeted session OSC controls",
    "OSC Studio": "Chatbox, avatar height and voice controls",
    "UI Color Studio": "Advanced UI colors and VRChat palette import/export",
    "Updates": "Verified EXE updates and rollback",
    "Settings": "Navigation preferences and recovery tools",
}


class DesktopShell:
    def __init__(self, root, data_directory, status_var):
        self.root = root
        self.state = NavigationState(data_directory)
        self.status_var = status_var
        self.page_names = []
        self._rendering = False
        self._palette = None
        self.page_select = tk.StringVar(value="Home")
        self.search_var = tk.StringVar()
        self.heading_var = tk.StringVar(value="HOME")
        self.description_var = tk.StringVar(value=DESCRIPTIONS["Home"])
        self.pin_var = tk.StringVar(value="Pin page")
        self.pinned_count = tk.StringVar(value="")
        self.root.title("TGC Co-Companion | Owner Preview v0.7.1")
        self.root.geometry("1180x690")
        self.root.minsize(900, 540)

        self.frame = ttk.Frame(root, style="Shell.TFrame")
        self.frame.pack(fill="both", expand=True)
        header = ttk.Frame(self.frame, style="Header.TFrame", padding=(18, 10))
        header.pack(fill="x")
        brand = ttk.Frame(header, style="Header.TFrame")
        brand.pack(side="left")
        ttk.Label(brand, text="TGC  /  CO-COMPANION",
                  style="Brand.TLabel").pack(anchor="w")
        ttk.Label(brand, text="WINDOWS OWNER PREVIEW    •    v0.7.1",
                  style="BrandHint.TLabel").pack(anchor="w")
        ttk.Button(header, text="Find Page  Ctrl+K", style="Accent.TButton",
                   command=self.show_palette).pack(side="right", padx=(9, 0))
        ttk.Button(header, text="Updates", command=lambda:
                   self.open_page("Updates")).pack(side="right", padx=(9, 0))
        ttk.Button(header, text="Home", command=lambda:
                   self.open_page("Home")).pack(side="right")

        body = ttk.Frame(self.frame, style="Shell.TFrame")
        body.pack(fill="both", expand=True)
        self.sidebar = ttk.Frame(body, style="Sidebar.TFrame", width=218)
        self.sidebar.pack(side="left", fill="y")
        self.sidebar.pack_propagate(False)
        top = ttk.Frame(self.sidebar, style="Sidebar.TFrame", padding=(11, 10))
        top.pack(fill="x")
        ttk.Button(top, text="☰", width=3, command=self.toggle_compact).pack(side="left")
        self.sidebar_hint = ttk.Label(top, text="NAVIGATION",
                                      style="SideHint.TLabel")
        self.sidebar_hint.pack(side="left", padx=9)
        self.search_entry = ttk.Entry(self.sidebar, textvariable=self.search_var)
        self.search_entry.pack(fill="x", padx=11, pady=(0, 8))
        self.search_var.trace_add("write", lambda *_: self.render_sidebar())
        listbox = ttk.Frame(self.sidebar, style="Sidebar.TFrame")
        listbox.pack(fill="both", expand=True)
        self.sidebar_canvas = tk.Canvas(listbox, background=SURFACE,
                     highlightthickness=0, borderwidth=0, width=205)
        scroll = ttk.Scrollbar(listbox, orient="vertical",
                               command=self.sidebar_canvas.yview)
        self.sidebar_canvas.configure(yscrollcommand=scroll.set)
        scroll.pack(side="right", fill="y")
        self.sidebar_canvas.pack(side="left", fill="both", expand=True)
        self.nav = ttk.Frame(self.sidebar_canvas, style="Sidebar.TFrame")
        self.nav_window = self.sidebar_canvas.create_window(
            (0, 0), window=self.nav, anchor="nw")
        self.nav.bind("<Configure>", lambda _:
              self.sidebar_canvas.configure(scrollregion=
                                             self.sidebar_canvas.bbox("all")))
        self.sidebar_canvas.bind("<Configure>", self._fit_sidebar)
        self.sidebar_canvas.bind("<MouseWheel>", lambda e:
              self.sidebar_canvas.yview_scroll(-int(e.delta/120), "units"))
        self.sidebar_canvas.bind("<Button-4>", lambda _: self.sidebar_canvas.yview_scroll(-1, "units"))
        self.sidebar_canvas.bind("<Button-5>", lambda _: self.sidebar_canvas.yview_scroll(1, "units"))

        main = ttk.Frame(body, style="Shell.TFrame", padding=(12, 8, 12, 10))
        main.pack(side="left", fill="both", expand=True)
        banner = ttk.Frame(main, style="Shell.TFrame")
        banner.pack(fill="x", pady=(0, 9))
        titles = ttk.Frame(banner, style="Shell.TFrame")
        titles.pack(side="left", fill="x", expand=True)
        ttk.Label(titles, textvariable=self.heading_var,
                  style="PageTitle.TLabel").pack(anchor="w")
        ttk.Label(titles, textvariable=self.description_var,
                  style="PageSubtitle.TLabel").pack(anchor="w")
        self.pin_button = ttk.Button(banner, textvariable=self.pin_var,
                                    command=self.toggle_pin)
        self.pin_button.pack(side="right", padx=(8, 0))
        self.page_picker = ttk.Combobox(banner, textvariable=self.page_select,
                         state="readonly", width=19)
        self.page_picker.pack(side="right")
        self.page_picker.bind("<<ComboboxSelected>>",
                              lambda _: self.open_page(self.page_select.get()))
        self.notebook = ttk.Notebook(main, style="Shell.TNotebook")
        self.notebook.pack(fill="both", expand=True)

        foot = ttk.Frame(self.frame, style="Footer.TFrame", padding=(15, 7))
        foot.pack(fill="x", side="bottom")
        ttk.Label(foot, text="OWNER-ONLY  •  SINGLE TGC RELAY  •  NO NODE UPLOADER",
                  style="FooterHint.TLabel").pack(side="left")
        ttk.Label(foot, textvariable=status_var, style="FooterStatus.TLabel",
                  wraplength=670).pack(side="right", padx=(10, 0))
        root.bind("<Control-k>", self._shortcut_palette)
        root.bind("<Control-f>", self._shortcut_search)
        for n in range(1, 10):
            root.bind(f"<Control-Key-{n}>",
                      lambda _, index=n-1: self.shortcut_page(index))
        root.bind("<Alt-Left>", self._shortcut_previous)
        self._apply_compact()

    def _fit_sidebar(self, event):
        self.sidebar_canvas.itemconfigure(self.nav_window, width=max(1, event.width))

    def register_pages(self):
        self.page_names = [self.notebook.tab(tab, "text")
                           for tab in self.notebook.tabs()]
        self.state.restrict(self.page_names)
        self.page_picker.configure(values=self.page_names)
        self.notebook.bind("<<NotebookTabChanged>>", self._tab_changed)
        self.open_page("Home" if self.state.open_at_home else self.state.last_page)
        self.render_sidebar()

    def open_page(self, title):
        if title not in self.page_names:
            return False
        self.notebook.select(self.notebook.tabs()[self.page_names.index(title)])
        self._tab_changed()
        return True

    def _tab_changed(self, _event=None):
        if not self.page_names:
            return
        try:
            title = self.notebook.tab(self.notebook.select(), "text")
        except (tk.TclError, ValueError):
            return
        self.page_select.set(title)
        self.heading_var.set(title.upper())
        self.description_var.set(DESCRIPTIONS.get(title, "Co-Companion tools"))
        self.pin_var.set("Unpin page" if title in self.state.pinned else "Pin page")
        if title != self.state.last_page or not self.state.recent:
            self.state.visit(title)
            self.render_sidebar()

    def toggle_pin(self):
        title = self.page_select.get()
        if title not in self.page_names:
            return
        try:
            self.state.toggle_pin(title)
        except ValueError as exc:
            messagebox.showinfo("Pinned pages", str(exc), parent=self.root)
            return
        self.pin_var.set("Unpin page" if title in self.state.pinned else "Pin page")
        self.render_sidebar()

    def render_sidebar(self):
        if self._rendering or not self.page_names:
            return
        self._rendering = True
        try:
            for child in self.nav.winfo_children():
                child.destroy()
            text = self.search_var.get().strip()
            visible = search_pages(self.page_names, text)
            if self.state.compact:
                from .desktop_compact_nav import build_rail
                build_rail(self.nav, self.state.pinned, self.page_select.get(),
                           self.page_names, self.open_page, self.show_palette)
            else:
                if not text and self.state.pinned:
                    self._section("PINNED")
                    for title in self.state.pinned:
                        self._nav_button(title, star=True)
                if text:
                    self._section(f"RESULTS ({len(visible)})")
                    for title in visible:
                        self._nav_button(title)
                    if not visible:
                        ttk.Label(self.nav, text="No matching pages",
                                  style="SideHint.TLabel").pack(padx=10, pady=10)
                else:
                    for section, pages in GROUPS:
                        names = [x for x in pages if x in self.page_names]
                        if names:
                            self._section(section)
                            for title in names:
                                self._nav_button(title)
                    remaining = [x for x in self.page_names
                                 if all(x not in pages for _, pages in GROUPS)]
                    if remaining:
                        self._section("OTHER")
                        for title in remaining:
                            self._nav_button(title)
        finally:
            self._rendering = False

    def _section(self, text):
        ttk.Label(self.nav, text=text, style="SideHint.TLabel").pack(
            fill="x", padx=13, pady=(13, 4))

    def _nav_button(self, title, star=False):
        active = title == self.page_select.get()
        ttk.Button(self.nav, text=("★  " if star else "     ") + title,
                   style="NavSelected.TButton" if active else "Nav.TButton",
                   command=lambda name=title: self.open_page(name)).pack(
                   fill="x", padx=6, pady=1)

    def _apply_compact(self):
        self.sidebar.configure(width=74 if self.state.compact else 230)
        self.sidebar_canvas.configure(width=56 if self.state.compact else 207)
        if self.state.compact:
            self.sidebar_hint.pack_forget()
            self.search_entry.pack_forget()
        else:
            if not self.sidebar_hint.winfo_manager():
                self.sidebar_hint.pack(side="left", padx=9)
            if not self.search_entry.winfo_manager():
                self.search_entry.pack(fill="x", padx=11, pady=(0, 8),
                                       before=self.sidebar_canvas.master)
        self.render_sidebar()

    def toggle_compact(self):
        self.state.compact = not self.state.compact
        self.state.save()
        self._apply_compact()

    def shortcut_page(self, index):
        shortcuts = ("Home", "Social", "VRChat Launcher", "StaffDex",
                     "OSC Studio", "Profiles", "Inbox", "Bookmarks", "Updates")
        if index < len(shortcuts):
            return self.open_page(shortcuts[index])
        return False

    def _shortcut_previous(self, _event=None):
        if len(self.state.recent) >= 2:
            self.open_page(self.state.recent[1])
        return "break"

    def _shortcut_palette(self, _event=None):
        self.show_palette()
        return "break"

    def _shortcut_search(self, _event=None):
        if self.state.compact:
            self.show_palette()
        else:
            self.search_entry.focus_set()
            self.search_entry.select_range(0, "end")
        return "break"

    def show_palette(self):
        if self._palette is not None and self._palette.winfo_exists():
            self._palette.lift()
            return
        win = tk.Toplevel(self.root)
        self._palette = win
        win.title("Find Co-Companion Page  ·  Ctrl+K")
        win.geometry("475x435")
        win.transient(self.root)
        win.configure(background=BG)
        ttk.Label(win, text="JUMP TO A PAGE",
                  style="PageTitle.TLabel").pack(anchor="w", padx=17, pady=(16, 7))
        query = tk.StringVar()
        entry = ttk.Entry(win, textvariable=query)
        entry.pack(fill="x", padx=17, pady=(0, 10))
        matches = tk.Listbox(win, bg=SURFACE, fg=TEXT, selectbackground=RAISED,
                     selectforeground=ACCENT, highlightthickness=0,
                     borderwidth=0, font=("Segoe UI", 11))
        matches.pack(fill="both", expand=True, padx=17, pady=(0, 12))

        def rebuild(*_):
            matches.delete(0, tk.END)
            for name in search_pages(self.page_names, query.get()):
                matches.insert(tk.END, name)
            if matches.size():
                matches.selection_set(0)

        def choose(_event=None):
            chosen = matches.curselection()
            if chosen:
                self.open_page(matches.get(chosen[0]))
                win.destroy()
            return "break"
        query.trace_add("write", rebuild)
        entry.bind("<Return>", choose)
        entry.bind("<Down>", lambda _: (matches.focus_set(), "break")[1])
        matches.bind("<Return>", choose)
        matches.bind("<Double-Button-1>", choose)
        win.bind("<Escape>", lambda _: win.destroy())
        rebuild()
        entry.focus_set()
