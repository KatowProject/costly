@echo off
setlocal
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0android.ps1" %*
set "costlyExitCode=%errorlevel%"
if not "%costlyExitCode%"=="0" if not "%COSTLY_NO_PAUSE%"=="1" (
    echo.
    pause
)
exit /b %costlyExitCode%
