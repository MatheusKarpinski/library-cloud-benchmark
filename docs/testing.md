# Testes

## Estratégia

| Nível | Ferramenta | Onde | Status |
|---|---|---|---|
| Unitário (domínio e casos de uso) | JUnit 5 + AssertJ, repositório em memória **apenas de teste** | `core/src/test` | GERADO · VERIFICADO PARCIALMENTE |
| Integração de persistência (PostgreSQL real) | Testcontainers (exige Docker Desktop) — **proposta** | `infrastructure` | PLANEJADO (Etapa 4) |
| Integração dos endpoints | A definir com o adaptador | `adapter-*` | PLANEJADO (Etapas 4–5) |
| Carga | k6 (ferramenta externa) | `load-tests/` | PLANEJADO (Etapa 7) |

## Etapa 2 — 35 testes no `core`

- `BookTest` (12): criação válida, normalização, ISBN-10 com X, ISBN opcional, padrão de
  `disponivel`, violações agregadas, limites de tamanho e de ano, `withDetails`, igualdade por id.
- `PageRequestTest` (4): padrões, offset, limites, valores inválidos.
- `BookServiceTest` (19): cadastro (válido, inválido, ISBN duplicado, sem ISBN), consulta
  (existente, inexistente, id mal formado), listagem paginada/ordenada/vazia, pesquisa (com
  e sem resultado, termo vazio), atualização (ok, mesmo ISBN, ISBN de outro, inexistente,
  inválida), exclusão (ok, inexistente) e propagação de falhas de persistência.

## Resultados observados

| Execução | Onde | Resultado |
|---|---|---|
| Compilação (`javac`, Java 21) + 35 testes com *stubs* mínimos de JUnit/AssertJ | Sandbox do assistente, 11/10/2026 | 35 aprovados, 0 falhas |
| `mvn test` com JUnit/AssertJ reais | **Sua máquina** | **PENDENTE — registrar aqui o resultado** |

> **Limitação importante:** a execução no sandbox usou um executor próprio e *stubs* que
> reproduzem só o subconjunto usado de JUnit/AssertJ, porque o sandbox não tem acesso à
> internet para baixar o Maven e as bibliotecas. Isso valida a lógica e a compilação do
> código, mas **não** valida o `pom.xml` nem a integração real com o Surefire.
> O status "TESTADO" só vale depois do seu `mvn test`.

## Como executar localmente

Ver o `README.md` da raiz (seção "Executar os testes").
