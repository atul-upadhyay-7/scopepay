package com.scopepay.api;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.scopepay.store.LedgerEntryRepository;
import com.scopepay.store.OutboxEvent;
import com.scopepay.store.OutboxEventRepository;
import com.scopepay.store.SandboxOrderRepository;
import com.scopepay.store.ScopeDraftRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {"PAYPAL_CLIENT_ID=test-public-id", "REQUIRE_REVIEWED_SCOPE=false",
    "DEMO_TOOLS=true", "PAYPAL_WEBHOOK_ID=test-webhook-id"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestStubs.class)
class WebhookApiTest {
  private static final String ORIGIN = "http://localhost:5173";
  private static final String SESSION = "webhook-session-012345";
  private static final String KEY = "webhook-idem-012345678";

  @Autowired MockMvc mvc;
  @Autowired TestStubs.StubPayPal paypal;
  @Autowired SandboxOrderRepository orders;
  @Autowired ScopeDraftRepository drafts;
  @Autowired LedgerEntryRepository ledgerEntries;
  @Autowired OutboxEventRepository outboxEvents;

  @BeforeEach
  void reset() {
    ledgerEntries.deleteAll(); outboxEvents.deleteAll();
    orders.deleteAll(); drafts.deleteAll(); paypal.reset();
  }

  private org.springframework.test.web.servlet.RequestBuilder post(String path, String body) {
    return org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path)
        .header("Origin", ORIGIN).header("x-demo-session", SESSION)
        .header("idempotency-key", KEY)
        .contentType(MediaType.APPLICATION_JSON).content(body);
  }

  private org.springframework.test.web.servlet.RequestBuilder webhook(String eventJson) {
    return org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/webhooks/paypal")
        .header("PAYPAL-TRANSMISSION-ID", "tx-12345")
        .header("PAYPAL-TRANSMISSION-TIME", "2026-10-18T00:00:00Z")
        .header("PAYPAL-CERT-URL", "https://api-m.sandbox.paypal.com/cert.pem")
        .header("PAYPAL-AUTH-ALGO", "SHA256withRSA")
        .header("PAYPAL-TRANSMISSION-SIG", "sig")
        .contentType(MediaType.APPLICATION_JSON).content(eventJson);
  }

  private static String captureEvent(String captureId) {
    return "{\"id\":\"EV-1\",\"event_type\":\"PAYMENT.CAPTURE.COMPLETED\",\"resource\":{\"id\":\"" + captureId
        + "\",\"status\":\"COMPLETED\",\"amount\":{\"value\":\"10.00\",\"currency_code\":\"USD\"}}}";
  }

  private void createAndCapture() throws Exception {
    mvc.perform(post("/api/orders", "{\"sku\":\"sandbox-demo\"}")).andExpect(status().isCreated());
    mvc.perform(post("/api/orders/ORDER12345/capture", "{}")).andExpect(status().isOk());
  }

  @Test
  void verifiedCaptureWebhookIsRecordedOnTheOrder() throws Exception {
    createAndCapture();
    // no session, no origin: PayPal is the caller, verification is the gate
    mvc.perform(webhook(captureEvent("CAPTURE123")))
        .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("WEBHOOK_CONFIRMED"));

    List<String> types = outboxEvents.findByAggregateIdOrderByCreatedAtAsc(KEY)
        .stream().map(OutboxEvent::getType).toList();
    assertEquals(List.of("ORDER_CREATED", "ORDER_COMPLETED", "WEBHOOK_CONFIRMED"), types);
    assertEquals("COMPLETED", orders.findById(KEY).orElseThrow().getState(),
        "a webhook never moves the state machine");
  }

  @Test
  void forgedWebhookIsRejectedAndRecorded() throws Exception {
    createAndCapture();
    paypal.verifyStatus = "FAILURE";
    mvc.perform(webhook(captureEvent("CAPTURE123")))
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value("WEBHOOK_REJECTED"));

    assertEquals(List.of("ORDER_CREATED", "ORDER_COMPLETED"),
        outboxEvents.findByAggregateIdOrderByCreatedAtAsc(KEY).stream().map(OutboxEvent::getType).toList(),
        "nothing is added to the order's trail by a forged webhook");
    assertEquals("WEBHOOK_REJECTED",
        outboxEvents.findByAggregateIdOrderByCreatedAtAsc("tx-12345").get(0).getType());
    assertEquals("COMPLETED", orders.findById(KEY).orElseThrow().getState());
  }

  @Test
  void verifiedWebhookForUnknownCaptureIsIgnored() throws Exception {
    mvc.perform(webhook(captureEvent("CAPTURE-UNKNOWN")))
        .andExpect(status().isAccepted()).andExpect(jsonPath("$.status").value("WEBHOOK_IGNORED"));
    assertEquals("WEBHOOK_IGNORED",
        outboxEvents.findByAggregateIdOrderByCreatedAtAsc("tx-12345").get(0).getType());
  }

  @Test
  void webhookWithMissingTransmissionHeadersFailsClosed() throws Exception {
    mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/webhooks/paypal")
            .contentType(MediaType.APPLICATION_JSON).content(captureEvent("CAPTURE123")))
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value("WEBHOOK_HEADERS_MISSING"));
  }

  @Test
  void webhookWithUntrustedCertUrlIsRejected() throws Exception {
    mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/webhooks/paypal")
            .header("PAYPAL-TRANSMISSION-ID", "tx-evil")
            .header("PAYPAL-TRANSMISSION-TIME", "2026-10-18T00:00:00Z")
            .header("PAYPAL-CERT-URL", "https://evil.example.com/cert.pem")
            .header("PAYPAL-AUTH-ALGO", "SHA256withRSA")
            .header("PAYPAL-TRANSMISSION-SIG", "sig")
            .contentType(MediaType.APPLICATION_JSON).content(captureEvent("CAPTURE123")))
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value("WEBHOOK_REJECTED"));
    assertEquals("WEBHOOK_REJECTED",
        outboxEvents.findByAggregateIdOrderByCreatedAtAsc("tx-evil").get(0).getType());
  }

  @Test
  void simulatorRunsSyntheticEventThroughTheRealVerifyPath() throws Exception {
    createAndCapture();
    JsonNode out = TestStubs.MAPPER.readTree(mvc.perform(post("/api/demo/webhook-simulator",
            "{\"requestId\":\"" + KEY + "\"}"))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    assertEquals("WEBHOOK_CONFIRMED", out.path("status").asText());
    assertTrue(out.path("simulated").asBoolean());
    assertEquals(List.of("ORDER_CREATED", "ORDER_COMPLETED", "WEBHOOK_CONFIRMED"),
        outboxEvents.findByAggregateIdOrderByCreatedAtAsc(KEY).stream().map(OutboxEvent::getType).toList());
  }

  @Test
  void simulatorRefusesOrdersWithoutACapture() throws Exception {
    mvc.perform(post("/api/orders", "{\"sku\":\"sandbox-demo\"}")).andExpect(status().isCreated());
    mvc.perform(post("/api/demo/webhook-simulator", "{\"requestId\":\"" + KEY + "\"}"))
        .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("WEBHOOK_SIMULATION_NEEDS_CAPTURE"));
  }

  @Test
  void simulatorCannotTouchAnotherSessionOrder() throws Exception {
    createAndCapture();
    mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
            .post("/api/demo/webhook-simulator")
            .header("Origin", ORIGIN).header("x-demo-session", "another-session-9999")
            .contentType(MediaType.APPLICATION_JSON).content("{\"requestId\":\"" + KEY + "\"}"))
        .andExpect(status().isNotFound());
  }
}
