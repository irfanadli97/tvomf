# Runs the in-game visual test and stops the client once it has finished.
# Other mods loaded from build/run/clientGameTest/mods can keep the client alive after the test,
# so this waits for the marker file the test writes rather than for the process to exit.
param([int]$TimeoutSeconds = 420)

$runDir = Join-Path $PSScriptRoot 'build\run\clientGameTest'
$marker = Join-Path $runDir 'test_done.txt'
if (Test-Path -LiteralPath $marker) { Remove-Item -LiteralPath $marker -Confirm:$false }

$gradle = Start-Process -FilePath (Join-Path $PSScriptRoot 'gradlew.bat') -ArgumentList 'runClientGameTest', '--console=plain' `
    -WorkingDirectory $PSScriptRoot -PassThru -WindowStyle Hidden `
    -RedirectStandardOutput (Join-Path $PSScriptRoot 'build\visual-test.log') -RedirectStandardError (Join-Path $PSScriptRoot 'build\visual-test.err.log')

$deadline = (Get-Date).AddSeconds($TimeoutSeconds)
while ((Get-Date) -lt $deadline -and -not (Test-Path -LiteralPath $marker) -and -not $gradle.HasExited) { Start-Sleep -Seconds 3 }

$finished = Test-Path -LiteralPath $marker
Start-Sleep -Seconds 2

$projectName = Split-Path $PSScriptRoot -Leaf
Get-CimInstance Win32_Process -Filter "Name='java.exe'" |
    Where-Object { $_.CommandLine -match [regex]::Escape($projectName) -and $_.CommandLine -notmatch 'GradleDaemon|GradleWrapperMain' } |
    ForEach-Object { Stop-Process -Id $_.ProcessId -Force -Confirm:$false }

if ($finished) { 'Visual test finished.' } else { 'Visual test did NOT finish; see build\visual-test.log and the run log.' }
