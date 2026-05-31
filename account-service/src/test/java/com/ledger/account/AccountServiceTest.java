package com.ledger.account;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AccountServiceTest {

    @Autowired
    MockMvc mockMvc;

    private String txn(String eventId, String type, String amount, String ts) {
        return """
                {"eventId":"%s","type":"%s","amount":%s,"currency":"USD","eventTimestamp":"%s"}
                """.formatted(eventId, type, amount, ts);
    }

    @Test
    void appliesCreditAndDebitThenComputesNetBalance() throws Exception {
        String acct = "acct-balance";
        performPost(acct, txn("e1", "CREDIT", "150.00", "2026-05-15T14:00:00Z"), 201);
        performPost(acct, txn("e2", "DEBIT", "40.00", "2026-05-15T15:00:00Z"), 201);
        performPost(acct, txn("e3", "CREDIT", "10.00", "2026-05-15T16:00:00Z"), 201);

        // net = 150 - 40 + 10 = 120
        mockMvc.perform(get("/accounts/{a}/balance", acct))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance", is(closeTo(120.00, 0.001))));
    }

    @Test
    void duplicateApplyIsIdempotentAndDoesNotDoubleCount() throws Exception {
        String acct = "acct-dupe";
        performPost(acct, txn("dup-1", "CREDIT", "100.00", "2026-05-15T14:00:00Z"), 201);
        // Same eventId again -> 200, not 201, and balance unchanged
        mockMvc.perform(post("/accounts/{a}/transactions", acct)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(txn("dup-1", "CREDIT", "100.00", "2026-05-15T14:00:00Z")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alreadyApplied", is(true)));

        mockMvc.perform(get("/accounts/{a}/balance", acct))
                .andExpect(jsonPath("$.balance", is(closeTo(100.00, 0.001))));
    }

    @Test
    void balanceIsCorrectRegardlessOfArrivalOrder() throws Exception {
        // Apply out of chronological order; balance must still be 60.
        String acct = "acct-ooo";
        performPost(acct, txn("o3", "DEBIT", "40.00", "2026-05-15T16:00:00Z"), 201);  // latest first
        performPost(acct, txn("o1", "CREDIT", "100.00", "2026-05-15T14:00:00Z"), 201); // earliest last
        performPost(acct, txn("o2", "CREDIT", "0.50", "2026-05-15T15:00:00Z"), 201);

        mockMvc.perform(get("/accounts/{a}/balance", acct))
                .andExpect(jsonPath("$.balance", is(closeTo(60.50, 0.001))));
    }

    @Test
    void rejectsNegativeAmount() throws Exception {
        mockMvc.perform(post("/accounts/{a}/transactions", "acct-x")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(txn("neg-1", "CREDIT", "-5.00", "2026-05-15T14:00:00Z")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsUnknownType() throws Exception {
        mockMvc.perform(post("/accounts/{a}/transactions", "acct-x")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(txn("bad-type", "TRANSFER", "5.00", "2026-05-15T14:00:00Z")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void healthReportsUpWithDatabase() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("UP")))
                .andExpect(jsonPath("$.database", is("UP")))
                .andExpect(jsonPath("$.service", is("account-service")));
    }

    private void performPost(String acct, String body, int expectedStatus) throws Exception {
        mockMvc.perform(post("/accounts/{a}/transactions", acct)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().is(expectedStatus));
    }
}