package com.scopepay.api;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.scopepay.store.SandboxOrderRepository;
import java.net.ServerSocket;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import redis.embedded.RedisServer;

@SpringBootTest(properties = {"PAYPAL_CLIENT_ID=test-public-id", "REQUIRE_REVIEWED_SCOPE=false",
    "scopepay.ratelimit.enabled=true", "scopepay.ratelimit.per-minute=3"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestStubs.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RateLimitApiTest {
  private static final String ORIGIN = "http://localhost:5173";
  private static final int REDIS_PORT = freePort();
  private static RedisServer redis;

  private static int freePort() {
    try (ServerSocket s = new ServerSocket(0)) { return s.getLocalPort(); }
    catch (Exception e) { throw new IllegalStateException(e); }
  }

  @DynamicPropertySource
  static void redisProps(DynamicPropertyRegistry registry) {
    try {
      redis = new RedisServer(REDIS_PORT);
      redis.start();
    } catch (java.io.IOException e) {
      throw new IllegalStateException("embedded redis failed to start", e);
    }
    registry.add("spring.data.redis.port", () -> REDIS_PORT);
  }

  @AfterAll
  static void stopRedis() throws java.io.IOException { if (redis != null && redis.isActive()) redis.stop(); }

  @Autowired MockMvc mvc;
  @Autowired TestStubs.StubPayPal paypal;
  @Autowired SandboxOrderRepository orders;

  @BeforeEach
  void reset() { orders.deleteAll(); paypal.reset(); }

  private org.springframework.test.web.servlet.RequestBuilder createOrder(String key) {
    return post("/api/orders")
        .header("Origin", ORIGIN).header("x-demo-session", "rl-session-00000001")
        .header("idempotency-key", key)
        .contentType(MediaType.APPLICATION_JSON).content("{\"sku\":\"sandbox-demo\"}");
  }

  @Test
  @Order(1)
  void fourthMutatingCallInTheSameMinuteIsRejected() throws Exception {
    paypal.createBehavior = () -> TestStubs.MAPPER.createObjectNode()
        .put("id", "ORDERRL" + (1000 + paypal.creates.get())).put("status", "CREATED");
    for (int i = 1; i <= 3; i++)
      mvc.perform(createOrder("rl-key-000000000000" + i)).andExpect(status().isCreated());
    mvc.perform(createOrder("rl-key-0000000000004")).andExpect(status().isTooManyRequests())
        .andExpect(jsonPath("$.error").value("RATE_LIMITED"));
  }

  @Test
  @Order(2)
  void readRoutesAndWebhooksAreExempt() throws Exception {
    mvc.perform(get("/api/health")).andExpect(status().isOk());
    mvc.perform(post("/api/webhooks/paypal").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(result -> assertNotEquals(429, result.getResponse().getStatus()));
  }

  @Test
  @Order(3)
  void redisOutageFailsOpen() throws Exception {
    redis.stop();
    try {
      mvc.perform(createOrder("rl-key-0000000000005")).andExpect(status().isCreated());
    } finally {
      redis.start();
    }
  }
}
