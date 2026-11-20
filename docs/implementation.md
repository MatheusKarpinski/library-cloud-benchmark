# Implementação

## Etapa 2 — núcleo (`core`) — status: TESTADO

Pacote base: `br.edu.tcc.librarycloud`.

| Arquivo | Papel |
|---|---|
| `domain/Book.java` | Entidade imutável; **todas** as validações e normalizações (texto, ISBN, ano) |
| `domain/exception/ValidationException.java`, `FieldViolation.java` | Erro de validação com a lista de campos inválidos |
| `application/BookService.java` | Casos de uso: criar, consultar, listar, pesquisar, atualizar, excluir |
| `application/BookData.java` | Dados de entrada (tipos `Integer`/`Boolean` para detectar campo ausente) |
| `application/PageRequest.java`, `Page.java` | Paginação (padrão 20, máximo 100, página começa em 0) |
| `application/port/BookRepository.java` | Porta de persistência + contrato (ordenação, busca, erros) |
| `application/exception/*` | `BookNotFoundException`, `DuplicateIsbnException`, `PersistenceFailureException` |

### Regras de validação (fonte única: `Book`)

| Campo | Regra |
|---|---|
| `titulo` | obrigatório, até 200 caracteres (após `strip`) |
| `autor` | obrigatório, até 150 |
| `categoria` | obrigatória, até 80 |
| `isbn` | opcional; normalizado (sem hífens/espaços, maiúsculas); 13 dígitos ou 9 dígitos + dígito/X. **Só formato — o dígito verificador não é conferido** |
| `anoPublicacao` | obrigatório, de 1000 até (ano atual + 1) |
| `disponivel` | opcional; ausente = `true` |
| `id` | `UUID` gerado pela aplicação |

### Ordem das verificações na atualização (PUT)

id válido (400) → livro existe (404) → dados válidos (400) → ISBN livre (409).

### Limitações conhecidas

- A checagem de ISBN duplicado (consulta + gravação) tem uma janela de corrida entre duas
  requisições simultâneas. A proteção definitiva será a restrição `UNIQUE` no banco
  (Etapa 3), convertida em `DuplicateIsbnException` pelo repositório.
- Busca por título não ignora acentos (ex.: "memorias" não encontra "Memórias").
- O ano máximo depende da data do sistema (`Year.now()`).


## Etapa 3 — API em container e persistência — status: TESTADA (validação manual em 02/11/2026)

| Módulo / arquivo | Papel |
|---|---|
| `api-contract/.../ErrorMapper.java` | Tabela única exceção → (status HTTP, corpo JSON) |
| `api-contract/.../BookRequest, BookResponse, PageResponse, ErrorResponse` | Contrato JSON compartilhado |
| `infrastructure/.../config/DatabaseConfig.java` | Lê `DB_*` de variáveis de ambiente; falha com mensagem clara se faltar algo; nunca imprime a senha |
| `infrastructure/.../config/DataSourceFactory.java` | Cria o pool HikariCP (não conecta na criação: `initializationFailTimeout = -1`) |
| `infrastructure/.../persistence/JdbcBookRepository.java` | SQL (INSERT … ON CONFLICT, ILIKE, LIMIT/OFFSET) e tradução de erros do banco |
| `infrastructure/.../persistence/DatabaseHealthChecker.java` | `isValid` na conexão, usado em `/health/ready` |
| `adapter-container/.../web/BookController.java` | Endpoints `/api/books` |
| `adapter-container/.../web/HealthController.java` | `/health` (sem banco) e `/health/ready` (com banco) |
| `adapter-container/.../web/ApiExceptionHandler.java` | Tratamento centralizado de erros (usa `ErrorMapper`) |
| `adapter-container/.../config/ApplicationConfig.java` | Montagem manual: DataSource → repositório → `BookService` |
| `db/schema.sql`, `db/seed.sql` | Esquema e carga inicial determinística |
| `docker-compose.yml`, `.env.example`, `scripts/*.ps1` | Banco local e procedimentos reproduzíveis |

### Comportamentos para conhecer

- **Criação** devolve 201 com cabeçalho `Location` e o livro criado; **exclusão** devolve 204.
- **Listagem** executa duas consultas (contagem + página). Em tabelas grandes `count(*)` tem custo; é igual nas duas implantações.
- **Pesquisa** usa `ILIKE '%termo%'` sem índice (um índice trigram exigiria uma extensão do PostgreSQL). O custo cresce com o número de linhas — e é o mesmo nos dois ambientes.
- **Concorrência de ISBN:** a restrição `UNIQUE` do banco fecha a janela de corrida descrita na Etapa 2 (violação `23505` → 409).
- **Pool:** `DB_POOL_MAX` padrão 5 e `DB_POOL_MIN_IDLE` padrão 1, **por instância**. Os valores definitivos dos experimentos serão registrados na metodologia.
- **Sem Spring Data, sem JPA, sem Actuator, sem springdoc** no adaptador (ADR-004, ADR-017).

### Limitações conhecidas (Etapa 3)

- A busca por título depende do `lower()`/`ILIKE` do PostgreSQL e da localidade do banco para caracteres não-ASCII; os dados de carga são só ASCII.
- Rotas inexistentes e métodos não permitidos retornam o formato de erro padrão do Spring (ver `architecture.md`).

### Lição da validação (02/11/2026): métodos de acesso em registros de resposta

Um método `isUp()` em `ReadinessResponse` fez o Jackson incluir um campo extra `"up": true` na resposta de `/health/ready`, fora do contrato. Corrigido renomeando para `up()`. **Regra:** classes do módulo `api-contract` não devem ter métodos com nome no padrão `getX()`/`isX()` além dos componentes do registro, pois serão serializados como propriedades.

### Etapa 4 — alteração no código de produção

`ApplicationConfig` passou a ler `DB_URL`, `DB_USER` etc. pelo `Environment` do Spring (`DatabaseConfig.from(name -> environment.getProperty(name))`) em vez de `DatabaseConfig.fromEnvironment()`. Em execução normal isso equivale a ler as variáveis de ambiente do sistema; nos testes de integração permite apontar a aplicação para o banco do container por argumentos de linha de comando, que têm prioridade sobre as variáveis do sistema. Validação, valores padrão e mensagens de erro continuam em `DatabaseConfig` (compartilhada com o futuro adaptador do Functions).


## Etapa 5 — adaptador Azure Functions — status: TESTADO localmente (smoke de 14/11/2026)

Pacote: `br.edu.tcc.librarycloud.functions`.

| Arquivo | Papel |
|---|---|
| `adapter-functions/pom.xml` | Depende de `core`, `api-contract`, `infrastructure`, `azure-functions-java-library` 3.2.4 e Jackson 3 (versão do BOM); plugin `azure-functions-maven-plugin` 1.40.0 com o objetivo `package` |
| `adapter-functions/host.json` | `extensions.http.routePrefix` vazio: as rotas ficam `/api/books` e `/health`, como no container |
| `adapter-functions/local.settings.example.json` | Modelo das configurações locais (`FUNCTIONS_WORKER_RUNTIME=java` e `DB_*`); o arquivo real (`local.settings.json`) é criado por `scripts/new-local-settings.ps1` e não é versionado |
| `BookFunctions` | 7 funções HTTP anônimas (a pesquisa é atendida por `getBook`, ver abaixo); converte `ApiResult` na resposta do runtime (status obtido por busca no enum `HttpStatus` pelo código) |
| `BookApiHandler` | Todos os casos de uso HTTP, sem dependência do runtime; recebe `BookService`, `JsonCodec` e um `BooleanSupplier` do banco |
| `ApiResult` | Status, corpo (texto JSON ou `null`) e cabeçalhos extras |
| `FunctionComponents` | Criação única e preguiçosa (por processo) de `BookService`, pool e verificador de saúde, com `DatabaseConfig.fromEnvironment()`; falha de criação não é guardada |
| `JsonCodec`, `JacksonJsonCodec`, `JsonCodecException` | JSON com Jackson 3; `FAIL_ON_UNKNOWN_PROPERTIES` desligado |

### Rotas

| Função | Método e rota |
|---|---|
| `health` | `GET /health` |
| `ready` | `GET /health/ready` |
| `listBooks` | `GET /api/books` |
| `createBook` | `POST /api/books` |
| `getBook` | `GET /api/books/{id}`; quando `{id}` é exatamente `search`, executa a pesquisa por título (`GET /api/books/search?title=`) |
| `updateBook` / `deleteBook` | `PUT` / `DELETE /api/books/{id}` |

### Comportamentos para conhecer

- **Ordem das verificações** (igual à do adaptador Spring): o corpo JSON é lido antes de qualquer regra do serviço (corpo ausente, em branco, `null`, `[]`, malformado ou com tipo incorreto => 400 `MALFORMED_REQUEST`, também no PUT de id inexistente); em listagem e pesquisa, `page` e depois `size` são convertidos (erro => 400 `VALIDATION_ERROR` com o nome do **primeiro** parâmetro inválido), depois `PageRequest`, depois o serviço.
- **`page` ou `size` vazios** (`?page=&size=`) usam o padrão.
- **Falha ao montar os objetos** (por exemplo, `DB_*` ausente): resposta `500 INTERNAL_ERROR` genérica; o detalhe vai para o log da função. A próxima requisição tenta montar de novo.
- **Cabeçalhos:** `Content-Type: application/json` em respostas com corpo; `Location` na criação; resposta 204 sem corpo.
- **Segurança:** acesso anônimo apenas para a execução local; a proteção do endpoint será tratada na Etapa 9.

### Limitações conhecidas (Etapa 5)

- Validado só na execução local (Core Tools); a implantação no Azure (Flex Consumption) não foi feita.
- Respostas sem corpo (204) levam `Content-Type: text/plain` no Functions e nenhum no container (T-010).
- O pool de conexões não é fechado explicitamente no Functions.
- O `JsonMapper` do Functions é montado à parte do mapper do Spring Boot; diferenças de configuração só aparecem comparando respostas (`api-smoke.ps1`).
- A conversão de `page` e `size` usa `Integer.parseInt`; o conversor do Spring é próprio e pode aceitar formatos incomuns de forma diferente.


## Etapa 6 — imagem Docker da API em container — status: TESTADO localmente (15/11/2026)

| Arquivo | Papel |
|---|---|
| `Dockerfile` | Estágio `build` (`maven:3.9-eclipse-temurin-21`): `mvn -pl adapter-container -am package -Dmaven.test.skip=true`, com cache do Maven (BuildKit). Estágio final (`eclipse-temurin:21-jre-noble`): cria o usuário `app` (uid 10001), copia `library-api.jar`, expõe a porta 8080 e inicia com `java -jar` |
| `.dockerignore` | Exclui do contexto: `target/`, `.git/`, `.github/`, `.vscode/`, `.env`, `local.settings.json`, `smoke-*.txt`, `docs/`, `scripts/`, arquivos `.md` e os arquivos do próprio Docker |
| `scripts/run-image.ps1` | Lê o `.env`, descobre a rede do contêiner `library-postgres`, reescreve o `DB_URL` para o host `postgres` (mesmo nome de banco), repassa `DB_USER`, `DB_PASSWORD` e `DB_POOL_*` por variável de ambiente do processo (a senha não vai para a linha de comando), publica `127.0.0.1:<Port>` (padrão 8081) e aceita `-Memory` e `-Cpus` |

### Comportamentos para conhecer

- A imagem **não** contém segredos nem o `.env`; sem as variáveis `DB_*`, a API falha ao iniciar (mesma validação do JAR).
- O módulo `adapter-functions` precisa estar presente no contexto (o POM pai o lista como módulo), mas **não** é compilado na imagem.
- `JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=75.0` faz a JVM imprimir `Picked up JAVA_TOOL_OPTIONS` ao iniciar (comportamento esperado do JDK).
- A tag da base (`21-jre-noble`) é flutuante: registrar o *digest* e a versão do Java após cada build.
- O processo Java é o PID 1 do contêiner e recebe o `SIGTERM` do `docker stop`: o encerramento gracioso foi observado (0,5 s, log do Spring).

### Limitações conhecidas (Etapa 6)

- Validada só localmente (Docker Desktop); a execução no Azure Container Apps não foi feita.
- Os limites de memória e CPU (passo opcional) ainda não foram exercitados.
- Sem `HEALTHCHECK` na imagem; as sondas serão configuradas no Container Apps (Etapa 9).
- Sem camadas de JAR, `jlink` ou imagem mínima: otimizações só depois de medir.


## Etapa 7 — scripts de carga com k6 — status: TESTADO (smokes locais, 20/11/2026)

| Arquivo | Papel |
|---|---|
| `k6/read.js` | `setup`: verifica `GET /health/ready` e a listagem, e coleta uma amostra de ids espalhada por todo o conjunto (páginas igualmente espaçadas de 100 itens, até `ID_SAMPLE_PAGES`). Cada iteração escolhe uma operação pelos pesos (`MIX_*`): listagem de página aleatória, consulta de id da amostra ou pesquisa por um dos termos fixos |
| `k6/write.js` | `setup`: verifica a API e guarda o total de livros. Cada iteração: POST (ISBN `979` + semente da execução + contador da iteração), PUT (marca `disponivel` falso) e DELETE; o DELETE é feito mesmo que o PUT falhe. `teardown`: registra no log a diferença de livros antes e depois |
| `k6/lib/common.js` | `resolveConfig` (valida variáveis de ambiente), `buildScenarios` (constant ou ramp), `buildThresholds`, `buildOptions`, geração de ISBN e livros, `pickOperation` e o resumo (`compactSummary`, `summaryText`, `summaryOutputs`). Sem módulos do k6 |
| `k6/lib/api.js` | `call` (HTTP com as etiquetas `endpoint` e `name`), `field` (leitura segura de campos JSON) e `checkApi` (verificação da API antes da carga) |
| `scripts/run-k6.ps1` | Valida a instalação do k6, define as variáveis de ambiente do processo, cria `results\`, executa `k6 run` e interpreta o código de saída |

### Comportamentos para conhecer

- **Fase de carga:** as métricas do resumo usam a etiqueta `phase:load`; as requisições do `setup` e do `teardown` ficam de fora.
- **Etiquetas:** `target`, `level`, `test` e `profile` em todas as métricas; `endpoint` por operação; `name` agrupa URLs com id.
- **Limites informativos** (não interrompem o teste): falhas < `MAX_ERROR_RATE` (1%), p95 < `P95_MS` (1000 ms), checks > 99%, `dropped_iterations` igual a 0. Violação => código de saída 99 do k6.
- **Arquivo de resultados:** `results/<execução>.summary.json` com `meta`, `summary` e `data`; o diretório precisa existir (o `run-k6.ps1` o cria). Execução **sem nenhuma iteração** (por exemplo, `setup` que falha por API parada) não grava arquivo e termina com código 107 (T-014).
- **Contagens:** a contagem de requisições da fase de carga e a de cada endpoint vêm da estatística `count` das métricas de latência; por isso `TREND_STATS` (em `k6/lib/common.js`) inclui `count` (T-013).
- **ISBN:** `979` + 3 dígitos da semente (`RUN_SEED`, padrão derivado da hora) + 7 dígitos do contador; a carga padrão usa `978`.

### Limitações conhecidas (Etapa 7)

- Validado só em smokes locais (5 iterações/s por 30 s), nos alvos `image` e `functions`; o alvo `container` (JAR) e os níveis `low`, `medium` e `high` não foram executados. Ver `testing.md`.
- Níveis provisórios; sem aquecimento (*warm-up*) separado, sem repetições automáticas e sem cenário de *cold start*.
- Pesquisa com termos fixos (8 termos); a mistura de operações é configurável, mas não há distribuição de acessos não uniforme (todos os ids e páginas têm a mesma chance).
- O arquivo de resultados não grava a versão do k6.
