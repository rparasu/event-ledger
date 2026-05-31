package com.ledger.account.domain;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Service
public class AccountService {

    private static final Logger log = LoggerFactory.getLogger(AccountService.class);

    private final AppliedTransactionRepository repository;
    private final Counter appliedCounter;
    private final Counter duplicateCounter;

    public AccountService(AppliedTransactionRepository repository, MeterRegistry meterRegistry) {
        this.repository = repository;
        this.appliedCounter = Counter.builder("account.transactions.applied")
                .description("Transactions newly applied to an account")
                .register(meterRegistry);
        this.duplicateCounter = Counter.builder("account.transactions.duplicate")
                .description("Duplicate transaction applies that were ignored")
                .register(meterRegistry);
    }

    public record ApplyResult(AppliedTransaction transaction, boolean alreadyApplied) {}

    @Transactional
    public ApplyResult apply(String eventId, String accountId, String type, BigDecimal amount,
                             String currency, Instant eventTimestamp) {
        return repository.findById(eventId)
                .map(existing -> {
                    duplicateCounter.increment();
                    log.info("Duplicate transaction ignored eventId={} accountId={}", eventId, accountId);
                    return new ApplyResult(existing, true);
                })
                .orElseGet(() -> {
                    AppliedTransaction saved = repository.save(new AppliedTransaction(
                            eventId, accountId, type, amount, currency, eventTimestamp, Instant.now()));
                    appliedCounter.increment();
                    log.info("Applied transaction eventId={} accountId={} type={} amount={}",
                            eventId, accountId, type, amount);
                    return new ApplyResult(saved, false);
                });
    }

    @Transactional(readOnly = true)
    public BigDecimal balanceOf(String accountId) {
        List<AppliedTransaction> txns = repository.findByAccountIdOrderByEventTimestampDesc(accountId);
        BigDecimal balance = BigDecimal.ZERO;
        for (AppliedTransaction t : txns) {
            if ("CREDIT".equals(t.getType())) {
                balance = balance.add(t.getAmount());
            } else {
                balance = balance.subtract(t.getAmount());
            }
        }
        return balance;
    }

    @Transactional(readOnly = true)
    public List<AppliedTransaction> transactionsFor(String accountId) {
        return repository.findByAccountIdOrderByEventTimestampDesc(accountId);
    }

    @Transactional(readOnly = true)
    public boolean accountExists(String accountId) {
        return repository.existsByAccountId(accountId);
    }
}