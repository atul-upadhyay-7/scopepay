package com.scopepay.config;

import com.scopepay.paypal.PayPalClient;
import com.scopepay.scope.GroqScopeProvider;
import com.scopepay.scope.ScopeProvider;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfig {
  public static final String SKU = "sandbox-demo";
  public static final String PRICE_VALUE = "10.00";
  public static final String CURRENCY = "USD";

  @Value("${APP_ORIGIN:http://localhost:5173}")
  public String appOrigin;
  @Value("${PAYPAL_CLIENT_ID:}")
  public String paypalClientId;
  @Value("${PAYPAL_CLIENT_SECRET:}")
  public String paypalClientSecret;
  @Value("${GROQ_API_KEY:}")
  public String groqApiKey;
  @Value("${GROQ_FREE_PLAN_CONFIRMED:false}")
  public boolean groqFreePlanConfirmed;
  @Value("${AI_SYNTHETIC_ONLY:true}")
  public boolean syntheticOnly;
  @Value("${REQUIRE_REVIEWED_SCOPE:true}")
  public boolean requireReviewedScope;
  @Value("${PAYPAL_WEBHOOK_ID:}")
  public String paypalWebhookId;
  /** Demo-only tools (webhook simulator, failure injection). Never enabled in production properties. */
  @Value("${DEMO_TOOLS:false}")
  public boolean demoTools;

  @Bean
  public PayPalClient payPalClient() {
    PayPalClient client = new PayPalClient(paypalClientId, paypalClientSecret);
    client.enableDefaultResilience();
    return client;
  }

  @Bean
  public com.scopepay.scope.ScopeExtractor scopeExtractor(
      org.springframework.beans.factory.ObjectProvider<ScopeProvider> provider) {
    return new com.scopepay.scope.ScopeExtractor(Optional.ofNullable(provider.getIfAvailable()));
  }

  @Bean
  public com.scopepay.scope.DeliveryComparator deliveryComparator(
      org.springframework.beans.factory.ObjectProvider<ScopeProvider> provider) {
    return new com.scopepay.scope.DeliveryComparator(Optional.ofNullable(provider.getIfAvailable()));
  }
}
