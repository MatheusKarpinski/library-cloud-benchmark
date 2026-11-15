package br.edu.tcc.librarycloud.functions;

import java.util.Map;

/**
 * Resultado de um caso de uso HTTP, independente do runtime do Azure Functions: status, corpo
 * (texto JSON, ou {@code null} quando não há corpo) e cabeçalhos extras. A classe
 * {@link BookFunctions} converte este objeto na resposta do Functions.
 */
public record ApiResult(int status, String body, Map<String, String> headers) {

    public ApiResult {
        headers = Map.copyOf(headers);
    }

    public static ApiResult of(int status, String body) {
        return new ApiResult(status, body, Map.of());
    }

    public static ApiResult noContent() {
        return new ApiResult(204, null, Map.of());
    }
}
