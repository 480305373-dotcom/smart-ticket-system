param([switch]$Demo,[switch]$WithAi,[switch]$Monitoring,[switch]$Build,[switch]$Prebuilt)
$ErrorActionPreference='Stop'
$ticketRoot=Split-Path -Parent $PSScriptRoot
Push-Location $ticketRoot
try {
    & (Join-Path $PSScriptRoot 'ensure-docker.ps1')
    New-Item -ItemType Directory -Force .runtime | Out-Null
    $ticketEnv='.runtime/production.env'
    if(-not(Test-Path $ticketEnv)){
        function New-TicketSecret { [Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(24)).ToLower() }
        $ticketMonitor=New-TicketSecret
        @("POSTGRES_PASSWORD=$(New-TicketSecret)","REDIS_PASSWORD=$(New-TicketSecret)","RABBITMQ_USER=smartticket","RABBITMQ_PASSWORD=$(New-TicketSecret)","JWT_SECRET=$([Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(48)))","MONITOR_PASSWORD=$ticketMonitor","GRAFANA_PASSWORD=$(New-TicketSecret)","ADMIN_USERNAME=admin","ADMIN_PASSWORD=$(New-TicketSecret)") | Set-Content $ticketEnv -Encoding utf8
        Set-Content .runtime/monitor-password $ticketMonitor -NoNewline -Encoding utf8
    }
    $ticketProfiles=if($Demo){'dev,prod'}else{'prod'}
    if($WithAi){$ticketProfiles+=',ai'}
    $env:APP_PROFILES=$ticketProfiles
    $env:BACKEND_BUILD_TARGET=if($Prebuilt){'prebuilt'}else{'production'}
    $env:FRONTEND_BUILD_TARGET=if($Prebuilt){'prebuilt'}else{'production'}
    if($Prebuilt -and (-not(Test-Path backend/target/backend-0.0.1-SNAPSHOT.jar) -or -not(Test-Path frontend/dist/index.html))){throw '预构建部署需要已构建的后端 JAR 和前端 dist；可先运行构建或使用发布 ZIP。'}
    $ticketArgs=@('compose','-p','smartticket-production','--env-file',$ticketEnv,'-f','compose.yaml','-f','compose.prod.yaml')
    if($Monitoring){$ticketArgs+=@('--profile','monitoring')}
    & docker @ticketArgs config --quiet
    if($LASTEXITCODE -ne 0){throw 'Compose 配置校验失败'}
    $ticketUp=@('up','-d');if($Build){$ticketUp+='--build'}
    & docker @ticketArgs @ticketUp
    if($LASTEXITCODE -ne 0){throw '容器启动失败'}
    Write-Host '应用：http://127.0.0.1:8088；私有初始密码保存在 .runtime/production.env。'
    if($Monitoring){Write-Host 'Prometheus：http://127.0.0.1:9090；Grafana：http://127.0.0.1:3000。'}
} finally {Pop-Location}
