package com.ledger.gateway.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EventRepository extends JpaRepository<EventRecord, String> {

    /**
     * Events for an account in chronological order by event timestamp.
     * This is where out-of-order arrival is corrected: regardless of the order
     * events were received, the listing is always sorted by when they occurred.
     */
    List<EventRecord> findByAccountIdOrderByEventTimestampAsc(String accountId);
}
