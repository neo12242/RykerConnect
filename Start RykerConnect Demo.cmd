@echo off
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0Simulator\Start-Simulator.ps1"
if errorlevel 1 pause
