@echo off
rem Hotel Booking concurrency tests: 4 original MANUAL + 2 new AUTO.
rem Forward -RoomId, -HotelId, -StressCount, -StartDate, -BaseUrl to .ps1.
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0parallel-booking-test-fixed.ps1" %*
exit /b %ERRORLEVEL%
