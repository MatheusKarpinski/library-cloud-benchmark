# Registro de decisões arquiteturais (ADRs)

Campos de cada ADR: contexto · decisão · justificativa · alternativas · consequências
(vantagens/desvantagens) · impacto na comparação · fontes · situação.
Se uma decisão mudar, **não apague**: marque como "substituída" e crie um novo ADR.
Acesso às fontes online: 11/10/2026.

---
## ADR-001 — Java 21 como linguagem (11/10/2026) — **ACEITA**
- **Contexto:** escolher a linguagem comum aos dois ambientes; o autor já conhece Java/Spring Boot, tem pouca experiência com Azure.
- **Decisão:** Java 21 (LTS).
- **Justificativa:** o autor domina a linguagem (reduz risco de erro de implementação); a documentação do Azure Functions lista o Java 21 como GA e o descreve como a última versão Java aceita no plano Linux Consumption **[DOC]**.
- **Alternativas:** Node.js/TypeScript, .NET, Python (maior curva de aprendizado; ganho principal seria cold start menor — **[HIPÓTESE]** não verificada). Java 25 (aparece como GA em algumas versões da página de suporte e como *preview* em outras; não adotado para evitar variável extra).
- **Consequências:** (+) código que o autor consegue defender; (−) JVM tende a custar mais na inicialização, o que deve ser tratado como característica da linguagem na análise de cold start.
- **Impacto na comparação:** a mesma linguagem nos dois lados elimina a linguagem como variável; o efeito do Java sobre o cold start afeta os dois ambientes, de formas possivelmente diferentes.
- **Fontes:** Microsoft Learn, "Azure Functions runtime versions" — https://learn.microsoft.com/azure/azure-functions/functions-versions

## ADR-002 — Maven multi-módulo (11/10/2026) — **ACEITA**
- **Contexto:** compartilhar código entre dois adaptadores sem duplicar regras.
- **Decisão:** POM pai + módulos `core`, `infrastructure`, `adapter-container`, `adapter-functions` (os três últimos entram nas próximas etapas).
- **Justificativa:** o `core` sem dependências de produção garante, pela própria ferramenta de build, que a regra de negócio não enxerga Spring nem Azure.
- **Alternativas:** projeto único com pacotes (separação só por convenção); Gradle (o autor conhece Maven/Spring; Maven é o padrão do plugin do Functions).
- **Consequências:** (+) separação verificável; (−) mais arquivos de configuração.
- **Impacto na comparação:** garante que ambos usam exatamente o mesmo código de negócio.
- **Fontes:** —

## ADR-003 — PostgreSQL compartilhado (11/10/2026) — **ACEITA**
- **Contexto:** as duas implantações precisam de dados equivalentes.
- **Decisão:** um PostgreSQL gerenciado no Azure, mesma região, mesmo conjunto de dados; PostgreSQL local via Docker para desenvolvimento.
- **Justificativa:** persistência real, reproduzível por scripts SQL; permite isolar a latência do banco como fator constante.
- **Alternativas:** dados em memória/arquivo (não representam persistência real, e a escala horizontal quebraria a consistência entre instâncias).
- **Consequências:** (+) equivalência; (−) o banco pode virar gargalo e é um custo fixo; os testes precisam ser sequenciais (ver `experimental-methodology.md`).
- **Impacto na comparação:** banco igual para ambos; limite de conexões pode limitar a escala observada (ameaça à validade).
- **Fontes:** —

## ADR-004 — JDBC puro, sem JPA (11/10/2026) — **ACEITA**
- **Contexto:** o código de acesso a dados precisa rodar igual nos dois adaptadores.
- **Decisão:** JDBC com pool de conexões, sem Spring Data/JPA/Hibernate.
- **Justificativa:** evita carregar o framework de persistência no Functions e que o cold start passe a medir o peso do framework.
- **Alternativas:** Spring Data JPA (familiar ao autor, porém acopla a persistência ao Spring).
- **Consequências:** (+) mesma implementação nos dois lados; (−) mais código SQL manual.
- **Impacto na comparação:** reduz uma diferença de framework entre os ambientes.
- **Fontes:** —

## ADR-005 — Spring Boot no adaptador de container (11/10/2026) — **ACEITA**
- **Contexto:** escolher o framework HTTP do container.
- **Decisão:** Spring Boot, por preferência do autor (concentrar esforço em compreender o projeto).
- **Justificativa:** familiaridade do autor.
- **Alternativas:** framework HTTP leve (ex.: Javalin) — diminuiria a diferença para o Functions, mas exigiria aprendizado.
- **Consequências:** (+) menos risco de implementação; (−) o Spring tem inicialização e consumo de memória próprios.
- **Impacto na comparação:** **ameaça à validade**: diferenças de latência/cold start/memória podem vir do framework e não apenas da arquitetura. Toda análise deve declarar isso.
- **Fontes:** —

## ADR-006 — Estratégia de testes automatizados (11/10/2026) — **ACEITA** (JUnit/AssertJ) · **PROPOSTA** (Testcontainers)
- **Decisão:** JUnit 5 + AssertJ nos testes unitários, com repositório em memória **somente de teste**. Para persistência real, Testcontainers com PostgreSQL (Etapa 4; exige Docker Desktop).
- **Alternativas:** Mockito (adiciona biblioteca sem necessidade: os fakes manuais bastam); H2 como "PostgreSQL de teste" (comportamento diferente do banco real).
- **Impacto na comparação:** nenhum direto; aumenta a confiança de que ambos se comportam igual.
- **Fontes:** versões do JUnit/AssertJ conferidas em repositórios públicos de dependências (ver `tcc-notes.md`).

## ADR-007 — Plano do Azure Functions: Flex Consumption (11/10/2026) — **PROPOSTA** (verificações pendentes)
- **Contexto:** escolher o plano serverless para o Java 21.
- **Decisão proposta:** Flex Consumption.
- **Justificativa [DOC]:** a documentação o descreve como o plano serverless recomendado, com escala a zero e opção de instâncias "sempre prontas" para reduzir cold start; a hospedagem Linux no plano Consumption tem retirada anunciada para depois de 30/09/2028, com orientação de migrar para o Flex.
- **Alternativas:** Consumption (legado), Premium (custo fixo maior, fora do foco "serverless puro").
- **Pendências antes de fechar (não verificadas ainda):** disponibilidade na região escolhida (ADR-011); limites de escala e de concorrência; tamanhos de instância; preços atuais (consultar a página de preços e a calculadora oficial — nenhum valor será inventado).
- **Consequências:** cenário "aquecido" = ≥1 instância sempre pronta (cobrança adicional de linha de base); cenário "frio" = 0 instâncias sempre prontas.
- **Impacto na comparação:** define o modelo de cobrança e de cold start do lado serverless.
- **Fontes:** https://learn.microsoft.com/azure/azure-functions/flex-consumption-plan · https://learn.microsoft.com/azure/azure-functions/functions-consumption-costs · https://learn.microsoft.com/azure/azure-functions/supported-languages

## ADR-008 — Identificador UUID gerado na aplicação (11/10/2026) — **PROPOSTA** (implementada na Etapa 2)
- **Decisão:** `id` é um `UUID` criado pelo núcleo.
- **Justificativa:** independe de sequência do banco; os dados iniciais podem ter ids fixos e idênticos em todos os ambientes (reprodutibilidade).
- **Alternativas:** `BIGSERIAL` (ids dependem da ordem de inserção; mais difícil de reproduzir).
- **Consequências:** (−) índice por UUID um pouco maior; irrelevante para este volume.
- **Impacto na comparação:** neutro.

## ADR-009 — Um único `BookService` (11/10/2026) — **PROPOSTA** (implementada na Etapa 2)
- **Decisão:** uma classe de serviço de aplicação com um método por caso de uso, em vez de uma classe por caso de uso.
- **Justificativa:** MVP; a composição manual no Functions fica trivial (um objeto); menos arquivos para explicar na banca.
- **Alternativas:** uma classe por caso de uso (mais purista, mais cerimônia).
- **Impacto na comparação:** nenhum; ambos chamam a mesma classe.

## ADR-010 — Regras de validação e limites de campos (11/10/2026) — **PROPOSTA** (implementada na Etapa 2)
- **Decisão:** regras em `Book` (ver `implementation.md`): títulos até 200, autor 150, categoria 80, ano de 1000 a (atual+1), ISBN opcional com verificação apenas de formato, paginação padrão 20 e máximo 100.
- **Justificativa:** uma só fonte de verdade para os dois adaptadores; paginação evita listagens ilimitadas nos testes de carga.
- **Consequências:** o esquema do banco (Etapa 3) deve espelhar os limites.
- **Impacto na comparação:** garante respostas funcionalmente equivalentes.

## ADR-011 — Região do Azure — **PENDENTE**
- **Critérios a verificar na documentação antes de decidir:** disponibilidade de Container Apps, Functions Flex Consumption e PostgreSQL gerenciado na mesma região; proximidade do cliente k6; preço; cotas da assinatura.
- **Risco:** o local de execução do k6 (máquina local vs. máquina virtual na mesma região) altera a latência de rede medida; a escolha deve ser documentada.

## ADR-012 — Cenários de cold start (11/10/2026) — **PROPOSTA**
- **Decisão:** dois cenários por ambiente, "aquecido" e "frio (escala a zero)", executados só se couberem no orçamento. Detalhes em `experimental-methodology.md`.
- **Alternativas:** apenas um cenário (perde o contraste que o TCC quer analisar).
- **Impacto na comparação:** a configuração de instâncias mínimas/sempre prontas é uma variável experimental e tem custo próprio.
- **Fontes:** https://learn.microsoft.com/azure/container-apps/scale-app · https://learn.microsoft.com/azure/azure-functions/flex-consumption-plan

## ADR-013 — Observabilidade escalonada (11/10/2026) — **ACEITA**
- **Decisão:** k6 + Azure Monitor primeiro; Prometheus/Grafana só em etapa posterior, se justificado.
- **Justificativa:** evitar integração decorativa: cada ferramenta de monitoramento só entra se houver benefício real para a comparação.


---
## ADR-014 — Spring Boot 4.1.x e uso do BOM em todo o projeto (11/10/2026) — **PROPOSTA** (implementada na Etapa 3; detalha o ADR-005)
- **Contexto:** o ADR-005 escolheu Spring Boot; faltava escolher a versão. O OSS do Spring Boot 3.5 terminou em 30/06/2026 e o 4.0 perde OSS em dezembro de 2026 **[DOC]**; o 4.1 é a série mantida mais recente.
- **Decisão:** Spring Boot **4.1.1** (versão mais recente encontrada na consulta), com Java 21. O BOM `spring-boot-dependencies` é importado no POM pai para **todos** os módulos, inclusive os que o Functions reutilizará; assim HikariCP e o driver PostgreSQL têm a mesma versão nas duas implantações. O JUnit continua fixado em 5.13.1 (validado na Etapa 2) porque é declarado antes do BOM.
- **Justificativa:** versão mantida; um único catálogo de versões evita divergência entre adaptadores.
- **Alternativas:** Spring Boot 3.5 (suporte OSS encerrado); 4.0 (suporte encerra em dez/2026); fixar versões de Hikari/driver manualmente (risco de números errados).
- **Consequências:** (+) menos versões a manter; (−) Spring Boot 4 é recente (Spring Framework 7, Jackson 3, starters modulares): a documentação e os exemplos online misturam 3.x e 4.x. O starter correto é `spring-boot-starter-webmvc` **[DOC]**.
- **Impacto na comparação:** mesma versão de pool e driver nos dois lados; o framework segue como ameaça à validade (ADR-005).
- **Fontes:** https://github.com/spring-projects/spring-boot/wiki/Supported-Versions · https://spring.io/blog/2026/08/20/spring-boot-4-1-1-available-now/ · repositório Maven Central (starter `spring-boot-starter-webmvc`).

## ADR-015 — Módulo compartilhado `api-contract` (11/10/2026) — **PROPOSTA**
- **Decisão:** DTOs e a tradução exceção → HTTP ficam num módulo Java puro, usado pelos dois adaptadores.
- **Justificativa:** evita que Spring e Functions respondam erros de forma diferente por acidente; reduz o que a Etapa 5 precisa reescrever.
- **Alternativas:** duplicar DTOs e handlers em cada adaptador (risco de divergência).
- **Consequências:** (+) respostas idênticas por construção; (−) um módulo a mais. A serialização JSON continua a cargo de cada adaptador (será revisada na Etapa 5).

## ADR-016 — PostgreSQL 17 local e política do pool (11/10/2026) — **PROPOSTA**
- **Decisão:** imagem `postgres:17` no Docker Compose; pool HikariCP com `DB_POOL_MAX=5` e `DB_POOL_MIN_IDLE=1` por padrão, configuráveis; o pool não conecta na criação.
- **Justificativa:** o PostgreSQL 17 e o 18 são suportados no serviço gerenciado do Azure **[DOC]**; o 17 é a escolha conservadora. A tag `postgres:17` acompanha a versão menor mais recente: a versão efetiva deve ser registrada (`SELECT version();`). Mínimo ocioso baixo evita que muitas instâncias serverless segurem conexões desnecessárias.
- **Alternativas:** PostgreSQL 18; pool fixo (min = max).
- **Consequências:** o limite de conexões do servidor e o máximo de instâncias devem ser dimensionados juntos (ver metodologia).
- **Fontes:** https://learn.microsoft.com/azure/postgresql/flexible-server/concepts-supported-versions

## ADR-017 — OpenAPI escrito à mão em vez de springdoc (11/10/2026) — **PROPOSTA**
- **Decisão:** `docs/openapi.yaml` mantido manualmente; sem springdoc/Swagger UI no adaptador.
- **Justificativa:** o springdoc só funciona no Spring, não no Functions; carregá-lo só de um lado alteraria o peso e a inicialização do container (variável de confusão). O springdoc 3.x é a linha para Spring Boot 4, mas não foi verificado com o 4.1 **[DOC parcial]**.
- **Alternativas:** springdoc no container (documentação gerada, porém diferente entre ambientes).
- **Consequências:** (−) a especificação pode divergir do código — será conferida nos testes da Etapa 4.

## ADR-018 — Contrato de erros e endpoints de saúde (11/10/2026) — **PROPOSTA**
- **Decisão:** corpo de erro `{code, message, violations[]}` sem data/pilha; `PersistenceFailureException` → 503, demais inesperadas → 500, sem expor detalhes técnicos. `/health` (liveness, sem banco) e `/health/ready` (readiness, com banco).
- **Justificativa:** respostas determinísticas facilitam comparar os adaptadores; separar saúde sem/com banco permite medir o cold start do runtime separadamente do custo de abrir conexão (ver metodologia).
- **Impacto na comparação:** padroniza respostas; erros de rotas inexistentes seguem o formato do framework (diferença documentada).

---
## Registro de validação (Etapa 3, 02/11/2026)

| ADR | Situação | Evidência |
|---|---|---|
| ADR-014 | PROPOSTA — validada funcionalmente | Spring Boot 4.1.1 compilou e a API iniciou e respondeu (`testing.md`) |
| ADR-015 | PROPOSTA — validada funcionalmente | Corpos de erro idênticos ao contrato em todos os casos testados |
| ADR-016 | PROPOSTA — validada funcionalmente | PostgreSQL 17 em container, pool 5/1, recuperação após queda do banco |
| ADR-017 | PROPOSTA — parcialmente validada | `openapi.yaml` ainda não conferido contra o código (Etapa 4) |
| ADR-018 | PROPOSTA — validada funcionalmente | 400/404/409/503 e separação `/health` × `/health/ready` observados |

Correção registrada no ADR-018: classes do `api-contract` não devem ter métodos `getX()`/`isX()` extras (campo `up`, ver `troubleshooting.md` T-002).
As propostas continuam aguardando aprovação do autor.
