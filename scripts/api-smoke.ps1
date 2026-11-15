<#
.SYNOPSIS
  Roda uma sequencia fixa de requisicoes contra a API e grava um REGISTRO NORMALIZADO das respostas,
  para comparar a API em container (porta 8080) com a API no Azure Functions (porta 7071).
.DESCRIPTION
  Para cada cenario o registro tem: nome, metodo e caminho, status HTTP, tipo de conteudo (sem o
  parametro charset, em minusculas; "(sem corpo)" quando a resposta nao tem corpo), cabecalho Location
  (se houver) e corpo. UUIDs sao trocados por
  <uuid>, porque sao gerados a cada execucao. O corpo e lido como UTF-8.
  Fora do registro, de proposito: rota inexistente e metodo nao permitido (o formato dessas respostas
  e do framework e difere entre os adaptadores; ver docs/architecture.md).

  ANTES de CADA execucao, restaure o banco para o mesmo estado:
      .\scripts\db-reset.ps1 -Rows 10000
  O script cria e exclui os proprios livros de teste; os cenarios de leitura vem primeiro.
.PARAMETER BaseUrl
  Ex.: http://localhost:8080  (container)  ou  http://localhost:7071  (Functions).
.PARAMETER OutFile
  Arquivo de saida (UTF-8 sem BOM), ex.: smoke-container.txt
.EXAMPLE
  .\scripts\api-smoke.ps1 -BaseUrl http://localhost:8080 -OutFile smoke-container.txt
.EXAMPLE
  Compare-Object (Get-Content -Encoding UTF8 smoke-container.txt) (Get-Content -Encoding UTF8 smoke-functions.txt)
  # sem saida = registros identicos
#>
param(
    [Parameter(Mandatory = $true)][string]$BaseUrl,
    [Parameter(Mandatory = $true)][string]$OutFile
)

$ErrorActionPreference = "Stop"
$BaseUrl = $BaseUrl.TrimEnd("/")
if (-not [System.IO.Path]::IsPathRooted($OutFile)) { $OutFile = Join-Path $PWD.Path $OutFile }

$uuidPattern = '[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}'
$unknownId = "00000000-0000-0000-0000-000000000000"
$script:Record = New-Object System.Collections.Generic.List[string]
$script:Count = 0

function Send-Request {
    param([string]$Method, [string]$Path, [string]$Body)

    $request = [System.Net.HttpWebRequest][System.Net.WebRequest]::Create($BaseUrl + $Path)
    $request.Method = $Method
    $request.Accept = "application/json"
    $request.Timeout = 60000
    $request.AllowAutoRedirect = $false
    if ($PSBoundParameters.ContainsKey("Body")) {
        $bytes = [System.Text.Encoding]::UTF8.GetBytes($Body)
        $request.ContentType = "application/json"
        $request.ContentLength = $bytes.Length
        if ($bytes.Length -gt 0) {
            $stream = $request.GetRequestStream()
            $stream.Write($bytes, 0, $bytes.Length)
            $stream.Close()
        }
    }

    try {
        $response = $request.GetResponse()
    } catch [System.Net.WebException] {
        $response = $_.Exception.Response
        if ($null -eq $response) { throw "Sem resposta de $Method $Path : $($_.Exception.Message)" }
    }

    $reader = New-Object System.IO.StreamReader($response.GetResponseStream(), [System.Text.Encoding]::UTF8)
    $text = $reader.ReadToEnd()
    $reader.Close()
    $result = [pscustomobject]@{
        Status      = [int]$response.StatusCode
        ContentType = [string]$response.ContentType
        Location    = [string]$response.Headers["Location"]
        Text        = $text
    }
    $response.Close()
    return $result
}

function Invoke-Scenario {
    param([string]$Name, [string]$Method, [string]$Path, [string]$Body)

    if ($PSBoundParameters.ContainsKey("Body")) {
        $r = Send-Request -Method $Method -Path $Path -Body $Body
    } else {
        $r = Send-Request -Method $Method -Path $Path
    }

    # Resposta sem corpo (ex.: 204): o Content-Type nao tem significado e o runtime do Functions envia
    # text/plain onde o container nao envia nada (docs/troubleshooting.md, T-010). Registra "(sem corpo)".
    $contentType = if ($r.Text -eq "") { "(sem corpo)" } else { ($r.ContentType -split ";")[0].Trim().ToLowerInvariant() }
    $bodyText = if ($r.Text -eq "") { "(vazio)" } else { [regex]::Replace($r.Text, $uuidPattern, "<uuid>") }
    $script:Record.Add("### $Name")
    $script:Record.Add("$Method " + [regex]::Replace($Path, $uuidPattern, "<uuid>"))
    $script:Record.Add("status: $($r.Status)")
    $script:Record.Add("content-type: $contentType")
    if ($r.Location -ne "") { $script:Record.Add("location: " + [regex]::Replace($r.Location, $uuidPattern, "<uuid>")) }
    $script:Record.Add("body: $bodyText")
    $script:Record.Add("")
    $script:Count++
    Write-Host ("{0,2}. {1} -> {2}" -f $script:Count, $Name, $r.Status)
    return $r
}

function Get-Id([string]$json) {
    $m = [regex]::Match($json, '"id":"([^"]+)"')
    if (-not $m.Success) { throw "Resposta sem id: $json" }
    return $m.Groups[1].Value
}

# Textos com acento usam escapes JSON (\u00f3 etc.) para este arquivo ser ASCII e nao depender da codificacao.
$bookA = '{"titulo":"Mem\u00f3rias P\u00f3stumas de Br\u00e1s Cubas","autor":"Machado de Assis","isbn":"978-85-359-0277-5","anoPublicacao":1881,"categoria":"Romance"}'
$bookB = '{"titulo":"Livro B","autor":"Autor B","isbn":"9788535914849","anoPublicacao":2000,"categoria":"Teste"}'
$bookDuplicate = '{"titulo":"Livro Repetido","autor":"Autor","isbn":"978-85-359-0277-5","anoPublicacao":2000,"categoria":"Teste"}'
$bookExtra = '{"titulo":"Livro Extra","autor":"Autor","anoPublicacao":2001,"categoria":"Teste","disponivel":false,"campoDesconhecido":123}'

Write-Host "Base: $BaseUrl"

# --- Saude ---
[void](Invoke-Scenario "saude (liveness)" "GET" "/health")
[void](Invoke-Scenario "saude (readiness)" "GET" "/health/ready")

# --- Leituras (dependem da carga de 10000 livros) ---
[void](Invoke-Scenario "listagem size=3" "GET" "/api/books?size=3")
[void](Invoke-Scenario "listagem page=1 size=2" "GET" "/api/books?page=1&size=2")
[void](Invoke-Scenario "listagem alem da ultima pagina" "GET" "/api/books?page=99999&size=5")
[void](Invoke-Scenario "listagem com page e size vazios (padroes)" "GET" "/api/books?page=&size=")
[void](Invoke-Scenario "pesquisa dom casmurro" "GET" "/api/books/search?title=dom%20casmurro&size=2")
[void](Invoke-Scenario "pesquisa sem diferenciar maiusculas" "GET" "/api/books/search?title=DOM%20CASMURRO&size=1")
[void](Invoke-Scenario "pesquisa sem resultado" "GET" "/api/books/search?title=nada-existe-xyz")

# --- Parametros invalidos ---
[void](Invoke-Scenario "page nao numerico" "GET" "/api/books?page=abc")
[void](Invoke-Scenario "page negativo" "GET" "/api/books?page=-1")
[void](Invoke-Scenario "size zero" "GET" "/api/books?size=0")
[void](Invoke-Scenario "size acima do maximo" "GET" "/api/books?size=1000")
[void](Invoke-Scenario "page e size invalidos juntos" "GET" "/api/books?page=-1&size=1000")
[void](Invoke-Scenario "page nao numerico e size nao numerico" "GET" "/api/books?page=abc&size=xyz")
[void](Invoke-Scenario "pesquisa com termo em branco" "GET" "/api/books/search?title=%20%20")
[void](Invoke-Scenario "pesquisa sem termo" "GET" "/api/books/search")
[void](Invoke-Scenario "pesquisa com page nao numerico" "GET" "/api/books/search?title=dom&page=abc")

# --- Corpo invalido na criacao ---
[void](Invoke-Scenario "POST corpo vazio de campos" "POST" "/api/books" "{}")
[void](Invoke-Scenario "POST JSON malformado" "POST" "/api/books" "{")
[void](Invoke-Scenario "POST tipo incorreto" "POST" "/api/books" '{"anoPublicacao":"abc"}')
[void](Invoke-Scenario "POST sem corpo" "POST" "/api/books" "")
[void](Invoke-Scenario "POST corpo null" "POST" "/api/books" "null")

# --- Ciclo de vida ---
$created = Invoke-Scenario "POST livro valido (com acentos e ISBN com hifens)" "POST" "/api/books" $bookA
$idA = Get-Id $created.Text
[void](Invoke-Scenario "GET do livro criado" "GET" "/api/books/$idA")
[void](Invoke-Scenario "POST com ISBN repetido" "POST" "/api/books" $bookDuplicate)
$createdB = Invoke-Scenario "POST segundo livro" "POST" "/api/books" $bookB
$idB = Get-Id $createdB.Text
$createdExtra = Invoke-Scenario "POST com campo desconhecido e disponivel=false" "POST" "/api/books" $bookExtra
$idExtra = Get-Id $createdExtra.Text

# --- Ids ---
[void](Invoke-Scenario "GET id mal formado" "GET" "/api/books/abc")
[void](Invoke-Scenario "GET id inexistente" "GET" "/api/books/$unknownId")

# --- Atualizacao (ordem das verificacoes) ---
[void](Invoke-Scenario "PUT JSON malformado em id inexistente (400 antes do 404)" "PUT" "/api/books/$unknownId" "{")
[void](Invoke-Scenario "PUT corpo incompleto em id inexistente (404 antes da validacao)" "PUT" "/api/books/$unknownId" '{"titulo":"X"}')
[void](Invoke-Scenario "PUT id mal formado" "PUT" "/api/books/abc" "{}")
[void](Invoke-Scenario "PUT corpo invalido em id existente" "PUT" "/api/books/$idA" "{}")
[void](Invoke-Scenario "PUT com ISBN de outro livro" "PUT" "/api/books/$idB" $bookA)
$updateA = '{"titulo":"Livro revisado","autor":"Autora","isbn":"9788535902775","anoPublicacao":2021,"categoria":"Ensaio","disponivel":false}'
[void](Invoke-Scenario "PUT valido" "PUT" "/api/books/$idA" $updateA)

# --- Exclusao ---
[void](Invoke-Scenario "DELETE livro A" "DELETE" "/api/books/$idA")
[void](Invoke-Scenario "GET apos DELETE" "GET" "/api/books/$idA")
[void](Invoke-Scenario "DELETE repetido" "DELETE" "/api/books/$idA")
[void](Invoke-Scenario "DELETE id mal formado" "DELETE" "/api/books/abc")
[void](Invoke-Scenario "DELETE livro B" "DELETE" "/api/books/$idB")
[void](Invoke-Scenario "DELETE livro extra" "DELETE" "/api/books/$idExtra")

$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
[System.IO.File]::WriteAllLines($OutFile, $script:Record.ToArray(), $utf8NoBom)
Write-Host ""
Write-Host "$($script:Count) cenarios registrados em: $OutFile"
