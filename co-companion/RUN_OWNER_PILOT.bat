@echo off
setlocal EnableExtensions
title TGC Co-Companion - Python Owner Preview v0.4
cd /d "%~dp0"
set "PY=py -3.13"
%PY% --version >nul 2>&1
if errorlevel 1 set "PY=py -3"
%PY% --version >nul 2>&1
if errorlevel 1 set "PY=python"
%PY% --version >nul 2>&1
if errorlevel 1 (
 echo ERROR: Python 3.11+ was not found.
 pause
 exit /b 1
)
%PY% -c "import sys; assert sys.version_info >= (3,11)" >nul 2>&1
if errorlevel 1 (
 echo ERROR: Python 3.11 or newer is required.
 pause
 exit /b 1
)
%PY% -c "import tkinter as tk; root=tk.Tk(); root.withdraw(); root.destroy()" >nul 2>&1
if errorlevel 1 (
 echo ERROR: Python Tkinter / Tcl-Tk is unavailable.
 echo Repair your Python installation with Tcl/Tk selected.
 pause
 exit /b 1
)
echo Checking psutil and Windows DPAPI dependencies (no virtual environment)...
%PY% -c "import psutil, win32crypt" >nul 2>&1
if errorlevel 1 (
 %PY% -m pip install --user --disable-pip-version-check -r requirements_owner_pilot.txt
 if errorlevel 1 (
  echo ERROR: Could not install Co-Companion requirements.
  pause
  exit /b 1
 )
)
echo Starting TGC Co-Companion Python Owner Preview...
%PY% owner_pilot_entry.py
if errorlevel 1 (
 echo Co-Companion exited with an error; see above.
 pause
 exit /b 1
)
endlocal
