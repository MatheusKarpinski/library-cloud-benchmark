package br.edu.tcc.librarycloud.functions;

import br.edu.tcc.librarycloud.application.Page;
import br.edu.tcc.librarycloud.contract.BookRequest;
import br.edu.tcc.librarycloud.contract.BookResponse;
import br.edu.tcc.librarycloud.contract.ErrorResponse;
import br.edu.tcc.librarycloud.contract.ErrorResponse.Violation;
import br.edu.tcc.librarycloud.contract.HealthResponse;
import br.edu.tcc.librarycloud.contract.PageResponse;
import br.edu.tcc.librarycloud.contract.ReadinessResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Confere o JSON produzido e lido pelo {@link JacksonJsonCodec} contra textos literais: formato
 * compacto, campos na ordem dos componentes do registro, {@code null} explícito e acentos sem
 * escape. Os textos de saúde são os mesmos asseridos nos testes de integração do adaptador de
 * container; a equivalência completa entre os adaptadores é conferida pelo script
 * {@code scripts/api-smoke.ps1}.
 */
class JacksonJsonCodecTest {

    private final JsonCodec codec = new JacksonJsonCodec();

    @Test
    @DisplayName("saúde e readiness têm só os campos do contrato (sem campo extra 'up')")
    void serializesHealthAndReadiness() {
        assertThat(codec.toJson(HealthResponse.up())).isEqualTo("{\"status\":\"UP\"}");
        assertThat(codec.toJson(ReadinessResponse.of(true)))
                .isEqualTo("{\"status\":\"UP\",\"database\":\"UP\"}");
        assertThat(codec.toJson(ReadinessResponse.of(false)))
                .isEqualTo("{\"status\":\"DOWN\",\"database\":\"DOWN\"}");
    }

    @Test
    @DisplayName("resposta de erro: code, message e violations, com acentos sem escape")
    void serializesErrorResponse() {
        ErrorResponse error = new ErrorResponse("VALIDATION_ERROR", "Dados inválidos",
                List.of(new Violation("titulo", "é obrigatório"), new Violation("autor", "é obrigatório")));

        assertThat(codec.toJson(error)).isEqualTo(
                "{\"code\":\"VALIDATION_ERROR\",\"message\":\"Dados inválidos\",\"violations\":["
                        + "{\"field\":\"titulo\",\"message\":\"é obrigatório\"},"
                        + "{\"field\":\"autor\",\"message\":\"é obrigatório\"}]}");
        assertThat(codec.toJson(new ErrorResponse("NOT_FOUND", "Livro não encontrado", List.of())))
                .isEqualTo("{\"code\":\"NOT_FOUND\",\"message\":\"Livro não encontrado\",\"violations\":[]}");
    }

    @Test
    @DisplayName("livro: ordem dos campos, isbn nulo explícito, id como texto")
    void serializesBookResponse() {
        BookResponse book = new BookResponse("123e4567-e89b-42d3-a456-426614174000",
                "Memórias Póstumas", "Machado de Assis", null, 1881, "Romance", true);

        assertThat(codec.toJson(book)).isEqualTo(
                "{\"id\":\"123e4567-e89b-42d3-a456-426614174000\",\"titulo\":\"Memórias Póstumas\","
                        + "\"autor\":\"Machado de Assis\",\"isbn\":null,\"anoPublicacao\":1881,"
                        + "\"categoria\":\"Romance\",\"disponivel\":true}");
    }

    @Test
    @DisplayName("página: items, page, size, totalElements e totalPages")
    void serializesPageResponse() {
        PageResponse<BookResponse> empty = PageResponse.from(new Page<>(List.of(), 0, 20, 0), BookResponse::from);

        assertThat(codec.toJson(empty))
                .isEqualTo("{\"items\":[],\"page\":0,\"size\":20,\"totalElements\":0,\"totalPages\":0}");
    }

    @Test
    @DisplayName("leitura do corpo: ignora campos desconhecidos, ausentes viram null, erros viram JsonCodecException")
    void deserializesRequestBody() {
        BookRequest full = codec.fromJson("{\"titulo\":\"Dom Casmurro\",\"autor\":\"Machado de Assis\","
                + "\"isbn\":\"978-85-359-0277-5\",\"anoPublicacao\":1899,\"categoria\":\"Romance\","
                + "\"disponivel\":false,\"campoDesconhecido\":123}", BookRequest.class);
        assertThat(full).isEqualTo(new BookRequest("Dom Casmurro", "Machado de Assis",
                "978-85-359-0277-5", 1899, "Romance", false));

        BookRequest empty = codec.fromJson("{}", BookRequest.class);
        assertThat(empty).isEqualTo(new BookRequest(null, null, null, null, null, null));

        assertThat(codec.fromJson("null", BookRequest.class)).isNull();

        assertThatThrownBy(() -> codec.fromJson("{", BookRequest.class)).isInstanceOf(JsonCodecException.class);
        assertThatThrownBy(() -> codec.fromJson("{\"anoPublicacao\":\"abc\"}", BookRequest.class))
                .isInstanceOf(JsonCodecException.class);
    }
}
