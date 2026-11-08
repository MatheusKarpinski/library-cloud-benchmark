package br.edu.tcc.librarycloud.infrastructure.persistence;

import br.edu.tcc.librarycloud.infrastructure.config.DatabaseConfig;
import org.testcontainers.postgresql.PostgreSQLContainer;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Um PostgreSQL de verdade, em container, compartilhado por todos os testes de integração do
 * módulo (sobe uma vez por execução e é removido ao final pelo próprio Testcontainers).
 * Usa a mesma imagem do docker-compose.yml e o mesmo esquema de db/schema.sql.
 *
 * <p>Se o Docker não estiver ativo, a classe falha ao carregar com a mensagem do Testcontainers.
 */
final class PostgresTestContainer {

    private static final PostgreSQLContainer CONTAINER = new PostgreSQLContainer("postgres:17");

    static {
        CONTAINER.start();
    }

    private PostgresTestContainer() {
    }

    static DatabaseConfig config(int maxPoolSize, long connectionTimeoutMs) {
        return new DatabaseConfig(CONTAINER.getJdbcUrl(), CONTAINER.getUsername(), CONTAINER.getPassword(),
                maxPoolSize, 1, connectionTimeoutMs);
    }

    /** Executa db/schema.sql (o Maven roda os testes com a pasta do módulo como diretório atual). */
    static void applySchema(DataSource dataSource) throws IOException, SQLException {
        String sql = Files.readString(Path.of("..", "db", "schema.sql"), StandardCharsets.UTF_8);
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }
}
