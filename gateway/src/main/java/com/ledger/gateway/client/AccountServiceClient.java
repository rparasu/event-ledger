package com.ledger.gateway.client;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Calls the Account Service over synchronous REST, guarded by a Resilience4j
 * circuit breaker.
 *
 * <p><b>Why a circuit breaker:</b> the Account Service is a hard dependency for
 * writes. If it starts failing, retrying every request would pile load onto an
 * already-struggling service and tie up Gateway threads waiting on calls that
 * are likely to fail. The breaker detects a sustained failure rate, "opens",
 * and then fails fast — every {@code POST /events} immediately returns 503
 * instead of hanging. After a wait window it lets a few trial calls through
 * (half-open) and closes again once the service recovers.
 *
 * <p>A per-call timeout (applied to the blocking call) converts a slow or hung
 * downstream into a counted failure, which is what the breaker trips on.
 * Breaker + timeout together are the resiliency pattern.
 *
 * <p><b>4xx vs 5xx:</b> a downstream 4xx means the request was bad, not that the
 * service is unhealthy, so it is raised as {@link AccountServiceClientException}
 * which the breaker is configured to ignore. 5xx, timeouts, and connection
 * failures are recorded as failures and can trip the breaker.
 */
@Component
public class AccountServiceClient {

    private static final Logger log = LoggerFactory.getLogger(AccountServiceClient.class);
    public static final String CB_NAME = "accountService";

    private final WebClient webClient;
    private final Duration requestTimeout;

    public AccountServiceClient(WebClient accountServiceWebClient,
                                @Value("${account-service.request-timeout-ms:2000}") long timeoutMs) {
        this.webClient = accountServiceWebClient;
        this.requestTimeout = Duration.ofMillis(timeoutMs);
    }

    @CircuitBreaker(name = CB_NAME, fallbackMethod = "applyFallback")
    public AccountServiceDtos.ApplyTransactionResponse applyTransaction(
            String accountId, AccountServiceDtos.ApplyTransactionRequest request) {

        return webClient.post()
                .uri("/accounts/{accountId}/transactions", accountId)
                .bodyValue(request)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, this::toClientError)
                .bodyToMono(AccountServiceDtos.ApplyTransactionResponse.class)
                .timeout(requestTimeout)
                .block();
    }

    @CircuitBreaker(name = CB_NAME, fallbackMethod = "balanceFallback")
    public AccountServiceDtos.BalanceResponse getBalance(String accountId) {
        return webClient.get()
                .uri("/accounts/{accountId}/balance", accountId)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, this::toClientError)
                .bodyToMono(AccountServiceDtos.BalanceResponse.class)
                .timeout(requestTimeout)
                .block();
    }

    private Mono<Throwable> toClientError(org.springframework.web.reactive.function.client.ClientResponse response) {
        return response.bodyToMono(String.class)
                .defaultIfEmpty("")
                .map(body -> new AccountServiceClientException(response.statusCode(), body));
    }

    // --- Fallbacks ---------------------------------------------------------
    // Resilience4j invokes these when the call fails or the breaker is open.
    // The trailing Throwable parameter receives the triggering exception.

    @SuppressWarnings("unused")
    private AccountServiceDtos.ApplyTransactionResponse applyFallback(
            String accountId, AccountServiceDtos.ApplyTransactionRequest request, Throwable t) {
        throw translate("apply transaction", t);
    }

    @SuppressWarnings("unused")
    private AccountServiceDtos.BalanceResponse balanceFallback(String accountId, Throwable t) {
        throw translate("get balance", t);
    }

    /**
     * Maps a downstream failure to the exception the API layer should see.
     * Always throws; the declared return type lets callers write
     * {@code throw translate(...)} so the compiler sees the method exits.
     */
    private RuntimeException translate(String op, Throwable t) {
        if (t instanceof AccountServiceClientException ce) {
            // Bad request rejected by the downstream — surface as-is.
            return ce;
        }
        if (t instanceof CallNotPermittedException) {
            log.warn("Circuit open; short-circuiting {} call to Account Service", op);
            return new AccountServiceUnavailableException(
                    "Account Service is temporarily unavailable (circuit open)", t);
        }
        log.warn("Account Service call failed during {}: {}", op, t.toString());
        return new AccountServiceUnavailableException("Account Service is unreachable", t);
    }
}
