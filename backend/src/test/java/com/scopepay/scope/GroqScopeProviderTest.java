package com.scopepay.scope;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class GroqScopeProviderTest {
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private HttpServer server;
  private final List<String> requestBodies = new ArrayList<>();
  private final AtomicInteger calls = new AtomicInteger();
  private volatile String responseBody = "";
  private volatile int responseStatus = 200;

  private GroqScopeProvider startProvider(int maxRequests) throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/", exchange -> {
      calls.incrementAndGet();
      requestBodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
      byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
      exchange.sendResponseHeaders(responseStatus, bytes.length);
      exchange.getResponseBody().write(bytes);
      exchange.close();
    });
    server.start();
    String endpoint = "http://127.0.0.1:" + server.getAddress().getPort() + "/openai/v1/chat/completions";
    return new GroqScopeProvider("test", true, maxRequests, endpoint, HttpClient.newHttpClient());
  }

  @AfterEach
  void stop() { if (server != null) server.stop(0); }

  private static String okResponse() {
    return "{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"content\":\"{\\\"summary\\\":\\\"draft\\\"}\"}}]}";
  }

  @Test
  void usesFixedModelJsonObjectSeparateBriefAndNoTools() throws Exception {
    responseBody = okResponse();
    GroqScopeProvider p = startProvider(50);
    GenerateResult result = p.generate("Policy", "Synthetic brief", "{\"type\":\"object\"}");
    assertEquals("draft", result.data().get("summary").asText());
    JsonNode body = MAPPER.readTree(requestBodies.get(0));
    assertEquals("openai/gpt-oss-20b", body.get("model").asText());
    // json_object mode: strict json_schema forbids the map-shaped deliverables object;
    // the schema rides in the system prompt and local validators enforce it fail-closed.
    assertEquals("json_object", body.get("response_format").get("type").asText());
    assertTrue(body.get("messages").get(0).get("content").asText().contains("{\"type\":\"object\"}"));
    assertEquals("Synthetic brief", body.get("messages").get(1).get("content").asText());
    assertFalse(body.has("tools"));
    assertTrue(body.get("messages").get(0).get("content").asText().contains("Policy"));
  }

  @Test
  void errorsNeverExposeUpstreamSecretsAndNeverRetry() throws Exception {
    responseStatus = 429;
    responseBody = "secret upstream";
    GroqScopeProvider p = startProvider(50);
    Exception e = assertThrows(Exception.class, () -> p.generate("i", "b", "{}"));
    assertTrue(e.getMessage().contains("AI provider unavailable"));
    assertFalse(e.getMessage().contains("secret"));
    assertEquals(1, calls.get());
  }

  @Test
  void rejectsTruncatedOutput() throws Exception {
    responseBody = "{\"choices\":[{\"finish_reason\":\"length\",\"message\":{\"content\":\"{}\"}}]}";
    GroqScopeProvider p = startProvider(50);
    assertTrue(assertThrows(Exception.class, () -> p.generate("i", "b", "{}")).getMessage().contains("Incomplete"));
  }

  @Test
  void refusalPreserved() throws Exception {
    responseBody = "{\"choices\":[{\"message\":{\"refusal\":\"No\"}}]}";
    GroqScopeProvider p = startProvider(50);
    assertTrue(p.generate("i", "b", "{}").refusal());
  }

  @Test
  void requestCapStopsAdditionalApiCalls() throws Exception {
    responseBody = okResponse();
    GroqScopeProvider p = startProvider(1);
    p.generate("i", "b", "{}");
    assertThrows(Exception.class, () -> p.generate("i", "b", "{}"));
    assertEquals(1, calls.get());
  }

  @Test
  void syntheticPolicyFlagFailsClosed() {
    GroqScopeProvider p = new GroqScopeProvider("test", false);
    assertThrows(Exception.class, () -> p.generate("i", "b", "{}"));
  }

  @Test
  void schemaFailureRefusalSafelyClassifiedFailedDraftsNeverAccepted() throws Exception {
    for (var pair : new String[][]{
        {"{\"refusal\":\"No\"}", "true"}, {"{\"summary\":\"bad draft\"}", "false"}}) {
      responseStatus = 400;
      responseBody = "{\"error\":{\"code\":\"json_validate_failed\",\"failed_generation\":"
          + MAPPER.writeValueAsString(pair[0]) + "}}";
      GroqScopeProvider p = startProvider(50);
      if (pair[1].equals("true")) assertTrue(p.generate("i", "b", "{}").refusal());
      else assertTrue(assertThrows(Exception.class, () -> p.generate("i", "b", "{}"))
          .getMessage().contains("unavailable"));
      stop();
    }
  }
}
