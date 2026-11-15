# Testes

## Estratégia

| Nível | Ferramenta | Onde | Status |
|---|---|---|---|
| Unitário (domínio e casos de uso) | JUnit 5 + AssertJ, repositório em memória **apenas de teste** | `core/src/test` | TESTADO |
| Integração de persistência (PostgreSQL real) | Testcontainers 2.x (exige Docker Desktop) | `infrastructure` | TESTADO (Etapa 4, informado pelo autor em 12/11/2026) |
| Integração dos endpoints | Aplicação real + cliente HTTP do JDK + PostgreSQL em container | `adapter-container` | TESTADO (Etapa 4, informado pelo autor em 12/11/2026) |
| Unitário do adaptador Functions (lógica HTTP e JSON) | JUnit 5 + AssertJ, repositório em memória **apenas de teste** | `adapter-functions/src/test` | TESTADO (Etapa 5, informado pelo autor em 14/11/2026; contagem não registrada) |
| Equivalência entre adaptadores (container × Functions) | `scripts/api-smoke.ps1` + `Compare-Object` dos registros | raiz | TESTADO (Etapa 5, 14/11/2026): respostas iguais nos cenários, salvo o `Content-Type` do 204 (T-010) |
| Carga | k6 (ferramenta externa) | `load-tests/` | PLANEJADO (Etapa 7) |

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
| `mvn test` (76 esperados) | Máquina do autor | Passou, sem falhas observadas (informado). A saída completa não foi anexada: **contagens por módulo não registradas**; o total de 76 permanece o valor esperado |
| `mvn test -Pintegration` (104 esperados) | Máquina do autor, Docker Desktop aberto | Idem |
| `mvn -pl adapter-functions -am clean package` | Máquina do autor | Gerou a pasta executável; `func start` a carregou e o smoke rodou contra a porta 7071 (primeira tentativa, sem `clean`, falhou: T-009) |
| `func start` | Máquina do autor | Core Tools 4.16.0, runtime 4.1054.250.26428; worker Java iniciado; `java -version` 21.0.9 (Oracle) e `JAVA_HOME` = `C:\Program Files\Java\jdk-21` |
| `GET /api/books?size=3` | Máquina do autor | 200; `totalElements` 10000, `totalPages` 3334, `size` 3, primeiro título `Capitaes Areia vol. 1045` |
| `GET /health/ready` após religar o banco | Máquina do autor | `status: UP`, `database: UP` |
| Banco parado (`docker compose stop postgres`) | Máquina do autor | `/health` `UP`; `/health/ready` 503; `GET /api/books?size=1` 503 |
| `api-smoke.ps1` nos dois endereços | Máquina do autor | Registros de 255 linhas cada |
| `Compare-Object` dos registros | Máquina do autor | Só linhas `content-type`: nos 3 `DELETE` com 204, o Functions responde `text/plain` e o container não envia `Content-Type` (T-010). Status e corpos dos 42 cenários, inclusive pesquisa, erros e ordem do PUT, não apareceram como diferentes |

Pendente: repetir os dois registros com o `api-smoke.ps1` ajustado (que registra `(sem corpo)` no 204) e confirmar `Compare-Object` sem saída; registrar as contagens por módulo de `mvn test` e `mvn test -Pintegration` (`mvn test | Select-String` com filtro, ver `README.md`).

### Riscos da Etapa 5 — situação

| Risco | Situação em 14/11/2026 |
|---|---|
| Java 21 no Core Tools local (Windows) | Funcionou: worker Java iniciado com JDK 21.0.9 (`JAVA_HOME` do autor) |
| `package` gerar a pasta esperada e copiar `host.json` e `local.settings.json` | Funcionou (com `clean`; sem ele, pasta antiga de função removida permanece: T-009) |
| Funções com a mesma rota e métodos diferentes (`api/books`; `api/books/{id}`) | Funcionou nos cenários do smoke (POST/GET em `/api/books`; GET/PUT/DELETE em `/api/books/{id}`) |
| Precedência da rota literal `api/books/search` sobre `api/books/{id}` | **Falhou** (T-008); a pesquisa passou a ser decidida no código de `getBook`; confirmada pelo smoke |
| Jackson 3 com a biblioteca do Functions 3.2.4 | Funcionou: JSON dos cenários do smoke igual ao do container |
| Decodificação de parâmetros de consulta (`%20`) e leitura do corpo como texto | Funcionou: cenários de pesquisa e de POST/PUT iguais nos dois registros |
