package com.ledger.gateway.client;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Client-side view of the Account Service contract. Kept deliberately separate
 * from the Gateway's own API DTOs so the two contracts can evolve independently.
 */
public final class AccountServiceDtos {

    private AccountServiceDtos() {
    }

    public record ApplyTransactionRequest(
            String eventId,
            String type,
            BigDecimal amount,
            String currency,
            Instant eventTimestamp) {
    }

    public record ApplyTransactionResponse(
            String eventId,
            String accountId,
            String type,
            BigDecimal amount,
            String currency,
            Instant eventTimestamp,
            boolean alreadyApplied) {
    }

    public record BalanceResponse(String accountId, BigDecimal balance) {
    }
}
