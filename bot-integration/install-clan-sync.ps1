param([Parameter(Mandatory = $true)][string]$BotDirectory)
$ErrorActionPreference = 'Stop'
$target = (Resolve-Path -LiteralPath $BotDirectory).Path
$botPath = Join-Path $target 'bot.py'
$source = [IO.File]::ReadAllText($botPath)
$anchor = '            install(bot, ROOT, read_scores)'
if (-not $source.Contains($anchor)) { throw 'Bot startup differs from the reviewed source. Reconcile it before installation.' }
$backup = Join-Path $target ('clan-sync-backup-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
New-Item -ItemType Directory -Path $backup | Out-Null
foreach ($name in @('bot.py','.env','clan_sync.py','clan_sync_roles.json')) {
    $path = Join-Path $target $name
    if (Test-Path -LiteralPath $path) { Copy-Item -LiteralPath $path -Destination (Join-Path $backup $name) }
}
if (-not $source.Contains('install_clan_sync(bot, ROOT)')) {
    $source = $source.Replace($anchor, $anchor + "`n            from clan_sync import install as install_clan_sync`n            install_clan_sync(bot, ROOT)")
}
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'clan_sync.py') -Destination (Join-Path $target 'clan_sync.py')
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'clan_sync_roles.json') -Destination (Join-Path $target 'clan_sync_roles.json')
[IO.File]::WriteAllText($botPath, $source, [Text.UTF8Encoding]::new($false))
$envPath = Join-Path $target '.env'
$settings = if (Test-Path -LiteralPath $envPath) { [IO.File]::ReadAllText($envPath) } else { '' }
$values = @{
    CLAN_SYNC_ENABLED='true';
    CLAN_SYNC_APPLY_ROLES='false';
    CLAN_SYNC_PORT='8788';
    CLAN_SYNC_STAFF_CHANNEL_ID='1255502022237491244'
}
if ($settings -notmatch '(?m)^CLAN_SYNC_REPORTER_TOKEN=.{32,}') {
    $randomBytes = New-Object byte[] 48
    $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($randomBytes) } finally { $rng.Dispose() }
    $values['CLAN_SYNC_REPORTER_TOKEN'] = [Convert]::ToBase64String($randomBytes).Replace('+','-').Replace('/','_').TrimEnd('=')
}
foreach ($entry in $values.GetEnumerator()) {
    $pattern = '(?m)^' + [regex]::Escape($entry.Key) + '=[^\r\n]*'
    $replacement = $entry.Key + '=' + $entry.Value
    if ([regex]::IsMatch($settings,$pattern)) { $settings = [regex]::Replace($settings,$pattern,$replacement) }
    else { $settings += "`r`n" + $replacement + "`r`n" }
}
[IO.File]::WriteAllText($envPath,$settings,[Text.UTF8Encoding]::new($false))
Write-Output "Clan sync installed in dry-run mode. Backup: $backup"
Write-Output 'Bot was not started or restarted. Existing bounty feed, schedules and credentials were retained. No Discord roles were changed.'
