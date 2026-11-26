# Índice da documentação

Legenda de **status** usada em todos os documentos:

| Status | Significado |
|---|---|
| PLANEJADO | Definido no papel, ainda não construído |
| ESCRITO | Arquivos/código escritos, ainda não executados |
| TESTADO | Executado pelo autor, com resultado observado e registrado |
| IMPLANTADO | Rodando no Azure, confirmado |
| MEDIDO | Existem dados brutos preservados que sustentam o resultado |

Legenda de **tipo de afirmação** (classificação adotada neste projeto):
**[DOC]** sustentada por documentação oficial · **[DECISÃO]** decisão de projeto ·
**[MEDIDO]** resultado efetivamente medido · **[HIPÓTESE]** interpretação ainda não comprovada.

| Arquivo | Conteúdo | Situação |
|---|---|---|
| [architecture.md](architecture.md) | Visão geral e responsabilidades | Atualizado (Etapa 7 testada) |
| [architecture-decisions.md](architecture-decisions.md) | ADRs (até o ADR-023; aceitos em 21/11/2026: 014, 015, 017, 018, 019 e 021) | Em andamento |
| [implementation.md](implementation.md) | Camadas, casos de uso, arquivos | Atualizado (Etapa 7 testada) |
| [testing.md](testing.md) | Estratégia e resultados de testes | Atualizado (Etapas 4 a 7 e 9 validadas) |
| [experimental-methodology.md](experimental-methodology.md) | Metodologia (plano) | PLANEJADO |
| [deployment.md](deployment.md) | Implantação no Azure, custos e plano de remoção | Etapa 9 implantada e testada em smokes (25/11/2026) |
| [azure-resource-plan.md](azure-resource-plan.md) | Recursos e quantidades por etapa para estimar custos (sem preços) | PLANEJADO |
| [dev-log.md](dev-log.md) | Diário de desenvolvimento | Em andamento |
| [tcc-notes.md](tcc-notes.md) | Notas com fontes para a redação | Em andamento |
| [troubleshooting.md](troubleshooting.md) | Problemas e soluções | 23 ocorrências registradas |
| [api-reference.md](api-reference.md) | Endpoints | TESTADO manualmente (Etapa 3) |
| [openapi.yaml](openapi.yaml) | Contrato OpenAPI 3.0 (escrito à mão) | ESCRITO (Etapa 3); consistência básica verificada por teste (Etapa 4) |
| [observability.md](observability.md) | Monitoramento | PLANEJADO |
| [experimental-results.md](experimental-results.md) | Resultados | SEM DADOS |
