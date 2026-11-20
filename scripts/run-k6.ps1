<#
.SYNOPSIS
  Executa um teste de carga k6 (leitura ou escrita) contra uma das implantacoes da API e grava o
  resumo da execucao em um arquivo JSON em results\ (Etapa 7 / ADR-023).
.DESCRIPTION
  Alvos (URL padrao): container = JAR em http://localhost:8080 ; functions = Functions local em
  http://localhost:7071 ; image = imagem Docker (scripts\run-image.ps1) em http://localhost:8081.
  Use -BaseUrl para apontar para outro endereco (ex.: Azure, nas etapas finais).
  Os niveis (smoke, low, medium, high) sao PROVISORIOS e servem para validar os scripts na maquina
  local; as medicoes finais usarao niveis definidos na metodologia. -Rate, -Duration e -MaxVus
  sobrescrevem o nivel. Modelo de carga: taxa de chegada constante (-Profile constant) ou degraus
  (-Profile ramp). Leitura: 1 iteracao = 1 requisicao. Escrita: 1 iteracao = POST + PUT + DELETE.
  Codigo de saida do k6: 0 = ok; 99 = algum limite (threshold) violado; outros = erro.
  Variaveis opcionais (ambiente): MIX_LIST, MIX_GET, MIX_SEARCH, P95_MS, MAX_ERROR_RATE, PAGE_SIZE,
  RAMP_STEPS, ID_SAMPLE_PAGES, RUN_SEED (ver README.md, secao "Etapa 7").
.PARAMETER Target
  container | functions | image
.PARAMETER Test
  read | write
.PARAMETER Level
  smoke (padrao) | low | medium | high
.PARAMETER Profile
  constant (padrao) | ramp
.PARAMETER ResetDb
  Restaura a carga padrao (10000 livros) antes de executar (recomendado antes de cada teste).
.PARAMETER Raw
  Grava tambem todas as medicoes em results\raw\<execucao>.json (arquivo grande).
.EXAMPLE
  .\scripts\run-k6.ps1 -Target image -Test read -Level smoke
.EXAMPLE
  .\scripts\run-k6.ps1 -Target functions -Test write -Level low -ResetDb
#>
param(
    [Parameter(Mandatory = $true)][ValidateSet("container", "functions", "image")][string]$Target,
    [Parameter(Mandatory = $true)][ValidateSet("read", "write")][string]$Test,
    [ValidateSet("smoke", "low", "medium", "high")][string]$Level = "smoke",
    [ValidateSet("constant", "ramp")][string]$Profile = "constant",
    [int]$Rate = 0,
    [string]$Duration = "",
    [int]$MaxVus = 0,
    [string]$BaseUrl = "",
    [string]$OutDir = "results",
    [switch]$ResetDb,
    [switch]$Raw
)

Set-Location (Join-Path $PSScriptRoot "..")

if (-not (Get-Command k6 -ErrorAction SilentlyContinue)) {
    throw "k6 nao encontrado. Instale com: winget install k6 --source winget  (depois feche e reabra o VS Code)."
}

if ($BaseUrl -eq "") {
    switch ($Target) {
        "container" { $BaseUrl = "http://localhost:8080" }
        "functions" { $BaseUrl = "http://localhost:7071" }
        "image"     { $BaseUrl = "http://localhost:8081" }
    }
}

$scriptPath = Join-Path $PWD.Path ("k6\" + $Test + ".js")
if (-not (Test-Path $scriptPath)) { throw "Script nao encontrado: $scriptPath" }

if ($ResetDb) {
    & (Join-Path $PSScriptRoot "db-reset.ps1") -Rows 10000
    if ($LASTEXITCODE -ne 0) { throw "Falha ao restaurar o banco (db-reset.ps1)." }
}

$runId = (Get-Date -Format "yyyyMMdd-HHmmss") + "-" + $Test + "-" + $Target + "-" + $Level
if ($Profile -eq "ramp") { $runId = $runId + "-ramp" }

New-Item -ItemType Directory -Force -Path $OutDir | Out-Null

# Configuracao do teste por variaveis de ambiente do processo (lidas pelo k6 como __ENV).
$env:BASE_URL = $BaseUrl
$env:TARGET = $Target
$env:LEVEL = $Level
$env:PROFILE = $Profile
$env:RUN_ID = $runId
$env:RESULTS_DIR = $OutDir.Replace("\", "/")
if ($Rate -gt 0) { $env:RATE = "$Rate" } else { Remove-Item Env:RATE -ErrorAction SilentlyContinue }
if ($Duration -ne "") { $env:DURATION = $Duration } else { Remove-Item Env:DURATION -ErrorAction SilentlyContinue }
if ($MaxVus -gt 0) { $env:MAX_VUS = "$MaxVus" } else { Remove-Item Env:MAX_VUS -ErrorAction SilentlyContinue }

$k6Args = @("run")
if ($Raw) {
    New-Item -ItemType Directory -Force -Path (Join-Path $OutDir "raw") | Out-Null
    $k6Args += @("--out", ("json=" + $OutDir.Replace("\", "/") + "/raw/" + $runId + ".json"))
}
$k6Args += $scriptPath

Write-Host "Execucao: $runId | alvo: $Target ($BaseUrl) | teste: $Test | nivel: $Level | perfil: $Profile"
& k6 @k6Args
$code = $LASTEXITCODE

Write-Host ""
switch ($code) {
    0  { Write-Host "k6 terminou sem violar limites." }
    99 { Write-Host "k6 terminou, mas algum limite (threshold) foi violado (veja o resumo). Os resultados continuam validos como registro." }
    default { Write-Host "k6 terminou com erro (codigo $code). Veja as mensagens acima." }
}
$summaryFile = Join-Path $OutDir ($runId + ".summary.json")
if (Test-Path $summaryFile) { Write-Host "Resumo gravado em: $summaryFile" } else { Write-Host "Arquivo de resumo nao encontrado: $summaryFile" }
exit $code
