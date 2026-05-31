package com.ledger.gateway.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI gatewayOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Event Gateway API")
                .version("1.0.0")
                .description("""
                        Public-facing entry point for transaction events. Validates input,
                        enforces idempotency, stores events, and applies them via the Account
                        Service behind a circuit breaker.
                        """));
    }
}
