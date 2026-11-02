package br.edu.tcc.librarycloud.contract;

import br.edu.tcc.librarycloud.domain.Book;

/** Representação JSON de um livro na resposta da API. O id sai como texto (UUID). */
public record BookResponse(
        String id,
        String titulo,
        String autor,
        String isbn,
        int anoPublicacao,
        String categoria,
        boolean disponivel) {

    public static BookResponse from(Book book) {
        return new BookResponse(
                book.id().toString(),
                book.titulo(),
                book.autor(),
                book.isbn(),
                book.anoPublicacao(),
                book.categoria(),
                book.disponivel());
    }
}
