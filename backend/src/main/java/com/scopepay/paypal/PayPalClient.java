package com.scopepay.paypal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.Callable;

/** Direct PayPal REST integration over the real sandbox endpoints. No live mode exists. */
public class PayPalClient {
  private static final String SANDBOX_API = "https://api-m.sandbox.paypal.com";
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private final String clientId;
  private final String clientSecret;
  private final String api;
  private final HttpClient client;
  private String token;
  private long tokenExpiresAtMs;
  private volatile Retry retry;
  private volatile CircuitBreaker circuitBreaker;

  /** Retry + circuit breaker for transient PayPal faults. Business errors (4xx) never retry. */
  public void setResilience(Retry retry, CircuitBreaker circuitBreaker) {
    this.retry = retry; this.circuitBreaker = circuitBreaker;
  }

  /** Production wiring: 3 attempts with exponential backoff on transient faults, circuit opens at 60% failures. */
  public void enableDefaultResilience() {
    io.github.resilience4j.retry.RetryConfig retryConfig = io.github.resilience4j.retry.RetryConfig.custom()
        .maxAttempts(3)
        .intervalFunction(io.github.resilience4j.core.IntervalFunction
            .ofExponentialBackoff(java.time.Duration.ofMillis(500), 2.0))
        .retryOnException(PayPalClient::isTransient)
        .build();
    io.github.resilience4j.circuitbreaker.CircuitBreakerConfig breakerConfig =
        io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.custom()
            .slidingWindowType(io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
            .slidingWindowSize(10)
            .minimumNumberOfCalls(5)
            .failureRateThreshold(60)
            .waitDurationInOpenState(java.time.Duration.ofSeconds(30))
            .build();
    setResilience(io.github.resilience4j.retry.Retry.of("paypal", retryConfig),
        io.github.resilience4j.circuitbreaker.CircuitBreaker.of("paypal", breakerConfig));
  }

  static boolean isTransient(Throwable e) {
    if (e instanceof IOException) return true;
    if (e instanceof PayPalException p) return p.getStatus() >= 500 || p.getStatus() == 429;
    return false;
  }

  public PayPalClient(String clientId, String clientSecret) {
    this(clientId, clientSecret, SANDBOX_API, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
  }

  /** Test constructor: endpoint and transport injection. Production code uses only the sandbox base. */
  public PayPalClient(String clientId, String clientSecret, String api, HttpClient client) {
    this.clientId = clientId; this.clientSecret = clientSecret; this.api = api; this.client = client;
  }

  private synchronized String accessToken() throws Exception {
    if (token != null && System.currentTimeMillis() < tokenExpiresAtMs) return token;
    String basic = Base64.getEncoder().encodeToString((clientId + ":" + clientSecret).getBytes(StandardCharsets.UTF_8));
    HttpRequest request = HttpRequest.newBuilder(URI.create(api + "/v1/oauth2/token"))
        .timeout(Duration.ofSeconds(15))
        .header("Authorization", "Basic " + basic)
        .header("Content-Type", "application/x-www-form-urlencoded")
        .POST(HttpRequest.BodyPublishers.ofString("grant_type=client_credentials")).build();
    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
    if (response.statusCode() != 200) throw new PayPalException(response.statusCode(), "PAYPAL_AUTH_FAILED", List.of("PAYPAL_AUTH_FAILED"));
    JsonNode data = MAPPER.readTree(response.body());
    token = data.path("access_token").asText();
    long expiresIn = data.path("expires_in").asLong(300);
    tokenExpiresAtMs = System.currentTimeMillis() + (Math.max(expiresIn, 1) - 60) * 1000;
    return token;
  }

  private JsonNode request(String method, String path, JsonNode body, String idempotencyKey) throws Exception {
    Callable<JsonNode> call = () -> doRequest(method, path, body, idempotencyKey);
    if (circuitBreaker != null) call = CircuitBreaker.decorateCallable(circuitBreaker, call);
    if (retry != null) call = Retry.decorateCallable(retry, call);
    try {
      return call.call();
    } catch (CallNotPermittedException e) {
      throw new PayPalException(503, "PAYPAL_CIRCUIT_OPEN", List.of("PAYPAL_CIRCUIT_OPEN"));
    }
  }

  private JsonNode doRequest(String method, String path, JsonNode body, String idempotencyKey) throws Exception {
    String uri = api + path;
    HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(uri))
        .timeout(Duration.ofSeconds(20))
        .header("Authorization", "Bearer " + accessToken())
        .header("Content-Type", "application/json");
    if (idempotencyKey != null) builder.header("PayPal-Request-Id", idempotencyKey);
    if ("POST".equals(method)) builder.POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body)));
    else builder.GET();
    HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    JsonNode data;
    try { data = MAPPER.readTree(response.body()); } catch (Exception e) { data = MAPPER.createObjectNode(); }
    if (response.statusCode() >= 400) {
      List<String> issues = new ArrayList<>();
      if (data.path("details").isArray()) for (JsonNode d : data.path("details")) {
        String issue = d.path("issue").asText(null);
        if (issue != null) issues.add(issue);
      }
      throw new PayPalException(response.statusCode(),
          data.path("name").asText("PAYPAL_REQUEST_FAILED"), issues);
    }
    return data;
  }

  public JsonNode createOrder(String requestId, String sku, String value) throws Exception {
    ObjectNode payload = MAPPER.createObjectNode();
    payload.put("intent", "CAPTURE");
    ArrayNode units = payload.putArray("purchase_units");
    ObjectNode unit = units.addObject();
    unit.put("custom_id", sku);
    ObjectNode amount = unit.putObject("amount");
    amount.put("currency_code", "USD");
    amount.put("value", value);
    ObjectNode context = payload.putObject("payment_source").putObject("paypal").putObject("experience_context");
    context.put("shipping_preference", "NO_SHIPPING");
    return request("POST", "/v2/checkout/orders", payload, requestId);
  }

  public JsonNode captureOrder(String orderId) throws Exception {
    return request("POST", "/v2/checkout/orders/" + orderId + "/capture", MAPPER.createObjectNode(), "capture-" + orderId);
  }

  /** Reads an order back from PayPal. Used by the reconciler; never trusts local state alone. */
  public JsonNode getOrder(String orderId) throws Exception {
    return request("GET", "/v2/checkout/orders/" + orderId, null, null);
  }

  /** Asks PayPal's own verify endpoint whether a webhook transmission is genuine. Never trusts the payload alone. */
  public String verifyWebhookSignature(java.util.Map<String, String> transmission, JsonNode webhookEvent, String webhookId) throws Exception {
    ObjectNode payload = MAPPER.createObjectNode();
    payload.put("transmission_id", transmission.get("paypal-transmission-id"));
    payload.put("transmission_time", transmission.get("paypal-transmission-time"));
    payload.put("cert_url", transmission.get("paypal-cert-url"));
    payload.put("auth_algo", transmission.get("paypal-auth-algo"));
    payload.put("transmission_sig", transmission.get("paypal-transmission-sig"));
    payload.put("webhook_id", webhookId);
    payload.set("webhook_event", webhookEvent);
    JsonNode data = request("POST", "/v1/notifications/verify-webhook-signature", payload, null);
    return data.path("verification_status").asText("");
  }
}
