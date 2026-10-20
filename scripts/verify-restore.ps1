param([switch]$Production,[switch]$Distributed,[switch]$Formal)
$ErrorActionPreference='Stop'
if((@($Production,$Distributed,$Formal | Where-Object {$_})).Count -gt 1){throw 'Choose one deployment mode.'}
if($Formal){
    & python (Join-Path $PSScriptRoot 'distributed_backup.py') --formal backup --verify
    exit $LASTEXITCODE
}
if($Distributed){
    & python (Join-Path $PSScriptRoot 'distributed_backup.py') backup --verify
    exit $LASTEXITCODE
}
$ticketRoot=Split-Path -Parent $PSScriptRoot
$ticketSource=if($Production){'smartticket-prod-postgres'}else{'smartticket-postgres'}
if($Production){& (Join-Path $PSScriptRoot 'backup-db.ps1') -Production}else{& (Join-Path $PSScriptRoot 'backup-db.ps1')}
$ticketArchive=Get-ChildItem (Join-Path $ticketRoot '.runtime/backups') -Filter "$ticketSource-*.dump" | Sort-Object LastWriteTime -Descending | Select-Object -First 1
$ticketName='smartticket-restore-'+[Guid]::NewGuid().ToString('N').Substring(0,10)
$ticketPassword=[Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(24))
$ticketQuery="SELECT json_build_object('users',(SELECT count(*) FROM app_user),'tickets',(SELECT count(*) FROM ticket),'articles',(SELECT count(*) FROM knowledge_article),'comments',(SELECT count(*) FROM ticket_comment),'history',(SELECT count(*) FROM ticket_history))"
$ticketExpected=(& docker exec $ticketSource psql -U smartticket -d intelligent_ticket -Atc $ticketQuery) -join ''
try {
    & docker run -d --rm --name $ticketName --network none --label smartticket.verification=restore --tmpfs '/var/lib/postgresql:rw,size=256m' -e POSTGRES_USER=smartticket -e POSTGRES_DB=restore_verification -e "POSTGRES_PASSWORD=$ticketPassword" pgvector/pgvector:pg18 | Out-Null
    if($LASTEXITCODE -ne 0){throw '恢复验证容器启动失败'}
    for($ticketTry=0;$ticketTry -lt 30;$ticketTry++){
        & docker exec $ticketName pg_isready -U smartticket -d restore_verification 2>$null | Out-Null
        if($LASTEXITCODE -eq 0){break};Start-Sleep -Seconds 1
    }
    & docker cp $ticketArchive.FullName "${ticketName}:/tmp/restore.dump"
    if($LASTEXITCODE -ne 0){throw '备份复制失败'}
    & docker exec $ticketName pg_restore -U smartticket -d restore_verification --exit-on-error /tmp/restore.dump
    if($LASTEXITCODE -ne 0){throw '恢复失败'}
    $ticketActual=(& docker exec $ticketName psql -U smartticket -d restore_verification -Atc $ticketQuery) -join ''
    if($LASTEXITCODE -ne 0 -or $ticketActual -ne $ticketExpected){throw '恢复后的核心表数量与源库不一致；请检查备份时是否仍有业务写入'}
    @{result='PASS';method='pg_dump custom archive + isolated pg_restore';source=$ticketSource;counts=($ticketActual|ConvertFrom-Json)} | ConvertTo-Json -Depth 4 | Set-Content (Join-Path $ticketRoot 'docs/restore-verification.json') -Encoding utf8
    Write-Host 'PASS: 备份在独立临时容器恢复成功，五张核心表数量一致；业务原库未覆盖。'
} finally {
    $ticketLabel=& docker inspect --format '{{index .Config.Labels "smartticket.verification"}}' $ticketName 2>$null
    if($ticketLabel -eq 'restore'){& docker stop $ticketName | Out-Null}
}
