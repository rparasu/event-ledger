package com.ledger.gateway.domain;

/**
 * Tracks the downstream outcome of an event after it has been stored locally.
 *
 * <ul>
 *   <li>{@code APPLIED} — the Account Service confirmed the transaction.</li>
 *   <li>{@code PENDING} — stored locally, but the Account Service was
 *       unreachable (circuit open / timeout). The event is preserved and could
 *       be reconciled later; this is what enables graceful degradation.</li>
 * </ul>
 */
public enum ProcessingStatus {
    APPLIED,
    PENDING
}
