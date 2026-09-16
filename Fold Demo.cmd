@echo off
setlocal
if not defined ANDROID_HOME set "ANDROID_HOME=%LOCALAPPDATA%\Android\Sdk"
"%ANDROID_HOME%\platform-tools\adb.exe" -s emulator-5556 shell cmd device_state state 0
timeout /t 2 /nobreak >nul
"%ANDROID_HOME%\platform-tools\adb.exe" -s emulator-5556 shell input -d 0 keyevent 224
"%ANDROID_HOME%\platform-tools\adb.exe" -s emulator-5556 shell wm dismiss-keyguard
if errorlevel 1 pause
