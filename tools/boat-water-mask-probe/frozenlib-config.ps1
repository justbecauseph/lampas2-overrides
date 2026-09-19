function Set-FrozenLibPackDownloadingDisabled {
    param(
        [Parameter(Mandatory = $true)]
        [AllowEmptyString()]
        [string]$Content
    )

    # The probe only needs this one fixture setting. Replacing the entire copied
    # FrozenLib object avoids interpreting arbitrary JSON5 comments or strings.
    $disabledLine = ([char]9).ToString() + 'packDownloading: ' + ([char]34).ToString() + 'pack_downloading.disabled' + ([char]34).ToString() + ','
    return [string]::Join("`r`n", @(
        '{',
        $disabledLine,
        '}',
        ''
    ))
}

function Test-FrozenLibPackDownloadingDisabled {
    param(
        [Parameter(Mandatory = $true)]
        [AllowEmptyString()]
        [string]$Content
    )

    $minimalConfigPattern = '^\{\r?\n[ \t]*packDownloading[ \t]*:[ \t]*"pack_downloading\.disabled"[ \t]*,[ \t]*\r?\n\}[ \t]*(?:\r?\n)?$'
    return [Regex]::IsMatch($Content, $minimalConfigPattern)
}
