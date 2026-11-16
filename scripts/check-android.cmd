@echo off
call "%~dp0_launcher.cmd" -Action Check %*
exit /b %errorlevel%
