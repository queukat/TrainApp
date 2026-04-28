[CmdletBinding()]
param(
    [string]$PackageName = "com.queukat.train",

    [string[]]$Locales,

    [ValidateSet("phoneScreenshots", "featureGraphic", "icon", "tvBanner")]
    [string]$ImageType = "phoneScreenshots"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Write-Step {
    param([string]$Message)
    Write-Host "==> $Message"
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

if (-not $Locales -or $Locales.Count -eq 0) {
    throw "Pass at least one locale via -Locales."
}

$playKeyFile = Get-RequiredEnvValue -Name "PLAY_KEY_FILE"
if (-not (Test-Path $playKeyFile)) {
    throw "PLAY_KEY_FILE points to a missing file: $playKeyFile"
}

$token = New-PlayAccessToken -KeyPath $playKeyFile
$headers = @{ Authorization = "Bearer $token" }
$edit = Invoke-RestMethod `
    -Method Post `
    -Uri "https://androidpublisher.googleapis.com/androidpublisher/v3/applications/$PackageName/edits" `
    -Headers $headers `
    -ContentType "application/json" `
    -Body "{}"

foreach ($locale in $Locales) {
    Write-Step "Deleting '$ImageType' for locale '$locale'"
    $uri = "https://androidpublisher.googleapis.com/androidpublisher/v3/applications/$PackageName/edits/$($edit.id)/listings/$locale/$ImageType"
    Invoke-RestMethod -Method Delete -Uri $uri -Headers $headers | Out-Null
}

Write-Step "Committing edit"
Invoke-RestMethod `
    -Method Post `
    -Uri "https://androidpublisher.googleapis.com/androidpublisher/v3/applications/$PackageName/edits/$($edit.id):commit" `
    -Headers $headers `
    -ContentType "application/json" `
    -Body "{}" | Out-Null

Write-Step "Delete completed."
