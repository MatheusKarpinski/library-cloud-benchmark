package br.edu.tcc.librarycloud.functions;

import br.edu.tcc.librarycloud.application.BookService;
import br.edu.tcc.librarycloud.application.PageRequest;
import br.edu.tcc.librarycloud.contract.ApiError;
import br.edu.tcc.librarycloud.contract.BookRequest;
import br.edu.tcc.librarycloud.contract.BookResponse;
import br.edu.tcc.librarycloud.contract.ErrorMapper;
import br.edu.tcc.librarycloud.contract.HealthResponse;
import br.edu.tcc.librarycloud.contract.PageResponse;
import br.edu.tcc.librarycloud.contract.ReadinessResponse;
import br.edu.tcc.librarycloud.domain.Book;

import java.util.Map;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Lógica HTTP do adaptador Functions, SEM dependência do runtime do Azure: recebe e devolve
 * texto e números ({@link ApiResult}). Isso permite testá-la sem o Functions e mantém as
 * funções de {@link BookFunctions} finas, como os controllers do adaptador de container.
 *
 * <p>A ordem das verificações imita a do adaptador Spring (ADR-020):
 * <ol>
 *   <li>corpo JSON lido (400 {@code MALFORMED_REQUEST}) ANTES de qualquer regra do serviço, como o
 *       Spring faz ao resolver {@code @RequestBody};</li>
 *   <li>conversão de {@code page} e {@code size} (400 com o nome do parâmetro);</li>
 *   <li>{@link PageRequest} (limites) e, por fim, o {@link BookService}.</li>
 * </ol>
 * Exceções do núcleo viram HTTP em {@link ErrorMapper}, a mesma tabela do adaptador Spring.
 */
public final class BookApiHandler {

    private static final System.Logger LOG = System.getLogger(BookApiHandler.class.getName());
    private static final String JSON = "application/json";

    private final BookService bookService;
    private final JsonCodec codec;
    private final BooleanSupplier databaseAvailable;

    public BookApiHandler(BookService bookService, JsonCodec codec, BooleanSupplier databaseAvailable) {
        this.bookService = Objects.requireNonNull(bookService, "bookService");
        this.codec = Objects.requireNonNull(codec, "codec");
        this.databaseAvailable = Objects.requireNonNull(databaseAvailable, "databaseAvailable");
    }

    // ---------- saúde ----------

    /** Liveness: não consulta o banco. */
    public ApiResult health() {
        return execute(() -> json(200, HealthResponse.up()));
    }

    /** Readiness: consulta o banco; 200 se disponível, 503 se não. */
    public ApiResult ready() {
        return execute(() -> {
            ReadinessResponse readiness = ReadinessResponse.of(databaseAvailable.getAsBoolean());
            return json(readiness.up() ? 200 : 503, readiness);
        });
    }

    // ---------- livros ----------

    public ApiResult list(String page, String size) {
        return execute(() -> {
            Integer pageNumber = parseInteger("page", page);
            Integer pageSize = parseInteger("size", size);
            PageRequest request = PageRequest.of(pageNumber, pageSize);
            return json(200, PageResponse.from(bookService.list(request), BookResponse::from));
        });
    }

    public ApiResult search(String title, String page, String size) {
        return execute(() -> {
            Integer pageNumber = parseInteger("page", page);
            Integer pageSize = parseInteger("size", size);
            PageRequest request = PageRequest.of(pageNumber, pageSize);
            return json(200, PageResponse.from(bookService.searchByTitle(title, request), BookResponse::from));
        });
    }

    public ApiResult get(String id) {
        return execute(() -> json(200, BookResponse.from(bookService.getById(id))));
    }

    public ApiResult create(String body) {
        return execute(() -> {
            BookRequest request = readBody(body);
            Book created = bookService.create(request.toBookData());
            return json(201, BookResponse.from(created), Map.of("Location", "/api/books/" + created.id()));
        });
    }

    public ApiResult update(String id, String body) {
        return execute(() -> {
            BookRequest request = readBody(body);
            return json(200, BookResponse.from(bookService.update(id, request.toBookData())));
        });
    }

    public ApiResult delete(String id) {
        return execute(() -> {
            bookService.delete(id);
            return ApiResult.noContent();
        });
    }

    /**
     * Resposta para uma falha inesperada ocorrida FORA do tratador (ex.: erro ao montar os objetos
     * na primeira requisição). Usa a mesma tabela de {@link ErrorMapper}: o detalhe técnico vai só
     * para o log, nunca para o cliente.
     */
    public static ApiResult failure(JsonCodec codec, Throwable cause) {
        ApiError error = ErrorMapper.map(cause);
        if (error.status() >= 500) {
            LOG.log(System.Logger.Level.ERROR, "Falha ao processar a requisição", cause);
        }
        try {
            return new ApiResult(error.status(), codec.toJson(error.body()), Map.of("Content-Type", JSON));
        } catch (RuntimeException serializationFailure) {
            LOG.log(System.Logger.Level.ERROR, "Falha ao serializar a resposta de erro", serializationFailure);
            return new ApiResult(500,
                    "{\"code\":\"INTERNAL_ERROR\",\"message\":\"Erro interno inesperado\",\"violations\":[]}",
                    Map.of("Content-Type", JSON));
        }
    }

    // ---------- internos ----------

    private ApiResult execute(Supplier<ApiResult> action) {
        try {
            return action.get();
        } catch (MalformedBody e) {
            return error(ErrorMapper.malformedBody());
        } catch (InvalidParameter e) {
            return error(ErrorMapper.invalidParameter(e.name()));
        } catch (RuntimeException e) {
            return failure(codec, e);
        }
    }

    private ApiResult json(int status, Object body) {
        return json(status, body, Map.of());
    }

    private ApiResult json(int status, Object body, Map<String, String> extraHeaders) {
        Map<String, String> headers = new java.util.LinkedHashMap<>();
        headers.put("Content-Type", JSON);
        headers.putAll(extraHeaders);
        return new ApiResult(status, codec.toJson(body), headers);
    }

    private ApiResult error(ApiError error) {
        return json(error.status(), error.body());
    }

    /** Corpo ausente, em branco, {@code null}, malformado ou com tipos incorretos => MALFORMED_REQUEST. */
    private BookRequest readBody(String body) {
        if (body == null || body.isBlank()) {
            throw new MalformedBody();
        }
        BookRequest request;
        try {
            request = codec.fromJson(body, BookRequest.class);
        } catch (JsonCodecException e) {
            throw new MalformedBody();
        }
        if (request == null) {
            throw new MalformedBody();
        }
        return request;
    }

    /** Parâmetro ausente ou vazio usa o padrão (retorna null); texto não numérico => 400. */
    private static Integer parseInteger(String name, String raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(raw.strip());
        } catch (NumberFormatException e) {
            throw new InvalidParameter(name);
        }
    }

    private static final class MalformedBody extends RuntimeException {
        MalformedBody() {
            super(null, null, false, false);
        }
    }

    private static final class InvalidParameter extends RuntimeException {
        private final String name;

        InvalidParameter(String name) {
            super(name, null, false, false);
            this.name = name;
        }

        String name() {
            return name;
        }
    }
}
