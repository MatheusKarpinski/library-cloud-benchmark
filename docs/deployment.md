# Implantação e custos

Status: execução local **PLANEJADA** (instruções no `README.md` para o núcleo); implantação no Azure **NÃO INICIADA**.

## Regra de aprovação (definida pelo autor)

**Nenhum recurso pago será criado sem confirmação explícita do autor.** Antes de qualquer
implantação (Etapa 9) serão apresentados: lista de recursos, custos possíveis, orçamento
máximo sugerido e plano de remoção.

## Recursos que provavelmente serão provisionados (a confirmar na Etapa 9)

| Recurso | Para quê | Comportamento de custo (qualitativo) |
|---|---|---|
| Grupo de recursos | Agrupar tudo e facilitar a exclusão ao final | Sem custo próprio |
| PostgreSQL gerenciado | Banco compartilhado | Tende a ser o maior custo **fixo** (cobrança enquanto existir); verificar opções de dimensionamento e de pausa |
| Container Apps (ambiente + aplicativo) | Versão em container | Depende de réplicas, CPU/memória e tempo ativo; réplicas mínimas > 0 geram custo contínuo |
| Registro de imagens (ou alternativa gratuita) | Guardar a imagem Docker | A decidir (alternativas a avaliar) |
| Function App (Flex Consumption) + conta de armazenamento associada | Versão serverless | Tempo de execução; instâncias sempre prontas cobram linha de base [DOC] |
| Log Analytics / Application Insights | Observabilidade | Volume de dados ingeridos; limitar retenção e coleta |
| Tráfego de saída | Respostas ao cliente | Verificar na calculadora |

> Nenhum preço é informado aqui. Valores devem vir da página oficial de preços e da
> calculadora de preços do Azure **no momento da decisão**, e os valores reais, da
> ferramenta de custos do Azure após os testes.

## Como verificar crédito e orçamento (a confirmar na Etapa 9)

1. No portal do Azure, abrir **Cost Management + Billing** e conferir a assinatura ativa, o saldo de créditos (quando a assinatura for de estudante/teste) e o histórico de custos. (Os nomes exatos dos menus podem variar.)
2. Criar um **orçamento (Budget)** com alertas por e-mail em porcentagens do limite escolhido.
3. Conferir se a assinatura tem **limite de gastos** que bloqueia recursos ao esgotar o crédito.
4. Planejar a **remoção do grupo de recursos** logo após cada bateria de testes.

## Fontes consultadas

Ver `tcc-notes.md` (preços **não** foram consultados nesta etapa).
