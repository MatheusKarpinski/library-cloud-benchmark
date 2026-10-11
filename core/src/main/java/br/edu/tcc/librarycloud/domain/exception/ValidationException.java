package br.edu.tcc.librarycloud.domain.exception;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Lançada quando os dados de entrada violam as regras do domínio ou do caso de uso.
 * Reúne TODAS as violações encontradas, para que o cliente corrija tudo de uma vez.
 *
 * <p>Os adaptadores (Spring Boot e Azure Functions) traduzem esta exceção para HTTP 400.
 */
public class ValidationException extends RuntimeException {

    private final transient List<FieldViolation> violations;

    public ValidationException(List<FieldViolation> violations) {
        super(buildMessage(violations));
        this.violations = List.copyOf(violations);
    }

    public List<FieldViolation> violations() {
        return violations;
    }

    private static String buildMessage(List<FieldViolation> violations) {
        return violations.stream()
                .map(v -> v.field() + " " + v.message())
                .collect(Collectors.joining("; ", "Dados inválidos: ", ""));
    }
}
