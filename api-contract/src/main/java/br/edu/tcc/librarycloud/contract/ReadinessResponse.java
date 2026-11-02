package br.edu.tcc.librarycloud.contract;

/** Resposta de GET /health/ready (readiness: consulta o banco com uma verificação simples). */
public record ReadinessResponse(String status, String database) {

    public static ReadinessResponse of(boolean databaseAvailable) {
        String state = databaseAvailable ? "UP" : "DOWN";
        return new ReadinessResponse(state, state);
    }

    public boolean up() {
        return "UP".equals(status);
    }
}
