Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

if (-not $env:GRADLE_USER_HOME) {
    $env:GRADLE_USER_HOME = Join-Path $env:USERPROFILE ".gradle"
}

$script:TestbedRoot = Split-Path -Parent $PSScriptRoot
$script:RepositoryRoot = Split-Path -Parent $script:TestbedRoot
$script:ComposeFile = Join-Path $script:TestbedRoot "compose.yml"
$script:ServicePorts = [ordered]@{
    checkout = 18080
    orders = 18081
    payments = 18082
    bank = 18083
}

function Assert-DockerAvailable {
    & docker info --format '{{.ServerVersion}}' | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "Docker Engine is not available. Start Docker Desktop/Engine and try again."
    }
    & docker compose version | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "Docker Compose is not available."
    }
}

function Set-ScenarioEnvironment([ValidateSet("1", "3", "9", "27")][string]$Scenario) {
    if ($Scenario -eq "27") {
        $env:CONFIG_ROOT = "../retrylint-cli/src/test/resources/fixtures/complete-example/services"
    } else {
        $env:CONFIG_ROOT = "./configs/scenarios/$Scenario/services"
    }
}

function Get-ScenarioManifest([ValidateSet("1", "3", "9", "27")][string]$Scenario) {
    if ($Scenario -eq "27") {
        return "retrylint-cli/src/test/resources/fixtures/complete-example/retrylint.yml"
    }
    return "testbed/configs/scenarios/$Scenario/retrylint.yml"
}

function Get-ExpectedCounts([ValidateSet("1", "3", "9", "27")][string]$Scenario) {
    switch ($Scenario) {
        "1" { return [ordered]@{ checkout = 1; orders = 1; payments = 1; bank = 1 } }
        "3" { return [ordered]@{ checkout = 1; orders = 1; payments = 1; bank = 3 } }
        "9" { return [ordered]@{ checkout = 1; orders = 3; payments = 3; bank = 9 } }
        "27" { return [ordered]@{ checkout = 1; orders = 3; payments = 9; bank = 27 } }
    }
}

function Wait-TestbedHealthy([int]$TimeoutSeconds = 120) {
    $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
    do {
        $allHealthy = $true
        foreach ($entry in $script:ServicePorts.GetEnumerator()) {
            try {
                $health = Invoke-RestMethod -Uri "http://localhost:$($entry.Value)/actuator/health" -TimeoutSec 2
                if ($health.status -ne "UP") { $allHealthy = $false }
            } catch {
                $allHealthy = $false
            }
        }
        if ($allHealthy) { return }
        Start-Sleep -Seconds 2
    } while ([DateTime]::UtcNow -lt $deadline)
    throw "The four testbed services did not become healthy within $TimeoutSeconds seconds."
}

function Reset-Testbed {
    foreach ($entry in $script:ServicePorts.GetEnumerator()) {
        Invoke-RestMethod -Method Post -Uri "http://localhost:$($entry.Value)/test/reset" -TimeoutSec 10 | Out-Null
    }
    foreach ($entry in $script:ServicePorts.GetEnumerator()) {
        $snapshot = Invoke-RestMethod -Uri "http://localhost:$($entry.Value)/test/counters" -TimeoutSec 10
        if ([long]$snapshot.total -ne 0) {
            throw "Reset failed for $($entry.Key): total is $($snapshot.total), expected 0."
        }
    }
}

function Invoke-RootRequestExpectingFailure([string]$TraceId) {
    try {
        $response = Invoke-WebRequest -Method Post -Uri "http://localhost:18080/invoke" `
            -Headers @{ "X-RetryLint-Trace-Id" = $TraceId } -TimeoutSec 60 -UseBasicParsing
        throw "Persistent-failure root request unexpectedly returned HTTP $($response.StatusCode)."
    } catch {
        if ($_.Exception.Message -like "Persistent-failure root request unexpectedly*") { throw }
        $statusCode = [int]$_.Exception.Response.StatusCode
        if ($statusCode -ne 503) {
            throw "Root request returned HTTP $statusCode; expected 503."
        }
    }
}

function Invoke-RootRequestExpectingSuccess([string]$TraceId) {
    $response = Invoke-WebRequest -Method Post -Uri "http://localhost:18080/invoke" `
        -Headers @{ "X-RetryLint-Trace-Id" = $TraceId } -TimeoutSec 60 -UseBasicParsing
    if ([int]$response.StatusCode -ne 200) {
        throw "Root request returned HTTP $($response.StatusCode); expected 200."
    }
}

function Get-TraceCounts([string]$TraceId) {
    $counts = [ordered]@{}
    $encodedTrace = [Uri]::EscapeDataString($TraceId)
    foreach ($entry in $script:ServicePorts.GetEnumerator()) {
        $snapshot = Invoke-RestMethod `
            -Uri "http://localhost:$($entry.Value)/test/counters?traceId=$encodedTrace" -TimeoutSec 10
        $counts[$entry.Key] = [long]$snapshot.traceCount
    }
    return $counts
}

function Invoke-PersistentFailureRun(
    [ValidateSet("1", "3", "9", "27")][string]$Scenario,
    [int]$Run = 1
) {
    Reset-Testbed
    $traceId = "week8-$Scenario-$Run-$([Guid]::NewGuid().ToString('N'))"
    Invoke-RootRequestExpectingFailure $traceId
    $actual = Get-TraceCounts $traceId
    $expected = Get-ExpectedCounts $Scenario
    $passed = $true
    foreach ($service in $expected.Keys) {
        if ($actual[$service] -ne $expected[$service]) { $passed = $false }
    }
    $result = [pscustomobject]@{
        Scenario = $Scenario
        Run = $Run
        Checkout = $actual.checkout
        Orders = $actual.orders
        Payments = $actual.payments
        Bank = $actual.bank
        Result = if ($passed) { "PASS" } else { "FAIL" }
        TraceId = $traceId
    }
    if (-not $passed) {
        throw "Scenario $Scenario run $Run failed: $($result | ConvertTo-Json -Compress)"
    }
    return $result
}

function Invoke-BankThirdCallSuccessRun {
    Reset-Testbed
    $traceId = "week8-bank-third-$([Guid]::NewGuid().ToString('N'))"
    Invoke-RootRequestExpectingSuccess $traceId
    $actual = Get-TraceCounts $traceId
    $expected = [ordered]@{ checkout = 1; orders = 1; payments = 1; bank = 3 }
    foreach ($service in $expected.Keys) {
        if ($actual[$service] -ne $expected[$service]) {
            throw "Bank-third-success failed for ${service}: observed $($actual[$service]), expected $($expected[$service])."
        }
    }
    return [pscustomobject]@{
        Scenario = "bank-third-success"
        Run = 1
        Checkout = $actual.checkout
        Orders = $actual.orders
        Payments = $actual.payments
        Bank = $actual.bank
        Result = "PASS"
        TraceId = $traceId
    }
}
