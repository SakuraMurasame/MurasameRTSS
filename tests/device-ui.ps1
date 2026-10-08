# Optional ADB UI helpers for manual acceptance tests. Never installed in the app.
param([Parameter(Mandatory = $true)][string]$Serial, [string]$Adb = 'adb')
$taskUiPath = Join-Path (Split-Path $PSScriptRoot -Parent) 'build\mrtss-ui.xml'
function Read-DeviceUi {
    & $Adb -s $Serial shell uiautomator dump /data/local/tmp/mrtss-ui.xml | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'UI dump failed' }
    & $Adb -s $Serial pull /data/local/tmp/mrtss-ui.xml $taskUiPath 2>&1 | Out-Null
    [xml]$taskDocument = Get-Content -LiteralPath $taskUiPath -Raw -Encoding UTF8
    return $taskDocument
}
function Tap-DeviceText([string]$Text) {
    $taskDocument = Read-DeviceUi
    $taskMatches = @($taskDocument.SelectNodes('//node') | Where-Object { $_.text -eq $Text -and $_.enabled -eq 'true' })
    if ($taskMatches.Count -ne 1) { throw "Expected exactly one enabled item: $Text" }
    $taskCoordinates = @([regex]::Matches($taskMatches[0].bounds, '\d+') | ForEach-Object { [int]$_.Value })
    & $Adb -s $Serial shell input tap ([int](($taskCoordinates[0] + $taskCoordinates[2]) / 2)) ([int](($taskCoordinates[1] + $taskCoordinates[3]) / 2))
}
function Show-DeviceUi {
    (Read-DeviceUi).SelectNodes('//node') | Where-Object { $_.text -or $_.'content-desc' } | ForEach-Object { $_.text + ' ' + $_.'content-desc' + ' ' + $_.bounds }
}
