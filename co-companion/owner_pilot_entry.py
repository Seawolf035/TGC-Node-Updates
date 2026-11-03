"""Windows owner preview packaged entrypoint. No production deployment."""
from __future__ import annotations

import sys
import os
from threading import Thread


def main():
    if len(sys.argv) == 2 and sys.argv[1] == "--smoke-check":
        # This only proves the PACKAGED Tk UI + Tcl resources start.
        # No TGC network, VRChat launch, Node uploader, or action occurs.
        os.environ["TGC_COCOMPANION_SMOKE"] = "1"
        import tkinter as tk
        from tgc_cocompanion.owner_pilot import OwnerPilotApp
        root=tk.Tk()
        root.withdraw()
        try:
            app=OwnerPilotApp(root)
            root.update_idletasks()
            assert hasattr(app,"updates") and hasattr(app.features,"collections")
            assert hasattr(app.features,"two_factor_panel")
            assert not app.features.two_factor_panel.winfo_manager()
            app.shell.toggle_compact()
            root.update_idletasks()
            assert app.shell.state.compact
            app.shell.toggle_compact()
            root.update_idletasks()
            assert not app.shell.state.compact
        finally:
            root.destroy()
        return
    # Create/repair a stable Start Menu entry without blocking the GUI.
    if getattr(sys, 'frozen', False) and os.name == 'nt':
        from tgc_cocompanion.exe_first_run import first_run_install_dialog
        if first_run_install_dialog():
            return
        from tgc_cocompanion.exe_release_updater import maybe_create_shortcut_for_running_exe
        Thread(target=maybe_create_shortcut_for_running_exe,
               name='CoCompanionStartMenu', daemon=True).start()
    from tgc_cocompanion.owner_pilot import main as launch
    launch()


if __name__=="__main__":
    main()
