package com.scopepay.api;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.scopepay.store.LedgerEntry;
import com.scopepay.store.LedgerEntryRepository;
import com.scopepay.store.OutboxEvent;
import com.scopepay.store.OutboxEventRepository;
import com.scopepay.store.SandboxOrderRepository;
import com.scopepay.store.ScopeDraftRepository;
import java.math.BigDecimal;
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

@SpringBootTest(properties = {"PAYPAL_CLIENT_ID=test-public-id", "REQUIRE_REVIEWED_SCOPE=false"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestStubs.class)
class LedgerOutboxApiTest {
  private static final String ORIGIN = "http://localhost:5173";
  private static final String SESSION = "ledger-session-0123456";
  private static final String KEY = "ledger-idem-0123456789";

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

  @Test
  void completedCaptureWritesBalancedDoubleEntryAndOutbox() throws Exception {
    mvc.perform(post("/api/orders", "{\"sku\":\"sandbox-demo\"}")).andExpect(status().isCreated());
    mvc.perform(post("/api/orders/ORDER12345/capture", "{}")).andExpect(status().isOk());

    List<LedgerEntry> entries = ledgerEntries.findByRequestIdOrderByCreatedAtAsc(KEY);
    assertEquals(2, entries.size(), "one debit and one credit per capture");
    BigDecimal net = entries.stream()
        .map(e -> "DEBIT".equals(e.getDirection()) ? e.getAmount() : e.getAmount().negate())
        .reduce(BigDecimal.ZERO, BigDecimal::add);
    assertEquals(0, net.compareTo(BigDecimal.ZERO), "ledger group must net to zero");
    assertTrue(entries.stream().allMatch(e -> "USD".equals(e.getCurrency())
        && e.getGroupId().equals(entries.get(0).getGroupId())));
    assertTrue(entries.stream().anyMatch(e -> "paypal_sandbox_clearing".equals(e.getAccount())));
    assertTrue(entries.stream().anyMatch(e -> "demo_revenue".equals(e.getAccount())));

    List<OutboxEvent> events = outboxEvents.findByAggregateIdOrderByCreatedAtAsc(KEY);
    assertEquals(List.of("ORDER_CREATED", "ORDER_COMPLETED"),
        events.stream().map(OutboxEvent::getType).toList());
    OutboxEvent completed = events.get(1);
    assertEquals("CAPTURE123", completed.getPayload().path("captureId").asText());
    assertEquals(entries.get(0).getGroupId(), completed.getPayload().path("ledgerGroupId").asText());
    assertTrue(events.stream().allMatch(e -> e.getPublishedAt() == null), "events await a publisher");
  }

  @Test
  void idempotentCaptureRetryDoesNotDoubleWriteLedger() throws Exception {
    mvc.perform(post("/api/orders", "{\"sku\":\"sandbox-demo\"}")).andExpect(status().isCreated());
    mvc.perform(post("/api/orders/ORDER12345/capture", "{}")).andExpect(status().isOk());
    mvc.perform(post("/api/orders/ORDER12345/capture", "{}")).andExpect(status().isOk());
    assertEquals(2, ledgerEntries.findByRequestIdOrderByCreatedAtAsc(KEY).size(),
        "a replayed capture must not post the ledger twice");
    assertEquals(1, paypal.captures.get());
  }

  @Test
  void pendingCaptureWritesOutboxButNoLedger() throws Exception {
    paypal.captureBehavior = () -> TestStubs.MAPPER.createObjectNode().put("status", "APPROVED");
    mvc.perform(post("/api/orders", "{\"sku\":\"sandbox-demo\"}")).andExpect(status().isCreated());
    mvc.perform(post("/api/orders/ORDER12345/capture", "{}")).andExpect(status().isConflict());

    assertTrue(ledgerEntries.findByRequestIdOrderByCreatedAtAsc(KEY).isEmpty(),
        "no money moved, so no ledger entries");
    List<OutboxEvent> events = outboxEvents.findByAggregateIdOrderByCreatedAtAsc(KEY);
    assertEquals(List.of("ORDER_CREATED", "ORDER_PENDING_REVIEW"),
        events.stream().map(OutboxEvent::getType).toList());
  }

  @Test
  void unconfirmedAmountRollsBackCleanly() throws Exception {
    paypal.captureBehavior = () -> {
      var root = TestStubs.MAPPER.createObjectNode();
      root.put("status", "COMPLETED");
      var capture = root.putArray("purchase_units").addObject().putObject("payments").putArray("captures").addObject();
      capture.put("id", "CAPTURE123").put("status", "COMPLETED");
      capture.putObject("amount").put("value", "9.99").put("currency_code", "USD");
      return root;
    };
    mvc.perform(post("/api/orders", "{\"sku\":\"sandbox-demo\"}")).andExpect(status().isCreated());
    mvc.perform(post("/api/orders/ORDER12345/capture", "{}")).andExpect(status().isBadGateway());

    assertTrue(ledgerEntries.findByRequestIdOrderByCreatedAtAsc(KEY).isEmpty());
    assertEquals(List.of("ORDER_CREATED"),
        outboxEvents.findByAggregateIdOrderByCreatedAtAsc(KEY).stream().map(OutboxEvent::getType).toList());
    assertEquals("CREATED", orders.findById(KEY).orElseThrow().getState());
  }

  @Test
  void scopeExtractAndReviewWriteOutboxEvents() throws Exception {
    MvcResultHolder holder = new MvcResultHolder();
    holder.result = mvc.perform(post("/api/scopes/extract", "{\"brief\":\"A site with a contact form.\"}"))
        .andExpect(status().isOk()).andReturn();
    String scopeId = TestStubs.MAPPER.readTree(holder.result.getResponse().getContentAsString())
        .path("scopeId").asText();
    mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
            .post("/api/scopes/" + scopeId + "/review")
        .header("Origin", ORIGIN).header("x-demo-session", SESSION)
        .contentType(MediaType.APPLICATION_JSON).content("{\"acknowledge\":true}"))
        .andExpect(status().isOk());

    List<OutboxEvent> events = outboxEvents.findByAggregateIdOrderByCreatedAtAsc(scopeId);
    assertEquals(List.of("SCOPE_EXTRACTED", "SCOPE_REVIEWED"),
        events.stream().map(OutboxEvent::getType).toList());
    assertEquals("scope", events.get(0).getAggregate());
  }

  @Test
  void receiptsExposeOrderTimelineAndLedger() throws Exception {
    mvc.perform(post("/api/orders", "{\"sku\":\"sandbox-demo\"}")).andExpect(status().isCreated());
    mvc.perform(post("/api/orders/ORDER12345/capture", "{}")).andExpect(status().isOk());

    String json = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
            .get("/api/receipts").header("Origin", ORIGIN).header("x-demo-session", SESSION))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    JsonNode order = TestStubs.MAPPER.readTree(json).path("orders").path(0);
    assertEquals(List.of("ORDER_CREATED", "ORDER_COMPLETED"),
        TestStubs.MAPPER.convertValue(order.path("timeline"),
            new com.fasterxml.jackson.core.type.TypeReference<List<JsonNode>>() {})
            .stream().map(e -> e.path("type").asText()).toList());
    assertEquals("CAPTURE123", order.path("timeline").path(1).path("payload").path("captureId").asText());
    assertEquals(2, order.path("ledger").size());
    assertEquals("DEBIT", order.path("ledger").path(0).path("direction").asText());
    assertEquals("CREDIT", order.path("ledger").path(1).path("direction").asText());
    // another session sees none of this
    String other = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
            .get("/api/receipts").header("Origin", ORIGIN).header("x-demo-session", "someone-else-session-1"))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    assertEquals(0, TestStubs.MAPPER.readTree(other).path("orders").size());
  }

  private static final class MvcResultHolder {
    org.springframework.test.web.servlet.MvcResult result;
  }
}
