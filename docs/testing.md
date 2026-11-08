# Testes

## Estratégia

| Nível | Ferramenta | Onde | Status |
|---|---|---|---|
| Unitário (domínio e casos de uso) | JUnit 5 + AssertJ, repositório em memória **apenas de teste** | `core/src/test` | TESTADO |
| Integração de persistência (PostgreSQL real) | Testcontainers 2.x (exige Docker Desktop) | `infrastructure` | ESCRITO (Etapa 4) |
| Integração dos endpoints | Aplicação real + cliente HTTP do JDK + PostgreSQL em container | `adapter-container` | ESCRITO (Etapa 4); `adapter-functions` PLANEJADO (Etapa 5) |
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

## Etapa 4 — testes de integração (ESCRITO; execução pendente)

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
| `mvn test` (57 unitários) | Máquina do autor | **PENDENTE** |
| `mvn test -Pintegration` (85 testes) | Máquina do autor, Docker Desktop aberto | **PENDENTE** |

### Limitações

- A ordenação `lower(titulo)` depende da **collation** do banco. O repositório em memória dos testes unitários usa a ordem de pontos de código do Java; as duas coincidem para os dados dos testes (letras ASCII), mas não são equivalentes em geral. A collation do PostgreSQL no Azure deve ser conferida antes dos experimentos.
- Os testes de API verificam o JSON por trechos de texto (o formato compacto do Spring), não por esquema.
- O arquivo `openapi.yaml` é verificado só quanto a rotas, métodos e códigos de erro; os esquemas dos objetos não são validados automaticamente.
- O ciclo de vida e a falha de banco são cobertos; **desempenho e concorrência** não são objeto desta etapa.
