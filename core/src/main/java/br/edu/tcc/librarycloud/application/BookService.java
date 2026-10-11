package br.edu.tcc.librarycloud.application;

import br.edu.tcc.librarycloud.application.exception.BookNotFoundException;
import br.edu.tcc.librarycloud.application.exception.DuplicateIsbnException;
import br.edu.tcc.librarycloud.application.port.BookRepository;
import br.edu.tcc.librarycloud.domain.Book;
import br.edu.tcc.librarycloud.domain.exception.FieldViolation;
import br.edu.tcc.librarycloud.domain.exception.ValidationException;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Serviço de aplicação: um método público por caso de uso da API de livros.
 *
 * <p>Este é o ÚNICO lugar onde as regras de negócio são orquestradas. Spring Boot e Azure
 * Functions apenas traduzem HTTP para chamadas a esta classe e o resultado de volta para HTTP.
 * Usa {@link System.Logger} (JDK) para não introduzir dependência de biblioteca de log.
 */
public final class BookService {

    private static final System.Logger LOG = System.getLogger(BookService.class.getName());
    private static final System.Logger.Level INFO = System.Logger.Level.INFO;

    private final BookRepository repository;

    public BookService(BookRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    /** Cadastra um livro. Erros: {@link ValidationException}, {@link DuplicateIsbnException}. */
    public Book create(BookData data) {
        Book book = Book.create(data.titulo(), data.autor(), data.isbn(),
                data.anoPublicacao(), data.categoria(), data.disponivel());
        ensureIsbnIsAvailable(book.isbn(), null);
        Book saved = repository.save(book);
        LOG.log(INFO, "Livro criado: id={0}", saved.id());
        return saved;
    }

    /** Consulta por id. Erros: {@link ValidationException} (id mal formado), {@link BookNotFoundException}. */
    public Book getById(String rawId) {
        UUID id = parseId(rawId);
        return repository.findById(id).orElseThrow(() -> new BookNotFoundException(id));
    }

    public Page<Book> list(PageRequest pageRequest) {
        return repository.findAll(pageRequest);
    }

    /** Pesquisa por trecho do título. Erro: {@link ValidationException} se o termo for vazio ou longo. */
    public Page<Book> searchByTitle(String title, PageRequest pageRequest) {
        String term = title == null ? "" : title.strip();
        List<FieldViolation> violations = new ArrayList<>();
        if (term.isEmpty()) {
            violations.add(new FieldViolation("title", "é obrigatório"));
        } else if (term.length() > Book.TITULO_MAX) {
            violations.add(new FieldViolation("title",
                    "deve ter no máximo " + Book.TITULO_MAX + " caracteres"));
        }
        if (!violations.isEmpty()) {
            throw new ValidationException(violations);
        }
        return repository.searchByTitle(term, pageRequest);
    }

    /**
     * Atualização completa (PUT). Ordem das verificações, igual nas duas implantações:
     * id válido (400) → livro existe (404) → dados válidos (400) → ISBN livre (409).
     */
    public Book update(String rawId, BookData data) {
        UUID id = parseId(rawId);
        Book existing = repository.findById(id).orElseThrow(() -> new BookNotFoundException(id));
        Book updated = existing.withDetails(data.titulo(), data.autor(), data.isbn(),
                data.anoPublicacao(), data.categoria(), data.disponivel());
        ensureIsbnIsAvailable(updated.isbn(), id);
        Book saved = repository.save(updated);
        LOG.log(INFO, "Livro atualizado: id={0}", saved.id());
        return saved;
    }

    /** Exclui um livro. Erros: {@link ValidationException}, {@link BookNotFoundException}. */
    public void delete(String rawId) {
        UUID id = parseId(rawId);
        if (!repository.deleteById(id)) {
            throw new BookNotFoundException(id);
        }
        LOG.log(INFO, "Livro excluído: id={0}", id);
    }

    /** Garante que nenhum OUTRO livro use o ISBN (livro sem ISBN nunca conflita). */
    private void ensureIsbnIsAvailable(String isbn, UUID currentBookId) {
        if (isbn == null) {
            return;
        }
        repository.findByIsbn(isbn)
                .filter(other -> !other.id().equals(currentBookId))
                .ifPresent(other -> {
                    throw new DuplicateIsbnException(isbn);
                });
    }

    /** Converte o texto da URL em UUID, rejeitando formatos "esquisitos" que o JDK aceitaria. */
    private static UUID parseId(String rawId) {
        String text = rawId == null ? "" : rawId.strip();
        try {
            UUID parsed = UUID.fromString(text);
            if (parsed.toString().equalsIgnoreCase(text)) {
                return parsed;
            }
        } catch (IllegalArgumentException ignored) {
            // cai no lançamento de ValidationException logo abaixo (tratamento intencional)
        }
        throw new ValidationException(List.of(new FieldViolation("id", "deve ser um UUID válido")));
    }
}
