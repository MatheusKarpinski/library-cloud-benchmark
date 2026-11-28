<#
.SYNOPSIS
  Consolida os resultados de uma campanha (execucoes.csv e os .summary.json) em dois arquivos CSV:
  um por execucao e outro por grupo (teste, nivel, alvo) com medias entre as repeticoes.
  Status: ESCRITO (nao testado na entrega).

.EXAMPLE
  .\scripts\consolidar-resultados.ps1 -Tag calibracao-high
  .\scripts\consolidar-resultados.ps1 -Tag campanha
#>
[CmdletBinding()]
param([string]$Tag = "campanha")

$root = Split-Path -Parent $PSScriptRoot
$dir = Join-Path $root ("results\azure\" + $Tag)
$idx = Join-Path $dir "execucoes.csv"
if (-not (Test-Path $idx)) { Write-Host "ERRO: nao encontrei $idx"; exit 1 }
$inv = [Globalization.CultureInfo]::InvariantCulture

function Fmt($v, [int]$casas) {
    if ($null -eq $v -or "$v" -eq "") { return "" }
    return ([double]$v).ToString("F$casas", $inv)
}
function Stat($itens, [string]$campo) {
    $vals = @($itens | Where-Object { $null -ne $_.$campo } | ForEach-Object { [double]$_.$campo })
    if ($vals.Count -eq 0) { return $null }
    $m = $vals | Measure-Object -Average -Minimum -Maximum
    return $m
}

$linhas = @()
foreach ($e in (Import-Csv $idx)) {
    if (-not $e.resumo) { Write-Host ("AVISO: sem resumo para rep {0} {1} {2} {3}" -f $e.rep, $e.teste, $e.nivel, $e.alvo); continue }
    $arq = Join-Path $dir $e.resumo
    if (-not (Test-Path $arq)) { Write-Host ("AVISO: arquivo nao encontrado: " + $e.resumo); continue }
    $j = Get-Content $arq -Raw | ConvertFrom-Json
    $s = $j.summary
    $linhas += [pscustomobject]@{
        rep = $e.rep; teste = $e.teste; nivel = $e.nivel; alvo = $e.alvo
        inicio_utc = $e.inicio_utc; fim_utc = $e.fim_utc; codigo_saida = $e.codigo_saida
        requisicoes = $s.requests.count; iteracoes = $s.iterations; descartadas = $s.droppedIterations
        taxa_falha = $s.failedRate; vus_max = $s.vusMax
        media_ms = $s.latencyMs.avg; mediana_ms = $s.latencyMs.med; p90_ms = $s.latencyMs.p90
        p95_ms = $s.latencyMs.p95; p99_ms = $s.latencyMs.p99; max_ms = $s.latencyMs.max
    }
}
if ($linhas.Count -eq 0) { Write-Host "Nenhuma execucao com resumo."; exit 1 }

$porExec = $linhas | ForEach-Object {
    [pscustomobject]@{
        rep = $_.rep; teste = $_.teste; nivel = $_.nivel; alvo = $_.alvo
        inicio_utc = $_.inicio_utc; fim_utc = $_.fim_utc; codigo_saida = $_.codigo_saida
        requisicoes = Fmt $_.requisicoes 0; iteracoes = Fmt $_.iteracoes 0; descartadas = Fmt $_.descartadas 0
        taxa_falha = Fmt $_.taxa_falha 6; vus_max = Fmt $_.vus_max 0
        media_ms = Fmt $_.media_ms 1; mediana_ms = Fmt $_.mediana_ms 1; p90_ms = Fmt $_.p90_ms 1
        p95_ms = Fmt $_.p95_ms 1; p99_ms = Fmt $_.p99_ms 1; max_ms = Fmt $_.max_ms 1
    }
}
$porExec | Export-Csv -Path (Join-Path $dir "consolidado-por-execucao.csv") -NoTypeInformation -Encoding ASCII

$ordemNivel = @{ smoke = 0; low = 1; medium = 2; high = 3 }
$grupos = $linhas | Group-Object teste, nivel, alvo
$tabela = foreach ($g in $grupos) {
    $x = $g.Group
    $desc = Stat $x "descartadas"; $med = Stat $x "mediana_ms"; $p95 = Stat $x "p95_ms"; $p99 = Stat $x "p99_ms"; $mx = Stat $x "max_ms"
    [pscustomobject]@{
        teste = $x[0].teste; nivel = $x[0].nivel; alvo = $x[0].alvo; repeticoes = $x.Count
        descartadas_media = Fmt $desc.Average 1
        mediana_media_ms = Fmt $med.Average 1
        p95_media_ms = Fmt $p95.Average 1; p95_min_ms = Fmt $p95.Minimum 1; p95_max_ms = Fmt $p95.Maximum 1
        p99_media_ms = Fmt $p99.Average 1
        max_maior_ms = Fmt $mx.Maximum 1
    }
}
$tabela = $tabela | Sort-Object teste, @{ Expression = { $ordemNivel[$_.nivel] } }, alvo
$tabela | Export-Csv -Path (Join-Path $dir "consolidado-por-grupo.csv") -NoTypeInformation -Encoding ASCII
$tabela | Format-Table -AutoSize
Write-Host ("Arquivos gravados em: " + $dir)
