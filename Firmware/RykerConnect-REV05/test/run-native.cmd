@echo off
setlocal
call "C:\Program Files\Microsoft Visual Studio\18\Community\VC\Auxiliary\Build\vcvars64.bat" >nul
if errorlevel 1 exit /b 1
cd /d "%~dp0"
cl /nologo /EHsc /std:c++17 /I"..\include" rtc_native.cpp /Fe:rtc_native.exe
if errorlevel 1 exit /b 1
rtc_native.exe
exit /b %errorlevel%
