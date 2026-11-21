# Plano de recursos do Azure para estimativa de custos

Status: **PLANEJADO** (nenhum recurso foi criado; este documento não contém preços).

Objetivo: listar os recursos e as quantidades de cada etapa, para montar a estimativa na calculadora
oficial e levar um número concreto ao orientador **antes** de qualquer aprovação de custo.

Regra do projeto: nenhum recurso pago é criado sem confirmação explícita do autor, depois da lista de
recursos, dos custos possíveis e da conferência do crédito disponível.

## 1. Situação da assinatura (informado pelo autor, 21/11/2026)

- A assinatura usada em uma disciplina no ano anterior expirou e foi excluída; não há assinatura no portal.
- A renovação do Azure for Students e a inscrição no Azure for Students Starter foram negadas
  ("não qualificado").
- Opções em avaliação: recurso institucional (orientador/faculdade), suporte de cobrança da Microsoft,
  conta gratuita (exige cartão de crédito no Brasil e é limitada a novos clientes) e pagamento conforme o uso.
  Nenhuma foi aprovada. Fontes: `tcc-notes.md`, C35.

## 2. Convenções

- **[DOC]** fato obtido na documentação da Microsoft (ver `tcc-notes.md`, C31 a C36).
- **[DECISÃO PENDENTE]** valor que o autor ainda precisa escolher (provável novo ADR).
- **[HIPÓTESE]** valor de planejamento para a calculadora, sem medição; precisa ser substituído por medição.
- **[MEDIDO]** observado pelo autor.

## 3. Recursos por etapa

### Etapa 8: monitoramento (depende dos recursos da Etapa 9)

| Recurso | Qtd | Parâmetros para a calculadora | Observação |
|---|---|---|---|
| Espaço de trabalho do Log Analytics | 1 | ingestão em GB/mês; retenção padrão | métricas de plataforma gratuitas; primeiros 5 GB/mês de logs gratuitos por conta de cobrança [DOC, C34]. Volume real: [HIPÓTESE] abaixo de 5 GB/mês com logs mínimos |
| Application Insights (baseado em workspace) | 1 (Functions) | mesmo workspace | cobrado pelas mesmas tarifas de log [DOC, C34] |
| Configuração de diagnóstico (AllMetrics) | 1 por recurso monitorado | envio ao workspace | para preservar as métricas (retenção de 90 dias) [DOC, C34] |

### Etapa 9: implantação e validação funcional (smokes)

| Recurso | Qtd | Parâmetros para a calculadora | Observação |
|---|---|---|---|
| Grupo de recursos | 1 | | contêiner lógico; apagá-lo ao fim de cada bateria remove tudo o que há dentro |
| PostgreSQL servidor flexível (versão 17) | 1 (compartilhado pelos dois ambientes, ADR-003) | camada Burstable B1ms (1 vCore, 2 GiB) [DOC, C33]; armazenamento 32 GiB (mínimo) [DOC, C33]; retenção de backup 7 dias (mínimo) [DOC, C33]; sem alta disponibilidade; região [DECISÃO PENDENTE, ADR-011] | cenários: A) ligado o mês inteiro (730 h); B) ligado só nas janelas de teste (horas = dias x horas/dia, [DECISÃO PENDENTE]). Verificar na calculadora o que continua sendo cobrado com o servidor parado |
| Registro de contêiner | 1 | camada Basic [HIPÓTESE: suficiente]; 1 imagem (444 MB em disco, 120 MB de conteúdo [MEDIDO, `docker image ls`]) | verificar na calculadora o armazenamento incluído e o preço diário da camada |
| Ambiente do Container Apps | 1 | plano Consumption (perfil de carga de trabalho) | |
| Container App | 1 | vCPU e memória por réplica [DECISÃO PENDENTE] (proposta: 1 vCPU e 2 GiB, para equivaler à instância de 2.048 MB do Functions, que tem 1 CPU [DOC, C32]); mínimo de réplicas 0 (cenário frio) e 1 (aquecido); máximo [DECISÃO PENDENTE] | pool de 5 conexões por réplica (ADR-016): máximo de réplicas x 5 deve caber no limite de conexões do servidor (limite por camada: **NÃO VERIFICADO**) |
| Function App (Flex Consumption) | 1 | memória da instância 2.048 MB (padrão) [DOC, C32]; instâncias sempre prontas 0 (frio) e 1 (aquecido); máximo de instâncias [DECISÃO PENDENTE] | disponibilidade do plano na região escolhida: **NÃO VERIFICADA** (ADR-007) |
| Conta de armazenamento do Functions | 1 | Standard, redundância local [HIPÓTESE] | exigida pelo Functions |
| Log Analytics e Application Insights | ver Etapa 8 | | |

Uso na Etapa 9: poucas centenas de requisições (smokes de 150 a 450 requisições por execução [MEDIDO]).

### Etapa 10: experimentos

| Recurso | Qtd | Parâmetros | Observação |
|---|---|---|---|
| Os mesmos recursos da Etapa 9 | iguais | | |
| PostgreSQL | 1 | **camada Burstable não é recomendada para carga sustentada**: com CPU perto do limite os créditos acabam e o servidor pode ficar inacessível [DOC, C33]. Para os níveis `medium` e `high`, considerar General Purpose com o menor número de vCores disponível [DECISÃO PENDENTE] | um banco subdimensionado satura nos dois ambientes e invalida a comparação |
| Gerador de carga (k6) | máquina local ou 1 VM Linux pequena na mesma região | horas = janelas de teste [DECISÃO PENDENTE, ADR-011] | a máquina local não gera custo no Azure, mas a latência de rede entra na medição |

Volume de requisições (aritmética sobre os níveis **provisórios** do k6, ADR-023; a leitura faz 1 requisição
por iteração e a escrita faz 3):

| Nível | Taxa (iterações/s) | Duração | Leitura (req) | Escrita (req) |
|---|---|---|---|---|
| low | 20 | 120 s | 2.400 | 7.200 |
| medium | 100 | 180 s | 18.000 | 54.000 |
| high | 300 | 180 s | 54.000 | 162.000 |
| **Soma por repetição, por ambiente** | | | **74.400** | **223.200** |

- Total por ambiente e por repetição (leitura + escrita, níveis low, medium e high): **297.600 requisições**.
- Com R repetições: R x 297.600 por ambiente. Exemplo [HIPÓTESE] com R = 3: 892.800 por ambiente e
  1.785.600 nos dois. O número de repetições é [DECISÃO PENDENTE] (metodologia).
- Container Apps: 2 milhões de requisições por mês são gratuitas por assinatura; só contam requisições de fora do
  ambiente, e as sondas de saúde não contam [DOC, C31]. Com R = 3, as 892.800 do Container Apps ficam abaixo disso.
- Container Apps, vCPU-segundo (fórmula): réplicas ativas x vCPU x segundos. Exemplo [HIPÓTESE]: 5 réplicas x 1 vCPU
  x 5.760 s (2.880 s de carga com R = 3, dobrados para aquecimento e intervalos) = 28.800 vCPU-s, abaixo dos 180.000
  gratuitos por mês [DOC, C31]. Com 1 réplica mínima o mês inteiro (2.628.000 s), o total passa muito da franquia.
- Functions: a calculadora pede execuções por mês, tempo de execução e memória. O tempo real por execução só virá
  da medição; para uma estimativa conservadora, usar o período mínimo faturável de 1.000 ms por execução [DOC, C32]
  como limite superior [HIPÓTESE], rotulando o resultado como estimativa.
- O cenário de cold start (ADR-012) acrescenta janelas de espera para escalar a zero: o número de janelas e o tempo
  de cada uma ainda não foram definidos [DECISÃO PENDENTE].

### Etapa 11: documentação final

Sem recursos novos. Antes de apagar o grupo de recursos: exportar as métricas e os resultados (a retenção das
métricas de plataforma é de 90 dias [DOC, C34]).

## 4. Como montar a estimativa na calculadora oficial

1. Abrir https://azure.microsoft.com/pricing/calculator/ (a versão em português do Brasil abre pelo seletor de
   idioma do site). Escolher a moeda e, em cada item, a região.
2. Em **Adicionar itens**, abrir as categorias e adicionar um item para cada linha das tabelas acima
   (Databases > Azure Database for PostgreSQL [DOC, C33]; Containers > Container Apps e Container Registry;
   Compute > Azure Functions, escolhendo o plano Flex Consumption se estiver disponível; Storage > Storage Accounts;
   Management > Azure Monitor).
3. Preencher cada item com os parâmetros desta tabela. Rotular cada estimativa com o cenário (por exemplo: "A, validação
   da Etapa 9"; "B, campanha da Etapa 10 com R = 3").
4. A calculadora mostra os preços de acordo com a oferta quando se entra com a conta; sem conta, os valores
   são genéricos.
5. Guardar cada estimativa (exportar ou salvar o link) e registrar o resultado em `deployment.md` como **estimativa**,
   separado dos custos **medidos** pela ferramenta de custos do Azure (metodologia).

## 5. Pendências de decisão que afetam o custo

- Região (ADR-011), com disponibilidade de Container Apps, Functions Flex e PostgreSQL na mesma região.
- Camada, vCores e horas de uso do PostgreSQL, e limite de conexões por camada.
- vCPU e memória por réplica no Container Apps e memória da instância no Functions (equivalência de recursos).
- Máximos de réplicas e de instâncias, e número de repetições.
- Gerador de carga local ou em máquina virtual.
- Segurança do endpoint anônimo do Functions e do ingresso externo do Container Apps (antes de publicar).
