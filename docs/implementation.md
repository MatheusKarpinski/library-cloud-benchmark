# Implementação

## Etapa 2 — núcleo (`core`) — status: GERADO / VERIFICADO PARCIALMENTE

Pacote base: `br.edu.tcc.librarycloud`.

| Arquivo | Papel |
|---|---|
| `domain/Book.java` | Entidade imutável; **todas** as validações e normalizações (texto, ISBN, ano) |
| `domain/exception/ValidationException.java`, `FieldViolation.java` | Erro de validação com a lista de campos inválidos |
| `application/BookService.java` | Casos de uso: criar, consultar, listar, pesquisar, atualizar, excluir |
| `application/BookData.java` | Dados de entrada (tipos `Integer`/`Boolean` para detectar campo ausente) |
| `application/PageRequest.java`, `Page.java` | Paginação (padrão 20, máximo 100, página começa em 0) |
| `application/port/BookRepository.java` | Porta de persistência + contrato (ordenação, busca, erros) |
| `application/exception/*` | `BookNotFoundException`, `DuplicateIsbnException`, `PersistenceFailureException` |

### Regras de validação (fonte única: `Book`)

| Campo | Regra |
|---|---|
| `titulo` | obrigatório, até 200 caracteres (após `strip`) |
| `autor` | obrigatório, até 150 |
| `categoria` | obrigatória, até 80 |
| `isbn` | opcional; normalizado (sem hífens/espaços, maiúsculas); 13 dígitos ou 9 dígitos + dígito/X. **Só formato — o dígito verificador não é conferido** |
| `anoPublicacao` | obrigatório, de 1000 até (ano atual + 1) |
| `disponivel` | opcional; ausente = `true` |
| `id` | `UUID` gerado pela aplicação |

### Ordem das verificações na atualização (PUT)

id válido (400) → livro existe (404) → dados válidos (400) → ISBN livre (409).

### Limitações conhecidas

- A checagem de ISBN duplicado (consulta + gravação) tem uma janela de corrida entre duas
  requisições simultâneas. A proteção definitiva será a restrição `UNIQUE` no banco
  (Etapa 3), convertida em `DuplicateIsbnException` pelo repositório.
- Busca por título não ignora acentos (ex.: "memorias" não encontra "Memórias").
- O ano máximo depende da data do sistema (`Year.now()`).
