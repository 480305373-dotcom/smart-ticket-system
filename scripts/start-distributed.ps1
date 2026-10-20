param([switch]$SkipBuild,[switch]$SkipImageBuild)
$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
Push-Location $root
try {
    New-Item -ItemType Directory -Force -Path .runtime | Out-Null
    if (!(Test-Path -LiteralPath .runtime/distributed.env)) {
        $existingVolumes=docker volume ls --filter label=com.docker.compose.project=smartticket-cluster --quiet
        if($LASTEXITCODE -ne 0){throw 'Docker is unavailable; start Docker before generating deployment credentials.'}
        if($existingVolumes){throw 'Existing cluster data volumes found. Restore .runtime/distributed.env instead of generating different credentials.'}
        python -c "import secrets,base64,pathlib; keys=['POSTGRES_PASSWORD','CORE_DB_PASSWORD','KNOWLEDGE_DB_PASSWORD','REDIS_PASSWORD','RABBITMQ_PASSWORD','RABBITMQ_COOKIE','INTERNAL_SERVICE_KEY']; d={k:secrets.token_hex(24) for k in keys}; d['JWT_SECRET']=base64.b64encode(secrets.token_bytes(48)).decode(); pathlib.Path('.runtime/distributed.env').write_text(''.join(k+'='+v+'\n' for k,v in d.items()))"
        if ($LASTEXITCODE -ne 0) { throw 'Secret generation failed' }
    }
    if (!$SkipBuild) {
        mvn -B package -DskipTests
        if ($LASTEXITCODE -ne 0) { throw 'Backend build failed' }
        & "$PSScriptRoot/npm.ps1" --prefix frontend run build
        if ($LASTEXITCODE -ne 0) { throw 'Frontend build failed' }
    }
    python scripts/verify_distributed_artifacts.py
    if ($LASTEXITCODE -ne 0) { throw 'Distributed executable artifacts are stale; rebuild the Maven reactor.' }
    $redisTemplateHash=(Get-FileHash docker/distributed/redis-start.sh -Algorithm SHA256).Hash.ToLowerInvariant()
    $redisContainer=docker compose -p smartticket-cluster --env-file .runtime/distributed.env -f compose.distributed.yaml ps -q redis-0
    if($LASTEXITCODE -ne 0){throw 'Could not inspect the dedicated Redis deployment'}
    if($redisContainer){
        $redisPreviousHash=docker inspect --format '{{index .Config.Labels "smartticket.config-sha256"}}' $redisContainer
        if($LASTEXITCODE -ne 0){throw 'Could not inspect Redis bootstrap version'}
        if($redisPreviousHash -ne $redisTemplateHash){
            # Upgrade the bootstrap consistently; avoid resolving old/new aliases together.
            docker compose -p smartticket-cluster --env-file .runtime/distributed.env -f compose.distributed.yaml stop redis-0 redis-1 redis-2 redis-3 redis-4 redis-5
            if($LASTEXITCODE -ne 0){throw 'Could not stop Redis nodes for the bootstrap configuration upgrade'}
        }
    }
    $composeArguments=@('compose','-p','smartticket-cluster','--env-file','.runtime/distributed.env','-f','compose.distributed.yaml','up','-d','--scale','backend=2','--scale','knowledge=2')
    if($SkipImageBuild){$composeArguments+='--no-build'}else{$composeArguments+='--build'}
    docker @composeArguments
    if ($LASTEXITCODE -ne 0) { throw 'Distributed deployment failed' }
    Write-Host 'Distributed application: http://127.0.0.1:8090; Eureka: http://127.0.0.1:8501'
} finally { Pop-Location }
