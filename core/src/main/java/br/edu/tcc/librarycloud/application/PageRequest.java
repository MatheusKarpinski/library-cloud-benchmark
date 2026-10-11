package br.edu.tcc.librarycloud.application;

import br.edu.tcc.librarycloud.domain.exception.FieldViolation;
import br.edu.tcc.librarycloud.domain.exception.ValidationException;

import java.util.ArrayList;
import java.util.List;

/**
 * Pedido de paginação (página começa em 0). Impede listagens ilimitadas, o que também
 * mantém os testes de carga de leitura previsíveis.
 */
public record PageRequest(int page, int size) {

    public static final int DEFAULT_PAGE = 0;
    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    public PageRequest {
        List<FieldViolation> violations = new ArrayList<>();
        if (page < 0) {
            violations.add(new FieldViolation("page", "deve ser maior ou igual a 0"));
        }
        if (size < 1 || size > MAX_SIZE) {
            violations.add(new FieldViolation("size", "deve estar entre 1 e " + MAX_SIZE));
        }
        if (!violations.isEmpty()) {
            throw new ValidationException(violations);
        }
    }

    /** Aplica os valores padrão quando o cliente não informa página ou tamanho. */
    public static PageRequest of(Integer page, Integer size) {
        return new PageRequest(
                page == null ? DEFAULT_PAGE : page,
                size == null ? DEFAULT_SIZE : size);
    }

    public long offset() {
        return (long) page * size;
    }
}
