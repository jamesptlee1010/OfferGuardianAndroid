# Offer Guardian: build a DEBUG Android APK on Windows with the Android SDK installed.
# Requirements: Android Studio + Android 35 SDK Platform + Internet connection.
# This script fetches official Gradle 8.9 if necessary. It does not upload private data.
$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot
$sdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } elseif ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT } else { Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
if (-not (Test-Path (Join-Path $sdk 'platforms\android-35\android.jar'))) {
    Write-Host "Android SDK Platform 35 not found: $sdk" -ForegroundColor Red
    Write-Host 'Install Android Studio, open Tools -> SDK Manager, select Android API 35, and try again.'
    exit 1
}
$env:ANDROID_HOME = $sdk
$env:ANDROID_SDK_ROOT = $sdk
if (-not $env:JAVA_HOME) {
    $studioJava = Join-Path $env:ProgramFiles 'Android\Android Studio\jbr'
    if (Test-Path (Join-Path $studioJava 'bin\java.exe')) { $env:JAVA_HOME = $studioJava }
}
if ($env:JAVA_HOME) { $env:PATH = (Join-Path $env:JAVA_HOME 'bin') + ';' + $env:PATH }

$tools = Join-Path $env:LOCALAPPDATA 'OfferGuardianBuildTools'
$gradleHome = Join-Path $tools 'gradle-8.9'
$gradleExe = Join-Path $gradleHome 'bin\gradle.bat'
if (-not (Test-Path $gradleExe)) {
    New-Item -ItemType Directory -Force -Path $tools | Out-Null
    $download = Join-Path $tools 'gradle-8.9-bin.zip'
    if (-not (Test-Path $download)) {
        Write-Host 'Downloading the official Gradle 8.9 build tool...'
        Invoke-WebRequest -UseBasicParsing -Uri 'https://services.gradle.org/distributions/gradle-8.9-bin.zip' -OutFile $download
    }
    Write-Host 'Extracting Gradle...'
    Expand-Archive -Path $download -DestinationPath $tools -Force
}
if (-not (Test-Path $gradleExe)) { throw 'Gradle extraction was unsuccessful.' }
Push-Location $root
try {
    Write-Host 'Building Android debug APK. Gradle may download Android dependencies.'
    & $gradleExe --no-daemon :app:assembleDebug
    if ($LASTEXITCODE -ne 0) { throw "Gradle failed with exit code $LASTEXITCODE" }
    $built = Join-Path $root 'app\build\outputs\apk\debug\app-debug.apk'
    if (-not (Test-Path $built)) { throw 'Gradle completed but the APK was not found.' }
    $destination = Join-Path $root 'OfferGuardian-debug.apk'
    Copy-Item $built $destination -Force
    Write-Host "`nBUILT: $destination" -ForegroundColor Green
} finally { Pop-Location }
