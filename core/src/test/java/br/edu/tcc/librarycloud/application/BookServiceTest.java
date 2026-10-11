package br.edu.tcc.librarycloud.application;

import br.edu.tcc.librarycloud.application.exception.BookNotFoundException;
import br.edu.tcc.librarycloud.application.exception.DuplicateIsbnException;
import br.edu.tcc.librarycloud.application.exception.PersistenceFailureException;
import br.edu.tcc.librarycloud.application.support.FailingBookRepository;
import br.edu.tcc.librarycloud.application.support.InMemoryBookRepository;
import br.edu.tcc.librarycloud.domain.Book;
import br.edu.tcc.librarycloud.domain.exception.FieldViolation;
import br.edu.tcc.librarycloud.domain.exception.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BookServiceTest {

    private static final PageRequest FIRST_PAGE = PageRequest.of(0, 10);

    private InMemoryBookRepository repository;
    private BookService service;

    @BeforeEach
    void setUp() {
        repository = new InMemoryBookRepository();
        service = new BookService(repository);
    }

    private static BookData data(String titulo, String isbn) {
        return new BookData(titulo, "Autor Teste", isbn, 2000, "Romance", true);
    }

    private static List<String> invalidFields(ValidationException ex) {
        return ex.violations().stream().map(FieldViolation::field).toList();
    }

    // ---------- cadastro ----------

    @Test
    @DisplayName("cadastro válido persiste e devolve o livro com id")
    void createsValidBook() {
        Book created = service.create(data("Dom Casmurro", "9788535902775"));

        assertThat(created.id()).isNotNull();
        assertThat(repository.count()).isEqualTo(1);
        assertThat(service.getById(created.id().toString()).titulo()).isEqualTo("Dom Casmurro");
    }

    @Test
    @DisplayName("cadastro inválido lança ValidationException e não persiste nada")
    void invalidCreationPersistsNothing() {
        BookData invalid = new BookData("", null, "123", null, "", true);

        ValidationException ex = assertThrows(ValidationException.class, () -> service.create(invalid));

        assertThat(invalidFields(ex)).containsExactlyInAnyOrder("titulo", "autor", "isbn", "anoPublicacao", "categoria");
        assertThat(repository.count()).isEqualTo(0);
    }

    @Test
    @DisplayName("ISBN repetido gera conflito, mesmo escrito com hífens")
    void duplicateIsbnIsRejected() {
        service.create(data("Livro A", "9788535902775"));

        assertThrows(DuplicateIsbnException.class, () -> service.create(data("Livro B", "978-85-359-0277-5")));
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("vários livros sem ISBN não geram conflito entre si")
    void booksWithoutIsbnNeverConflict() {
        service.create(data("Livro A", null));
        service.create(data("Livro B", null));

        assertThat(repository.count()).isEqualTo(2);
    }

    // ---------- consulta por id ----------

    @Test
    @DisplayName("consulta por id inexistente lança BookNotFoundException")
    void getByUnknownIdFails() {
        String unknown = UUID.randomUUID().toString();

        assertThrows(BookNotFoundException.class, () -> service.getById(unknown));
    }

    @Test
    @DisplayName("id mal formado lança ValidationException")
    void malformedIdIsRejected() {
        ValidationException ex = assertThrows(ValidationException.class, () -> service.getById("abc"));
        ValidationException odd = assertThrows(ValidationException.class, () -> service.getById("1-1-1-1-1"));

        assertThat(invalidFields(ex)).containsExactly("id");
        assertThat(invalidFields(odd)).containsExactly("id");
    }

    // ---------- listagem e pesquisa ----------

    @Test
    @DisplayName("listagem é paginada e ordenada por título")
    void listIsPagedAndOrdered() {
        for (int i = 0; i < 25; i++) {
            service.create(data(String.format("Livro %02d", i), null));
        }

        Page<Book> first = service.list(PageRequest.of(0, 10));
        Page<Book> last = service.list(PageRequest.of(2, 10));

        assertThat(first.items()).hasSize(10);
        assertThat(first.items().get(0).titulo()).isEqualTo("Livro 00");
        assertThat(first.totalElements()).isEqualTo(25L);
        assertThat(first.totalPages()).isEqualTo(3);
        assertThat(last.items()).hasSize(5);
        assertThat(last.items().get(4).titulo()).isEqualTo("Livro 24");
    }

    @Test
    @DisplayName("listagem vazia retorna página vazia")
    void emptyListing() {
        Page<Book> page = service.list(FIRST_PAGE);

        assertThat(page.items()).isEmpty();
        assertThat(page.totalElements()).isEqualTo(0L);
    }

    @Test
    @DisplayName("pesquisa por título encontra trechos sem diferenciar maiúsculas")
    void searchByTitleIsCaseInsensitiveContains() {
        service.create(data("Dom Casmurro", null));
        service.create(data("Dom Quixote", null));
        service.create(data("Memórias Póstumas", null));

        Page<Book> result = service.searchByTitle("  DOM ", FIRST_PAGE);

        assertThat(result.totalElements()).isEqualTo(2L);
        assertThat(result.items().get(0).titulo()).isEqualTo("Dom Casmurro");
        assertThat(result.items().get(1).titulo()).isEqualTo("Dom Quixote");
    }

    @Test
    @DisplayName("pesquisa sem resultado retorna página vazia")
    void searchWithoutMatch() {
        service.create(data("Dom Casmurro", null));

        assertThat(service.searchByTitle("inexistente", FIRST_PAGE).items()).isEmpty();
    }

    @Test
    @DisplayName("pesquisa com termo vazio ou nulo é inválida")
    void blankSearchTermIsRejected() {
        ValidationException blank = assertThrows(ValidationException.class,
                () -> service.searchByTitle("   ", FIRST_PAGE));
        ValidationException missing = assertThrows(ValidationException.class,
                () -> service.searchByTitle(null, FIRST_PAGE));

        assertThat(invalidFields(blank)).containsExactly("title");
        assertThat(invalidFields(missing)).containsExactly("title");
    }

    // ---------- atualização ----------

    @Test
    @DisplayName("atualização válida altera os dados e mantém o id")
    void updatesBook() {
        Book created = service.create(data("Título antigo", "9788535902775"));

        Book updated = service.update(created.id().toString(),
                new BookData("Título novo", "Outro Autor", "9788535902775", 2010, "Ensaio", false));

        assertThat(updated.id()).isEqualTo(created.id());
        assertThat(service.getById(created.id().toString()).titulo()).isEqualTo("Título novo");
        assertThat(service.getById(created.id().toString()).disponivel()).isFalse();
    }

    @Test
    @DisplayName("atualizar mantendo o próprio ISBN não é conflito")
    void keepingOwnIsbnIsNotAConflict() {
        Book created = service.create(data("Livro", "9788535902775"));

        Book updated = service.update(created.id().toString(), data("Livro revisado", "978-85-359-0277-5"));

        assertThat(updated.titulo()).isEqualTo("Livro revisado");
    }

    @Test
    @DisplayName("atualizar para o ISBN de outro livro gera conflito")
    void updatingToAnotherBooksIsbnFails() {
        service.create(data("Livro A", "9788535902775"));
        Book other = service.create(data("Livro B", "9788535914849"));

        assertThrows(DuplicateIsbnException.class,
                () -> service.update(other.id().toString(), data("Livro B", "9788535902775")));
    }

    @Test
    @DisplayName("atualizar livro inexistente lança BookNotFoundException")
    void updatingUnknownBookFails() {
        String unknown = UUID.randomUUID().toString();

        assertThrows(BookNotFoundException.class, () -> service.update(unknown, data("Livro", null)));
    }

    @Test
    @DisplayName("atualização inválida lança ValidationException e preserva os dados")
    void invalidUpdateKeepsOriginalData() {
        Book created = service.create(data("Título original", null));

        assertThrows(ValidationException.class,
                () -> service.update(created.id().toString(), new BookData("", "Autor", null, 2000, "Romance", true)));

        assertThat(service.getById(created.id().toString()).titulo()).isEqualTo("Título original");
    }

    // ---------- exclusão ----------

    @Test
    @DisplayName("exclusão remove o livro")
    void deletesBook() {
        Book created = service.create(data("Livro", null));

        service.delete(created.id().toString());

        assertThat(repository.count()).isEqualTo(0);
        assertThrows(BookNotFoundException.class, () -> service.getById(created.id().toString()));
    }

    @Test
    @DisplayName("excluir livro inexistente lança BookNotFoundException")
    void deletingUnknownBookFails() {
        String unknown = UUID.randomUUID().toString();

        assertThrows(BookNotFoundException.class, () -> service.delete(unknown));
    }

    // ---------- falhas de persistência ----------

    @Test
    @DisplayName("falhas de persistência chegam ao chamador (não são engolidas)")
    void persistenceFailuresPropagate() {
        BookService failing = new BookService(new FailingBookRepository());
        String someId = UUID.randomUUID().toString();

        assertThrows(PersistenceFailureException.class, () -> failing.create(data("Livro", null)));
        assertThrows(PersistenceFailureException.class, () -> failing.getById(someId));
        assertThrows(PersistenceFailureException.class, () -> failing.list(FIRST_PAGE));
        assertThrows(PersistenceFailureException.class, () -> failing.searchByTitle("livro", FIRST_PAGE));
        assertThrows(PersistenceFailureException.class, () -> failing.update(someId, data("Livro", null)));
        assertThrows(PersistenceFailureException.class, () -> failing.delete(someId));
    }
}
