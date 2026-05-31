package com.ledger.account.api;

import com.ledger.account.domain.AccountService;
import com.ledger.account.domain.AppliedTransaction;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping("/{accountId}/transactions")
    public ResponseEntity<AccountDtos.ApplyTransactionResponse> applyTransaction(
            @PathVariable String accountId,
            @Valid @RequestBody AccountDtos.ApplyTransactionRequest request) {

        String type = request.type().toUpperCase();
        if (!type.equals("CREDIT") && !type.equals("DEBIT")) {
            throw new IllegalArgumentException("type must be CREDIT or DEBIT, was: " + request.type());
        }

        AccountService.ApplyResult result = accountService.apply(
                request.eventId(), accountId, type, request.amount(),
                request.currency(), request.eventTimestamp());

        AppliedTransaction t = result.transaction();
        AccountDtos.ApplyTransactionResponse body = new AccountDtos.ApplyTransactionResponse(
                t.getEventId(), t.getAccountId(), t.getType(), t.getAmount(),
                t.getCurrency(), t.getEventTimestamp(), result.alreadyApplied());

        // 200 if it was already applied (idempotent replay), 201 if newly created.
        HttpStatus status = result.alreadyApplied() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(body);
    }

    @GetMapping("/{accountId}/balance")
    public AccountDtos.BalanceResponse balance(@PathVariable String accountId) {
        return new AccountDtos.BalanceResponse(accountId, accountService.balanceOf(accountId));
    }

    @GetMapping("/{accountId}")
    public AccountDtos.AccountResponse account(@PathVariable String accountId) {
        List<AppliedTransaction> txns = accountService.transactionsFor(accountId);
        List<AccountDtos.TransactionView> recent = txns.stream()
                .limit(20)
                .map(t -> new AccountDtos.TransactionView(
                        t.getEventId(), t.getType(), t.getAmount(), t.getCurrency(), t.getEventTimestamp()))
                .toList();
        return new AccountDtos.AccountResponse(
                accountId, accountService.balanceOf(accountId), txns.size(), recent);
    }
}
