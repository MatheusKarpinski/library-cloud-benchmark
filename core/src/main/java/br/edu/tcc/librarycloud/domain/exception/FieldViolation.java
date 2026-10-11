package br.edu.tcc.librarycloud.domain.exception;

import java.util.Objects;

/**
 * Descreve um campo inválido e o motivo, de forma independente de qualquer framework.
 *
 * @param field   nome do campo como aparece na API (ex.: "titulo", "anoPublicacao")
 * @param message motivo legível da invalidez
 */
public record FieldViolation(String field, String message) {

    public FieldViolation {
        Objects.requireNonNull(field, "field");
        Objects.requireNonNull(message, "message");
    }
}
