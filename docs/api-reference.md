# Referência da API

Status: **TESTADA** manualmente (Etapa 3, 02/11/2026).
Especificação formal: [`openapi.yaml`](openapi.yaml). Todas as respostas são JSON (UTF-8).

## Endpoints

| Método | Caminho | Sucesso | Erros possíveis |
|---|---|---|---|
| GET | `/api/books?page=&size=` | 200, `PageResponse` | 400 (page/size inválidos), 503 |
| GET | `/api/books/search?title=&page=&size=` | 200, `PageResponse` | 400 (title vazio ou > 200), 503 |
| GET | `/api/books/{id}` | 200, `BookResponse` | 400 (id não é UUID), 404, 503 |
| POST | `/api/books` | 201 + `Location`, `BookResponse` | 400, 409 (ISBN repetido), 503 |
| PUT | `/api/books/{id}` | 200, `BookResponse` | 400, 404, 409, 503 |
| DELETE | `/api/books/{id}` | 204 | 400, 404, 503 |
| GET | `/health` | 200 `{"status":"UP"}` (não consulta o banco) | — |
| GET | `/health/ready` | 200 `{"status":"UP","database":"UP"}` | 503 `{"status":"DOWN","database":"DOWN"}` |

Paginação: `page` começa em 0 (padrão 0); `size` de 1 a 100 (padrão 20). Ordenação fixa: título (sem diferenciar maiúsculas) e depois id.
A pesquisa busca o termo em **qualquer parte** do título, sem diferenciar maiúsculas.

## Corpo para POST e PUT (substituição completa)

```json
{
  "titulo": "Dom Casmurro",
  "autor": "Machado de Assis",
  "isbn": "978-85-359-0277-5",
  "anoPublicacao": 1899,
  "categoria": "Romance",
  "disponivel": true
}
```

`isbn` e `disponivel` são opcionais (`disponivel` ausente = `true`). O ISBN é gravado sem hífens.
Regras e limites: ver `implementation.md`.

## Formato de erro (todas as falhas geradas pela aplicação)

```json
{
  "code": "VALIDATION_ERROR",
  "message": "Dados inválidos",
  "violations": [
    { "field": "titulo", "message": "é obrigatório" }
  ]
}
```

| `code` | HTTP | Quando |
|---|---|---|
| `VALIDATION_ERROR` | 400 | Campo inválido, id mal formado, parâmetro com tipo errado |
| `MALFORMED_REQUEST` | 400 | Corpo ausente ou JSON inválido |
| `NOT_FOUND` | 404 | Livro inexistente |
| `CONFLICT` | 409 | ISBN já cadastrado em outro livro |
| `SERVICE_UNAVAILABLE` | 503 | Falha ao acessar o banco |
| `INTERNAL_ERROR` | 500 | Erro inesperado |

Rotas inexistentes e métodos não permitidos retornam o formato padrão do framework (diferença documentada em `architecture.md`).
