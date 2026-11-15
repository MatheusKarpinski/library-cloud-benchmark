package br.edu.tcc.librarycloud.functions;

import br.edu.tcc.librarycloud.application.BookService;
import br.edu.tcc.librarycloud.application.Page;
import br.edu.tcc.librarycloud.application.PageRequest;
import br.edu.tcc.librarycloud.functions.support.FailingBookRepository;
import br.edu.tcc.librarycloud.functions.support.InMemoryBookRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testa a lógica HTTP do adaptador Functions SEM o runtime do Azure e SEM banco: o
 * {@link BookApiHandler} real, o serviço real e um repositório em memória apenas de teste.
 * Os cenários espelham os de {@code BookApiIntegrationTest} (adaptador de container).
 */
class BookApiHandlerTest {

    private static final String UNKNOWN_ID = "00000000-0000-0000-0000-000000000000";

    private final InMemoryBookRepository repository = new InMemoryBookRepository();
    private final BookApiHandler handler =
            new BookApiHandler(new BookService(repository), new JacksonJsonCodec(), () -> true);

    // ---------- utilitários ----------

    private static String bookJson(String titulo, String isbn) {
        String isbnJson = isbn == null ? "null" : "\"" + isbn + "\"";
        return "{\"titulo\":\"" + titulo + "\",\"autor\":\"Autor Teste\",\"isbn\":" + isbnJson
                + ",\"anoPublicacao\":2000,\"categoria\":\"Teste\"}";
    }

    private ApiResult createBook(String titulo, String isbn) {
        ApiResult result = handler.create(bookJson(titulo, isbn));
        assertThat(result.status()).isEqualTo(201);
        return result;
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
    @DisplayName("health responde 200 {\"status\":\"UP\"} em JSON")
    void healthIsUp() {
        ApiResult result = handler.health();

        assertThat(result.status()).isEqualTo(200);
        assertThat(result.body()).isEqualTo("{\"status\":\"UP\"}");
        assertThat(result.headers()).containsEntry("Content-Type", "application/json");
    }

    @Test
    @DisplayName("ready responde 200 com o banco disponível e 503 DOWN sem ele")
    void readinessFollowsDatabase() {
        ApiResult up = handler.ready();
        assertThat(up.status()).isEqualTo(200);
        assertThat(up.body()).isEqualTo("{\"status\":\"UP\",\"database\":\"UP\"}");

        BookApiHandler databaseDown =
                new BookApiHandler(new BookService(repository), new JacksonJsonCodec(), () -> false);
        ApiResult down = databaseDown.ready();
        assertThat(down.status()).isEqualTo(503);
        assertThat(down.body()).isEqualTo("{\"status\":\"DOWN\",\"database\":\"DOWN\"}");
    }

    // ---------- criação ----------

    @Test
    @DisplayName("POST válido responde 201 com Location, ISBN normalizado e disponivel padrão true")
    void createReturns201() {
        ApiResult created = createBook("Livro de Teste", "978-85-359-0277-5");

        String id = stringField(created.body(), "id");
        assertThat(created.headers()).containsEntry("Location", "/api/books/" + id);
        assertThat(created.headers()).containsEntry("Content-Type", "application/json");
        assertThat(created.body()).contains("\"isbn\":\"9788535902775\"", "\"disponivel\":true");
    }

    @Test
    @DisplayName("corpo sem campos obrigatórios responde 400 listando todas as violações")
    void validationErrorsAreAggregated() {
        ApiResult result = handler.create("{}");

        assertThat(result.status()).isEqualTo(400);
        assertThat(result.body()).contains("\"code\":\"VALIDATION_ERROR\"", "\"field\":\"titulo\"",
                "\"field\":\"autor\"", "\"field\":\"categoria\"", "\"field\":\"anoPublicacao\"");
    }

    @Test
    @DisplayName("corpo ausente, em branco, null, malformado ou com tipo incorreto responde 400 MALFORMED_REQUEST")
    void malformedBodiesAreRejected() {
        String[] bodies = {null, "", "   ", "{", "null", "[]", "{\"anoPublicacao\":\"abc\"}"};

        for (String body : bodies) {
            ApiResult result = handler.create(body);
            assertThat(result.status()).as("corpo: %s", body).isEqualTo(400);
            assertThat(result.body()).as("corpo: %s", body).contains("\"code\":\"MALFORMED_REQUEST\"");
        }
    }

    @Test
    @DisplayName("ISBN repetido responde 409, inclusive ao atualizar para o ISBN de outro livro")
    void duplicateIsbnIsRejected() {
        createBook("Livro A", "9788535902775");

        ApiResult duplicate = handler.create(bookJson("Livro B", "978-85-359-0277-5"));
        assertThat(duplicate.status()).isEqualTo(409);
        assertThat(duplicate.body()).contains("\"code\":\"CONFLICT\"");

        String otherId = stringField(createBook("Livro C", "9788535914849").body(), "id");
        ApiResult conflictOnUpdate = handler.update(otherId, bookJson("Livro C", "9788535902775"));
        assertThat(conflictOnUpdate.status()).isEqualTo(409);
    }

    // ---------- consulta, atualização e exclusão ----------

    @Test
    @DisplayName("GET por id devolve o mesmo corpo da criação")
    void getReturnsCreatedBook() {
        ApiResult created = createBook("Livro de Teste", null);
        String id = stringField(created.body(), "id");

        ApiResult fetched = handler.get(id);

        assertThat(fetched.status()).isEqualTo(200);
        assertThat(fetched.body()).isEqualTo(created.body());
    }

    @Test
    @DisplayName("id mal formado responde 400 (campo id); id inexistente responde 404 NOT_FOUND")
    void invalidAndUnknownIds() {
        ApiResult invalid = handler.get("abc");
        assertThat(invalid.status()).isEqualTo(400);
        assertThat(invalid.body()).contains("\"field\":\"id\"");

        ApiResult unknown = handler.get(UNKNOWN_ID);
        assertThat(unknown.status()).isEqualTo(404);
        assertThat(unknown.body()).contains("\"code\":\"NOT_FOUND\"");
    }

    @Test
    @DisplayName("PUT substitui os dados e preserva o id")
    void updateReplacesBook() {
        String id = stringField(createBook("Livro de Teste", "978-85-359-0277-5").body(), "id");
        String update = "{\"titulo\":\"Livro revisado\",\"autor\":\"Autora\",\"isbn\":\"9788535902775\","
                + "\"anoPublicacao\":2021,\"categoria\":\"Ensaio\",\"disponivel\":false}";

        ApiResult updated = handler.update(id, update);

        assertThat(updated.status()).isEqualTo(200);
        assertThat(updated.body()).contains("\"id\":\"" + id + "\"", "\"titulo\":\"Livro revisado\"",
                "\"anoPublicacao\":2021", "\"categoria\":\"Ensaio\"", "\"disponivel\":false");
    }

    @Test
    @DisplayName("ordem no PUT: JSON malformado (400) antes de tudo; depois id válido, existe (404) e corpo válido")
    void updateCheckOrder() {
        String existingId = stringField(createBook("Livro de Teste", null).body(), "id");

        // JSON ilegível é rejeitado antes do serviço, mesmo para id inexistente (como o Spring).
        ApiResult brokenBody = handler.update(UNKNOWN_ID, "{");
        assertThat(brokenBody.status()).isEqualTo(400);
        assertThat(brokenBody.body()).contains("\"code\":\"MALFORMED_REQUEST\"");

        // JSON legível porém incompleto em id inexistente: 404 vem antes da validação do corpo.
        assertThat(handler.update(UNKNOWN_ID, "{\"titulo\":\"X\"}").status()).isEqualTo(404);

        // id mal formado vem antes de existir/validar.
        ApiResult invalidId = handler.update("abc", "{}");
        assertThat(invalidId.status()).isEqualTo(400);
        assertThat(invalidId.body()).contains("\"field\":\"id\"");

        // id existente com corpo incompleto: validação de dados.
        ApiResult invalidData = handler.update(existingId, "{}");
        assertThat(invalidData.status()).isEqualTo(400);
        assertThat(invalidData.body()).contains("\"code\":\"VALIDATION_ERROR\"");
    }

    @Test
    @DisplayName("DELETE responde 204 sem corpo; repetir ou usar id inexistente responde 404; id inválido 400")
    void deleteBook() {
        String id = stringField(createBook("Livro de Teste", null).body(), "id");

        ApiResult deleted = handler.delete(id);
        assertThat(deleted.status()).isEqualTo(204);
        assertThat(deleted.body()).isNull();

        assertThat(handler.get(id).status()).isEqualTo(404);
        assertThat(handler.delete(id).status()).isEqualTo(404);
        assertThat(handler.delete(UNKNOWN_ID).status()).isEqualTo(404);
        assertThat(handler.delete("abc").status()).isEqualTo(400);
    }

    // ---------- listagem e pesquisa ----------

    @Test
    @DisplayName("listagem ordenada por título, paginada, com totais; page/size vazios usam o padrão")
    void listingIsOrderedAndPaginated() {
        for (String title : new String[] {"Livro C", "Livro A", "Livro E", "Livro B", "Livro D"}) {
            createBook(title, null);
        }

        ApiResult firstPage = handler.list("0", "2");
        assertThat(firstPage.status()).isEqualTo(200);
        assertThat(numberField(firstPage.body(), "totalElements")).isEqualTo(5L);
        assertThat(numberField(firstPage.body(), "totalPages")).isEqualTo(3L);
        assertThat(occurrences(firstPage.body(), "\"id\":")).isEqualTo(2);
        assertThat(firstPage.body().indexOf("Livro A") < firstPage.body().indexOf("Livro B")).isTrue();
        assertThat(firstPage.body()).doesNotContain("Livro C");

        ApiResult lastPage = handler.list("2", "2");
        assertThat(occurrences(lastPage.body(), "\"id\":")).isEqualTo(1);
        assertThat(lastPage.body()).contains("Livro E");

        for (ApiResult defaults : new ApiResult[] {handler.list(null, null), handler.list("", "")}) {
            assertThat(numberField(defaults.body(), "page")).isEqualTo((long) PageRequest.DEFAULT_PAGE);
            assertThat(numberField(defaults.body(), "size")).isEqualTo((long) PageRequest.DEFAULT_SIZE);
            assertThat(occurrences(defaults.body(), "\"id\":")).isEqualTo(5);
        }
    }

    @Test
    @DisplayName("parâmetros inválidos respondem 400 com o campo; pesquisa valida o termo e ignora maiúsculas")
    void invalidParametersAndSearch() {
        ApiResult notANumber = handler.list("abc", null);
        assertThat(notANumber.status()).isEqualTo(400);
        assertThat(notANumber.body()).contains("\"field\":\"page\"", "valor com tipo inválido");

        ApiResult negative = handler.list("-1", null);
        assertThat(negative.status()).isEqualTo(400);
        assertThat(negative.body()).contains("\"field\":\"page\"");

        ApiResult tooBig = handler.list(null, "1000");
        assertThat(tooBig.status()).isEqualTo(400);
        assertThat(tooBig.body()).contains("\"field\":\"size\"");

        // Com page e size inválidos ao mesmo tempo, só o primeiro (page) é informado, como no Spring.
        ApiResult both = handler.list("abc", "xyz");
        assertThat(both.body()).contains("\"field\":\"page\"").doesNotContain("\"field\":\"size\"");

        createBook("Dom Casmurro", null);
        createBook("Dom Quixote", null);
        createBook("Memorias Postumas", null);

        ApiResult found = handler.search("DOM", null, null);
        assertThat(found.status()).isEqualTo(200);
        assertThat(numberField(found.body(), "totalElements")).isEqualTo(2L);
        assertThat(found.body()).doesNotContain("Memorias");
        assertThat(numberField(handler.search("inexistente", null, null).body(), "totalElements")).isEqualTo(0L);

        ApiResult blank = handler.search("  ", null, null);
        assertThat(blank.status()).isEqualTo(400);
        assertThat(blank.body()).contains("\"field\":\"title\"");
        assertThat(handler.search(null, null, null).status()).isEqualTo(400);

        // page inválido na pesquisa é verificado antes do termo.
        ApiResult badPage = handler.search(null, "abc", null);
        assertThat(badPage.body()).contains("\"field\":\"page\"");
    }

    // ---------- falhas ----------

    @Test
    @DisplayName("falha de persistência responde 503 e erro inesperado responde 500, sem detalhes técnicos")
    void failuresDoNotLeakDetails() {
        BookApiHandler databaseDown =
                new BookApiHandler(new BookService(new FailingBookRepository()), new JacksonJsonCodec(), () -> false);

        ApiResult unavailable = databaseDown.list(null, null);
        assertThat(unavailable.status()).isEqualTo(503);
        assertThat(unavailable.body()).contains("\"code\":\"SERVICE_UNAVAILABLE\"")
                .doesNotContain("simulado", "conexão", "Exception");

        InMemoryBookRepository unexpected = new InMemoryBookRepository() {
            @Override
            public Page<br.edu.tcc.librarycloud.domain.Book> findAll(PageRequest pageRequest) {
                throw new IllegalStateException("segredo-interno");
            }
        };
        BookApiHandler broken = new BookApiHandler(new BookService(unexpected), new JacksonJsonCodec(), () -> true);

        ApiResult internal = broken.list(null, null);
        assertThat(internal.status()).isEqualTo(500);
        assertThat(internal.body()).contains("\"code\":\"INTERNAL_ERROR\"").doesNotContain("segredo-interno");
    }
}
