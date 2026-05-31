package com.ledger.gateway.api;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

public final class EventDtos {

    private EventDtos() {
    }

    /**
     * Body of POST /events. Bean Validation covers required fields and the
     * positive-amount rule; the CREDIT/DEBIT check is done in the service since
     * it needs a custom message.
     */
    public record SubmitEventRequest(
            @NotBlank(message = "eventId is required") String eventId,
            @NotBlank(message = "accountId is required") String accountId,
            @NotBlank(message = "type is required") String type,
            @NotNull(message = "amount is required")
            @Positive(message = "amount must be greater than 0") BigDecimal amount,
            @NotBlank(message = "currency is required") String currency,
            @NotNull(message = "eventTimestamp is required") Instant eventTimestamp,
            JsonNode metadata) {
    }

    public record EventResponse(
            String eventId,
            String accountId,
            String type,
            BigDecimal amount,
            String currency,
            Instant eventTimestamp,
            Map<String, Object> metadata,
            String status,
            Instant receivedAt,
            boolean duplicate) {
    }

    public record BalanceResponse(String accountId, BigDecimal balance) {
    }
}
