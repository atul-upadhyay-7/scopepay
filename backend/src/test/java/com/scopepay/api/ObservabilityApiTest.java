package com.scopepay.api;

import static org.junit.jupiter.api.Assertions.*;

import com.scopepay.store.SandboxOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;

@org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"PAYPAL_CLIENT_ID=test-public-id", "REQUIRE_REVIEWED_SCOPE=false"})
@ActiveProfiles("test")
@Import(TestStubs.class)
class ObservabilityApiTest {
  private static final String ORIGIN = "http://localhost:5173";

  @org.junit.jupiter.api.BeforeAll
  static void allowOriginHeader() {
    // HttpURLConnection restricts the Origin header; the SessionGate requires it.
    System.setProperty("sun.net.http.allowRestrictedHeaders", "true");
  }

  @LocalServerPort int port;
  @Autowired TestRestTemplate http;
  @Autowired TestStubs.StubPayPal paypal;
  @Autowired SandboxOrderRepository orders;

  @BeforeEach
  void reset() { orders.deleteAll(); paypal.reset(); }

  private String url(String path) { return "http://127.0.0.1:" + port + path; }

  @Test
  void actuatorHealthIsUp() {
    var response = http.getForEntity(url("/actuator/health"), String.class);
    assertEquals(200, response.getStatusCode().value());
    assertTrue(response.getBody().contains("\"UP\""));
  }

  @Test
  void openApiDocumentIsServed() {
    var response = http.getForEntity(url("/v3/api-docs"), String.class);
    assertEquals(200, response.getStatusCode().value());
    assertTrue(response.getBody().contains("ScopePay Sandbox API"));
  }

  @Test
  void prometheusExposesBusinessCounters() {
    HttpHeaders headers = new HttpHeaders();
    headers.set("Origin", ORIGIN);
    headers.set("x-demo-session", "obs-session-0000001");
    headers.set("idempotency-key", "obs-key-000000000001");
    headers.setContentType(MediaType.APPLICATION_JSON);
    var created = http.exchange(url("/api/orders"), HttpMethod.POST,
        new HttpEntity<>("{\"sku\":\"sandbox-demo\"}", headers), String.class);
    assertEquals(201, created.getStatusCode().value());

    var metricsEndpoint = http.getForEntity(url("/actuator/metrics/scopepay.orders.placed"), String.class);
    assertEquals(200, metricsEndpoint.getStatusCode().value());
    assertTrue(metricsEndpoint.getBody().contains("scopepay.orders.placed"), metricsEndpoint::getBody);

    var prometheus = http.getForEntity(url("/actuator/prometheus"), String.class);
    assertEquals(200, prometheus.getStatusCode().value());
    assertTrue(prometheus.getBody().contains("scopepay_orders_placed_total"), () -> prometheus.getBody());
  }

  @Test
  void everyResponseCarriesACorrelationId() {
    var generated = http.getForEntity(url("/api/health"), String.class);
    assertTrue(generated.getHeaders().containsKey(CorrelationIdFilter.HEADER));
    HttpHeaders headers = new HttpHeaders();
    headers.set(CorrelationIdFilter.HEADER, "judge-trace-1");
    var echoed = http.exchange(url("/api/health"), HttpMethod.GET, new HttpEntity<>(headers), String.class);
    assertEquals("judge-trace-1", echoed.getHeaders().getFirst(CorrelationIdFilter.HEADER));
  }
}
