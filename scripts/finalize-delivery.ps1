<#
.SYNOPSIS
  Runs every verification needed for the final RetryLint delivery and records the evidence.

.DESCRIPTION
  1. Clean build with all tests (unit, contract, E2E fixtures, Spring contract) and the
     JaCoCo branch-coverage gate (>= 80% on config/graph/rules).
  2. Archives the JaCoCo XML/HTML/CSV report into evaluation/coverage/retrylint-cli.
  3. Copies JUnit XML results into evaluation/test-results.
  4. Builds the CLI distribution ZIP and writes its SHA-256 to release/SHA256SUMS.txt.
  5. Runs the Week 8 Docker testbed (all scenarios) unless -SkipTestbed is given.
  A step failure is recorded and the script continues, so every result can be reviewed.

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File scripts\finalize-delivery.ps1
#>
param([switch]$SkipTestbed)

$ErrorActionPreference = "Continue"
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root
$log = Join-Path $root "evaluation/finalize-log.txt"
$summaryPath = Join-Path $root "evaluation/finalize-summary.json"
Set-Content -LiteralPath $log -Value "RetryLint final delivery run $([DateTime]::UtcNow.ToString('o'))" -Encoding UTF8
$steps = [ordered]@{}

function Invoke-Step([string]$Name, [scriptblock]$Action) {
    Write-Host ""
    Write-Host "=== $Name" -ForegroundColor Cyan
    Add-Content -LiteralPath $log -Value "`n=== $Name"
    $global:LASTEXITCODE = 0
    try {
        & $Action 2>&1 | ForEach-Object { "$_" } | Tee-Object -FilePath $log -Append
        $code = $global:LASTEXITCODE
    } catch {
        Add-Content -LiteralPath $log -Value "EXCEPTION: $($_.Exception.Message)"
        Write-Host "EXCEPTION: $($_.Exception.Message)" -ForegroundColor Red
        $code = -1
    }
    $steps[$Name] = if ($code -eq 0) { "PASS" } else { "FAIL (exit $code)" }
    Write-Host "$Name -> $($steps[$Name])"
}

Invoke-Step "environment" {
    & java -version
    & git rev-parse HEAD
    & git status --short
    & docker version --format "{{.Server.Version}}"
}

Invoke-Step "gradle clean build" {
    & .\gradlew.bat clean build --continue --warning-mode all --no-daemon
}

Invoke-Step "archive coverage" {
    & .\gradlew.bat :retrylint-cli:archiveCoverage --no-daemon
}

Invoke-Step "archive test results" {
    $target = Join-Path $root "evaluation/test-results"
    if (Test-Path $target) { Remove-Item -Recurse -Force $target }
    foreach ($module in @("retrylint-cli", "testbed-service")) {
        $source = Join-Path $root "$module/build/test-results/test"
        if (Test-Path $source) {
            $destination = Join-Path $target $module
            New-Item -ItemType Directory -Force -Path $destination | Out-Null
            Copy-Item -Path (Join-Path $source "*.xml") -Destination $destination
        }
    }
    Get-ChildItem -Recurse $target -Filter *.xml | Measure-Object | ForEach-Object { "XML files: $($_.Count)" }
}

Invoke-Step "distribution and SHA-256" {
    & .\gradlew.bat :retrylint-cli:distZip --no-daemon
    if ($LASTEXITCODE -ne 0) { return }
    $zip = Get-ChildItem (Join-Path $root "retrylint-cli/build/distributions") -Filter "retrylint-cli-*.zip" | Select-Object -First 1
    $hash = (Get-FileHash -Algorithm SHA256 -LiteralPath $zip.FullName).Hash
    Set-Content -LiteralPath (Join-Path $root "release/SHA256SUMS.txt") -Value "$hash  $($zip.Name)" -Encoding ASCII
    "$($zip.Name) $($zip.Length) bytes SHA-256 $hash"
}

if (-not $SkipTestbed) {
    Invoke-Step "week 8 testbed" {
        & powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $root "testbed/scripts/verify-week8.ps1")
    }
} else {
    $steps["week 8 testbed"] = "SKIPPED"
}

$summary = [ordered]@{
    recordedAtUtc = [DateTime]::UtcNow.ToString("o")
    commit = (& git rev-parse HEAD).Trim()
    steps = $steps
}
$summary | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath $summaryPath -Encoding UTF8
Write-Host ""
Write-Host "Summary written to $summaryPath" -ForegroundColor Green
$steps.GetEnumerator() | ForEach-Object { "{0,-28} {1}" -f $_.Key, $_.Value }
