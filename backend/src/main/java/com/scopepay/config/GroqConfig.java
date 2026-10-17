package com.scopepay.config;

import com.scopepay.scope.GroqScopeProvider;
import com.scopepay.scope.ScopeProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GroqConfig {
  /** Registered only with a key AND the verified Free-plan flag; otherwise the API reports AI_NOT_CONFIGURED. */
  @Bean
  @ConditionalOnExpression("T(java.lang.Boolean).parseBoolean('${GROQ_FREE_PLAN_CONFIRMED:false}') && !'${GROQ_API_KEY:}'.isBlank()")
  public ScopeProvider groqScopeProvider(AppConfig config) {
    return new GroqScopeProvider(config.groqApiKey, config.syntheticOnly);
  }
}
