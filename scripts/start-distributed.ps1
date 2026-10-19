param([switch]$SkipBuild)
$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
Push-Location $root
try {
    New-Item -ItemType Directory -Force -Path .runtime | Out-Null
    if (!(Test-Path -LiteralPath .runtime/distributed.env)) {
        python -c "import secrets,base64,pathlib; keys=['POSTGRES_PASSWORD','CORE_DB_PASSWORD','KNOWLEDGE_DB_PASSWORD','REDIS_PASSWORD','RABBITMQ_PASSWORD','RABBITMQ_COOKIE','INTERNAL_SERVICE_KEY']; d={k:secrets.token_hex(24) for k in keys}; d['JWT_SECRET']=base64.b64encode(secrets.token_bytes(48)).decode(); pathlib.Path('.runtime/distributed.env').write_text(''.join(k+'='+v+'\n' for k,v in d.items()))"
        if ($LASTEXITCODE -ne 0) { throw 'Secret generation failed' }
    }
    if (!$SkipBuild) {
        mvn -B package -DskipTests
        if ($LASTEXITCODE -ne 0) { throw 'Backend build failed' }
        & "$PSScriptRoot/npm.ps1" --prefix frontend run build
        if ($LASTEXITCODE -ne 0) { throw 'Frontend build failed' }
    }
    docker compose -p smartticket-cluster --env-file .runtime/distributed.env -f compose.distributed.yaml up -d --build --scale backend=2 --scale knowledge=2
    if ($LASTEXITCODE -ne 0) { throw 'Distributed deployment failed' }
    Write-Host 'Distributed application: http://127.0.0.1:8090; Eureka: http://127.0.0.1:8501'
} finally { Pop-Location }
