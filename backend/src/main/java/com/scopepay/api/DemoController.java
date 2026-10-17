package com.scopepay.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.scopepay.config.AppConfig;
import com.scopepay.scope.ScopeException;
import com.scopepay.store.SandboxOrder;
import com.scopepay.store.SandboxOrderRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Demo-only tools (webhook simulator). Disabled unless DEMO_TOOLS=true, which production config never sets.
 * The simulator feeds a synthetic event through the REAL verification path - with live sandbox credentials
 * PayPal correctly refuses it, which is itself the demonstration; only the local demo stub verifies it.
 */
@RestController
public class DemoController {
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final AppConfig config;
  private final SandboxOrderRepository orders;
  private final WebhookService webhooks;
  private final DemoControls demoControls;

  public DemoController(AppConfig config, SandboxOrderRepository orders, WebhookService webhooks, DemoControls demoControls) {
    this.config = config; this.orders = orders; this.webhooks = webhooks; this.demoControls = demoControls;
  }

  @PostMapping("/api/demo/failure-mode")
  public Map<String, Object> failureMode(@RequestBody(required = false) JsonNode body) {
    if (!config.demoTools) throw new ScopeException("NOT_FOUND", 404);
    if (body == null || !body.isObject() || !body.path("mode").isTextual()
        || !DemoControls.MODES.contains(body.path("mode").asText()))
      throw new ScopeException("INVALID_FAILURE_MODE", 400);
    demoControls.setMode(body.path("mode").asText());
    return Map.of("mode", demoControls.mode(), "simulated", true);
  }

  @PostMapping("/api/demo/webhook-simulator")
  public Map<String, Object> simulateWebhook(@RequestBody(required = false) JsonNode body, HttpServletRequest request) {
    if (!config.demoTools) throw new ScopeException("NOT_FOUND", 404);
    if (body == null || !body.isObject() || !body.path("requestId").isTextual())
      throw new ScopeException("INVALID_SIMULATION_REQUEST", 400);
    String owner = SessionGate.ownerHash(request);
    SandboxOrder order = orders.findById(body.path("requestId").asText())
        .filter(o -> owner.equals(o.getOwnerHash()))
        .orElseThrow(() -> new ScopeException("ORDER_NOT_FOUND", 404));
    if (order.getCaptureId() == null) throw new ScopeException("WEBHOOK_SIMULATION_NEEDS_CAPTURE", 409);

    ObjectNode resource = MAPPER.createObjectNode();
    resource.put("id", order.getCaptureId());
    resource.put("status", "COMPLETED");
    resource.putObject("amount").put("value", AppConfig.PRICE_VALUE).put("currency_code", AppConfig.CURRENCY);
    ObjectNode event = MAPPER.createObjectNode();
    event.put("id", "EV-SIMULATED-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
    event.put("event_type", "PAYMENT.CAPTURE.COMPLETED");
    event.set("resource", resource);

    Map<String, String> transmission = new LinkedHashMap<>();
    transmission.put("paypal-transmission-id", "simulated-" + UUID.randomUUID());
    transmission.put("paypal-transmission-time", java.time.OffsetDateTime.now().toString());
    transmission.put("paypal-cert-url", "https://api-m.sandbox.paypal.com/simulated/cert.pem");
    transmission.put("paypal-auth-algo", "SHA256withRSA");
    transmission.put("paypal-transmission-sig", "simulated-signature");

    WebhookService.Result result = webhooks.process(transmission, event);
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("status", result.code());
    out.put("verification", result.verification() == null ? "UNAVAILABLE" : result.verification());
    out.put("requestId", order.getRequestId());
    out.put("simulated", true);
    return out;
  }
}
