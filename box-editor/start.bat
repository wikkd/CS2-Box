@echo off
rem CS2-Box box editor - local deployment launcher (Windows)
rem Double-click this file, or run:  start.bat [--port 4173] [--no-open]
cd /d "%~dp0"
where node >nul 2>nul
if errorlevel 1 (
  echo [ERROR] Node.js was not found. Install it from https://nodejs.org/ first.
  pause
  exit /b 1
)
echo Starting CS2-Box box editor at http://127.0.0.1:4173/  (Ctrl+C to stop)
node server.mjs %*
pause