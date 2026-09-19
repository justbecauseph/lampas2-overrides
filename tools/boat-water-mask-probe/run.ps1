param(
    [switch]$Strict,
    [switch]$SkipLaunch,
    [Parameter(Mandatory = $true)]
    [string]$EmfArtifactPath,
    [Parameter(Mandatory = $true)]
    [string]$EmfVersion,
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^[0-9a-fA-F]{64}$')]
    [string]$EmfSha256
)

$ErrorActionPreference = 'Stop'
$configHelper = Join-Path $PSScriptRoot 'frozenlib-config.ps1'
if (-not (Test-Path -LiteralPath $configHelper -PathType Leaf)) {
    throw "Missing FrozenLib fixture config helper: $configHelper"
}
. $configHelper
$repo = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$runDir = Join-Path $repo 'build/boat-mask-smoke'
$probeBuild = Join-Path $repo 'build/boat-mask-smoke-probe'
$buildRoot = (Resolve-Path (Join-Path $repo 'build')).Path
$loaderVersion = '0.19.5'
$activeRoot = 'C:\Users\markj\AppData\Roaming\.minecraft-lampas'
$activeMods = Join-Path $activeRoot 'mods'
$activePacks = Join-Path $activeRoot 'resourcepacks'
$probeJar = Join-Path $runDir 'mods/boat-water-mask-probe.jar'
$expectedEmfHashes = @{
    '3.3.5' = '72b2d489d03bf2ea07b5693ef26cd03572a42dda181e095aed85b3ab39cce549'
    '3.3.8' = '714686cefe56a7e46fa1e13ecdeddcb55ddfbb9715ae5b1ffd7573c1928d9fdd'
}

if (-not $expectedEmfHashes.ContainsKey($EmfVersion)) {
    throw "Unsupported EMF profile version '$EmfVersion'. Supply 3.3.5 or 3.3.8 explicitly."
}
$resolvedEmfArtifact = (Resolve-Path -LiteralPath $EmfArtifactPath -ErrorAction Stop).Path
if (-not (Test-Path -LiteralPath $resolvedEmfArtifact -PathType Leaf)) {
    throw "EMF artifact is not a regular file: $resolvedEmfArtifact"
}
$expectedEmfSha256 = $expectedEmfHashes[$EmfVersion]
if (-not $expectedEmfSha256.Equals($EmfSha256, [StringComparison]::OrdinalIgnoreCase)) {
    throw "EMF hash does not match the locked $EmfVersion profile: supplied=$EmfSha256 expected=$expectedEmfSha256"
}
$actualEmfSha256 = (Get-FileHash -LiteralPath $resolvedEmfArtifact -Algorithm SHA256).Hash.ToLowerInvariant()
if (-not $actualEmfSha256.Equals($EmfSha256.ToLowerInvariant(), [StringComparison]::Ordinal)) {
    throw "EMF artifact hash mismatch before fixture construction: actual=$actualEmfSha256 supplied=$EmfSha256"
}

Add-Type -AssemblyName System.IO.Compression.FileSystem
$emfMetadata = $null
$emfArchive = [IO.Compression.ZipFile]::OpenRead($resolvedEmfArtifact)
try {
    $metadataEntry = $emfArchive.GetEntry('fabric.mod.json')
    if ($null -eq $metadataEntry) {
        throw "EMF artifact has no fabric.mod.json: $resolvedEmfArtifact"
    }
    $metadataReader = [IO.StreamReader]::new($metadataEntry.Open())
    try {
        $emfMetadata = $metadataReader.ReadToEnd() | ConvertFrom-Json
    } finally {
        $metadataReader.Dispose()
    }
} finally {
    $emfArchive.Dispose()
}
if ($emfMetadata.id -ne 'entity_model_features' -or $emfMetadata.version -ne $EmfVersion) {
    throw "EMF metadata identity mismatch: id=$($emfMetadata.id) version=$($emfMetadata.version) expected=entity_model_features/$EmfVersion"
}

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
    $historyRoot = Join-Path $buildRoot 'boat-mask-smoke-history'
    New-Item -ItemType Directory -Force $historyRoot | Out-Null
    $archiveName = Get-Date -Format 'yyyyMMdd-HHmmssfff'
    $archiveDir = Join-Path $historyRoot $archiveName
    $suffix = 0
    while (Test-Path -LiteralPath $archiveDir) {
        $suffix++
        $archiveDir = Join-Path $historyRoot ("{0}-{1:D2}" -f $archiveName, $suffix)
    }
    Move-Item -LiteralPath $runDir -Destination $archiveDir
    Write-Output "Preserved prior fixture evidence at $archiveDir"
}
New-Item -ItemType Directory -Force $runDir, (Join-Path $runDir 'mods'), (Join-Path $runDir 'resourcepacks'), (Join-Path $runDir 'config') | Out-Null
New-Item -ItemType Directory -Force $probeBuild, (Join-Path $probeBuild 'classes') | Out-Null

$fabricApi = Get-ChildItem -LiteralPath $activeMods -Filter 'fabric-api-*.jar' | Sort-Object LastWriteTime -Descending | Select-Object -First 1
if (-not $fabricApi) { throw 'No installed Fabric API jar was found.' }
$fixtureMods = @(
    $fabricApi.FullName,
    $resolvedEmfArtifact,
    (Join-Path $activeMods 'entity_texture_features-7.2.1-26.2-fabric.jar'),
    (Join-Path $activeMods 'pyrite-0.18.3+26.2-fabric.jar'),
    (Join-Path $activeMods 'promenade-5.6.0.jar'),
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
$biolithCandidates = @(Get-ChildItem -LiteralPath $activeMods -Filter 'biolith-*.jar' -File | Sort-Object LastWriteTime -Descending)
if ($biolithCandidates.Count -ne 1) {
    throw "Expected exactly one installed Biolith jar, found $($biolithCandidates.Count): $($biolithCandidates.Name -join ', ')"
}
$fixtureMods += $biolithCandidates[0].FullName
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
$frozenConfigSource = Join-Path $activeRoot 'config/frozenlib/main.json5'
$frozenConfigDestination = Join-Path $runDir 'config/frozenlib/main.json5'
New-Item -ItemType Directory -Force (Split-Path -Parent $frozenConfigDestination) | Out-Null
if (Test-Path -LiteralPath $frozenConfigSource) {
    $frozenConfig = Get-Content -LiteralPath $frozenConfigSource -Raw
    $frozenConfig = Set-FrozenLibPackDownloadingDisabled -Content $frozenConfig
} else {
    $frozenConfig = Set-FrozenLibPackDownloadingDisabled -Content "{`r`n}`r`n"
}
if (-not (Test-FrozenLibPackDownloadingDisabled -Content $frozenConfig)) {
    throw 'FrozenLib fixture config verification failed before evidence recording.'
}
[IO.File]::WriteAllText($frozenConfigDestination, $frozenConfig)

$inputEvidence = Join-Path $runDir 'boat-water-mask-probe-inputs.txt'
@(
    "EMF_MOD_ID=entity_model_features",
    "EMF_VERSION=$EmfVersion",
    "EMF_ARTIFACT_SOURCE=$resolvedEmfArtifact",
    "EMF_ARTIFACT_FILENAME=$([IO.Path]::GetFileName($resolvedEmfArtifact))",
    "EMF_ARTIFACT_SHA256=$actualEmfSha256",
    "BIOLITH_ARTIFACT=$($biolithCandidates[0].FullName)",
    "BIOLITH_FIXTURE_SHA256=$((Get-FileHash -LiteralPath $biolithCandidates[0].FullName -Algorithm SHA256).Hash.ToLowerInvariant())",
    "HARNESS_FROZENLIB_PACK_DOWNLOADING=disabled_fixture_only"
) | Set-Content -LiteralPath $inputEvidence -Encoding utf8

$fixtureEmfArtifact = Join-Path (Join-Path $runDir 'mods') ([IO.Path]::GetFileName($resolvedEmfArtifact))
if (-not (Test-Path -LiteralPath $fixtureEmfArtifact -PathType Leaf)) {
    throw "EMF artifact was not copied into the fixture: $fixtureEmfArtifact"
}
$fixtureEmfSha256 = (Get-FileHash -LiteralPath $fixtureEmfArtifact -Algorithm SHA256).Hash.ToLowerInvariant()
if (-not $fixtureEmfSha256.Equals($actualEmfSha256, [StringComparison]::Ordinal)) {
    throw "Copied EMF artifact hash mismatch: fixture=$fixtureEmfSha256 source=$actualEmfSha256"
}

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
& (Join-Path $repo 'gradlew.bat') -I (Join-Path $PSScriptRoot 'smoke.init.gradle') runClient "-Ploader_version=$loaderVersion" "-PboatProbeStrict=$strictValue" "-PboatProbeEmfVersion=$EmfVersion" "-PboatProbeEmfSha256=$EmfSha256" --no-daemon
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
