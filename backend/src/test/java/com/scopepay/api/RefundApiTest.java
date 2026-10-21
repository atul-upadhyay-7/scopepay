package com.scopepay.api;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.scopepay.store.LedgerEntryRepository;
import com.scopepay.store.SandboxOrderRepository;
import com.scopepay.store.ScopeDraftRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {"PAYPAL_CLIENT_ID=test-public-id", "REQUIRE_REVIEWED_SCOPE=false"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestStubs.class)
class RefundApiTest {
  private static final String ORIGIN = "http://localhost:5173";
  private static final String SESSION = "a-session-0123456789";
  private static final String KEY = "idempotency-0123456789";

  @Autowired MockMvc mvc;
  @Autowired TestStubs.StubPayPal paypal;
  @Autowired SandboxOrderRepository orders;
  @Autowired ScopeDraftRepository drafts;
  @Autowired LedgerEntryRepository ledger;

  @BeforeEach
  void reset() { orders.deleteAll(); drafts.deleteAll(); paypal.reset(); }

  private org.springframework.test.web.servlet.RequestBuilder post(String path, String body) {
    return post(path, body, SESSION, KEY);
  }

  private org.springframework.test.web.servlet.RequestBuilder post(String path, String body, String session, String key) {
    var b = org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path)
        .header("Origin", ORIGIN)
        .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content(body);
    if (session != null && !session.isEmpty()) b = b.header("x-demo-session", session);
    if (key != null && !key.isEmpty()) b = b.header("idempotency-key", key);
    return b;
  }

  private void createAndCapture() throws Exception {
    mvc.perform(post("/api/orders", "{\"sku\":\"sandbox-demo\"}")).andExpect(status().isCreated());
    mvc.perform(post("/api/orders/ORDER12345/capture", "{}")).andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("COMPLETED"));
  }

  @Test
  void refundCompletesAndRetryUsesStoredReceipt() throws Exception {
    createAndCapture();
    mvc.perform(post("/api/orders/ORDER12345/refund", "{}")).andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("REFUNDED"))
        .andExpect(jsonPath("$.refundId").value("REFUND123"));
    mvc.perform(post("/api/orders/ORDER12345/refund", "{}")).andExpect(status().isOk())
        .andExpect(jsonPath("$.refundId").value("REFUND123"));
    assertEquals(1, paypal.refunds.get());
    var record = orders.findByOrderId("ORDER12345").orElseThrow();
    assertEquals("REFUNDED", record.getState());
    assertEquals("REFUND123", record.getRefundId());
    var entries = ledger.findByRequestIdOrderByCreatedAtAsc(record.getRequestId());
    assertTrue(entries.stream().anyMatch(e -> e.getMemo() != null && e.getMemo().contains("REFUND123")),
        "ledger carries the refund entries");
  }

  @Test
  void refundRequiresCompletedOrder() throws Exception {
    mvc.perform(post("/api/orders", "{\"sku\":\"sandbox-demo\"}")).andExpect(status().isCreated());
    mvc.perform(post("/api/orders/ORDER12345/refund", "{}")).andExpect(status().isConflict())
        .andExpect(jsonPath("$.error").value("ORDER_NOT_COMPLETED"));
    assertEquals(0, paypal.refunds.get());
  }

  @Test
  void refundOfUnknownOrderIs404() throws Exception {
    mvc.perform(post("/api/orders/ORDER12345/refund", "{}")).andExpect(status().isNotFound());
  }

  @Test
  void refundBlockedForOtherSession() throws Exception {
    createAndCapture();
    mvc.perform(post("/api/orders/ORDER12345/refund", "{}", "different-session-123", KEY))
        .andExpect(status().isNotFound());
    assertEquals(0, paypal.refunds.get());
  }

  @Test
  void refundFailsClosedWhenPayPalDoesNotComplete() throws Exception {
    createAndCapture();
    paypal.refundBehavior = () -> TestStubs.MAPPER.createObjectNode().put("id", "REFUND123").put("status", "PENDING");
    mvc.perform(post("/api/orders/ORDER12345/refund", "{}")).andExpect(status().isBadGateway())
        .andExpect(jsonPath("$.error").value("REFUND_NOT_COMPLETED"));
    var record = orders.findByOrderId("ORDER12345").orElseThrow();
    assertEquals("COMPLETED", record.getState());
    assertNull(record.getRefundId());
  }
}
