$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
Push-Location $root
try {
    & mvn -B test
    if ($LASTEXITCODE -ne 0) { throw "Maven tests failed with exit code $LASTEXITCODE" }
} finally {
    Pop-Location
}
