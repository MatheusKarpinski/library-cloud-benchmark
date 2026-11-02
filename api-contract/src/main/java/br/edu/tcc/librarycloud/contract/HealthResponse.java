package br.edu.tcc.librarycloud.contract;

/** Resposta de GET /health (liveness: o processo está de pé; não consulta o banco). */
public record HealthResponse(String status) {

    public static HealthResponse up() {
        return new HealthResponse("UP");
    }
}
