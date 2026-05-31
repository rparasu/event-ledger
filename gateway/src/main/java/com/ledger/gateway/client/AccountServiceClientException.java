package com.ledger.gateway.client;

import org.springframework.http.HttpStatusCode;

/**
 * Raised when the Account Service returns a 4xx response — i.e. the request
 * itself was rejected (bad input), as opposed to the service being unavailable.
 *
 * <p>This is deliberately a distinct type so the circuit breaker can be
 * configured to <b>ignore</b> it: a caller's bad request is not evidence that
 * the downstream is unhealthy and must not count toward tripping the breaker.
 * The API layer surfaces the original status to the client.
 */
public class AccountServiceClientException extends RuntimeException {

    private final HttpStatusCode statusCode;
    private final String responseBody;

    public AccountServiceClientException(HttpStatusCode statusCode, String responseBody) {
        super("Account Service rejected request with status " + statusCode);
        this.statusCode = statusCode;
        this.responseBody = responseBody;
    }

    public HttpStatusCode getStatusCode() {
        return statusCode;
    }

    public String getResponseBody() {
        return responseBody;
    }
}
