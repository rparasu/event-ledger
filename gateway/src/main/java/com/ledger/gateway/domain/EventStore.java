package com.ledger.gateway.domain;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Transactional persistence operations for events.
 *
 * <p>Kept as a separate bean from {@link EventService} on purpose: each method
 * here runs in its own transaction that commits independently. The orchestration
 * in {@link EventService} is deliberately <i>not</i> transactional so that a
 * failure of the downstream Account Service call cannot roll back the locally
 * persisted event. (If persistence and the remote call shared one transaction,
 * a downstream failure would roll back the stored event and break graceful
 * degradation.)
 */
@Component
public class EventStore {

    private static final Logger log = LoggerFactory.getLogger(EventStore.class);

    private final EventRepository repository;
    private final Counter duplicateCounter;

    public EventStore(EventRepository repository, MeterRegistry meterRegistry) {
        this.repository = repository;
        this.duplicateCounter = Counter.builder("gateway.events.duplicate")
                .description("Duplicate event submissions detected by idempotency check")
                .register(meterRegistry);
    }

    public record PersistResult(EventRecord event, boolean duplicate) {
    }

    /**
     * Idempotently persist a new event as PENDING. If an event with the same id
     * already exists, return it and flag it as a duplicate without modifying it.
     * Commits before any downstream call is made.
     */
    @Transactional
    public PersistResult persistIfNew(String eventId, String accountId, String type, BigDecimal amount,
                                      String currency, Instant eventTimestamp, String metadataJson) {
        Optional<EventRecord> existing = repository.findById(eventId);
        if (existing.isPresent()) {
            duplicateCounter.increment();
            log.info("Duplicate event ignored eventId={} accountId={}", eventId, accountId);
            return new PersistResult(existing.get(), true);
        }
        EventRecord saved = repository.save(new EventRecord(
                eventId, accountId, type, amount, currency, eventTimestamp,
                metadataJson, Instant.now(), ProcessingStatus.PENDING));
        return new PersistResult(saved, false);
    }

    @Transactional
    public void markStatus(String eventId, ProcessingStatus status) {
        repository.findById(eventId).ifPresent(r -> {
            r.setStatus(status);
            repository.save(r);
        });
    }

    @Transactional(readOnly = true)
    public Optional<EventRecord> findById(String eventId) {
        return repository.findById(eventId);
    }

    @Transactional(readOnly = true)
    public List<EventRecord> findByAccount(String accountId) {
        return repository.findByAccountIdOrderByEventTimestampAsc(accountId);
    }
}
