# Observabilidade

Status: **TESTADO** (Etapa 8, resultados informados pelo autor em 26/11/2026): leitura das métricas no portal e pela linha de comando, e exportação para arquivos locais.

- **[DECISÃO]** (ADR-013) Primeiro k6 (visão do cliente) + Azure Monitor (infraestrutura). Prometheus/Grafana ficam para etapa posterior, só se houver benefício real.

## Ordem de trabalho (Etapas 9, 8 e 10)

A Etapa 8 depende dos recursos da Etapa 9: as métricas só existem para recursos criados. Sequência: implantar (Etapa 9) -> conferir o monitoramento (Etapa 8) -> rodar os experimentos (Etapa 10).

## Situação dos recursos de monitoramento (informada pelo autor)

- O ambiente do Container Apps criou sozinho o espaço de trabalho do Log Analytics `workspace-rglibrarytccIhlv` (destino padrão dos logs). O Azure CLI avisou que o padrão mudará para `azure-monitor` em versão futura (2.93).
- A Function App foi criada com o Application Insights ligado (componente com o nome do app) e `APPLICATIONINSIGHTS_CONNECTION_STRING` definida; os logs do host foram lidos por `az rest` (T-023).

## Métricas efetivas (lista obtida com `az monitor metrics list-definitions`, 26/11/2026)

**Container App `ca-library-api` (31 métricas):** `UsageNanoCores`, `WorkingSetBytes`, `TxBytes`, `RxBytes`, `Requests`, `RestartCount`, `Replicas`, `GpuUtilizationPercentage`, `CoresQuotaUsed`, `TotalCoresQuotaUsed`, `ResiliencyConnectTimeouts`, `ResiliencyRequestRetries`, `ResiliencyRequestTimeouts`, `ResiliencyRequestsPendingConnectionPool`, `ResiliencyEjectedHosts`, `ResiliencyEjectionsAborted`, `JvmMemoryTotalUsed`, `JvmMemoryTotalCommitted`, `JvmMemoryTotalLimit`, `JvmMemoryUsed`, `JvmMemoryCommitted`, `JvmMemoryLimit`, `JvmGcCount`, `JvmGcDuration`, `JvmThreadCount`, `JvmBufferMemoryUsage`, `JvmBufferMemoryLimit`, `JvmBufferCount`, `ResponseTime`, `CpuPercentage`, `MemoryPercentage`. No portal, `CpuPercentage`, `MemoryPercentage` e `ResponseTime` aparecem como **versão prévia**. Se as métricas `Jvm*` têm dados nesta app **não foi verificado** (só a lista de definições).

**Function App `fn-library-tcc-25367` (9 métricas):** `MemoryWorkingSet`, `AverageMemoryWorkingSet`, `InstanceCount`, `OnDemandFunctionExecutionCount`, `OnDemandFunctionExecutionUnits`, `CpuPercentage`, `AlwaysReadyFunctionExecutionCount`, `AlwaysReadyFunctionExecutionUnits`, `AlwaysReadyUnits`. A métrica de memória **existe** no Functions Flex (os trechos contraditórios da documentação, `tcc-notes.md` C36, ficam resolvidos para este app). Não há métricas de plataforma de tempo de resposta nem de códigos HTTP no Functions: elas vêm do Application Insights (tabela `requests`).

## Observações (resultados informados pelo autor)

- **Escala a zero do Container App confirmada pela métrica `Replicas`** (máximo por 15 minutos, 25 e 26/11/2026): 0 em quase todo o período; 1 nas janelas de 23:11Z e 23:26Z, 2 na de 23:41Z (período dos testes do dia 25/11, 20h41 a 20h56 no horário de Brasília) e 0 depois. Com janela de 15 minutos não se vê o tempo de subida ou de descida: na Etapa 10 usar intervalo de 1 minuto.
- **Application Insights e erros:** a consulta à tabela `requests` (3 dias) mostrou 200: 320, 201: 156, 204: 140, 400: 19, 404: 4 e 409: 2 (total 641, igual ao painel do portal), **todas com `success = True`**. Ou seja, para as funções que respondem normalmente com código 4xx, o Application Insights **não** conta falha (painel "Solicitações com falha" = 0). Os códigos 400, 404 e 409 batem exatamente com os cenários do smoke (19, 4 e 2); 201 e 204 não batem com a soma esperada do smoke e do k6, então essa contagem **não** é auditoria exata. Fonte oficial de contagens e de erros: o k6.
- Painéis do portal vistos pelo autor: Container App (CPU média 0,1667%, memória média 8,1667%, tempo de resposta médio 20,50 ms, bytes de entrada e saída); Function App (solicitações do servidor 641, tempo de resposta do servidor médio 13,63 ms, 0 falhas, disponibilidade "--" por não haver teste de disponibilidade). São médias do período inteiro, sem separar o tempo ocioso: não usar como resultado.

## Tempo para voltar a zero (dados de 1 minuto, UTC, informados pelo autor em 26/11/2026)

| Serviço | Minutos com réplica ou instância |
|---|---|
| Container App | 1 réplica de 23:21 a 23:27; **0 de 23:28 a 23:41**; 1 réplica em 23:42 e 23:43; 2 réplicas de 23:44 a 23:48; 1 réplica de 23:49 a 23:53; 0 a partir de 23:54 |
| Function App | 1 instância em 23:19 e 2 em 23:20; 1 de 23:29 a 23:35; 1 de 23:45 a 23:53 (4 desses pontos aparecem com a data 26/11 no arquivo, provável erro de rotulagem, **não verificado**); nenhuma nos demais minutos |

Horários dos k6 (relógio do PC, convertidos de Brasília para UTC): Container App leitura 23:44:22 e escrita 23:45:46; Functions leitura 23:47:19 e escrita 23:49:12.

- **Antes dos testes os dois estavam em zero** (Container App sem réplica de 23:28 a 23:41; Functions sem instância de 23:36 a 23:44). Uma leitura anterior, com janelas de 15 minutos, parecia mostrar uma réplica ativa antes do teste; os dados de 1 minuto mostram o contrário. Janelas de 15 minutos **não** servem para isso.
- A primeira réplica e a primeira instância aparecem cerca de 2 minutos **antes** dos horários dos k6, nos dois serviços: indício de que o relógio do PC está adiantado cerca de 2 minutos (**a verificar**). Isso importa para alinhar o k6 com as métricas.
- Depois do último teste de cada serviço, o Container App levou cerca de 3 a 4 minutos a mais que o Functions para chegar a zero (aproximadamente 8 a 10 minutos contra 4 a 6, conforme o desvio do relógio do PC).
- Indício preliminar, **uma amostra de cada, não medição**: o k6 de leitura gastou cerca de 27 s antes da carga no Container App (execução de 57,1 s) e cerca de 2 s no Functions (32,4 s), ambos começando de zero. Testar de forma controlada na Etapa 10.

## O que coletar na Etapa 10

| Pergunta | Container Apps | Functions (Flex) |
|---|---|---|
| Escala (réplicas ou instâncias; está em zero?) | `Replicas` (Maximum) | `InstanceCount` (Maximum) |
| Volume de requisições | `Requests` (Total) | `OnDemandFunctionExecutionCount` (Total) |
| CPU | `UsageNanoCores` (Average, Maximum) | `CpuPercentage` (Average, Maximum) |
| Memória | `WorkingSetBytes` (Average, Maximum) | `AverageMemoryWorkingSet` (Average, Maximum) |
| Reinícios, rede, resposta | `RestartCount`, `RxBytes`, `TxBytes`, `ResponseTime` (prévia) | `OnDemandFunctionExecutionUnits` |

A divisão de `Requests` por código de status e as dimensões de cada métrica **não** foram verificadas.

**Banco (mesmo servidor para os dois ambientes; lista de 27/11/2026):** `cpu_percent`, `memory_percent`, `active_connections`, `max_connections` (859), `connections_failed` e `disk_iops_consumed_percentage`, exportadas e **testadas** em 27/11/2026. `tps` está na lista de definições, mas veio **sem dados** (causa não determinada; talvez exija habilitar métricas avançadas do servidor, **não verificado**) e foi retirada da exportação. Têm a mesma definição nos dois ambientes e servem para saber se o banco virou o gargalo.

## Exportação das métricas (TESTADA em 26/11/2026)

O script `scripts\export-azure-metrics.ps1` grava, para uma janela de tempo, um arquivo JSON por métrica em `results\azure\metrics` (somente leitura; não cria recursos nem custo). Escolha: guardar os dados **em arquivos locais** em vez de enviá-los a um espaço de trabalho, porque o grupo de recursos será apagado ao final (as métricas de plataforma somem junto) e porque os arquivos entram na evidência da Etapa 10. Uso: `.\scripts\export-azure-metrics.ps1 -Hours 3` ou `-Start` e `-End` em UTC, com `-Tag` para identificar a bateria. Os arquivos contêm o identificador da assinatura nos campos de recurso: **não versionar** (`results/azure/` no `.gitignore`).

### Resultado do teste (informado pelo autor)

Janela de 30 horas (2026-11-25T18:24Z a 2026-11-27T00:24Z), intervalo de 1 minuto, 1.800 pontos por métrica; 13 métricas exportadas, 14 arquivos (13 mais o de metadados), nenhuma falha.

| Alvo | Métrica (agregação) | Pontos com dado | Maior valor |
|---|---|---|---|
| Container App | `Replicas` (Maximum) | 1.800 | 2 |
| Container App | `Requests` (Total) | 1.800 | 413 em um minuto |
| Container App | `UsageNanoCores` | 17 | 90.866.396 (cerca de 0,09 vCPU) |
| Container App | `WorkingSetBytes` | 17 | 294.244.352 bytes (cerca de 281 MiB) |
| Container App | `RestartCount` | 1.800 | 0 |
| Container App | `RxBytes` / `TxBytes` | 1.800 | 552.464 / 349.181 bytes em um minuto |
| Container App | `ResponseTime` (prévia) | 5 | 41,2 ms |
| Function App | `InstanceCount` (Maximum) | 18 | 2 |
| Function App | `OnDemandFunctionExecutionCount` | 1.800 | 257 em um minuto |
| Function App | `OnDemandFunctionExecutionUnits` | 1.800 | 49.152.000 |
| Function App | `CpuPercentage` | 1.800 | **0** durante todo o período |
| Function App | `AverageMemoryWorkingSet` | 1.800 | 698.060.800 bytes (cerca de 666 MiB) |

Leitura dos números:

- As métricas de CPU e memória do Container App só têm dado nos minutos em que havia réplica (17 de 1.800); `InstanceCount` do Functions só tem dado em 18 minutos. As convenções diferem: `Replicas` devolve 0 quando não há réplica, e `InstanceCount` não devolve valor quando não há instância. Não comparar "minutos com dado" entre os dois sem esse cuidado.
- Máximos incluem o `setup` do k6, os smokes e testes manuais: não são resultados de desempenho.
- `CpuPercentage` do Functions ficou em 0 mesmo durante os testes: **não é utilizável sem investigar** (causa não determinada). `UsageNanoCores` e `WorkingSetBytes` do Container App e `AverageMemoryWorkingSet` do Functions medem coisas de definição diferente e **não** devem ser comparados diretamente entre os ambientes.
- Não verificados: dados das métricas `Jvm*`, divisão de `Requests` por código de status e métricas do banco.

## Evidência (Etapa 10)

- Guardar, por bateria: o `.summary.json` do k6, as métricas exportadas para o mesmo intervalo (o horário do k6 está em horário local; as métricas, em UTC) e o custo real da ferramenta de custos do Azure (separado de estimativas).
- Registrar a hora de início e fim de cada bateria.
