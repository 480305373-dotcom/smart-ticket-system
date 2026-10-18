$ErrorActionPreference='Stop'
& docker info --format '{{.ServerVersion}}' 2>$null | Out-Null
if($LASTEXITCODE -ne 0){ & docker desktop start --detach; if($LASTEXITCODE -ne 0){throw 'Docker Desktop 未能启动'} }
for($ticketDockerTry=0;$ticketDockerTry -lt 60;$ticketDockerTry++){
    & docker info --format '{{.ServerVersion}}' 2>$null | Out-Null
    if($LASTEXITCODE -eq 0){return}
    Start-Sleep -Seconds 1
}
throw 'Docker 引擎启动超时，请查看 Docker Desktop 状态。'
