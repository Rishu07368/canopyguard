$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
Push-Location $root
try {
    $jar = Join-Path $root "target\canopyguard-1.0.0.jar"
    if (-not (Test-Path $jar)) {
        & mvn -B package
        if ($LASTEXITCODE -ne 0) { throw "Maven package failed with exit code $LASTEXITCODE" }
    }
    & java -jar $jar
    if ($LASTEXITCODE -ne 0) { throw "Application exited with code $LASTEXITCODE" }
} finally {
    Pop-Location
}
