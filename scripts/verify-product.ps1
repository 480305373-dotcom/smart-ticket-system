param([switch]$LocalInfrastructure,[switch]$InstallDependencies)
$ErrorActionPreference='Stop'
$ticketRoot=Split-Path -Parent $PSScriptRoot
$ticketNames=@('SPRING_DATASOURCE_URL','SPRING_DATASOURCE_USERNAME','DB_PASSWORD','ENTERPRISE_DB_URL','ENTERPRISE_DB_USER','ENTERPRISE_DB_PASSWORD')
$ticketPrevious=@{}
foreach($ticketName in $ticketNames){$ticketPrevious[$ticketName]=[Environment]::GetEnvironmentVariable($ticketName,'Process')}
Push-Location $ticketRoot
try{
    New-Item -ItemType Directory -Force .runtime | Out-Null
    if($LocalInfrastructure){
        $ticketDbSettings=& docker inspect smartticket-postgres --format '{{json .Config.Env}}'
        if($LASTEXITCODE -ne 0){throw 'Start the original local PostgreSQL/Redis/RabbitMQ infrastructure first.'}
        $ticketDbSettings=$ticketDbSettings | ConvertFrom-Json
        $ticketPassword=($ticketDbSettings | Where-Object {$_.StartsWith('POSTGRES_PASSWORD=')}) -replace '^POSTGRES_PASSWORD=',''
        if(-not $ticketPassword){throw 'Local database password was not available in the selected project configuration.'}
        $env:SPRING_DATASOURCE_URL=$env:ENTERPRISE_DB_URL='jdbc:postgresql://localhost:5433/intelligent_ticket'
        $env:SPRING_DATASOURCE_USERNAME=$env:ENTERPRISE_DB_USER='smartticket'
        $env:DB_PASSWORD=$env:ENTERPRISE_DB_PASSWORD=$ticketPassword
    }
    & python -m unittest discover -s scripts -p 'test_*.py' -v
    if($LASTEXITCODE -ne 0){throw 'Delivery tool tests failed.'}
    & mvn -B clean package '-Dinfra.tests=true'
    if($LASTEXITCODE -ne 0){throw 'Backend build or real infrastructure tests failed.'}
    if($InstallDependencies -or -not(Test-Path frontend/node_modules)){
        & (Join-Path $PSScriptRoot 'npm.ps1') --prefix frontend ci
        if($LASTEXITCODE -ne 0){throw 'Locked frontend dependency installation failed; stop the project Vite server before reinstalling on Windows.'}
    }
    & (Join-Path $PSScriptRoot 'npm.ps1') --prefix frontend test
    if($LASTEXITCODE -ne 0){throw 'Frontend tests failed.'}
    & (Join-Path $PSScriptRoot 'npm.ps1') --prefix frontend run build
    if($LASTEXITCODE -ne 0){throw 'Frontend production build failed.'}
    & python scripts/verify_distributed_artifacts.py
    if($LASTEXITCODE -ne 0){throw 'Executable shared code verification failed.'}
    & python scripts/record_product_build.py
    if($LASTEXITCODE -ne 0){throw 'Build verification evidence could not be recorded.'}
}finally{
    foreach($ticketName in $ticketNames){[Environment]::SetEnvironmentVariable($ticketName,$ticketPrevious[$ticketName],'Process')}
    Pop-Location
}
