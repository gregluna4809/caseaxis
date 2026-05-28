param(
    [switch]$Seed
)

$ErrorActionPreference = "Stop"

function Read-CaseAxisEnv {
    param(
        [string]$EnvPath
    )

    $values = @{}

    if (-not (Test-Path -LiteralPath $EnvPath)) {
        throw ".env file not found at $EnvPath"
    }

    Get-Content -LiteralPath $EnvPath | ForEach-Object {
        $line = $_.Trim()
        if ($line.Length -gt 0 -and -not $line.StartsWith("#")) {
            $separatorIndex = $line.IndexOf("=")
            if ($separatorIndex -ge 1) {
                $key = $line.Substring(0, $separatorIndex).Trim()
                $value = $line.Substring($separatorIndex + 1).Trim()
                $values[$key] = $value
            }
        }
    }

    return $values
}

Set-Location -LiteralPath $PSScriptRoot

Write-Host "Checking Docker..."
try {
    docker info *> $null
} catch {
    Write-Error "Docker does not appear to be running. Start Docker Desktop and try again."
    exit 1
}
if ($LASTEXITCODE -ne 0) {
    Write-Error "Docker does not appear to be running. Start Docker Desktop and try again."
    exit 1
}

Write-Host "Starting CaseAxis containers..."
docker compose up -d
if ($LASTEXITCODE -ne 0) {
    Write-Error "docker compose up -d failed."
    exit 1
}

$healthUrl = "http://localhost:8080/actuator/health"
$deadline = (Get-Date).AddSeconds(60)
$healthy = $false

Write-Host "Waiting for backend health endpoint..."
while ((Get-Date) -lt $deadline) {
    try {
        $response = Invoke-WebRequest -Uri $healthUrl -UseBasicParsing -TimeoutSec 2
        if ($response.StatusCode -eq 200) {
            $healthy = $true
            break
        }
    } catch {
        Start-Sleep -Seconds 2
        continue
    }

    Start-Sleep -Seconds 2
}

if (-not $healthy) {
    Write-Error "Backend health endpoint did not return HTTP 200 within 60 seconds: $healthUrl"
    exit 1
}

Write-Host "Backend is healthy."

if ($Seed) {
    Write-Host "Seeding demo data..."
    $envValues = Read-CaseAxisEnv -EnvPath (Join-Path $PSScriptRoot ".env")

    foreach ($requiredKey in @("POSTGRES_USER", "POSTGRES_PASSWORD", "POSTGRES_DB")) {
        if (-not $envValues.ContainsKey($requiredKey) -or [string]::IsNullOrWhiteSpace($envValues[$requiredKey])) {
            Write-Error "Missing required .env value: $requiredKey"
            exit 1
        }
    }

    $dbUser = $envValues["POSTGRES_USER"]
    $dbPassword = $envValues["POSTGRES_PASSWORD"]
    $dbName = $envValues["POSTGRES_DB"]
    $env:DB_URL = "postgresql://${dbUser}:${dbPassword}@localhost:5434/${dbName}"

    python (Join-Path $PSScriptRoot "tools\seed_bir_demo.py") --reset-demo --organizations 10 --clients 100 --cases 200 --notes 400 --tasks 300 --attachments 150
    if ($LASTEXITCODE -ne 0) {
        Write-Error "Demo data seeding failed."
        exit 1
    }
}

$frontendPath = Join-Path $PSScriptRoot "frontend"
$escapedFrontendPath = $frontendPath.Replace("'", "''")

Write-Host "Opening frontend dev server in a new PowerShell window..."
Start-Process -FilePath "powershell.exe" -ArgumentList @(
    "-NoExit",
    "-Command",
    "Set-Location -LiteralPath '$escapedFrontendPath'; npm run dev"
)

Write-Host ""
Write-Host "CaseAxis is starting."
Write-Host "Backend:  http://localhost:8080"
Write-Host "Health:   http://localhost:8080/actuator/health"
Write-Host "Frontend: http://localhost:5173"
