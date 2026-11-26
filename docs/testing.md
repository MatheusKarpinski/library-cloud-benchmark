# Testes

## Estratégia

| Nível | Ferramenta | Onde | Status |
|---|---|---|---|
| Unitário (domínio e casos de uso) | JUnit 5 + AssertJ, repositório em memória **apenas de teste** | `core/src/test` | TESTADO |
| Integração de persistência (PostgreSQL real) | Testcontainers 2.x (exige Docker Desktop) | `infrastructure` | TESTADO (Etapa 4, informado pelo autor em 12/11/2026) |
| Integração dos endpoints | Aplicação real + cliente HTTP do JDK + PostgreSQL em container | `adapter-container` | TESTADO (Etapa 4, informado pelo autor em 12/11/2026) |
| Unitário do adaptador Functions (lógica HTTP e JSON) | JUnit 5 + AssertJ, repositório em memória **apenas de teste** | `adapter-functions/src/test` | TESTADO (Etapa 5, 14/11/2026): 19 testes, 0 falhas |
| Equivalência entre adaptadores (container × Functions) | `scripts/api-smoke.ps1` + `Compare-Object` dos registros | raiz | TESTADO (Etapa 5, 14/11/2026): respostas iguais nos cenários, salvo o `Content-Type` do 204 (T-010) |
| Imagem Docker da API (execução, equivalência com o JAR, encerramento) | `docker build`, `scripts/run-image.ps1`, `scripts/api-smoke.ps1` + `Compare-Object` | raiz | TESTADO (Etapa 6, 15/11/2026): imagem idêntica ao JAR nos 42 cenários |
| Scripts de carga (k6): execução, resultados e limpeza do banco | `scripts/run-k6.ps1` nos alvos `image` e `functions` | `k6/`, `scripts/` | TESTADO (Etapa 7, 20/11/2026): smokes de leitura e escrita, perfil `ramp` e alvo parado; ver seção "Etapa 7" |
| Carga (medição de desempenho) | k6 (ferramenta externa) | `k6/` | PLANEJADO (Etapa 10) |

## Etapa 2 — 35 testes no `core`

- `BookTest` (12): criação válida, normalização, ISBN-10 com X, ISBN opcional, padrão de
  `disponivel`, violações agregadas, limites de tamanho e de ano, `withDetails`, igualdade por id.
- `PageRequestTest` (4): padrões, offset, limites, valores inválidos.
- `BookServiceTest` (19): cadastro (válido, inválido, ISBN duplicado, sem ISBN), consulta
  (existente, inexistente, id mal formado), listagem paginada/ordenada/vazia, pesquisa (com
  e sem resultado, termo vazio), atualização (ok, mesmo ISBN, ISBN de outro, inexistente,
  inválida), exclusão (ok, inexistente) e propagação de falhas de persistência.

## Resultados observados

| Execução | Onde | Resultado |
|---|---|---|
| `mvn test` com JUnit/AssertJ reais | Máquina do autor, 11/10/2026 (JDK Oracle 21.0.9, Maven 3.9.11, Windows) | 35 testes, 0 falhas, 0 erros, 0 ignorados, BUILD SUCCESS (3,8 s) |


## Como executar localmente

Ver o `README.md` da raiz (seção "Executar os testes").


## Etapa 3 — testes adicionados (19 testes unitários)

| Módulo | Classe | Testes | O que cobre |
|---|---|---|---|
| `api-contract` | `ErrorMapperTest` | 6 | 400, 404, 409, 503, 500 (sem vazar detalhes), corpo malformado |
| `api-contract` | `ResponseMappingTest` | 5 | Conversão de `Book` → resposta, paginação, `BookRequest` → `BookData`, readiness |
| `infrastructure` | `DatabaseConfigTest` | 6 | Padrões, variáveis opcionais, obrigatórias ausentes, valores inválidos, senha oculta |
| `infrastructure` | `LikePatternEscapeTest` | 2 | Escape de `%`, `_` e `\` na pesquisa |

Total esperado de `mvn test` na raiz: **54** (35 do `core` + 19 novos).

**Não há testes de integração com PostgreSQL ainda** — entram na Etapa 4 (Testcontainers). Até lá, a validação do banco e dos endpoints é **manual**, pelos comandos do `README.md`.

### Resultados observados (Etapa 3)

| Execução | Onde | Resultado |
|---|---|---|
| `mvn test` real na raiz | Máquina do autor, 02/11/2026 | BUILD SUCCESS nos 4 módulos (contagem por módulo não registrada) |
| Execução real do adaptador Spring Boot, do SQL e dos scripts | Máquina do autor, 02/11/2026 | Validada manualmente (ver abaixo). |

## Etapa 3 — validação funcional manual (02/11/2026, máquina do autor)

Tipo de evidência: **observação funcional** (não é medição de desempenho). Saídas do terminal informadas pelo autor.

| Bloco | O que foi verificado | Resultado |
|---|---|---|
| Preparação | `docker compose up -d`, `db-reset.ps1 -Rows 10000` | Banco `healthy`; contagem final 10000 |
| A — leituras | `/health`, `/health/ready`, listagem (`size=3`), pesquisa `dom casmurro` | Conforme esperado: 10000 elementos, 3334 páginas, primeiro título `Capitaes Areia vol. 1045`, pesquisa com 100 resultados; ordenação e ISBN nulo corretos |
| B — ciclo de vida | POST (201 + `Location`), GET, PUT, ISBN repetido (409 `CONFLICT`), DELETE, GET após exclusão (404 `NOT_FOUND`) | Conforme esperado, após correção do campo `up` (ver `troubleshooting.md`) |
| C — erros | 12 casos de validação/erro (400, 404) | Conforme esperado: códigos, campos e mensagens; rota inexistente com formato do Spring (diferença documentada) |
| D — banco fora do ar | `docker compose stop/start postgres` | `/health` 200; `/health/ready` 503 `DOWN`; dados 503 `SERVICE_UNAVAILABLE`; log com `ERROR` e aviso de banco indisponível; recuperação após religar, com dados preservados |

**Não medido:** tempo de resposta durante a falha do banco (esperado ≈ `DB_CONNECTION_TIMEOUT_MS`), tempo de recuperação do pool e qualquer métrica de desempenho. **Ainda sem testes automatizados contra PostgreSQL** (Etapa 4).

## Etapa 4 — testes de integração (TESTADO; resultado informado pelo autor)

### Organização

- **Dois níveis** (ADR-019): `mvn test` roda só os unitários (57 testes, sem Docker); `mvn test -Pintegration` roda também os testes marcados com `@Tag("integration")` (mais 28, total 85), que exigem Docker.
- Os testes de integração usam um **PostgreSQL 17 em container** (mesma imagem do `docker-compose.yml`) com o **mesmo `db/schema.sql`** da aplicação. A carga `db/seed.sql` não é usada nos testes (usa comandos do `psql`); ela foi validada manualmente na Etapa 3.
- Os testes da API sobem a **aplicação real** (Spring Boot + Tomcat) numa porta livre e falam com ela por **HTTP** (cliente do JDK), como o k6 fará. Não usam MockMvc.

### Casos

| Classe | Testes | Cobertura |
|---|---|---|
| `JdbcBookRepositoryIntegrationTest` | 14 | gravar e recuperar todos os campos; atualizar sem duplicar; id inexistente; busca por ISBN; ISBN repetido (inserção e atualização); vários livros sem ISBN; ordenação por `lower(titulo)` e id com paginação e total; busca sem diferenciar maiúsculas; curingas `%`, `_` e `\` como texto; exclusão verdadeira/falsa; registro que viola as regras atuais; banco inacessível; health check |
| `BookApiIntegrationTest` | 11 | `/health`; `/health/ready`; ciclo completo (201 + `Location`, GET, PUT, 204, 404); ISBN repetido (409) na criação e na atualização; violações agregadas (400); JSON malformado/tipo incorreto; id inválido (400) e inexistente (404, inclusive PUT antes de validar o corpo); paginação inválida; rota inexistente (404); listagem ordenada e paginada com totais; pesquisa e validação do termo |
| `BookApiDatabaseDownIntegrationTest` | 3 | com banco inexistente: `/health` 200; `/health/ready` 503 `DOWN`; dados 503 `SERVICE_UNAVAILABLE` sem `jdbc`, host, usuário, senha ou nome de exceção no corpo |
| `OpenApiDocumentTest` (unitário) | 3 | `openapi.yaml` contém as rotas, os métodos e todos os códigos de erro produzidos por `ErrorMapper` |

### Resultados observados (Etapa 4)

| Execução | Onde | Resultado |
|---|---|---|
| `mvn test` (57 unitários) | Máquina do autor, informado em 12/11/2026 | Passou (57 testes). A saída completa não foi anexada: contagem por módulo e tempos **não registrados** |
| `mvn test -Pintegration` (85 testes) | Máquina do autor, Docker Desktop aberto, informado em 12/11/2026 | Passou (85 testes). Mesma observação |
| Contagem por módulo (relatórios do Surefire da execução com `-Pintegration` de 14/11/2026, já com a Etapa 5) | Máquina do autor | core 35, api-contract 11, infrastructure 22, adapter-container 17 (soma 85, igual ao esperado da Etapa 4); falhas, erros e ignorados 0 |

### Limitações

- A ordenação `lower(titulo)` depende da **collation** do banco. O repositório em memória dos testes unitários usa a ordem de pontos de código do Java; as duas coincidem para os dados dos testes (letras ASCII), mas não são equivalentes em geral. A collation do PostgreSQL no Azure deve ser conferida antes dos experimentos.
- Os testes de API verificam o JSON por trechos de texto (o formato compacto do Spring), não por esquema.
- O arquivo `openapi.yaml` é verificado só quanto a rotas, métodos e códigos de erro; os esquemas dos objetos não são validados automaticamente.
- O ciclo de vida e a falha de banco são cobertos; **desempenho e concorrência** não são objeto desta etapa.


## Etapa 5 — adaptador Azure Functions (TESTADO localmente; resultados informados pelo autor em 14/11/2026)

### Testes unitários novos (19), sem Docker e sem o runtime do Functions

| Classe | Testes | Cobertura |
|---|---|---|
| `BookApiHandlerTest` | 14 | `health`; `ready` (banco disponível e indisponível); criação (201, `Location`, ISBN normalizado, `disponivel` padrão); violações agregadas (400); corpo ausente, em branco, `null`, `[]`, malformado ou com tipo incorreto (400 `MALFORMED_REQUEST`); ISBN repetido (409) na criação e na atualização; consulta; id inválido (400) e inexistente (404); atualização; ordem das verificações no PUT; exclusão (204 sem corpo, depois 404); listagem ordenada e paginada, com `page`/`size` vazios usando o padrão; parâmetros inválidos (400, só o primeiro erro é informado), pesquisa (sem diferenciar maiúsculas, termo vazio ou ausente, ordem entre `page` e termo); falha de persistência (503) e erro inesperado (500) sem detalhes técnicos |
| `JacksonJsonCodecTest` | 5 | JSON compacto de saúde, readiness (sem campo extra `up`), erro, livro (ordem dos campos, `isbn` nulo, acentos sem escape) e página; leitura do corpo (campos desconhecidos ignorados, ausentes viram `null`, `null` literal, erros viram `JsonCodecException`) |

Total esperado: `mvn test` na raiz = **76** (35 + 11 + 8 + 3 + 19); `mvn test -Pintegration` = **104**.
Os repositórios `InMemoryBookRepository` e `FailingBookRepository` são cópias, em `adapter-functions/src/test`, das versões usadas nos testes do núcleo (os módulos de teste não são compartilhados entre si).

### Equivalência entre adaptadores (`scripts/api-smoke.ps1`)

O script executa uma sequência fixa de 42 requisições e grava, para cada uma, o status, o `Content-Type` (sem `charset`; `(sem corpo)` quando não há corpo), o `Location` e o corpo, com os UUIDs trocados por `<uuid>`. O banco deve ser restaurado (`db-reset.ps1 -Rows 10000`) antes de cada execução. A comparação dos registros do container (porta 8080) e do Functions (porta 7071) com `Compare-Object` sem saída indica respostas idênticas **nesses cenários**.

Fora do registro, por serem de responsabilidade do framework: rota inexistente e método não permitido. Não coberto: formatos numéricos incomuns em `page`/`size`, cabeçalhos além de `Content-Type` e `Location`, concorrência e desempenho.

### Resultados observados (Etapa 5, informados pelo autor em 14/11/2026)

| Execução | Onde | Resultado |
|---|---|---|
| `mvn test` (76 esperados) | Máquina do autor | `BUILD SUCCESS` nos 5 módulos (informado). A contagem exata dos unitários não foi registrada à parte; os números por módulo abaixo vêm da execução com integração |
| `mvn test -Pintegration` (104 esperados) | Máquina do autor, Docker Desktop aberto | `BUILD SUCCESS` nos 5 módulos. Relatórios do Surefire (14/11/2026): core 35, api-contract 11, infrastructure 22, adapter-container 17 e adapter-functions 19 = **104 testes; 0 falhas, 0 erros, 0 ignorados** |
| `mvn -pl adapter-functions -am clean package` | Máquina do autor | Gerou a pasta executável; `func start` a carregou e o smoke rodou contra a porta 7071 (primeira tentativa, sem `clean`, falhou: T-009) |
| `func start` | Máquina do autor | Core Tools 4.16.0, runtime 4.1054.250.26428; worker Java iniciado; `java -version` 21.0.9 (Oracle) e `JAVA_HOME` = `C:\Program Files\Java\jdk-21` |
| `GET /api/books?size=3` | Máquina do autor | 200; `totalElements` 10000, `totalPages` 3334, `size` 3, primeiro título `Capitaes Areia vol. 1045` |
| `GET /health/ready` após religar o banco | Máquina do autor | `status: UP`, `database: UP` |
| Banco parado (`docker compose stop postgres`) | Máquina do autor | `/health` `UP`; `/health/ready` 503; `GET /api/books?size=1` 503 |
| `api-smoke.ps1` nos dois endereços | Máquina do autor | Registros de 255 linhas cada |
| `Compare-Object` dos registros (1ª execução) | Máquina do autor | Só linhas `content-type`: nos 3 `DELETE` com 204, o Functions responde `text/plain` e o container não envia `Content-Type` (T-010). Status e corpos dos 42 cenários, inclusive pesquisa, erros e ordem do PUT, não apareceram como diferentes |
| `api-smoke.ps1` repetido com o script ajustado (registra `(sem corpo)` no 204) e `Compare-Object` | Máquina do autor, 14/11/2026 | 42 cenários nos dois endereços; **`Compare-Object` sem saída: registros idênticos** |

Pendências de validação local: nenhuma. A repetição dos registros com o script ajustado está registrada na linha acima.

### Riscos da Etapa 5 — situação

| Risco | Situação em 14/11/2026 |
|---|---|
| Java 21 no Core Tools local (Windows) | Funcionou: worker Java iniciado com JDK 21.0.9 (`JAVA_HOME` do autor) |
| `package` gerar a pasta esperada e copiar `host.json` e `local.settings.json` | Funcionou (com `clean`; sem ele, pasta antiga de função removida permanece: T-009) |
| Funções com a mesma rota e métodos diferentes (`api/books`; `api/books/{id}`) | Funcionou nos cenários do smoke (POST/GET em `/api/books`; GET/PUT/DELETE em `/api/books/{id}`) |
| Precedência da rota literal `api/books/search` sobre `api/books/{id}` | **Falhou** (T-008); a pesquisa passou a ser decidida no código de `getBook`; confirmada pelo smoke |
| Jackson 3 com a biblioteca do Functions 3.2.4 | Funcionou: JSON dos cenários do smoke igual ao do container |
| Decodificação de parâmetros de consulta (`%20`) e leitura do corpo como texto | Funcionou: cenários de pesquisa e de POST/PUT iguais nos dois registros |


## Etapa 6 — imagem Docker da API (TESTADO localmente; resultados informados pelo autor em 15/11/2026)

### Verificações previstas

| # | Verificação | Como | Resultado esperado |
|---|---|---|---|
| 1 | Construção da imagem | `docker build -t library-api:local .` | Termina sem erro |
| 2 | Conteúdo da imagem | `docker images`; `docker run --rm --entrypoint java library-api:local -version`; `... --entrypoint id ...`; `docker buildx imagetools inspect eclipse-temurin:21-jre-noble | Select-String "^Digest:"` | Tamanho anotado; Java 21 Temurin; `uid=10001(app)`; *digest* da base anotado |
| 3 | Execução ligada ao banco | `.\scripts\run-image.ps1` | `Started LibraryApplication`, `Tomcat started on port 8080`, `Picked up JAVA_TOOL_OPTIONS: -XX:MaxRAMPercentage=75.0` |
| 4 | Endpoints básicos | `/health`, `/health/ready`, listagem na porta 8081 | Mesmos valores da Etapa 3 (`totalElements` 10000, `totalPages` 3334) |
| 5 | Equivalência com o JAR | `api-smoke.ps1` na porta 8081 e `Compare-Object` com `smoke-container.txt` | Sem saída (42 cenários) |
| 6 | Encerramento | `docker stop library-api-local` | Encerra em poucos segundos; log de *graceful shutdown* |
| 7 | Limites (opcional) | `run-image.ps1 -Memory 512m -Cpus 1` e `docker exec ... java -XX:+PrintFlagsFinal -version` | `MaxHeapSize` por volta de 402653184 |

### Resultados observados (Etapa 6)

| Execução | Onde | Resultado |
|---|---|---|
| 1. `docker build -t library-api:local .` | Máquina do autor, Docker Desktop | Concluído; imagem `library-api:local` (ID `3d9f362ee5f0`) |
| 2. Conteúdo da imagem | Máquina do autor | 444 MB em disco e 120 MB de conteúdo (colunas do Docker Desktop); `java -version`: OpenJDK 21.0.13 (2026-10-20 LTS), Temurin-21.0.13+9; `id`: `uid=10001(app) gid=10001(app)`; `/app` contém só `library-api.jar` (21593322 bytes, dono `app`); nenhuma variável `DB_*` no ambiente da imagem (contagem 0); digest da base `eclipse-temurin:21-jre-noble` consultado depois do build: `sha256:0d57a8e90c2bb844c5a8e1b2b71e11eff06dad7edcfd803cd21ed4ae203b2515` (a tag é flutuante; o digest pode diferir do usado no build se a tag mudou entre os dois momentos) |
| 3. Execução ligada ao banco | Máquina do autor | Logs: Spring Boot v4.1.1, Tomcat 11.0.24, Java 21.0.13, PID 1 executado por `app`; `Picked up JAVA_TOOL_OPTIONS: -XX:MaxRAMPercentage=75.0`; HikariCP `Start completed` sem conexão na criação; `Tomcat started on port 8080`; `Started LibraryApplication in 1.317 seconds` (1ª execução) e `1.621 seconds` (2ª). Valores de uma execução cada, em máquina sem controle de carga: **não são medição de desempenho** |
| 4. Endpoints básicos (porta 8081) | Máquina do autor | Saída manual não anexada; coberto pelos cenários 1 a 3 do smoke (saúde, readiness, listagem), idênticos aos do JAR |
| 5. Equivalência com o JAR | Máquina do autor | `smoke-image.txt` com 255 linhas e 42 cenários, como `smoke-container.txt`; `Compare-Object` **sem saída** |
| 6. Encerramento | Máquina do autor | `docker stop`: 0,4996669 s (`Measure-Command`); logs: `Commencing graceful shutdown`, `Graceful shutdown complete` e encerramento do pool, em poucos milissegundos |
| 7. Limites de recursos (opcional) | — | **Não executado** |

Não coberto nesta etapa: desempenho e consumo de recursos sob carga (Etapas 7 e 10), execução com a imagem no Azure (Etapa 9) e varredura de vulnerabilidades da imagem.


## Etapa 7 — scripts de carga com k6 (TESTADO localmente em smokes, 20/11/2026)

**Escopo:** validar que os scripts funcionam e produzem os arquivos esperados. As execuções locais **não** são medição de desempenho (gerador e API na mesma máquina, níveis provisórios).

### Verificações

| # | Verificação | Como | Resultado esperado |
|---|---|---|---|
| 1 | k6 instalado | `k6 version` | Versão 2.x anotada |
| 2 | Leitura `smoke`, alvo `image` | `run-k6.ps1 -Target image -Test read -Level smoke` | 150 iterações e 150 requisições na fase de carga (o k6 pode iniciar ±1); falhas 0%; checks 100%; 0 descartadas; arquivo em `results\` |
| 3 | Escrita `smoke`, alvo `image` | `run-k6.ps1 -Target image -Test write -Level smoke -ResetDb` | 150 iterações e 450 requisições; falhas 0%; log do `teardown` com diferença 0; `totalElements` 10000 depois |
| 4 | Mesmos testes, alvo `functions` | `run-k6.ps1 -Target functions ...` | Mesmos resultados |
| 5 | Perfil em degraus | `run-k6.ps1 -Target image -Test read -Level smoke -Profile ramp` | Cerca de 100 iterações (±10), sem erro; arquivo `...-ramp.summary.json` |
| 6 | Alvo parado | Parar a API e rodar `run-k6.ps1 -Target container -Test read` | Falha imediata com "API indisponivel" e código de saída diferente de zero |
| 7 | Conteúdo do arquivo | `(Get-Content <arquivo> -Raw \| ConvertFrom-Json).summary` | Campos `requests`, `failedRate`, `latencyMs`, `byEndpoint` preenchidos (sem `null` nos principais) |
| 8 | (opcional) Nível `low`, leitura | `run-k6.ps1 -Target image -Test read -Level low` | 2400 iterações, falhas 0% |

### Resultados observados (Etapa 7)

Todas as saídas foram informadas pelo autor em 20/11/2026 (k6 v2.4.0, commit dd9395906d, go1.26.11, windows/amd64; gerador e API na mesma máquina; PostgreSQL do compose).

| # | Execução (identificador) | Resultado |
|---|---|---|
| 1 | `k6 version` | v2.4.0 (instalado com `winget install k6 --source winget`) |
| 2a | Leitura `smoke`, `image` (`20261120-180243`) | 151 iterações, 0% de falhas, checks 100%, 0 descartadas, código 0. **Resumo com contagem errada:** 163 requisições (incluía as 12 do `setup`) e `n/d` por endpoint (T-013); corrigido |
| 2b | Leitura `smoke`, `image` (`20261120-181002`, após a correção) | 150 iterações e 150 requisições (list 78, get 44, search 28), 0% de falhas, checks 100%, 0 descartadas, código 0 |
| 2c | Leitura `smoke`, `image` (`20261120-183505`, após a segunda correção) | 150 iterações e 150 requisições (list 73, get 42, search 35), 0% de falhas, checks 100%, arquivo gerado normalmente |
| 3 | Escrita `smoke`, `image`, `-ResetDb` (`20261120-181331`) | 151 iterações e 453 requisições (create, update e delete: 151 cada), 0% de falhas, checks 100%, 0 descartadas, código 0; "Livros antes: 10000 \| depois: 10000 \| diferença: 0"; `totalElements` 10000 |
| 4a | Leitura `smoke`, `functions` (`20261120-182215`) | 151 iterações e 151 requisições (list 82, get 42, search 27), 0% de falhas, checks 100%, 0 descartadas |
| 4b | Escrita `smoke`, `functions`, `-ResetDb` (`20261120-182303`) | 151 iterações e 453 requisições (151 de cada operação), 0% de falhas, checks 100%, 0 descartadas; diferença 0; `totalElements` 10000 depois |
| 5 | Leitura `smoke`, `image`, `-Profile ramp` (`20261120-181653`) | 102 iterações e 102 requisições (list 50, get 32, search 20), 0% de falhas, 0 descartadas, código 0; arquivo `...-read-image-smoke-ramp.summary.json`; cenário de 8 etapas e 32 s (cada degrau de 7,5 s é arredondado para 8 s) |
| 6a | Alvo parado, `container`, porta 8080 (`20261120-183138`, antes da correção) | Mensagem "API indisponivel em http://localhost:8080", código de saída **107**; **gravou** um `.summary.json` com zeros e "nenhum limite violado" (T-014); arquivo removido pelo autor |
| 6b | Alvo parado (`20261120-183431`, após a correção) | Mesma mensagem e código 107; "nenhuma iteracao executada"; **nenhum** arquivo gravado |
| 7 | Conteúdo do `.summary.json` (leitura `image`, `20261120-181002`) | `requests`, `failedRate`, `latencyMs`, `byEndpoint` e `thresholdsViolated` preenchidos (sem `null` nos principais); `thresholdsViolated` vazio |
| 8 | Nível `low` | **Não executado** |

Observações:

- A iteração a mais (151 em vez de 150) apareceu em 4 de 6 execuções de 30 s (nas outras 2 foram 150) e não houve descartadas: o k6 pode iniciar uma iteração a mais no executor de taxa constante.
- Em algumas saídas coladas do terminal faltaram a linha `alvo=...` e a linha de latência geral do resumo (causa não determinada); o `.summary.json` guardou os valores de latência.
- A API na imagem escuta a porta 8080 dentro do contêiner e é publicada no Windows como 8081 (`run-image.ps1`); o k6 usa 8081 no alvo `image`.
- As latências dos smokes **não** são medição de desempenho (carga leve, gerador e API na mesma máquina) e não comparam container e Functions.
- O código de saída dos testes no alvo `functions` não foi colado; a mensagem final do script ("k6 terminou sem violar limites") só é impressa com código 0.

### Riscos da Etapa 7 (situação em 20/11/2026)

- Estrutura do objeto do `handleSummary` no k6 2.4.0: funcionou; os campos foram preenchidos no arquivo. A contagem das métricas de latência só apareceu após incluir `count` em `TREND_STATS` (T-013).
- Limites com etiquetas (`http_req_duration{phase:load}`): a contagem da fase de carga passou a excluir as requisições do `setup` (150 de 150 iterações, contra 163 antes). O disparo de um limite violado (código 99) **não foi exercitado**.
- Variáveis de ambiente do PowerShell 5.1 e criação do arquivo em `results\`: funcionaram (taxa, duração e nível apareceram no cenário do k6 e os arquivos foram criados).
- Ids da amostra do `setup` e ISBNs sintéticos sob concorrência: sem `409` nem falha de PUT ou DELETE nos smokes (até 5 VUs). Níveis mais altos não foram executados.

Não coberto: desempenho, escalabilidade, cold start e custo (Etapa 10), gerador de carga separado da máquina da API e monitoramento (Etapa 8).

## Etapa 9 — validação funcional no Azure (TESTADO; resultados informados pelo autor em 25/11/2026)

Alvos: Container App (`ca-library-api`) e Function App (`fn-library-tcc-25367`), ambos em Brazil South, com o mesmo PostgreSQL 17.11 (General Purpose D2ds_v5) carregado com 10.000 livros. Gerador (k6 e `api-smoke.ps1`) na máquina do autor, pela internet.

### Resultados observados (Etapa 9)

| # | Verificação | Resultado |
|---|---|---|
| 1 | Banco no Azure: contagem, versão, collation e conexões | 10.000 linhas; PostgreSQL 17.11; `en_US.utf8`; `max_connections` 859; servidor `Ready`, General Purpose `Standard_D2ds_v5`, alta disponibilidade desativada |
| 2 | Imagem no registro | tag `v1` enviada; o digest termina como o da imagem local (`3d9f362ee5f0`) |
| 3 | Container App: `/health/ready` e listagem | `status UP`, `database UP`, `totalElements` 10000 |
| 4 | Function App: funções publicadas | 7 (`createBook`, `deleteBook`, `getBook`, `health`, `listBooks`, `ready`, `updateBook`); `/health` UP, `/health/ready` com `database UP`, `totalElements` 10000 (antes disso, publicações com zip errado carregaram 0 funções: T-018 e T-019) |
| 5 | `api-smoke.ps1` (42 cenários) no Container App | códigos como no local; `Compare-Object` contra `smoke-image.txt`: **sem diferenças** (arquivo `smoke-azure-container.txt`) |
| 6 | `api-smoke.ps1` (42 cenários) no Function App | códigos como no local; `Compare-Object` contra `smoke-functions.txt`: **sem diferenças** (arquivo `smoke-azure-functions.txt`) |
| 7 | k6 leitura `smoke`, Container App (`20261125-204422`) | 151 iterações e 151 requisições (list 78, get 53, search 20), 0% de falhas, checks 100%, 0 descartadas; mediana 22,8 ms, p95 33,2 ms; execução total de 57,1 s (30 s de carga; o restante foi antes da carga, provavelmente a primeira chamada com o app em zero réplicas: **não medido de forma controlada**) |
| 8 | k6 escrita `smoke`, Container App (`20261125-204546`) | 151 iterações e 453 requisições, 0% de falhas, 0 descartadas; mediana 20,1 ms, p95 27,1 ms, máximo 254,5 ms; livros antes 10000, depois 10000 |
| 9 | k6 leitura `smoke`, Function App (`20261125-204719`) | 151 iterações e 151 requisições (list 69, get 55, search 27), 0% de falhas, 0 descartadas; mediana 29,3 ms, p95 53,3 ms, p99 95,3 ms; execução total de 32,4 s |
| 10 | k6 escrita `smoke`, Function App (`20261125-204912`) | 150 iterações e 450 requisições, 0% de falhas, 0 descartadas; mediana 24,2 ms, p95 33,0 ms; livros antes 10000, depois 10000 |
| 11 | Banco após os testes | `totalElements` 10000 nos dois endereços |

Arquivos de resultados dos itens 7 a 10: `results\azure\*.summary.json`. Em todas as execuções o resumo do k6 diz "nenhum limite violado" (código de saída 0 pela mensagem do script).

### Observações e limitações (Etapa 9)

- Os smokes (5 iterações/s por 30 s) **não são medição de desempenho** e as latências **não comparam** container e Functions: gerador na máquina do autor, rede de internet, primeira chamada com possível cold start, uma execução de cada.
- A diferença de uma iteração (151 e 150) é a variação do executor de taxa constante já observada na Etapa 7.
- Não executados: níveis `low`, `medium` e `high` no Azure; `-Profile ramp` no Azure; cenário de cold start; métricas do Azure Monitor (Etapa 8).
