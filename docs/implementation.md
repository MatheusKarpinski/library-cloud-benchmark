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
