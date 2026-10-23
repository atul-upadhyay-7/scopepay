package com.scopepay.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.scopepay.scope.ScopeProvider;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Regression for the 2026-10-23 laptop failure: with a real GROQ_API_KEY and
 * GROQ_FREE_PLAN_CONFIRMED=true in the environment, the live Groq provider leaked
 * into the test context and collided with the stub (NoUniqueBeanDefinitionException,
 * 63 test errors). The Groq bean is now @Profile("!test"), so the test context must
 * contain exactly the stub no matter what the environment provides.
 */
@SpringBootTest(properties = {"PAYPAL_CLIENT_ID=test-public-id", "REQUIRE_REVIEWED_SCOPE=false",
    "GROQ_API_KEY=gsk_leaked_env_key_simulation", "GROQ_FREE_PLAN_CONFIRMED=true"})
@ActiveProfiles("test")
@Import(TestStubs.class)
class TestIsolationRegressionTest {
  @Autowired ApplicationContext context;

  @Test
  void onlyTheStubProviderIsRegisteredEvenWithRealKeyEnvironment() {
    assertEquals(List.of("stubScopeProvider"), List.of(context.getBeanNamesForType(ScopeProvider.class)));
  }
}
