package br.edu.tcc.librarycloud.contract;

import java.util.List;

/**
 * Corpo JSON de TODAS as respostas de erro geradas pela aplicação.
 * Sem data/hora nem rastreio de pilha, para que as duas implantações produzam respostas
 * idênticas e nenhum detalhe interno vaze para o cliente.
 *
 * @param code       código estável da categoria do erro (ex.: VALIDATION_ERROR)
 * @param message    mensagem legível
 * @param violations campos inválidos (vazia quando não se aplica)
 */
public record ErrorResponse(String code, String message, List<Violation> violations) {

    public ErrorResponse {
        violations = List.copyOf(violations);
    }

    /** Um campo inválido e o motivo. */
    public record Violation(String field, String message) {
    }
}
