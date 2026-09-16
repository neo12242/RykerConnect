param([ValidateSet('Open','Closed')][string]$Posture='Open')
$ErrorActionPreference='Stop'
$sdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
$adb=Join-Path $sdk 'platform-tools\adb.exe'
$emulator=Join-Path $sdk 'emulator\emulator.exe'
$serial='emulator-5556'
$avd='RykerConnect_Foldable_Demo'
if(!((& $emulator -list-avds) -contains $avd)){throw 'Foldable demo AVD is missing. See Simulator/README.md.'}
if(!((& $adb devices) -match "$serial\s+device")) {
    if((& $adb devices) -match $serial){throw 'Foldable is still starting. Wait and retry.'}
    Start-Process -FilePath $emulator -ArgumentList "-avd $avd -port 5556 -no-boot-anim -gpu software -memory 2048" -WindowStyle Hidden -RedirectStandardOutput (Join-Path $PSScriptRoot 'foldable.log') -RedirectStandardError (Join-Path $PSScriptRoot 'foldable-error.log')
}
$ready=$false
for($attempt=0;$attempt -lt 90;$attempt++) {
    if((& $adb -s $serial shell getprop sys.boot_completed 2>$null) -match '^1$'){$ready=$true;break}
    Start-Sleep -Seconds 2
}
if(!$ready){throw 'Foldable did not finish booting.'}
$name=& $adb -s $serial emu avd name
if($name[0].Trim() -ne $avd){throw 'Port 5556 belongs to a different emulator.'}
$apk=Join-Path $PSScriptRoot '..\Android\app\build\outputs\apk\debug\app-debug.apk'
& $adb -s $serial install -r $apk
if($LASTEXITCODE -ne 0){throw 'App installation failed.'}
& $adb -s $serial shell cmd device_state state $(if($Posture -eq 'Closed'){0}else{2})
Start-Sleep -Seconds 2
& $adb -s $serial shell input -d 0 keyevent 224
& $adb -s $serial shell wm dismiss-keyguard
& $adb -s $serial shell am force-stop de.chaostheorybot.rykerconnect
Start-Sleep -Seconds 1
& $adb -s $serial shell am start -W -n de.chaostheorybot.rykerconnect/.MainActivity --ez demo true
Write-Host 'Foldable demo is open. Tap Start demo. Use Fold Demo.cmd or Unfold Demo.cmd to change posture.'
Write-Host 'This emulator has separate app data. Guided demo needs no ESP or GPS permissions.'
