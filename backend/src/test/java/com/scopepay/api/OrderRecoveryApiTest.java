package com.scopepay.api;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scopepay.store.ClaimedOrderRepository;
import com.scopepay.store.OutboxEventRepository;
import com.scopepay.store.RefundRequestRepository;
import com.scopepay.store.SandboxOrderRepository;
import com.scopepay.store.ScopeDraftRepository;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Owner-controlled order recovery (ORDER_RECOVERY=true here). The security boundary: knowing
 * a PayPal order ID is never enough - recovery needs the one-time code the server prints to
 * its own console, which only the person running this machine can read. Codes are bound to
 * order and session, single-use, quick to expire, and burn after repeated wrong attempts.
 */
@SpringBootTest(properties = {"PAYPAL_CLIENT_ID=test", "REQUIRE_REVIEWED_SCOPE=true", "ORDER_RECOVERY=true"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestStubs.class)
class OrderRecoveryApiTest {
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final String ORIGIN = "http://localhost:5173";
  private static final String OWNER = "recovery-owner-sess01";
  private static final String CLAIMER = "recovery-claimer-sess";
  private static final String STRANGER = "recovery-stranger-s01";
  private static final String DELIVERY =
      "I delivered the contact form but the gallery page is not started yet, and the client asked about it twice.";

  @Autowired MockMvc mvc;
  @Autowired TestStubs.StubPayPal paypal;
  @Autowired SandboxOrderRepository orders;
  @Autowired ScopeDraftRepository drafts;
  @Autowired RefundRequestRepository refundRequests;
  @Autowired ClaimedOrderRepository claims;
  @Autowired OutboxEventRepository events;
  @Autowired OrderRecoveryService recovery;

  @BeforeEach
  void reset() {
    claims.deleteAll(); refundRequests.deleteAll(); orders.deleteAll(); drafts.deleteAll(); paypal.reset();
  }

  @AfterEach
  void removeClaims() { claims.deleteAll(); }

  private MockHttpServletRequestBuilder post(String path, String body, String session) {
    return org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path)
        .header("Origin", ORIGIN).header("x-demo-session", session)
        .contentType(MediaType.APPLICATION_JSON).content(body);
  }

  private MockHttpServletRequestBuilder receipts(String session) {
    return get("/api/receipts").header("Origin", ORIGIN).header("x-demo-session", session);
  }

  /** A COMPLETED order owned by OWNER; the stub always issues PayPal id ORDER12345. */
  private String completedOrder() throws Exception {
    var d = mvc.perform(post("/api/scopes/extract",
        "{\"brief\":\"Build a website with a contact form.\"}", OWNER)).andExpect(status().isOk()).andReturn();
    String scopeId = MAPPER.readTree(d.getResponse().getContentAsString()).get("scopeId").asText();
    mvc.perform(post("/api/scopes/" + scopeId + "/review", "{\"acknowledge\":true}", OWNER))
        .andExpect(status().isOk());
    mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/orders")
        .header("Origin", ORIGIN).header("x-demo-session", OWNER)
        .header("idempotency-key", "recovery-test-" + UUID.randomUUID())
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"sku\":\"sandbox-demo\",\"scopeId\":\"" + scopeId + "\"}"))
        .andExpect(status().isCreated());
    mvc.perform(post("/api/orders/ORDER12345/capture", "{}", OWNER)).andExpect(status().isOk());
    return "ORDER12345";
  }

  private String requestIdOf(String orderId) {
    return orders.findByOrderId(orderId).orElseThrow().getRequestId();
  }

  @Test
  void knownOrderIdAloneIsDenied() throws Exception {
    completedOrder();
    mvc.perform(post("/api/orders/ORDER12345/recover", "{\"code\":\"AAAA-BBBB\"}", STRANGER))
        .andExpect(status().isForbidden()).andExpect(jsonPath("$.error").value("ORDER_RECOVERY_DENIED"));
    mvc.perform(post("/api/orders/ORDER12345/recover", "{}", STRANGER)).andExpect(status().isBadRequest());
    assertEquals(0, claims.count());
    JsonNode forStranger = MAPPER.readTree(mvc.perform(receipts(STRANGER))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    assertEquals(0, forStranger.get("orders").size());
  }

  @Test
  void issueEndpointNeverReturnsTheCode() throws Exception {
    completedOrder();
    String body = mvc.perform(post("/api/orders/ORDER12345/recovery-code", "{}", CLAIMER))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("RECOVERY_CODE_ON_SERVER_CONSOLE"))
        .andReturn().getResponse().getContentAsString();
    assertFalse(body.matches("(?s).*[0-9A-F]{4}-[0-9A-F]{4}.*"), "the code must never reach the browser");
    mvc.perform(post("/api/orders/ZZZZ99999/recovery-code", "{}", CLAIMER)).andExpect(status().isNotFound());
    mvc.perform(post("/api/orders/order12345/recovery-code", "{}", CLAIMER)).andExpect(status().isBadRequest());
  }

  @Test
  void validCodeRecoversOnlyForItsSessionAndAuditsOnce() throws Exception {
    completedOrder();
    String code = recovery.issue(requestIdOf("ORDER12345"), SessionGate.ownerHash(CLAIMER));
    // Bound to the requesting session: a stranger holding the same code is denied.
    mvc.perform(post("/api/orders/ORDER12345/recover", "{\"code\":\"" + code + "\"}", STRANGER))
        .andExpect(status().isForbidden());
    mvc.perform(post("/api/orders/ORDER12345/recover", "{\"code\":\"" + code + "\"}", CLAIMER))
        .andExpect(status().isOk()).andExpect(jsonPath("$.recovered").value(true))
        .andExpect(jsonPath("$.state").value("COMPLETED"));
    assertEquals(1, claims.count());
    String requestId = requestIdOf("ORDER12345");
    assertEquals(1, events.findByAggregateIdOrderByCreatedAtAsc(requestId).stream()
        .filter(e -> "ORDER_RECOVERED".equals(e.getType())).count());
    JsonNode forClaimer = MAPPER.readTree(mvc.perform(receipts(CLAIMER))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    assertEquals(1, forClaimer.get("orders").size());
    assertEquals("ORDER12345", forClaimer.get("orders").get(0).get("orderId").asText());
    JsonNode forStranger = MAPPER.readTree(mvc.perform(receipts(STRANGER))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    assertEquals(0, forStranger.get("orders").size());
    // Already recovered: a repeat is a harmless no-op, not a second event.
    mvc.perform(post("/api/orders/ORDER12345/recover", "{\"code\":\"" + code + "\"}", CLAIMER))
        .andExpect(status().isOk());
    assertEquals(1, events.findByAggregateIdOrderByCreatedAtAsc(requestId).stream()
        .filter(e -> "ORDER_RECOVERED".equals(e.getType())).count());
  }

  @Test
  void codesAreSingleUseAndBurnAfterRepeatedWrongAttempts() {
    String code = recovery.issue("req-1", SessionGate.ownerHash(CLAIMER));
    assertTrue(recovery.redeem(code, "req-1", SessionGate.ownerHash(CLAIMER)));
    assertFalse(recovery.redeem(code, "req-1", SessionGate.ownerHash(CLAIMER)), "second use must fail");
    String code2 = recovery.issue("req-2", SessionGate.ownerHash(CLAIMER));
    for (int i = 0; i < 5; i++)
      assertFalse(recovery.redeem(code2, "req-2", SessionGate.ownerHash(STRANGER)));
    assertFalse(recovery.redeem(code2, "req-2", SessionGate.ownerHash(CLAIMER)),
        "repeated wrong attempts burn the legitimate code");
    assertFalse(recovery.redeem(null, "req-1", SessionGate.ownerHash(CLAIMER)));
  }

  @Test
  void recoveringOwnOrderIsANoop() throws Exception {
    completedOrder();
    mvc.perform(post("/api/orders/ORDER12345/recover", "{\"code\":\"AAAA-BBBB\"}", OWNER))
        .andExpect(status().isOk());
    assertEquals(0, claims.count());
    String requestId = requestIdOf("ORDER12345");
    assertTrue(events.findByAggregateIdOrderByCreatedAtAsc(requestId).stream()
        .noneMatch(e -> "ORDER_RECOVERED".equals(e.getType())));
  }

  @Test
  void recoveredOrderSupportsMakeGoodAndBlocksLegacyRefund() throws Exception {
    completedOrder();
    String code = recovery.issue(requestIdOf("ORDER12345"), SessionGate.ownerHash(CLAIMER));
    mvc.perform(post("/api/orders/ORDER12345/recover", "{\"code\":\"" + code + "\"}", CLAIMER))
        .andExpect(status().isOk());
    String body = "{\"amount\":\"3.50\",\"reason\":\"client reports missing work\","
        + "\"deliveryText\":\"" + DELIVERY + "\"}";
    mvc.perform(post("/api/orders/ORDER12345/refund-requests", body, STRANGER)).andExpect(status().isNotFound());
    mvc.perform(post("/api/orders/ORDER12345/refund-requests", body, CLAIMER))
        .andExpect(status().isCreated()).andExpect(jsonPath("$.state").value("DRAFT"));
    mvc.perform(post("/api/orders/ORDER12345/refund", "{}", CLAIMER))
        .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("USE_MAKE_GOOD_REFUND"));
  }

  @Test
  void legacyRefundWorksForRecovererAndNotForStranger() throws Exception {
    completedOrder();
    mvc.perform(post("/api/orders/ORDER12345/refund", "{}", STRANGER)).andExpect(status().isNotFound());
    String code = recovery.issue(requestIdOf("ORDER12345"), SessionGate.ownerHash(CLAIMER));
    mvc.perform(post("/api/orders/ORDER12345/recover", "{\"code\":\"" + code + "\"}", CLAIMER))
        .andExpect(status().isOk());
    mvc.perform(post("/api/orders/ORDER12345/refund", "{}", CLAIMER))
        .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REFUNDED"));
  }
}
