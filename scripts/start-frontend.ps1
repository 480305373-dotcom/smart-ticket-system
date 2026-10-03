$ErrorActionPreference = 'Stop'
$ticketRoot = Split-Path -Parent $PSScriptRoot
Push-Location (Join-Path $ticketRoot 'frontend')
try {
    if (-not (Test-Path -LiteralPath 'node_modules')) { & (Join-Path $PSScriptRoot 'npm.ps1') install }
    & (Join-Path $PSScriptRoot 'npm.ps1') run dev
} finally { Pop-Location }
