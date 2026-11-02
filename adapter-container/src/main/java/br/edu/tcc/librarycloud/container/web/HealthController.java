package br.edu.tcc.librarycloud.container.web;

import br.edu.tcc.librarycloud.contract.HealthResponse;
import br.edu.tcc.librarycloud.contract.ReadinessResponse;
import br.edu.tcc.librarycloud.infrastructure.persistence.DatabaseHealthChecker;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * GET /health        -> liveness: o processo responde (NÃO consulta o banco).
 * GET /health/ready  -> readiness: consulta o banco; 200 se disponível, 503 se não.
 */
@RestController
public class HealthController {

    private final DatabaseHealthChecker databaseHealthChecker;

    public HealthController(DatabaseHealthChecker databaseHealthChecker) {
        this.databaseHealthChecker = databaseHealthChecker;
    }

    @GetMapping("/health")
    public HealthResponse health() {
        return HealthResponse.up();
    }

    @GetMapping("/health/ready")
    public ResponseEntity<ReadinessResponse> ready() {
        ReadinessResponse readiness = ReadinessResponse.of(databaseHealthChecker.isAvailable());
        return ResponseEntity.status(readiness.up() ? 200 : 503).body(readiness);
    }
}
