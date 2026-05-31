package com.ledger.gateway;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.ledger.gateway.client.AccountServiceClient;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.github.tomakehurst.wiremock.client.WireMock;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext
@TestPropertySource(properties = {
        "account-service.base-url=http://localhost:9876",
        "account-service.request-timeout-ms=500",
        "resilience4j.circuitbreaker.instances.accountService.sliding-window-size=5",
        "resilience4j.circuitbreaker.instances.accountService.minimum-number-of-calls=3",
        "resilience4j.circuitbreaker.instances.accountService.failure-rate-threshold=50",
        "resilience4j.circuitbreaker.instances.accountService.wait-duration-in-open-state=60s"
})
class GatewayApplicationTests {

    private static final int WIREMOCK_PORT = 9876;

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
            .options(wireMockConfig().port(WIREMOCK_PORT))
            .build();

    @Autowired MockMvc mockMvc;
    @Autowired CircuitBreakerRegistry circuitBreakerRegistry;
    @Autowired Tracer tracer;

    @BeforeEach
    void setUp() {
        wireMock.resetAll();
        circuitBreakerRegistry.circuitBreaker(AccountServiceClient.CB_NAME).reset();
    }

    // ── Happy path ──────────────────────────────────────────────────────────

    @Test
    void submitEventSuccessfully() throws Exception {
        stubApply(false, 201);

        mockMvc.perform(post("/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(event("evt-ok-1", "acct-ok", "CREDIT", "100.00")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.eventId", is("evt-ok-1")))
                .andExpect(jsonPath("$.status", is("APPLIED")))
                .andExpect(jsonPath("$.duplicate", is(false)));
    }

    @Test
    void duplicateEventReturns200WithDuplicateFlag() throws Exception {
        stubApply(false, 201);

        mockMvc.perform(post("/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(event("evt-dup-1", "acct-dup", "CREDIT", "50.00")))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(event("evt-dup-1", "acct-dup", "CREDIT", "50.00")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.duplicate", is(true)));
    }

    @Test
    void getEventByIdReturnsStoredEvent() throws Exception {
        stubApply(false, 201);

        mockMvc.perform(post("/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(event("evt-get-1", "acct-get", "DEBIT", "25.00")))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/events/evt-get-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId", is("evt-get-1")))
                .andExpect(jsonPath("$.accountId", is("acct-get")));
    }

    @Test
    void listEventsByAccountReturnsEvents() throws Exception {
        stubApply(false, 201);

        mockMvc.perform(post("/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(event("evt-list-1", "acct-list", "CREDIT", "100.00")))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/events").param("account", "acct-list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].eventId", is("evt-list-1")));
    }

    // ── Graceful degradation ────────────────────────────────────────────────

    @Test
    void accountServiceUnavailableReturns503OnSubmit() throws Exception {
        wireMock.stubFor(WireMock.post(WireMock.urlPathMatching("/accounts/.*/transactions"))
                .willReturn(WireMock.aResponse().withStatus(500)));

        mockMvc.perform(post("/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(event("evt-fail-1", "acct-fail", "CREDIT", "10.00")))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void getEventByIdWorksWhenAccountServiceIsDown() throws Exception {
        stubApply(false, 201);
        mockMvc.perform(post("/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(event("evt-gd-1", "acct-gd", "CREDIT", "10.00")))
                .andExpect(status().isCreated());

        wireMock.resetAll(); // AS goes down

        mockMvc.perform(get("/events/evt-gd-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId", is("evt-gd-1")));
    }

    @Test
    void listEventsWorksWhenAccountServiceIsDown() throws Exception {
        stubApply(false, 201);
        mockMvc.perform(post("/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(event("evt-gd2-1", "acct-gd2", "CREDIT", "10.00")))
                .andExpect(status().isCreated());

        wireMock.resetAll(); // AS goes down

        mockMvc.perform(get("/events").param("account", "acct-gd2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].eventId", is("evt-gd2-1")));
    }

    // ── Circuit breaker ─────────────────────────────────────────────────────

    @Test
    void circuitBreakerOpensAfterRepeatedFailures() throws Exception {
        wireMock.stubFor(WireMock.post(WireMock.urlPathMatching("/accounts/.*/transactions"))
                .willReturn(WireMock.aResponse().withStatus(500)));

        for (int i = 0; i < 4; i++) {
            mockMvc.perform(post("/events")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(event("evt-cb-" + i, "acct-cb", "CREDIT", "10.00")))
                    .andExpect(status().isServiceUnavailable());
        }

        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker(AccountServiceClient.CB_NAME);
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);
    }

    // ── Validation ──────────────────────────────────────────────────────────

    @Test
    void validationRejectsMissingEventId() throws Exception {
        mockMvc.perform(post("/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"accountId":"a","type":"CREDIT","amount":1.0,"currency":"USD","eventTimestamp":"2026-05-15T14:00:00Z"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void validationRejectsInvalidType() throws Exception {
        mockMvc.perform(post("/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(event("evt-bad", "acct-x", "TRANSFER", "10.00")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void validationRejectsNegativeAmount() throws Exception {
        mockMvc.perform(post("/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(event("evt-neg", "acct-x", "CREDIT", "-5.00")))
                .andExpect(status().isBadRequest());
    }

    // ── Trace propagation ───────────────────────────────────────────────

    @Test
    void traceIdPropagatedToAccountService() throws Exception {
        stubApply(false, 201);

        // Start an explicit span so tracer.currentSpan() is non-null on the calling thread.
        // WebClientConfig's b3TraceFilter reads the span synchronously before the reactive
        // chain switches to a Netty I/O thread, injecting the B3 headers.
        Span span = tracer.nextSpan().name("test.gateway");
        try (Tracer.SpanInScope scope = tracer.withSpan(span.start())) {
            mockMvc.perform(post("/events")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(event("evt-trace-1", "acct-trace", "CREDIT", "100.00")))
                    .andExpect(status().isCreated());
        } finally {
            span.end();
        }

        wireMock.verify(WireMock.postRequestedFor(WireMock.urlPathMatching("/accounts/.*/transactions"))
                .withHeader("X-B3-TraceId", WireMock.matching(".+")));
    }

    // ── Health ──────────────────────────────────────────────────────────────

    @Test
    void healthReportsUp() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("UP")))
                .andExpect(jsonPath("$.database", is("UP")))
                .andExpect(jsonPath("$.service", is("gateway")));
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private String event(String eventId, String accountId, String type, String amount) {
        return """
                {"eventId":"%s","accountId":"%s","type":"%s","amount":%s,"currency":"USD","eventTimestamp":"2026-05-15T14:00:00Z"}
                """.formatted(eventId, accountId, type, amount);
    }

    private void stubApply(boolean alreadyApplied, int httpStatus) {
        wireMock.stubFor(WireMock.post(WireMock.urlPathMatching("/accounts/.*/transactions"))
                .willReturn(WireMock.aResponse()
                        .withStatus(httpStatus)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"eventId":"any","accountId":"any","type":"CREDIT","amount":100.00,"currency":"USD","eventTimestamp":"2026-05-15T14:00:00Z","alreadyApplied":%s}
                                """.formatted(alreadyApplied))));
    }
}