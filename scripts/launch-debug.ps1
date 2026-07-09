$ErrorActionPreference = "Stop"
. (Join-Path $PSScriptRoot "dev-env.ps1")

& (Join-Path $env:ANDROID_HOME "platform-tools\adb.exe") shell monkey -p org.zenconverter.app -c android.intent.category.LAUNCHER 1
exit $LASTEXITCODE
