package com.ledger.gateway;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full end-to-end integration test: starts the real Account Service JAR as a
 * subprocess so requests flow through the actual Gateway → Account Service path.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext
@TestPropertySource(properties = {
        "account-service.base-url=http://localhost:8081",
        "account-service.request-timeout-ms=5000",
        "resilience4j.circuitbreaker.instances.accountService.minimum-number-of-calls=10"
})
class GatewayIntegrationTest {

    private static Process accountServiceProcess;

    @Autowired
    MockMvc mockMvc;

    @BeforeAll
    static void startAccountService() throws Exception {
        Path jar = Paths.get("../account-service/target/account-service.jar")
                .toAbsolutePath();

        accountServiceProcess = new ProcessBuilder("java", "-jar", jar.toString())
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start();

        // Poll until Account Service is healthy (up to 30s)
        HttpClient http = HttpClient.newHttpClient();
        long deadline = System.currentTimeMillis() + 30_000;
        boolean ready = false;
        while (System.currentTimeMillis() < deadline) {
            try {
                HttpResponse<String> resp = http.send(
                        HttpRequest.newBuilder(URI.create("http://localhost:8081/health")).GET().build(),
                        HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() == 200) {
                    ready = true;
                    break;
                }
            } catch (Exception ignored) {
            }
            Thread.sleep(1000);
        }
        if (!ready) throw new IllegalStateException("Account Service did not start in time");
    }

    @AfterAll
    static void stopAccountService() {
        if (accountServiceProcess != null) {
            accountServiceProcess.destroyForcibly();
        }
    }

    @Test
    void submitEvent_appliedToRealAccountService() throws Exception {
        mockMvc.perform(post("/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"eventId":"int-001","accountId":"int-acct","type":"CREDIT",
                                 "amount":150.00,"currency":"USD","eventTimestamp":"2026-05-15T14:00:00Z"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status", is("APPLIED")))
                .andExpect(jsonPath("$.duplicate", is(false)));
    }

    @Test
    void duplicateEvent_notReapplied() throws Exception {
        String payload = """
                {"eventId":"int-002","accountId":"int-acct","type":"CREDIT",
                 "amount":50.00,"currency":"USD","eventTimestamp":"2026-05-15T15:00:00Z"}
                """;

        mockMvc.perform(post("/events").contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.duplicate", is(false)));

        mockMvc.perform(post("/events").contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.duplicate", is(true)));
    }

    @Test
    void balanceReflectsRealAccountService() throws Exception {
        // CREDIT 200
        mockMvc.perform(post("/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"eventId":"int-003","accountId":"int-bal","type":"CREDIT",
                                 "amount":200.00,"currency":"USD","eventTimestamp":"2026-05-15T10:00:00Z"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status", is("APPLIED")));

        // DEBIT 60 — out-of-order (earlier timestamp)
        mockMvc.perform(post("/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"eventId":"int-004","accountId":"int-bal","type":"DEBIT",
                                 "amount":60.00,"currency":"USD","eventTimestamp":"2026-05-14T09:00:00Z"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status", is("APPLIED")));

        // Balance via Gateway → Account Service: 200 - 60 = 140
        mockMvc.perform(get("/events/balance").param("account", "int-bal"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance", is(140.0)));
    }

    @Test
    void getEvents_returnedInChronologicalOrder() throws Exception {
        // Submit in reverse order
        mockMvc.perform(post("/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"eventId":"int-005","accountId":"int-order","type":"CREDIT",
                                 "amount":100.00,"currency":"USD","eventTimestamp":"2026-05-16T10:00:00Z"}
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"eventId":"int-006","accountId":"int-order","type":"DEBIT",
                                 "amount":30.00,"currency":"USD","eventTimestamp":"2026-05-15T08:00:00Z"}
                                """))
                .andExpect(status().isCreated());

        // List — int-006 (May 15) must come before int-005 (May 16)
        mockMvc.perform(get("/events").param("account", "int-order"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].eventId", is("int-006")))
                .andExpect(jsonPath("$[1].eventId", is("int-005")));
    }
}
