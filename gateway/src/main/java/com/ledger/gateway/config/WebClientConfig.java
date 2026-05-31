package com.ledger.gateway.config;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    /**
     * WebClient for the Account Service.
     *
     * <p>The builder is the Spring-managed {@link WebClient.Builder}, which
     * Micrometer Tracing post-processes for metrics/observations. Trace header
     * injection (B3 format) is handled by an explicit {@link ExchangeFilterFunction}
     * because the auto-configured propagation relies on Reactor context propagation
     * that is not guaranteed when using {@code .block()} in a servlet context.
     *
     * <p>The filter reads {@link Tracer#currentSpan()} synchronously on the calling
     * thread — before the reactive chain switches to a Netty I/O thread — so the
     * ThreadLocal span is always available.
     */
    @Bean
    public WebClient accountServiceWebClient(WebClient.Builder builder,
                                             @Value("${account-service.base-url}") String baseUrl,
                                             Tracer tracer) {
        return builder
                .baseUrl(baseUrl)
                .filter(b3TraceFilter(tracer))
                .build();
    }

    private static ExchangeFilterFunction b3TraceFilter(Tracer tracer) {
        return (request, next) -> {
            Span span = tracer.currentSpan();
            if (span == null || span.isNoop()) {
                return next.exchange(request);
            }
            TraceContext ctx = span.context();
            ClientRequest traced = ClientRequest.from(request)
                    .header("X-B3-TraceId", ctx.traceId())
                    .header("X-B3-SpanId", ctx.spanId())
                    .header("X-B3-Sampled", "1")
                    .build();
            return next.exchange(traced);
        };
    }
}
