package br.edu.tcc.librarycloud.infrastructure.persistence;

import br.edu.tcc.librarycloud.application.Page;
import br.edu.tcc.librarycloud.application.PageRequest;
import br.edu.tcc.librarycloud.application.exception.DuplicateIsbnException;
import br.edu.tcc.librarycloud.application.exception.PersistenceFailureException;
import br.edu.tcc.librarycloud.application.port.BookRepository;
import br.edu.tcc.librarycloud.domain.Book;
import br.edu.tcc.librarycloud.domain.exception.ValidationException;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Implementação JDBC de {@link BookRepository} para PostgreSQL.
 * Respeita o contrato da porta: ordenação por {@code lower(titulo), id}, busca por trecho sem
 * diferenciar maiúsculas (ILIKE), violação de ISBN único -> DuplicateIsbnException, e
 * qualquer {@link SQLException} -> PersistenceFailureException.
 *
 * <p>Esta classe é idêntica nas duas implantações; ela não conhece Spring nem Azure.
 */
public class JdbcBookRepository implements BookRepository {

    private static final String COLUMNS = "id, titulo, autor, isbn, ano_publicacao, categoria, disponivel";
    private static final String ORDER_BY = " ORDER BY lower(titulo), id";
    /** Código SQLSTATE do PostgreSQL para violação de unicidade. */
    private static final String UNIQUE_VIOLATION = "23505";

    private final DataSource dataSource;

    public JdbcBookRepository(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    @Override
    public Book save(Book book) {
        String sql = "INSERT INTO books (" + COLUMNS + ") VALUES (?, ?, ?, ?, ?, ?, ?) "
                + "ON CONFLICT (id) DO UPDATE SET "
                + "titulo = EXCLUDED.titulo, autor = EXCLUDED.autor, isbn = EXCLUDED.isbn, "
                + "ano_publicacao = EXCLUDED.ano_publicacao, categoria = EXCLUDED.categoria, "
                + "disponivel = EXCLUDED.disponivel";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, book.id());
            statement.setString(2, book.titulo());
            statement.setString(3, book.autor());
            statement.setString(4, book.isbn());
            statement.setInt(5, book.anoPublicacao());
            statement.setString(6, book.categoria());
            statement.setBoolean(7, book.disponivel());
            statement.executeUpdate();
            return book;
        } catch (SQLException e) {
            if (UNIQUE_VIOLATION.equals(e.getSQLState())) {
                throw new DuplicateIsbnException(book.isbn());
            }
            throw failure("salvar livro", e);
        }
    }

    @Override
    public Optional<Book> findById(UUID id) {
        String sql = "SELECT " + COLUMNS + " FROM books WHERE id = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, id);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw failure("consultar livro por id", e);
        }
    }

    @Override
    public Page<Book> findAll(PageRequest pageRequest) {
        return queryPage("", null, pageRequest);
    }

    @Override
    public Page<Book> searchByTitle(String titleFragment, PageRequest pageRequest) {
        String pattern = "%" + escapeLikePattern(titleFragment) + "%";
        return queryPage(" WHERE titulo ILIKE ? ESCAPE '\\'", pattern, pageRequest);
    }

    @Override
    public Optional<Book> findByIsbn(String isbn) {
        String sql = "SELECT " + COLUMNS + " FROM books WHERE isbn = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, isbn);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw failure("consultar livro por ISBN", e);
        }
    }

    @Override
    public boolean deleteById(UUID id) {
        String sql = "DELETE FROM books WHERE id = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, id);
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            throw failure("excluir livro", e);
        }
    }

    /** Duas consultas na mesma conexão: total de itens e a página pedida. */
    private Page<Book> queryPage(String where, String parameter, PageRequest pageRequest) {
        String countSql = "SELECT count(*) FROM books" + where;
        String listSql = "SELECT " + COLUMNS + " FROM books" + where + ORDER_BY + " LIMIT ? OFFSET ?";
        try (Connection connection = dataSource.getConnection()) {
            long total;
            try (PreparedStatement count = connection.prepareStatement(countSql)) {
                if (parameter != null) {
                    count.setString(1, parameter);
                }
                try (ResultSet rs = count.executeQuery()) {
                    rs.next();
                    total = rs.getLong(1);
                }
            }
            List<Book> items = new ArrayList<>();
            try (PreparedStatement list = connection.prepareStatement(listSql)) {
                int index = 1;
                if (parameter != null) {
                    list.setString(index++, parameter);
                }
                list.setInt(index++, pageRequest.size());
                list.setLong(index, pageRequest.offset());
                try (ResultSet rs = list.executeQuery()) {
                    while (rs.next()) {
                        items.add(mapRow(rs));
                    }
                }
            }
            return new Page<>(items, pageRequest.page(), pageRequest.size(), total);
        } catch (SQLException e) {
            throw failure("listar livros", e);
        }
    }

    private static Book mapRow(ResultSet rs) throws SQLException {
        try {
            return Book.restore(
                    rs.getObject("id", UUID.class),
                    rs.getString("titulo"),
                    rs.getString("autor"),
                    rs.getString("isbn"),
                    rs.getInt("ano_publicacao"),
                    rs.getString("categoria"),
                    rs.getBoolean("disponivel"));
        } catch (ValidationException e) {
            // Dado já gravado que viola as regras atuais: é problema de persistência, não do cliente.
            throw new PersistenceFailureException("Registro inconsistente no banco de dados: " + e.getMessage(), e);
        }
    }

    /** Escapa os caracteres curinga do LIKE (\, % e _) para que o termo seja tratado como texto literal. */
    static String escapeLikePattern(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static PersistenceFailureException failure(String action, SQLException cause) {
        return new PersistenceFailureException("Falha ao " + action, cause);
    }
}
