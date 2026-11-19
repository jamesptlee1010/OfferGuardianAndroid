@echo off
cd /d "%~dp0"
echo OFFER GUARDIAN - Android APK Builder
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0Build-APK.ps1"
if errorlevel 1 (
  echo.
  echo Build did not complete. Read the error above and README.md.
) else (
  echo.
  echo Your Android installation file should be: OfferGuardian-debug.apk
)
pause
