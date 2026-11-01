@echo off
setlocal EnableExtensions
title TGC Co-Companion - Owner Preview v0.2
cd /d "%~dp0"
set "PY=py -3.13"
%PY% --version >nul 2>&1
if errorlevel 1 set "PY=py -3"
%PY% --version >nul 2>&1
if errorlevel 1 (
    echo ERROR: Python 3.11 or newer is needed for this SOURCE-BASED build.
    echo Use the bundled owner-test executable if you don't want Python.
    pause
    exit /b 1
)
echo Verifying Python Tkinter...
%PY% -c "import tkinter as t; x=t.Tk(); x.withdraw(); x.destroy()" >nul 2>&1
if errorlevel 1 (
    echo ERROR: Python Tcl/Tk is unavailable. Run: py -3.13 -m tkinter
    echo Repair Tcl/Tk in your Python installer or use the bundled EXE build.
    pause
    exit /b 1
)
echo Checking required Windows libraries; no virtual environment...
%PY% -c "import psutil,win32crypt" >nul 2>&1
if errorlevel 1 (
    echo Installing psutil and pywin32 into this Windows user's Python only...
    %PY% -m pip install --user --disable-pip-version-check -r requirements_owner_pilot.txt
    if errorlevel 1 (
        echo Dependency installation failed. No TGC services were modified.
        pause
        exit /b 1
    )
)
echo Starting TGC Co-Companion Owner Preview v0.2...
%PY% -m tgc_cocompanion.owner_pilot
if errorlevel 1 (
    echo Owner preview ended with an error. See above for details.
    pause
    exit /b 1
)
endlocal
