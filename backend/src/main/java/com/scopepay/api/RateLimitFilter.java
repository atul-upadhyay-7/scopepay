package com.scopepay.api;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Sliding-minute per-session rate limit on mutating API routes, backed by Redis. Webhook and demo
 * routes are exempt (PayPal retries and local tooling must never be throttled). If Redis is down
 * the filter fails open and counts it - correctness of money movement never depends on Redis.
 */
public class RateLimitFilter extends OncePerRequestFilter {
  private final StringRedisTemplate redis;
  private final MeterRegistry metrics;
  private final int perMinute;

  public RateLimitFilter(StringRedisTemplate redis, MeterRegistry metrics, int perMinute) {
    this.redis = redis; this.metrics = metrics; this.perMinute = perMinute;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String path = request.getRequestURI();
    String method = request.getMethod();
    if (!path.startsWith("/api/")) return true;
    if ("GET".equals(method) || "HEAD".equals(method) || "OPTIONS".equals(method)) return true;
    if (path.startsWith("/api/webhooks/") || path.startsWith("/api/demo/")) return true;
    return false;
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String owner = SessionGate.ownerHash(request);
    long bucket = System.currentTimeMillis() / 60000L;
    String key = "scopepay:rl:" + owner + ":" + bucket;
    try {
      Long count = redis.opsForValue().increment(key);
      if (count != null && count == 1L) redis.expire(key, Duration.ofSeconds(90));
      if (count != null && count > perMinute) {
        metrics.counter("scopepay.ratelimit.rejected").increment();
        response.setStatus(429);
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"RATE_LIMITED\"}");
        return;
      }
    } catch (DataAccessException e) {
      metrics.counter("scopepay.ratelimit.unavailable").increment(); // fail open
    }
    chain.doFilter(request, response);
  }
}
