[CmdletBinding()]
param(
    [ValidateRange(1, 100)]
    [int]$Limit = 50,
    [switch]$Local
)

$databaseName = "trainme-feedback"
$query = "SELECT id, created_at, type, message, contact, app_version, android_version, ui_locale FROM feedback WHERE status = 'new' ORDER BY created_at DESC LIMIT $Limit"
$scope = if ($Local) { "--local" } else { "--remote" }

& wrangler d1 execute $databaseName $scope --command $query
exit $LASTEXITCODE
