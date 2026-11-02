package br.edu.tcc.librarycloud.contract;

import br.edu.tcc.librarycloud.application.exception.BookNotFoundException;
import br.edu.tcc.librarycloud.application.exception.DuplicateIsbnException;
import br.edu.tcc.librarycloud.application.exception.PersistenceFailureException;
import br.edu.tcc.librarycloud.domain.exception.FieldViolation;
import br.edu.tcc.librarycloud.domain.exception.ValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorMapperTest {

    @Test
    @DisplayName("ValidationException vira 400 com a lista de campos inválidos")
    void validationBecomes400() {
        ApiError error = ErrorMapper.map(new ValidationException(List.of(
                new FieldViolation("titulo", "é obrigatório"),
                new FieldViolation("isbn", "formato inválido"))));

        assertThat(error.status()).isEqualTo(400);
        assertThat(error.body().code()).isEqualTo("VALIDATION_ERROR");
        assertThat(error.body().violations()).hasSize(2);
        assertThat(error.body().violations().get(0).field()).isEqualTo("titulo");
        assertThat(error.body().violations().get(1).message()).isEqualTo("formato inválido");
    }

    @Test
    @DisplayName("BookNotFoundException vira 404")
    void notFoundBecomes404() {
        ApiError error = ErrorMapper.map(new BookNotFoundException(UUID.randomUUID()));

        assertThat(error.status()).isEqualTo(404);
        assertThat(error.body().code()).isEqualTo("NOT_FOUND");
        assertThat(error.body().violations()).isEmpty();
    }

    @Test
    @DisplayName("DuplicateIsbnException vira 409")
    void duplicateBecomes409() {
        ApiError error = ErrorMapper.map(new DuplicateIsbnException("9788535902775"));

        assertThat(error.status()).isEqualTo(409);
        assertThat(error.body().code()).isEqualTo("CONFLICT");
    }

    @Test
    @DisplayName("PersistenceFailureException vira 503 sem expor o detalhe técnico")
    void persistenceFailureBecomes503WithoutLeakingDetails() {
        ApiError error = ErrorMapper.map(new PersistenceFailureException(
                "senha do banco incorreta na conexão X", new IllegalStateException("segredo")));

        assertThat(error.status()).isEqualTo(503);
        assertThat(error.body().code()).isEqualTo("SERVICE_UNAVAILABLE");
        assertThat(error.body().message().contains("senha")).isFalse();
        assertThat(error.body().message().contains("segredo")).isFalse();
    }

    @Test
    @DisplayName("exceção inesperada vira 500 sem expor a mensagem original")
    void unexpectedBecomes500WithoutLeakingDetails() {
        ApiError error = ErrorMapper.map(new IllegalStateException("detalhe interno"));

        assertThat(error.status()).isEqualTo(500);
        assertThat(error.body().code()).isEqualTo("INTERNAL_ERROR");
        assertThat(error.body().message().contains("detalhe interno")).isFalse();
    }

    @Test
    @DisplayName("corpo malformado e parâmetro inválido viram 400")
    void malformedBodyAndInvalidParameterBecome400() {
        ApiError body = ErrorMapper.malformedBody();
        ApiError param = ErrorMapper.invalidParameter("page");

        assertThat(body.status()).isEqualTo(400);
        assertThat(body.body().code()).isEqualTo("MALFORMED_REQUEST");
        assertThat(param.status()).isEqualTo(400);
        assertThat(param.body().violations().get(0).field()).isEqualTo("page");
    }
}
