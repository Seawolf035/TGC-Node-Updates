"""Co-Companion v0.4 owner-only modern dark UI palette, pure ttk / Tk."""
from __future__ import annotations

from tkinter import ttk

BG = "#081320"
SURFACE = "#11243B"
RAISED = "#1D3754"
TEXT = "#EAF5FF"
MUTED = "#9FB6CA"
ACCENT = "#3CD6FF"
HOVER = "#274867"
LINE = "#24435D"
DISABLED = "#6C8093"


def apply_desktop_theme(root):
    root.configure(background=BG)
    style = ttk.Style(root)
    style.theme_use("clam")
    style.configure(".", font=("Segoe UI", 10), background=BG, foreground=TEXT)
    style.configure("TFrame", background=BG)
    style.configure("Shell.TFrame", background=BG)
    style.configure("Header.TFrame", background=SURFACE)
    style.configure("Footer.TFrame", background=SURFACE)
    style.configure("Sidebar.TFrame", background=SURFACE)
    style.configure("Card.TFrame", background=SURFACE)
    style.configure("TLabelframe", background=BG, foreground=TEXT,
                    bordercolor=LINE, relief="flat")
    style.configure("TLabelframe.Label", background=BG, foreground=ACCENT,
                    font=("Segoe UI", 10, "bold"))
    style.configure("TLabel", background=BG, foreground=TEXT)
    style.configure("Brand.TLabel", background=SURFACE, foreground=ACCENT,
                    font=("Segoe UI Semibold", 16))
    style.configure("BrandHint.TLabel", background=SURFACE, foreground=MUTED,
                    font=("Segoe UI", 8))
    style.configure("SideHint.TLabel", background=SURFACE, foreground=MUTED,
                    font=("Segoe UI Semibold", 9))
    style.configure("PageTitle.TLabel", background=BG, foreground=TEXT,
                    font=("Segoe UI Semibold", 17))
    style.configure("PageSubtitle.TLabel", background=BG, foreground=MUTED,
                    font=("Segoe UI", 9))
    style.configure("FooterHint.TLabel", background=SURFACE, foreground=MUTED,
                    font=("Segoe UI", 8))
    style.configure("FooterStatus.TLabel", background=SURFACE, foreground=TEXT,
                    font=("Segoe UI", 9))
    style.configure("CardLabel.TLabel", background=SURFACE, foreground=MUTED,
                    font=("Segoe UI Semibold", 10))
    style.configure("CardValue.TLabel", background=SURFACE, foreground=TEXT,
                    font=("Segoe UI", 10))
    style.configure("CardAccent.TLabel", background=SURFACE, foreground=ACCENT,
                    font=("Segoe UI Semibold", 14))
    style.configure("TNotebook", background=BG, foreground=TEXT,
                    borderwidth=0, tabmargins=(2, 4, 2, 0))
    style.configure("TNotebook.Tab", padding=(12, 9), background=SURFACE,
                    foreground=MUTED, font=("Segoe UI", 10))
    style.map("TNotebook.Tab", background=[("selected", RAISED), ("active", HOVER)],
              foreground=[("selected", ACCENT), ("active", TEXT)])
    # The new left navigation replaces tabs only for the MAIN notebook.
    # Existing individual page tabs remain registered and fully functional.
    style.configure("Shell.TNotebook", background=BG, borderwidth=0,
                    tabmargins=(0, 0, 0, 0))
    style.layout("Shell.TNotebook.Tab", [])
    style.configure("TButton", background=RAISED, foreground=TEXT,
                    padding=(11, 7), borderwidth=0, bordercolor=LINE)
    style.map("TButton", background=[("active", HOVER), ("disabled", SURFACE)],
              foreground=[("disabled", DISABLED), ("active", ACCENT)])
    style.configure("Accent.TButton", background="#087C99", foreground="#FFFFFF",
                    padding=(13, 9), font=("Segoe UI Semibold", 10))
    style.map("Accent.TButton", background=[("active", "#0BA6CA")])
    style.configure("Nav.TButton", background=SURFACE, foreground=MUTED,
                    anchor="w", padding=(10, 6), borderwidth=0)
    style.map("Nav.TButton", background=[("active", HOVER)],
              foreground=[("active", TEXT)])
    style.configure("NavSelected.TButton", background=RAISED, foreground=ACCENT,
                    anchor="w", padding=(10, 6), borderwidth=0,
                    font=("Segoe UI Semibold", 10))
    style.map("NavSelected.TButton", background=[("active", HOVER)])
    style.configure("TEntry", fieldbackground=SURFACE, foreground=TEXT,
                    insertcolor=TEXT, bordercolor=LINE, padding=(5, 6))
    style.configure("TCombobox", fieldbackground=SURFACE, foreground=TEXT,
                    background=SURFACE, arrowcolor=ACCENT, padding=(4, 5))
    style.map("TCombobox", fieldbackground=[("readonly", SURFACE)],
              foreground=[("readonly", TEXT)])
    style.configure("TSpinbox", fieldbackground=SURFACE, foreground=TEXT, arrowsize=12)
    style.configure("TSeparator", background=LINE)
    style.configure("Treeview", background=SURFACE, foreground=TEXT,
                    fieldbackground=SURFACE, rowheight=30, borderwidth=0)
    style.map("Treeview", background=[("selected", HOVER)],
              foreground=[("selected", ACCENT)])
    style.configure("Treeview.Heading", background=RAISED, foreground=TEXT,
                    font=("Segoe UI Semibold", 10), padding=(8, 7))
    style.configure("Vertical.TScrollbar", background=RAISED,
                    troughcolor=SURFACE, arrowcolor=ACCENT)
