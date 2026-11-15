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

## C3. Testcontainers e Docker (Etapa 4)

| # | Afirmação | Tipo | Fonte |
|---|---|---|---|
| C9 | O Docker Engine 29 passou a exigir API mínima 1.44; clientes antigos do Testcontainers falham com "client version ... is too old" | [DOC secundária] | Notas de versão do Docker 29 (citadas em fóruns e relatos de terceiros); **conferir em https://docs.docker.com/engine/release-notes/29/** |
| C10 | O Testcontainers 2.0 (e o 2.0.2 em especial) é a linha compatível com Docker 29; o Spring Boot 4.0 traz o Testcontainers 2.0.2 por padrão | [DOC secundária] | Relatos de terceiros e atas de projeto; **reconferir nas notas de versão do Testcontainers e do Spring Boot** |
| C11 | No Testcontainers 2.x os módulos têm prefixo `testcontainers-` e as classes ficam em pacotes por módulo (`org.testcontainers.postgresql.PostgreSQLContainer`, não genérica); o suporte a JUnit 4 foi removido | [DOC secundária] | Notas da versão 2.0.0 do Testcontainers (GitHub) |

## C4. Azure Functions — adaptador Java (Etapa 5; acessos em 14/11/2026)

| # | Afirmação | Tipo | Fonte |
|---|---|---|---|
| C12 | `azure-functions-java-library` 3.2.4 consta no Maven Central com data de 07/03/2026; existe também a 3.3.0 (29/04/2026), **não avaliada** neste projeto | [DOC secundária] | https://mvnrepository.com/artifact/com.microsoft.azure.functions/azure-functions-java-library — **reconferir no próprio Maven Central antes de citar** |
| C13 | `azure-functions-maven-plugin` 1.40.0 consta como a versão mais recente listada, com data de 06/11/2025 | [DOC secundária] | https://mvnrepository.com/artifact/com.microsoft.azure/azure-functions-maven-plugin — **reconferir no Maven Central** |
| C14 | Azure Functions Core Tools 4.16.0 é a versão instalada na máquina do autor | informado pelo autor | — (a versão mais recente disponível **não foi consultada**) |
| C15 | O guia de Java do Functions lista o Java 21 para o runtime 4.x, mas as capturas consultadas divergem: uma lista o Java 21 para Windows e Linux; outra o descreve como *preview* no Linux. O mesmo guia diz que o Java do `pom.xml` deve corresponder ao Java usado no desenvolvimento e nos testes locais, e que `JAVA_HOME` deve apontar para o JDK usado na compilação | [DOC secundária / divergente] | https://learn.microsoft.com/azure/azure-functions/functions-reference-java — **reconferir na versão em inglês antes de citar** |
| C16 | Em projetos Java, o plugin monta `target/azure-functions/<app>` com `host.json`, uma pasta com `function.json` por função, o JAR e `lib/`; as dependências do `pom.xml` são empacotadas pelo `package` | [DOC] | idem |
| C17 | O guia afirma que é necessário configurar uma conta de armazenamento em `local.settings.json` para executar localmente gatilhos de Blob, Fila e Tabela (as funções deste projeto usam apenas gatilhos HTTP) | [DOC] | idem |

## C5. Imagem Docker da API em container (Etapa 6; acessos em 15/11/2026)

| # | Afirmação | Tipo | Fonte |
|---|---|---|---|
| C18 | A imagem oficial `eclipse-temurin` publica a tag `21-jre-noble` (JRE 21 sobre Ubuntu 24.04), para linux/amd64, com cerca de 95 MB comprimidos na listagem consultada (patch 21.0.10_7 na captura) | [DOC secundária] captura da listagem de tags do Docker Hub | https://hub.docker.com/_/eclipse-temurin/tags — **reconferir antes de citar** (tamanho e patch mudam) |
| C19 | A documentação das imagens oficiais lista variantes JRE do Java 21 (`alpine`, `jammy`, `ubi9-minimal` em captura antiga, sem `noble`); as listas mudam com o tempo, por isso a tag e o *digest* efetivos devem ser registrados após cada build | [DOC secundária] | https://github.com/docker-library/docs/blob/master/eclipse-temurin/README.md |
| C20 | Comportamento padrão da JVM sob limite de memória de contêiner (`MaxRAMPercentage`) e efeito de `-XX:MaxRAMPercentage=75.0` | **NÃO CONSULTADO** | Reconferir na documentação do JDK antes de citar; a validação local (Etapa 6, passo 7) mostra o `MaxHeapSize` efetivo |
| C21 | A imagem `maven:3.9-eclipse-temurin-21` para o estágio de build | [NÃO VERIFICADO] a tag aparece em exemplos públicos de Dockerfile Java (ex.: https://oneuptime.com/blog/post/2026-02-08-how-to-speed-up-docker-build-for-javamaven-projects/view); o `docker build` do autor a confirma | — |

## D. Decisões de projeto (não são recomendações da literatura)

Java 21; Maven multi-módulo; JDBC puro; Spring Boot 4.1.1 no container; UUID gerado na aplicação; serviço único; paginação padrão 20/máx. 100; módulo `api-contract`; PostgreSQL 17; pool 5/1; OpenAPI manual; 503 para falha de persistência; testes de integração por etiqueta e perfil Maven; adaptador Functions com lógica HTTP sem dependência do runtime e rotas sem prefixo (ADR-020); Jackson 3 no Functions (ADR-021); imagem Docker multi-estágio com JRE Temurin 21 e `MaxRAMPercentage=75` (ADR-022). Ver ADRs.

## E. Hipóteses a testar (sem comprovação ainda)

- H1: o cold start do Java no Functions e a inicialização do Spring Boot no Container Apps contribuem de forma diferente para a latência da primeira requisição.
- H2: o limite de conexões do PostgreSQL pode limitar a escala observada antes da plataforma.
- H3: o tempo até uma falha de banco ser reportada ao cliente fica próximo de `DB_CONNECTION_TIMEOUT_MS` (5000 ms por padrão); não medido.

## F. Resultados medidos

Nenhuma medição de **desempenho** até o momento.

**Observações funcionais (Etapa 5, 14/11/2026, informado pelo autor)** — [MEDIDO como observação funcional, não de desempenho]:
- O Azure Functions local (Core Tools 4.16.0, runtime 4.1054.250.26428, JDK Oracle 21.0.9, Windows) executou a API Java 21 com Jackson 3.
- Em 42 cenários, as respostas do container e do Functions foram iguais em status e corpo; única diferença observada: `Content-Type` do 204 (`text/plain` no Functions; ausente no container), tratada como diferença inerente (T-010). Registros repetidos com o script ajustado: idênticos.
- O host do Functions entregou `GET /api/books/search` à função de `api/books/{id}`; a escolha passou ao código (T-008).

**Testes automatizados (Etapa 4, informado pelo autor em 12/11/2026)** — `mvn test` (57 testes) e `mvn test -Pintegration` (85 testes) passaram; contagem por módulo observada em 14/11/2026 (core 35, api-contract 11, infrastructure 22, adapter-container 17; soma 85); tempos não registrados. Com a Etapa 5: 104 testes (adapter-functions 19), 0 falhas, 0 erros, 0 ignorados.

**Observações funcionais (Etapa 3, 02/11/2026)** — [MEDIDO como observação funcional, não de desempenho]:
- A API em container responde 400, 404, 409 e 503 conforme o contrato; rota inexistente usa o formato do Spring (diferença entre adaptadores a considerar na comparação).
- Com o PostgreSQL parado, `/health` segue 200 e os endpoints de dados respondem 503; após religar o banco, a API se recupera sem reinício.
- Ver `testing.md` para a tabela completa.
