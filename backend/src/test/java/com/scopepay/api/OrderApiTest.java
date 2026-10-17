package com.scopepay.api;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.scopepay.store.SandboxOrderRepository;
import com.scopepay.store.ScopeDraftRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = {"PAYPAL_CLIENT_ID=test-public-id", "REQUIRE_REVIEWED_SCOPE=false"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestStubs.class)
class OrderApiTest {
  private static final String ORIGIN = "http://localhost:5173";
  private static final String SESSION = "a-session-0123456789";
  private static final String KEY = "idempotency-0123456789";

  @Autowired MockMvc mvc;
  @Autowired TestStubs.StubPayPal paypal;
  @Autowired SandboxOrderRepository orders;
  @Autowired ScopeDraftRepository drafts;

  @BeforeEach
  void reset() { orders.deleteAll(); drafts.deleteAll(); paypal.reset(); }

  private org.springframework.test.web.servlet.RequestBuilder post(String path, String body) {
    return post(path, body, SESSION, KEY);
  }

  private org.springframework.test.web.servlet.RequestBuilder post(String path, String body, String session, String key) {
    var b = org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path)
        .header("Origin", ORIGIN).contentType(MediaType.APPLICATION_JSON).content(body);
    if (session != null && !session.isEmpty()) b = b.header("x-demo-session", session);
    if (key != null && !key.isEmpty()) b = b.header("idempotency-key", key);
    return b;
  }

  @Test
  void healthAndConfigExposeSandboxNeverSecret() throws Exception {
    mvc.perform(get("/api/health")).andExpect(status().isOk())
        .andExpect(jsonPath("$.environment").value("sandbox"));
    mvc.perform(get("/api/config")).andExpect(status().isOk())
        .andExpect(jsonPath("$.price").value("10.00"))
        .andExpect(jsonPath("$.clientId").value("test-public-id"));
  }

  @Test
  void serverOwnedPriceInjectedAmountRejected() throws Exception {
    mvc.perform(post("/api/orders", "{\"sku\":\"sandbox-demo\",\"amount\":\"0.01\"}")).andExpect(status().isBadRequest());
    assertEquals(0, paypal.creates.get());
  }

  @Test
  void unknownSkuRejected() throws Exception {
    mvc.perform(post("/api/orders", "{\"sku\":\"other\"}")).andExpect(status().isBadRequest());
  }

  @Test
  void originRequired() throws Exception {
    mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/orders")
        .contentType(MediaType.APPLICATION_JSON).content("{\"sku\":\"sandbox-demo\"}")
        .header("x-demo-session", SESSION).header("idempotency-key", KEY))
        .andExpect(status().isForbidden()).andExpect(jsonPath("$.error").value("ORIGIN_NOT_ALLOWED"));
  }

  @Test
  void sessionRequired() throws Exception {
    mvc.perform(post("/api/orders", "{\"sku\":\"sandbox-demo\"}", "", KEY)).andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("SESSION_REQUIRED"));
  }

  @Test
  void idempotencyKeyRequired() throws Exception {
    mvc.perform(post("/api/orders", "{\"sku\":\"sandbox-demo\"}", SESSION, "")).andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("IDEMPOTENCY_KEY_REQUIRED"));
  }

  @Test
  void createRetryReusesOriginalOrder() throws Exception {
    mvc.perform(post("/api/orders", "{\"sku\":\"sandbox-demo\"}")).andExpect(status().isCreated());
    mvc.perform(post("/api/orders", "{\"sku\":\"sandbox-demo\"}")).andExpect(status().isOk())
        .andExpect(jsonPath("$.orderId").value("ORDER12345"));
    assertEquals(1, paypal.creates.get());
  }

  @Test
  void otherBrowserCannotCapture() throws Exception {
    mvc.perform(post("/api/orders", "{\"sku\":\"sandbox-demo\"}")).andExpect(status().isCreated());
    mvc.perform(post("/api/orders/ORDER12345/capture", "{}", "different-session-123", KEY))
        .andExpect(status().isNotFound());
  }

  @Test
  void captureCompletesAndRetryUsesStoredReceipt() throws Exception {
    mvc.perform(post("/api/orders", "{\"sku\":\"sandbox-demo\"}")).andExpect(status().isCreated());
    mvc.perform(post("/api/orders/ORDER12345/capture", "{}")).andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("COMPLETED"));
    mvc.perform(post("/api/orders/ORDER12345/capture", "{}")).andExpect(status().isOk())
        .andExpect(jsonPath("$.captureId").value("CAPTURE123"));
    assertEquals(1, paypal.captures.get());
  }

  @Test
  void pendingCaptureNeverShownAsPaid() throws Exception {
    paypal.captureBehavior = () -> TestStubs.MAPPER.createObjectNode().put("status", "APPROVED");
    mvc.perform(post("/api/orders", "{\"sku\":\"sandbox-demo\"}")).andExpect(status().isCreated());
    mvc.perform(post("/api/orders/ORDER12345/capture", "{}")).andExpect(status().isConflict())
        .andExpect(jsonPath("$.status").value("PENDING_REVIEW"));
  }

  @Test
  void wrongCapturedAmountIsNotAccepted() throws Exception {
    paypal.captureBehavior = () -> {
      var root = TestStubs.MAPPER.createObjectNode();
      root.put("status", "COMPLETED");
      var capture = root.putArray("purchase_units").addObject().putObject("payments").putArray("captures").addObject();
      capture.put("id", "CAPTURE123").put("status", "COMPLETED");
      capture.putObject("amount").put("value", "0.01").put("currency_code", "USD");
      return root;
    };
    mvc.perform(post("/api/orders", "{\"sku\":\"sandbox-demo\"}")).andExpect(status().isCreated());
    mvc.perform(post("/api/orders/ORDER12345/capture", "{}")).andExpect(status().isBadGateway())
        .andExpect(jsonPath("$.error").value("PAYMENT_UNCONFIRMED"));
    assertNotEquals("COMPLETED", orders.findById(KEY).orElseThrow().getState());
  }

  @Test
  void failedCreateStaysReservedPreventingSilentDuplicate() throws Exception {
    paypal.createBehavior = () -> { throw new RuntimeException("secret-token-not-for-user"); };
    mvc.perform(post("/api/orders", "{\"sku\":\"sandbox-demo\"}")).andExpect(status().isBadGateway());
    MvcResult r = mvc.perform(post("/api/orders", "{\"sku\":\"sandbox-demo\"}")).andExpect(status().isConflict())
        .andReturn();
    assertEquals(1, paypal.creates.get());
    assertFalse(r.getResponse().getContentAsString().contains("secret-token"));
  }

  @Test
  void unknownCaptureRejected() throws Exception {
    mvc.perform(post("/api/orders/ORDER55555/capture", "{}")).andExpect(status().isNotFound());
  }
}
