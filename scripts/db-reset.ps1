<#
.SYNOPSIS
  Recria o esquema (se necessário), ESVAZIA a tabela books e recarrega o conjunto inicial.
.DESCRIPTION
  Procedimento reproduzível de preparação/limpeza dos dados do banco LOCAL (container "postgres"
  do docker-compose.yml). Executa psql dentro do container, então não exige psql instalado.
  Requer o arquivo .env (o docker compose lê POSTGRES_PASSWORD dele).
.PARAMETER Rows
  Quantidade de livros do conjunto inicial (padrão 10000).
.EXAMPLE
  .\scripts\db-reset.ps1 -Rows 10000
#>
param([int]$Rows = 10000)

$ErrorActionPreference = "Stop"
Set-Location (Join-Path $PSScriptRoot "..")

$user = if ($env:POSTGRES_USER) { $env:POSTGRES_USER } else { "library" }
$database = if ($env:POSTGRES_DB) { $env:POSTGRES_DB } else { "library" }

function Invoke-Step {
    param([string]$Description, [scriptblock]$Command)
    Write-Host "-> $Description"
    & $Command
    if ($LASTEXITCODE -ne 0) { throw "Falhou: $Description (código $LASTEXITCODE)" }
}

# Copiar os arquivos para dentro do container evita problemas de codificação do PowerShell.
Invoke-Step "copiando schema.sql" { docker compose cp db/schema.sql postgres:/tmp/schema.sql }
Invoke-Step "copiando seed.sql"   { docker compose cp db/seed.sql postgres:/tmp/seed.sql }
Invoke-Step "criando esquema"     { docker compose exec -T postgres psql -U $user -d $database -v ON_ERROR_STOP=1 -q -f /tmp/schema.sql }
Invoke-Step "esvaziando books"    { docker compose exec -T postgres psql -U $user -d $database -v ON_ERROR_STOP=1 -q -c "TRUNCATE TABLE books;" }
Invoke-Step "carregando $Rows livros" { docker compose exec -T postgres psql -U $user -d $database -v ON_ERROR_STOP=1 -q -v rows=$Rows -f /tmp/seed.sql }

Write-Host "-> conferindo contagem de linhas"
docker compose exec -T postgres psql -U $user -d $database -t -A -c "SELECT count(*) FROM books;"
