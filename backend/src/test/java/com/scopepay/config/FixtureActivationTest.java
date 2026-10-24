package com.scopepay.config;

import static org.junit.jupiter.api.Assertions.*;

import com.scopepay.scope.FixtureScopeProvider;
import com.scopepay.scope.GroqScopeProvider;
import com.scopepay.scope.ScopeProvider;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** Provider wiring: the fixture replay never overrides or impersonates the live provider.
    Every case pins DEMO_FIXTURES explicitly because a real DEMO_FIXTURES env var is a
    legitimate Spring property source and must not leak between cases. */
class FixtureActivationTest {
  private final ApplicationContextRunner runner = new ApplicationContextRunner()
      .withConfiguration(AutoConfigurations.of(PropertyPlaceholderAutoConfiguration.class))
      .withUserConfiguration(AppConfig.class, GroqConfig.class);

  @Test
  void fixtureRegistersOnlyWithFlagAndBlankKey() {
    runner.withPropertyValues("DEMO_FIXTURES=true", "GROQ_API_KEY=", "GROQ_FREE_PLAN_CONFIRMED=false")
        .run(ctx -> {
          assertTrue(ctx.containsBean("fixtureScopeProvider"));
          assertInstanceOf(FixtureScopeProvider.class, ctx.getBean(ScopeProvider.class));
        });
  }

  @Test
  void liveKeyAlwaysWinsOverFixtureFlag() {
    runner.withPropertyValues("DEMO_FIXTURES=true", "GROQ_API_KEY=dummy-test-key",
            "GROQ_FREE_PLAN_CONFIRMED=true")
        .run(ctx -> {
          assertFalse(ctx.containsBean("fixtureScopeProvider"));
          assertInstanceOf(GroqScopeProvider.class, ctx.getBean(ScopeProvider.class));
        });
  }

  @Test
  void noFlagsMeansNoProviderHonestUnconfigured() {
    runner.withPropertyValues("GROQ_API_KEY=", "GROQ_FREE_PLAN_CONFIRMED=false", "DEMO_FIXTURES=false")
        .run(ctx -> assertEquals(0, ctx.getBeansOfType(ScopeProvider.class).size()));
  }
}
