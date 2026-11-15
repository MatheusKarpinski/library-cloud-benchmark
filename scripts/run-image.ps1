<#
.SYNOPSIS
  Executa a imagem Docker da API (adaptador de container) ligada ao PostgreSQL local do docker-compose.
.DESCRIPTION
  Le o .env (DB_URL, DB_USER, DB_PASSWORD, DB_POOL_*), descobre a rede do container do banco e inicia a
  imagem nessa rede. Dentro dessa rede o banco e acessado pelo nome "postgres" (nao por "localhost"),
  por isso o DB_URL e reescrito (mesmo nome de banco do DB_URL original). A porta HTTP do conteiner
  e sempre 8080; no host ela e publicada em 127.0.0.1:<Port> (padrao 8081, para nao conflitar com o
  JAR rodando na 8080). A senha nao aparece na linha de comando (passa por variavel de ambiente do
  processo). Para encerrar: Ctrl+C (ou "docker stop library-api-local" em outro terminal).
.PARAMETER Image
  Imagem a executar (padrao: library-api:local).
.PARAMETER Port
  Porta no host (padrao: 8081).
.PARAMETER Memory
  Limite de memoria do conteiner, ex.: 512m (opcional; sem limite, a JVM enxerga a memoria da VM do Docker).
.PARAMETER Cpus
  Limite de CPUs, ex.: 1 ou 0.5 (opcional).
.EXAMPLE
  .\scripts\run-image.ps1
.EXAMPLE
  .\scripts\run-image.ps1 -Memory 512m -Cpus 1
#>
param(
    [string]$Image = "library-api:local",
    [int]$Port = 8081,
    [string]$EnvPath = ".env",
    [string]$DbContainer = "library-postgres",
    [string]$Name = "library-api-local",
    [string]$Memory = "",
    [string]$Cpus = ""
)

Set-Location (Join-Path $PSScriptRoot "..")

if (-not (Test-Path $EnvPath)) {
    throw "Arquivo '$EnvPath' nao encontrado. Copie .env.example para .env e ajuste os valores."
}

# Le NOME=valor (ignora linhas vazias e comentarios), como o scripts\load-env.ps1.
$fromEnv = @{}
foreach ($rawLine in (Get-Content -Path $EnvPath -Encoding UTF8)) {
    $line = $rawLine.Trim()
    if ($line -eq "" -or $line.StartsWith("#")) { continue }
    $separator = $line.IndexOf("=")
    if ($separator -lt 1) { continue }
    $fromEnv[$line.Substring(0, $separator).Trim()] = $line.Substring($separator + 1).Trim()
}

$missing = @(@("DB_URL", "DB_USER", "DB_PASSWORD") | Where-Object { -not $fromEnv.ContainsKey($_) -or $fromEnv[$_] -eq "" })
if ($missing.Count -gt 0) { throw ("Variaveis ausentes em '$EnvPath': " + ($missing -join ", ")) }

$dbMatch = [regex]::Match($fromEnv["DB_URL"], '^jdbc:postgresql://[^/]+/([^?]+)')
if (-not $dbMatch.Success) { throw "DB_URL com formato inesperado (esperado jdbc:postgresql://host:porta/banco)." }
$dbName = $dbMatch.Groups[1].Value

# A imagem existe?
$null = docker image inspect $Image 2>$null
if ($LASTEXITCODE -ne 0) { throw "Imagem '$Image' nao encontrada. Construa com: docker build -t $Image ." }

# O container do banco esta de pe? Qual e a rede dele?
$running = docker inspect -f '{{.State.Running}}' $DbContainer 2>$null
if ($LASTEXITCODE -ne 0 -or "$running".Trim() -ne "true") {
    throw "Container '$DbContainer' nao esta em execucao. Suba o banco com: docker compose up -d"
}
$network = ("" + (docker inspect -f '{{range $k, $v := .NetworkSettings.Networks}}{{$k}} {{end}}' $DbContainer)).Trim().Split(" ")[0]
if ($network -eq "") { throw "Nao foi possivel descobrir a rede do container '$DbContainer'." }

$dockerArgs = @(
    "run", "--rm", "--name", $Name, "--network", $network,
    "-p", "127.0.0.1:${Port}:8080",
    "-e", "SERVER_PORT=8080",
    "-e", "DB_URL=jdbc:postgresql://postgres:5432/$dbName"
)
if ($Memory -ne "") { $dockerArgs += @("--memory", $Memory) }
if ($Cpus -ne "") { $dockerArgs += @("--cpus", $Cpus) }

# Repassa as demais variaveis pelo ambiente do processo (a senha nao vai para a linha de comando).
foreach ($varName in @("DB_USER", "DB_PASSWORD", "DB_POOL_MAX", "DB_POOL_MIN_IDLE", "DB_CONNECTION_TIMEOUT_MS")) {
    if ($fromEnv.ContainsKey($varName) -and $fromEnv[$varName] -ne "") {
        Set-Item -Path ("Env:" + $varName) -Value $fromEnv[$varName]
        $dockerArgs += @("-e", $varName)
    }
}
$dockerArgs += $Image

Write-Host "Imagem: $Image | rede: $network | banco: postgres:5432/$dbName | host: http://localhost:$Port"
if ($Memory -ne "") { Write-Host "Limite de memoria: $Memory" }
if ($Cpus -ne "") { Write-Host "Limite de CPUs: $Cpus" }
& docker @dockerArgs
