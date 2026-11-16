@echo off
call "%~dp0_launcher.cmd" -Action List %*
exit /b %errorlevel%
