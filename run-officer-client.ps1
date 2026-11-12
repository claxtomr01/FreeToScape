param([string]$BotSettingsPath = $env:F2S_BOT_SETTINGS)
$ErrorActionPreference = 'Stop'
if (-not $BotSettingsPath) {
    $localPathFile = Join-Path $PSScriptRoot '.tools/officer-bot-settings-path.txt'
    if (Test-Path -LiteralPath $localPathFile) { $BotSettingsPath = (Get-Content -LiteralPath $localPathFile -Raw).Trim() }
}
if (-not $BotSettingsPath) { throw 'Use Clan activity > Officer setup, or supply -BotSettingsPath pointing to the local bot .env file.' }
$botSettings = $BotSettingsPath
if (-not (Test-Path -LiteralPath $botSettings)) { throw 'Local bot settings were not found.' }
$tokenLine = Get-Content -LiteralPath $botSettings | Where-Object { $_ -match '^CLAN_SYNC_REPORTER_TOKEN=' } | Select-Object -First 1
if (-not $tokenLine) { throw 'Install the clan-sync integration first.' }
$env:CLAN_SYNC_REPORTER_TOKEN = $tokenLine.Substring('CLAN_SYNC_REPORTER_TOKEN='.Length).Trim()
try { & (Join-Path $PSScriptRoot 'run-dev-client.ps1') }
finally { Remove-Item Env:\CLAN_SYNC_REPORTER_TOKEN -ErrorAction SilentlyContinue }
