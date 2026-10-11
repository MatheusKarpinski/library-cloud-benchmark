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

## Etapa 2 — Ambiente e núcleo (11/10/2026) — GERADA, VALIDAÇÃO LOCAL PENDENTE

- **Objetivo:** preparar o ambiente Windows/VS Code e implementar domínio e casos de uso.
- **Arquivos criados:** `pom.xml`, `core/pom.xml`, `.gitignore`, 11 classes de produção e
  5 de teste em `core/`, README e documentos em `docs/`.
- **Funcionalidades:** CRUD, pesquisa por título, paginação, validações centralizadas.
- **Verificação feita pelo assistente:** compilação com Java 21 e execução dos 35 testes
  com *stubs* (35/35). **Não** foi executado `mvn`.
- **Verificação pendente (autor):** instalar ferramentas, rodar `mvn test` (ver checklist).
- **Problemas encontrados:** nenhum até aqui (usar `troubleshooting.md` se ocorrerem).
- **Pendências / próximos passos:** Etapa 3 (infraestrutura JDBC + adaptador Spring Boot),
  decisão de região (ADR-011).
