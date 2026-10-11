package br.edu.tcc.librarycloud.application.exception;

/** Já existe outro livro com o mesmo ISBN. Os adaptadores traduzem para HTTP 409. */
public class DuplicateIsbnException extends RuntimeException {

    public DuplicateIsbnException(String isbn) {
        super("Já existe um livro cadastrado com o ISBN " + isbn);
    }
}
