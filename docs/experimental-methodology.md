# Metodologia experimental

Status: **PLANEJADO.** Nada abaixo foi executado. Valores marcados "a definir" serão fechados
antes da Etapa 10 e registrados aqui com data.

## 1. Objetivo e questões de pesquisa

Comparar Azure Container Apps (container) e Azure Functions (Flex Consumption, ADR-007)
em **desempenho, escalabilidade e custo**, executando a mesma lógica de negócio.

## 2. Variáveis

| Tipo | Variável |
|---|---|
| Independente | Arquitetura de implantação (container vs. serverless) |
| Dependente | Latência (p50/p95/p99), vazão (req/s), taxa de erro, instâncias/réplicas, custo |
| Controladas | Mesmo `core`, mesmos endpoints, mesmo PostgreSQL (mesma região e dimensionamento), mesmo conjunto de dados, mesmos scripts k6, mesmos horários aproximados |
| **Não controláveis (confundidoras)** | Framework (Spring Boot vs. runtime Functions), política de escala, modelo de cobrança, tamanho/modelo de recurso por instância, ruído da rede e da plataforma |

## 3. Cenários de cold start (ADR-012)

| Cenário | Container Apps | Functions (Flex) |
|---|---|---|
| **Aquecido** | réplicas mínimas ≥ 1 | ≥ 1 instância sempre pronta |
| **Frio (escala a zero)** | réplicas mínimas = 0 (valor padrão segundo a documentação) | nenhuma instância sempre pronta |

**Medição do cenário frio (procedimento proposto):**
1. Garantir antes do teste que o serviço está em zero réplicas/instâncias (confirmar por métrica do Azure Monitor, não por suposição).
2. Aguardar o tempo ocioso necessário (a verificar na documentação o prazo de escala a zero de cada serviço e **registrar o valor usado**).
3. Enviar **uma** requisição a um endpoint de leitura que acesse o banco (inclui criação do pool) com k6, anotando `http_req_duration` e `http_req_waiting`.
4. Repetir N vezes, cada uma após nova espera ociosa (N a definir; é limitado por tempo e custo).
5. Repetir com `/health` (sem banco) para separar custo de inicialização do runtime do custo de abrir conexão com o banco.

Cenário aquecido: mesma requisição, após aquecimento explícito, para servir de linha de base.

**Configurações a registrar por cenário:** réplicas mínimas/máximas, instâncias sempre prontas, tamanho de memória/CPU, regra de escala (ex.: requisições HTTP simultâneas por réplica), concorrência por instância, variáveis de ambiente relevantes.

## 4. Controle do PostgreSQL

- **Conexões:** pool pequeno e **configurável por variável de ambiente** (`DB_POOL_MAX`, nome provisório). Regra: `máximo de instâncias × tamanho do pool` deve ficar abaixo do limite de conexões do servidor, com folga. Por isso o **máximo de réplicas/instâncias** será limitado explicitamente nos dois ambientes.
- **Dados equivalentes:** conjunto inicial **fixo e determinístico** (UUIDs e conteúdo gerados por script, mesmo tamanho nos dois testes — tamanho a definir). Carga e limpeza por scripts versionados em `db/` (Etapa 3).
- **Preparação antes de cada rodada:** esvaziar a tabela, recarregar o conjunto inicial, atualizar estatísticas do banco, conferir contagem de linhas.
- **Limpeza depois:** testes de escrita usam marcador identificável nos dados criados e/ou reset completo ao final.
- **Interferência do banco:** coletar CPU, conexões ativas e latência do banco durante cada rodada; **rodadas com banco saturado são sinalizadas** e analisadas à parte (limite de saturação a definir). Assim não se atribui à arquitetura uma lentidão que é do banco.
- **Execução sequencial:** nunca testar os dois ambientes ao mesmo tempo; intervalo ocioso entre rodadas; mesma ordem de cenários nos dois lados; alternar qual ambiente roda primeiro entre repetições, para diluir efeito de horário.

## 5. Cenários de carga (k6, Etapa 7)

Leitura (listar, consultar, pesquisar) e escrita (cadastrar/atualizar/excluir) em scripts **separados**; níveis leve, intermediário e elevado **configuráveis** (valores finais ajustados aos limites medidos). Escrita com volume limitado e ISBNs únicos por iteração. Thresholds servem para verificar critérios por experimento, **não** como prova de superioridade.

## 6. Coleta e preservação de dados

- Resultados brutos do k6 preservados sem alteração (exportação de resumo e de série temporal), em pasta `load-tests/results/raw/` (a política de versionamento será decidida na Etapa 7).
- Metadados por rodada: data/hora, versões, configuração dos serviços, região, repetição.
- Estatística: múltiplas repetições (número a definir); reportar mediana e dispersão, não apenas uma execução.

## 7. Custos (diferenciar medido de estimado)

Custos **medidos** vêm da ferramenta de custos do Azure para o período e os recursos de cada teste; **estimativas** (calculadora de preços) ficam em seção separada e rotuladas. Custos compartilhados (banco, monitoramento) são tratados à parte para não distorcer a comparação.

## 8. Ameaças à validade (preliminar)

Framework diferente; planos com modelos de recurso e cobrança diferentes; local do cliente de carga; ruído de plataforma compartilhada; limite do banco; poucas repetições; metas de escala diferentes por padrão entre os serviços.


## 9. Versões e ambiente (registrado até a Etapa 3)

| Item | Valor | Fonte |
|---|---|---|
| JDK de desenvolvimento | Oracle Java 21.0.9 LTS | Saída do autor, 11/10/2026 |
| Maven | 3.9.11 | Saída do autor, 11/10/2026 |
| Docker / Compose | 29.8.2 / v5.5.1 | Saída do autor, 11/10/2026 |
| Spring Boot | 4.1.1 (ADR-014) | Configuração do POM |
| PostgreSQL local | imagem `postgres:17` — **registrar `SELECT version();`** | Docker Compose |
| HikariCP, driver PostgreSQL, Tomcat | gerenciados pelo BOM — **registrar com `mvn dependency:tree`** | A preencher |
| Pool de conexões | máx. 5, mín. ocioso 1 (padrões; ajustáveis por variável de ambiente) | `DatabaseConfig` |
| Conjunto de dados | 10000 livros determinísticos (`db/seed.sql`), 1 em cada 10 sem ISBN | `db/seed.sql` |

### Pendências de registro de ambiente (a preencher antes dos experimentos)

- Versão do Windows PowerShell/PowerShell do autor (`$PSVersionTable.PSVersion`).
- Saída de `SELECT version();` no PostgreSQL local e versões efetivas de HikariCP, driver PostgreSQL e Tomcat (`mvn dependency:tree`).
- `DB_CONNECTION_TIMEOUT_MS` (padrão 5000 ms) define o tempo máximo até um erro de banco ser reportado; deve ser fixado e registrado para os dois ambientes **[DECISÃO a tomar]**.
- A ordenação das listagens usa `lower(titulo)` na **collation** do banco; conferir e igualar a collation do PostgreSQL local e a do serviço no Azure antes dos experimentos **[a verificar]**.
