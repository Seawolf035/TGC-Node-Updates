@echo off
setlocal EnableExtensions
cd /d "%~dp0"
title TGC Staff Launcher Installer
echo =============================================
echo       TGC Staff Launcher Installer
echo =============================================
echo.
if exist ".venv\Scripts\python.exe" (
  ".venv\Scripts\python.exe" -c "import psutil,win32api,tkinter" >nul 2>&1
  if not errorlevel 1 goto :deps
)
:detect
set "PYTHON="
call :probe py -3.13
if not defined PYTHON call :probe py -3.12
if not defined PYTHON call :probe py -3.11
if not defined PYTHON call :probe python
if not defined PYTHON call :probe python3
for %%V in (313 312 311) do (
 if not defined PYTHON if exist "%LocalAppData%\Programs\Python\Python%%V\python.exe" call :probe "%LocalAppData%\Programs\Python\Python%%V\python.exe"
 if not defined PYTHON if exist "%ProgramFiles%\Python%%V\python.exe" call :probe "%ProgramFiles%\Python%%V\python.exe"
)
if not defined PYTHON goto :missing
echo Using %PYTHON%
%PYTHON% -m venv ".venv"
if errorlevel 1 goto :failed
:deps
".venv\Scripts\python.exe" -m pip install --upgrade pip
if errorlevel 1 goto :failed
".venv\Scripts\python.exe" -m pip install -r requirements.txt
if errorlevel 1 goto :failed
".venv\Scripts\python.exe" -c "import psutil,win32api,tkinter; print('Dependencies verified')"
if errorlevel 1 goto :failed
echo Installation complete. Run Run_TGC_Staff_Launcher.bat.
pause
exit /b 0
:probe
if defined PYTHON exit /b 0
%* -c "import sys,tkinter; assert sys.implementation.name=='cpython' and (3,11)<=sys.version_info[:2]<=(3,13) and sys.maxsize>2**32" >nul 2>&1
if not errorlevel 1 set "PYTHON=%*"
exit /b 0
:missing
echo Compatible Python not detected.
echo [I] Install Python 3.13 automatically using Windows Package Manager
echo [O] Open official Python download page
echo [Q] Quit
choice /C IOQ /N /M "Choose I, O, or Q: "
if errorlevel 3 exit /b 1
if errorlevel 2 goto :web
where winget >nul 2>&1
if errorlevel 1 goto :web
winget install --id Python.Python.3.13 -e --scope user --accept-source-agreements --accept-package-agreements
if errorlevel 1 goto :web
echo Python installed. Checking installed location...
set "PYTHON="
call :probe py -3.13
if not defined PYTHON if exist "%LocalAppData%\Programs\Python\Python313\python.exe" call :probe "%LocalAppData%\Programs\Python\Python313\python.exe"
if not defined PYTHON (
 echo Rerun installer after Windows refreshes Python paths.
 pause
 exit /b 1
)
goto :detect
:web
start "" "https://www.python.org/downloads/windows/"
echo Install standard 64-bit Python 3.13 and rerun this installer.
pause
exit /b 1
:failed
echo Installation failed. Send error above to Seawolf.
pause
exit /b 1
