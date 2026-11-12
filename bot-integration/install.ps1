param([Parameter(Mandatory = $true)][string]$BotDirectory)
$ErrorActionPreference = 'Stop'
$target = $BotDirectory
$target = (Resolve-Path -LiteralPath $target).Path
$originalHash = 'DBB5F631BB504AED522AB613D028D58F5A868141FD088DFC2BA71C2F571760BF'
if ((Get-FileHash -LiteralPath (Join-Path $target 'bot.py') -Algorithm SHA256).Hash -ne $originalHash) {
    throw 'Existing bot.py changed since inspection. Reconcile changes before installing.'
}
$backup = Join-Path $target ('runelite-integration-backup-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
New-Item -ItemType Directory -Path $backup | Out-Null
foreach ($name in @('bot.py', 'runelite_feed.py', '.env')) {
    $source = Join-Path $target $name
    if (Test-Path -LiteralPath $source) { Copy-Item -LiteralPath $source -Destination (Join-Path $backup $name) }
}
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'runelite_feed.py') -Destination (Join-Path $target 'runelite_feed.py')
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'bot.py') -Destination (Join-Path $target 'bot.py')
$envPath = Join-Path $target '.env'
$settings = if (Test-Path -LiteralPath $envPath) { [IO.File]::ReadAllText($envPath) } else { '' }
if ($settings -match '(?m)^RUNELITE_FEED_ENABLED=') {
    $settings = [regex]::Replace($settings, '(?m)^RUNELITE_FEED_ENABLED=[^\r\n]*', 'RUNELITE_FEED_ENABLED=true')
} else { $settings += "`r`nRUNELITE_FEED_ENABLED=true`r`n" }
[IO.File]::WriteAllText($envPath, $settings, [Text.UTF8Encoding]::new($false))
Write-Output "Installed feed integration. Backup: $backup"
Write-Output 'Existing credentials, challenge lists and schedule state retained. Bot was not started.'
