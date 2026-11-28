<#
.SYNOPSIS
  Exporta metricas do Azure Monitor (Container App, Function App e banco PostgreSQL) para arquivos JSON locais.
  Status: TESTADO em 26/11/2026 para Container App e Function App (13 metricas); as metricas do banco foram acrescentadas e TESTADAS em 27/11/2026 (a metrica tps veio sem dados e foi removida).

.DESCRIPTION
  Le os nomes dos recursos em .env.azure (AZ_RG, AZ_FN), monta os identificadores dos recursos e,
  para cada metrica da lista abaixo, grava um arquivo JSON em results\azure\metrics (ou -OutDir).
  Nao altera nada no Azure (somente leitura). Se uma metrica falhar, avisa e continua.

.EXAMPLE
  .\scripts\export-azure-metrics.ps1 -Hours 3
  .\scripts\export-azure-metrics.ps1 -Start "2026-11-26T14:00:00Z" -End "2026-11-26T15:30:00Z" -Tag bateria1
#>
[CmdletBinding()]
param(
    [int]$Hours = 0,
    [string]$Start,
    [string]$End,
    [string]$Interval = "PT1M",
    [string]$Tag = "manual",
    [string]$OutDir = "results\azure\metrics"
)

$root = Split-Path -Parent $PSScriptRoot
$envFile = Join-Path $root ".env.azure"
if (-not (Test-Path $envFile)) { Write-Host "ERRO: .env.azure nao encontrado em $root"; exit 1 }
$cfg = @{}
Get-Content $envFile | ForEach-Object { $p = $_ -split '=', 2; if ($p.Count -eq 2) { $cfg[$p[0]] = $p[1] } }
if (-not $cfg["AZ_RG"] -or -not $cfg["AZ_FN"]) { Write-Host "ERRO: AZ_RG ou AZ_FN ausente em .env.azure"; exit 1 }

if ($Hours -gt 0) {
    $endDt = (Get-Date).ToUniversalTime()
    $startDt = $endDt.AddHours(-$Hours)
} elseif ($Start -and $End) {
    $startDt = ([DateTime]::Parse($Start)).ToUniversalTime()
    $endDt = ([DateTime]::Parse($End)).ToUniversalTime()
} else {
    Write-Host "ERRO: informe -Hours N ou -Start e -End (horario UTC, ex.: 2026-11-26T14:00:00Z)"; exit 1
}
$s = $startDt.ToString("yyyy-MM-ddTHH:mm:ssZ")
$e = $endDt.ToString("yyyy-MM-ddTHH:mm:ssZ")

$sub = (az account show --query id -o tsv)
if ($LASTEXITCODE -ne 0 -or -not $sub) { Write-Host "ERRO: sem login no Azure CLI (rode az login)"; exit 1 }
$rg = $cfg["AZ_RG"]; $fn = $cfg["AZ_FN"]; $pg = $cfg["AZ_PG_SERVER"]
$ids = @{
    ca = "/subscriptions/$sub/resourceGroups/$rg/providers/Microsoft.App/containerApps/ca-library-api"
    fn = "/subscriptions/$sub/resourceGroups/$rg/providers/Microsoft.Web/sites/$fn"
    db = "/subscriptions/$sub/resourceGroups/$rg/providers/Microsoft.DBforPostgreSQL/flexibleServers/$pg"
}

# alvo, metrica, agregacoes (nomes vistos em az monitor metrics list-definitions, 26/11/2026)
$lista = @(
    @("ca", "Replicas", @("Maximum")),
    @("ca", "Requests", @("Total")),
    @("ca", "UsageNanoCores", @("Average", "Maximum")),
    @("ca", "WorkingSetBytes", @("Average", "Maximum")),
    @("ca", "RestartCount", @("Total")),
    @("ca", "RxBytes", @("Total")),
    @("ca", "TxBytes", @("Total")),
    @("ca", "ResponseTime", @("Average")),
    @("fn", "InstanceCount", @("Maximum")),
    @("fn", "OnDemandFunctionExecutionCount", @("Total")),
    @("fn", "OnDemandFunctionExecutionUnits", @("Total")),
    @("fn", "CpuPercentage", @("Average", "Maximum")),
    @("fn", "AverageMemoryWorkingSet", @("Average", "Maximum")),
    @("db", "cpu_percent", @("Average", "Maximum")),
    @("db", "memory_percent", @("Average", "Maximum")),
    @("db", "active_connections", @("Maximum")),
    @("db", "connections_failed", @("Total")),
    @("db", "disk_iops_consumed_percentage", @("Maximum"))
)

$outFull = Join-Path $root $OutDir
New-Item -ItemType Directory -Force -Path $outFull | Out-Null
$stamp = (Get-Date).ToString("yyyyMMdd-HHmmss")
$linhas = @()

foreach ($item in $lista) {
    $alvo = $item[0]; $metrica = $item[1]; $aggs = $item[2]
    $arq = Join-Path $outFull ("{0}-{1}-{2}-{3}.json" -f $stamp, $Tag, $alvo, $metrica)
    $errTmp = Join-Path $env:TEMP "export-azure-metrics.err"
    $saida = az monitor metrics list --resource $ids[$alvo] --metric $metrica --aggregation $aggs --interval $Interval --start-time $s --end-time $e -o json 2> $errTmp
    if ($LASTEXITCODE -ne 0) {
        Write-Host ("FALHOU: {0} {1}" -f $alvo, $metrica)
        if (Test-Path $errTmp) { Write-Host ((Get-Content $errTmp -Raw).Trim()) }
        $linhas += ("{0,-3} {1,-34} FALHOU" -f $alvo, $metrica)
        continue
    }
    $saida | Set-Content -Path $arq -Encoding UTF8
    $total = 0; $comDado = 0; $maximo = $null
    try {
        $j = ($saida | Out-String) | ConvertFrom-Json
        $ts = $j.value[0].timeseries
        if ($ts -and $ts.Count -gt 0) {
            foreach ($pt in $ts[0].data) {
                $total++
                $v = $null
                foreach ($campo in @("maximum", "total", "average")) { if ($null -ne $pt.$campo) { $v = $pt.$campo; break } }
                if ($null -ne $v) { $comDado++; if ($null -eq $maximo -or $v -gt $maximo) { $maximo = $v } }
            }
        }
    } catch { }
    $linhas += ("{0,-3} {1,-34} pontos={2} com_dado={3} maior_valor={4}" -f $alvo, $metrica, $total, $comDado, $maximo)
}

$meta = [ordered]@{ tag = $Tag; inicio_utc = $s; fim_utc = $e; intervalo = $Interval; gerado_em = $stamp; aviso = "estimativa de coleta; nao contem enderecos nem senhas" }
$meta | ConvertTo-Json | Set-Content -Path (Join-Path $outFull ("{0}-{1}-meta.json" -f $stamp, $Tag)) -Encoding UTF8

Write-Host ""
Write-Host "=== Resumo da exportacao ($s ate $e, intervalo $Interval) ==="
$linhas | ForEach-Object { Write-Host $_ }
Write-Host "Arquivos em: $outFull"
