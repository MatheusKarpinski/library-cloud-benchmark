# Arquitetura

Status: núcleo **TESTADO** · `api-contract`, `infrastructure`, `adapter-container` **TESTADOS** (manualmente na Etapa 3; testes automatizados informados como aprovados na Etapa 4) · `adapter-functions` **TESTADO** localmente (Etapa 5, 14/11/2026).

## Princípio central

Um **núcleo compartilhado** (regras de negócio e casos de uso) e **dois adaptadores finos**
(um por ambiente de execução). Assim a diferença entre as duas implantações fica restrita
à forma de executar e implantar, como exige o objetivo do TCC. **[DECISÃO]**

```
 adapter-container (Spring Boot)      adapter-functions (Azure Functions)
   (TESTADO)                             (TESTADO local, Etapa 5)
        \                                 /
         +--- api-contract (DTOs + erros) -+      Java puro                    <- TESTADO
         +--- infrastructure -------------+      JDBC + PostgreSQL + Hikari    <- TESTADO
                              |
                    core (domain + application)                                <- TESTADO
```

A seta de dependência sempre aponta para dentro: o `core` não conhece ninguém.

## Módulos

| Módulo | Responsabilidade | Dependências de produção | Status |
|---|---|---|---|
| `core` | `Book` e regras (domain); `BookService`, `BookRepository` (porta), exceções, paginação (application) | **nenhuma** | TESTADO |
| `api-contract` | DTOs (`BookRequest`, `BookResponse`, `PageResponse`), `ErrorMapper` (exceção → HTTP), respostas de saúde | `core` | TESTADO (Etapa 3) |
| `infrastructure` | `JdbcBookRepository`, pool HikariCP, `DatabaseConfig` (variáveis de ambiente), `DatabaseHealthChecker` | `core`, HikariCP, driver PostgreSQL | TESTADO (Etapa 3) |
| `adapter-container` | Controllers REST, tratamento de erros, `/health`, montagem dos objetos | `core`, `api-contract`, `infrastructure`, Spring Boot | TESTADO (Etapa 3) |
| `adapter-functions` | Funções HTTP que chamam o mesmo `BookService`; lógica HTTP em `BookApiHandler`, sem dependência do runtime | `core`, `api-contract`, `infrastructure`, biblioteca Azure Functions, Jackson 3 | TESTADO localmente (Etapa 5) |

## Mapa de erros (contrato que os dois adaptadores devem seguir)

| Exceção do núcleo | HTTP |
|---|---|
| `ValidationException` | 400 |
| `BookNotFoundException` | 404 |
| `DuplicateIsbnException` | 409 |
| `PersistenceFailureException` | 503 |
| Qualquer outra exceção | 500 |

A tabela vive em **um único lugar**: `ErrorMapper` (módulo `api-contract`), usado pelos dois adaptadores (ADR-018).

## Diferenças inevitáveis entre os dois adaptadores (registradas na Etapa 5; confirmadas na execução local de 14/11/2026, exceto onde indicado)

| Aspecto | Container Apps | Functions | Pode influenciar |
|---|---|---|---|
| Framework HTTP | Spring Boot (servidor embutido) | Runtime do Azure Functions + biblioteca de bindings | Latência, memória, tempo de inicialização |
| Inicialização | Contexto Spring completo | Host Functions + worker Java; sem Spring | Cold start |
| Erros do próprio framework (rota inexistente, método não permitido) | Formato padrão do Spring | Formato do runtime Functions | Pequena diferença nas respostas de erro de rotas inexistentes |
| Modelo de escala | Regras (ex.: requisições HTTP simultâneas) por réplica | Gerenciada pela plataforma por instância | Escalabilidade |
| Cobrança | Recursos alocados/consumidos por réplica | Tempo de execução (+ instâncias sempre prontas, se houver) | Custo |
| `Content-Type` em respostas sem corpo (204) | Nenhum cabeçalho | `text/plain` (observado em 14/11/2026, T-010) | Nenhum efeito prático |
| Serialização JSON | Mapper configurado pelo Spring Boot | `JsonMapper` do Jackson 3 montado à parte (ADR-021) | Pequenas diferenças de formato (verificadas pelo `api-smoke.ps1`) |
| Erro inesperado (fora das exceções conhecidas) | Tratamento padrão do Spring (formato do framework, status 500) | `500 INTERNAL_ERROR` no formato da aplicação | Corpo da resposta em falhas raras |
| Conversão de `page`/`size` | Conversor numérico do Spring | `Integer.parseInt` após `strip()` | Formatos numéricos incomuns |
| Ciclo de vida do pool de conexões | Fechado no encerramento do contexto Spring | Criado na primeira requisição e não fechado explicitamente | Conexões ao encerrar/escalar para zero |

> Estas diferenças são **ameaças à validade**: os resultados não representam um isolamento
> perfeito do efeito "arquitetura". **[DECISÃO]** de registrar isso em toda análise.

## Comportamentos observados no adaptador de container (validados em 02/11/2026)

- **Ordem das verificações em PUT:** id válido (400) → livro existe (404) → dados válidos (400) → ISBN livre (409). Observado: `PUT` em id inexistente com corpo incompleto responde **404**, não 400.
- **Rota inexistente** (`/api/nada`) responde 404 no formato padrão do Spring: `{"timestamp":...,"status":404,"error":"Not Found","path":...}`, **diferente** do formato da aplicação (`code`, `message`, `violations`). O Azure Functions responderá rotas inexistentes à sua maneira; os testes de carga não devem depender dessas respostas. **[MEDIDO — observação funcional]**
- **Banco indisponível:** `/health` continua 200, `/health/ready` responde 503 e os endpoints de dados respondem 503 `SERVICE_UNAVAILABLE` sem detalhe técnico; o detalhe fica no log. Após religar o banco a API se recupera sem reinício.

## Comportamentos do adaptador Functions (verificados pelo smoke de 14/11/2026, salvo onde indicado)

- **Rotas** iguais às do container: `host.json` zera o `routePrefix`; sem isso o Functions acrescentaria `/api` e as rotas virariam `/api/api/books`.
- **Ordem das verificações** igual à do Spring (corpo antes do serviço; `page` e `size` antes do serviço): detalhes em `implementation.md`.
- **Falha ao montar os objetos** (por exemplo, `DB_*` ausente): `500 INTERNAL_ERROR` genérico, detalhe no log; nova tentativa na requisição seguinte.
- **Rota inexistente e método não permitido:** responderão no formato do runtime do Functions (não medido); os testes de carga não devem depender dessas respostas.
- **Pesquisa:** o host entregou `GET /api/books/search` à função da rota `api/books/{id}` (T-008); a função `getBook` desvia o segmento exato `search` para a pesquisa.
- **Equivalência:** verificada apenas pelos cenários do `scripts/api-smoke.ps1` (ver `testing.md`), não por construção. Única diferença observada: `Content-Type` do 204 (T-010).
