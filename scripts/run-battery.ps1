<#
.SYNOPSIS
  Executa a campanha de testes de carga (k6) nos dois alvos do Azure (Container App e Function App),
  alternando quem vai primeiro a cada repeticao, e exporta as metricas do periodo.
  Status: ESCRITO (nao testado na entrega).

.DESCRIPTION
  Usa scripts\run-k6.ps1 para cada execucao. Os enderecos vem de .env.azure (AZ_CA_FQDN e AZ_FN).
  Ordem: para cada repeticao, para cada teste, para cada nivel, os dois alvos em sequencia
  (nunca ao mesmo tempo, porque compartilham o banco). Nas repeticoes pares a ordem dos alvos se inverte.
  O setup do k6 (verificacao e amostragem de ids) ja acorda o servico antes da fase de carga.
  Resultados em results\azure\<Tag>: *.summary.json, logs\*.txt, execucoes.csv, meta.json e metricas\.
  O banco precisa estar ligado.

.EXAMPLE
  .\scripts\run-battery.ps1 -DryRun
  .\scripts\run-battery.ps1 -Levels smoke -Repetitions 1 -Tag teste
  .\scripts\run-battery.ps1 -Levels low,medium -Tests read -Repetitions 1 -Tag calibracao
  .\scripts\run-battery.ps1 -Repetitions 3 -Tag campanha
#>
[CmdletBinding()]
param(
    [string[]]$Levels = @("low", "medium", "high"),
    [string[]]$Tests = @("read", "write"),
    [int]$Repetitions = 3,
    [string]$Tag = "campanha",
    [int]$PauseSeconds = 30,
    [switch]$DryRun
)

$root = Split-Path -Parent $PSScriptRoot
Set-Location $root
$envFile = Join-Path $root ".env.azure"
if (-not (Test-Path $envFile)) { Write-Host "ERRO: .env.azure nao encontrado"; exit 1 }
$cfg = @{}
Get-Content $envFile | ForEach-Object { $p = $_ -split '=', 2; if ($p.Count -eq 2) { $cfg[$p[0]] = $p[1] } }
if (-not $cfg["AZ_CA_FQDN"] -or -not $cfg["AZ_FN"]) { Write-Host "ERRO: AZ_CA_FQDN ou AZ_FN ausente em .env.azure"; exit 1 }
$urls = @{
    container = "https://" + $cfg["AZ_CA_FQDN"]
    functions = "https://" + $cfg["AZ_FN"] + ".azurewebsites.net"
}
$duracao = @{ smoke = 30; low = 120; medium = 180; high = 180 }

# plano
$plano = @()
for ($rep = 1; $rep -le $Repetitions; $rep++) {
    $ordem = @("container", "functions")
    if ($rep % 2 -eq 0) { $ordem = @("functions", "container") }
    foreach ($t in $Tests) {
        foreach ($l in $Levels) {
            foreach ($alvo in $ordem) {
                $plano += [pscustomobject]@{ Rep = $rep; Teste = $t; Nivel = $l; Alvo = $alvo }
            }
        }
    }
}
$segundos = 0
foreach ($item in $plano) { $segundos += $duracao[$item.Nivel] + 60 + $PauseSeconds }
Write-Host ("Plano: {0} execucoes, estimativa de {1} minutos (carga + cerca de 60 s de setup e fechamento por execucao + pausas)" -f $plano.Count, [math]::Round($segundos / 60))
if ($DryRun) { $plano | Format-Table -AutoSize; Write-Host "DryRun: nada foi executado."; exit 0 }

$outRel = "results\azure\" + $Tag
$outDir = Join-Path $root $outRel
$logDir = Join-Path $outDir "logs"
New-Item -ItemType Directory -Force -Path $logDir | Out-Null
$indexFile = Join-Path $outDir "execucoes.csv"
if (-not (Test-Path $indexFile)) { "rep,teste,nivel,alvo,inicio_utc,fim_utc,codigo_saida,resumo" | Set-Content $indexFile -Encoding ASCII }

# relogio do PC contra o servidor
$desvio = "n/d"
try {
    $r = Invoke-WebRequest -Uri "https://azure.microsoft.com" -UseBasicParsing -TimeoutSec 30
    $srv = ([DateTime]::Parse($r.Headers["Date"])).ToUniversalTime()
    $desvio = [math]::Round(((Get-Date).ToUniversalTime() - $srv).TotalSeconds)
    if ([math]::Abs($desvio) -gt 30) { Write-Host ("AVISO: o relogio do PC difere do servidor em {0} s. Sincronize o relogio antes da campanha." -f $desvio) }
} catch { Write-Host "AVISO: nao consegui comparar o relogio com o servidor." }

# verificacao dos dois alvos (e do banco)
foreach ($alvo in @("container", "functions")) {
    try {
        $h = Invoke-RestMethod -Uri ($urls[$alvo] + "/health/ready") -TimeoutSec 180
        if ($h.database -ne "UP") { Write-Host ("ERRO: {0} responde, mas o banco nao esta UP. Ligue o banco e tente de novo." -f $alvo); exit 1 }
    } catch { Write-Host ("ERRO: {0} nao respondeu em /health/ready ({1}). O banco esta ligado?" -f $alvo, $_.Exception.Message); exit 1 }
}

$k6v = ""
try { $k6v = ((& k6 version) | Out-String).Trim() } catch { $k6v = "n/d" }
$meta = [ordered]@{
    tag = $Tag; niveis = ($Levels -join ","); testes = ($Tests -join ","); repeticoes = $Repetitions
    pausa_s = $PauseSeconds; k6 = $k6v; desvio_relogio_pc_s = $desvio
    inicio_utc = (Get-Date).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ssZ")
}
$meta | ConvertTo-Json | Set-Content -Path (Join-Path $outDir "meta.json") -Encoding UTF8

$inicioCampanha = (Get-Date).ToUniversalTime()
$n = 0
foreach ($item in $plano) {
    $n++
    $ini = (Get-Date).ToUniversalTime()
    $nome = "{0:D2}-r{1}-{2}-{3}-{4}" -f $n, $item.Rep, $item.Teste, $item.Nivel, $item.Alvo
    $log = Join-Path $logDir ($nome + ".txt")
    Write-Host ("[{0}/{1}] {2}  inicio {3:HH:mm:ss}Z" -f $n, $plano.Count, $nome, $ini)
    & (Join-Path $PSScriptRoot "run-k6.ps1") -Target $item.Alvo -Test $item.Teste -Level $item.Nivel -BaseUrl $urls[$item.Alvo] -OutDir $outRel *>&1 | Tee-Object -FilePath $log | Out-Null
    $codigo = $LASTEXITCODE
    $fim = (Get-Date).ToUniversalTime()
    $resumo = ""
    $arq = Get-ChildItem $outDir -Filter "*.summary.json" | Where-Object { $_.LastWriteTimeUtc -ge $ini.AddSeconds(-5) } | Sort-Object LastWriteTime | Select-Object -Last 1
    if ($arq) { $resumo = $arq.Name }
    ("{0},{1},{2},{3},{4},{5},{6},{7}" -f $item.Rep, $item.Teste, $item.Nivel, $item.Alvo, $ini.ToString("yyyy-MM-ddTHH:mm:ssZ"), $fim.ToString("yyyy-MM-ddTHH:mm:ssZ"), $codigo, $resumo) | Add-Content $indexFile -Encoding ASCII
    Write-Host ("    fim {0:HH:mm:ss}Z  codigo de saida {1}  {2}" -f $fim, $codigo, $resumo)
    Select-String -Path $log -Pattern "requisicoes \(fase", "latencia ms", "Livros antes", "descartadas" -ErrorAction SilentlyContinue | Select-Object -First 4 | ForEach-Object { Write-Host ("    " + $_.Line.Trim()) }
    if ($n -lt $plano.Count) { Start-Sleep -Seconds $PauseSeconds }
}
$fimCampanha = (Get-Date).ToUniversalTime()

Write-Host ""
Write-Host "=== Exportando metricas do Azure Monitor do periodo ==="
$ps = $inicioCampanha.AddMinutes(-3).ToString("yyyy-MM-ddTHH:mm:ssZ")
$pe = $fimCampanha.AddMinutes(3).ToString("yyyy-MM-ddTHH:mm:ssZ")
& (Join-Path $PSScriptRoot "export-azure-metrics.ps1") -Start $ps -End $pe -Tag $Tag -OutDir ("results\azure\" + $Tag + "\metricas")

Write-Host ""
Write-Host ("Campanha concluida: {0} execucoes. Indice: {1}" -f $plano.Count, $indexFile)
Write-Host "Confira o custo real em Gerenciamento de Custos (os dados atrasam algumas horas)."
