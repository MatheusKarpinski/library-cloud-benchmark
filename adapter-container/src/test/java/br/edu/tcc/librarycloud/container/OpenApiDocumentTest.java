package br.edu.tcc.librarycloud.container;

import br.edu.tcc.librarycloud.application.exception.BookNotFoundException;
import br.edu.tcc.librarycloud.application.exception.DuplicateIsbnException;
import br.edu.tcc.librarycloud.application.exception.PersistenceFailureException;
import br.edu.tcc.librarycloud.contract.ErrorMapper;
import br.edu.tcc.librarycloud.domain.exception.FieldViolation;
import br.edu.tcc.librarycloud.domain.exception.ValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verificação simples de consistência entre a especificação docs/openapi.yaml (escrita à mão,
 * ADR-017) e o que a aplicação implementa. Não valida o esquema completo: pega esquecimentos
 * de rota, método ou código de erro.
 */
class OpenApiDocumentTest {

    private static String spec() throws IOException {
        return Files.readString(Path.of("..", "docs", "openapi.yaml"), StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("a especificação documenta todas as rotas implementadas")
    void documentsEveryRoute() throws IOException {
        String spec = spec();

        assertThat(spec).contains("/api/books:", "/api/books/search:", "/api/books/{id}:", "/health:", "/health/ready:");
    }

    @Test
    @DisplayName("a especificação documenta todos os métodos HTTP usados")
    void documentsEveryHttpMethod() throws IOException {
        String spec = spec();

        assertThat(spec).contains("    get:", "    post:", "    put:", "    delete:");
    }

    @Test
    @DisplayName("a especificação lista todos os códigos de erro que a aplicação pode produzir")
    void documentsEveryErrorCode() throws IOException {
        String spec = spec();
        List<String> producedCodes = List.of(
                ErrorMapper.map(new ValidationException(List.of(new FieldViolation("x", "y")))).body().code(),
                ErrorMapper.map(new BookNotFoundException(UUID.randomUUID())).body().code(),
                ErrorMapper.map(new DuplicateIsbnException("1")).body().code(),
                ErrorMapper.map(new PersistenceFailureException("x", new RuntimeException())).body().code(),
                ErrorMapper.map(new IllegalStateException()).body().code(),
                ErrorMapper.malformedBody().body().code());

        for (String code : producedCodes) {
            assertThat(spec).contains(code);
        }
    }
}
