[CmdletBinding(SupportsShouldProcess)]
param([switch]$StopInfrastructure)
$ErrorActionPreference='Stop'
$ticketRoot=Split-Path -Parent $PSScriptRoot
foreach($ticketEntry in @(@{File='backend-dev.pid';Pattern='*backend-0.0.1-SNAPSHOT.jar*'},@{File='frontend-dev.pid';Pattern='*frontend/node_modules/vite/bin/vite.js*'},@{File='frontend-preview.pid';Pattern='*frontend/node_modules/vite/bin/vite.js*'})){
    $ticketPidFile=Join-Path $ticketRoot $ticketEntry.File
    if(-not(Test-Path -LiteralPath $ticketPidFile)){continue}
    $ticketProcessId=0
    if(-not[int]::TryParse((Get-Content -LiteralPath $ticketPidFile -Raw).Trim(),[ref]$ticketProcessId)){continue}
    $ticketProcess=Get-CimInstance Win32_Process -Filter "ProcessId=$ticketProcessId"
    $ticketNormalized=if($ticketProcess){$ticketProcess.CommandLine.Replace('\','/')}else{''}
    $ticketNormalizedRoot=$ticketRoot.Replace('\','/')
    if($ticketProcess -and $ticketNormalized -like "*$ticketNormalizedRoot*" -and $ticketNormalized -like $ticketEntry.Pattern){
        if($PSCmdlet.ShouldProcess("项目进程 $ticketProcessId",'停止')){Stop-Process -Id $ticketProcessId}
    }
}
if($StopInfrastructure -and $PSCmdlet.ShouldProcess('本项目 PostgreSQL / Redis / RabbitMQ 容器','停止并保留数据卷')){
    & docker compose -f (Join-Path $ticketRoot 'compose.yaml') stop postgres redis rabbitmq
    if($LASTEXITCODE -ne 0){throw '基础服务停止失败'}
}
