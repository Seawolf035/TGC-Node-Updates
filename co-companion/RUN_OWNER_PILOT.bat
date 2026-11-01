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
