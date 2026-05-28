$ErrorActionPreference = "Stop"

Set-Location -LiteralPath $PSScriptRoot

Write-Host "Stopping CaseAxis containers..."
docker compose down
if ($LASTEXITCODE -ne 0) {
    Write-Error "docker compose down failed."
    exit 1
}

Write-Host ""
Write-Host "Containers stopped."
Write-Host "Reminder: stop the frontend dev server window manually with Ctrl+C."
