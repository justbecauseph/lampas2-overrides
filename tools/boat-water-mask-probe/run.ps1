param(
    [ValidateSet('baseline', 'patched')]
    [string]$Mode = 'baseline',
    [Parameter(Mandatory = $true)]
    [string]$InventoryPath,
    [string]$OverrideArtifactPath,
    [string]$MinecraftRoot,
    [string]$EmfArtifactPath,
    [string]$EmfVersion,
    [ValidatePattern('^$|^[0-9a-fA-F]{64}$')]
    [string]$EmfSha256,
    [int]$TimeoutSeconds = 300,
    [switch]$Strict,
    [switch]$SkipLaunch
)

$ErrorActionPreference = 'Stop'
$probeArguments = @((Join-Path $PSScriptRoot 'run.py'), $Mode, '--inventory', $InventoryPath,
    '--timeout-seconds', $TimeoutSeconds)
if ($OverrideArtifactPath) { $probeArguments += @('--override-jar', $OverrideArtifactPath) }
if ($MinecraftRoot) { $probeArguments += @('--minecraft-root', $MinecraftRoot) }
if ($EmfArtifactPath) { $probeArguments += @('--emf-artifact', $EmfArtifactPath) }
if ($EmfVersion) { $probeArguments += @('--emf-version', $EmfVersion) }
if ($EmfSha256) { $probeArguments += @('--emf-sha256', $EmfSha256) }
if ($Strict) { $probeArguments += '--strict' }
if ($SkipLaunch) { $probeArguments += '--skip-launch' }
& python @probeArguments
exit $LASTEXITCODE
