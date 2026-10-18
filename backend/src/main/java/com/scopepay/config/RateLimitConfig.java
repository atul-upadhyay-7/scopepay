package com.scopepay.config;

import com.scopepay.api.RateLimitFilter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
@ConditionalOnProperty(name = "scopepay.ratelimit.enabled", havingValue = "true")
public class RateLimitConfig {
  @Bean
  public RateLimitFilter rateLimitFilter(StringRedisTemplate redis, MeterRegistry metrics,
      @Value("${scopepay.ratelimit.per-minute:60}") int perMinute) {
    return new RateLimitFilter(redis, metrics, perMinute);
  }
}
