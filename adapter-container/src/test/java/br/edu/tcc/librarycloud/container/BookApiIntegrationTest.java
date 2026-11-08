package br.edu.tcc.librarycloud.container;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.net.http.HttpResponse;
import java.sql.SQLException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testa a API completa por HTTP, com a aplicação real e um PostgreSQL real.
 * Exige Docker. Executar com: mvn test -Pintegration
 */
@Tag("integration")
class BookApiIntegrationTest {

    private static RunningApplication app;

    @BeforeAll
    static void startApplication() throws Exception {
        PostgresTestContainer.applySchema();
        app = RunningApplication.start(PostgresTestContainer.applicationArgs());
    }

    @AfterAll
    static void stopApplication() {
        app.close();
    }

    @BeforeEach
    void cleanTable() throws SQLException {
        PostgresTestContainer.truncateBooks();
    }

    // ---------- utilitários ----------

    private static String bookJson(String titulo, String isbn) {
        String isbnJson = isbn == null ? "null" : "\"" + isbn + "\"";
        return "{\"titulo\":\"" + titulo + "\",\"autor\":\"Autor Teste\",\"isbn\":" + isbnJson
                + ",\"anoPublicacao\":2000,\"categoria\":\"Teste\"}";
    }

    private static String createBook(String titulo, String isbn) throws Exception {
        HttpResponse<String> response = app.post("/api/books", bookJson(titulo, isbn));
        assertThat(response.statusCode()).isEqualTo(201);
        return stringField(response.body(), "id");
    }

    private static String stringField(String json, String name) {
        Matcher matcher = Pattern.compile("\"" + name + "\":\"([^\"]*)\"").matcher(json);
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }

    private static long numberField(String json, String name) {
        Matcher matcher = Pattern.compile("\"" + name + "\":(\\d+)").matcher(json);
        assertThat(matcher.find()).isTrue();
        return Long.parseLong(matcher.group(1));
    }

    private static int occurrences(String text, String token) {
        int count = 0;
        for (int index = text.indexOf(token); index >= 0; index = text.indexOf(token, index + token.length())) {
            count++;
        }
        return count;
    }

    // ---------- saúde ----------

    @Test
    @DisplayName("GET /health responde 200 sem consultar o banco")
    void healthIsUp() throws Exception {
        HttpResponse<String> response = app.get("/health");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo("{\"status\":\"UP\"}");
    }

    @Test
    @DisplayName("GET /health/ready responde 200 com o banco disponível")
    void readinessIsUp() throws Exception {
        HttpResponse<String> response = app.get("/health/ready");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo("{\"status\":\"UP\",\"database\":\"UP\"}");
    }

    // ---------- ciclo de vida ----------

    @Test
    @DisplayName("ciclo completo: cadastrar, consultar, atualizar, excluir")
    void fullLifecycle() throws Exception {
        HttpResponse<String> created = app.post("/api/books", bookJson("Livro de Teste", "978-85-359-0277-5"));
        assertThat(created.statusCode()).isEqualTo(201);
        String id = stringField(created.body(), "id");
        assertThat(created.headers().firstValue("Location").orElse("")).isEqualTo("/api/books/" + id);
        assertThat(created.body()).contains("\"isbn\":\"9788535902775\"", "\"disponivel\":true");

        HttpResponse<String> fetched = app.get("/api/books/" + id);
        assertThat(fetched.statusCode()).isEqualTo(200);
        assertThat(fetched.body()).isEqualTo(created.body());

        String update = "{\"titulo\":\"Livro revisado\",\"autor\":\"Autora\",\"isbn\":\"9788535902775\","
                + "\"anoPublicacao\":2021,\"categoria\":\"Ensaio\",\"disponivel\":false}";
        HttpResponse<String> updated = app.put("/api/books/" + id, update);
        assertThat(updated.statusCode()).isEqualTo(200);
        assertThat(updated.body()).contains("\"id\":\"" + id + "\"", "\"titulo\":\"Livro revisado\"",
                "\"anoPublicacao\":2021", "\"categoria\":\"Ensaio\"", "\"disponivel\":false");

        HttpResponse<String> deleted = app.delete("/api/books/" + id);
        assertThat(deleted.statusCode()).isEqualTo(204);
        assertThat(deleted.body()).isEqualTo("");

        HttpResponse<String> afterDelete = app.get("/api/books/" + id);
        assertThat(afterDelete.statusCode()).isEqualTo(404);
        assertThat(afterDelete.body()).contains("\"code\":\"NOT_FOUND\"");
    }

    @Test
    @DisplayName("ISBN repetido responde 409, inclusive ao atualizar para o ISBN de outro livro")
    void duplicateIsbnIsRejected() throws Exception {
        createBook("Livro A", "9788535902775");

        HttpResponse<String> duplicate = app.post("/api/books", bookJson("Livro B", "978-85-359-0277-5"));
        assertThat(duplicate.statusCode()).isEqualTo(409);
        assertThat(duplicate.body()).contains("\"code\":\"CONFLICT\"");

        String otherId = createBook("Livro C", "9788535914849");
        HttpResponse<String> conflictOnUpdate = app.put("/api/books/" + otherId, bookJson("Livro C", "9788535902775"));
        assertThat(conflictOnUpdate.statusCode()).isEqualTo(409);
    }

    // ---------- erros ----------

    @Test
    @DisplayName("corpo sem campos obrigatórios responde 400 listando todas as violações")
    void validationErrorsAreAggregated() throws Exception {
        HttpResponse<String> response = app.post("/api/books", "{}");

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("\"code\":\"VALIDATION_ERROR\"", "\"field\":\"titulo\"",
                "\"field\":\"autor\"", "\"field\":\"categoria\"", "\"field\":\"anoPublicacao\"");
    }

    @Test
    @DisplayName("JSON malformado ou com tipo incorreto responde 400 MALFORMED_REQUEST")
    void malformedBodyIsRejected() throws Exception {
        HttpResponse<String> broken = app.post("/api/books", "{");
        HttpResponse<String> wrongType = app.post("/api/books", "{\"anoPublicacao\":\"abc\"}");

        assertThat(broken.statusCode()).isEqualTo(400);
        assertThat(broken.body()).contains("\"code\":\"MALFORMED_REQUEST\"");
        assertThat(wrongType.statusCode()).isEqualTo(400);
        assertThat(wrongType.body()).contains("\"code\":\"MALFORMED_REQUEST\"");
    }

    @Test
    @DisplayName("id inválido responde 400; id inexistente responde 404 (inclusive em PUT antes de validar o corpo)")
    void invalidAndUnknownIds() throws Exception {
        String unknown = "00000000-0000-0000-0000-000000000000";

        HttpResponse<String> invalid = app.get("/api/books/abc");
        assertThat(invalid.statusCode()).isEqualTo(400);
        assertThat(invalid.body()).contains("\"field\":\"id\"");

        assertThat(app.get("/api/books/" + unknown).statusCode()).isEqualTo(404);
        assertThat(app.put("/api/books/" + unknown, "{\"titulo\":\"X\"}").statusCode()).isEqualTo(404);
        assertThat(app.delete("/api/books/" + unknown).statusCode()).isEqualTo(404);
    }

    @Test
    @DisplayName("parâmetros de paginação inválidos respondem 400")
    void invalidPaginationParameters() throws Exception {
        HttpResponse<String> notANumber = app.get("/api/books?page=abc");
        HttpResponse<String> negative = app.get("/api/books?page=-1");
        HttpResponse<String> tooBig = app.get("/api/books?size=1000");

        assertThat(notANumber.statusCode()).isEqualTo(400);
        assertThat(notANumber.body()).contains("\"field\":\"page\"");
        assertThat(negative.statusCode()).isEqualTo(400);
        assertThat(negative.body()).contains("\"field\":\"page\"");
        assertThat(tooBig.statusCode()).isEqualTo(400);
        assertThat(tooBig.body()).contains("\"field\":\"size\"");
    }

    @Test
    @DisplayName("rota inexistente responde 404 (formato do framework)")
    void unknownRoute() throws Exception {
        assertThat(app.get("/api/nada").statusCode()).isEqualTo(404);
    }

    // ---------- listagem e pesquisa ----------

    @Test
    @DisplayName("listagem é ordenada por título e paginada, com totais corretos")
    void listingIsOrderedAndPaginated() throws Exception {
        for (String title : new String[] {"Livro C", "Livro A", "Livro E", "Livro B", "Livro D"}) {
            createBook(title, null);
        }

        HttpResponse<String> firstPage = app.get("/api/books?page=0&size=2");
        assertThat(firstPage.statusCode()).isEqualTo(200);
        assertThat(numberField(firstPage.body(), "totalElements")).isEqualTo(5L);
        assertThat(numberField(firstPage.body(), "totalPages")).isEqualTo(3L);
        assertThat(occurrences(firstPage.body(), "\"id\":")).isEqualTo(2);
        assertThat(firstPage.body().indexOf("Livro A") < firstPage.body().indexOf("Livro B")).isTrue();
        assertThat(firstPage.body()).doesNotContain("Livro C");

        HttpResponse<String> lastPage = app.get("/api/books?page=2&size=2");
        assertThat(occurrences(lastPage.body(), "\"id\":")).isEqualTo(1);
        assertThat(lastPage.body()).contains("Livro E");

        HttpResponse<String> defaults = app.get("/api/books");
        assertThat(numberField(defaults.body(), "size")).isEqualTo(20L);
        assertThat(occurrences(defaults.body(), "\"id\":")).isEqualTo(5);
    }

    @Test
    @DisplayName("pesquisa por título encontra trechos sem diferenciar maiúsculas e valida o termo")
    void searchByTitle() throws Exception {
        createBook("Dom Casmurro", null);
        createBook("Dom Quixote", null);
        createBook("Memorias Postumas", null);

        HttpResponse<String> found = app.get("/api/books/search?title=DOM");
        assertThat(found.statusCode()).isEqualTo(200);
        assertThat(numberField(found.body(), "totalElements")).isEqualTo(2L);
        assertThat(found.body()).doesNotContain("Memorias");

        HttpResponse<String> none = app.get("/api/books/search?title=inexistente");
        assertThat(numberField(none.body(), "totalElements")).isEqualTo(0L);

        HttpResponse<String> blank = app.get("/api/books/search?title=%20%20");
        assertThat(blank.statusCode()).isEqualTo(400);
        assertThat(blank.body()).contains("\"field\":\"title\"");

        HttpResponse<String> missing = app.get("/api/books/search");
        assertThat(missing.statusCode()).isEqualTo(400);
    }
}
