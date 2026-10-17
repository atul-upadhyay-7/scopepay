package com.scopepay.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.scopepay.config.AppConfig;
import com.scopepay.paypal.PayPalClient;
import com.scopepay.store.OutboxService;
import com.scopepay.store.SandboxOrder;
import com.scopepay.store.SandboxOrderRepository;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * PayPal webhook intake. The payload is untrusted until PayPal's own verify endpoint confirms the
 * transmission; anything unverifiable is rejected and recorded, never acted on. A verified webhook
 * never changes order state by itself - the capture path and the state machine stay the authority;
 * the webhook only adds a confirmation event to the order's audit trail.
 */
@Service
public class WebhookService {
  private static final Set<String> REQUIRED_HEADERS = Set.of(
      "paypal-transmission-id", "paypal-transmission-time", "paypal-cert-url", "paypal-auth-algo", "paypal-transmission-sig");
  private static final Set<String> TRUSTED_CERT_HOSTS = Set.of(
      "api.paypal.com", "api.sandbox.paypal.com", "api-m.sandbox.paypal.com");

  private final AppConfig config;
  private final PayPalClient paypal;
  private final SandboxOrderRepository orders;
  private final OutboxService outbox;

  public WebhookService(AppConfig config, PayPalClient paypal, SandboxOrderRepository orders, OutboxService outbox) {
    this.config = config; this.paypal = paypal; this.orders = orders; this.outbox = outbox;
  }

  public record Result(int status, String code, String verification, String requestId) {}

  public Result process(Map<String, String> transmission, JsonNode event) {
    if (config.paypalWebhookId == null || config.paypalWebhookId.isBlank())
      return new Result(503, "WEBHOOK_NOT_CONFIGURED", null, null);
    if (event == null || !event.isObject()) return new Result(400, "INVALID_WEBHOOK", null, null);
    for (String h : REQUIRED_HEADERS) {
      String v = transmission.get(h);
      if (v == null || v.isBlank()) return new Result(400, "WEBHOOK_HEADERS_MISSING", null, null);
    }
    try {
      String host = URI.create(transmission.get("paypal-cert-url")).getHost();
      if (host == null || !TRUSTED_CERT_HOSTS.contains(host)) return reject(transmission, event, "untrusted cert url");
    } catch (Exception e) {
      return reject(transmission, event, "unparseable cert url");
    }

    String verification;
    try {
      verification = paypal.verifyWebhookSignature(transmission, event, config.paypalWebhookId);
    } catch (Exception e) {
      // Verification itself could not be completed: fail closed, record nothing as confirmed.
      return new Result(502, "WEBHOOK_VERIFICATION_UNAVAILABLE", null, null);
    }
    if (!"SUCCESS".equals(verification)) return reject(transmission, event, "verification status " + verification);

    String eventType = event.path("event_type").asText("");
    if (!"PAYMENT.CAPTURE.COMPLETED".equals(eventType)) {
      outbox.record("webhook", transmission.get("paypal-transmission-id"), "WEBHOOK_IGNORED",
          Map.of("reason", "unhandled event type", "eventType", eventType));
      return new Result(202, "WEBHOOK_IGNORED", verification, null);
    }
    String captureId = event.path("resource").path("id").asText("");
    Optional<SandboxOrder> match = captureId.isEmpty() ? Optional.empty() : orders.findByCaptureId(captureId);
    if (match.isEmpty()) {
      outbox.record("webhook", transmission.get("paypal-transmission-id"), "WEBHOOK_IGNORED",
          Map.of("reason", "verified but no matching order", "captureId", captureId));
      return new Result(202, "WEBHOOK_IGNORED", verification, null);
    }
    SandboxOrder order = match.get();
    Map<String, String> fields = new LinkedHashMap<>();
    fields.put("captureId", captureId);
    fields.put("eventId", event.path("id").asText(""));
    fields.put("orderId", order.getOrderId() == null ? "" : order.getOrderId());
    outbox.record("order", order.getRequestId(), "WEBHOOK_CONFIRMED", fields);
    return new Result(200, "WEBHOOK_CONFIRMED", verification, order.getRequestId());
  }

  private Result reject(Map<String, String> transmission, JsonNode event, String reason) {
    Map<String, String> fields = new LinkedHashMap<>();
    fields.put("reason", reason);
    fields.put("eventType", event == null ? "" : event.path("event_type").asText(""));
    outbox.record("webhook", transmission.getOrDefault("paypal-transmission-id", "unknown"),
        "WEBHOOK_REJECTED", fields);
    return new Result(400, "WEBHOOK_REJECTED", "FAILURE", null);
  }
}
