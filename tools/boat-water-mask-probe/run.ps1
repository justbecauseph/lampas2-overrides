param(
    [switch]$Strict,
    [switch]$SkipLaunch
)

$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$runDir = Join-Path $repo 'build/boat-mask-smoke'
$probeBuild = Join-Path $repo 'build/boat-mask-smoke-probe'
$buildRoot = (Resolve-Path (Join-Path $repo 'build')).Path
$loaderVersion = '0.19.5'
$activeRoot = 'C:\Users\markj\AppData\Roaming\.minecraft-lampas'
$activeMods = Join-Path $activeRoot 'mods'
$activePacks = Join-Path $activeRoot 'resourcepacks'
$probeJar = Join-Path $runDir 'mods/boat-water-mask-probe.jar'

if (Test-Path -LiteralPath $runDir) {
    $resolvedBuildRoot = [IO.Path]::GetFullPath($buildRoot).TrimEnd('\\')
    $resolvedRunDir = [IO.Path]::GetFullPath($runDir).TrimEnd('\\')
    $expectedRunDir = [IO.Path]::GetFullPath((Join-Path $buildRoot 'boat-mask-smoke')).TrimEnd('\\')
    $buildPrefix = $resolvedBuildRoot + [IO.Path]::DirectorySeparatorChar
    if ((-not $resolvedRunDir.Equals($expectedRunDir, [StringComparison]::OrdinalIgnoreCase)) -or ($resolvedRunDir.Equals($resolvedBuildRoot, [StringComparison]::OrdinalIgnoreCase)) -or (-not $resolvedRunDir.StartsWith($buildPrefix, [StringComparison]::OrdinalIgnoreCase))) {
        throw "Refusing recursive delete outside the exact build/boat-mask-smoke fixture: $runDir"
    }
    $existingRunDir = (Resolve-Path -LiteralPath $runDir).Path
    if (-not (([IO.Path]::GetFullPath($existingRunDir).TrimEnd('\\')).Equals($expectedRunDir, [StringComparison]::OrdinalIgnoreCase))) {
        throw "Refusing recursive delete through a redirected fixture path: $existingRunDir"
    }
    Remove-Item -LiteralPath $runDir -Recurse -Force
}
New-Item -ItemType Directory -Force $runDir, (Join-Path $runDir 'mods'), (Join-Path $runDir 'resourcepacks'), (Join-Path $runDir 'config') | Out-Null
New-Item -ItemType Directory -Force $probeBuild, (Join-Path $probeBuild 'classes') | Out-Null

$fabricApi = Get-ChildItem -LiteralPath $activeMods -Filter 'fabric-api-*.jar' | Sort-Object LastWriteTime -Descending | Select-Object -First 1
if (-not $fabricApi) { throw 'No installed Fabric API jar was found.' }
$fixtureMods = @(
    $fabricApi.FullName,
    (Join-Path $activeMods 'entity_model_features-3.3.5-26.2-fabric.jar'),
    (Join-Path $activeMods 'entity_texture_features-7.2.1-26.2-fabric.jar'),
    (Join-Path $activeMods 'pyrite-0.18.3+26.2-fabric.jar'),
    (Join-Path $activeMods 'promenade-5.6.0.jar'),
    (Join-Path $activeMods 'biolith-fabric-3.7.0-alpha.2.jar'),
    (Join-Path $activeMods 'WilderWild-4.2.11-mc26.2.jar'),
    (Join-Path $activeMods 'FrozenLib-2.5.3-mc26.2.jar'),
    (Join-Path $activeMods 'better-end-26.201.2.jar'),
    (Join-Path $activeMods 'better-nether-26.201.2.jar'),
    (Join-Path $activeMods 'bclib-26.201.2.jar'),
    (Join-Path $activeMods 'worldweaver-26.201.2.jar'),
    (Join-Path $activeMods 'wunderlib-26.201.0.jar'),
    (Join-Path $activeMods 'letsdo-bloomingnature-fabric-1.1.10.jar'),
    (Join-Path $activeMods 'architectury-fabric-21.1.9.jar')
)
foreach ($mod in $fixtureMods) {
    if (-not (Test-Path -LiteralPath $mod)) { throw "Missing fixture jar: $mod" }
    Copy-Item -LiteralPath $mod -Destination (Join-Path $runDir 'mods')
}
$faZip = Join-Path $activePacks 'FA+All_Extensions-v1.9.2.zip'
if (-not (Test-Path -LiteralPath $faZip)) { throw "Missing fixture resource pack: $faZip" }
Copy-Item -LiteralPath $faZip -Destination (Join-Path $runDir 'resourcepacks')
$faBaseZip = Join-Path $activePacks 'FreshAnimations_v1.10.5.zip'
if (-not (Test-Path -LiteralPath $faBaseZip)) { throw "Missing fixture resource pack: $faBaseZip" }
Copy-Item -LiteralPath $faBaseZip -Destination (Join-Path $runDir 'resourcepacks')
$faPlayerZip = Join-Path $activePacks 'FA+Player-v1.1.zip'
if (-not (Test-Path -LiteralPath $faPlayerZip)) { throw "Missing fixture resource pack: $faPlayerZip" }
Copy-Item -LiteralPath $faPlayerZip -Destination (Join-Path $runDir 'resourcepacks')
$emfConfig = Join-Path $activeRoot 'config/entity_model_features.json'
if (Test-Path -LiteralPath $emfConfig) { Copy-Item -LiteralPath $emfConfig -Destination (Join-Path $runDir 'config') }

& (Join-Path $repo 'gradlew.bat') -I (Join-Path $PSScriptRoot 'smoke.init.gradle') boatProbeClasspath "-Ploader_version=$loaderVersion" --no-daemon
if ($LASTEXITCODE -ne 0) { throw "Gradle classpath task failed with exit code $LASTEXITCODE" }
$classpath = (Get-Content -LiteralPath (Join-Path $probeBuild 'classpath.txt') -Raw).Trim()
$source = Join-Path $PSScriptRoot 'BoatWaterMaskProbe.java'
& javac --release 25 -cp $classpath -d (Join-Path $probeBuild 'classes') $source
if ($LASTEXITCODE -ne 0) { throw "Probe compilation failed with exit code $LASTEXITCODE" }
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'fabric.mod.json') -Destination (Join-Path $probeBuild 'classes')
& jar --create --file $probeJar -C (Join-Path $probeBuild 'classes') .
if ($LASTEXITCODE -ne 0) { throw "Probe jar creation failed with exit code $LASTEXITCODE" }

if ($SkipLaunch) {
    Write-Output "Prepared isolated fixture under $runDir"
    exit 0
}

$strictValue = if ($Strict) { 'true' } else { 'false' }
& (Join-Path $repo 'gradlew.bat') -I (Join-Path $PSScriptRoot 'smoke.init.gradle') runClient "-Ploader_version=$loaderVersion" "-PboatProbeStrict=$strictValue" --no-daemon
$gradleExit = $LASTEXITCODE
if ($gradleExit -ne 0) {
    exit $gradleExit
}

$resultPath = Join-Path $runDir 'boat-water-mask-probe-result.txt'
if (-not (Test-Path -LiteralPath $resultPath)) {
    Write-Error "Probe exited successfully without a result artifact: $resultPath"
    exit 1
}
$probeResult = Get-Content -LiteralPath $resultPath -Raw
$requiredStages = @('ON-1', 'OFF', 'ON-2', 'OFF-2')
$missingStages = @(
    $requiredStages | Where-Object {
        $stage = [Regex]::Escape($_)
        $probeResult -notmatch "(?m)^BOAT_PROBE_CHECK stage=$stage PASS\s*$"
    }
)
if (($probeResult -notmatch '(?m)^BOAT_PROBE_RESULT PASS\s*$') -or $missingStages.Count -gt 0) {
    $missing = if ($missingStages.Count -gt 0) { $missingStages -join ', ' } else { 'none' }
    Write-Error "Probe result is incomplete or failed; missing stage PASS: $missing"
    exit 1
}
Write-Output 'Boat water-mask probe completed PASS.'
exit 0
