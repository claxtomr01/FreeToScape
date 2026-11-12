$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot
$jdkDirectory = Join-Path $PSScriptRoot '.tools/jdk'
if (Test-Path -LiteralPath $jdkDirectory) {
    $env:JAVA_HOME = (Get-ChildItem -LiteralPath $jdkDirectory -Directory | Select-Object -First 1).FullName
}
if (-not $env:JAVA_HOME) { throw 'Install JDK 17 and set JAVA_HOME first.' }
$env:GRADLE_USER_HOME = Join-Path $PSScriptRoot '.tools/gradle-home'
& (Join-Path $PSScriptRoot 'gradlew.bat') run --no-daemon
