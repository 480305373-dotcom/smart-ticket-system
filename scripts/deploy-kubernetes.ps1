param([string]$KindPath='.runtime/tools/kind.exe',[switch]$SkipImageLoad)
$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
Push-Location $root
try {
    if (!(Test-Path -LiteralPath .runtime/distributed.env)) { throw 'Run start-distributed.ps1 first to build images and generate private credentials.' }
    $config=Join-Path $root '.runtime/kubeconfig'
    $clusters=& $KindPath get clusters
    if ('smartticket' -notin $clusters) {
        & $KindPath create cluster --name smartticket --config deploy/kubernetes/kind.json --kubeconfig $config --wait 180s
        if ($LASTEXITCODE -ne 0) { throw 'kind cluster creation failed' }
    } else {
        & $KindPath export kubeconfig --name smartticket --kubeconfig $config
    }
    if (!$SkipImageLoad) {
        & $KindPath load docker-image smartticket/backend:local smartticket/knowledge-service:local smartticket/gateway:local smartticket/frontend:local smartticket/registry:local pgvector/pgvector:pg18 redis:7-alpine rabbitmq:4-management --name smartticket
        if ($LASTEXITCODE -ne 0) { throw 'Application image load failed' }
    }
    kubectl --kubeconfig $config create namespace smartticket --dry-run=client -o yaml | kubectl --kubeconfig $config apply -f -
    kubectl --kubeconfig $config -n smartticket create secret generic cluster-secrets --from-env-file=.runtime/distributed.env --dry-run=client -o yaml | kubectl --kubeconfig $config apply -f -
    # Re-run only the disposable initialization Job; StatefulSet volumes are retained.
    kubectl --kubeconfig $config -n smartticket delete job redis-cluster-init --ignore-not-found
    if ($LASTEXITCODE -ne 0) { throw 'Previous Redis initialization Job cleanup failed' }
    kubectl --kubeconfig $config apply -k deploy/kubernetes
    if ($LASTEXITCODE -ne 0) { throw 'Kubernetes apply failed' }
    Write-Host 'Resources applied. Inspect readiness before visiting http://127.0.0.1:8091.'
} finally { Pop-Location }
