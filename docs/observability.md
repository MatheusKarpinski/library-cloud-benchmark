# Observabilidade

Status: **PLANEJADO** (Etapa 8; nenhum recurso criado).

- **[DECISÃO]** (ADR-013) Primeiro k6 (visão do cliente) + Azure Monitor (infraestrutura).
  Prometheus/Grafana ficam para etapa posterior, só se houver benefício real.
- Métricas do Azure diferem entre Container Apps e Functions; a lista efetiva será
  documentada na Etapa 8, a partir do que o portal realmente expuser.

## Ordem de trabalho (Etapas 9, 8 e 10)

A Etapa 8 depende dos recursos da Etapa 9: as métricas só existem para recursos criados. Por isso a sequência é:
implantar (Etapa 9) -> configurar e conferir o monitoramento (Etapa 8) -> rodar os experimentos (Etapa 10).

Decisões de monitoramento que precisam ser tomadas **na criação** dos recursos (Etapa 9):

- Container Apps: destino dos logs do ambiente (Log Analytics, outro destino ou nenhum). Métricas de plataforma são gratuitas;
  logs são cobrados por GB acima de 5 GB/mês [DOC, `tcc-notes.md` C34].
- Functions (Flex Consumption): Application Insights habilitado na criação, ligado ao mesmo espaço de trabalho do Log Analytics.
- Marcas (tags) iguais em todos os recursos (por exemplo, `projeto=tcc`), para filtrar o custo real na análise de custos.

## Métricas candidatas [DOC, `tcc-notes.md` C36; lista efetiva NÃO VERIFICADA]

| Pergunta do experimento | Container Apps | Functions (Flex) |
|---|---|---|
| Quantas réplicas ou instâncias ativas? Está em zero? | Replicas | contagem de instâncias; Always Ready Units |
| Quantas requisições e com qual status? | Requests (por código de status) | contagem de execuções (sob demanda e sempre prontas); contagem de respostas HTTP |
| CPU e memória | UsageNanoCores; WorkingSetBytes | CpuPercentage (só Flex); memória a confirmar (trechos contraditórios na documentação) |
| Reinícios, rede | RestartCount; RxBytes; TxBytes | tempo de resposta HTTP |

## Evidência (Etapa 10)

- Métricas de plataforma ficam 90 dias; exportá-las antes de apagar o grupo de recursos [DOC, C34].
- Guardar, por bateria: o `.summary.json` do k6 (`results\`), a captura ou exportação das métricas do Azure Monitor para o
  mesmo intervalo de horário e o custo real da ferramenta de custos do Azure (separado de estimativas).
- Registrar a hora de início e fim de cada bateria para alinhar o k6 e o Azure Monitor.

## Situação após a Etapa 9 (informada pelo autor, 24 e 25/11/2026)

- O ambiente do Container Apps criou sozinho o espaço de trabalho do Log Analytics `workspace-rglibrarytccIhlv` (destino padrão dos logs). O Azure CLI avisou que o padrão mudará para `azure-monitor` em versão futura (2.93).
- A Function App foi criada com o Application Insights ligado (componente com o nome do app) e `APPLICATIONINSIGHTS_CONNECTION_STRING` definida; os logs do host foram lidos por `az rest` (T-023).
- Ainda **não** foram conferidas no portal as métricas efetivas de cada recurso (Etapa 8).
