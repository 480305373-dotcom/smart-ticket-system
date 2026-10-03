# Uses installed npm, or bootstraps npm into the OS temporary directory for bundled Node.
param([Parameter(ValueFromRemainingArguments = $true)][string[]]$NpmArguments)
$ErrorActionPreference = 'Stop'
$ticketNpm = Get-Command npm.cmd -ErrorAction SilentlyContinue
if ($ticketNpm) { & $ticketNpm.Source @NpmArguments; exit $LASTEXITCODE }
$ticketNode = Get-Command node -ErrorAction SilentlyContinue
if (-not $ticketNode) { throw '请先安装 Node.js 22.12+（包含 npm），并重新打开终端。' }
$ticketNpmDir = Join-Path ([IO.Path]::GetTempPath()) 'smartticket-npm'
$ticketNpmCli = Join-Path $ticketNpmDir 'package/bin/npm-cli.js'
if (-not (Test-Path -LiteralPath $ticketNpmCli)) {
    New-Item -ItemType Directory -Force -Path $ticketNpmDir | Out-Null
    $ticketMetadata = Invoke-RestMethod 'https://registry.npmjs.org/npm/latest'
    $ticketArchive = Join-Path $ticketNpmDir 'npm.tgz'
    Invoke-WebRequest -Uri $ticketMetadata.dist.tarball -OutFile $ticketArchive
    & tar -xzf $ticketArchive -C $ticketNpmDir
    if ($LASTEXITCODE -ne 0) { throw 'npm bootstrap failed.' }
}
& $ticketNode.Source $ticketNpmCli @NpmArguments
exit $LASTEXITCODE
