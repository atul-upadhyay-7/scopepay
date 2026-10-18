package com.scopepay.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
  @Bean
  public OpenAPI scopePayOpenAPI() {
    return new OpenAPI().info(new Info()
        .title("ScopePay Sandbox API")
        .version("0.1.0")
        .description("Sandbox-only AI scope review and fixed-price PayPal test payment API. "
            + "Not a production payment service; the AI never sets the price."));
  }
}
