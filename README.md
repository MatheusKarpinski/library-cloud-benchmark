# library-cloud-benchmark

API REST de biblioteca usada como parte prática de um TCC de Engenharia de Software:

> **"Análise dos impactos da escolha entre arquiteturas Serverless e baseadas em Containers
> na escalabilidade, desempenho e custos de aplicações web."**

A mesma lógica de negócio será executada em **Azure Container Apps** (Spring Boot em container)
e em **Azure Functions** (Flex Consumption), e submetida aos mesmos testes de carga (k6).

## Status do projeto

| Etapa | Descrição | Status |
|---|---|---|
| 1 | Planejamento | Concluída |
| 2 | Ambiente + núcleo (`core`) | **Testada** (35 testes) |
| 3 | PostgreSQL local, JDBC, API Spring Boot | **Testada** (validação manual em 02/11/2026) |
| 4 | Testes de integração (PostgreSQL real e API por HTTP) | **Testada** (57 unitários e 85 com integração passaram; informado pelo autor em 12/11/2026) |
| 5 | API no Azure Functions (execução local) e comparação com o container | **Testada** localmente em 14/11/2026 (104 testes com integração; smoke idêntico nos 42 cenários; ver seção "Etapa 5") |
| 6 | Imagem Docker da API em container (`Dockerfile`, `.dockerignore`) e teste local da imagem | **Testada** localmente em 15/11/2026 (imagem de 444 MB em disco; smoke idêntico ao do JAR; ver seção "Etapa 6") |
| 7 | Scripts de carga com k6 (leitura e escrita), níveis configuráveis e exportação de resultados | **Testada** localmente em 20/11/2026 (smokes de leitura e escrita nos alvos `image` e `functions`, perfil `ramp` e alvo parado; k6 v2.4.0; informado pelo autor em `docs/testing.md`) |
| 8 | Monitoramento com Azure Monitor | Não iniciada (plano em `docs/observability.md`) |
| 9 | Implantação no Azure: Container Apps e Functions Flex com o mesmo PostgreSQL gerenciado | **Implantada e testada** em 25/11/2026 (smoke de 42 cenários idêntico ao local nos dois; k6 smoke de leitura e escrita nos dois; ver `docs/deployment.md` e `docs/testing.md`) |
| 10–11 | Experimentos, documentação final | Não iniciadas |

Documentação completa e atualizada a cada etapa em [`docs/`](docs/README.md).

## Estrutura atual

```
library-cloud-benchmark/
├── pom.xml                 POM pai (versões, plugins e BOM do Spring Boot)
├── core/                   Regras de negócio e casos de uso (Java puro)
├── api-contract/           DTOs e tradução exceção -> HTTP (Java puro)
├── infrastructure/         JDBC + PostgreSQL + pool HikariCP
├── adapter-container/      API Spring Boot (Azure Container Apps)
├── adapter-functions/      API Azure Functions (Flex Consumption) — Etapa 5
├── db/                     schema.sql e seed.sql (carga determinística)
├── k6/                     Scripts de carga: read.js, write.js e lib/ — Etapa 7
├── scripts/                load-env.ps1, db-reset.ps1, new-local-settings.ps1, api-smoke.ps1, run-image.ps1, run-k6.ps1
├── results/                Resumos das execuções do k6 (criada pelo run-k6.ps1) — Etapa 7
├── Dockerfile              Imagem da API em container — Etapa 6
├── .dockerignore           Contexto de build sem segredos — Etapa 6
├── docker-compose.yml      PostgreSQL local
├── .env.example            Modelo de variáveis (copiar para .env)
└── docs/                   Arquitetura, ADRs, metodologia, diário, openapi.yaml, notas do TCC
```

O módulo `adapter-functions` foi adicionado na Etapa 5 (validado localmente em 14/11/2026, ver a seção "Etapa 5").

## Pré-requisitos (Etapa 2)

| Ferramenta | Versão | Observação |
|---|---|---|
| JDK | **21** (Temurin recomendado) | A versão 21 é a usada em todo o projeto |
| Apache Maven | **3.9.x** (a série estável mais recente) | Não usar a série 4.x (prévia) |
| Git | qualquer versão recente | Controle de versão |
| VS Code + *Extension Pack for Java* | atual | Inclui Maven e execução de testes |

| Docker Desktop | atual (validado: Docker 29.8.2, Compose v5.5.1) | Banco PostgreSQL local (Etapa 3) |
| Azure Functions Core Tools | 4.x (instalada: 4.16.0) | Execução local do Functions (Etapa 5) |
| k6 | 2.x (instalada: v2.4.0, em 20/11/2026; registrar `k6 version` a cada bateria) | Testes de carga (Etapa 7) |

### Instalação no Windows (PowerShell)

```powershell
# 1) JDK 21 (Temurin). Se o winget não encontrar o pacote, baixe em https://adoptium.net
winget install --id EclipseAdoptium.Temurin.21.JDK -e

# 2) Git
winget install --id Git.Git -e
```

**Maven:** baixe o arquivo `apache-maven-3.9.x-bin.zip` em <https://maven.apache.org/download.cgi>,
extraia em `C:\tools` (ficará, por exemplo, `C:\tools\apache-maven-3.9.11`) e execute
(ajuste o nome da pasta para a versão baixada):

```powershell
[Environment]::SetEnvironmentVariable("MAVEN_HOME", "C:\tools\apache-maven-3.9.11", "User")
$p = [Environment]::GetEnvironmentVariable("Path", "User")
[Environment]::SetEnvironmentVariable("Path", "$p;C:\tools\apache-maven-3.9.11\bin", "User")
```

**Feche e reabra o VS Code** (as variáveis só valem em terminais novos).

### Verificar a instalação

```powershell
java -version      # deve mostrar 21.x
javac -version     # deve mostrar javac 21.x
mvn -v             # deve mostrar Apache Maven 3.9.x e "Java version: 21.x"
git --version
echo $env:JAVA_HOME   # deve apontar para a pasta do JDK 21
```

Se `JAVA_HOME` estiver vazio:

```powershell
Get-ChildItem "C:\Program Files\Eclipse Adoptium"      # descubra o nome exato da pasta
[Environment]::SetEnvironmentVariable("JAVA_HOME", "C:\Program Files\Eclipse Adoptium\<pasta-do-jdk-21>", "User")
```

## Executar os testes

Na raiz do projeto (pasta que contém o `pom.xml` pai), no terminal do VS Code:

```powershell
mvn test
```

Resultado esperado ao final (soma de todos os módulos; a **primeira** execução baixa muitas
dependências e pode levar alguns minutos):

```
Reactor Summary ...
library-core ................ SUCCESS
library-api-contract ........ SUCCESS
library-infrastructure ...... SUCCESS
library-adapter-container ... SUCCESS
library-adapter-functions ... SUCCESS
BUILD SUCCESS
```

Os testes unitários somam **76** (core 35, api-contract 11, infrastructure 8, adapter-container 3, adapter-functions 19; eram 57 antes da Etapa 5). Cada módulo imprime sua linha `Tests run: N`. Os testes de integração (Etapa 4) ficam de fora deste comando.

Linhas `INFO: Livro criado: id=...` no meio da saída são normais (logs do serviço durante os testes).

Outros comandos úteis:

```powershell
mvn -pl core test            # só o módulo core
mvn -pl core -Dtest=BookServiceTest test   # só uma classe de teste
mvn clean test               # limpa e recompila tudo
```

No VS Code também é possível rodar pelo painel **Testing** (ícone de frasco) após abrir a pasta.

## Etapa 3 — Banco local e API em container

### 1. Preparar variáveis e subir o PostgreSQL

Se o PowerShell bloquear scripts `.ps1`, libere **somente nesta janela**:

```powershell
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
```

```powershell
Copy-Item .env.example .env        # cria o arquivo local de configuração
notepad .env                       # troque a senha em POSTGRES_PASSWORD e DB_PASSWORD (iguais entre si)
docker compose up -d               # sobe o PostgreSQL 17 em segundo plano
docker compose ps                  # a coluna STATUS deve mostrar "healthy" (pode levar ~10 s)
```

### 2. Criar a tabela e carregar os dados (10000 livros determinísticos)

```powershell
.\scripts\db-reset.ps1 -Rows 10000
```

Esperado: passos `->` sem erro e, no final, a linha `10000`. Pode rodar de novo quando quiser: ele esvazia e recarrega.

### 3. Compilar e iniciar a API

```powershell
. .\scripts\load-env.ps1          # (ponto + espaço no início) carrega o .env NESTA janela
mvn -pl adapter-container -am package
java -jar adapter-container\target\library-api.jar
```

Esperado: logs do Spring terminando com algo como `Started LibraryApplication` e `Tomcat started on port 8080`. **Deixe esta janela aberta** e use **outro** terminal para os testes abaixo.

### 4. Experimentar os endpoints (outro terminal)

```powershell
Invoke-RestMethod http://localhost:8080/health
Invoke-RestMethod http://localhost:8080/health/ready
Invoke-RestMethod "http://localhost:8080/api/books?size=3"
Invoke-RestMethod "http://localhost:8080/api/books/search?title=dom%20casmurro&size=2"
```

Esperado, com os 10000 livros da carga:

| Chamada | Resultado esperado |
|---|---|
| `/health` | `status: UP` |
| `/health/ready` | `status: UP` e `database: UP` |
| `/api/books?size=3` | 3 itens, `page: 0`, `totalElements: 10000`, `totalPages: 3334`; o primeiro título começa com `Capitaes Areia` |
| pesquisa `dom casmurro` | `totalElements: 100` |

Cadastrar, consultar, atualizar e excluir:

```powershell
$body = @{ titulo = "Livro de Teste"; autor = "Autora Teste"; isbn = "978-85-359-0277-5"; anoPublicacao = 2020; categoria = "Teste" } | ConvertTo-Json
$created = Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/books -ContentType "application/json" -Body $body
$created                                              # id (UUID), isbn sem hífens: 9788535902775, disponivel: True
$id = $created.id
Invoke-RestMethod http://localhost:8080/api/books/$id # mesmo livro

$update = @{ titulo = "Livro de Teste (revisado)"; autor = "Autora Teste"; isbn = "9788535902775"; anoPublicacao = 2021; categoria = "Teste"; disponivel = $false } | ConvertTo-Json
Invoke-RestMethod -Method Put -Uri http://localhost:8080/api/books/$id -ContentType "application/json" -Body $update

Invoke-RestMethod -Method Delete -Uri http://localhost:8080/api/books/$id   # sem saída = 204
```

Erros (o `Invoke-RestMethod` lança exceção em 4xx/5xx; este bloco mostra o status e o corpo). Dica: o PowerShell pode esconder propriedades ao exibir vários objetos seguidos; use `| ConvertTo-Json` para ver tudo.

```powershell
function Show-Error($script) {
    try { & $script } catch {
        "HTTP " + $_.Exception.Response.StatusCode.value__
        if ($_.ErrorDetails -and $_.ErrorDetails.Message) {
            $_.ErrorDetails.Message
        } else {
            # Windows PowerShell 5.1 nem sempre preenche ErrorDetails: lê o corpo direto da resposta.
            $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
            $reader.ReadToEnd()
        }
    }
}
Show-Error { Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/books -ContentType "application/json" -Body '{"titulo":""}' }   # 400 VALIDATION_ERROR (titulo, autor, categoria, anoPublicacao)
Show-Error { Invoke-RestMethod http://localhost:8080/api/books/abc }                                                                      # 400 (id não é UUID)
Show-Error { Invoke-RestMethod http://localhost:8080/api/books/00000000-0000-0000-0000-000000000000 }                                     # 404 NOT_FOUND
Show-Error { Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/books -ContentType "application/json" -Body '{' }              # 400 MALFORMED_REQUEST
Show-Error { Invoke-RestMethod "http://localhost:8080/api/books?page=abc" }                                                               # 400 (page com tipo inválido)
Show-Error { Invoke-RestMethod "http://localhost:8080/api/books?size=1000" }                                                              # 400 (size acima de 100)
```

Conflito de ISBN: rode o `Post` de cadastro **duas vezes** com o mesmo `$body`; a segunda deve responder **409 CONFLICT**
(use `Show-Error { Invoke-RestMethod -Method Post ... -Body $body }`). Depois exclua o livro criado.

Banco fora do ar (testa o tratamento de falhas e a recuperação):

```powershell
docker compose stop postgres
Invoke-RestMethod http://localhost:8080/health                            # continua UP (não usa o banco)
Show-Error { Invoke-RestMethod http://localhost:8080/health/ready }       # 503, database: DOWN
Show-Error { Invoke-RestMethod "http://localhost:8080/api/books?size=1" } # ~5 s depois: 503 SERVICE_UNAVAILABLE (sem detalhes técnicos)
docker compose start postgres                                             # aguarde ~10 s
Invoke-RestMethod http://localhost:8080/health/ready                      # volta a UP
```

### 5. Encerrar

`Ctrl+C` na janela da API. Para parar o banco: `docker compose stop` (os dados permanecem no volume).
Para apagar banco **e** dados: `docker compose down -v`.

### Checklist da Etapa 3

- [ ] `mvn test` na raiz termina com `BUILD SUCCESS` nos 4 módulos
- [ ] `docker compose ps` mostra o PostgreSQL `healthy`
- [ ] `db-reset.ps1` termina com `10000`
- [ ] A API inicia e `/health/ready` mostra `database: UP`
- [ ] Listagem e pesquisa batem com a tabela de valores esperados
- [ ] Cadastro (201), consulta, atualização, exclusão (204) funcionam
- [ ] Os erros respondem com os status esperados (400, 404, 409)
- [ ] Com o banco parado: `/health` UP, `/health/ready` 503 e a API volta ao normal após `docker compose start`

### Problemas comuns

| Sintoma | Causa provável | O que fazer |
|---|---|---|
| `...ps1 cannot be loaded because running scripts is disabled` | Política de execução do PowerShell | `Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass` |
| `required variable POSTGRES_PASSWORD is missing a value` | Falta o arquivo `.env` | `Copy-Item .env.example .env` e edite |
| `port is already allocated` (5432 ou 8080) | Outro programa usa a porta | Pare o outro programa ou mude a porta (`SERVER_PORT` para a API) |
| `Variáveis de ambiente obrigatórias ausentes: DB_URL...` ao iniciar a API | Não carregou o `.env` na janela | `. .\scripts\load-env.ps1` e inicie de novo |
| `FATAL: password authentication failed` | `DB_PASSWORD` diferente de `POSTGRES_PASSWORD`, ou volume antigo com outra senha | Igualar as senhas; se o volume é antigo: `docker compose down -v` e recriar |
| Erros ao baixar dependências | Rede/proxy | Repetir; enviar a mensagem completa |

Qualquer erro novo deve ser registrado em `docs/troubleshooting.md`.

## Etapa 4 — Testes automatizados

Há dois níveis de teste:

```powershell
mvn test                    # unitários: rápido, sem Docker (76 testes; eram 57 na Etapa 4)
mvn test -Pintegration      # unitários + integração: exige o Docker Desktop aberto (104 testes; eram 85 na Etapa 4)
```

Os testes de integração (marcados com `@Tag("integration")`) sobem um **PostgreSQL 17 em container**
(Testcontainers, com o `db/schema.sql` real) e a **API real por HTTP**:

| Classe | Módulo | Testes | O que cobre |
|---|---|---|---|
| `JdbcBookRepositoryIntegrationTest` | infrastructure | 14 | Contrato do repositório no banco real: gravação, ISBN único, ordenação, paginação, busca, curingas, exclusão, registro inconsistente, banco inacessível |
| `BookApiIntegrationTest` | adapter-container | 11 | Todos os endpoints por HTTP: ciclo de vida, 400, 404, 409, paginação, pesquisa, saúde |
| `BookApiDatabaseDownIntegrationTest` | adapter-container | 3 | Aplicação com banco inexistente: `/health` 200, `/health/ready` 503, dados 503 sem detalhes técnicos |
| `OpenApiDocumentTest` (unitário) | adapter-container | 3 | `docs/openapi.yaml` cobre as rotas, métodos e códigos de erro implementados |

Resultado esperado de `mvn test -Pintegration` (a primeira execução baixa imagens e dependências e pode levar vários minutos):

```
core ................ Tests run: 35
api-contract ........ Tests run: 11
infrastructure ...... Tests run: 22
adapter-container ... Tests run: 17
adapter-functions ... Tests run: 19
BUILD SUCCESS   (total: 104 testes, 0 falhas)
```

Observações:

- O Docker Desktop precisa estar aberto. Sem ele, os testes de integração falham com a mensagem `Could not find a valid Docker environment`.
- Não é necessário parar o PostgreSQL do `docker compose` nem a API: os testes usam um banco próprio em porta aleatória e sobem a aplicação numa porta livre.
- Um módulo isolado: `mvn -pl infrastructure -am test -Pintegration`.

## Etapa 5 — API no Azure Functions (execução local)

O módulo `adapter-functions` expõe as **mesmas rotas e o mesmo contrato** do adaptador de container e chama o mesmo `BookService`, o mesmo `api-contract` e a mesma `infrastructure` (ADR-020 e ADR-021). Status: **TESTADO localmente** em 14/11/2026 (resultados informados pelo autor em `docs/testing.md`). Diferença conhecida entre os adaptadores: o `Content-Type` de respostas sem corpo (204 do DELETE), que o `api-smoke.ps1` registra como `(sem corpo)` — ver `docs/troubleshooting.md`, T-010.

| Classe | Papel |
|---|---|
| `BookFunctions` | As 7 funções HTTP (`health`, `ready`, `listBooks`, `getBook` — que também atende `/api/books/search` —, `createBook`, `updateBook`, `deleteBook`); só extrai dados da requisição e converte a resposta |
| `BookApiHandler` | Lógica HTTP sem dependência do runtime do Functions (ordem das verificações igual à do Spring) |
| `FunctionComponents` | Monta `BookService` e pool uma vez por processo, na primeira requisição |
| `JsonCodec`, `JacksonJsonCodec` | JSON com Jackson 3 |

### 1. Testes automatizados

```powershell
mvn test                    # esperado: 76 testes (adapter-functions: 19), BUILD SUCCESS
mvn test -Pintegration      # esperado: 104 testes; exige o Docker Desktop aberto
```

Para ver só o resumo por módulo (e registrar as contagens em `docs/testing.md`):

```powershell
mvn test | Select-String "Tests run: \d+, Failures: \d+, Errors: \d+, Skipped: \d+$|Reactor Summary|SUCCESS|FAIL|BUILD"
```

### 2. Gerar a configuração local e empacotar

O Functions lê `local.settings.json` (não o `.env`). O script abaixo o cria a partir do `.env` (UTF-8 sem BOM; ignorado pelo Git; não exibe valores). **Rode antes do `package`.**

```powershell
.\scripts\new-local-settings.ps1
mvn -pl adapter-functions -am clean package -DskipTests
```

Use `clean`: sem ele, a pasta de uma função removida do código continua em `target\azure-functions` e o `func start` falha (T-008/T-009 em `docs/troubleshooting.md`). Antes de empacotar de novo, pare o `func start` e deixe o terminal **fora** da pasta empacotada (o Windows não apaga uma pasta em uso).

Esperado: `BUILD SUCCESS` e a pasta `adapter-functions\target\azure-functions\library-cloud-benchmark-fn-local` com `host.json`, `local.settings.json`, o JAR, `lib\` e uma pasta por função.

```powershell
Get-ChildItem adapter-functions\target\azure-functions\library-cloud-benchmark-fn-local
```

### 3. Iniciar o Functions local (deixe a janela aberta)

```powershell
cd adapter-functions\target\azure-functions\library-cloud-benchmark-fn-local
func start
```

(O `cd` acima parte da raiz do projeto. Se o terminal já estiver dentro de outra pasta, volte à raiz antes.)

Esperado: a lista das 7 funções com URLs do tipo `http://localhost:7071/health` e `http://localhost:7071/api/books` (sem `/api/api`). O banco local precisa estar de pé (`docker compose ps`).

### 4. Conferir à mão (outro terminal, na raiz do projeto)

```powershell
Invoke-RestMethod http://localhost:7071/health
Invoke-RestMethod http://localhost:7071/health/ready
Invoke-RestMethod "http://localhost:7071/api/books?size=3"
Invoke-RestMethod "http://localhost:7071/api/books/search?title=dom%20casmurro&size=2"
```

Esperado (banco com a carga de 10000 livros): os mesmos valores da tabela da Etapa 3 (`totalElements: 10000`, `totalPages: 3334`, pesquisa com 100 resultados).

### 5. Comparar com o container

O container e o Functions podem ficar ligados ao mesmo tempo (portas 8080 e 7071). Antes de **cada** registro, restaure o banco.

```powershell
# janela A: container (se ainda não estiver rodando)
. .\scripts\load-env.ps1
java -jar adapter-container\target\library-api.jar
```

```powershell
# janela B: registros
.\scripts\db-reset.ps1 -Rows 10000
.\scripts\api-smoke.ps1 -BaseUrl http://localhost:8080 -OutFile smoke-container.txt
.\scripts\db-reset.ps1 -Rows 10000
.\scripts\api-smoke.ps1 -BaseUrl http://localhost:7071 -OutFile smoke-functions.txt
Compare-Object (Get-Content -Encoding UTF8 smoke-container.txt) (Get-Content -Encoding UTF8 smoke-functions.txt)
```

Sem saída do `Compare-Object` = registros idênticos. Qualquer linha listada é uma diferença a analisar: registre em `docs/troubleshooting.md` (defeito) ou em `docs/architecture.md` (diferença inerente à plataforma). Os arquivos `smoke-*.txt` são evidência: guarde-os.

Rota inexistente e método não permitido ficam **fora** do registro (o formato dessas respostas é do framework de cada adaptador).

### Checklist da Etapa 5

- [ ] `mvn test` termina com `BUILD SUCCESS` e 76 testes (adapter-functions: 19)
- [ ] `mvn test -Pintegration` termina com `BUILD SUCCESS` e 104 testes
- [ ] `mvn -pl adapter-functions -am package` termina com `BUILD SUCCESS` e gera a pasta `target\azure-functions\library-cloud-benchmark-fn-local`
- [ ] A pasta tem `host.json` e `local.settings.json`
- [ ] `func start` carrega o Java 21 e lista as 7 funções (anote a versão do Java e do Core Tools exibidas)
- [ ] `/health`, `/health/ready`, listagem e pesquisa respondem como na tabela esperada
- [ ] `GET /api/books/search?...` responde com a página de resultados (a pesquisa é decidida no código de `getBook`; ver T-008)
- [ ] `api-smoke.ps1` termina nos dois endereços sem erro
- [ ] `Compare-Object` sem saída (ou diferenças analisadas e registradas)
- [ ] Banco parado (`docker compose stop postgres`): `/health` 200, `/health/ready` 503, dados 503 sem detalhes; após `docker compose start postgres`, volta ao normal

### Problemas comuns

Ver também `docs/troubleshooting.md` (seção "Problemas conhecidos").

| Sintoma | Causa provável | O que fazer |
|---|---|---|
| Todas as rotas respondem `500 INTERNAL_ERROR` e o log cita variáveis ausentes | `local.settings.json` sem `DB_*` na pasta empacotada | `.\scripts\new-local-settings.ps1` (grava também na pasta empacotada, se ela existir) e reinicie o `func start` |
| `func` não é reconhecido | Core Tools fora do `PATH` ou terminal antigo | Feche e reabra o terminal; `func --version` |
| `func start` mostra 0 funções ou erro do worker Java | Versão do Java/`JAVA_HOME` | Envie a saída completa; confira `java -version` e `echo $env:JAVA_HOME` |
| Porta 7071 em uso | Outra instância do `func start` | Encerre-a com `Ctrl+C` |

## Etapa 6 — Imagem Docker da API em container

Status: **TESTADO localmente** em 15/11/2026 (resultados informados pelo autor em `docs/testing.md`; o passo opcional 7 não foi executado). Decisão: ADR-022 (`docs/architecture-decisions.md`).

| Arquivo | Papel |
|---|---|
| `Dockerfile` | Multi-estágio: `maven:3.9-eclipse-temurin-21` compila só os módulos da API em container; `eclipse-temurin:21-jre-noble` (JRE 21, Ubuntu 24.04) executa; usuário sem privilégios; porta 8080; `JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=75.0` como padrão |
| `.dockerignore` | Deixa de fora `.env`, `local.settings.json`, `target/`, `.git`, `docs/` e `scripts/` do contexto de build |
| `scripts/run-image.ps1` | Executa a imagem na rede do `docker-compose.yml`, ligada ao banco local (host `postgres`), publicando a porta em `127.0.0.1:8081` |

A imagem não contém segredos: a configuração (`DB_URL`, `DB_USER`, `DB_PASSWORD`, `DB_POOL_*`, `SERVER_PORT`) vem só de variáveis de ambiente.

### 1. Construir a imagem (raiz do projeto)

```powershell
docker build -t library-api:local .
```

Esperado: termina com `naming to docker.io/library/library-api:local`. A primeira vez baixa as imagens base e as dependências do Maven (alguns minutos); as seguintes usam o cache.

### 2. Inspecionar a imagem (bloco inteiro)

```powershell
docker images library-api:local
docker run --rm --entrypoint java library-api:local -version
docker run --rm --entrypoint id library-api:local
docker buildx imagetools inspect eclipse-temurin:21-jre-noble | Select-String "^Digest:"
```

Esperado: a imagem listada com o tamanho; Java `21.x` Temurin (a linha `Picked up JAVA_TOOL_OPTIONS` também aparece); `uid=10001(app) gid=10001(app)`; o *digest* da imagem base. **Anote** o tamanho, a versão do Java e o *digest* (a tag `21-jre-noble` muda com o tempo). O `docker build` não deixa a imagem base salva localmente; por isso o *digest* é consultado no registro com `buildx imagetools`, e não com `docker image inspect` (T-011).

### 3. Executar a imagem (segundo terminal; deixe aberto)

Pare antes o JAR (`Ctrl+C`), se ele ainda estiver rodando na 8080. O banco do `docker-compose.yml` precisa estar de pé (`docker compose ps`).

```powershell
.\scripts\run-image.ps1
```

Esperado: `Picked up JAVA_TOOL_OPTIONS: -XX:MaxRAMPercentage=75.0` e os logs do Spring terminando com `Started LibraryApplication` e `Tomcat started on port 8080`.

### 4. Conferir à mão (terminal principal)

```powershell
Invoke-RestMethod http://localhost:8081/health
Invoke-RestMethod http://localhost:8081/health/ready
Invoke-RestMethod "http://localhost:8081/api/books?size=3"
```

Esperado (banco com a carga de 10000 livros): os mesmos valores da Etapa 3.

### 5. Comparar com o JAR (bloco inteiro)

Compara o registro da imagem com o `smoke-container.txt` já existente (gerado com o JAR na Etapa 5).

```powershell
.\scripts\db-reset.ps1 -Rows 10000
.\scripts\api-smoke.ps1 -BaseUrl http://localhost:8081 -OutFile smoke-image.txt
Compare-Object (Get-Content -Encoding UTF8 smoke-container.txt) (Get-Content -Encoding UTF8 smoke-image.txt)
```

Sem saída do `Compare-Object` = registros idênticos.

### 6. Encerramento gracioso (terminal principal; observe o segundo terminal)

```powershell
docker stop library-api-local
```

Esperado: o contêiner encerra em poucos segundos e o log mostra o encerramento do Spring (*graceful shutdown*). Anote as últimas linhas do log.

### 7. Opcional — limites de recursos (segundo terminal; depois, terminal principal)

```powershell
.\scripts\run-image.ps1 -Memory 512m -Cpus 1
```

```powershell
docker exec library-api-local java -XX:+PrintFlagsFinal -version | Select-String "MaxHeapSize|MaxRAMPercentage"
```

Esperado: `MaxRAMPercentage` 75 e `MaxHeapSize` por volta de 402653184 (75% de 512 MiB). Encerre depois com `docker stop library-api-local`.

### Checklist da Etapa 6

- [ ] `docker build` termina sem erro
- [ ] Tamanho da imagem, versão do Java e *digest* da imagem base anotados
- [ ] A imagem executa como `app` (uid 10001), não como root
- [ ] `Started LibraryApplication` e `Tomcat started on port 8080` nos logs
- [ ] `/health`, `/health/ready` e a listagem respondem como na Etapa 3
- [ ] `Compare-Object` entre `smoke-container.txt` e `smoke-image.txt` sem saída
- [ ] `docker stop` encerra a imagem em poucos segundos
- [ ] (opcional) `MaxHeapSize` coerente com o limite de memória

### Problemas comuns

Ver também `docs/troubleshooting.md` (seção "Problemas conhecidos").

| Sintoma | Causa provável | O que fazer |
|---|---|---|
| `docker build` falha ao baixar dependências | Sem rede ou instabilidade ao baixar do Maven Central | Repita o comando (o cache preserva o que já foi baixado) |
| `run-image.ps1`: "Container 'library-postgres' nao esta em execucao" | Banco parado | `docker compose up -d` e espere o `healthy` |
| `run-image.ps1`: "Imagem ... nao encontrada" | A imagem ainda não foi construída | Passo 1 |
| Erro `container name "/library-api-local" is already in use` | Execução anterior ainda existe | `docker rm -f library-api-local` |
| `port is already allocated` (8081) | Outro programa usa a porta | `.\scripts\run-image.ps1 -Port 8082` (e use a nova porta nos comandos) |
| A API sobe, mas `/health/ready` responde 503 | Rede ou credenciais do banco | Confira `DB_USER` e `DB_PASSWORD` no `.env` e os logs do contêiner |

## Etapa 7 — Scripts de carga com k6

Status: **TESTADO localmente** em 20/11/2026 (smokes; resultados informados pelo autor em `docs/testing.md`). Não executados: alvo `container` (JAR) e níveis `low`, `medium` e `high`. Decisão: ADR-023 (`docs/architecture-decisions.md`).

**Escopo:** esta etapa entrega scripts prontos, configuráveis e validados. Ela **não** produz medições de desempenho utilizáveis no TCC: rodar o k6 na mesma máquina da API faz os dois disputarem CPU e memória. As medições finais (Etapa 10) exigem níveis calibrados e um gerador de carga fora da máquina da API.

| Arquivo | Papel |
|---|---|
| `k6/read.js` | Carga de **leitura**: mistura de listagem paginada (50%), consulta por id (30%) e pesquisa por título (20%) |
| `k6/write.js` | Carga de **escrita**: cada iteração faz POST, PUT e DELETE de um livro novo (o banco volta ao tamanho original) |
| `k6/lib/common.js` | Configuração, cenários, limites, geração de dados e resumo (sem dependência de módulos do k6) |
| `k6/lib/api.js` | Chamadas HTTP e verificação inicial da API |
| `scripts/run-k6.ps1` | Executa um teste, define as variáveis de ambiente e grava `results\<execução>.summary.json` |

### Instalar o k6

```powershell
winget install k6 --source winget
```

Feche e reabra o VS Code por completo (para atualizar o `PATH`) e confira:

```powershell
k6 version
```

Esperado: `k6 v2.x`. **Anote a versão** (cada execução precisa registrar a versão usada).

### Executar

Suba antes o alvo: `container` = JAR na porta 8080; `functions` = `func start` na 7071; `image` = `.\scripts\run-image.ps1` na 8081. O banco deve estar de pé com a carga padrão.

```powershell
.\scripts\run-k6.ps1 -Target image -Test read -Level smoke
.\scripts\run-k6.ps1 -Target image -Test write -Level smoke -ResetDb
```

| Parâmetro | Significado |
|---|---|
| `-Target` | `container`, `functions` ou `image` (define a URL padrão e a etiqueta `target` nas métricas) |
| `-Test` | `read` ou `write` |
| `-Level` | `smoke` (padrão), `low`, `medium` ou `high` |
| `-Profile` | `constant` (taxa fixa, padrão) ou `ramp` (degraus 25%, 50%, 75%, 100% da taxa) |
| `-Rate`, `-Duration`, `-MaxVus` | Sobrescrevem o nível (taxa de iterações por segundo, ex.: `2m`, máximo de VUs) |
| `-BaseUrl` | Outro endereço (ex.: Azure, nas etapas finais) |
| `-ResetDb` | Restaura a carga padrão (10000 livros) antes de executar |
| `-Raw` | Grava todas as medições em `results\raw\` (arquivo grande; não versionar) |

Níveis **provisórios** (servem para validar os scripts na máquina local; os níveis finais serão definidos na metodologia):

| Nível | Taxa (iterações/s) | Duração | Máximo de VUs |
|---|---|---|---|
| `smoke` | 5 | 30 s | 20 |
| `low` | 20 | 2 min | 100 |
| `medium` | 100 | 3 min | 300 |
| `high` | 300 | 3 min | 1000 |

**Modelo de carga:** taxa de chegada (modelo aberto): o k6 inicia a taxa pedida de iterações por segundo, independentemente do tempo de resposta. Na leitura, 1 iteração = 1 requisição; na escrita, 1 iteração = 3 requisições (POST, PUT, DELETE).

Variáveis de ambiente opcionais (definidas antes de rodar, ex.: `$env:MIX_LIST = "100"`): `MIX_LIST`, `MIX_GET`, `MIX_SEARCH` (pesos da leitura, padrão 50/30/20), `P95_MS` (limite de p95, padrão 1000), `MAX_ERROR_RATE` (padrão 0.01), `PAGE_SIZE` (padrão 20), `RAMP_STEPS` (padrão `0.25,0.5,0.75,1`), `ID_SAMPLE_PAGES` (padrão 10), `PRE_VUS`, `RUN_SEED`.

**Resultados:** o terminal mostra um resumo curto e `results\<aaaammdd-hhmmss>-<teste>-<alvo>-<nível>.summary.json` guarda `meta` (parâmetros da execução), `summary` (números principais) e `data` (objeto completo do k6). Os resumos são pequenos e servem de evidência; para não versionar as medições completas, execute uma vez: `Add-Content .gitignore "results/raw/"`.

**Código de saída:** 0 = limites respeitados; 99 = algum limite (erro acima de 1%, p95 acima de 1000 ms, checks abaixo de 99% ou iterações descartadas) foi violado; o teste **não** é interrompido por isso. 107 = exceção no script (por exemplo, API parada no `setup`); nesse caso **nenhum** resumo é gravado.

### Checklist da Etapa 7

- [x] `k6 version` mostra uma versão 2.x (anotada): **v2.4.0** (commit dd9395906d, go1.26.11, windows/amd64)
- [x] Leitura `smoke` no alvo `image`: 150 iterações, 150 requisições na fase de carga, falhas 0%, checks 100%, 0 descartadas (execução `20261120-181002`)
- [x] Escrita `smoke` no alvo `image` com `-ResetDb`: 151 iterações e 453 requisições (o k6 pode iniciar ±1), falhas 0%; o log do `teardown` mostra diferença 0 e a listagem volta a `totalElements` 10000
- [x] Os mesmos dois testes no alvo `functions` (leitura: 151 iterações e requisições; escrita: 151 iterações e 453 requisições; diferença 0; `totalElements` 10000)
- [x] `-Profile ramp` (leitura, `smoke`) executa sem erro e o arquivo termina em `-ramp.summary.json` (102 iterações)
- [x] Com a API parada, o teste falha de imediato com a mensagem "API indisponivel" (código de saída 107) e não grava resumo
- [x] O arquivo `.summary.json` abre com `ConvertFrom-Json` e tem `meta`, `summary` e `data`
- [ ] (opcional) Nível `low` na leitura: não executado

### Problemas comuns

Ver também `docs/troubleshooting.md` (seção "Problemas conhecidos").

| Sintoma | Causa provável | O que fazer |
|---|---|---|
| `k6` não é reconhecido | Instalado, mas o VS Code ainda usa o `PATH` antigo | Feche e reabra o VS Code por completo |
| Erro "API indisponivel em ..." | O alvo não está rodando | Suba o JAR, o `func start` ou a imagem, conforme o `-Target` |
| Erro "O banco esta vazio" | Carga ausente | `.\scripts\db-reset.ps1 -Rows 10000` ou use `-ResetDb` |
| Falhas `409` no teste de escrita | ISBN de uma execução interrompida ainda no banco | Use `-ResetDb` |
| Código de saída 99 | Algum limite foi violado | Veja a seção de limites no resumo; não é erro de script |
| Aviso de VUs insuficientes ou `descartadas` > 0 | A API (ou o gerador) não acompanha a taxa pedida | Aumente `-MaxVus` ou reduza `-Rate`; registre como resultado |
| Mensagem "nenhuma iteracao executada" e nenhum arquivo em `results\` | O `setup` falhou (por exemplo, API parada) e o k6 saiu com código 107 | Suba o alvo e repita; execuções sem iterações não geram resumo (T-014) |

## Etapa 9 — Implantação no Azure

O procedimento executado (comandos, recursos, custos e plano de remoção) está em [`docs/deployment.md`](docs/deployment.md); os resultados, em `docs/testing.md`; as ocorrências, em `docs/troubleshooting.md` (T-015 a T-023).

- A senha do banco e os nomes dos recursos ficam em `.env.azure`, que está no `.gitignore`. **Nunca** versionar esse arquivo.
- Todo terminal novo precisa recarregar as variáveis de `.env.azure` antes de usar `az`.
- Ao fim de cada sessão, parar o banco; ao fim do projeto, exportar as evidências e apagar o grupo de recursos.
- Os endereços das APIs no Azure são públicos e sem autenticação durante o experimento: não divulgá-los.

## Segurança

Nunca versione senhas, chaves ou strings de conexão. O `.gitignore` já exclui `.env` e
`local.settings.json`. Configurações reais virão de variáveis de ambiente (Etapa 3).
