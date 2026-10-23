package com.scopepay.paypal;

import static org.junit.jupiter.api.Assertions.*;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class PayPalClientTest {
  private HttpServer server;
  private final List<String> urls = new ArrayList<>();
  private final List<java.util.Map<String, String>> headers = new ArrayList<>();
  private final List<String> bodies = new ArrayList<>();

  private void start(boolean authFails) throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/", exchange -> {
      urls.add(exchange.getRequestURI().getPath());
      var h = new java.util.HashMap<String, String>();
      exchange.getRequestHeaders().forEach((k, v) -> h.put(k.toLowerCase(), String.join(",", v)));
      headers.add(h);
      bodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
      String path = exchange.getRequestURI().getPath();
      byte[] out;
      if (path.endsWith("/token")) {
        if (authFails) {
          out = "{}".getBytes();
          exchange.sendResponseHeaders(401, out.length);
        } else {
          out = "{\"access_token\":\"fake-token\",\"expires_in\":3600}".getBytes();
          exchange.sendResponseHeaders(200, out.length);
        }
      } else {
        out = "{\"id\":\"ORDER12345\",\"status\":\"CREATED\"}".getBytes();
        exchange.sendResponseHeaders(200, out.length);
      }
      exchange.getResponseBody().write(out);
      exchange.close();
    });
    server.start();
  }

  @AfterEach
  void stop() { if (server != null) server.stop(0); }

  @Test
  void sandboxOnlySendsTokenAndStableRequestIdReusesOauth() throws Exception {
    start(false);
    String base = "http://127.0.0.1:" + server.getAddress().getPort();
    PayPalClient p = new PayPalClient("fake", "test-only", base, HttpClient.newHttpClient());
    p.createOrder("create-123", "test", "10.00");
    p.captureOrder("ORDER12345");
    assertEquals(3, urls.size());
    assertTrue(urls.get(0).endsWith("/token"));
    assertEquals("/v2/checkout/orders", urls.get(1));
    assertEquals("/v2/checkout/orders/ORDER12345/capture", urls.get(2));
    assertEquals("Bearer fake-token", headers.get(1).get("authorization"));
    assertEquals("create-123", headers.get(1).get("paypal-request-id"));
    assertEquals("capture-ORDER12345", headers.get(2).get("paypal-request-id"));
    assertTrue(bodies.get(1).contains("\"value\":\"10.00\""));
    assertTrue(bodies.get(1).contains("\"currency_code\":\"USD\""));
  }

  @Test
  void refundUsesSingularCapturesRefundRoute() throws Exception {
    start(false);
    String base = "http://127.0.0.1:" + server.getAddress().getPort();
    PayPalClient p = new PayPalClient("fake", "test-only", base, HttpClient.newHttpClient());
    p.refundCapture("CAPTURE123");
    // Official Payments v2 reference: POST /v2/payments/captures/{capture_id}/refund (singular).
    // A plural "/refunds" route 404s against the real sandbox; assert the exact path so the
    // stub-based API tests cannot mask a route regression again.
    assertEquals("/v2/payments/captures/CAPTURE123/refund", urls.get(1));
    assertEquals("refund-CAPTURE123", headers.get(1).get("paypal-request-id"));
  }

  @Test
  void productionBaseIsSandboxOnly() throws Exception {
    start(false);
    // The default constructor must hardcode the sandbox API base; no live mode exists.
    PayPalClient p = new PayPalClient("fake", "test-only");
    var field = PayPalClient.class.getDeclaredField("api");
    field.setAccessible(true);
    assertEquals("https://api-m.sandbox.paypal.com", field.get(p));
  }

  @Test
  void upstreamAuthSecretsNotExposedInError() throws Exception {
    start(true);
    String base = "http://127.0.0.1:" + server.getAddress().getPort();
    PayPalClient p = new PayPalClient("fake", "test-only", base, HttpClient.newHttpClient());
    PayPalException e = assertThrows(PayPalException.class, () -> p.createOrder("t", "s", "10.00"));
    assertEquals("PAYPAL_AUTH_FAILED", e.getIssue());
    assertFalse(e.getMessage().contains("test-only"));
  }
}
