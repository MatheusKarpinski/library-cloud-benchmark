package br.edu.tcc.librarycloud.application.exception;

import java.util.UUID;

/** Nenhum livro com o identificador informado. Os adaptadores traduzem para HTTP 404. */
public class BookNotFoundException extends RuntimeException {

    public BookNotFoundException(UUID id) {
        super("Livro não encontrado: " + id);
    }
}
