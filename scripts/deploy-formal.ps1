param([switch]$SkipBuild,[switch]$SkipImageBuild,[switch]$WithAi)
$ErrorActionPreference='Stop'
$ticketRoot=Split-Path -Parent $PSScriptRoot
Push-Location $ticketRoot
try{
    & (Join-Path $PSScriptRoot 'ensure-docker.ps1')
    New-Item -ItemType Directory -Force .runtime | Out-Null
    $ticketEnv='.runtime/formal.env'
    if(-not(Test-Path -LiteralPath $ticketEnv)){
        $ticketVolumes=docker volume ls --filter label=com.docker.compose.project=smartticket-formal --quiet
        if($LASTEXITCODE -ne 0){throw 'Cannot inspect formal deployment volumes.'}
        if($ticketVolumes){throw 'Formal data already exists; restore its original .runtime/formal.env instead of generating different credentials.'}
        function New-FormalSecret {[Convert]::ToHexString([System.Security.Cryptography.RandomNumberGenerator]::GetBytes(24)).ToLowerInvariant()}
        $ticketKeys=@('POSTGRES_PASSWORD','CORE_DB_PASSWORD','KNOWLEDGE_DB_PASSWORD','REDIS_PASSWORD','RABBITMQ_PASSWORD','RABBITMQ_COOKIE','INTERNAL_SERVICE_KEY','MONITOR_PASSWORD','ADMIN_PASSWORD')
        $ticketValues=@($ticketKeys | ForEach-Object {$_+'='+(New-FormalSecret)})
        $ticketValues+=('JWT_SECRET='+[Convert]::ToBase64String([System.Security.Cryptography.RandomNumberGenerator]::GetBytes(48)))
        $ticketValues+='ADMIN_USERNAME=admin'
        $ticketValues | Set-Content -LiteralPath $ticketEnv -Encoding utf8
        if($IsLinux){& chmod 600 $ticketEnv}
    }
    & (Join-Path $PSScriptRoot 'new-local-tls.ps1')
    if(-not $SkipBuild){
        & mvn -B package -DskipTests
        if($LASTEXITCODE -ne 0){throw 'Backend executable build failed.'}
        & (Join-Path $PSScriptRoot 'npm.ps1') --prefix frontend run build
        if($LASTEXITCODE -ne 0){throw 'Frontend build failed.'}
    }
    & python scripts/verify_distributed_artifacts.py
    if($LASTEXITCODE -ne 0){throw 'Executable packages contain stale shared code.'}
    $ticketPreviousProfiles=$env:FORMAL_PROFILES
    try{
        $env:FORMAL_PROFILES=if($WithAi){'prod,distributed,ai'}else{'prod,distributed'}
        $ticketArgs=@('compose','-p','smartticket-formal','--env-file',$ticketEnv,'-f','compose.distributed.yaml','-f','compose.formal.yaml')
        & docker @ticketArgs config --quiet
        if($LASTEXITCODE -ne 0){throw 'Formal deployment configuration is invalid.'}
        $ticketUp=@('up','-d','--scale','backend=2','--scale','knowledge=2')
        if($SkipImageBuild){$ticketUp+='--no-build'}else{$ticketUp+='--build'}
        & docker @ticketArgs @ticketUp
        if($LASTEXITCODE -ne 0){throw 'Formal deployment failed; inspect the smartticket-formal project logs.'}
    }finally{$env:FORMAL_PROFILES=$ticketPreviousProfiles}
    Write-Host 'Formal HTTPS: https://localhost:8443 ; HTTP redirect: http://localhost:8089'
    Write-Host 'Private administrator credentials: .runtime/formal.env ; local CA: .runtime/formal/tls/ca.crt'
    Write-Host 'Run python scripts/formal_smoke.py to verify HTTPS and the real business workflow.'
}finally{Pop-Location}
