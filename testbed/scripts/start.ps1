param(
    [ValidateSet("1", "3", "9", "27")]
    [string]$Scenario = "27",
    [ValidateSet("always-fail", "success", "fail-first-n")]
    [string]$BankFailureMode = "always-fail",
    [int]$BankFailFirstN = 0
)

. (Join-Path $PSScriptRoot "common.ps1")
Assert-DockerAvailable
Set-ScenarioEnvironment $Scenario
$env:BANK_FAILURE_MODE = $BankFailureMode
$env:BANK_FAIL_FIRST_N = $BankFailFirstN.ToString()

& docker compose -f $script:ComposeFile down --remove-orphans
if ($LASTEXITCODE -ne 0) { throw "Could not stop the previous testbed." }
& docker compose -f $script:ComposeFile up -d --build
if ($LASTEXITCODE -ne 0) { throw "Could not start the testbed." }

Wait-TestbedHealthy
Write-Host "RetryLint Week 8 testbed is healthy (scenario $Scenario, bank mode $BankFailureMode)."
