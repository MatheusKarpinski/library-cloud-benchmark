<#
.SYNOPSIS
  Gera adapter-functions\local.settings.json a partir do arquivo .env (execucao LOCAL do Azure Functions).
.DESCRIPTION
  O Azure Functions Core Tools le as configuracoes locais de local.settings.json (nao do .env).
  Este script copia para ele as variaveis DB_* do .env e define FUNCTIONS_WORKER_RUNTIME=java.
  O arquivo e gravado em UTF-8 SEM BOM e e ignorado pelo Git (.gitignore). Nenhum valor e exibido.
  Rode ANTES de "mvn -pl adapter-functions -am package" (o plugin copia o arquivo para a pasta
  executavel). Se a pasta target\azure-functions\<AppName> ja existir, o arquivo tambem e gravado nela.
.PARAMETER EnvPath
  Caminho do .env (padrao: .env na raiz do projeto).
.PARAMETER AppName
  Nome da pasta gerada pelo plugin em target\azure-functions (padrao: library-cloud-benchmark-fn-local).
.EXAMPLE
  .\scripts\new-local-settings.ps1
#>
param(
    [string]$EnvPath = ".env",
    [string]$AppName = "library-cloud-benchmark-fn-local"
)

$ErrorActionPreference = "Stop"
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

$required = @("DB_URL", "DB_USER", "DB_PASSWORD")
$optional = @("DB_POOL_MAX", "DB_POOL_MIN_IDLE", "DB_CONNECTION_TIMEOUT_MS")

$missing = @($required | Where-Object { -not $fromEnv.ContainsKey($_) -or $fromEnv[$_] -eq "" })
if ($missing.Count -gt 0) {
    throw ("Variaveis ausentes em '$EnvPath': " + ($missing -join ", "))
}

$values = [ordered]@{ FUNCTIONS_WORKER_RUNTIME = "java" }
foreach ($name in ($required + $optional)) {
    if ($fromEnv.ContainsKey($name) -and $fromEnv[$name] -ne "") {
        $values[$name] = $fromEnv[$name]
    }
}

$settings = [ordered]@{ IsEncrypted = $false; Values = $values }
$json = $settings | ConvertTo-Json -Depth 5
$utf8NoBom = New-Object System.Text.UTF8Encoding($false)

$targets = @((Join-Path $PWD.Path "adapter-functions\local.settings.json"))
$packagedDir = Join-Path $PWD.Path ("adapter-functions\target\azure-functions\" + $AppName)
if (Test-Path $packagedDir) {
    $targets += (Join-Path $packagedDir "local.settings.json")
}

foreach ($target in $targets) {
    [System.IO.File]::WriteAllText($target, $json, $utf8NoBom)
    Write-Host "Gravado: $target"
}
Write-Host ("Variaveis incluidas (so os nomes): " + (($values.Keys) -join ", "))
