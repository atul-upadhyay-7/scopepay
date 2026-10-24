package com.scopepay.config;

import com.scopepay.scope.FixtureScopeProvider;
import com.scopepay.scope.GroqScopeProvider;
import com.scopepay.scope.ScopeProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
public class GroqConfig {
  /** Registered only with a key AND the verified Free-plan flag; otherwise the API reports AI_NOT_CONFIGURED. */
  @Bean
  @Profile("!test") // the live provider never enters the test context, even when real keys are in the environment
  @ConditionalOnExpression("T(java.lang.Boolean).parseBoolean('${GROQ_FREE_PLAN_CONFIRMED:false}') && !'${GROQ_API_KEY:}'.isBlank()")
  public ScopeProvider groqScopeProvider(AppConfig config) {
    return new GroqScopeProvider(config.groqApiKey, config.syntheticOnly);
  }

  /**
   * Deterministic replay of one bundled synthetic demo example, for judge machines with no Groq
   * key. Registered only when explicitly enabled AND no live key exists, so it never overrides or
   * impersonates a live model run; the config endpoint and UI label its output as fixture data.
   */
  @Bean
  @Profile("!test") // same rule as the live provider: never in the test context
  @ConditionalOnExpression("T(java.lang.Boolean).parseBoolean('${DEMO_FIXTURES:false}') && '${GROQ_API_KEY:}'.isBlank()")
  public ScopeProvider fixtureScopeProvider() {
    return new FixtureScopeProvider();
  }
}
