package com.ledger.account.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A transaction that has been applied to an account.
 *
 * <p>The {@code eventId} is the primary key. Using it as the PK gives us
 * idempotency for free at the storage layer: a duplicate apply for the same
 * event collides on insert and we treat it as an already-applied no-op,
 * so the balance can never be double-counted.
 */
@Entity
@Table(name = "applied_transactions", indexes = {
        @Index(name = "idx_txn_account", columnList = "accountId")
})
public class AppliedTransaction {

    @Id
    private String eventId;

    @Column(nullable = false)
    private String accountId;

    @Column(nullable = false)
    private String type; // CREDIT or DEBIT

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(nullable = false)
    private String currency;

    @Column(nullable = false)
    private Instant eventTimestamp;

    @Column(nullable = false)
    private Instant appliedAt;

    protected AppliedTransaction() {
        // for JPA
    }

    public AppliedTransaction(String eventId, String accountId, String type, BigDecimal amount,
                              String currency, Instant eventTimestamp, Instant appliedAt) {
        this.eventId = eventId;
        this.accountId = accountId;
        this.type = type;
        this.amount = amount;
        this.currency = currency;
        this.eventTimestamp = eventTimestamp;
        this.appliedAt = appliedAt;
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

    public Instant getAppliedAt() {
        return appliedAt;
    }
}
