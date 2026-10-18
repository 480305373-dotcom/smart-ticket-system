param([switch]$WithAi)
$ErrorActionPreference='Stop'
$ticketRoot=Split-Path -Parent $PSScriptRoot
Push-Location $ticketRoot
try {
    & docker info --format '{{.ServerVersion}}' 2>$null | Out-Null
    if($LASTEXITCODE -ne 0){ & docker desktop start --detach; if($LASTEXITCODE -ne 0){throw 'Docker Desktop 未能启动'} }
    for($ticketTry=0;$ticketTry -lt 30;$ticketTry++){
        & docker info --format '{{.ServerVersion}}' 2>$null | Out-Null
        if($LASTEXITCODE -eq 0){break}; Start-Sleep -Seconds 1
    }
    if(-not(Test-Path '.env')){throw '请复制 .env.example 为 .env，填写 POSTGRES_PASSWORD；已有数据库请使用原密码。'}
    $ticketLine=Get-Content '.env' | Where-Object {$_ -match '^POSTGRES_PASSWORD='} | Select-Object -First 1
    $env:ENTERPRISE_DB_PASSWORD=($ticketLine -split '=',2)[1].Trim('"',"'")
    if(-not $env:ENTERPRISE_DB_PASSWORD){throw '.env 中缺少 POSTGRES_PASSWORD'}
    & docker compose up -d postgres redis rabbitmq
    if($LASTEXITCODE -ne 0){throw '基础服务启动失败'}
    $ticketJar=Join-Path $ticketRoot 'backend/target/backend-0.0.1-SNAPSHOT.jar'
    if(-not(Test-Path $ticketJar) -or (Get-ChildItem backend/src -Recurse -File | Where-Object LastWriteTime -GT (Get-Item $ticketJar).LastWriteTime)){
        & .\backend\mvnw.cmd -f pom.xml -DskipTests package
        if($LASTEXITCODE -ne 0){throw '后端构建失败'}
    }
    if(Test-Path backend-dev.pid){
        $ticketOldPid=[int](Get-Content backend-dev.pid)
        $ticketOld=Get-CimInstance Win32_Process -Filter "ProcessId=$ticketOldPid"
        if($ticketOld -and $ticketOld.CommandLine -like '*backend-0.0.1-SNAPSHOT.jar*'){Stop-Process -Id $ticketOldPid}
    }
    $ticketProfiles=if($WithAi){'enterprise,ai'}else{'enterprise'}
    $ticketBackend=Start-Process -FilePath (Get-Command java).Source -ArgumentList @('-jar',$ticketJar,"--spring.profiles.active=$ticketProfiles") -WorkingDirectory $ticketRoot -RedirectStandardOutput "$ticketRoot/backend-dev.log" -RedirectStandardError "$ticketRoot/backend-dev-error.log" -WindowStyle Hidden -PassThru
    Set-Content backend-dev.pid $ticketBackend.Id
    if(-not(Test-Path frontend/node_modules)){Push-Location frontend;try{& ../scripts/npm.ps1 install}finally{Pop-Location}}
    $ticketListening=Get-NetTCPConnection -State Listen -LocalPort 5173 -ErrorAction SilentlyContinue
    if(-not $ticketListening){
        $ticketFrontend=Start-Process -FilePath (Get-Command node).Source -ArgumentList @("$ticketRoot/frontend/node_modules/vite/bin/vite.js",'--host','127.0.0.1') -WorkingDirectory "$ticketRoot/frontend" -RedirectStandardOutput "$ticketRoot/frontend-dev.log" -RedirectStandardError "$ticketRoot/frontend-dev-error.log" -WindowStyle Hidden -PassThru
        Set-Content frontend-dev.pid $ticketFrontend.Id
    }
    for($ticketTry=0;$ticketTry -lt 60;$ticketTry++){
        try{$ticketHealth=Invoke-WebRequest 'http://127.0.0.1:8080/actuator/health/readiness' -TimeoutSec 2;if($ticketHealth.StatusCode -eq 200){break}}catch{}
        if($ticketBackend.HasExited){throw '后端退出，请查看 backend-dev.log'};Start-Sleep -Seconds 1
    }
    if($ticketTry -eq 60){throw '后端启动超时，请查看日志'}
    Write-Host "已启动 $ticketProfiles，访问 http://127.0.0.1:5173；Docker 演示库位于 5433，原本机库未改动。"
} finally {Pop-Location}
