# Arquitetura

Status: **GERADO** (núcleo) · adaptadores **PLANEJADOS**.

## Princípio central

Um **núcleo compartilhado** (regras de negócio e casos de uso) e **dois adaptadores finos**
(um por ambiente de execução). Assim a diferença entre as duas implantações fica restrita
à forma de executar e implantar, como exige o objetivo do TCC. **[DECISÃO]**

```
 adapter-container (Spring Boot)      adapter-functions (Azure Functions)   <- PLANEJADO
              \                              /
               +-------- infrastructure ----+   JDBC + PostgreSQL             <- PLANEJADO
                              |
                    core (domain + application)                               <- GERADO
```

A seta de dependência sempre aponta para dentro: o `core` não conhece ninguém.

## Módulos

| Módulo | Responsabilidade | Dependências de produção | Status |
|---|---|---|---|
| `core` | `Book` e regras (domain); `BookService`, `BookRepository` (porta), exceções, paginação (application) | **nenhuma** | GERADO |
| `infrastructure` | `BookRepository` com JDBC/PostgreSQL, pool, configuração por variáveis de ambiente | `core`, driver PostgreSQL, pool | PLANEJADO (Etapa 3) |
| `adapter-container` | Controllers REST, DTOs, tratamento de erros, `/health`, OpenAPI | `core`, `infrastructure`, Spring Boot | PLANEJADO (Etapa 3) |
| `adapter-functions` | Funções HTTP que chamam o mesmo `BookService` | `core`, `infrastructure`, biblioteca Azure Functions | PLANEJADO (Etapa 5) |

## Mapa de erros (contrato que os dois adaptadores devem seguir)

| Exceção do núcleo | HTTP |
|---|---|
| `ValidationException` | 400 |
| `BookNotFoundException` | 404 |
| `DuplicateIsbnException` | 409 |
| `PersistenceFailureException` | 500 (ou 503) |

## Diferenças inevitáveis entre os dois adaptadores (a documentar durante a Etapa 5)

| Aspecto | Container Apps | Functions | Pode influenciar |
|---|---|---|---|
| Framework HTTP | Spring Boot (servidor embutido) | Runtime do Azure Functions + biblioteca de bindings | Latência, memória, tempo de inicialização |
| Inicialização | Contexto Spring completo | Host Functions + worker Java; sem Spring | Cold start |
| Modelo de escala | Regras (ex.: requisições HTTP simultâneas) por réplica | Gerenciada pela plataforma por instância | Escalabilidade |
| Cobrança | Recursos alocados/consumidos por réplica | Tempo de execução (+ instâncias sempre prontas, se houver) | Custo |

> Estas diferenças são **ameaças à validade**: os resultados não representam um isolamento
> perfeito do efeito "arquitetura". **[DECISÃO]** de registrar isso em toda análise.
