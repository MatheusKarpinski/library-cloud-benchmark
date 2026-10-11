package br.edu.tcc.librarycloud.application.support;

import br.edu.tcc.librarycloud.application.Page;
import br.edu.tcc.librarycloud.application.PageRequest;
import br.edu.tcc.librarycloud.application.port.BookRepository;
import br.edu.tcc.librarycloud.domain.Book;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Repositório em memória, EXCLUSIVO para testes unitários do núcleo.
 * Não é uma solução de persistência: não é thread-safe e perde tudo ao encerrar.
 * Reproduz o contrato de {@link BookRepository} (ordenação e busca por trecho).
 */
public class InMemoryBookRepository implements BookRepository {

    private static final Comparator<Book> CONTRACT_ORDER = Comparator
            .comparing((Book b) -> b.titulo().toLowerCase(Locale.ROOT))
            .thenComparing(b -> b.id().toString());

    private final Map<UUID, Book> store = new LinkedHashMap<>();

    @Override
    public Book save(Book book) {
        store.put(book.id(), book);
        return book;
    }

    @Override
    public Optional<Book> findById(UUID id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public Page<Book> findAll(PageRequest pageRequest) {
        return paginate(store.values().stream().toList(), pageRequest);
    }

    @Override
    public Page<Book> searchByTitle(String titleFragment, PageRequest pageRequest) {
        String needle = titleFragment.toLowerCase(Locale.ROOT);
        List<Book> matches = store.values().stream()
                .filter(b -> b.titulo().toLowerCase(Locale.ROOT).contains(needle))
                .toList();
        return paginate(matches, pageRequest);
    }

    @Override
    public Optional<Book> findByIsbn(String isbn) {
        return store.values().stream().filter(b -> isbn.equals(b.isbn())).findFirst();
    }

    @Override
    public boolean deleteById(UUID id) {
        return store.remove(id) != null;
    }

    public int count() {
        return store.size();
    }

    private static Page<Book> paginate(List<Book> books, PageRequest request) {
        List<Book> sorted = books.stream().sorted(CONTRACT_ORDER).toList();
        int from = (int) Math.min(request.offset(), sorted.size());
        int to = Math.min(from + request.size(), sorted.size());
        return new Page<>(sorted.subList(from, to), request.page(), request.size(), sorted.size());
    }
}
