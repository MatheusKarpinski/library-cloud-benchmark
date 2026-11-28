# Plano dos experimentos (Etapa 10)

Status: **PLANEJADO**; `scripts/run-battery.ps1`, `scripts/cold-start.ps1` e `scripts/export-azure-metrics.ps1` foram **TESTADOS em testes curtos** em 27/11/2026 (resultados abaixo). Decisões marcadas como **[DECISÃO PENDENTE]** dependem do autor.

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
3. **Repetições:** 3 por combinação (36 execuções, cerca de **2 h**, medido o tempo real de cada execução na calibração); alternativa de 2 repetições (24 execuções, cerca de 1 h 20 min). **[DECISÃO PENDENTE]**
4. **Aquecimento:** o `setup` do k6 (verificação e amostragem de ids) acorda o serviço antes da fase de carga; a medição de latência considera só a fase de carga. A subida de réplicas e de instâncias durante a carga **faz parte** do que se mede.
5. **Cold start:** 5 rodadas; em cada uma, 15 minutos sem nenhum acesso, e então 3 requisições seguidas a cada serviço (a primeira é a fria). Os 15 minutos cobrem o tempo que o Container App levou para voltar a zero (cerca de 8 a 10 minutos nos dados de 25/11, `observability.md`). Cerca de 75 minutos de espera, sem uso do PC para acessar as APIs.
6. **Gerador de carga:** o PC do autor, para os dois alvos, com a limitação registrada (internet doméstica e relógio); uma máquina virtual no Azure na mesma região fica como alternativa se a calibração mostrar saturação do PC. **[DECISÃO PENDENTE]**
7. **Evidência:** `.summary.json` do k6, `execucoes.csv` com o início e o fim em UTC de cada execução, logs, métricas exportadas (`export-azure-metrics.ps1`), custo real e os dados do cold start, tudo em `results\azure` (fora do Git). Depois, apagar o grupo de recursos.

## Scripts (ESCRITOS, não testados)

| Script | Uso |
|---|---|
| `run-battery.ps1` | `-DryRun` mostra o plano e a estimativa; `-Levels smoke -Repetitions 1 -Tag teste` é o teste curto (cerca de 5 min); `-Levels low,medium -Tests read -Repetitions 1 -Tag calibracao`; `-Levels high -Tests read,write -Repetitions 1 -Tag calibracao-high`; `-Repetitions 3 -Tag campanha` |
| `cold-start.ps1` | `-Rounds 1 -IdleMinutes 1 -Tag cold-teste` é o teste curto; `-Rounds 5 -IdleMinutes 15 -Tag coldstart` |
| `export-azure-metrics.ps1` | chamado ao fim da campanha; inclui 5 métricas do banco (`tps` veio sem dados e foi removida) |
| `consolidar-resultados.ps1` | `-Tag <nome>` junta o `execucoes.csv` e os `.summary.json` em `consolidado-por-execucao.csv` e `consolidado-por-grupo.csv` (**ESCRITO, não testado**) |

## Resultados dos testes curtos e da calibração (informados pelo autor, 27/11/2026)

Relógio do PC sincronizado com o servidor antes dos testes (mesmo segundo).

**Teste curto da campanha** (`smoke`, 4 execuções): todas com código de saída 0, 0% de falhas, 0 descartadas; o CSV de execuções, os logs e as métricas foram gerados. O campo de latência não apareceu no resumo impresso de uma execução (Functions, leitura); o `.summary.json` guarda os valores. A estimativa do script (60 s de sobrecarga por execução) estava alta: a sobrecarga real foi de cerca de 1 s; o script foi ajustado.

**Teste curto do cold start** (1 rodada, 1 minuto de espera): o script funcionou; as métricas mostraram réplica (2) e instância (1) nos 6 minutos anteriores, então **não foi cold start** e os tempos (Container App 606 ms na primeira requisição; Functions 1.948 ms; depois 35 a 46 ms) não são resultado do experimento.

**Calibração, leitura, uma repetição** (resumos do k6):

| Nível | Alvo | Iterações | Descartadas | Mediana / p95 / p99 (ms) | Máximo (ms) |
|---|---|---|---|---|---|
| low (20/s) | Container App | 2.401 | 0 | 20,3 / 27,3 / 31,6 | 77,6 |
| low (20/s) | Functions | 2.401 | 0 | 25,7 / 34,6 / 41,6 | 86,9 |
| medium (100/s) | Container App | 17.997 | 3 | 18,6 / 26,3 / 32,7 | 269,0 |
| medium (100/s) | Functions | 17.996 | 4 | 22,6 / 33,1 / 51,3 | 980,5 |

Todas com 0% de falhas e checks 100%. As duas execuções `medium` terminaram com código 99 por causa do limite informativo de 0 descartadas.

- **Descartadas no `medium`:** 3 e 4 iterações em cerca de 18.000. O k6 usou no máximo 13 e 14 usuários virtuais (limite de 300), então não faltaram usuários: eles foram criados sob demanda no início, porque só 10 estavam pré-alocados. Ajuste feito em `k6/lib/common.js`: pré-alocar `taxa / 5` usuários (medium 20; high 60), em vez de `taxa / 10`. Vale para os dois alvos.
- **Escala observada (`medium`):** Container App chegou a **5 réplicas** (o máximo configurado); Functions ficou em **1 instância**. Uma diferença de comportamento de escala; não é conclusão de desempenho.
- **Banco no `medium`, leitura (exportação de 28/11/2026, UTC):** `cpu_percent` máximo 21,8%, `memory_percent` 17,4%, `active_connections` máximo 30 (limite 859), `connections_failed` 0, `disk_iops_consumed_percentage` máximo 23%: o banco **não** foi gargalo.

**Calibração do `high` (leitura e escrita, uma repetição, informada pelo autor em 27/11/2026; janela de métricas 01:43Z a 02:02Z):**

| Teste | Alvo | Iterações (esperado cerca de 54.000) | Descartadas | VUs máx. | Mediana / p90 / p95 / p99 (ms) | Máximo (ms) |
|---|---|---|---|---|---|---|
| leitura | Container App | 53.568 | 433 | 491 | 22,2 / 412,1 / 1.031,1 / 1.798,7 | 5.480,9 |
| leitura | Functions | 53.924 | 77 | 137 | 27,6 / 59,1 / 99,1 / 342,4 | 3.147,5 |
| escrita | Container App | 53.913 (161.739 requisições, 898,5 por segundo) | 88 | 148 | 17,3 / 21,2 / 28,7 / 66,6 | 695,3 |
| escrita | Functions | 53.813 (161.439 requisições, 896,9 por segundo) | 188 | 246 | 23,1 / 33,3 / 45,8 / 153,0 | 3.010,4 |

Todas com 0% de falhas e checks 100%; todas com código 99 (descartadas e, na leitura do Container App, p95 acima de 1.000 ms, limites informativos).

- **Métricas da janela:** Container App com até 5 réplicas; Functions com até 2 instâncias; banco com `cpu_percent` máximo de **78,2%**, `memory_percent` 23,6%, `connections_failed` 0 e `disk_iops_consumed_percentage` 11%.
- **Leitura no Container App:** a primeira execução do `high` teve cauda de latência alta (mediana 22,2 ms, mas p95 de 1.031 ms) e 491 usuários virtuais; a causa **não foi determinada** (hipóteses não testadas: subida de réplicas com JVM fria, pico de CPU do banco, efeito de ser a primeira execução). A escrita no mesmo alvo teve p95 de 28,7 ms.
- **Conexões no banco:** `active_connections` chegou a **176**, contra cerca de 35 esperadas (5 réplicas e 2 instâncias, 5 conexões cada, como nas execuções anteriores: 14 no smoke e 30 no medium). **Não explicado**; a série por minuto (comando abaixo) permite atribuir ao alvo e ao teste.
- **Gerador:** no máximo 491 usuários virtuais (limite de 1.000) e 900 requisições por segundo sustentadas na escrita; não há sinal de saturação do PC, mas a CPU e a rede do PC **não foram medidas**.
- **Descartadas:** o k6 perdeu de 77 a 433 iterações (0,14% a 0,8%) por criar usuários sob demanda, mesmo com 60 pré-alocados; a pré-alocação passou a ser igual à taxa (medium 100, high 300), para os dois alvos.

**Decisões do autor (27/11/2026):** 3 repetições; campanha principal no sábado, 28/11 (cerca de 2 h); cold start no domingo, 29/11.


## Calendário proposto (a ajustar pelo autor)

| Dia | Atividade |
|---|---|
| 27/11 (sexta) | sincronizar o relógio; ligar o banco; teste curto dos dois scripts; calibração |
| 28/11 (sábado) | campanha principal (cerca de 2 h 30 min) |
| 29/11 (domingo) | cold start (cerca de 80 min, sem usar o PC para acessar as APIs); exportar métricas; conferir custo |
| 30/11 e 01/12 | análise e escrita dos resultados; parar o banco; decidir o momento de apagar o grupo de recursos |
| 02/12 | entrega do TCC-I |
