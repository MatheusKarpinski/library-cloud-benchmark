package br.edu.tcc.librarycloud.application.support;

import br.edu.tcc.librarycloud.application.Page;
import br.edu.tcc.librarycloud.application.PageRequest;
import br.edu.tcc.librarycloud.application.exception.PersistenceFailureException;
import br.edu.tcc.librarycloud.application.port.BookRepository;
import br.edu.tcc.librarycloud.domain.Book;

import java.util.Optional;
import java.util.UUID;

/** Repositório de teste que simula um banco indisponível: toda operação falha. */
public class FailingBookRepository implements BookRepository {

    private static PersistenceFailureException failure() {
        return new PersistenceFailureException("banco indisponível (simulado)",
                new IllegalStateException("conexão recusada"));
    }

    @Override
    public Book save(Book book) {
        throw failure();
    }

    @Override
    public Optional<Book> findById(UUID id) {
        throw failure();
    }

    @Override
    public Page<Book> findAll(PageRequest pageRequest) {
        throw failure();
    }

    @Override
    public Page<Book> searchByTitle(String titleFragment, PageRequest pageRequest) {
        throw failure();
    }

    @Override
    public Optional<Book> findByIsbn(String isbn) {
        throw failure();
    }

    @Override
    public boolean deleteById(UUID id) {
        throw failure();
    }
}
