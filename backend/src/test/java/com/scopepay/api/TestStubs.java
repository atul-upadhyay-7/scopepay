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
    public volatile DemoControls controls;
    public volatile Supplier<JsonNode> createBehavior = () ->
        MAPPER.createObjectNode().put("id", "ORDER12345").put("status", "CREATED");
    public volatile Supplier<JsonNode> captureBehavior = () -> completedCapture();

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

    public void reset() {
      creates.set(0); captures.set(0);
      verifyStatus = "SUCCESS";
      createBehavior = () -> MAPPER.createObjectNode().put("id", "ORDER12345").put("status", "CREATED");
      captureBehavior = TestStubs.StubPayPal::completedCapture;
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

  @Bean
  public ScopeProvider stubScopeProvider() {
    return (instructions, brief, schema) -> {
      var d = MAPPER.createObjectNode();
      d.put("summary", "A contact website.");
      d.putArray("deliverables").addObject().put("description", "Contact form").put("evidence", "a contact form");
      d.putArray("questions").add("Where should messages go?");
      d.putArray("conflicts");
      return GenerateResult.ofData(d);
    };
  }
}
