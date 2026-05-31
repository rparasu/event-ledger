package com.ledger.account.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AppliedTransactionRepository extends JpaRepository<AppliedTransaction, String> {

    /**
     * Transactions for an account, most-recent event first.
     * Used for the "recent transactions" view on the account details endpoint.
     */
    List<AppliedTransaction> findByAccountIdOrderByEventTimestampDesc(String accountId);

    boolean existsByAccountId(String accountId);
}
