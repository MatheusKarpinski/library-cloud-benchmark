package br.edu.tcc.librarycloud.contract;

import br.edu.tcc.librarycloud.application.exception.BookNotFoundException;
import br.edu.tcc.librarycloud.application.exception.DuplicateIsbnException;
import br.edu.tcc.librarycloud.application.exception.PersistenceFailureException;
import br.edu.tcc.librarycloud.contract.ErrorResponse.Violation;
import br.edu.tcc.librarycloud.domain.exception.ValidationException;

import java.util.List;

/**
 * Tabela ÚNICA de tradução de exceções do núcleo para HTTP. Spring Boot e Azure Functions
 * chamam esta classe, então o mesmo erro vira a mesma resposta nos dois ambientes.
 *
 * <pre>
 * ValidationException         -> 400 VALIDATION_ERROR
 * BookNotFoundException       -> 404 NOT_FOUND
 * DuplicateIsbnException      -> 409 CONFLICT
 * PersistenceFailureException -> 503 SERVICE_UNAVAILABLE (detalhe técnico NÃO é exposto)
 * qualquer outra              -> 500 INTERNAL_ERROR       (detalhe técnico NÃO é exposto)
 * </pre>
 */
public final class ErrorMapper {

    private ErrorMapper() {
    }

    public static ApiError map(Throwable error) {
        if (error instanceof ValidationException ex) {
            List<Violation> violations = ex.violations().stream()
                    .map(v -> new Violation(v.field(), v.message()))
                    .toList();
            return new ApiError(400, new ErrorResponse("VALIDATION_ERROR", "Dados inválidos", violations));
        }
        if (error instanceof BookNotFoundException ex) {
            return new ApiError(404, new ErrorResponse("NOT_FOUND", ex.getMessage(), List.of()));
        }
        if (error instanceof DuplicateIsbnException ex) {
            return new ApiError(409, new ErrorResponse("CONFLICT", ex.getMessage(), List.of()));
        }
        if (error instanceof PersistenceFailureException) {
            return new ApiError(503, new ErrorResponse("SERVICE_UNAVAILABLE",
                    "Serviço temporariamente indisponível", List.of()));
        }
        return new ApiError(500, new ErrorResponse("INTERNAL_ERROR", "Erro interno inesperado", List.of()));
    }

    /** Corpo JSON ausente, malformado ou com tipos incorretos. */
    public static ApiError malformedBody() {
        return new ApiError(400, new ErrorResponse("MALFORMED_REQUEST",
                "Corpo da requisição ausente ou inválido (JSON malformado ou tipos incorretos)", List.of()));
    }

    /** Parâmetro de URL/consulta com tipo incorreto (ex.: page=abc). */
    public static ApiError invalidParameter(String name) {
        return new ApiError(400, new ErrorResponse("VALIDATION_ERROR", "Dados inválidos",
                List.of(new Violation(name, "valor com tipo inválido"))));
    }
}
