package br.edu.tcc.librarycloud.functions;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.BindingName;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;

import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.logging.Level;

/**
 * Funções HTTP do Azure Functions. Cada método só extrai os dados da requisição, chama o
 * {@link BookApiHandler} e converte o {@link ApiResult} na resposta do runtime; nenhuma regra
 * de negócio mora aqui.
 *
 * <p>As rotas são as mesmas do adaptador de container porque {@code host.json} define
 * {@code routePrefix} vazio (sem o prefixo {@code /api} que o Functions adiciona por padrão).
 * Acesso ANÔNIMO nesta etapa (ambiente local); a proteção do endpoint será tratada na Etapa 9.
 */
public class BookFunctions {

    private static final String SEARCH_SEGMENT = "search";

    @FunctionName("health")
    public HttpResponseMessage health(
            @HttpTrigger(name = "req", methods = {HttpMethod.GET}, authLevel = AuthorizationLevel.ANONYMOUS,
                    route = "health") HttpRequestMessage<Optional<String>> request,
            final ExecutionContext context) {
        return respond(request, context, BookApiHandler::health);
    }

    @FunctionName("ready")
    public HttpResponseMessage ready(
            @HttpTrigger(name = "req", methods = {HttpMethod.GET}, authLevel = AuthorizationLevel.ANONYMOUS,
                    route = "health/ready") HttpRequestMessage<Optional<String>> request,
            final ExecutionContext context) {
        return respond(request, context, BookApiHandler::ready);
    }

    @FunctionName("listBooks")
    public HttpResponseMessage listBooks(
            @HttpTrigger(name = "req", methods = {HttpMethod.GET}, authLevel = AuthorizationLevel.ANONYMOUS,
                    route = "api/books") HttpRequestMessage<Optional<String>> request,
            final ExecutionContext context) {
        Map<String, String> query = request.getQueryParameters();
        return respond(request, context, h -> h.list(query.get("page"), query.get("size")));
    }

    /**
     * Atende {@code GET /api/books/{id}} E {@code GET /api/books/search}. Uma função separada com a
     * rota literal {@code api/books/search} NÃO funcionou: na execução local o host entregou a
     * requisição a esta função com {@code id="search"} (ver docs/troubleshooting.md, T-008). Por isso
     * a escolha entre pesquisa e consulta por id é feita aqui, no código, e não pela precedência
     * de rotas do host. Como no Spring, só o texto exato {@code search} (minúsculas) é a pesquisa.
     */
    @FunctionName("getBook")
    public HttpResponseMessage getBook(
            @HttpTrigger(name = "req", methods = {HttpMethod.GET}, authLevel = AuthorizationLevel.ANONYMOUS,
                    route = "api/books/{id}") HttpRequestMessage<Optional<String>> request,
            @BindingName("id") String id,
            final ExecutionContext context) {
        if (SEARCH_SEGMENT.equals(id)) {
            Map<String, String> query = request.getQueryParameters();
            return respond(request, context,
                    h -> h.search(query.get("title"), query.get("page"), query.get("size")));
        }
        return respond(request, context, h -> h.get(id));
    }

    @FunctionName("createBook")
    public HttpResponseMessage createBook(
            @HttpTrigger(name = "req", methods = {HttpMethod.POST}, authLevel = AuthorizationLevel.ANONYMOUS,
                    route = "api/books") HttpRequestMessage<Optional<String>> request,
            final ExecutionContext context) {
        String body = request.getBody().orElse(null);
        return respond(request, context, h -> h.create(body));
    }

    @FunctionName("updateBook")
    public HttpResponseMessage updateBook(
            @HttpTrigger(name = "req", methods = {HttpMethod.PUT}, authLevel = AuthorizationLevel.ANONYMOUS,
                    route = "api/books/{id}") HttpRequestMessage<Optional<String>> request,
            @BindingName("id") String id,
            final ExecutionContext context) {
        String body = request.getBody().orElse(null);
        return respond(request, context, h -> h.update(id, body));
    }

    @FunctionName("deleteBook")
    public HttpResponseMessage deleteBook(
            @HttpTrigger(name = "req", methods = {HttpMethod.DELETE}, authLevel = AuthorizationLevel.ANONYMOUS,
                    route = "api/books/{id}") HttpRequestMessage<Optional<String>> request,
            @BindingName("id") String id,
            final ExecutionContext context) {
        return respond(request, context, h -> h.delete(id));
    }

    // ---------- conversão ApiResult -> resposta do Functions ----------

    private static HttpResponseMessage respond(HttpRequestMessage<?> request, ExecutionContext context,
                                               Function<BookApiHandler, ApiResult> call) {
        ApiResult result;
        try {
            result = call.apply(FunctionComponents.handler());
        } catch (RuntimeException e) {
            // Falha ao montar os objetos (ex.: DB_* ausente). Detalhe só no log; resposta genérica.
            context.getLogger().log(Level.SEVERE, "Falha ao inicializar ou executar a função", e);
            result = BookApiHandler.failure(FunctionComponents.codec(), e);
        }

        var builder = request.createResponseBuilder(statusOf(result.status()));
        result.headers().forEach(builder::header);
        if (result.body() != null) {
            builder.body(result.body());
        }
        return builder.build();
    }

    /** Procura no enum o status com o código numérico; os códigos usados pela API existem todos. */
    private static HttpStatus statusOf(int code) {
        for (HttpStatus status : HttpStatus.values()) {
            if (status.value() == code) {
                return status;
            }
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }
}
