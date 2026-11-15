package br.edu.tcc.librarycloud.functions;

/** JSON de entrada malformado ou com tipos incorretos, ou objeto que não pôde ser serializado. */
public class JsonCodecException extends RuntimeException {

    public JsonCodecException(String message, Throwable cause) {
        super(message, cause);
    }
}
