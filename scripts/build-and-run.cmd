@echo off
call "%~dp0_launcher.cmd" -Action BuildRun %*
exit /b %errorlevel%
