package com.ledger.account.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * API contract DTOs between the Gateway and the Account Service.
 * Kept in one file for readability; these define the internal REST contract.
 */
public final class AccountDtos {

    private AccountDtos() {
    }

    /** Body of POST /accounts/{accountId}/transactions. */
    public record ApplyTransactionRequest(
            @NotBlank String eventId,
            @NotBlank String type,
            @NotNull @Positive BigDecimal amount,
            @NotBlank String currency,
            @NotNull Instant eventTimestamp) {
    }

    /** Response from applying a transaction. */
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

    public record TransactionView(
            String eventId,
            String type,
            BigDecimal amount,
            String currency,
            Instant eventTimestamp) {
    }

    public record AccountResponse(
            String accountId,
            BigDecimal balance,
            int transactionCount,
            List<TransactionView> recentTransactions) {
    }
}
