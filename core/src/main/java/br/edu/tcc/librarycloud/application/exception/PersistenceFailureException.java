package br.edu.tcc.librarycloud.application.exception;

/**
 * Falha técnica ao acessar a persistência (banco indisponível, timeout etc.).
 * As implementações de {@code BookRepository} devem encapsular suas exceções nesta classe,
 * para que o núcleo não conheça JDBC. Os adaptadores traduzem para HTTP 500 (ou 503).
 */
public class PersistenceFailureException extends RuntimeException {

    public PersistenceFailureException(String message, Throwable cause) {
        super(message, cause);
    }
}
