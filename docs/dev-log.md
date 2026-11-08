# Diário de desenvolvimento

## Etapa 1 — Planejamento (11/10/2026) — CONCLUÍDA

- **Objetivo:** definir stack, arquitetura, estrutura e riscos.
- **Entregue:** proposta de stack e arquitetura; ver `architecture-decisions.md`.
- **Decisões aprovadas pelo autor:** Java 21 + Maven multi-módulo, PostgreSQL compartilhado,
  JDBC puro, Clean Architecture, Spring Boot no container (ADR-001 a ADR-005).
- **Cuidados adicionais pedidos:** comparabilidade, cold start (aquecido e escala a zero),
  avaliação do Flex Consumption, controle do PostgreSQL, aprovação de custos antes de
  provisionar, documentação contínua → ADR-007, ADR-012, `experimental-methodology.md`,
  `deployment.md`.
- **Pesquisa em documentação oficial:** ver `tcc-notes.md`.

## Etapa 2 — Ambiente e núcleo (11/10/2026) — CONCLUÍDA E VALIDADA

- **Objetivo:** preparar o ambiente Windows/VS Code e implementar domínio e casos de uso.
- **Arquivos criados:** `pom.xml`, `core/pom.xml`, `.gitignore`, 11 classes de produção e
  5 de teste em `core/`, README e documentos em `docs/`.
- **Funcionalidades:** CRUD, pesquisa por título, paginação, validações centralizadas.
- **Verificação do autor (11/10/2026):** `java -version` 21.0.9 (Oracle), `mvn -v` 3.9.11, `JAVA_HOME` coerente com o JDK do Maven, Git 2.51.2. `mvn test`: 35 testes, 0 falhas, BUILD SUCCESS (3,8 s). Confirma também o `pom.xml`, o Surefire 3.5.3 e JUnit/AssertJ.
- **Problemas encontrados:** nenhum até aqui (usar `troubleshooting.md` se ocorrerem).
- **Pendências / próximos passos:** Etapa 3 (infraestrutura JDBC + adaptador Spring Boot),
  decisão de região (ADR-011).


## Pré-etapa 3 — Docker (11/10/2026) — VALIDADA

- Docker 29.8.2, Compose v5.5.1; `docker run --rm hello-world` executou com sucesso (saída informada pelo autor).

## Etapa 3 — Persistência e API em container (escrita em 11/10/2026; validada em 02/11/2026) — CONCLUÍDA E VALIDADA

- **Objetivo:** PostgreSQL local, repositório JDBC, adaptador Spring Boot com os endpoints e saúde.
- **Decisões:** ADR-014 (Spring Boot 4.1.1 e BOM), ADR-015 (módulo `api-contract`), ADR-016 (PostgreSQL 17 e pool), ADR-017 (OpenAPI estático), ADR-018 (contrato de erros e saúde).
- **Arquivos criados:** módulos `api-contract`, `infrastructure`, `adapter-container`; `db/schema.sql`, `db/seed.sql`; `docker-compose.yml`; `.env.example`; `scripts/load-env.ps1`, `scripts/db-reset.ps1`; `docs/openapi.yaml`.
- **Arquivos modificados:** `pom.xml` (módulos, BOM do Spring Boot, plugin), `README.md`, vários arquivos de `docs/`.
- **Verificação do autor (02/11/2026):** `mvn test` na raiz com BUILD SUCCESS (4 módulos); PostgreSQL `healthy`; carga de 10000 livros; API iniciada; blocos A, B, C e D de testes manuais conforme `testing.md`. O Spring Boot 4.1.1 compilou e iniciou com o código gerado.
- **Problemas encontrados e soluções:** campo extra `up` em `/health/ready` (corrigido, T-002); `Show-Error` sem corpo no PowerShell 5.1 (corrigido, T-003); política de execução (T-001); exibição do PowerShell (T-004). Ver `troubleshooting.md`.
- **Código alterado após a primeira versão:** `ReadinessResponse.isUp()` → `up()`; ajustes correspondentes em `HealthController` e `ResponseMappingTest`. Testes automatizados após a correção: build com sucesso (a API foi reiniciada a partir do novo build).
- **Decisões:** ADR-014 a ADR-018 seguem **propostas**, agora validadas funcionalmente; aguardam aprovação explícita do autor.
- **Pendências / próximos passos:** (1) Etapa 4: testes de integração com PostgreSQL real (Testcontainers) e dos endpoints; (2) registrar versões efetivas de HikariCP, driver PostgreSQL, Tomcat e `SELECT version();` do PostgreSQL; (3) confirmar a versão do PowerShell; (4) região do Azure (ADR-011); (5) medir o tempo de resposta durante falha de banco, se relevante.

## Etapa 4 — Testes de integração (11/2026) — ESCRITA, VALIDAÇÃO PENDENTE

- **Objetivo:** automatizar a verificação do repositório JDBC contra PostgreSQL real e da API por HTTP; conferir o `openapi.yaml` contra a aplicação.
- **Decisões:** ADR-019 (proposta). Testcontainers 2.x pelo BOM (compatível com Docker 29); testes de integração por `@Tag("integration")` e perfil Maven `integration`.
- **Arquivos criados:** `infrastructure/src/test/.../PostgresTestContainer.java`, `JdbcBookRepositoryIntegrationTest.java`; `adapter-container/src/test/.../PostgresTestContainer.java`, `RunningApplication.java`, `BookApiIntegrationTest.java`, `BookApiDatabaseDownIntegrationTest.java`, `OpenApiDocumentTest.java`.
- **Arquivos modificados:** `pom.xml` (propriedade `tests.excludedGroups`, configuração do Surefire, perfil `integration`), `infrastructure/pom.xml` e `adapter-container/pom.xml` (dependências de teste), `ApplicationConfig.java` (configuração pelo `Environment`), `README.md` e documentos de `docs/`.
- **Funcionalidades:** 28 testes de integração e 3 testes unitários novos (contrato OpenAPI).
- **Testes executados e resultados:** **pendente.** Registrar aqui e em `testing.md` o resultado de `mvn test` (57 testes) e de `mvn test -Pintegration` (85 testes).
- **Pendências:** executar e registrar; registrar as versões efetivas do Testcontainers, HikariCP, driver PostgreSQL e Tomcat; confirmar a versão do PowerShell; collation do PostgreSQL no Azure; região do Azure (ADR-011); aprovação dos ADR-014 a ADR-019.
