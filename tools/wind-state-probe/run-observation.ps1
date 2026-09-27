[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [ValidatePattern('^[a-z0-9][a-z0-9.-]{1,63}$')]
    [string]$RunId,

    [Parameter(Mandatory)]
    [string]$FrozenLibPath,

    [Parameter(Mandatory)]
    [ValidatePattern('^[0-9a-fA-F]{64}$')]
    [string]$FrozenLibSha256,

    [Parameter(Mandatory)]
    [string]$WilderWildPath,

    [Parameter(Mandatory)]
    [ValidatePattern('^[0-9a-fA-F]{64}$')]
    [string]$WilderWildSha256,

    [Parameter(Mandatory)]
    [string]$FabricApiPath,

    [Parameter(Mandatory)]
    [ValidatePattern('^[0-9a-fA-F]{64}$')]
    [string]$FabricApiSha256
)

$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$runRoot = Join-Path $repo "build\wind-state-probe\$RunId"
$mods = Join-Path $runRoot 'mods'
$classes = Join-Path $runRoot 'probe-classes'
$classpathFile = Join-Path $runRoot 'compile-classpath.txt'
$initScript = Join-Path $PSScriptRoot 'wind-observation.init.gradle'
$resultPath = Join-Path $runRoot 'wind-observation-result.txt'
$runLog = Join-Path $runRoot 'gradle-run.log'
$latestLog = Join-Path $runRoot 'client-latest.log'
$debugLog = Join-Path $runRoot 'client-debug.log'

if (Test-Path -LiteralPath $runRoot) {
    throw "Refusing to overwrite preserved probe output: $runRoot"
}

$inputs = @(
    @{ Name = 'FrozenLib'; Path = $FrozenLibPath; Expected = $FrozenLibSha256 },
    @{ Name = 'Wilder Wild'; Path = $WilderWildPath; Expected = $WilderWildSha256 },
    @{ Name = 'Fabric API'; Path = $FabricApiPath; Expected = $FabricApiSha256 }
)
foreach ($input in $inputs) {
    $resolved = (Resolve-Path -LiteralPath $input.Path -ErrorAction Stop).Path
    $actual = (Get-FileHash -LiteralPath $resolved -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($actual -ne $input.Expected.ToLowerInvariant()) {
        throw "$($input.Name) SHA-256 mismatch: expected $($input.Expected), got $actual ($resolved)"
    }
    $input.Resolved = $resolved
    $input.Actual = $actual
}

New-Item -ItemType Directory -Force -Path $mods, $classes | Out-Null
$runtimeInputs = @()
foreach ($input in $inputs) {
    $destination = Join-Path $mods (Split-Path -Leaf $input.Resolved)
    Copy-Item -LiteralPath $input.Resolved -Destination $destination
    $runtimeInputs += [pscustomobject]@{
        name = $input.Name
        sourcePath = $input.Resolved
        runtimePath = $destination
        sha256 = $input.Actual
    }
}

$attestation = [pscustomobject]@{
    runId = $RunId
    mode = 'unpatched-observation'
    overrideModDisabled = 'lampas2-overrides'
    inputs = $runtimeInputs
    runtimeMods = @(Get-ChildItem -LiteralPath $mods -File | Select-Object -ExpandProperty Name)
}
$attestation | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $runRoot 'artifact-attestation.json') -Encoding utf8

$relativeRunRoot = [IO.Path]::GetRelativePath($repo, $runRoot).Replace('\', '/')
$relativeClasspath = [IO.Path]::GetRelativePath($repo, $classpathFile).Replace('\', '/')

Push-Location $repo
try {
    & .\gradlew.bat -I $initScript windObservationClasspath `
        "-PwindObservationRunDir=$relativeRunRoot" `
        "-PwindObservationClasspathFile=$relativeClasspath" --no-daemon *>&1 |
        Tee-Object -FilePath (Join-Path $runRoot 'gradle-classpath.log')
    if ($LASTEXITCODE -ne 0) { throw "windObservationClasspath failed with exit code $LASTEXITCODE" }

    if (!(Test-Path -LiteralPath $classpathFile -PathType Leaf)) {
        throw "Gradle did not write probe classpath: $classpathFile"
    }
    $projectClasspath = (Get-Content -Raw -LiteralPath $classpathFile).Trim()
    $candidateClasspath = @($inputs | ForEach-Object { $_.Resolved }) -join [IO.Path]::PathSeparator
    $probeClasspath = $candidateClasspath + [IO.Path]::PathSeparator + $projectClasspath
    $probeSource = Join-Path $PSScriptRoot 'WindStateObservationProbe.java'
    & javac -cp $probeClasspath -d $classes $probeSource *>&1 |
        Tee-Object -FilePath (Join-Path $runRoot 'javac.log')
    if ($LASTEXITCODE -ne 0) { throw "javac failed with exit code $LASTEXITCODE" }

    $metadata = @{
        schemaVersion = 1
        id = 'wind-state-observation-probe'
        version = '1.0.0'
        environment = 'client'
        entrypoints = @{ client = @('WindStateObservationProbe') }
        depends = @{
            minecraft = '~26.2'
            fabricloader = '>=0.19.3'
            'fabric-api' = '*'
            frozenlib = '*'
            wilderwild = '*'
        }
    } | ConvertTo-Json -Depth 4 -Compress
    [IO.File]::WriteAllText((Join-Path $classes 'fabric.mod.json'), $metadata, [Text.UTF8Encoding]::new($false))
    $probeJar = Join-Path $mods 'wind-state-observation-probe.jar'
    & jar cf $probeJar -C $classes . *>&1 |
        Tee-Object -FilePath (Join-Path $runRoot 'jar.log')
    if ($LASTEXITCODE -ne 0) { throw "jar failed with exit code $LASTEXITCODE" }

    & .\gradlew.bat -I $initScript runClient `
        "-PwindObservationRunDir=$relativeRunRoot" `
        "-PwindObservationClasspathFile=$relativeClasspath" --no-daemon *>&1 |
        Tee-Object -FilePath $runLog
    $runExit = $LASTEXITCODE

    $actualResult = Join-Path $runRoot 'wind-observation-result.txt'
    if (!(Test-Path -LiteralPath $actualResult -PathType Leaf)) {
        throw "Probe did not write $actualResult"
    }
    $result = Get-Content -Raw -LiteralPath $actualResult
    if ($result -notmatch '(?m)^status=PASS\s*$') {
        Write-Output $result
        throw "Probe did not complete its observation capture; see $actualResult"
    }
    if ($runExit -ne 0) { throw "runClient failed with exit code $runExit; see $runLog" }

    $gameLog = Join-Path $runRoot 'logs\latest.log'
    $gameDebugLog = Join-Path $runRoot 'logs\debug.log'
    if (Test-Path -LiteralPath $gameLog -PathType Leaf) { Copy-Item $gameLog $latestLog }
    if (Test-Path -LiteralPath $gameDebugLog -PathType Leaf) { Copy-Item $gameDebugLog $debugLog }
    Get-Content -LiteralPath $actualResult
    Write-Output "run.exitCode=$runExit"
    Write-Output "run.output=$runRoot"
} finally {
    Pop-Location
}
