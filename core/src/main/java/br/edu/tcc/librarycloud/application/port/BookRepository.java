package br.edu.tcc.librarycloud.application.port;

import br.edu.tcc.librarycloud.application.Page;
import br.edu.tcc.librarycloud.application.PageRequest;
import br.edu.tcc.librarycloud.domain.Book;

import java.util.Optional;
import java.util.UUID;

/**
 * Porta de saída: o que o núcleo precisa da persistência, sem saber como ela funciona.
 * A implementação real (JDBC + PostgreSQL) virá no módulo {@code infrastructure}.
 *
 * <p>Contrato que TODA implementação deve respeitar (testes de integração verificarão isso):
 * <ul>
 *   <li>Ordenação das listagens: {@code lower(titulo)} e depois {@code id}, para resultados
 *       determinísticos e comparáveis entre execuções.</li>
 *   <li>{@link #searchByTitle}: busca por trecho, sem diferenciar maiúsculas de minúsculas.</li>
 *   <li>Violação de unicidade do ISBN no banco deve virar
 *       {@code DuplicateIsbnException}; qualquer outra falha técnica deve virar
 *       {@code PersistenceFailureException}.</li>
 * </ul>
 */
public interface BookRepository {

    /** Insere o livro se o id não existe; caso contrário, atualiza. Retorna o livro salvo. */
    Book save(Book book);

    Optional<Book> findById(UUID id);

    Page<Book> findAll(PageRequest pageRequest);

    Page<Book> searchByTitle(String titleFragment, PageRequest pageRequest);

    /** @param isbn ISBN já normalizado (sem hífens). */
    Optional<Book> findByIsbn(String isbn);

    /** @return {@code true} se o livro existia e foi removido. */
    boolean deleteById(UUID id);
}
