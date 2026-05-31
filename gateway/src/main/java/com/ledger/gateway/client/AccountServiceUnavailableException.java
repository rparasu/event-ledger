package com.ledger.gateway.client;

/**
 * Thrown when the Account Service cannot be reached or the circuit breaker is
 * open. The API layer maps this to 503 Service Unavailable so the client gets
 * a meaningful error instead of a hang or a 500.
 */
public class AccountServiceUnavailableException extends RuntimeException {
    public AccountServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }

    public AccountServiceUnavailableException(String message) {
        super(message);
    }
}
