# Arquitetura

Status: núcleo **TESTADO** · `api-contract`, `infrastructure`, `adapter-container` **TESTADOS** manualmente (Etapa 3) · `adapter-functions` **PLANEJADO**.

## Princípio central

Um **núcleo compartilhado** (regras de negócio e casos de uso) e **dois adaptadores finos**
(um por ambiente de execução). Assim a diferença entre as duas implantações fica restrita
à forma de executar e implantar, como exige o objetivo do TCC. **[DECISÃO]**

```
 adapter-container (Spring Boot)      adapter-functions (Azure Functions)
   (TESTADO)                             (PLANEJADO)
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
| `adapter-functions` | Funções HTTP que chamam o mesmo `BookService` | `core`, `infrastructure`, biblioteca Azure Functions | PLANEJADO (Etapa 5) |

## Mapa de erros (contrato que os dois adaptadores devem seguir)

| Exceção do núcleo | HTTP |
|---|---|
| `ValidationException` | 400 |
| `BookNotFoundException` | 404 |
| `DuplicateIsbnException` | 409 |
| `PersistenceFailureException` | 503 |
| Qualquer outra exceção | 500 |

A tabela vive em **um único lugar**: `ErrorMapper` (módulo `api-contract`), usado pelos dois adaptadores (ADR-018).

## Diferenças inevitáveis entre os dois adaptadores (a documentar durante a Etapa 5)

| Aspecto | Container Apps | Functions | Pode influenciar |
|---|---|---|---|
| Framework HTTP | Spring Boot (servidor embutido) | Runtime do Azure Functions + biblioteca de bindings | Latência, memória, tempo de inicialização |
| Inicialização | Contexto Spring completo | Host Functions + worker Java; sem Spring | Cold start |
| Erros do próprio framework (rota inexistente, método não permitido) | Formato padrão do Spring | Formato do runtime Functions | Pequena diferença nas respostas de erro de rotas inexistentes |
| Modelo de escala | Regras (ex.: requisições HTTP simultâneas) por réplica | Gerenciada pela plataforma por instância | Escalabilidade |
| Cobrança | Recursos alocados/consumidos por réplica | Tempo de execução (+ instâncias sempre prontas, se houver) | Custo |

> Estas diferenças são **ameaças à validade**: os resultados não representam um isolamento
> perfeito do efeito "arquitetura". **[DECISÃO]** de registrar isso em toda análise.

## Comportamentos observados no adaptador de container (validados em 02/11/2026)

- **Ordem das verificações em PUT:** id válido (400) → livro existe (404) → dados válidos (400) → ISBN livre (409). Observado: `PUT` em id inexistente com corpo incompleto responde **404**, não 400.
- **Rota inexistente** (`/api/nada`) responde 404 no formato padrão do Spring: `{"timestamp":...,"status":404,"error":"Not Found","path":...}`, **diferente** do formato da aplicação (`code`, `message`, `violations`). O Azure Functions responderá rotas inexistentes à sua maneira; os testes de carga não devem depender dessas respostas. **[MEDIDO — observação funcional]**
- **Banco indisponível:** `/health` continua 200, `/health/ready` responde 503 e os endpoints de dados respondem 503 `SERVICE_UNAVAILABLE` sem detalhe técnico; o detalhe fica no log. Após religar o banco a API se recupera sem reinício.
