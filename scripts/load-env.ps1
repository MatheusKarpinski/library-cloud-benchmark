<#
.SYNOPSIS
  Carrega as variáveis do arquivo .env na sessão ATUAL do PowerShell.
.DESCRIPTION
  A aplicação lê configurações de variáveis de ambiente, não do arquivo .env.
  Use com o ponto e o espaço no início (dot-source), para as variáveis valerem na sua sessão:
      . .\scripts\load-env.ps1
  As variáveis valem só nesta janela do terminal; nada é gravado no Windows.
#>
param([string]$Path = ".env")

if (-not (Test-Path $Path)) {
    Write-Error "Arquivo '$Path' não encontrado. Copie .env.example para .env e ajuste os valores."
    return
}

Get-Content $Path | ForEach-Object {
    $line = $_.Trim()
    if ($line -eq "" -or $line.StartsWith("#")) { return }
    $separator = $line.IndexOf("=")
    if ($separator -lt 1) { return }
    $name = $line.Substring(0, $separator).Trim()
    $value = $line.Substring($separator + 1).Trim()
    [Environment]::SetEnvironmentVariable($name, $value, "Process")
}

Write-Host "Variáveis de '$Path' carregadas nesta sessão do PowerShell."
