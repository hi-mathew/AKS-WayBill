@echo off
setlocal
powershell -ExecutionPolicy Bypass -File "%~dp0package-windows.ps1"
if errorlevel 1 (
  echo.
  echo Packaging failed.
  pause
  exit /b 1
)
echo.
echo Packaging completed successfully.
pause
