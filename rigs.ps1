# Compatibility rigs for section 5 of BETA-CHECKLIST.md: small sets of other mods to test with.
#
#   .\rigs.ps1 -GameVersion 1.21.11 -Download        fetch every rig's mods from Modrinth into rigs\<version>\
#   .\rigs.ps1 -GameVersion 1.21.11 -Use A,B         fill the test client's mods folder with rigs A and B
#   .\rigs.ps1 -Use none                             empty the test client's mods folder
#
# Rigs live outside build\ so "gradlew clean" does not delete them. Required dependencies are
# fetched too (Fabric API excepted: the test client already has it) and checksums are verified.
param(
    [string]$GameVersion = '',
    [switch]$Download,
    [string[]]$Use = @()
)

$rigs = [ordered]@{
    # What nearly everyone runs; these change how the game draws.
    'A'      = 'sodium', 'lithium', 'ferrite-core', 'entityculling', 'immediatelyfast', 'modmenu', 'cloth-config'
    'A-iris' = 'iris'
    # The common HUD mods, one of each kind.
    'B'      = 'xaeros-minimap', 'journeymap', 'jade', 'appleskin', 'jei', 'simple-voice-chat'
    # Rarely installed, but they draw or move HUD parts in unusual ways.
    'C'      = 'bedrockify', 'raised', 'scoreboard-overhaul', 'chatanimation', 'smooth-gui', 'inventory-profiles-next'
}
$fabricApi = 'P7dR8mSH'
$root = Join-Path $PSScriptRoot 'rigs'
$testMods = Join-Path $PSScriptRoot 'build\run\clientGameTest\mods'

function Get-ModVersion([string]$project) {
    $query = 'game_versions=' + [uri]::EscapeDataString("[`"$GameVersion`"]") + '&loaders=' + [uri]::EscapeDataString('["fabric"]')
    try { $response = Invoke-RestMethod "https://api.modrinth.com/v2/project/$project/version?$query" } catch { return $null }
    # Windows PowerShell hands the JSON array back as one object; unroll it into separate versions.
    $versions = @($response | ForEach-Object { $_ })
    $release = $versions | Where-Object version_type -eq 'release' | Select-Object -First 1
    if ($release) { $release } else { $versions | Select-Object -First 1 }
}

function Save-Mod([string]$project, [string]$folder, [System.Collections.Generic.HashSet[string]]$seen, [string]$because) {
    if (-not $seen.Add($project)) { return }
    $version = Get-ModVersion $project
    if (-not $version) { "  MISSING  $project has no Fabric build for $GameVersion$because"; return }

    $file = @($version.files | Where-Object primary)[0]
    if (-not $file) { $file = $version.files[0] }
    $target = Join-Path $folder $file.filename

    if (-not (Test-Path -LiteralPath $target)) { Invoke-WebRequest $file.url -OutFile $target -UseBasicParsing }
    $ok = (Get-FileHash -LiteralPath $target -Algorithm SHA512).Hash -eq $file.hashes.sha512
    "  {0}  {1}  ({2}, {3}){4}" -f $(if ($ok) { 'ok     ' } else { 'BAD SUM' }), $file.filename, $version.version_number, $version.version_type, $because

    foreach ($dependency in $version.dependencies) {
        if ($dependency.dependency_type -eq 'required' -and $dependency.project_id -and $dependency.project_id -ne $fabricApi) {
            Save-Mod $dependency.project_id $folder $seen "  <- needed by $project"
        }
    }
}

if ($Download) {
    if (-not $GameVersion) { throw 'Give -GameVersion with -Download.' }
    foreach ($rig in $rigs.Keys) {
        $folder = Join-Path (Join-Path $root $GameVersion) $rig
        New-Item -ItemType Directory -Force $folder | Out-Null
        "Rig $rig"
        $seen = New-Object 'System.Collections.Generic.HashSet[string]'
        foreach ($project in $rigs[$rig]) { Save-Mod $project $folder $seen '' }
    }
}

if ($Use.Count -gt 0) {
    New-Item -ItemType Directory -Force $testMods | Out-Null
    Get-ChildItem -LiteralPath $testMods -File | ForEach-Object { [IO.File]::Delete($_.FullName) }

    if ($Use -notcontains 'none') {
        if (-not $GameVersion) { throw 'Give -GameVersion with -Use.' }
        foreach ($rig in $Use) {
            $folder = Join-Path (Join-Path $root $GameVersion) $rig
            if (-not (Test-Path -LiteralPath $folder)) { throw "No rig '$rig' for $GameVersion; run with -Download first." }
            # Two rigs can share a dependency; the same file name simply overwrites.
            Get-ChildItem -LiteralPath $folder -Filter *.jar | Copy-Item -Destination $testMods -Force
        }
    }

    "Test client mods: $((Get-ChildItem -LiteralPath $testMods -Filter *.jar).Count)  (rigs: $($Use -join ', '))"
}
