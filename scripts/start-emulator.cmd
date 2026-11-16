@echo off
call "%~dp0_launcher.cmd" -Action Start %*
exit /b %errorlevel%
