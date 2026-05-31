package com.ledger.gateway.domain;

import com.ledger.gateway.client.AccountServiceClient;
import com.ledger.gateway.client.AccountServiceDtos;
import com.ledger.gateway.client.AccountServiceUnavailableException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Orchestrates event handling: idempotency, local persistence, the downstream
 * apply call, and status tracking.
 *
 * <p>This class is intentionally <b>not</b> {@code @Transactional}. Persistence
 * happens inside {@link EventStore} (whose methods each commit on their own),
 * and the remote call happens between those commits. This guarantees a stored
 * event survives an Account Service outage — the basis for graceful degradation.
 */
@Service
public class EventService {

    private static final Logger log = LoggerFactory.getLogger(EventService.class);

    private final EventStore store;
    private final AccountServiceClient accountServiceClient;
    private final MeterRegistry meterRegistry;

    public EventService(EventStore store,
                        AccountServiceClient accountServiceClient,
                        MeterRegistry meterRegistry) {
        this.store = store;
        this.accountServiceClient = accountServiceClient;
        this.meterRegistry = meterRegistry;
    }

    public record SubmitResult(EventRecord event, boolean duplicate) {
    }

    /**
     * Flow: idempotent persist (commits) → call Account Service → mark status.
     *
     * @throws AccountServiceUnavailableException if the event is new and the
     *         Account Service cannot be reached (caller maps this to 503). The
     *         event remains stored as PENDING.
     */
    public SubmitResult submit(String eventId, String accountId, String type, BigDecimal amount,
                               String currency, Instant eventTimestamp, String metadataJson) {

        EventStore.PersistResult persisted = store.persistIfNew(
                eventId, accountId, type, amount, currency, eventTimestamp, metadataJson);

        if (persisted.duplicate()) {
            return new SubmitResult(persisted.event(), true);
        }

        try {
            AccountServiceDtos.ApplyTransactionResponse resp = accountServiceClient.applyTransaction(
                    accountId,
                    new AccountServiceDtos.ApplyTransactionRequest(
                            eventId, type, amount, currency, eventTimestamp));
            store.markStatus(eventId, ProcessingStatus.APPLIED);
            countOutcome(type, "applied");
            log.info("Event applied eventId={} accountId={} alreadyApplied={}",
                    eventId, accountId, resp.alreadyApplied());
            EventRecord updated = store.findById(eventId).orElse(persisted.event());
            return new SubmitResult(updated, false);
        } catch (AccountServiceUnavailableException e) {
            // Event stays PENDING and remains queryable; surface 503 to client.
            countOutcome(type, "deferred");
            log.warn("Event stored but Account Service unavailable eventId={} accountId={}", eventId, accountId);
            throw e;
        }
    }

    private void countOutcome(String type, String outcome) {
        Counter.builder("gateway.events.processed")
                .description("Events processed by the Gateway")
                .tag("type", type)
                .tag("outcome", outcome)
                .register(meterRegistry)
                .increment();
    }

    public Optional<EventRecord> findById(String eventId) {
        return store.findById(eventId);
    }

    public List<EventRecord> findByAccount(String accountId) {
        return store.findByAccount(accountId);
    }

    /**
     * Balance lookup delegates to the Account Service. If it is unreachable the
     * client throws {@link AccountServiceUnavailableException}, which the API
     * layer maps to a clear error.
     */
    public BigDecimal balanceOf(String accountId) {
        return accountServiceClient.getBalance(accountId).balance();
    }
}
