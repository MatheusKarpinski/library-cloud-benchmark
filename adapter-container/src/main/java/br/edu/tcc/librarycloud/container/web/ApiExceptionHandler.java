package br.edu.tcc.librarycloud.container.web;

import br.edu.tcc.librarycloud.application.exception.BookNotFoundException;
import br.edu.tcc.librarycloud.application.exception.DuplicateIsbnException;
import br.edu.tcc.librarycloud.application.exception.PersistenceFailureException;
import br.edu.tcc.librarycloud.contract.ApiError;
import br.edu.tcc.librarycloud.contract.ErrorMapper;
import br.edu.tcc.librarycloud.contract.ErrorResponse;
import br.edu.tcc.librarycloud.domain.exception.ValidationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Tratamento centralizado de erros. A tradução exceção -> (status, corpo) vem de
 * {@link ErrorMapper}, compartilhado com a versão Azure Functions.
 *
 * <p>Erros gerados pelo próprio framework (rota inexistente, método não permitido, tipo de
 * conteúdo não suportado) continuam com o formato padrão do Spring: é uma diferença
 * inevitável entre os adaptadores e está documentada em docs/architecture.md.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final System.Logger LOG = System.getLogger(ApiExceptionHandler.class.getName());

    @ExceptionHandler({
            ValidationException.class,
            BookNotFoundException.class,
            DuplicateIsbnException.class,
            PersistenceFailureException.class})
    public ResponseEntity<ErrorResponse> handleApplicationErrors(RuntimeException exception) {
        ApiError error = ErrorMapper.map(exception);
        if (error.status() >= 500) {
            // O detalhe técnico fica no log; o cliente recebe só a mensagem genérica.
            LOG.log(System.Logger.Level.ERROR, "Falha ao processar a requisição", exception);
        }
        return toResponse(error);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableBody(HttpMessageNotReadableException exception) {
        return toResponse(ErrorMapper.malformedBody());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException exception) {
        return toResponse(ErrorMapper.invalidParameter(exception.getName()));
    }

    private static ResponseEntity<ErrorResponse> toResponse(ApiError error) {
        return ResponseEntity.status(error.status()).body(error.body());
    }
}
