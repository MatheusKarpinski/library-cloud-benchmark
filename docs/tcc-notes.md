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

## C2. Spring Boot, PostgreSQL (Etapa 3)

| # | Afirmação | Tipo | Fonte |
|---|---|---|---|
| C3 | O Spring Boot dá suporte OSS a cada versão menor por pelo menos 12 meses e lança versões a cada seis meses | [DOC] | https://github.com/spring-projects/spring-boot/wiki/Supported-Versions |
| C4 | Em 10/06/2026 foi lançado o Spring Boot 4.1.0; o 4.1.1 saiu em 20/08/2026 | [DOC] | https://spring.io/blog/2026/06/10/spring-boot-4/ · https://spring.io/blog/2026/08/20/spring-boot-4-1-1-available-now/ |
| C5 | O OSS do Spring Boot 3.5 encerrou em 30/06/2026; o 4.0 encerra em 31/12/2026 | [DOC secundária] | artigo de terceiros (issoh.co.jp); **reconferir na página oficial de versões suportadas antes de citar** |
| C6 | No Spring Boot 4, `spring-boot-starter-web` foi renomeado para `spring-boot-starter-webmvc`, que inclui Tomcat e Jackson | [DOC] | Maven Central (descrição e dependências dos dois starters) |
| C7 | O serviço gerenciado de PostgreSQL do Azure suporta, entre outras, as versões 17 e 18 | [DOC] | https://learn.microsoft.com/azure/postgresql/flexible-server/concepts-supported-versions |
| C8 | A linha 3.x do springdoc-openapi é a voltada ao Spring Boot 4 (compatibilidade explícita verificada até o 4.0.x) | [DOC secundária] | notas de versão no GitHub do projeto |

## D. Decisões de projeto (não são recomendações da literatura)

Java 21; Maven multi-módulo; JDBC puro; Spring Boot 4.1.1 no container; UUID gerado na aplicação; serviço único; paginação padrão 20/máx. 100; módulo `api-contract`; PostgreSQL 17; pool 5/1; OpenAPI manual; 503 para falha de persistência. Ver ADRs.

## E. Hipóteses a testar (sem comprovação ainda)

- H1: o cold start do Java no Functions e a inicialização do Spring Boot no Container Apps contribuem de forma diferente para a latência da primeira requisição.
- H2: o limite de conexões do PostgreSQL pode limitar a escala observada antes da plataforma.
- H3: o tempo até uma falha de banco ser reportada ao cliente fica próximo de `DB_CONNECTION_TIMEOUT_MS` (5000 ms por padrão); não medido.

## F. Resultados medidos

Nenhuma medição de **desempenho** até o momento.

**Observações funcionais (Etapa 3, 02/11/2026)** — [MEDIDO como observação funcional, não de desempenho]:
- A API em container responde 400, 404, 409 e 503 conforme o contrato; rota inexistente usa o formato do Spring (diferença entre adaptadores a considerar na comparação).
- Com o PostgreSQL parado, `/health` segue 200 e os endpoints de dados respondem 503; após religar o banco, a API se recupera sem reinício.
- Ver `testing.md` para a tabela completa.
