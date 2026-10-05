# Runs the visual test once per compatibility rig and keeps each run's screenshots and findings
# in build\rig-results\<game version>\<run name>\. See BETA-CHECKLIST.md section 5 and rigs.ps1.
param([Parameter(Mandatory)][string]$GameVersion, [string]$ShaderPack = '')

$runDir = Join-Path $PSScriptRoot 'build\run\clientGameTest'
$results = Join-Path (Join-Path $PSScriptRoot 'build\rig-results') $GameVersion
$runs = [ordered]@{
    'A'             = 'A'
    'A-iris-shader' = 'A', 'A-iris'
    'B'             = 'B'
    'C'             = 'C'
    'ABC'           = 'A', 'B', 'C'
}

foreach ($name in $runs.Keys) {
    & (Join-Path $PSScriptRoot 'rigs.ps1') -GameVersion $GameVersion -Use $runs[$name] | Out-Null

    # Only the Iris run has shaders switched on.
    $irisConfig = Join-Path $runDir 'config\iris.properties'
    if (Test-Path -LiteralPath $irisConfig) { [IO.File]::Delete($irisConfig) }
    if ($name -eq 'A-iris-shader' -and $ShaderPack) {
        New-Item -ItemType Directory -Force (Join-Path $runDir 'shaderpacks'), (Join-Path $runDir 'config') | Out-Null
        Copy-Item -LiteralPath $ShaderPack -Destination (Join-Path $runDir 'shaderpacks') -Force
        Set-Content -LiteralPath $irisConfig -Value "shaderPack=$(Split-Path $ShaderPack -Leaf)", 'enableShaders=true' -Encoding ascii
    }

    $shots = Join-Path $runDir 'screenshots'
    if (Test-Path -LiteralPath $shots) { Get-ChildItem -LiteralPath $shots -File | ForEach-Object { [IO.File]::Delete($_.FullName) } }

    & (Join-Path $PSScriptRoot 'run-visual-test.ps1') | Out-Null

    $out = Join-Path $results $name
    New-Item -ItemType Directory -Force $out | Out-Null
    Get-ChildItem -LiteralPath $out -File | ForEach-Object { [IO.File]::Delete($_.FullName) }
    if (Test-Path -LiteralPath $shots) { Copy-Item (Join-Path $shots '*.png') $out -Force }
    $log = Join-Path $runDir 'logs\latest.log'
    Copy-Item -LiteralPath $log -Destination (Join-Path $out 'latest.log') -Force

    $finished = Test-Path -LiteralPath (Join-Path $runDir 'test_done.txt')
    $ours = @(Select-String -LiteralPath $log -Pattern '\(tvomf\).*(WARN|Could not hook)|Not available on this game version' | ForEach-Object Line)
    $crash = @(Select-String -LiteralPath $log -Pattern 'Mixin apply .* failed|InvalidInjection|Missing elements in vertex|Reported exception|Crash report saved|out of bounds for render area' | ForEach-Object Line)
    $summary = "{0,-14} mods={1,-3} finished={2,-5} screenshots={3,-3} tvomf warnings={4} crash signs={5}" -f $name,
        (Get-ChildItem -LiteralPath (Join-Path $runDir 'mods') -Filter *.jar).Count, $finished, (Get-ChildItem $out -Filter *.png).Count, $ours.Count, $crash.Count
    $summary
    Set-Content -LiteralPath (Join-Path $out 'summary.txt') -Value (@($summary) + $ours + $crash)
}
