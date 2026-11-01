"""Windows TGC Co-Companion dark interface (pure ttk, no external theme)."""
from __future__ import annotations

from tkinter import ttk

BG = "#0B1424"
SURFACE = "#16243A"
RAISED = "#233553"
TEXT = "#E7F1FF"
MUTED = "#A6BBD1"
ACCENT = "#36D3FF"
HOVER = "#25456A"


def apply_desktop_theme(root):
    root.configure(background=BG)
    style=ttk.Style(root)
    style.theme_use("clam")
    style.configure(".",font=("Segoe UI",10),background=BG,foreground=TEXT)
    style.configure("TFrame",background=BG)
    style.configure("TLabelframe",background=BG,foreground=TEXT,bordercolor=RAISED)
    style.configure("TLabelframe.Label",background=BG,foreground=ACCENT,
                    font=("Segoe UI",10,"bold"))
    style.configure("TLabel",background=BG,foreground=TEXT)
    style.configure("TNotebook",background=BG,foreground=TEXT,
                    borderwidth=0,tabmargins=(2,4,2,0))
    style.configure("TNotebook.Tab",padding=(12,9),background=SURFACE,
                    foreground=MUTED,font=("Segoe UI",10))
    style.map("TNotebook.Tab",
              background=[("selected",RAISED),("active",HOVER)],
              foreground=[("selected",ACCENT),("active",TEXT)])
    style.configure("TButton",background=RAISED,foreground=TEXT,
                    padding=(11,7),borderwidth=1,bordercolor=RAISED)
    style.map("TButton",background=[("active",HOVER),("disabled",SURFACE)],
              foreground=[("disabled","#728398"),("active",ACCENT)])
    style.configure("TEntry",fieldbackground=SURFACE,foreground=TEXT,
                    insertcolor=TEXT,bordercolor=RAISED)
    style.configure("TCombobox",fieldbackground=SURFACE,foreground=TEXT,
                    background=SURFACE,arrowcolor=ACCENT)
    style.map("TCombobox",fieldbackground=[("readonly",SURFACE)],
              foreground=[("readonly",TEXT)])
    style.configure("TSpinbox",fieldbackground=SURFACE,foreground=TEXT,
                    arrowsize=12)
    style.configure("TSeparator",background=RAISED)
    style.configure("Treeview",background=SURFACE,foreground=TEXT,
                    fieldbackground=SURFACE,rowheight=27,borderwidth=0)
    style.map("Treeview",background=[("selected",HOVER)],
              foreground=[("selected",ACCENT)])
    style.configure("Treeview.Heading",background=RAISED,foreground=TEXT,
                    font=("Segoe UI",10,"bold"),padding=(8,7))
    style.configure("Vertical.TScrollbar",background=RAISED,
                    troughcolor=SURFACE,arrowcolor=ACCENT)
