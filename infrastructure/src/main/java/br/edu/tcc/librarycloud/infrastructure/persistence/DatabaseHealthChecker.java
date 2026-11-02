package br.edu.tcc.librarycloud.infrastructure.persistence;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;

/** Verificação simples de disponibilidade do banco, usada pelo endpoint /health/ready. */
public class DatabaseHealthChecker {

    private static final System.Logger LOG = System.getLogger(DatabaseHealthChecker.class.getName());
    private static final int VALIDATION_TIMEOUT_SECONDS = 2;

    private final DataSource dataSource;

    public DatabaseHealthChecker(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    public boolean isAvailable() {
        try (Connection connection = dataSource.getConnection()) {
            return connection.isValid(VALIDATION_TIMEOUT_SECONDS);
        } catch (SQLException e) {
            LOG.log(System.Logger.Level.WARNING, "Banco de dados indisponível: {0}", e.getMessage());
            return false;
        }
    }
}
