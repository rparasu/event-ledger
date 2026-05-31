package com.ledger.gateway.api;

import com.ledger.gateway.client.AccountServiceClient;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Health endpoint required by the spec at GET /health. Reports service status,
 * local database connectivity, and — as a useful diagnostic — the current
 * state of the Account Service circuit breaker.
 *
 * <p>Note: the Gateway is considered healthy even when the circuit is open.
 * A tripped breaker means a <i>dependency</i> is degraded, not that the
 * Gateway itself is down — its read endpoints still serve local data.
 */
@RestController
public class HealthController {

    private final DataSource dataSource;
    private final CircuitBreakerRegistry circuitBreakerRegistry;

    public HealthController(DataSource dataSource, CircuitBreakerRegistry circuitBreakerRegistry) {
        this.dataSource = dataSource;
        this.circuitBreakerRegistry = circuitBreakerRegistry;
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("service", "gateway");
        body.put("timestamp", Instant.now().toString());

        boolean dbUp;
        try (Connection c = dataSource.getConnection()) {
            dbUp = c.isValid(2);
        } catch (Exception e) {
            dbUp = false;
        }
        body.put("database", dbUp ? "UP" : "DOWN");

        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker(AccountServiceClient.CB_NAME);
        Map<String, Object> cbInfo = new LinkedHashMap<>();
        cbInfo.put("state", cb.getState().name());
        cbInfo.put("failureRate", cb.getMetrics().getFailureRate());
        cbInfo.put("bufferedCalls", cb.getMetrics().getNumberOfBufferedCalls());
        body.put("accountServiceCircuit", cbInfo);

        boolean healthy = dbUp;
        body.put("status", healthy ? "UP" : "DOWN");
        return ResponseEntity.status(healthy ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE).body(body);
    }
}
