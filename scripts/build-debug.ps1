$ErrorActionPreference = "Stop"
. (Join-Path $PSScriptRoot "dev-env.ps1")

Set-Location -LiteralPath $ProjectRoot
& (Join-Path $ProjectRoot "gradlew.bat") assembleDebug
exit $LASTEXITCODE
