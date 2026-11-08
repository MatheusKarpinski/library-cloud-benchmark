package br.edu.tcc.librarycloud.container;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Aplicação real apontada para um banco inexistente: verifica o comportamento sob falha
 * (a aplicação sobe, /health segue 200, os demais endpoints respondem 503 sem detalhes técnicos).
 * Não precisa de container, mas sobe o Spring completo, por isso também é de integração.
 */
@Tag("integration")
class BookApiDatabaseDownIntegrationTest {

    private static RunningApplication app;

    @BeforeAll
    static void startApplicationWithUnreachableDatabase() throws Exception {
        app = RunningApplication.start(
                "--DB_URL=jdbc:postgresql://localhost:1/inexistente",
                "--DB_USER=usuario-de-teste",
                "--DB_PASSWORD=senha-de-teste",
                "--DB_POOL_MAX=1",
                "--DB_POOL_MIN_IDLE=0",
                "--DB_CONNECTION_TIMEOUT_MS=250");
    }

    @AfterAll
    static void stopApplication() {
        app.close();
    }

    @Test
    @DisplayName("a aplicação sobe e /health responde 200 mesmo sem banco")
    void healthStaysUp() throws Exception {
        HttpResponse<String> response = app.get("/health");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo("{\"status\":\"UP\"}");
    }

    @Test
    @DisplayName("/health/ready responde 503 informando banco indisponível")
    void readinessReportsDown() throws Exception {
        HttpResponse<String> response = app.get("/health/ready");

        assertThat(response.statusCode()).isEqualTo(503);
        assertThat(response.body()).isEqualTo("{\"status\":\"DOWN\",\"database\":\"DOWN\"}");
    }

    @Test
    @DisplayName("endpoints de dados respondem 503 sem expor detalhes técnicos")
    void dataEndpointsReturn503WithoutTechnicalDetails() throws Exception {
        HttpResponse<String> list = app.get("/api/books");
        HttpResponse<String> create = app.post("/api/books",
                "{\"titulo\":\"Livro\",\"autor\":\"Autor\",\"anoPublicacao\":2000,\"categoria\":\"Teste\"}");

        for (HttpResponse<String> response : java.util.List.of(list, create)) {
            assertThat(response.statusCode()).isEqualTo(503);
            assertThat(response.body()).contains("\"code\":\"SERVICE_UNAVAILABLE\"");
            assertThat(response.body()).doesNotContain("jdbc", "localhost", "senha-de-teste", "usuario-de-teste", "Exception");
        }
    }
}
