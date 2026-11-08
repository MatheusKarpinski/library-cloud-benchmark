package br.edu.tcc.librarycloud.infrastructure.persistence;

import br.edu.tcc.librarycloud.application.Page;
import br.edu.tcc.librarycloud.application.PageRequest;
import br.edu.tcc.librarycloud.application.exception.DuplicateIsbnException;
import br.edu.tcc.librarycloud.application.exception.PersistenceFailureException;
import br.edu.tcc.librarycloud.domain.Book;
import br.edu.tcc.librarycloud.infrastructure.config.DataSourceFactory;
import br.edu.tcc.librarycloud.infrastructure.config.DatabaseConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifica o contrato de {@code BookRepository} contra um PostgreSQL real.
 * Exige Docker. Executar com: mvn test -Pintegration
 */
@Tag("integration")
class JdbcBookRepositoryIntegrationTest {

    private static final PageRequest FIRST_PAGE = PageRequest.of(0, 10);

    private static HikariDataSource dataSource;
    private JdbcBookRepository repository;

    @BeforeAll
    static void createPoolAndSchema() throws Exception {
        dataSource = DataSourceFactory.create(PostgresTestContainer.config(3, 2_000));
        PostgresTestContainer.applySchema(dataSource);
    }

    @AfterAll
    static void closePool() {
        dataSource.close();
    }

    @BeforeEach
    void cleanTable() throws SQLException {
        execute("TRUNCATE TABLE books");
        repository = new JdbcBookRepository(dataSource);
    }

    private static void execute(String sql) throws SQLException {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private static Book book(String titulo, String isbn) {
        return Book.create(titulo, "Autor Teste", isbn, 2000, "Romance", true);
    }

    private static Book bookWithId(String id, String titulo) {
        return Book.restore(UUID.fromString(id), titulo, "Autor Teste", null, 2000, "Romance", true);
    }

    private static List<String> titles(Page<Book> page) {
        return page.items().stream().map(Book::titulo).toList();
    }

    @Test
    @DisplayName("salva e recupera o livro com todos os campos")
    void savesAndFindsById() {
        Book saved = repository.save(Book.create("Dom Casmurro", "Machado de Assis", "9788535902775", 1899, "Romance", false));

        Book found = repository.findById(saved.id()).orElseThrow();

        assertThat(found.titulo()).isEqualTo("Dom Casmurro");
        assertThat(found.autor()).isEqualTo("Machado de Assis");
        assertThat(found.isbn()).isEqualTo("9788535902775");
        assertThat(found.anoPublicacao()).isEqualTo(1899);
        assertThat(found.categoria()).isEqualTo("Romance");
        assertThat(found.disponivel()).isFalse();
    }

    @Test
    @DisplayName("salvar um livro existente atualiza em vez de duplicar")
    void saveUpdatesExistingBook() {
        Book original = repository.save(book("Título antigo", "9788535902775"));

        repository.save(original.withDetails("Título novo", "Outro Autor", "9788535902775", 2010, "Ensaio", false));

        Book found = repository.findById(original.id()).orElseThrow();
        assertThat(found.titulo()).isEqualTo("Título novo");
        assertThat(found.disponivel()).isFalse();
        assertThat(repository.findAll(FIRST_PAGE).totalElements()).isEqualTo(1L);
    }

    @Test
    @DisplayName("id inexistente retorna vazio")
    void unknownIdReturnsEmpty() {
        assertThat(repository.findById(UUID.randomUUID()).isPresent()).isFalse();
    }

    @Test
    @DisplayName("encontra livro por ISBN")
    void findsByIsbn() {
        Book saved = repository.save(book("Livro", "9788535902775"));

        assertThat(repository.findByIsbn("9788535902775").orElseThrow().id()).isEqualTo(saved.id());
        assertThat(repository.findByIsbn("9780000000000").isPresent()).isFalse();
    }

    @Test
    @DisplayName("ISBN repetido em outro livro vira DuplicateIsbnException")
    void duplicateIsbnOnInsert() {
        repository.save(book("Livro A", "9788535902775"));

        assertThrows(DuplicateIsbnException.class, () -> repository.save(book("Livro B", "9788535902775")));
        assertThat(repository.findAll(FIRST_PAGE).totalElements()).isEqualTo(1L);
    }

    @Test
    @DisplayName("atualizar para o ISBN de outro livro vira DuplicateIsbnException")
    void duplicateIsbnOnUpdate() {
        repository.save(book("Livro A", "9788535902775"));
        Book other = repository.save(book("Livro B", "9788535914849"));

        assertThrows(DuplicateIsbnException.class,
                () -> repository.save(other.withDetails("Livro B", "Autor", "9788535902775", 2000, "Romance", true)));
    }

    @Test
    @DisplayName("vários livros sem ISBN são permitidos")
    void manyBooksWithoutIsbn() {
        repository.save(book("Livro A", null));
        repository.save(book("Livro B", null));

        assertThat(repository.findAll(FIRST_PAGE).totalElements()).isEqualTo(2L);
    }

    @Test
    @DisplayName("listagem ordena por título (sem diferenciar maiúsculas) e depois por id, com paginação e total")
    void ordersAndPaginates() {
        repository.save(bookWithId("00000000-0000-0000-0000-000000000002", "banana"));
        repository.save(book("cereja", null));
        repository.save(bookWithId("00000000-0000-0000-0000-000000000001", "Banana"));
        repository.save(book("Abacaxi", null));

        Page<Book> first = repository.findAll(PageRequest.of(0, 2));
        Page<Book> second = repository.findAll(PageRequest.of(1, 2));

        assertThat(titles(first)).containsExactly("Abacaxi", "Banana");
        assertThat(titles(second)).containsExactly("banana", "cereja");
        assertThat(first.totalElements()).isEqualTo(4L);
        assertThat(first.totalPages()).isEqualTo(2);
    }

    @Test
    @DisplayName("pesquisa por trecho do título, sem diferenciar maiúsculas, com total correto")
    void searchIsCaseInsensitiveContains() {
        repository.save(book("Dom Casmurro", null));
        repository.save(book("Dom Quixote", null));
        repository.save(book("Memorias Postumas", null));

        Page<Book> result = repository.searchByTitle("DOM", FIRST_PAGE);

        assertThat(titles(result)).containsExactly("Dom Casmurro", "Dom Quixote");
        assertThat(result.totalElements()).isEqualTo(2L);
        assertThat(repository.searchByTitle("inexistente", FIRST_PAGE).items()).isEmpty();
    }

    @Test
    @DisplayName("pesquisa trata %, _ e barra invertida como texto comum")
    void searchTreatsWildcardsLiterally() {
        repository.save(book("100% Verdade", null));
        repository.save(book("1000 Dias", null));
        repository.save(book("a_b", null));
        repository.save(book("axb", null));

        assertThat(titles(repository.searchByTitle("100%", FIRST_PAGE))).containsExactly("100% Verdade");
        assertThat(titles(repository.searchByTitle("a_b", FIRST_PAGE))).containsExactly("a_b");
        assertThat(repository.searchByTitle("\\", FIRST_PAGE).totalElements()).isEqualTo(0L);
    }

    @Test
    @DisplayName("exclusão retorna verdadeiro uma vez e falso quando o livro não existe")
    void deleteReportsWhetherBookExisted() {
        Book saved = repository.save(book("Livro", null));

        assertThat(repository.deleteById(saved.id())).isTrue();
        assertThat(repository.deleteById(saved.id())).isFalse();
        assertThat(repository.findById(saved.id()).isPresent()).isFalse();
    }

    @Test
    @DisplayName("registro que viola as regras atuais vira PersistenceFailureException")
    void inconsistentRowBecomesPersistenceFailure() throws SQLException {
        execute("INSERT INTO books (id, titulo, autor, isbn, ano_publicacao, categoria, disponivel) "
                + "VALUES ('00000000-0000-0000-0000-0000000000aa', 'Livro', 'Autor', NULL, 3000, 'Teste', true)");
        UUID id = UUID.fromString("00000000-0000-0000-0000-0000000000aa");

        assertThrows(PersistenceFailureException.class, () -> repository.findById(id));
        assertThrows(PersistenceFailureException.class, () -> repository.findAll(FIRST_PAGE));
    }

    @Test
    @DisplayName("banco inacessível vira PersistenceFailureException e o health check informa indisponível")
    void unreachableDatabaseIsReportedAsFailure() {
        DatabaseConfig unreachable = new DatabaseConfig(
                "jdbc:postgresql://localhost:1/inexistente", "usuario", "senha", 1, 0, 250);
        try (HikariDataSource broken = DataSourceFactory.create(unreachable)) {
            JdbcBookRepository brokenRepository = new JdbcBookRepository(broken);

            assertThrows(PersistenceFailureException.class, () -> brokenRepository.findById(UUID.randomUUID()));
            assertThrows(PersistenceFailureException.class, () -> brokenRepository.save(book("Livro", null)));
            assertThat(new DatabaseHealthChecker(broken).isAvailable()).isFalse();
        }
    }

    @Test
    @DisplayName("health check informa disponível quando o banco responde")
    void healthCheckReportsAvailable() {
        assertThat(new DatabaseHealthChecker(dataSource).isAvailable()).isTrue();
    }
}
