"""Windows owner preview packaged entrypoint. No production deployment."""
from __future__ import annotations

import sys


def main():
    if len(sys.argv) == 2 and sys.argv[1] == "--smoke-check":
        # This only proves the PACKAGED Tk UI + Tcl resources start.
        # No TGC network, VRChat launch, Node uploader, or action occurs.
        import tkinter as tk
        from tgc_cocompanion.owner_pilot import OwnerPilotApp
        root=tk.Tk()
        root.withdraw()
        try:
            app=OwnerPilotApp(root)
            root.update_idletasks()
            assert hasattr(app,"updates") and hasattr(app.features,"collections")
        finally:
            root.destroy()
        return
    from tgc_cocompanion.owner_pilot import main as launch
    launch()


if __name__=="__main__":
    main()
