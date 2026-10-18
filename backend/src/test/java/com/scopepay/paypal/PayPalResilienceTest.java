package com.scopepay.paypal;

import static org.junit.jupiter.api.Assertions.*;

import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandler;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import org.junit.jupiter.api.Test;

class PayPalResilienceTest {

  /** Scripted in-memory transport: N failures, then success. No network involved. */
  static class FakeHttpClient extends HttpClient {
    final AtomicInteger orderCalls = new AtomicInteger();
    final AtomicInteger failuresLeft;

    FakeHttpClient(int failures) { this.failuresLeft = new AtomicInteger(failures); }

    @Override
    @SuppressWarnings("unchecked")
    public <T> HttpResponse<T> send(HttpRequest request, BodyHandler<T> handler) {
      int status;
      String body;
      if (request.uri().getPath().contains("/v1/oauth2/token")) {
        status = 200; body = "{\"access_token\":\"t\",\"expires_in\":300}";
      } else {
        orderCalls.incrementAndGet();
        if (failuresLeft.getAndUpdate(n -> Math.max(0, n - 1)) > 0) {
          status = 500; body = "{\"name\":\"INTERNAL_SERVER_ERROR\"}";
        } else {
          status = 201; body = "{\"id\":\"ORDEROK123\",\"status\":\"CREATED\"}";
        }
      }
      String payload = body;
      int code = status;
      return new HttpResponse<>() {
        public int statusCode() { return code; }
        public HttpRequest request() { return request; }
        public Optional<HttpResponse<T>> previousResponse() { return Optional.empty(); }
        public HttpHeaders headers() { return HttpHeaders.of(java.util.Map.of(), (a, b) -> true); }
        public T body() { return (T) payload; }
        public Optional<SSLSession> sslSession() { return Optional.empty(); }
        public java.net.URI uri() { return request.uri(); }
        public Version version() { return Version.HTTP_1_1; }
      };
    }

    public Optional<CookieHandler> cookieHandler() { return Optional.empty(); }
    public Optional<Duration> connectTimeout() { return Optional.of(Duration.ofSeconds(1)); }
    public Redirect followRedirects() { return Redirect.NEVER; }
    public Optional<ProxySelector> proxy() { return Optional.empty(); }
    public SSLContext sslContext() { return null; }
    public SSLParameters sslParameters() { return null; }
    public Optional<Authenticator> authenticator() { return Optional.empty(); }
    public Version version() { return Version.HTTP_1_1; }
    public Optional<Executor> executor() { return Optional.empty(); }
    public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest r, BodyHandler<T> h) { throw new UnsupportedOperationException(); }
    public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest r, BodyHandler<T> h, HttpResponse.PushPromiseHandler<T> p) { throw new UnsupportedOperationException(); }
  }

  private static PayPalClient resilientClient(FakeHttpClient transport) {
    PayPalClient client = new PayPalClient("id", "secret", "http://fake.local", transport);
    client.enableDefaultResilience();
    return client;
  }

  @Test
  void transientFailuresAreRetriedUntilSuccess() throws Exception {
    FakeHttpClient transport = new FakeHttpClient(2);
    PayPalClient client = resilientClient(transport);
    var order = client.createOrder("req-retry-0000000001", "sandbox-demo", "10.00");
    assertEquals("ORDEROK123", order.path("id").asText());
    assertEquals(3, transport.orderCalls.get()); // two 500s + one success
  }

  @Test
  void persistentOutageOpensTheCircuit() {
    FakeHttpClient transport = new FakeHttpClient(1000);
    PayPalClient client = resilientClient(transport);
    List<String> issues = new ArrayList<>();
    for (int i = 0; i < 6; i++) {
      try {
        client.createOrder("req-breaker-0000000" + i, "sandbox-demo", "10.00");
      } catch (PayPalException e) {
        issues.add(e.getIssue());
      } catch (Exception e) {
        fail("unexpected: " + e);
      }
    }
    assertTrue(issues.contains("PAYPAL_CIRCUIT_OPEN"), "expected circuit to open, got " + issues);
    assertTrue(transport.orderCalls.get() < 18, "breaker must short-circuit, calls=" + transport.orderCalls.get());
  }

  @Test
  void businessErrorsAreNotRetried() {
    FakeHttpClient transport = new FakeHttpClient(0) {
      @Override
      public <T> HttpResponse<T> send(HttpRequest request, BodyHandler<T> handler) {
        if (request.uri().getPath().contains("/v1/oauth2/token")) return super.send(request, handler);
        orderCalls.incrementAndGet();
        return fake(422, "{\"name\":\"UNPROCESSABLE_ENTITY\"}", request);
      }
      @SuppressWarnings("unchecked")
      private <T> HttpResponse<T> fake(int code, String payload, HttpRequest request) {
        return new HttpResponse<>() {
          public int statusCode() { return code; }
          public HttpRequest request() { return request; }
          public Optional<HttpResponse<T>> previousResponse() { return Optional.empty(); }
          public HttpHeaders headers() { return HttpHeaders.of(java.util.Map.of(), (a, b) -> true); }
          public T body() { return (T) payload; }
          public Optional<SSLSession> sslSession() { return Optional.empty(); }
          public java.net.URI uri() { return request.uri(); }
          public Version version() { return Version.HTTP_1_1; }
        };
      }
    };
    PayPalClient client = resilientClient(transport);
    PayPalException e = assertThrows(PayPalException.class,
        () -> client.createOrder("req-business-00000001", "sandbox-demo", "10.00"));
    assertEquals(422, e.getStatus());
    assertEquals(1, transport.orderCalls.get()); // no retry on 4xx
  }
}
