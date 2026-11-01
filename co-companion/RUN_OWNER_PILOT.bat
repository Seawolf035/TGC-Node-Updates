@echo off
setlocal EnableExtensions
title TGC Co-Companion - Owner Pilot (NOT STAFF RELEASE)
cd /d "%~dp0"
if not exist ".venv\Scripts\python.exe" (
  echo Creating isolated Python virtual environment for OWNER PILOT...
  py -3 -m venv .venv
  if errorlevel 1 (
    echo Python 3.11+ launcher is required. No TGC data was changed.
    pause
    exit /b 1
  )
)
rem Tcl/Tk is bundled with the BASE Python installer, not installed by pip.
rem Test actual GUI creation in this venv before installing anything.
echo Checking that this Python can open a Tkinter window...
".venv\Scripts\python.exe" -c "import tkinter as t; w=t.Tk(); w.withdraw(); w.destroy()" >nul 2>&1
if errorlevel 1 (
  echo.
  echo ERROR: Python cannot load the Tcl/Tk GUI files ^(init.tcl^).
  echo This is a broken/incomplete Python installation or Python venv.
  echo The Co-Companion dependencies are not responsible.
  echo.
  echo 1. Run: py -3.13 -m tkinter
  echo 2. If it fails, open Windows Settings ^> Apps ^> Installed apps ^>
  echo    Python 3.13 ^> Modify / Repair.
  echo 3. Include the optional "tcl/tk and IDLE" component.
  echo 4. Run: py -3.13 -m tkinter again. It must open a small window.
  echo 5. Then rerun this BAT. If base Python works but the venv still
  echo    fails, update Python 3.13 to the latest patch and recreate .venv.
  echo.
  echo No TGC credentials, Node files, Relay or production apps were touched.
  pause
  exit /b 1
)
echo Installing required Windows libraries inside .venv only...
".venv\Scripts\python.exe" -m pip install --disable-pip-version-check -r requirements_owner_pilot.txt
if errorlevel 1 (
  echo Dependencies could not be installed. Nothing was deployed.
  pause
  exit /b 1
)
echo Starting isolated TGC Co-Companion OWNER PILOT...
".venv\Scripts\python.exe" -m tgc_cocompanion.owner_pilot
if errorlevel 1 (
  echo Pilot ended with an error. See the console output above.
  pause
  exit /b 1
)
endlocal
