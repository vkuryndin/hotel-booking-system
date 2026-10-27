@echo off
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0parallel-booking-test-fixed.ps1" %*
exit /b %ERRORLEVEL%
