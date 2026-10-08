# Section 6 of BETA-CHECKLIST.md: runs the settings-file cases in the test client and prints the
# result of each, plus the log lines the mod wrote while handling them.
param([int]$TimeoutSeconds = 420)

$runDir = Join-Path $PSScriptRoot 'build\run\clientGameTest'
$results = Join-Path $runDir 'config_test_results.txt'
$request = Join-Path $runDir 'config_test_request.txt'
New-Item -ItemType Directory -Force $runDir | Out-Null
if (Test-Path -LiteralPath $results) { [IO.File]::Delete($results) }
Set-Content -LiteralPath $request -Value 'run' -Encoding ascii

$gradle = Start-Process -FilePath (Join-Path $PSScriptRoot 'gradlew.bat') -ArgumentList 'runClientGameTest', '--console=plain' `
    -WorkingDirectory $PSScriptRoot -PassThru -WindowStyle Hidden `
    -RedirectStandardOutput (Join-Path $PSScriptRoot 'build\config-test.log') -RedirectStandardError (Join-Path $PSScriptRoot 'build\config-test.err.log')

$deadline = (Get-Date).AddSeconds($TimeoutSeconds)
while ((Get-Date) -lt $deadline -and -not (Test-Path -LiteralPath $results) -and -not $gradle.HasExited) { Start-Sleep -Seconds 3 }
Start-Sleep -Seconds 2

$projectName = Split-Path $PSScriptRoot -Leaf
Get-CimInstance Win32_Process -Filter "Name='java.exe'" |
    Where-Object { $_.CommandLine -match [regex]::Escape($projectName) -and $_.CommandLine -notmatch 'GradleDaemon|GradleWrapperMain' } |
    ForEach-Object { Stop-Process -Id $_.ProcessId -Force -Confirm:$false }
[IO.File]::Delete($request)

if (-not (Test-Path -LiteralPath $results)) { 'Config test did NOT finish; see build\config-test.log and the run log.'; exit 1 }

Get-Content -LiteralPath $results
''
'What the mod logged:'
Select-String -LiteralPath (Join-Path $runDir 'logs\latest.log') -Pattern '/(WARN|ERROR)\] \(tvomf\)' |
    ForEach-Object { '  ' + ($_.Line -replace '^\[[^\]]+\] \[[^\]]+\] ', '').Substring(0, [Math]::Min(190, ($_.Line -replace '^\[[^\]]+\] \[[^\]]+\] ', '').Length)) } |
    Select-Object -Unique
