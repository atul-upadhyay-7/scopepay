package com.scopepay.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scopepay.paypal.PayPalClient;
import com.scopepay.scope.GenerateResult;
import com.scopepay.scope.ScopeProvider;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration
public class TestStubs {
  public static final ObjectMapper MAPPER = new ObjectMapper();

  public static class StubPayPal extends PayPalClient {
    public final AtomicInteger creates = new AtomicInteger();
    public final AtomicInteger captures = new AtomicInteger();
    public final AtomicInteger refunds = new AtomicInteger();
    public volatile DemoControls controls;
    public volatile Supplier<JsonNode> createBehavior = () ->
        MAPPER.createObjectNode().put("id", "ORDER12345").put("status", "CREATED");
    public volatile Supplier<JsonNode> captureBehavior = () -> completedCapture();
    public volatile Supplier<JsonNode> refundBehavior = () -> completedRefund();
    public volatile Supplier<JsonNode> getBehavior = () ->
        MAPPER.createObjectNode().put("id", "ORDER12345").put("status", "APPROVED");

    public StubPayPal() { super("stub", "stub"); }

    static JsonNode completedCapture() {
      var root = MAPPER.createObjectNode();
      root.put("status", "COMPLETED");
      var unit = root.putArray("purchase_units").addObject();
      var capture = unit.putObject("payments").putArray("captures").addObject();
      capture.put("id", "CAPTURE123").put("status", "COMPLETED");
      capture.putObject("amount").put("value", "10.00").put("currency_code", "USD");
      return root;
    }

    static JsonNode completedRefund() {
      var root = MAPPER.createObjectNode();
      root.put("id", "REFUND123").put("status", "COMPLETED");
      root.putObject("amount").put("value", "10.00").put("currency_code", "USD");
      return root;
    }

    public void reset() {
      creates.set(0); captures.set(0); refunds.set(0);
      refundBehavior = TestStubs.StubPayPal::completedRefund;
      partialRefundBehavior = null;
      getRefundBehavior = () -> MAPPER.createObjectNode().put("id", "REFUND-PARTIAL-1").put("status", "COMPLETED");
      comparisonBehavior = TestStubs::defaultComparison;
      verifyStatus = "SUCCESS";
      createBehavior = () -> MAPPER.createObjectNode().put("id", "ORDER12345").put("status", "CREATED");
      captureBehavior = TestStubs.StubPayPal::completedCapture;
      getBehavior = () -> MAPPER.createObjectNode().put("id", "ORDER12345").put("status", "APPROVED");
    }

    @Override
    public JsonNode getOrder(String orderId) {
      return getBehavior.get();
    }

    @Override
    public JsonNode createOrder(String requestId, String sku, String value) {
      creates.incrementAndGet();
      if (controls != null && "create-down".equals(controls.mode()))
        throw new RuntimeException("simulated paypal outage");
      return createBehavior.get();
    }

    @Override
    public JsonNode captureOrder(String orderId) {
      captures.incrementAndGet();
      if (controls != null && "capture-pending".equals(controls.mode()))
        return MAPPER.createObjectNode().put("status", "APPROVED");
      if (controls != null && "capture-wrong-amount".equals(controls.mode())) {
        var root = MAPPER.createObjectNode();
        root.put("status", "COMPLETED");
        var capture = root.putArray("purchase_units").addObject().putObject("payments").putArray("captures").addObject();
        capture.put("id", "CAPTURE123").put("status", "COMPLETED");
        capture.putObject("amount").put("value", "0.01").put("currency_code", "USD");
        return root;
      }
      return captureBehavior.get();
    }

    @Override
    public JsonNode refundCapture(String captureId) {
      refunds.incrementAndGet();
      return refundBehavior.get();
    }

    public volatile Supplier<JsonNode> partialRefundBehavior = null; // null = echo the requested amount as COMPLETED
    public volatile Supplier<JsonNode> getRefundBehavior = () ->
        MAPPER.createObjectNode().put("id", "REFUND-PARTIAL-1").put("status", "COMPLETED");
    public volatile String lastPartialValue;
    public volatile String lastPartialCurrency;
    public volatile String lastPartialKey;

    static JsonNode defaultPartialRefund() {
      var root = MAPPER.createObjectNode();
      root.put("id", "REFUND-PARTIAL-1").put("status", "COMPLETED");
      root.putObject("amount").put("value", "3.50").put("currency_code", "USD");
      return root;
    }

    @Override
    public JsonNode refundCapture(String captureId, String value, String currency, String idempotencyKey) {
      refunds.incrementAndGet();
      lastPartialValue = value; lastPartialCurrency = currency; lastPartialKey = idempotencyKey;
      if (partialRefundBehavior != null) return partialRefundBehavior.get();
      var root = MAPPER.createObjectNode();
      root.put("id", "REFUND-PARTIAL-1").put("status", "COMPLETED");
      root.putObject("amount").put("value", value).put("currency_code", currency);
      return root;
    }

    @Override
    public JsonNode getRefund(String refundId) {
      return getRefundBehavior.get();
    }

    public volatile String verifyStatus = "SUCCESS";

    @Override
    public String verifyWebhookSignature(java.util.Map<String, String> transmission, JsonNode webhookEvent, String webhookId) {
      return verifyStatus;
    }
  }

  @Bean
  @Primary
  public StubPayPal stubPayPal(DemoControls controls) {
    StubPayPal stub = new StubPayPal();
    stub.controls = controls;
    return stub;
  }

  public static volatile java.util.function.Function<String, JsonNode> comparisonBehavior = TestStubs::defaultComparison;

  static JsonNode defaultComparison(String account) {
    var root = MAPPER.createObjectNode();
    var item = root.putArray("comparisons").addObject();
    item.put("deliverable", "Contact form").put("verdict", "CLAIMED_PARTIAL")
        .put("quote", "delivered the contact form").put("note", "stub note");
    root.put("overallNote", "Stub comparison for tests.");
    return root;
  }

  @Bean
  public ScopeProvider stubScopeProvider() {
    return (instructions, brief, schema) -> {
      if (schema.contains("comparisons")) {
        JsonNode out = comparisonBehavior.apply(brief);
        if (schema.contains("complaintCoverage") && out.isObject() && !out.has("complaintCoverage"))
          ((com.fasterxml.jackson.databind.node.ObjectNode) out).putArray("complaintCoverage");
        return GenerateResult.ofData(out);
      }
      var d = MAPPER.createObjectNode();
      d.put("summary", "A contact website.");
      d.putArray("deliverables").addObject().put("description", "Contact form").put("evidence", "a contact form");
      d.putArray("questions").add("Where should messages go?");
      d.putArray("conflicts");
      return GenerateResult.ofData(d);
    };
  }
}
