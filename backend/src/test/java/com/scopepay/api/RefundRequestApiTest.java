package com.scopepay.api;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.scopepay.store.LedgerEntryRepository;
import com.scopepay.store.ReconcilerService;
import com.scopepay.store.RefundRequestRepository;
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
class RefundRequestApiTest {
  private static final String ORIGIN = "http://localhost:5173";
  private static final String SESSION = "a-session-0123456789";
  private static final String KEY = "idempotency-0123456789";
  private static final String DELIVERY =
      "I delivered the contact form but the gallery page is not started yet, and the client asked about it twice.";

  @Autowired MockMvc mvc;
  @Autowired TestStubs.StubPayPal paypal;
  @Autowired SandboxOrderRepository orders;
  @Autowired ScopeDraftRepository drafts;
  @Autowired RefundRequestRepository refundRequests;
  @Autowired LedgerEntryRepository ledger;
  @Autowired com.scopepay.store.LedgerService ledgerService;
  @Autowired com.scopepay.store.OutboxService outboxService;
  @Autowired org.springframework.transaction.PlatformTransactionManager txManager;

  @BeforeEach
  void reset() { refundRequests.deleteAll(); orders.deleteAll(); drafts.deleteAll(); ledger.deleteAll(); paypal.reset(); }

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

  private String createScopeAndCapture() throws Exception {
    String scopeId = mvc.perform(post("/api/scopes/extract",
        "{\"brief\":\"Build a three-page portfolio website: Home, Projects and Contact. Include a contact form. Do not include visitor login.\"}"))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()
        .replaceAll(".*\\\"scopeId\\\":\\\"([^\\\"]+)\\\".*", "$1");
    mvc.perform(post("/api/scopes/" + scopeId + "/review", "{\"acknowledge\":true}")).andExpect(status().isOk());
    mvc.perform(post("/api/orders", "{\"sku\":\"sandbox-demo\",\"scopeId\":\"" + scopeId + "\"}"))
        .andExpect(status().isCreated());
    mvc.perform(post("/api/orders/ORDER12345/capture", "{}")).andExpect(status().isOk());
    return scopeId;
  }

  private String createRequest() throws Exception {
    String body = "{\"amount\":\"3.50\",\"reason\":\"client reports missing work\","
        + "\"deliveryText\":\"" + DELIVERY + "\"}";
    String json = mvc.perform(post("/api/orders/ORDER12345/refund-requests", body))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.state").value("DRAFT"))
        .andExpect(jsonPath("$.comparison.comparisons[0].deliverable").value("Contact form"))
        .andReturn().getResponse().getContentAsString();
    return json.replaceAll(".*\\\"refundRequestId\\\":\\\"([^\\\"]+)\\\".*", "$1");
  }

  @Test
  void createAndApprovePartialRefundIsIdempotentAndBalanced() throws Exception {
    createScopeAndCapture();
    String id = createRequest();
    mvc.perform(post("/api/refund-requests/" + id + "/approve", "{\"acknowledge\":true}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.state").value("COMPLETED"))
        .andExpect(jsonPath("$.paypalRefundId").value("REFUND-PARTIAL-1"));
    assertEquals("3.50", paypal.lastPartialValue);
    assertEquals("USD", paypal.lastPartialCurrency);
    assertEquals(id, paypal.lastPartialKey);
    // Replay returns the stored receipt; PayPal is not called again.
    mvc.perform(post("/api/refund-requests/" + id + "/approve", "{\"acknowledge\":true}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.paypalRefundId").value("REFUND-PARTIAL-1"));
    assertEquals(1, paypal.refunds.get());
    var entries = ledger.findAll().stream()
        .filter(e -> KEY.equals(e.getRequestId()) && e.getAmount().toPlainString().equals("3.50")).toList();
    assertEquals(2, entries.size(), "partial reversal posts exactly one balanced group");
  }

  @Test
  void cumulativeCapBlocksOverRefund() throws Exception {
    createScopeAndCapture();
    String body = "{\"amount\":\"10.00\",\"reason\":\"full make-good\",\"deliveryText\":\"" + DELIVERY + "\"}";
    String json = mvc.perform(post("/api/orders/ORDER12345/refund-requests", body))
        .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    String id = json.replaceAll(".*\\\"refundRequestId\\\":\\\"([^\\\"]+)\\\".*", "$1");
    mvc.perform(post("/api/refund-requests/" + id + "/approve", "{\"acknowledge\":true}"))
        .andExpect(status().isOk());
    mvc.perform(post("/api/orders/ORDER12345/refund-requests",
        "{\"amount\":\"0.01\",\"reason\":\"more\",\"deliveryText\":\"" + DELIVERY + "\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error").value("REFUND_EXCEEDS_REMAINING"));
  }

  @Test
  void createValidationFailures() throws Exception {
    createScopeAndCapture();
    String base = "{\"reason\":\"r\",\"deliveryText\":\"" + DELIVERY + "\"}";
    for (String bad : new String[] {"abc", "-1", "0", "10.005", "10000000"}) {
      mvc.perform(post("/api/orders/ORDER12345/refund-requests",
          "{\"amount\":\"" + bad + "\",\"reason\":\"r\",\"deliveryText\":\"" + DELIVERY + "\"}"))
          .andExpect(status().isBadRequest());
    }
    mvc.perform(post("/api/orders/NOPE12345/refund-requests",
        "{\"amount\":\"1.00\",\"reason\":\"r\",\"deliveryText\":\"" + DELIVERY + "\"}"))
        .andExpect(status().isNotFound());
    mvc.perform(post("/api/orders/ORDER12345/refund-requests",
        "{\"amount\":\"1.00\",\"reason\":\"r\",\"deliveryText\":\"" + DELIVERY + "\"}", "other-session-5678", KEY))
        .andExpect(status().isNotFound());
  }

  @Test
  void notCompletedOrderAndMissingScopeRejected() throws Exception {
    // Order exists but is only CREATED, not captured.
    String scopeId = mvc.perform(post("/api/scopes/extract",
        "{\"brief\":\"Build a three-page portfolio website: Home, Projects and Contact. Include a contact form. Do not include visitor login.\"}"))
        .andReturn().getResponse().getContentAsString()
        .replaceAll(".*\\\"scopeId\\\":\\\"([^\\\"]+)\\\".*", "$1");
    mvc.perform(post("/api/scopes/" + scopeId + "/review", "{\"acknowledge\":true}"));
    mvc.perform(post("/api/orders", "{\"sku\":\"sandbox-demo\",\"scopeId\":\"" + scopeId + "\"}"));
    mvc.perform(post("/api/orders/ORDER12345/refund-requests",
        "{\"amount\":\"1.00\",\"reason\":\"r\",\"deliveryText\":\"" + DELIVERY + "\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error").value("ORDER_NOT_COMPLETED"));
    // Captured order with no scope bound (distinct PayPal order id: order_id is unique).
    paypal.createBehavior = () -> TestStubs.MAPPER.createObjectNode().put("id", "ORDER67890").put("status", "CREATED");
    mvc.perform(post("/api/orders", "{\"sku\":\"sandbox-demo\"}", SESSION, "idempotency-9876543210"))
        .andExpect(status().isCreated());
    mvc.perform(post("/api/orders/ORDER67890/capture", "{}"));
    mvc.perform(post("/api/orders/ORDER67890/refund-requests",
        "{\"amount\":\"1.00\",\"reason\":\"r\",\"deliveryText\":\"" + DELIVERY + "\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error").value("SCOPE_REQUIRED"));
  }

  @Test
  void pendingRefundBecomesUnclearAndReconcilerRecoversIt() throws Exception {
    createScopeAndCapture();
    paypal.partialRefundBehavior = () -> TestStubs.MAPPER.createObjectNode()
        .put("id", "REFUND-PEND-1").put("status", "PENDING");
    String id = createRequest();
    mvc.perform(post("/api/refund-requests/" + id + "/approve", "{\"acknowledge\":true}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error").value("REFUND_NOT_COMPLETED"));
    assertEquals("UNCLEAR", refundRequests.findById(id).orElseThrow().getState());
    // An UNCLEAR row cannot be approved again; only PayPal's own record can resolve it.
    mvc.perform(post("/api/refund-requests/" + id + "/approve", "{\"acknowledge\":true}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error").value("REFUND_REQUEST_NOT_ACTIONABLE"));
    paypal.getRefundBehavior = () -> TestStubs.MAPPER.createObjectNode()
        .put("id", "REFUND-PEND-1").put("status", "COMPLETED");
    new ReconcilerService(paypal, orders, ledgerService, outboxService,
        new io.micrometer.core.instrument.simple.SimpleMeterRegistry(), txManager, refundRequests).reconcile();
    assertEquals("COMPLETED", refundRequests.findById(id).orElseThrow().getState());
  }

  @Test
  void ungroundedComparisonFailsClosed() throws Exception {
    createScopeAndCapture();
    TestStubs.comparisonBehavior = account -> {
      var root = TestStubs.MAPPER.createObjectNode();
      root.putArray("comparisons").addObject()
          .put("deliverable", "Contact form").put("verdict", "CLAIMED_ADDRESSED")
          .put("quote", "words never said by the submitter").put("note", "stub");
      root.put("overallNote", "stub");
      return root;
    };
    mvc.perform(post("/api/orders/ORDER12345/refund-requests",
        "{\"amount\":\"1.00\",\"reason\":\"r\",\"deliveryText\":\"" + DELIVERY + "\"}"))
        .andExpect(status().is(422))
        .andExpect(jsonPath("$.error").value("UNGROUNDED_COMPARISON"));
    assertEquals(0, refundRequests.count(), "failed comparison stores nothing");
  }
}
