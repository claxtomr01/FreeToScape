$ErrorActionPreference = 'Stop'
$projectRoot = $PSScriptRoot
$exportRoot = Join-Path $projectRoot ('share/FreeToScape-plugin-hub-' + (Get-Date -Format 'yyyyMMdd-HHmmss-fff'))
New-Item -ItemType Directory -Path $exportRoot | Out-Null

# Export only public plugin source and build inputs, never the live bot folder.
$files = @('.gitignore', 'LICENSE', 'runelite-plugin.properties', 'build.gradle',
    'settings.gradle', 'gradlew', 'gradlew.bat', 'icon.png')
foreach ($file in $files) {
    Copy-Item -LiteralPath (Join-Path $projectRoot $file) -Destination $exportRoot
}
Copy-Item -LiteralPath (Join-Path $projectRoot 'PLUGIN-HUB-README.md') -Destination (Join-Path $exportRoot 'README.md')
foreach ($relativeRoot in @('src/main', 'src/test', 'gradle/wrapper')) {
    $sourceRoot = Join-Path $projectRoot $relativeRoot
    foreach ($file in Get-ChildItem -LiteralPath $sourceRoot -File -Recurse) {
        $relative = $file.FullName.Substring($projectRoot.Length + 1)
        if ($relative -notmatch '\.(java|jar|properties|png)$' -and $relative -notmatch 'src[\\/]main[\\/]resources[\\/]META-INF[\\/]LICENSE$') {
            throw "Unexpected source/build input: $relative"
        }
        $destination = Join-Path $exportRoot $relative
        New-Item -ItemType Directory -Force -Path (Split-Path -Parent $destination) | Out-Null
        Copy-Item -LiteralPath $file.FullName -Destination $destination
    }
}
$archive = $exportRoot + '.zip'
Compress-Archive -Path (Join-Path $exportRoot '*') -DestinationPath $archive
Write-Output "Public source folder: $exportRoot"
Write-Output "Source archive: $archive"
Write-Output 'This prepares source only. Nothing has been published to GitHub or RuneLite.'
