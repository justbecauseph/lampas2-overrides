param(
	[Parameter(Mandatory = $true)]
	[string]$ProductionJar,
	[Parameter(Mandatory = $true)]
	[string]$TrinketsJar,
	[string]$RuntimeClasspathFile = "build\wind-state-probe\frozen3-wilder43\compile-classpath.txt"
)

$ErrorActionPreference = "Stop"
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path

function Resolve-RepoPath([string]$Path) {
	$candidate = if ([System.IO.Path]::IsPathRooted($Path)) { $Path } else { Join-Path $repoRoot $Path }
	return (Resolve-Path -LiteralPath $candidate).Path
}

$productionPath = Resolve-RepoPath $ProductionJar
$trinketsPath = Resolve-RepoPath $TrinketsJar
$classpathFile = Resolve-RepoPath $RuntimeClasspathFile
$runtimeClasspath = (Get-Content -LiteralPath $classpathFile -Raw).Trim()
if ([string]::IsNullOrWhiteSpace($runtimeClasspath)) {
	throw "Runtime classpath file is empty: $classpathFile"
}

$sourceFile = Join-Path $PSScriptRoot "TrinketsDfuProbe.java"
$combinedClasspath = "$productionPath;$trinketsPath;$runtimeClasspath"
$javaArguments = @(
	"-cp", $combinedClasspath,
	$sourceFile,
	"--production-jar", $productionPath,
	"--trinkets-jar", $trinketsPath
)

Write-Host "Running the isolated Trinkets V1460 DFU probe against the supplied production JAR."
& java @javaArguments
if ($LASTEXITCODE -ne 0) {
	exit $LASTEXITCODE
}
