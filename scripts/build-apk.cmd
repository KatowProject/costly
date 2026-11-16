@echo off
call "%~dp0_launcher.cmd" -Action Build %*
exit /b %errorlevel%
