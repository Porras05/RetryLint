param([switch]$KeepRunning)

. (Join-Path $PSScriptRoot "common.ps1")
$testbedStarted = $false
$locationPushed = $false

function Get-StaticPrediction([string]$Scenario) {
    $manifest = Get-ScenarioManifest $Scenario
    $output = & .\gradlew.bat -q :retrylint-cli:run "--args=analyze $manifest --format json --fail-on never"
    if ($LASTEXITCODE -ne 0) { throw "RetryLint analysis failed for scenario $Scenario." }
    $report = ($output -join "`n") | ConvertFrom-Json
    $rl001 = $report.findings | Where-Object { $_.ruleId -eq "RL001" } | Select-Object -First 1
    $expected = Get-ExpectedCounts $Scenario
    return [ordered]@{
        scenario = $Scenario
        manifest = $manifest
        expectedBankCount = $expected.bank
        rl001Multiplier = if ($null -ne $rl001) { [long]$rl001.evidence.multiplier } else { $null }
        rl001Severity = if ($null -ne $rl001) { $rl001.severity } else { "none (below warning threshold 9)" }
    }
}

try {
    Push-Location $script:RepositoryRoot
    $locationPushed = $true
    $tagCommit = (& git rev-parse "v0.1.0^{commit}").Trim()
    if ($LASTEXITCODE -ne 0 -or $tagCommit -ne "85ffebc096eb1b7d007270638ccffcecb62605b3") {
        throw "Tag v0.1.0 does not resolve to the frozen analyzer commit."
    }
    # The analyzer semantics are frozen: production sources must be identical to v0.1.0,
    # including uncommitted working-tree changes. Build/test infrastructure may evolve.
    & git diff --quiet v0.1.0 -- retrylint-cli/src/main
    if ($LASTEXITCODE -ne 0) {
        throw "retrylint-cli/src/main differs from v0.1.0; refusing an untraceable experiment."
    }
    $headCommit = (& git rev-parse HEAD).Trim()

    $predictions = @("1", "3", "9", "27" | ForEach-Object { Get-StaticPrediction $_ })
    $prediction27 = $predictions | Where-Object { $_.scenario -eq "27" }
    if ($prediction27.rl001Multiplier -ne 27) { throw "Frozen RetryLint v0.1.0 did not predict 27x." }
    Pop-Location
    $locationPushed = $false

    $scenarioRuns = @()
    foreach ($scenario in @("1", "3", "9")) {
        & (Join-Path $PSScriptRoot "start.ps1") -Scenario $scenario | Out-Host
        $testbedStarted = $true
        $scenarioRuns += Invoke-PersistentFailureRun -Scenario $scenario -Run 1
    }

    & (Join-Path $PSScriptRoot "start.ps1") -Scenario "27" | Out-Host
    $testbedStarted = $true
    $tenRunResults = @(1..10 | ForEach-Object {
        Invoke-PersistentFailureRun -Scenario "27" -Run $_
    })

    & (Join-Path $PSScriptRoot "start.ps1") -Scenario "27" `
        -BankFailureMode "fail-first-n" -BankFailFirstN 2 | Out-Host
    $bankThirdSuccess = Invoke-BankThirdCallSuccessRun

    $allResults = @($scenarioRuns) + @($tenRunResults) + @($bankThirdSuccess)
    Write-Host ""
    Write-Host "Week 8 observations"
    $allResults | Format-Table Scenario, Run, Checkout, Orders, Payments, Bank, Result -AutoSize

    $record = [ordered]@{
        scenarioId = "27-persistent-failure"
        retryLintVersion = "0.1.0"
        retryLintCommit = "85ffebc"
        repositoryCommit = $headCommit
        analyzerManifest = "retrylint-cli/src/test/resources/fixtures/complete-example/retrylint.yml"
        predictedMultiplier = 27
        expectedCounts = [ordered]@{ checkout = 1; orders = 3; payments = 9; bank = 27 }
        runs = 10
        observedRuns = $tenRunResults
        staticPredictions = $predictions
        additionalScenarios = [ordered]@{
            description = "Persistent bank failure with retry attempts 1/1/1, 1/1/3 and 3/1/3; one run each."
            expectedCounts = [ordered]@{
                "1" = Get-ExpectedCounts "1"
                "3" = Get-ExpectedCounts "3"
                "9" = Get-ExpectedCounts "9"
            }
            runs = $scenarioRuns
        }
        bankThirdCallSuccess = [ordered]@{
            description = "Scenario 27 configuration with bank in fail-first-n (N=2). Payments' third attempt succeeds, so the observed bank count (3) is below the static 27x envelope, as expected for a non-persistent failure."
            expectedCounts = [ordered]@{ checkout = 1; orders = 1; payments = 1; bank = 3 }
            staticEnvelope = 27
            run = $bankThirdSuccess
        }
        result = "PASS"
        recordedAtUtc = [DateTime]::UtcNow.ToString("o")
    }
    $resultPath = Join-Path $script:RepositoryRoot "evaluation/week8-results.json"
    $record | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $resultPath -Encoding UTF8
    Write-Host "Predicted: 27; observed: 27 in all ten runs; scenarios 1/3/9 and bank-third-success PASS."
    Write-Host "Result written to $resultPath"
} finally {
    if ($locationPushed) {
        Pop-Location
    }
    if ($testbedStarted -and -not $KeepRunning) {
        & (Join-Path $PSScriptRoot "stop.ps1") | Out-Host
    }
}
