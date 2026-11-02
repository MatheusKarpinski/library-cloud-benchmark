package br.edu.tcc.librarycloud.infrastructure.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

/** Cria o pool de conexões (HikariCP) a partir de {@link DatabaseConfig}. */
public final class DataSourceFactory {

    private DataSourceFactory() {
    }

    public static HikariDataSource create(DatabaseConfig config) {
        HikariConfig hikari = new HikariConfig();
        hikari.setPoolName("library-pool");
        hikari.setJdbcUrl(config.jdbcUrl());
        hikari.setUsername(config.username());
        hikari.setPassword(config.password());
        hikari.setMaximumPoolSize(config.maxPoolSize());
        hikari.setMinimumIdle(config.minIdle());
        hikari.setConnectionTimeout(config.connectionTimeoutMs());
        // -1: não tenta conectar na criação do pool. A aplicação sobe mesmo com o banco fora do
        // ar (a falha aparece como 503 / readiness DOWN) e a inicialização não bloqueia o cold start.
        hikari.setInitializationFailTimeout(-1);
        return new HikariDataSource(hikari);
    }
}
