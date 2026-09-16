$ErrorActionPreference = 'Stop'
$sdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
$adb = Join-Path $sdk 'platform-tools\adb.exe'
$emulator = Join-Path $sdk 'emulator\emulator.exe'
$python = Join-Path $PSScriptRoot '.venv\Scripts\python.exe'
$script = Join-Path $PSScriptRoot 'esp_simulator.py'
$avd = 'RykerConnect_Pixel_7'
$serial = 'emulator-5554'

if (!(Test-Path -LiteralPath $python)) {
    throw 'Simulator environment missing. Follow the installation steps in README.md.'
}
$devices = & $adb devices
if (!($devices -match "$serial\s+device")) {
    if ($devices -match $serial) { throw 'Emulator 5554 is not ready. Wait for it to boot and try again.' }
    Start-Process -FilePath $emulator -ArgumentList "-avd $avd -port 5554 -no-boot-anim -gpu software" -WindowStyle Hidden `
        -RedirectStandardOutput (Join-Path $PSScriptRoot 'emulator.log') `
        -RedirectStandardError (Join-Path $PSScriptRoot 'emulator-error.log')
}
$ready = $false
for ($attempt = 0; $attempt -lt 90; $attempt++) {
    $bootStatus = ''
    try { $bootStatus = & $adb -s $serial shell getprop sys.boot_completed 2>$null } catch { }
    if ($bootStatus -match '^1$') {
        $ready = $true
        break
    }
    Start-Sleep -Seconds 2
}
if (!$ready) { throw 'The virtual phone did not finish booting. See emulator-error.log.' }
$actualAvd = & $adb -s $serial emu avd name
if ($actualAvd[0].Trim() -ne $avd) { throw "Port 5554 belongs to a different virtual phone: $($actualAvd[0])" }
$running = Get-CimInstance Win32_Process -Filter "Name='python.exe'" | Where-Object {
    $_.ExecutablePath -eq $python -and $_.CommandLine -like '*esp_simulator.py*'
}
if (!$running) {
    $process = Start-Process -FilePath $python -ArgumentList @('-u', ('"' + $script + '"')) `
        -WorkingDirectory $PSScriptRoot -WindowStyle Hidden -PassThru `
        -RedirectStandardOutput (Join-Path $PSScriptRoot 'stdout.log') `
        -RedirectStandardError (Join-Path $PSScriptRoot 'stderr.log')
    Start-Sleep -Seconds 2
    if ($process.HasExited) { throw 'Simulator exited. See stderr.log.' }
}
& $adb -s $serial reverse tcp:8876 tcp:8876
if ($LASTEXITCODE -ne 0) { throw 'Could not connect the in-app OLED preview to the simulator.' }
$package = 'de.chaostheorybot.rykerconnect'
if (!((& $adb -s $serial shell pm path $package) -match '^package:')) {
    $apk = Join-Path $PSScriptRoot '..\Android\app\build\outputs\apk\debug\app-debug.apk'
    & $adb -s $serial install $apk
    if ($LASTEXITCODE -ne 0) { throw 'Could not install the debug APK.' }
}
& $adb -s $serial shell am start -n "$package/.MainActivity"
if ($LASTEXITCODE -ne 0) { throw 'Could not launch RykerConnect.' }
Write-Host 'Virtual ESP is running. Pairing PIN: 123456. Leave the emulator open.'
Write-Host 'Use the main-unit Settings button to explore the simulated device.'
$displayUrl = 'http://127.0.0.1:8876'
$displayReady = $false
for ($attempt = 0; $attempt -lt 15; $attempt++) {
    try {
        $preview = Invoke-RestMethod -Uri "$displayUrl/state" -TimeoutSec 2
        if ($null -ne $preview.layout) { $displayReady = $true; break }
    } catch { }
    Start-Sleep -Seconds 1
}
if (!$displayReady) { throw 'Display server is not ready. Restart the simulator and check stderr.log.' }
Start-Process $displayUrl
Write-Host "Live OLED display: $displayUrl"
