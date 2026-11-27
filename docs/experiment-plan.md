# Plano dos experimentos (Etapa 10)

Status: **PLANEJADO**; os scripts `scripts/run-battery.ps1` e `scripts/cold-start.ps1` estão **ESCRITOS e não testados**. Decisões marcadas como **[DECISÃO PENDENTE]** dependem do autor.

## Prazo (informado pelo autor)

A entrega do TCC-I é em **02/12/2026**. Isto substitui a meta anterior de 10/12/2026 (que era uma margem em relação ao fim do crédito, em 22/12/2026). A coleta precisa terminar a tempo de analisar e escrever os resultados.

## Pergunta e controles de justiça

Comparar Azure Container Apps e Azure Functions (Flex Consumption) com o mesmo núcleo, o mesmo contrato, o mesmo banco e as mesmas cargas. Controles:

| Controle | Como |
|---|---|
| Mesma carga e mesmo gerador | os mesmos scripts k6 e a mesma máquina para os dois alvos |
| Sem interferência | os alvos são testados **em sequência**, nunca ao mesmo tempo (compartilham o banco) |
| Efeito do horário e da ordem | a ordem dos alvos alterna a cada repetição |
| Recursos equivalentes | Container App com 1 vCPU e 2 GiB (0 a 5 réplicas); Functions com instância de 2.048 MB (1 CPU, segundo a documentação) e máximo de 5 instâncias |
| Banco | o mesmo servidor (General Purpose D2ds_v5); suas métricas têm a mesma definição para os dois |
| Regras de escala | as padrão de cada plataforma, **não ajustadas** (fazem parte da arquitetura); **a registrar** os valores efetivos (`az containerapp show` e `az functionapp scale config show`) |
| Relógio | sincronizar o relógio do PC com o servidor antes da campanha (desvio medido em 27/11/2026: PC adiantado 3 min 43 s) |

## O que se compara e o que só se descreve

- **Comparável (mesma definição, vista do cliente, pelo k6):** vazão, taxa de erros e códigos de resposta, latência (mediana, p90, p95, p99), iterações descartadas, tempo da primeira requisição depois de zero (cold start).
- **Descritivo (cada plataforma mede de um jeito):** número de réplicas e de instâncias, CPU e memória do servidor (`UsageNanoCores` e `WorkingSetBytes` no Container Apps; `AverageMemoryWorkingSet` no Functions; o `CpuPercentage` do Functions veio sempre 0 e não é usado), custo (modelos de cobrança diferentes, por recurso na ferramenta de custos).
- **Para achar o gargalo (mesmo servidor nos dois):** `cpu_percent`, `memory_percent`, `active_connections`, `connections_failed`, `tps` e `disk_iops_consumed_percentage` do banco; o limite de conexões é 859. Se o banco saturar nos dois, o resultado reflete o banco, e não as arquiteturas.
- **Não usar como medida de erro:** o painel de falhas do Application Insights (não conta 4xx devolvido pelo código; `observability.md`).

## Desenho

1. **Níveis do k6:** `low` (20 por segundo, 2 min), `medium` (100 por segundo, 3 min) e `high` (300 por segundo, 3 min), leitura e escrita; níveis ainda **provisórios** (ADR-023) até a calibração.
2. **Calibração:** `low` e `medium` de leitura, uma repetição, nos dois alvos; conferir 0 iterações descartadas. Se o PC não sustentar um nível, reduzir esse nível e registrar a limitação.
3. **Repetições:** 3 por combinação (36 execuções, cerca de 2 h 30 min); alternativa de 2 repetições (24 execuções, cerca de 1 h 40 min). **[DECISÃO PENDENTE]**
4. **Aquecimento:** o `setup` do k6 (verificação e amostragem de ids) acorda o serviço antes da fase de carga; a medição de latência considera só a fase de carga. A subida de réplicas e de instâncias durante a carga **faz parte** do que se mede.
5. **Cold start:** 5 rodadas; em cada uma, 15 minutos sem nenhum acesso, e então 3 requisições seguidas a cada serviço (a primeira é a fria). Os 15 minutos cobrem o tempo que o Container App levou para voltar a zero (cerca de 8 a 10 minutos nos dados de 25/11, `observability.md`). Cerca de 75 minutos de espera, sem uso do PC para acessar as APIs.
6. **Gerador de carga:** o PC do autor, para os dois alvos, com a limitação registrada (internet doméstica e relógio); uma máquina virtual no Azure na mesma região fica como alternativa se a calibração mostrar saturação do PC. **[DECISÃO PENDENTE]**
7. **Evidência:** `.summary.json` do k6, `execucoes.csv` com o início e o fim em UTC de cada execução, logs, métricas exportadas (`export-azure-metrics.ps1`), custo real e os dados do cold start, tudo em `results\azure` (fora do Git). Depois, apagar o grupo de recursos.

## Scripts (ESCRITOS, não testados)

| Script | Uso |
|---|---|
| `run-battery.ps1` | `-DryRun` mostra o plano e a estimativa; `-Levels smoke -Repetitions 1 -Tag teste` é o teste curto (cerca de 8 min); `-Levels low,medium -Tests read -Repetitions 1 -Tag calibracao`; `-Repetitions 3 -Tag campanha` |
| `cold-start.ps1` | `-Rounds 1 -IdleMinutes 1 -Tag cold-teste` é o teste curto; `-Rounds 5 -IdleMinutes 15 -Tag coldstart` |
| `export-azure-metrics.ps1` | chamado ao fim da campanha; agora inclui 6 métricas do banco (**não testadas**) |

## Calendário proposto (a ajustar pelo autor)

| Dia | Atividade |
|---|---|
| 27/11 (sexta) | sincronizar o relógio; ligar o banco; teste curto dos dois scripts; calibração |
| 28/11 (sábado) | campanha principal (cerca de 2 h 30 min) |
| 29/11 (domingo) | cold start (cerca de 80 min, sem usar o PC para acessar as APIs); exportar métricas; conferir custo |
| 30/11 e 01/12 | análise e escrita dos resultados; parar o banco; decidir o momento de apagar o grupo de recursos |
| 02/12 | entrega do TCC-I |
