# Notas técnicas para a redação do TCC

Estas notas **não** são texto final. Cada item indica o tipo de afirmação. Acessos em 11/10/2026.
Revisar as fontes diretamente antes de citar; as páginas da Microsoft mudam com frequência.

## A. Hospedagem do Azure Functions

| # | Afirmação | Tipo | Fonte |
|---|---|---|---|
| A1 | O Azure Functions suporta apenas a versão 4.x do runtime; as 2.x e 3.x não são mais suportadas | [DOC] | Microsoft Learn — Azure Functions runtime versions (https://learn.microsoft.com/azure/azure-functions/functions-versions) |
| A2 | Java 21 é GA no Functions e é a última versão Java aceita no plano Linux Consumption | [DOC] | idem; Supported languages (https://learn.microsoft.com/azure/azure-functions/supported-languages) |
| A3 | A hospedagem Linux no plano Consumption será retirada após 30/09/2028; orientação de migrar para Flex Consumption | [DOC] | Supported languages (conteúdo localizado em página espelho da documentação; reconferir na versão em inglês antes de citar) |
| A4 | Flex Consumption é o plano serverless recomendado; tem escala a zero e permite instâncias "sempre prontas" para reduzir cold start | [DOC] | https://learn.microsoft.com/azure/azure-functions/flex-consumption-plan |
| A5 | No Flex, cobra-se tempo de execução das instâncias; com instâncias sempre prontas há também cobrança de linha de base (GB-segundos) | [DOC] | https://learn.microsoft.com/azure/azure-functions/functions-consumption-costs |
| A6 | Existem métricas do Azure Monitor ligadas à cobrança de instâncias sempre prontas | [DOC] | idem |

## B. Container Apps

| # | Afirmação | Tipo | Fonte |
|---|---|---|---|
| B1 | A escala horizontal é regida por regras declarativas; há regra HTTP baseada em requisições simultâneas por réplica | [DOC] | https://learn.microsoft.com/azure/container-apps/scale-app |
| B2 | O número mínimo de réplicas por revisão tem padrão 0 (escala a zero) | [DOC] | idem |

## C. Ferramentas de build e teste

| # | Afirmação | Tipo | Fonte |
|---|---|---|---|
| C1 | A página oficial de download do Maven indicava a série 3.9 como estável recomendada (a versão exata varia entre as capturas consultadas: 3.9.9 a 3.9.11); a série 4.x aparecia como prévia | [DOC] | https://maven.apache.org/download.cgi |
| C2 | JUnit 5.13.1, AssertJ 3.27.3 e maven-compiler-plugin 3.14.0 apareciam em arquivos de projetos públicos; maven-surefire-plugin 3.5.3 foi escolhido sem conferência individual | [DECISÃO] | arquivos públicos de dependências (Maven Central/GitHub); revisar versões ao rodar o build |

## D. Decisões de projeto (não são recomendações da literatura)

Java 21; Maven multi-módulo; JDBC puro; Spring Boot no container; UUID gerado na aplicação; serviço único; paginação padrão 20/máx. 100. Ver ADRs.

## E. Hipóteses a testar (sem comprovação ainda)

- H1: o cold start do Java no Functions e a inicialização do Spring Boot no Container Apps contribuem de forma diferente para a latência da primeira requisição.
- H2: o limite de conexões do PostgreSQL pode limitar a escala observada antes da plataforma.

## F. Resultados medidos

Nenhum até o momento.
