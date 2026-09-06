param([switch]$KeepRunning)

. (Join-Path $PSScriptRoot "common.ps1")
$allResults = @()
$testbedStarted = $false
$locationPushed = $false

try {
    Push-Location $script:RepositoryRoot
    $locationPushed = $true
    $tagCommit = (& git rev-parse "v0.1.0^{commit}").Trim()
    if ($LASTEXITCODE -ne 0 -or $tagCommit -ne "85ffebc096eb1b7d007270638ccffcecb62605b3") {
        throw "Tag v0.1.0 does not resolve to the frozen analyzer commit."
    }
    $coreChanges = & git status --porcelain -- `
        retrylint-cli/src/main `
        retrylint-cli/build.gradle.kts `
        docs/CORE_CONTRACT_V0.1.md
    if ($coreChanges) {
        throw "Frozen analyzer core has local changes; refusing an untraceable experiment."
    }
    $manifest = Get-ScenarioManifest "27"
    $analyzerOutput = & .\gradlew.bat -q :retrylint-cli:run `
        "--args=analyze $manifest --format json --fail-on never"
    if ($LASTEXITCODE -ne 0) { throw "RetryLint analysis failed." }
    $analyzerReport = ($analyzerOutput -join "`n") | ConvertFrom-Json
    $prediction = $analyzerReport.findings |
        Where-Object { $_.ruleId -eq "RL001" -and $_.evidence.multiplier -eq "27" } |
        Select-Object -First 1
    if ($null -eq $prediction) { throw "Frozen RetryLint v0.1.0 did not predict 27x." }
    Pop-Location
    $locationPushed = $false

    foreach ($scenario in @("1", "3", "9")) {
        & (Join-Path $PSScriptRoot "start.ps1") -Scenario $scenario | Out-Host
        $testbedStarted = $true
        $allResults += Invoke-PersistentFailureRun -Scenario $scenario -Run 1
    }

    & (Join-Path $PSScriptRoot "start.ps1") -Scenario "27" | Out-Host
    $tenRunResults = @(1..10 | ForEach-Object {
        Invoke-PersistentFailureRun -Scenario "27" -Run $_
    })
    $allResults += $tenRunResults

    & (Join-Path $PSScriptRoot "start.ps1") -Scenario "27" `
        -BankFailureMode "fail-first-n" -BankFailFirstN 2 | Out-Host
    $allResults += Invoke-BankThirdCallSuccessRun

    Write-Host ""
    Write-Host "Persistent-failure observations"
    $allResults | Format-Table Scenario, Run, Checkout, Orders, Payments, Bank, Result -AutoSize

    $record = [ordered]@{
        scenarioId = "27-persistent-failure"
        retryLintVersion = "0.1.0"
        retryLintCommit = "85ffebc"
        analyzerManifest = "retrylint-cli/src/test/resources/fixtures/complete-example/retrylint.yml"
        predictedMultiplier = 27
        expectedCounts = [ordered]@{ checkout = 1; orders = 3; payments = 9; bank = 27 }
        runs = 10
        observedRuns = $tenRunResults
        result = "PASS"
        recordedAtUtc = [DateTime]::UtcNow.ToString("o")
    }
    $resultPath = Join-Path $script:RepositoryRoot "evaluation/week8-results.json"
    $record | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $resultPath -Encoding UTF8
    Write-Host "Predicted: 27; observed: 27 in all ten runs."
    Write-Host "Result written to $resultPath"
} finally {
    if ($locationPushed) {
        Pop-Location
    }
    if ($testbedStarted -and -not $KeepRunning) {
        & (Join-Path $PSScriptRoot "stop.ps1") | Out-Host
    }
}
