$ErrorActionPreference = 'Stop'
$ticketRoot = Split-Path -Parent $PSScriptRoot
Push-Location (Join-Path $ticketRoot 'backend')
try {
    & .\mvnw.cmd spring-boot:run '-Dspring-boot.run.profiles=dev'
    if ($LASTEXITCODE -ne 0) { throw 'Backend failed to start.' }
} finally { Pop-Location }
