param([switch]$Production,[switch]$Distributed,[switch]$Formal)
$ErrorActionPreference='Stop'
if((@($Production,$Distributed,$Formal | Where-Object {$_})).Count -gt 1){throw 'Choose one deployment mode.'}
if($Formal){
    & python (Join-Path $PSScriptRoot 'distributed_backup.py') --formal backup
    exit $LASTEXITCODE
}
if($Distributed){
    & python (Join-Path $PSScriptRoot 'distributed_backup.py') backup
    exit $LASTEXITCODE
}
$ticketRoot=Split-Path -Parent $PSScriptRoot
$ticketContainer=if($Production){'smartticket-prod-postgres'}else{'smartticket-postgres'}
$ticketStamp=Get-Date -Format 'yyyyMMdd-HHmmss'
$ticketTemp="/tmp/smartticket-$ticketStamp.dump"
$ticketBackupDir=Join-Path $ticketRoot '.runtime/backups'
New-Item -ItemType Directory -Force $ticketBackupDir | Out-Null
$ticketDestination=Join-Path $ticketBackupDir "$ticketContainer-$ticketStamp.dump"
& docker exec $ticketContainer pg_dump -U smartticket -d intelligent_ticket -Fc -f $ticketTemp
if($LASTEXITCODE -ne 0){throw 'PostgreSQL 备份失败'}
& docker cp "${ticketContainer}:$ticketTemp" $ticketDestination
if($LASTEXITCODE -ne 0){throw '备份复制失败'}
& docker exec $ticketContainer pg_restore --list $ticketTemp | Out-Null
if($LASTEXITCODE -ne 0){throw '备份目录校验失败'}
Write-Host "已备份并校验 PostgreSQL 归档目录：$ticketDestination"
Write-Host '归档含真实业务数据，请保留在私有位置，不要提交到 Git。'
