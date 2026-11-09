"""Optional native Windows notification balloons for opt-in pinned friends.

Uses Shell_NotifyIconW directly (no PowerShell, subprocess, extra package,
background process or service). On supported Windows 10/11 systems the shell
may surface balloons as Action Center notifications; Focus Assist and the
user's notification settings can suppress them. The in-app feed always works.

Creating a temporary user-scoped notification icon is required by the Win32
Shell API. Its lifetime is tied to the GUI and removed on app close.
"""
from __future__ import annotations
import ctypes
from ctypes import wintypes
import os
import re

NIM_ADD = 0x00000000
NIM_MODIFY = 0x00000001
NIM_DELETE = 0x00000002
NIF_MESSAGE = 0x00000001
NIF_ICON = 0x00000002
NIF_TIP = 0x00000004
NIF_INFO = 0x00000010
NIIF_INFO = 0x00000001
IDI_INFORMATION = 32516
TRAY_ID = 17303
CALLBACK_MSG = 0x8000 + 174


class GUID(ctypes.Structure):
    _fields_ = [("Data1",wintypes.DWORD),("Data2",wintypes.WORD),
                ("Data3",wintypes.WORD),("Data4",ctypes.c_ubyte*8)]


class NOTIFYICONDATAW(ctypes.Structure):
    _fields_ = [
        ("cbSize",wintypes.DWORD),
        ("hWnd",wintypes.HWND),
        ("uID",wintypes.UINT),
        ("uFlags",wintypes.UINT),
        ("uCallbackMessage",wintypes.UINT),
        ("hIcon",wintypes.HICON),
        ("szTip",ctypes.c_wchar*128),
        ("dwState",wintypes.DWORD),
        ("dwStateMask",wintypes.DWORD),
        ("szInfo",ctypes.c_wchar*256),
        ("uTimeoutOrVersion",wintypes.UINT),
        ("szInfoTitle",ctypes.c_wchar*64),
        ("dwInfoFlags",wintypes.DWORD),
        ("guidItem",GUID),
        ("hBalloonIcon",wintypes.HICON),
    ]


def safe_label(value,limit):
    value=str(value or "")
    value=re.sub(r"[\x00-\x1f\x7f]+"," ",value).strip()
    return value[:limit]


class WindowsFriendNotifier:
    """Call ONLY from the owner Tk GUI thread. Fail quietly, never block."""

    def __init__(self,root):
        self.root=root
        self._registered=False
        self._hwnd=0

    def _shell(self,command,data):
        shell=ctypes.windll.shell32.Shell_NotifyIconW
        shell.argtypes=[wintypes.DWORD,ctypes.POINTER(NOTIFYICONDATAW)]
        shell.restype=wintypes.BOOL
        return bool(shell(command,ctypes.byref(data)))

    def _struct(self,flags):
        data=NOTIFYICONDATAW()
        data.cbSize=ctypes.sizeof(NOTIFYICONDATAW)
        data.hWnd=self._hwnd
        data.uID=TRAY_ID
        data.uFlags=flags
        data.uCallbackMessage=CALLBACK_MSG
        return data

    def show(self,name):
        return self._notify("Pinned VRChat friend online",
                            safe_label(name,70)+" is online in VRChat")

    def show_test(self):
        return self._notify("TGC Co-Companion notification test",
                            "Native Windows notifications are working.")

    def _notify(self,title,body):
        if os.name!="nt":
            return False
        try:
            if not self._registered:
                self._hwnd=int(self.root.winfo_id())
                if not self._hwnd:
                    return False
                icon=ctypes.windll.user32.LoadIconW
                icon.argtypes=[wintypes.HINSTANCE,wintypes.LPCWSTR]
                icon.restype=wintypes.HICON
                resource=ctypes.cast(ctypes.c_void_p(IDI_INFORMATION),
                                     wintypes.LPCWSTR)
                handle=icon(None,resource)
                if not handle:
                    return False
                created=self._struct(NIF_ICON|NIF_TIP|NIF_MESSAGE)
                created.hIcon=handle
                created.szTip="TGC Co-Companion • Pinned Friends"
                if not self._shell(NIM_ADD,created):
                    return False
                self._registered=True
            update=self._struct(NIF_INFO)
            update.szInfoTitle=safe_label(title,60)
            update.szInfo=safe_label(body,250)
            update.dwInfoFlags=NIIF_INFO
            update.uTimeoutOrVersion=10000
            return self._shell(NIM_MODIFY,update)
        except (AttributeError,OSError,ValueError,TypeError,ctypes.ArgumentError):
            return False

    def close(self):
        if not self._registered:
            return
        try:
            self._shell(NIM_DELETE,self._struct(0))
        except (AttributeError,OSError,ValueError,TypeError,ctypes.ArgumentError):
            pass
        finally:
            self._registered=False
