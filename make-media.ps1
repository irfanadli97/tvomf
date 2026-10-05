# Shoots the stills and the 5 second clip for the project page into docs\media.
#   -ModSet solo   only this mod (and Fabric API)
#   -ModSet full   plus every jar kept in build\run\clientGameTest\mods-full (or mods)
# Needs ffmpeg on the PATH.
#   -SettingsFile  a tvomf.json to shoot with; without it the mod's defaults are used
param([ValidateSet('solo', 'full')][string]$ModSet = 'solo', [string]$SettingsFile = '', [int]$TimeoutSeconds = 600)

$runDir = Join-Path $PSScriptRoot 'build\run\clientGameTest'
$mods = Join-Path $runDir 'mods'
$modsFull = Join-Path $runDir 'mods-full'
$outDir = Join-Path $PSScriptRoot 'docs\media'
New-Item -ItemType Directory -Force $runDir, $outDir | Out-Null

# The other mods live in "mods" while the full set is being shot and in "mods-full" otherwise.
if ($ModSet -eq 'solo') {
    if (-not (Test-Path -LiteralPath $modsFull) -and (Test-Path -LiteralPath $mods)) { Rename-Item -LiteralPath $mods -NewName 'mods-full' }
    New-Item -ItemType Directory -Force $mods | Out-Null
} elseif (Test-Path -LiteralPath $modsFull) {
    if (Test-Path -LiteralPath $mods) { [IO.Directory]::Delete($mods) }   # only succeeds when empty
    Rename-Item -LiteralPath $modsFull -NewName 'mods'
}

# Stills from an earlier shoot of this set would otherwise be copied again.
if (Test-Path -LiteralPath (Join-Path $runDir 'screenshots')) {
    Get-ChildItem (Join-Path $runDir 'screenshots') -Filter "*_${ModSet}_*.png" | ForEach-Object { [IO.File]::Delete($_.FullName) }
}

$done = Join-Path $runDir 'media_done.txt'
if (Test-Path -LiteralPath $done) { [IO.File]::Delete($done) }
$request = @($ModSet)
if ($SettingsFile) {
    New-Item -ItemType Directory -Force (Join-Path $runDir 'config') | Out-Null
    Copy-Item -LiteralPath $SettingsFile -Destination (Join-Path $runDir 'config\tvomf.json') -Force
    $request += 'configured'
}
Set-Content -LiteralPath (Join-Path $runDir 'media_request.txt') -Value $request -Encoding ascii

$gradle = Start-Process -FilePath (Join-Path $PSScriptRoot 'gradlew.bat') -ArgumentList 'runClientGameTest', '--console=plain' `
    -WorkingDirectory $PSScriptRoot -PassThru -WindowStyle Hidden `
    -RedirectStandardOutput (Join-Path $PSScriptRoot 'build\media.log') -RedirectStandardError (Join-Path $PSScriptRoot 'build\media.err.log')

$deadline = (Get-Date).AddSeconds($TimeoutSeconds)
while ((Get-Date) -lt $deadline -and -not (Test-Path -LiteralPath $done) -and -not $gradle.HasExited) { Start-Sleep -Seconds 3 }
$finished = Test-Path -LiteralPath $done
Start-Sleep -Seconds 2

$projectName = Split-Path $PSScriptRoot -Leaf
Get-CimInstance Win32_Process -Filter "Name='java.exe'" |
    Where-Object { $_.CommandLine -match [regex]::Escape($projectName) -and $_.CommandLine -notmatch 'GradleDaemon|GradleWrapperMain' } |
    ForEach-Object { Stop-Process -Id $_.ProcessId -Force -Confirm:$false }
[IO.File]::Delete((Join-Path $runDir 'media_request.txt'))

if (-not $finished) { 'Media shoot did NOT finish; see build\media.log and the run log.'; exit 1 }

Get-ChildItem (Join-Path $runDir 'screenshots') -Filter "*_${ModSet}_*.png" | ForEach-Object {
    Copy-Item -LiteralPath $_.FullName -Destination (Join-Path $outDir ($_.Name -replace '^\d+_', '')) -Force
}

$clipDir = Join-Path $runDir "media\$ModSet"

foreach ($clip in 'clip', 'flick') {
    $raw = Join-Path $clipDir "$clip.raw"
    $width, $height, $fps, $frames = (Get-Content -LiteralPath (Join-Path $clipDir "$clip.meta")) -split ' '
    $in = @('-hide_banner', '-loglevel', 'error', '-y', '-f', 'rawvideo', '-pix_fmt', 'rgb0', '-s', "${width}x${height}", '-r', $fps, '-i', $raw)

    & ffmpeg @in -c:v libx264 -pix_fmt yuv420p -crf 18 -movflags +faststart (Join-Path $outDir "${ModSet}_$clip.mp4")
    # GitHub shows an animated WebP committed to the repository inline; it does not do that for MP4.
    & ffmpeg @in -vf 'scale=800:-2:flags=lanczos' -c:v libwebp_anim -q:v 55 -compression_level 6 -loop 0 (Join-Path $outDir "${ModSet}_$clip.webp")

    if ($clip -eq 'clip') {
        & ffmpeg @in -ss 1.30 -frames:v 1 (Join-Path $outDir "${ModSet}_5_mid_turn.png")
        & ffmpeg @in -ss 4.20 -frames:v 1 (Join-Path $outDir "${ModSet}_6_sprinting.png")
    }

    [IO.File]::Delete($raw)
}

"Media shoot finished: ${width}x${height}, $frames frames at $fps fps."
Get-ChildItem $outDir -Filter "${ModSet}_*" | Select-Object Name, Length
