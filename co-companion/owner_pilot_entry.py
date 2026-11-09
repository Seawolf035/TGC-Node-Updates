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
            assert all(hasattr(app.features, x) for x in (
                "explore", "recent_players", "official_chatboxes", "collections"))
            assert all(x in app.shell.page_names for x in (
                "Explore","Friends Hub","Recent Players","Staff Chatboxes","StaffDex"))
            assert app.shell.open_page("Friends Hub")
            assert hasattr(app.features.friend_hub,"friend_tree")
            assert hasattr(app.features.friend_hub,"group_tree")
            assert hasattr(app.features.friend_hub,"activity_table")
            assert hasattr(app.features.friend_hub,"activity_query")
            assert hasattr(app.features.friend_hub,"activity_kind")
            assert hasattr(app.features.friend_hub,"activity_count")
            app.features.friend_hub.render_history()
            assert len(app.features.friend_hub._history_ids)==0
            assert hasattr(app.features.friend_hub,"favorite_index")
            assert hasattr(app.features.friend_hub,"alerts")
            assert hasattr(app.features.friend_hub,"alert_feed")
            assert hasattr(app.features.friend_hub,"native_notifier")
            assert hasattr(app.features.friend_hub,"windows_alerts")
            assert isinstance(app.features.friend_hub.windows_alerts.get(),bool)
            assert app.features.friend_hub.alert_feed.size()==0
            assert len(app.features.friend_hub.friends)==0
            assert app.shell.open_page("Staff Chatboxes")
            assert hasattr(app.features.official_chatboxes,"role_actions")
            assert len(app.features.official_chatboxes.table.get_children())==12
            assert app.features.official_chatboxes.table["columns"] == (
                "checked","role","message","state")
            assert app.shell.open_page("Recent Players")
            assert app.shell.open_page("Explore")
            from tgc_cocompanion.desktop_pages import CATEGORIES
            assert ("Group Search","search_groups") in CATEGORIES
            from tgc_cocompanion.desktop_explore import classify_result
            assert classify_result({"id":"grp_2a833076-03ac-4212-aeff-0708d8eab968",
                                    "name":"TGC"},"Groups")[0]=="Group"
            assert hasattr(app.features.explore,"result_filter")
            assert hasattr(app.features.explore,"type_filter")
            assert app.features.explore._render_results()==0
            for title in ("Avatars","Bookmarks","Inbox"):
                assert app.shell.open_page(title)
            assert hasattr(app.features.collections,"avatar_title_var")
            assert hasattr(app.features.collections,"avatar_meta_var")
            assert hasattr(app.features.collections,"bookmark_filter")
            assert hasattr(app.features.collections,"inbox_type_picker")
            assert hasattr(app.features.collections,"inbox_selected")
            app.features.collections.render_inbox()
            app.features.collections.clear_private_views()
            assert len(app.features.collections._inbox_rows)==0
            assert app.shell.open_page("Home")
            assert hasattr(app.settings,"theme_name")
            assert app.shell.open_page("UI Color Studio")
            assert len(app.color_studio.entries)==10
            from tgc_cocompanion.desktop_color_studio import (
                import_vrchat_colors, export_vrchat_colors)
            assert len(import_vrchat_colors(
                "#123456,#654321,#ABCDEF,#091827,#FFEEDD,#8899AA"))==10
            assert export_vrchat_colors(app.color_studio.colors()).count(",")==5
            # Verify the new focused workspaces exist in the real frozen
            # executable, rather than checking source strings alone.
            from tkinter import ttk
            def workspace_tabs(title):
                assert app.shell.open_page(title)
                index=app.shell.page_names.index(title)
                frame=app.shell.notebook.nametowidget(app.shell.notebook.tabs()[index])
                books=[child for child in frame.winfo_children()
                       if isinstance(child,ttk.Notebook)]
                assert len(books)==1, (title,len(books))
                book=books[0]
                return [book.tab(tab,"text") for tab in book.tabs()]
            assert workspace_tabs("StaffDex")==["Staff Directory","Achievements"]
            assert hasattr(app.features,"dex_count")
            assert hasattr(app.features,"dex_roster_count")
            assert workspace_tabs("OSC Studio")==[
                "Chatbox & Rotation","Avatar & Parameters","Voice"]
            assert hasattr(app.features,"osc_pid")
            assert hasattr(app.features,"osc_interval")
            assert app.shell.open_page("Profiles")
            assert hasattr(app.features.profile_tools,"visual")
            from PIL import Image, ImageTk
            placeholder=ImageTk.PhotoImage(Image.new("RGB",(24,24),"navy"),
                                           master=root)
            assert placeholder.width()==24
            profile=app.features.profile_tools.visual
            profile.update({"id":"usr_2a833076-03ac-4212-aeff-0708d8eab968",
                "displayName":"Test VRChat Profile","bio":"Structured responsive profile",
                "status":"active","dateJoined":"2024-03-01"},"user")
            assert profile.title.get()=="Test VRChat Profile"
            assert "Artwork" in profile.art_status.get()
            assert len(profile.meta_labels)==6
            for width in (800,1024,1280,1920,2560):
                app.shell._on_window_resize(width=width)
                profile._responsive(type("Ev",(),{"width":max(320,width-300)})())
                root.update_idletasks()
                assert app.shell.sidebar.cget("width") in (74,230)
            app.shell._on_window_resize(width=1280)
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
