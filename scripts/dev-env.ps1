$ProjectRoot = Split-Path -Parent $PSScriptRoot
$AndroidDev = "E:\AndroidDev"
$JavaHome = Join-Path $AndroidDev "JDK\jdk-17.0.15+6"
$AndroidHome = Join-Path $AndroidDev "SDK"
$GradleUserHome = Join-Path $AndroidDev ".gradle"

foreach ($PathToCheck in @($AndroidDev, $JavaHome, $AndroidHome, $GradleUserHome)) {
    if (-not (Test-Path -LiteralPath $PathToCheck)) {
        throw "Missing required development path: $PathToCheck"
    }
}

$env:JAVA_HOME = $JavaHome
$env:ANDROID_HOME = $AndroidHome
$env:ANDROID_SDK_ROOT = $AndroidHome
$env:ANDROID_USER_HOME = Join-Path $AndroidDev ".android"
$env:GRADLE_USER_HOME = $GradleUserHome
$env:GRADLE_OPTS = "-Dorg.gradle.java.installations.paths=$JavaHome $env:GRADLE_OPTS".Trim()
$env:Path = "$JavaHome\bin;$AndroidHome\platform-tools;$env:Path"
