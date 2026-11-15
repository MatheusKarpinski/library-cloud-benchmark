package br.edu.tcc.librarycloud.functions;

import br.edu.tcc.librarycloud.application.BookService;
import br.edu.tcc.librarycloud.infrastructure.config.DataSourceFactory;
import br.edu.tcc.librarycloud.infrastructure.config.DatabaseConfig;
import br.edu.tcc.librarycloud.infrastructure.persistence.DatabaseHealthChecker;
import br.edu.tcc.librarycloud.infrastructure.persistence.JdbcBookRepository;
import com.zaxxer.hikari.HikariDataSource;

/**
 * Montagem manual dos objetos (mesmas classes do adaptador de container), criada UMA vez por
 * processo na primeira requisição (inicialização preguiçosa).
 *
 * <p>Se a criação falhar (por exemplo, variável {@code DB_*} ausente) a falha NÃO é guardada: a
 * próxima requisição tenta de novo, o que permite corrigir a configuração sem reiniciar o host.
 * O pool de conexões não abre conexão ao ser criado (ver {@link DataSourceFactory}), então o
 * tempo de criação não inclui falar com o banco.
 *
 * <p>O pool não é fechado explicitamente: no Functions o processo é encerrado pela plataforma
 * (ver ADR-020).
 */
final class FunctionComponents {

    private static final Object LOCK = new Object();
    private static final JsonCodec CODEC = new JacksonJsonCodec();
    private static volatile BookApiHandler handler;

    private FunctionComponents() {
    }

    static JsonCodec codec() {
        return CODEC;
    }

    static BookApiHandler handler() {
        BookApiHandler current = handler;
        if (current != null) {
            return current;
        }
        synchronized (LOCK) {
            if (handler == null) {
                handler = create();
            }
            return handler;
        }
    }

    private static BookApiHandler create() {
        DatabaseConfig config = DatabaseConfig.fromEnvironment();
        HikariDataSource dataSource = DataSourceFactory.create(config);
        BookService bookService = new BookService(new JdbcBookRepository(dataSource));
        DatabaseHealthChecker healthChecker = new DatabaseHealthChecker(dataSource);
        return new BookApiHandler(bookService, CODEC, healthChecker::isAvailable);
    }
}
