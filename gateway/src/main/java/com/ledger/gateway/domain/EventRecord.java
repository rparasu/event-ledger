package com.ledger.gateway.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * An event as received and stored by the Gateway.
 *
 * <p>{@code eventId} is the primary key, which enforces idempotency at the
 * storage layer: a duplicate submission collides and we return the stored
 * event instead of creating a new one.
 *
 * <p>The Gateway persists the event <i>before</i> calling the Account Service.
 * This is what makes the GET endpoints survive an Account Service outage and
 * lets us record a downstream {@link ProcessingStatus} per event.
 */
@Entity
@Table(name = "events", indexes = {
        @Index(name = "idx_event_account_ts", columnList = "accountId,eventTimestamp")
})
public class EventRecord {

    @Id
    private String eventId;

    @Column(nullable = false)
    private String accountId;

    @Column(nullable = false)
    private String type;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(nullable = false)
    private String currency;

    @Column(nullable = false)
    private Instant eventTimestamp;

    @Column(length = 4000)
    private String metadataJson;

    @Column(nullable = false)
    private Instant receivedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProcessingStatus status;

    protected EventRecord() {
    }

    public EventRecord(String eventId, String accountId, String type, BigDecimal amount, String currency,
                       Instant eventTimestamp, String metadataJson, Instant receivedAt, ProcessingStatus status) {
        this.eventId = eventId;
        this.accountId = accountId;
        this.type = type;
        this.amount = amount;
        this.currency = currency;
        this.eventTimestamp = eventTimestamp;
        this.metadataJson = metadataJson;
        this.receivedAt = receivedAt;
        this.status = status;
    }

    public String getEventId() {
        return eventId;
    }

    public String getAccountId() {
        return accountId;
    }

    public String getType() {
        return type;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public Instant getEventTimestamp() {
        return eventTimestamp;
    }

    public String getMetadataJson() {
        return metadataJson;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public ProcessingStatus getStatus() {
        return status;
    }

    public void setStatus(ProcessingStatus status) {
        this.status = status;
    }
}
