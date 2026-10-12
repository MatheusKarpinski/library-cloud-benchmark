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
- **Verificação feita pelo assistente:** compilação com Java 21 e execução dos 35 testes
  com *stubs* (35/35). **Não** foi executado `mvn`.
- **Verificação pendente (autor):** instalar ferramentas, rodar `mvn test` (ver checklist).
- **Verificação do autor (12/10/2026):** `java -version` 21.0.9 (Oracle), `mvn -v` 3.9.11, `JAVA_HOME` coerente com o JDK usado pelo Maven, Git 2.51.2. `mvn test`: 35 testes, 0 falhas, BUILD SUCCESS. Isso confirma também que o `pom.xml`, o Surefire 3.5.3 e as versões de JUnit/AssertJ funcionam.
- **Problemas encontrados:** nenhum até aqui (usar `troubleshooting.md` se ocorrerem).
- **Pendências / próximos passos:** Etapa 3 (infraestrutura JDBC + adaptador Spring Boot),
  decisão de região (ADR-011).
