package br.edu.tcc.librarycloud.contract;

import br.edu.tcc.librarycloud.application.BookData;

/**
 * Corpo JSON de POST /api/books e PUT /api/books/{id}.
 * Campos ausentes chegam como {@code null}; quem valida é o núcleo, não o adaptador.
 */
public record BookRequest(
        String titulo,
        String autor,
        String isbn,
        Integer anoPublicacao,
        String categoria,
        Boolean disponivel) {

    public BookData toBookData() {
        return new BookData(titulo, autor, isbn, anoPublicacao, categoria, disponivel);
    }
}
