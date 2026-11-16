@echo off
call "%~dp0_launcher.cmd" -Action Run %*
exit /b %errorlevel%
