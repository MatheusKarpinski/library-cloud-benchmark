package br.edu.tcc.librarycloud.infrastructure.config;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Configuração de conexão com o banco, lida SOMENTE de variáveis de ambiente
 * (no Azure Functions, as "Application Settings" também chegam como variáveis de ambiente,
 * então o mesmo código serve às duas implantações).
 *
 * <pre>
 * DB_URL                   obrigatória  ex.: jdbc:postgresql://localhost:5432/library
 * DB_USER                  obrigatória
 * DB_PASSWORD              obrigatória  (nunca versionar)
 * DB_POOL_MAX              opcional, padrão 5     máximo de conexões POR INSTÂNCIA
 * DB_POOL_MIN_IDLE         opcional, padrão 1     conexões mantidas ociosas
 * DB_CONNECTION_TIMEOUT_MS opcional, padrão 5000  espera máxima por uma conexão (mínimo 250)
 * </pre>
 */
public record DatabaseConfig(
        String jdbcUrl,
        String username,
        String password,
        int maxPoolSize,
        int minIdle,
        long connectionTimeoutMs) {

    public static final int DEFAULT_MAX_POOL_SIZE = 5;
    public static final int DEFAULT_MIN_IDLE = 1;
    public static final long DEFAULT_CONNECTION_TIMEOUT_MS = 5_000L;
    private static final long MIN_CONNECTION_TIMEOUT_MS = 250L;

    public static DatabaseConfig fromEnvironment() {
        return from(System::getenv);
    }

    /** Versão testável: recebe a função que lê variáveis (nos testes, um Map). */
    public static DatabaseConfig from(Function<String, String> env) {
        List<String> missing = new ArrayList<>();
        String url = required(env, "DB_URL", missing);
        String user = required(env, "DB_USER", missing);
        String password = required(env, "DB_PASSWORD", missing);
        if (!missing.isEmpty()) {
            throw new IllegalStateException(
                    "Variáveis de ambiente obrigatórias ausentes: " + String.join(", ", missing));
        }

        int maxPool = integer(env, "DB_POOL_MAX", DEFAULT_MAX_POOL_SIZE, 1);
        int minIdle = integer(env, "DB_POOL_MIN_IDLE", DEFAULT_MIN_IDLE, 0);
        long timeout = integer(env, "DB_CONNECTION_TIMEOUT_MS",
                (int) DEFAULT_CONNECTION_TIMEOUT_MS, (int) MIN_CONNECTION_TIMEOUT_MS);
        if (minIdle > maxPool) {
            throw new IllegalStateException(
                    "DB_POOL_MIN_IDLE (" + minIdle + ") não pode ser maior que DB_POOL_MAX (" + maxPool + ")");
        }
        return new DatabaseConfig(url, user, password, maxPool, minIdle, timeout);
    }

    private static String required(Function<String, String> env, String name, List<String> missing) {
        String value = env.apply(name);
        if (value == null || value.isBlank()) {
            missing.add(name);
            return null;
        }
        return value.strip();
    }

    private static int integer(Function<String, String> env, String name, int defaultValue, int minimum) {
        String raw = env.apply(name);
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        int value;
        try {
            value = Integer.parseInt(raw.strip());
        } catch (NumberFormatException e) {
            throw new IllegalStateException(name + " deve ser um número inteiro, mas recebeu: " + raw, e);
        }
        if (value < minimum) {
            throw new IllegalStateException(name + " deve ser maior ou igual a " + minimum + ", mas recebeu: " + value);
        }
        return value;
    }

    /** Nunca imprime a senha (nem a URL, que pode conter parâmetros sensíveis). */
    @Override
    public String toString() {
        return "DatabaseConfig[user=" + username + ", maxPoolSize=" + maxPoolSize
                + ", minIdle=" + minIdle + ", connectionTimeoutMs=" + connectionTimeoutMs + "]";
    }
}
