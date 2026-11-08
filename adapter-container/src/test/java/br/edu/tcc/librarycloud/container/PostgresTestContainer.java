package br.edu.tcc.librarycloud.container;

import org.testcontainers.postgresql.PostgreSQLContainer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * PostgreSQL real em container para os testes da API (mesma imagem do docker-compose.yml e o
 * mesmo db/schema.sql). Sobe uma vez por execução; o Testcontainers o remove ao final.
 */
final class PostgresTestContainer {

    private static final PostgreSQLContainer CONTAINER = new PostgreSQLContainer("postgres:17");

    static {
        CONTAINER.start();
    }

    private PostgresTestContainer() {
    }

    /** Argumentos de linha de comando que apontam a aplicação para este banco. */
    static String[] applicationArgs() {
        return new String[] {
                "--DB_URL=" + CONTAINER.getJdbcUrl(),
                "--DB_USER=" + CONTAINER.getUsername(),
                "--DB_PASSWORD=" + CONTAINER.getPassword(),
                "--DB_POOL_MAX=3",
                "--DB_POOL_MIN_IDLE=1",
                "--DB_CONNECTION_TIMEOUT_MS=2000"
        };
    }

    /** Executa db/schema.sql (o Maven roda os testes com a pasta do módulo como diretório atual). */
    static void applySchema() throws IOException, SQLException {
        execute(Files.readString(Path.of("..", "db", "schema.sql"), StandardCharsets.UTF_8));
    }

    static void truncateBooks() throws SQLException {
        execute("TRUNCATE TABLE books");
    }

    private static void execute(String sql) throws SQLException {
        try (Connection connection = DriverManager.getConnection(
                CONTAINER.getJdbcUrl(), CONTAINER.getUsername(), CONTAINER.getPassword());
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }
}
