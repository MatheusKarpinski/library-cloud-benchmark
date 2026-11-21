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

## Etapa 4 — Testes de integração (11/2026) — CONCLUÍDA E VALIDADA (resultados informados em 12/11/2026)

- **Objetivo:** automatizar a verificação do repositório JDBC contra PostgreSQL real e da API por HTTP; conferir o `openapi.yaml` contra a aplicação.
- **Decisões:** ADR-019 (proposta). Testcontainers 2.x pelo BOM (compatível com Docker 29); testes de integração por `@Tag("integration")` e perfil Maven `integration`.
- **Arquivos criados:** `infrastructure/src/test/.../PostgresTestContainer.java`, `JdbcBookRepositoryIntegrationTest.java`; `adapter-container/src/test/.../PostgresTestContainer.java`, `RunningApplication.java`, `BookApiIntegrationTest.java`, `BookApiDatabaseDownIntegrationTest.java`, `OpenApiDocumentTest.java`.
- **Arquivos modificados:** `pom.xml` (propriedade `tests.excludedGroups`, configuração do Surefire, perfil `integration`), `infrastructure/pom.xml` e `adapter-container/pom.xml` (dependências de teste), `ApplicationConfig.java` (configuração pelo `Environment`), `README.md` e documentos de `docs/`.
- **Funcionalidades:** 28 testes de integração e 3 testes unitários novos (contrato OpenAPI).
- **Testes executados e resultados (informados pelo autor em 12/11/2026):** `mvn test` (57 testes) e `mvn test -Pintegration` (85 testes) passaram, com o Docker Desktop aberto. A saída completa não foi registrada; tempos **não constam**. Contagem por módulo observada em 14/11/2026 (relatórios do Surefire, execução com `-Pintegration`): core 35, api-contract 11, infrastructure 22, adapter-container 17 (soma 85).
- **Pendências:** registrar as versões efetivas do Testcontainers, HikariCP, driver PostgreSQL e Tomcat (`mvn dependency:tree`) e o `SELECT version();` do PostgreSQL; confirmar a versão do PowerShell; collation do PostgreSQL no Azure; região do Azure (ADR-011); aprovação dos ADR-014 a ADR-019.

## Etapa 5 — Adaptador Azure Functions (14/11/2026) — CONCLUÍDA E VALIDADA LOCALMENTE

- **Objetivo:** expor a mesma API no Azure Functions (plano Flex Consumption como destino final), reutilizando `core`, `api-contract` e `infrastructure`; executar localmente e comparar as respostas com as do container.
- **Decisões:** ADR-020 (adaptador Functions) e ADR-021 (JSON com Jackson 3), ambas propostas e validadas localmente.
- **Arquivos criados:**
  - `adapter-functions/pom.xml`, `host.json`, `local.settings.example.json`;
  - `adapter-functions/src/main/java/br/edu/tcc/librarycloud/functions/`: `ApiResult`, `JsonCodec`, `JsonCodecException`, `JacksonJsonCodec`, `BookApiHandler`, `FunctionComponents`, `BookFunctions`;
  - `adapter-functions/src/test/java/br/edu/tcc/librarycloud/functions/`: `BookApiHandlerTest` (14), `JacksonJsonCodecTest` (5), `support/InMemoryBookRepository`, `support/FailingBookRepository`;
  - `scripts/new-local-settings.ps1` e `scripts/api-smoke.ps1`.
- **Arquivos modificados:** `pom.xml` (módulo `adapter-functions`), `README.md` e os documentos de `docs/`. Também foram registrados os resultados da Etapa 4 informados pelo autor.
- **Código de produção alterado:** nenhum arquivo dos módulos `core`, `api-contract`, `infrastructure` e `adapter-container`.
- **Testes executados e resultados (informados pelo autor, 14/11/2026):** `mvn test` e `mvn test -Pintegration` com `BUILD SUCCESS`; relatórios do Surefire: core 35, api-contract 11, infrastructure 22, adapter-container 17 e adapter-functions 19 = **104 testes, 0 falhas, 0 erros, 0 ignorados**; `func start` local com Core Tools 4.16.0 e JDK 21.0.9; `api-smoke.ps1` nos dois endereços (42 cenários) e, após o ajuste do script, `Compare-Object` **sem saída**; banco parado e religado. Detalhes em `testing.md`.
- **Problemas encontrados e soluções:** T-008 (pesquisa capturada por `api/books/{id}`; a função `searchBooks` foi removida e `getBook` desvia o segmento `search`); T-009 (pasta empacotada antiga sem `clean`; empacotar com `clean`); T-010 (`Content-Type: text/plain` no 204 do Functions; diferença inerente). Ver `troubleshooting.md`.
- **Código alterado após a primeira versão:** `BookFunctions` (7 funções em vez de 8); `scripts/api-smoke.ps1` (registra `(sem corpo)` no lugar do `Content-Type` de respostas sem corpo).
- **Diferenças entre adaptadores observadas/esperadas:** `Content-Type` do 204 (observada); formato de rota inexistente, erro inesperado, conversão numérica de `page`/`size` e pool não fechado (esperadas, não exercitadas).
- **Pendências / próximos passos:** (1) aprovar ou ajustar os ADR-014 a ADR-021; (2) commit da Etapa 4 e da Etapa 5; (3) Etapa 6 (iniciada, ver abaixo); (4) pendências herdadas: versões efetivas (`mvn dependency:tree`, `SELECT version();`), collation, `DB_CONNECTION_TIMEOUT_MS` igual nos dois ambientes, região (ADR-011).

## Etapa 6 — Imagem Docker da API em container (15/11/2026) — CONCLUÍDA E VALIDADA LOCALMENTE

- **Objetivo:** empacotar o adaptador de container em uma imagem Docker reproduzível, executá-la localmente ligada ao banco do compose e verificar que responde como o JAR.
- **Decisão:** ADR-022 (proposta): build multi-estágio com `maven:3.9-eclipse-temurin-21`; execução em `eclipse-temurin:21-jre-noble`; usuário `app` sem privilégios; `JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=75.0` como padrão (variável do experimento); configuração só por variáveis de ambiente.
- **Arquivos criados:** `Dockerfile`, `.dockerignore`, `scripts/run-image.ps1`.
- **Arquivos modificados:** `README.md` (status, estrutura, seção "Etapa 6") e os documentos de `docs/` (`architecture-decisions.md`, `architecture.md`, `implementation.md`, `testing.md`, `dev-log.md`, `tcc-notes.md`, `troubleshooting.md`, `deployment.md`, `README.md`). Também foram entregues, junto, os ajustes finais da Etapa 5 (contagens por módulo e smoke repetido).
- **Código de produção alterado:** nenhum arquivo dos módulos Java.
- **Testes executados e resultados (informados pelo autor, 15/11/2026):** `docker build` concluído; imagem de 444 MB em disco (120 MB de conteúdo), Temurin 21.0.13+9, usuário `app` (uid 10001), só o JAR em `/app`, nenhuma variável `DB_*`; API iniciada em 1,3 s e 1,6 s (duas execuções, não são medição de desempenho); smoke com 42 cenários **idêntico** ao do JAR; `docker stop` em 0,5 s com encerramento gracioso; passo opcional dos limites de recursos **não executado**. Detalhes em `testing.md`.
- **Ocorrências:** T-011 (digest da base com `docker image inspect`: erro do roteiro, corrigido para `buildx imagetools`) e T-012 (dica do Docker Desktop, sem falha); T-001 voltou ao reabrir os terminais (já documentada).
- **Riscos (situação):** tags `maven:3.9-eclipse-temurin-21` e `eclipse-temurin:21-jre-noble` e o build dentro da imagem: funcionaram; nome `postgres` na rede do compose: funcionou; senha por variável de ambiente do processo no PowerShell 5.1: funcionou; encerramento gracioso como PID 1: funcionou (0,5 s).
- **Pendências / próximos passos:** (1) opcional: executar o passo 7 (limites de memória e CPU) antes das medições; (2) aprovar ou ajustar os ADR-014 a ADR-022; (3) commit das Etapas 4, 5 e 6; (4) Etapa 7: scripts de carga com k6 (leitura e escrita separados, níveis configuráveis, URLs por variável de ambiente, exportação de resultados; verificar a instalação do k6); (5) pendências herdadas: versões efetivas (`mvn dependency:tree`, `SELECT version();`), collation, `DB_CONNECTION_TIMEOUT_MS` igual nos dois ambientes, região (ADR-011).

## Etapa 7 — Scripts de carga com k6 (15/11/2026; validada localmente em 20/11/2026) — CONCLUÍDA E VALIDADA EM SMOKES

- **Objetivo:** ter scripts de carga de leitura e de escrita separados, com níveis configuráveis, URL por variável de ambiente e resultados exportados em arquivo, para uso nas etapas de experimento.
- **Decisão:** ADR-023 (proposta): modelo aberto de taxa de chegada, configuração só por variáveis de ambiente, níveis provisórios, resumo em JSON com os parâmetros da execução, limites informativos.
- **Arquivos criados:** `k6/read.js`, `k6/write.js`, `k6/lib/common.js`, `k6/lib/api.js`, `scripts/run-k6.ps1`.
- **Arquivos modificados:** `README.md` (status, estrutura, pré-requisito, seção "Etapa 7") e os documentos de `docs/` (`architecture-decisions.md`, `tcc-notes.md`, `testing.md`, `dev-log.md`, `implementation.md`, `architecture.md`, `troubleshooting.md`, `README.md`). Também foram entregues, junto, os ajustes finais da Etapa 6 (resultados da validação, T-011 e T-012).
- **Código de produção alterado:** nenhum arquivo dos módulos Java.
- **Testes executados e resultados (informados pelo autor, 20/11/2026):** k6 v2.4.0 instalado com `winget`; smokes de leitura e de escrita nos alvos `image` e `functions` sem falhas (150 a 151 iterações; escrita com 453 requisições e diferença 0 no `teardown`; `totalElements` 10000 depois), perfil `ramp` (102 iterações) e alvo parado (código 107). Detalhes e identificadores das execuções em `testing.md`. **Não executados:** alvo `container` (JAR), níveis `low`, `medium` e `high`, limite violado (código 99).
- **Riscos (situação):** objeto do `handleSummary`: funcionou, com a ressalva da contagem (T-013); limites com etiquetas: contagem da fase de carga confirmada, código 99 não exercitado; variáveis de ambiente do PowerShell 5.1: funcionaram; ids e ISBNs sob concorrência: sem conflito nos smokes (até 5 VUs), níveis altos não executados.
- **Ocorrências:** T-013 (contagem de requisições da fase de carga incluía o `setup` e contagens por endpoint `n/d`: faltava `count` em `TREND_STATS`) e T-014 (execução abortada no `setup` gravava um resumo com zeros); T-001 voltou ao reabrir os terminais (já documentada).
- **Código alterado após a primeira versão:** `k6/lib/common.js` (`TREND_STATS` com `count`; `summaryOutputs` não grava arquivo quando não há iterações).
- **Observação:** os níveis são provisórios; a calibração (taxas, durações, número de repetições, localização do gerador de carga) é parte da metodologia da Etapa 10.
- **Pendências / próximos passos:** (1) commit da Etapa 7 (`feat: etapa 7 - scripts de carga k6 ...`; decidir se `.github/modernize/` entra); (2) aprovar ou ajustar os ADR-016, 020, 022 e 023 (os ADR-014, 015, 017, 018, 019 e 021 foram aceitos em 21/11/2026); (3) obter uma assinatura do Azure (ver o registro de 21/11/2026, abaixo) e então a Etapa 8: monitoramento com Azure Monitor (e depois Prometheus/Grafana); (4) pendências herdadas: versões efetivas (`mvn dependency:tree`, `SELECT version();`), collation, `DB_CONNECTION_TIMEOUT_MS` igual nos dois ambientes, região (ADR-011), passo opcional da Etapa 6 (limites de memória e CPU); (5) decidir onde guardar as evidências locais (`smoke-*.txt`, `log-image.txt`, `results\*.summary.json`) e se versioná-las.

## Registro de 21/11/2026 — ADR e assinatura do Azure

- **ADR aceitos pelo autor:** 014, 015, 017, 018, 019 e 021. Continuam propostas: 016, 020, 022 e 023 (revisão prevista depois das etapas do Azure).
- **Assinatura do Azure (informado pelo autor):** a assinatura usada em uma disciplina no ano anterior expirou e foi excluída (e-mail de exclusão no mês anterior). Nenhuma assinatura aparece no portal, mesmo com o filtro de assinaturas desabilitadas. A tentativa de renovar o Azure for Students e a inscrição no Azure for Students Starter foram negadas ("não qualificado").
- **Consequência:** as Etapas 8 (monitoramento), 9 (implantação), 10 e 11 dependem de uma assinatura. Opções em avaliação: recurso institucional (orientador/faculdade), suporte de cobrança da Microsoft, conta gratuita e pagamento conforme o uso (sem aprovar nenhum custo ainda).
- **Preparação:** `docs/azure-resource-plan.md` lista os recursos e as quantidades por etapa, sem preços, para montar a estimativa na calculadora oficial. Nenhum recurso será criado sem aprovação explícita dos custos pelo autor.
