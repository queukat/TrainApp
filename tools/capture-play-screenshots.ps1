[CmdletBinding()]
param(
    [string]$DeviceSerial,

    [string]$LocaleTag = "en-US",

    [string]$StationLanguage = "en",

    [string]$OutputDir
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = Split-Path $PSScriptRoot -Parent
$gradleWrapper = Join-Path $repoRoot "gradlew.bat"
$packageName = "com.queukat.train"
$testPackageName = "$packageName.test"
$testClass = "com.queukat.train.screenshots.StoreScreenshotTest#captureStoreScreenshots"
$remoteDir = "/sdcard/Android/data/$packageName/files/store_screenshots/$LocaleTag"
$appApkPath = Join-Path $repoRoot "app\build\outputs\apk\debug\app-debug.apk"
$androidTestApkPath = Join-Path $repoRoot "app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk"

function Write-Step {
    param([string]$Message)
    Write-Host "==> $Message"
}

function Get-OnlineDeviceSerial {
    $lines = & adb devices
    if ($LASTEXITCODE -ne 0) {
        throw "adb devices failed with exit code $LASTEXITCODE."
    }

    $online = @(
        $lines |
            Select-Object -Skip 1 |
            Where-Object { $_ -match "\S+\s+device$" } |
            ForEach-Object { ($_ -split "\s+")[0] }
    )

    if ($online.Count -eq 0) {
        throw "No online Android device or emulator found. Connect one device and re-run the script."
    }

    if ($online.Count -gt 1) {
        throw "Multiple online devices found ($($online -join ', ')). Re-run with -DeviceSerial."
    }

    return $online[0]
}

if (-not (Test-Path $gradleWrapper)) {
    throw "Gradle wrapper not found at '$gradleWrapper'."
}

if ([string]::IsNullOrWhiteSpace($DeviceSerial)) {
    $DeviceSerial = Get-OnlineDeviceSerial
}

if ([string]::IsNullOrWhiteSpace($OutputDir)) {
    $OutputDir = Join-Path $repoRoot "app\fastlane\metadata\android\$LocaleTag\images\phoneScreenshots"
}

$resolvedOutputDir = [System.IO.Path]::GetFullPath($OutputDir)
New-Item -ItemType Directory -Force -Path $resolvedOutputDir | Out-Null
Get-ChildItem -LiteralPath $resolvedOutputDir -File -ErrorAction SilentlyContinue | Remove-Item -Force

$adbArgs = @("-s", $DeviceSerial)

Write-Step "Using device '$DeviceSerial'"
Write-Step "Building debug + androidTest APKs"
& $gradleWrapper "--no-daemon" ":app:assembleDebug" ":app:assembleDebugAndroidTest"
if ($LASTEXITCODE -ne 0) {
    throw "Gradle assemble tasks failed with exit code $LASTEXITCODE."
}

if (-not (Test-Path $appApkPath)) {
    throw "Main APK not found at '$appApkPath'."
}

if (-not (Test-Path $androidTestApkPath)) {
    throw "AndroidTest APK not found at '$androidTestApkPath'."
}

Write-Step "Installing APKs on '$DeviceSerial'"
& adb @adbArgs install -r $appApkPath
if ($LASTEXITCODE -ne 0) {
    throw "adb install for main APK failed with exit code $LASTEXITCODE."
}

& adb @adbArgs install -r $androidTestApkPath
if ($LASTEXITCODE -ne 0) {
    throw "adb install for androidTest APK failed with exit code $LASTEXITCODE."
}

Write-Step "Clearing previous screenshots on device"
& adb @adbArgs shell rm -rf $remoteDir
if ($LASTEXITCODE -ne 0) {
    throw "Failed to clear remote screenshot directory '$remoteDir'."
}

Write-Step "Running screenshot test for locale '$LocaleTag'"
$instrumentationOutput = & adb @adbArgs shell am instrument -w `
    -e localeTag $LocaleTag `
    -e stationLanguage $StationLanguage `
    -e class $testClass `
    "$testPackageName/androidx.test.runner.AndroidJUnitRunner"
$instrumentationOutput | Write-Host
if ($LASTEXITCODE -ne 0) {
    throw "Instrumentation run failed with exit code $LASTEXITCODE."
}

$instrumentationText = ($instrumentationOutput -join "`n")
if ($instrumentationText -match "FAILURES!!!" -or $instrumentationText -match "Process crashed" -or $instrumentationText -match "shortMsg=") {
    throw "Instrumentation reported a failure for locale '$LocaleTag'."
}

Write-Step "Pulling screenshots from device"
& adb @adbArgs pull "$remoteDir/." $resolvedOutputDir
if ($LASTEXITCODE -ne 0) {
    throw "adb pull failed with exit code $LASTEXITCODE."
}

$capturedFiles = Get-ChildItem -File $resolvedOutputDir | Sort-Object Name
if ($capturedFiles.Count -lt 4) {
    throw "Expected 4 screenshots for locale '$LocaleTag', but found $($capturedFiles.Count) in '$resolvedOutputDir'."
}

Write-Step "Screenshots saved to '$resolvedOutputDir'"
 $capturedFiles |
    Select-Object Name, Length, LastWriteTime
