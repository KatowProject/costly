@echo off
call "%~dp0_launcher.cmd" -Action Stop %*
exit /b %errorlevel%
