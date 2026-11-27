<#
.SYNOPSIS
  Mede o tempo da primeira requisicao ao Container App e a Function App depois de um periodo sem acesso
  (cold start), em varias rodadas, com o mesmo metodo para os dois.
  Status: ESCRITO (nao testado na entrega).

.DESCRIPTION
  Em cada rodada: espera -IdleMinutes sem nenhum acesso; confere pelas metricas se havia replica ou instancia
  nos ultimos minutos (so informativo, as metricas atrasam 1 a 2 minutos); envia -Requests requisicoes seguidas
  a cada servico (a primeira e a "fria", as seguintes sao "aquecidas") e cronometra cada uma.
  A ordem dos servicos alterna a cada rodada. O banco precisa estar ligado e o PC nao pode entrar em repouso.
  Resultados em results\azure\<Tag>\rodadas.csv.

.EXAMPLE
  .\scripts\cold-start.ps1 -Rounds 1 -IdleMinutes 1 -Tag cold-teste
  .\scripts\cold-start.ps1 -Rounds 5 -IdleMinutes 15 -Tag coldstart
#>
[CmdletBinding()]
param(
    [int]$Rounds = 5,
    [int]$IdleMinutes = 15,
    [int]$Requests = 3,
    [string]$Tag = "coldstart",
    [string]$Path = "/api/books?size=1"
)

$root = Split-Path -Parent $PSScriptRoot
Set-Location $root
$envFile = Join-Path $root ".env.azure"
if (-not (Test-Path $envFile)) { Write-Host "ERRO: .env.azure nao encontrado"; exit 1 }
$cfg = @{}
Get-Content $envFile | ForEach-Object { $p = $_ -split '=', 2; if ($p.Count -eq 2) { $cfg[$p[0]] = $p[1] } }
$rg = $cfg["AZ_RG"]; $fn = $cfg["AZ_FN"]; $pg = $cfg["AZ_PG_SERVER"]
if (-not $rg -or -not $fn -or -not $pg -or -not $cfg["AZ_CA_FQDN"]) { Write-Host "ERRO: variaveis ausentes em .env.azure"; exit 1 }

$estado = (az postgres flexible-server show --resource-group $rg --name $pg --query state -o tsv)
if ($estado -ne "Ready") { Write-Host "ERRO: o banco nao esta Ready (estado: $estado). Ligue o banco e tente de novo."; exit 1 }

$sub = (az account show --query id -o tsv)
$urls = [ordered]@{
    container = "https://" + $cfg["AZ_CA_FQDN"] + $Path
    functions = "https://" + $fn + ".azurewebsites.net" + $Path
}
$ids = @{
    container = "/subscriptions/$sub/resourceGroups/$rg/providers/Microsoft.App/containerApps/ca-library-api"
    functions = "/subscriptions/$sub/resourceGroups/$rg/providers/Microsoft.Web/sites/$fn"
}
$metrica = @{ container = "Replicas"; functions = "InstanceCount" }

$outDir = Join-Path $root ("results\azure\" + $Tag)
New-Item -ItemType Directory -Force -Path $outDir | Out-Null
$csv = Join-Path $outDir "rodadas.csv"
if (-not (Test-Path $csv)) { "rodada,alvo,requisicao,inicio_utc,tempo_ms,status,max_replicas_ou_instancias_ultimos_6min" | Set-Content $csv -Encoding ASCII }

function Get-MaxRecente([string]$id, [string]$nomeMetrica) {
    $errTmp = Join-Path $env:TEMP "cold-start.err"
    $a = (Get-Date).ToUniversalTime().AddMinutes(-6).ToString("yyyy-MM-ddTHH:mm:ssZ")
    $b = (Get-Date).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ssZ")
    $s = az monitor metrics list --resource $id --metric $nomeMetrica --aggregation Maximum --interval PT1M --start-time $a --end-time $b -o json 2> $errTmp
    if ($LASTEXITCODE -ne 0) { return "n/d" }
    try {
        $j = ($s | Out-String) | ConvertFrom-Json
        $ts = $j.value[0].timeseries
        if (-not $ts -or $ts.Count -eq 0) { return 0 }
        $max = 0
        foreach ($pt in $ts[0].data) { if ($null -ne $pt.maximum -and $pt.maximum -gt $max) { $max = $pt.maximum } }
        return $max
    } catch { return "n/d" }
}

function Invoke-Medido([string]$url) {
    $sw = [Diagnostics.Stopwatch]::StartNew()
    $status = 0
    try {
        $r = Invoke-WebRequest -Uri $url -UseBasicParsing -TimeoutSec 180
        $status = [int]$r.StatusCode
    } catch {
        if ($_.Exception.Response) { $status = [int]$_.Exception.Response.StatusCode } else { $status = -1 }
    }
    $sw.Stop()
    return @{ ms = [math]::Round($sw.Elapsed.TotalMilliseconds); status = $status }
}

Write-Host ("Cold start: {0} rodadas, {1} min de espera, {2} requisicoes por servico. Nao use este PC para acessar as APIs durante a espera." -f $Rounds, $IdleMinutes, $Requests)
for ($rodada = 1; $rodada -le $Rounds; $rodada++) {
    for ($m = $IdleMinutes; $m -gt 0; $m--) {
        Write-Host ("rodada {0}/{1}: aguardando sem acessar, faltam {2} min" -f $rodada, $Rounds, $m)
        Start-Sleep -Seconds 60
    }
    $ordem = @("container", "functions")
    if ($rodada % 2 -eq 0) { $ordem = @("functions", "container") }
    $pre = @{}
    foreach ($alvo in $ordem) { $pre[$alvo] = Get-MaxRecente $ids[$alvo] $metrica[$alvo] }
    foreach ($alvo in $ordem) {
        for ($k = 1; $k -le $Requests; $k++) {
            $ini = (Get-Date).ToUniversalTime()
            $res = Invoke-Medido $urls[$alvo]
            ("{0},{1},{2},{3},{4},{5},{6}" -f $rodada, $alvo, $k, $ini.ToString("yyyy-MM-ddTHH:mm:ssZ"), $res.ms, $res.status, $pre[$alvo]) | Add-Content $csv -Encoding ASCII
            Write-Host ("  {0,-9} req {1}: {2} ms (status {3}; replicas/instancias nos ultimos 6 min: {4})" -f $alvo, $k, $res.ms, $res.status, $pre[$alvo])
        }
    }
}
Write-Host ""
Write-Host "Concluido. Dados em: $csv"
