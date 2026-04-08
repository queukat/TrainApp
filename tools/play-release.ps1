[CmdletBinding()]
param(
    [ValidateSet("internal", "alpha", "beta", "production")]
    [string]$Track = "internal",

    [ValidateSet("completed", "draft", "halted", "inProgress")]
    [string]$ReleaseStatus = "completed",

    [string]$PackageName = "com.queukat.train",

    [string]$AabPath,

    [string]$VersionName,

    [int]$VersionCode,

    [switch]$Upload,

    [switch]$SkipBuild,

    [switch]$CheckAccessOnly
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = Split-Path $PSScriptRoot -Parent
$gradleWrapper = Join-Path $repoRoot "gradlew.bat"
$defaultBundleDir = Join-Path $repoRoot "app\build\outputs\bundle\release"
$playKeyFile = $env:PLAY_KEY_FILE

function Write-Step {
    param([string]$Message)
    Write-Host "==> $Message"
}

function Get-RequiredCommand {
    param([string]$Name)
    $command = Get-Command $Name -ErrorAction SilentlyContinue
    if (-not $command) {
        throw "Required command '$Name' was not found in PATH."
    }
    return $command.Source
}

function Get-RequiredEnvValue {
    param([string]$Name)
    $value = [Environment]::GetEnvironmentVariable($Name)
    if ([string]::IsNullOrWhiteSpace($value)) {
        throw "Required environment variable '$Name' is not set."
    }
    return $value
}

function ConvertTo-Base64Url {
    param([byte[]]$Bytes)
    return [Convert]::ToBase64String($Bytes).TrimEnd("=") -replace "\+", "-" -replace "/", "_"
}

function New-PlayAccessToken {
    param([string]$KeyPath)

    Add-Type -AssemblyName System.Security

    $key = Get-Content $KeyPath -Raw | ConvertFrom-Json
    $now = [DateTimeOffset]::UtcNow
    $headerJson = '{"alg":"RS256","typ":"JWT"}'
    $claimJson = (@{
            iss   = $key.client_email
            scope = "https://www.googleapis.com/auth/androidpublisher"
            aud   = $key.token_uri
            exp   = $now.ToUnixTimeSeconds() + 3600
            iat   = $now.ToUnixTimeSeconds()
        } | ConvertTo-Json -Compress)

    $header = ConvertTo-Base64Url ([Text.Encoding]::UTF8.GetBytes($headerJson))
    $claims = ConvertTo-Base64Url ([Text.Encoding]::UTF8.GetBytes($claimJson))
    $toSign = "$header.$claims"

    $pem = $key.private_key `
        -replace "-----BEGIN PRIVATE KEY-----", "" `
        -replace "-----END PRIVATE KEY-----", "" `
        -replace "\s", ""
    $keyBytes = [Convert]::FromBase64String($pem)

    $rsa = [System.Security.Cryptography.RSA]::Create()
    $bytesRead = 0
    $rsa.ImportPkcs8PrivateKey($keyBytes, [ref]$bytesRead) | Out-Null

    $signature = ConvertTo-Base64Url (
        $rsa.SignData(
            [Text.Encoding]::UTF8.GetBytes($toSign),
            [Security.Cryptography.HashAlgorithmName]::SHA256,
            [Security.Cryptography.RSASignaturePadding]::Pkcs1
        )
    )

    $jwt = "$toSign.$signature"
    $tokenResponse = Invoke-RestMethod -Method Post -Uri $key.token_uri -ContentType "application/x-www-form-urlencoded" -Body @{
        grant_type = "urn:ietf:params:oauth:grant-type:jwt-bearer"
        assertion  = $jwt
    }

    return $tokenResponse.access_token
}

function Test-PlayAccess {
    param(
        [string]$KeyPath,
        [string]$TargetPackage
    )

    $token = New-PlayAccessToken -KeyPath $KeyPath
    $headers = @{ Authorization = "Bearer $token" }
    $editUrl = "https://androidpublisher.googleapis.com/androidpublisher/v3/applications/$TargetPackage/edits"
    $result = Invoke-RestMethod -Method Post -Uri $editUrl -Headers $headers -ContentType "application/json" -Body "{}"

    return [pscustomobject]@{
        package           = $TargetPackage
        editId            = $result.id
        expiryTimeSeconds = $result.expiryTimeSeconds
        status            = "ok"
    }
}

function Assert-ReleaseSigningReady {
    $keystoreProperties = Join-Path $repoRoot "keystore.properties"
    if (Test-Path $keystoreProperties) {
        return
    }

    $requiredSigningVars = @(
        "TRAINAPP_KEYSTORE_FILE",
        "TRAINAPP_KEYSTORE_PASSWORD",
        "TRAINAPP_KEY_ALIAS",
        "TRAINAPP_KEY_PASSWORD"
    )

    $missingSigningVars = @(
        $requiredSigningVars | Where-Object {
            [string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($_))
        }
    )

    if ($missingSigningVars.Count -gt 0) {
        throw "Release signing is not configured. Add keystore.properties or set: $($missingSigningVars -join ', ')"
    }
}

function Resolve-BundlePath {
    param([string]$ExplicitPath)

    if (-not [string]::IsNullOrWhiteSpace($ExplicitPath)) {
        $resolved = Resolve-Path $ExplicitPath -ErrorAction Stop
        if ($resolved.Path -notmatch "\.aab$") {
            throw "AAB path must point to a .aab file: $($resolved.Path)"
        }
        return $resolved.Path
    }

    if (-not (Test-Path $defaultBundleDir)) {
        throw "No bundle directory found at '$defaultBundleDir'. Build a release first or pass -AabPath."
    }

    $bundle = Get-ChildItem -Path $defaultBundleDir -Filter *.aab -File |
        Sort-Object LastWriteTimeUtc -Descending |
        Select-Object -First 1

    if (-not $bundle) {
        throw "No .aab file found under '$defaultBundleDir'. Build a release first or pass -AabPath."
    }

    return $bundle.FullName
}

function Invoke-GradleBundle {
    param(
        [string]$BundleVersionName,
        [int]$BundleVersionCode,
        [bool]$HasVersionName,
        [bool]$HasVersionCode
    )

    if (-not (Test-Path $gradleWrapper)) {
        throw "Gradle wrapper not found at '$gradleWrapper'."
    }

    Assert-ReleaseSigningReady

    $previousVersionName = $env:VERSION_NAME
    $previousVersionCode = $env:VERSION_CODE

    try {
        if ($HasVersionName) {
            $env:VERSION_NAME = $BundleVersionName
        }

        if ($HasVersionCode) {
            $env:VERSION_CODE = $BundleVersionCode.ToString()
        }

        Write-Step "Building signed release bundle with Gradle"
        & $gradleWrapper ":app:bundleRelease"
        if ($LASTEXITCODE -ne 0) {
            throw "Gradle bundleRelease failed with exit code $LASTEXITCODE."
        }
    }
    finally {
        if ($HasVersionName) {
            if ($null -eq $previousVersionName) {
                Remove-Item Env:VERSION_NAME -ErrorAction SilentlyContinue
            }
            else {
                $env:VERSION_NAME = $previousVersionName
            }
        }

        if ($HasVersionCode) {
            if ($null -eq $previousVersionCode) {
                Remove-Item Env:VERSION_CODE -ErrorAction SilentlyContinue
            }
            else {
                $env:VERSION_CODE = $previousVersionCode
            }
        }
    }
}

function Invoke-PlayUpload {
    param(
        [string]$TargetPackage,
        [string]$KeyPath,
        [string]$BundlePath,
        [string]$TargetTrack,
        [string]$TargetReleaseStatus,
        [bool]$ValidateOnly,
        [string]$UploadVersionName,
        [int]$UploadVersionCode,
        [bool]$HasUploadVersionName,
        [bool]$HasUploadVersionCode
    )

    Get-RequiredCommand -Name "fastlane" | Out-Null

    $env:FASTLANE_SKIP_UPDATE_CHECK = "1"
    $env:FASTLANE_HIDE_CHANGELOG = "1"
    $env:LANG = "en_US.UTF-8"
    $env:LC_ALL = "en_US.UTF-8"

    $fastlaneArgs = @(
        "run",
        "upload_to_play_store",
        "package_name:$TargetPackage",
        "json_key:$KeyPath",
        "aab:$BundlePath",
        "track:$TargetTrack",
        "release_status:$TargetReleaseStatus",
        "skip_upload_apk:true",
        "skip_upload_metadata:true",
        "skip_upload_images:true",
        "skip_upload_screenshots:true",
        "skip_upload_changelogs:true",
        "validate_only:$($ValidateOnly.ToString().ToLowerInvariant())"
    )

    if ($HasUploadVersionName) {
        $fastlaneArgs += "version_name:$UploadVersionName"
    }

    if ($HasUploadVersionCode) {
        $fastlaneArgs += "version_code:$UploadVersionCode"
    }

    $mode = if ($ValidateOnly) { "validating" } else { "uploading" }
    Write-Step "$mode bundle in Google Play track '$TargetTrack'"
    & fastlane @fastlaneArgs
    if ($LASTEXITCODE -ne 0) {
        throw "fastlane upload_to_play_store failed with exit code $LASTEXITCODE."
    }
}

if ([string]::IsNullOrWhiteSpace($playKeyFile)) {
    $playKeyFile = Get-RequiredEnvValue -Name "PLAY_KEY_FILE"
}

if (-not (Test-Path $playKeyFile)) {
    throw "PLAY_KEY_FILE points to a missing file: $playKeyFile"
}

if ($env:PLAY_PACKAGE_NAME -and $env:PLAY_PACKAGE_NAME -ne $PackageName) {
    Write-Warning "PLAY_PACKAGE_NAME is '$($env:PLAY_PACKAGE_NAME)', but this script will use '$PackageName'. Pass -PackageName if you want a different app."
}

if ($CheckAccessOnly) {
    Write-Step "Checking Play Console access for '$PackageName'"
    Test-PlayAccess -KeyPath $playKeyFile -TargetPackage $PackageName | ConvertTo-Json -Compress
    exit 0
}

$hasVersionName = $PSBoundParameters.ContainsKey("VersionName")
$hasVersionCode = $PSBoundParameters.ContainsKey("VersionCode")

if (-not $SkipBuild) {
    Invoke-GradleBundle `
        -BundleVersionName $VersionName `
        -BundleVersionCode $VersionCode `
        -HasVersionName $hasVersionName `
        -HasVersionCode $hasVersionCode
}

$resolvedBundlePath = Resolve-BundlePath -ExplicitPath $AabPath
$validateOnly = -not $Upload

Write-Step "Using bundle '$resolvedBundlePath'"
Invoke-PlayUpload `
    -TargetPackage $PackageName `
    -KeyPath $playKeyFile `
    -BundlePath $resolvedBundlePath `
    -TargetTrack $Track `
    -TargetReleaseStatus $ReleaseStatus `
    -ValidateOnly $validateOnly `
    -UploadVersionName $VersionName `
    -UploadVersionCode $VersionCode `
    -HasUploadVersionName $hasVersionName `
    -HasUploadVersionCode $hasVersionCode

if ($validateOnly) {
    Write-Step "Validation completed. Re-run with -Upload to publish to Google Play."
}
else {
    Write-Step "Upload completed."
}
