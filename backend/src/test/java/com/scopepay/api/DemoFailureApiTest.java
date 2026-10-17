package com.scopepay.api;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.scopepay.store.OutboxEvent;
import com.scopepay.store.OutboxEventRepository;
import com.scopepay.store.SandboxOrderRepository;
import com.scopepay.store.ScopeDraftRepository;
import java.util.List;
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

/** Failure injection drives the scripted PayPal stand-in; the server's REAL fail-closed code handles each case. */
@SpringBootTest(properties = {"PAYPAL_CLIENT_ID=test-public-id", "REQUIRE_REVIEWED_SCOPE=false",
    "DEMO_TOOLS=true", "PAYPAL_WEBHOOK_ID=test-webhook-id"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestStubs.class)
class DemoFailureApiTest {
  private static final String ORIGIN = "http://localhost:5173";
  private static final String SESSION = "failure-session-01234";
  private static final String KEY = "failure-idem-01234567";

  @Autowired MockMvc mvc;
  @Autowired TestStubs.StubPayPal paypal;
  @Autowired SandboxOrderRepository orders;
  @Autowired ScopeDraftRepository drafts;
  @Autowired OutboxEventRepository outboxEvents;
  @Autowired DemoControls controls;

  @BeforeEach
  void reset() {
    outboxEvents.deleteAll(); orders.deleteAll(); drafts.deleteAll();
    paypal.reset(); controls.setMode("off");
  }

  @AfterEach
  void off() { controls.setMode("off"); }

  private org.springframework.test.web.servlet.RequestBuilder post(String path, String body) {
    return org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path)
        .header("Origin", ORIGIN).header("x-demo-session", SESSION)
        .header("idempotency-key", KEY)
        .contentType(MediaType.APPLICATION_JSON).content(body);
  }

  private void mode(String mode) throws Exception {
    mvc.perform(post("/api/demo/failure-mode", "{\"mode\":\"" + mode + "\"}"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.mode").value(mode));
  }

  @Test
  void invalidFailureModeRejected() throws Exception {
    mvc.perform(post("/api/demo/failure-mode", "{\"mode\":\"rm-rf\"}"))
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("INVALID_FAILURE_MODE"));
    assertEquals("off", controls.mode());
  }

  @Test
  void capturePendingDrivesRealPendingReviewPath() throws Exception {
    mode("capture-pending");
    mvc.perform(post("/api/orders", "{\"sku\":\"sandbox-demo\"}")).andExpect(status().isCreated());
    mvc.perform(post("/api/orders/ORDER12345/capture", "{}")).andExpect(status().isConflict())
        .andExpect(jsonPath("$.status").value("PENDING_REVIEW"));
    assertEquals("PENDING_REVIEW", orders.findById(KEY).orElseThrow().getState());
    assertEquals(List.of("ORDER_CREATED", "ORDER_PENDING_REVIEW"),
        outboxEvents.findByAggregateIdOrderByCreatedAtAsc(KEY).stream().map(OutboxEvent::getType).toList());
  }

  @Test
  void captureWrongAmountDrivesRealUnconfirmedPath() throws Exception {
    mode("capture-wrong-amount");
    mvc.perform(post("/api/orders", "{\"sku\":\"sandbox-demo\"}")).andExpect(status().isCreated());
    mvc.perform(post("/api/orders/ORDER12345/capture", "{}")).andExpect(status().isBadGateway())
        .andExpect(jsonPath("$.error").value("PAYMENT_UNCONFIRMED"));
    assertEquals("CREATED", orders.findById(KEY).orElseThrow().getState(),
        "a mismatched amount must never complete the order");
  }

  @Test
  void createDownKeepsReservationAndRetryConflicts() throws Exception {
    mode("create-down");
    mvc.perform(post("/api/orders", "{\"sku\":\"sandbox-demo\"}")).andExpect(status().isBadGateway());
    mvc.perform(post("/api/orders", "{\"sku\":\"sandbox-demo\"}")).andExpect(status().isConflict())
        .andExpect(jsonPath("$.error").value("CREATE_PENDING_REVIEW"));
    assertEquals(1, paypal.creates.get(), "the reservation blocks a silent duplicate create");
    // recovering clears the way: off mode, fresh key succeeds
    mode("off");
    mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/orders")
            .header("Origin", ORIGIN).header("x-demo-session", SESSION)
            .header("idempotency-key", "failure-idem-recovery")
            .contentType(MediaType.APPLICATION_JSON).content("{\"sku\":\"sandbox-demo\"}"))
        .andExpect(status().isCreated());
  }
}
