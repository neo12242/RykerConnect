@echo off
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0Simulator\Start-Foldable-Demo.ps1"
if errorlevel 1 pause
