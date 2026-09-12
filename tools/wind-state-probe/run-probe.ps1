[CmdletBinding()]
param(
    [ValidateSet('patched', 'baseline')]
    [string]$Mode = 'patched'
)

$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$smokeRoot = Join-Path $repo 'build\wind-smoke'
$probeRoot = Join-Path $repo 'build\wind-smoke-probe'
$mods = Join-Path $smokeRoot 'mods'
$classes = Join-Path $probeRoot 'classes'
$initScript = Join-Path $PSScriptRoot 'wind-smoke.init.gradle'
$probeSources = Get-ChildItem -LiteralPath $PSScriptRoot -Filter '*.java' -File |
    Sort-Object Name |
    Select-Object -ExpandProperty FullName
$probeJar = Join-Path $mods 'wind-state-probe.jar'

New-Item -ItemType Directory -Force -Path $mods, $classes | Out-Null
$resultPath = Join-Path $smokeRoot 'wind-probe-result.txt'
if (Test-Path -LiteralPath $resultPath -PathType Leaf) {
    [IO.File]::Delete($resultPath)
}

$requiredJars = @(
    (Join-Path $mods 'FrozenLib-2.5.3-mc26.2.jar'),
    (Join-Path $mods 'WilderWild-4.2.11-mc26.2.jar'),
    (Join-Path $mods 'fabric-api-0.160.0+26.2.jar')
)
foreach ($jar in $requiredJars) {
    if (!(Test-Path -LiteralPath $jar -PathType Leaf)) {
        throw "Missing audited jar: $jar"
    }
}

Push-Location $repo
try {
    & .\gradlew.bat -I $initScript windProbeClasspath --no-daemon
    if ($LASTEXITCODE -ne 0) { throw "windProbeClasspath failed with exit code $LASTEXITCODE" }

    $probeClasspath = (Get-Content -Raw (Join-Path $probeRoot 'classpath.txt')).Trim()
    & javac -cp $probeClasspath -d $classes $probeSources
    if ($LASTEXITCODE -ne 0) { throw "javac failed with exit code $LASTEXITCODE" }

    $fabricMetadata = @{
        schemaVersion = 1
        id = 'wind-state-probe'
        version = '1.0.0'
        environment = 'client'
        entrypoints = @{ client = @('WindStateProbe') }
        mixins = @('wind-state-probe.mixins.json')
        depends = @{ minecraft = '~26.2'; fabricloader = '>=0.19.3'; frozenlib = '*'; wilderwild = '*' }
    } | ConvertTo-Json -Depth 4 -Compress
    [IO.File]::WriteAllText((Join-Path $classes 'fabric.mod.json'), $fabricMetadata, [Text.UTF8Encoding]::new($false))
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'wind-state-probe.mixins.json') -Destination $classes
    & jar cf $probeJar -C $classes .
    if ($LASTEXITCODE -ne 0) { throw "jar failed with exit code $LASTEXITCODE" }

    $configDir = Join-Path $smokeRoot 'config\frozenlib'
    New-Item -ItemType Directory -Force -Path $configDir | Out-Null
    $configPath = Join-Path $configDir 'main.json5'
    [IO.File]::WriteAllText($configPath, "{`n  packDownloading: 'pack_downloading.disabled'`n}`n", [Text.UTF8Encoding]::new($false))

    & .\gradlew.bat -I $initScript runClient "-PwindProbeMode=$Mode" --no-daemon
    $runExit = $LASTEXITCODE
    if (!(Test-Path -LiteralPath $resultPath -PathType Leaf)) {
        throw "Probe did not write $resultPath"
    }
    $result = Get-Content -Raw -LiteralPath $resultPath
    $expectedMode = "expectPatched=$($Mode -eq 'patched')"
    if ($result -notmatch '(?m)^status=PASS\s*$' -or $result -notmatch [regex]::Escape($expectedMode)) {
        Write-Output $result
        throw "Probe result did not pass for mode '$Mode'"
    }
    Get-Content -LiteralPath $resultPath
    if ($runExit -ne 0) { throw "runClient failed with exit code $runExit" }
} finally {
    Pop-Location
}
