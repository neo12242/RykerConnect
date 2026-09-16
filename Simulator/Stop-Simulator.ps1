$python = Join-Path $PSScriptRoot '.venv\Scripts\python.exe'
$running = Get-CimInstance Win32_Process -Filter "Name='python.exe'" | Where-Object {
    $_.ExecutablePath -eq $python -and $_.CommandLine -like '*esp_simulator.py*'
}
foreach ($process in $running) { Stop-Process -Id $process.ProcessId }
Write-Host 'ESP simulator stopped. The virtual phone remains open; settings and pairing are preserved.'
