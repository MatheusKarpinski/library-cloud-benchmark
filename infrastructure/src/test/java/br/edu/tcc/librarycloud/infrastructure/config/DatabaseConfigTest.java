package br.edu.tcc.librarycloud.infrastructure.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DatabaseConfigTest {

    private static Map<String, String> requiredOnly() {
        Map<String, String> env = new HashMap<>();
        env.put("DB_URL", "jdbc:postgresql://localhost:5432/library");
        env.put("DB_USER", "library");
        env.put("DB_PASSWORD", "segredo-de-teste");
        return env;
    }

    @Test
    @DisplayName("usa os valores padrão quando só as variáveis obrigatórias existem")
    void usesDefaults() {
        DatabaseConfig config = DatabaseConfig.from(requiredOnly()::get);

        assertThat(config.jdbcUrl()).isEqualTo("jdbc:postgresql://localhost:5432/library");
        assertThat(config.username()).isEqualTo("library");
        assertThat(config.maxPoolSize()).isEqualTo(DatabaseConfig.DEFAULT_MAX_POOL_SIZE);
        assertThat(config.minIdle()).isEqualTo(DatabaseConfig.DEFAULT_MIN_IDLE);
        assertThat(config.connectionTimeoutMs()).isEqualTo(DatabaseConfig.DEFAULT_CONNECTION_TIMEOUT_MS);
    }

    @Test
    @DisplayName("lê as variáveis opcionais quando informadas")
    void readsOptionalOverrides() {
        Map<String, String> env = requiredOnly();
        env.put("DB_POOL_MAX", "12");
        env.put("DB_POOL_MIN_IDLE", "2");
        env.put("DB_CONNECTION_TIMEOUT_MS", "3000");

        DatabaseConfig config = DatabaseConfig.from(env::get);

        assertThat(config.maxPoolSize()).isEqualTo(12);
        assertThat(config.minIdle()).isEqualTo(2);
        assertThat(config.connectionTimeoutMs()).isEqualTo(3000L);
    }

    @Test
    @DisplayName("lista TODAS as variáveis obrigatórias ausentes (sem mostrar valores)")
    void reportsAllMissingRequiredVariables() {
        Map<String, String> env = new HashMap<>();
        env.put("DB_PASSWORD", "   ");

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> DatabaseConfig.from(env::get));

        assertThat(ex.getMessage().contains("DB_URL")).isTrue();
        assertThat(ex.getMessage().contains("DB_USER")).isTrue();
        assertThat(ex.getMessage().contains("DB_PASSWORD")).isTrue();
    }

    @Test
    @DisplayName("rejeita números inválidos ou fora do limite")
    void rejectsInvalidNumbers() {
        Map<String, String> notANumber = requiredOnly();
        notANumber.put("DB_POOL_MAX", "abc");
        Map<String, String> zeroPool = requiredOnly();
        zeroPool.put("DB_POOL_MAX", "0");
        Map<String, String> tinyTimeout = requiredOnly();
        tinyTimeout.put("DB_CONNECTION_TIMEOUT_MS", "10");

        assertThrows(IllegalStateException.class, () -> DatabaseConfig.from(notANumber::get));
        assertThrows(IllegalStateException.class, () -> DatabaseConfig.from(zeroPool::get));
        assertThrows(IllegalStateException.class, () -> DatabaseConfig.from(tinyTimeout::get));
    }

    @Test
    @DisplayName("rejeita DB_POOL_MIN_IDLE maior que DB_POOL_MAX")
    void rejectsMinIdleGreaterThanMax() {
        Map<String, String> env = requiredOnly();
        env.put("DB_POOL_MAX", "2");
        env.put("DB_POOL_MIN_IDLE", "3");

        assertThrows(IllegalStateException.class, () -> DatabaseConfig.from(env::get));
    }

    @Test
    @DisplayName("toString nunca revela a senha")
    void toStringHidesPassword() {
        DatabaseConfig config = DatabaseConfig.from(requiredOnly()::get);

        assertThat(config.toString().contains("segredo-de-teste")).isFalse();
    }
}
