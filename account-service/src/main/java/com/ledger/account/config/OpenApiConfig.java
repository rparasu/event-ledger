package com.ledger.account.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI accountServiceOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Account Service API")
                .version("1.0.0")
                .description("Internal service for managing account balances and applying transactions."));
    }
}