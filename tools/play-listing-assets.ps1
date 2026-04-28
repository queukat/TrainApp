[CmdletBinding()]
param(
    [ValidateSet("internal", "alpha", "beta", "production")]
    [string]$Track = "production",

    [string]$PackageName,

    [string[]]$Locales = @("en-US"),

    [switch]$ForceReplaceScreenshots,

    [switch]$Upload,

    [switch]$CheckAccessOnly
)

# Play nuance for this repo:
# `upload_to_play_store` is fine for normal listing asset pushes, including feature graphics.
# But if an existing locale keeps its old screenshots even after a reported success, do not keep
# retrying the same path blindly. Use `play-delete-listing-images.ps1` and then
# `play-force-upload-screenshots.rb` for the affected locales. That direct Supply::Client flow
# was the reliable fix for stale `en-GB` / `ru-RU` phone screenshots.

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = Split-Path $PSScriptRoot -Parent
$sourceMetadataRoot = Join-Path $repoRoot "app\fastlane\metadata\android"
$stagingMetadataRoot = Join-Path $repoRoot "artifacts\play-upload-metadata"

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

function Resolve-PackageName {
    param(
        [string]$ExplicitPackageName,
        [string]$RepoRoot
    )

    if (-not [string]::IsNullOrWhiteSpace($ExplicitPackageName)) {
        return $ExplicitPackageName
    }

    $buildGradlePath = Join-Path $RepoRoot "app\build.gradle.kts"
    if (Test-Path $buildGradlePath) {
        $appIdLine = Select-String -Path $buildGradlePath -Pattern 'applicationId\s*=\s*"([^"]+)"' | Select-Object -First 1
        if ($appIdLine -and $appIdLine.Matches.Count -gt 0) {
            return $appIdLine.Matches[0].Groups[1].Value
        }
    }

    return "com.queukat.train"
}

function New-StagedMetadataRoot {
    param(
        [string]$SourceRoot,
        [string]$StageRoot,
        [string[]]$LocalesToStage
    )

    if (Test-Path $StageRoot) {
        $resolvedStageRoot = (Resolve-Path $StageRoot).Path
        if (-not $resolvedStageRoot.StartsWith($repoRoot, [StringComparison]::OrdinalIgnoreCase)) {
            throw "Refusing to remove staged metadata outside repo root: $resolvedStageRoot"
        }
        Remove-Item -LiteralPath $resolvedStageRoot -Recurse -Force
    }

    New-Item -ItemType Directory -Force -Path $StageRoot | Out-Null

    foreach ($locale in $LocalesToStage) {
        $localeSource = Join-Path $SourceRoot $locale
        if (-not (Test-Path $localeSource)) {
            throw "Locale metadata directory is missing: $localeSource"
        }

        $sourceImagesDir = Join-Path $localeSource "images"
        if (-not (Test-Path $sourceImagesDir)) {
            throw "Locale images directory is missing: $sourceImagesDir"
        }

        $phoneScreenshotsDir = Join-Path $sourceImagesDir "phoneScreenshots"
        $featureGraphicPath = Join-Path $sourceImagesDir "featureGraphic.png"

        if (-not (Test-Path $phoneScreenshotsDir)) {
            throw "Phone screenshots directory is missing: $phoneScreenshotsDir"
        }

        $phoneScreenshots = Get-ChildItem -LiteralPath $phoneScreenshotsDir -File
        if ($phoneScreenshots.Count -eq 0) {
            throw "No phone screenshots found for locale '$locale'."
        }

        if (-not (Test-Path $featureGraphicPath)) {
            throw "Feature graphic is missing for locale '$locale': $featureGraphicPath"
        }

        $localeStage = Join-Path $StageRoot $locale
        New-Item -ItemType Directory -Force -Path $localeStage | Out-Null
        Copy-Item -LiteralPath $sourceImagesDir -Destination (Join-Path $localeStage "images") -Recurse -Force
    }

    return (Resolve-Path $StageRoot).Path
}

function Invoke-ListingAssetUpload {
    param(
        [string]$TargetPackage,
        [string]$KeyPath,
        [string]$MetadataPath,
        [string]$TargetTrack,
        [bool]$ValidateOnly,
        [bool]$UseSyncImageUpload
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
        "metadata_path:$MetadataPath",
        "track:$TargetTrack",
        "skip_upload_apk:true",
        "skip_upload_aab:true",
        "skip_upload_metadata:true",
        "skip_upload_changelogs:true",
        "skip_upload_images:false",
        "skip_upload_screenshots:false",
        "validate_only:$($ValidateOnly.ToString().ToLowerInvariant())"
    )

    if ($UseSyncImageUpload) {
        $fastlaneArgs += "sync_image_upload:true"
    }
    else {
        $fastlaneArgs += "sync_image_upload:false"
    }

    $mode = if ($ValidateOnly) { "Validating" } else { "Uploading" }
    Write-Step "$mode Play listing assets"
    & fastlane @fastlaneArgs
    if ($LASTEXITCODE -ne 0) {
        throw "fastlane upload_to_play_store failed with exit code $LASTEXITCODE."
    }
}

$resolvedPackageName = Resolve-PackageName -ExplicitPackageName $PackageName -RepoRoot $repoRoot
$playKeyFile = Get-RequiredEnvValue -Name "PLAY_KEY_FILE"
$playPackageFromEnv = [Environment]::GetEnvironmentVariable("PLAY_PACKAGE_NAME")
$sourceOnlyLocales = @("en-US", "sr-RS")

if (-not (Test-Path $playKeyFile)) {
    throw "PLAY_KEY_FILE points to a missing file: $playKeyFile"
}

if (
    -not [string]::IsNullOrWhiteSpace($playPackageFromEnv) -and
    -not $PSBoundParameters.ContainsKey("PackageName") -and
    $playPackageFromEnv -ne $resolvedPackageName
) {
    Write-Warning "PLAY_PACKAGE_NAME is '$playPackageFromEnv', but this script will use '$resolvedPackageName'. Pass -PackageName if you really want a different app."
}

$requestedSourceOnlyLocales = $Locales | Where-Object { $_ -in $sourceOnlyLocales }
if ($requestedSourceOnlyLocales.Count -gt 0) {
    Write-Warning "Requested locale(s) $($requestedSourceOnlyLocales -join ', ') look like local capture/source folders. For this app, the live Play listing locales are en-GB, ru-RU, and sr."
}

if ($CheckAccessOnly) {
    Write-Step "Checking Play Console access for '$resolvedPackageName'"
    Test-PlayAccess -KeyPath $playKeyFile -TargetPackage $resolvedPackageName | ConvertTo-Json -Compress
    exit 0
}

$stagedMetadata = New-StagedMetadataRoot `
    -SourceRoot $sourceMetadataRoot `
    -StageRoot $stagingMetadataRoot `
    -LocalesToStage $Locales

$validateOnly = -not $Upload

Write-Step "Using package '$resolvedPackageName'"
Write-Step "Using staged metadata '$stagedMetadata'"
Write-Step "Locales: $($Locales -join ', ')"
if ($ForceReplaceScreenshots) {
    Write-Warning "ForceReplaceScreenshots only disables sync_image_upload. If Play still keeps stale screenshots for an existing locale, switch to tools\\play-force-upload-screenshots.rb."
}

Invoke-ListingAssetUpload `
    -TargetPackage $resolvedPackageName `
    -KeyPath $playKeyFile `
    -MetadataPath $stagedMetadata `
    -TargetTrack $Track `
    -ValidateOnly $validateOnly `
    -UseSyncImageUpload (-not $ForceReplaceScreenshots)

if ($validateOnly) {
    Write-Step "Validation completed. Re-run with -Upload to push listing assets to Google Play."
}
else {
    Write-Step "Listing asset upload completed."
}
