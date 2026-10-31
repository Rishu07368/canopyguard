$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
Push-Location $root
try {
    & (Join-Path $PSScriptRoot "test.ps1")
    if ($LASTEXITCODE -ne 0) { throw "Automated tests failed" }
    & mvn -B package
    if ($LASTEXITCODE -ne 0) { throw "Maven package failed with exit code $LASTEXITCODE" }
    $jpackage = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME "bin\jpackage.exe" } else { "jpackage.exe" }
    if (-not (Get-Command $jpackage -ErrorAction SilentlyContinue)) { throw "jpackage was not found. Install a full JDK and set JAVA_HOME." }
    $stamp = Get-Date -Format "yyyyMMdd-HHmmss"
    $release = Join-Path $root "dist\CanopyGuard-Windows-$stamp"
    $input = Join-Path $root "target\package-input"
    New-Item -ItemType Directory -Force -Path $release, $input | Out-Null
    Copy-Item (Join-Path $root "target\canopyguard-1.0.0.jar") $input
    & $jpackage --type app-image --name CanopyGuard --app-version 1.0.0 --vendor CanopyGuard `
        --description "Offline rainforest wildfire planning prototype" --input $input `
        --main-jar canopyguard-1.0.0.jar --main-class com.canopyguard.app.CanopyGuardLauncher `
        --dest $release --java-options "--enable-native-access=ALL-UNNAMED"
    if ($LASTEXITCODE -ne 0) { throw "jpackage failed with exit code $LASTEXITCODE" }
    $image = Join-Path $release "CanopyGuard"
    $launcher = Join-Path $image "CanopyGuard.exe"
    if (-not (Test-Path $launcher)) { throw "Expected launcher not found: $launcher" }
    $zip = Join-Path $release "CanopyGuard-Windows.zip"
    Compress-Archive -Path $image -DestinationPath $zip -CompressionLevel Optimal
    if (-not (Test-Path $zip)) { throw "ZIP creation failed: $zip" }
    Write-Output "Application image: $image"
    Write-Output "Distribution ZIP: $zip"
} finally {
    Pop-Location
}
