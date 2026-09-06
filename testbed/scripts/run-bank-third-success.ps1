. (Join-Path $PSScriptRoot "common.ps1")
& (Join-Path $PSScriptRoot "start.ps1") -Scenario "27" `
    -BankFailureMode "fail-first-n" -BankFailFirstN 2 | Out-Host
$result = Invoke-BankThirdCallSuccessRun
$result | Format-Table Scenario, Run, Checkout, Orders, Payments, Bank, Result, TraceId -AutoSize
