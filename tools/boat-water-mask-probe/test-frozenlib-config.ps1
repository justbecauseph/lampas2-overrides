$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'frozenlib-config.ps1')

$expectedDisabledLine = ([char]9).ToString() + 'packDownloading: ' + ([char]34).ToString() + 'pack_downloading.disabled' + ([char]34).ToString() + ','
$expectedConfig = [string]::Join("`r`n", @(
    '{',
    $expectedDisabledLine,
    '}',
    ''
))
$cases = @(
    [pscustomobject]@{
        Name = 'blank line'
        Content = @'
{

    fileTransferClient: true,
}
'@
    },
    [pscustomobject]@{
        Name = 'absent key with trailing comment'
        Content = @'
{
    fileTransferClient: true // preserved comment
}
'@
    },
    [pscustomobject]@{
        Name = 'packDownloading inside multiline comment'
        Content = @'
{
    /*
    packDownloading: "pack_downloading.enabled",
    */
    fileTransferClient: true,
}
'@
    },
    [pscustomobject]@{
        Name = 'existing active key'
        Content = @'
{
    packDownloading: "pack_downloading.enabled",
}
'@
    }
)

foreach ($case in $cases) {
    $result = Set-FrozenLibPackDownloadingDisabled -Content $case.Content
    if (-not (Test-FrozenLibPackDownloadingDisabled -Content $result)) {
        throw "${case.Name}: effective disabled value was not verified"
    }
    if ($result -cne $expectedConfig) {
        throw "${case.Name}: helper did not emit the minimal valid fixture config`n$result"
    }
    Write-Output "PASS $($case.Name)"
}
