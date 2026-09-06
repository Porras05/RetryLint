param(
    [ValidateSet("1", "3", "9", "27")]
    [string]$Scenario = "27",
    [int]$Run = 1
)

. (Join-Path $PSScriptRoot "common.ps1")
$result = Invoke-PersistentFailureRun -Scenario $Scenario -Run $Run
$result | Format-Table Scenario, Run, Checkout, Orders, Payments, Bank, Result, TraceId -AutoSize
