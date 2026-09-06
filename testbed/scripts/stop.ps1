. (Join-Path $PSScriptRoot "common.ps1")
Assert-DockerAvailable
& docker compose -f $script:ComposeFile down --remove-orphans
if ($LASTEXITCODE -ne 0) { throw "Could not stop the testbed." }
