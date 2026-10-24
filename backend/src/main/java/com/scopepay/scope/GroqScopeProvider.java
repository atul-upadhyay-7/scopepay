package com.scopepay.scope;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

/** One fixed inference endpoint/model. No tools, retries, paid fallback or client keys. */
public class GroqScopeProvider implements ScopeProvider {
  private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(GroqScopeProvider.class);
  private static final String ENDPOINT = "https://api.groq.com/openai/v1/chat/completions";
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private final String apiKey;
  private final boolean syntheticOnly;
  private final int maxRequests;
  private final AtomicInteger requests = new AtomicInteger();
  private final String endpoint;
  private final HttpClient client;

  public GroqScopeProvider(String apiKey, boolean syntheticOnly) { this(apiKey, syntheticOnly, 50); }
  public GroqScopeProvider(String apiKey, boolean syntheticOnly, int maxRequests) {
    this(apiKey, syntheticOnly, maxRequests, ENDPOINT, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
  }

  /** Bounded allowlist for externally-influenced identifiers in logs: letters, digits, '.', '_'
      and '-' pass, anything else becomes '?', capped at 40 chars. Provider metadata (error codes,
      finish reasons) is untrusted input and can carry control characters or oversized content. */
  private static String safe(String value) {
    if (value == null) return "";
    StringBuilder out = new StringBuilder(Math.min(value.length(), 40));
    for (int i = 0; i < value.length() && out.length() < 40; i++) {
      char c = value.charAt(i);
      boolean ok = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
          || c == '.' || c == '_' || c == '-';
      out.append(ok ? c : '?');
    }
    return out.toString();
  }

  /** Test constructor: endpoint and transport injection. */
  public GroqScopeProvider(String apiKey, boolean syntheticOnly, int maxRequests, String endpoint, HttpClient client) {
    this.apiKey = apiKey; this.syntheticOnly = syntheticOnly; this.maxRequests = maxRequests;
    this.endpoint = endpoint; this.client = client;
  }

  @Override
  public GenerateResult generate(String instructions, String brief, String schemaJson) throws Exception {
    if (!syntheticOnly || requests.get() >= maxRequests) {
      LOG.warn("AI request rejected by local policy: syntheticOnly={} requests={} maxRequests={}",
          syntheticOnly, requests.get(), maxRequests);
      throw new IllegalStateException("AI request policy limit");
    }
    requests.incrementAndGet();
    ObjectNode body = MAPPER.createObjectNode();
    body.put("model", "openai/gpt-oss-20b");
    body.put("temperature", 0);
    body.put("max_completion_tokens", 2500);
    ArrayNode messages = body.putArray("messages");
    messages.addObject().put("role", "system").put("content",
        instructions + "\nRequired JSON schema (all keys must be present, including conflicts: [] when none): " + schemaJson);
    messages.addObject().put("role", "user").put("content", brief);
    // json_object mode: Groq's strict json_schema requires additionalProperties:false on every
    // object, which forbids the map-shaped deliverables object. The schema still goes in the
    // system prompt; the fail-closed local validators enforce the contract on every response.
    ObjectNode responseFormat = body.putObject("response_format");
    responseFormat.put("type", "json_object");

    HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
        .timeout(Duration.ofSeconds(20))
        .header("Authorization", "Bearer " + apiKey)
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body))).build();
    HttpResponse<String> response;
    try {
      response = client.send(request, HttpResponse.BodyHandlers.ofString());
    } catch (java.io.IOException | InterruptedException e) {
      LOG.warn("AI provider transport failure: {}", e.getClass().getSimpleName());
      throw e;
    }
    if (response.statusCode() != 200) {
      // Some provider refusals arrive as a schema-validation 400. Accept only an
      // exact refusal object for classification, never a failed draft as success.
      if (response.statusCode() == 400) {
        try {
          JsonNode error = MAPPER.readTree(response.body());
          JsonNode failed = error.path("error").path("failed_generation");
          if ("json_validate_failed".equals(error.path("error").path("code").asText()) && failed.isTextual()) {
            JsonNode parsed = MAPPER.readTree(failed.asText());
            if (parsed.isObject() && parsed.size() == 1 && parsed.has("refusal") && parsed.get("refusal").isTextual())
              return GenerateResult.ofRefusal();
          }
        } catch (Exception ignored) {}
      }
      String errorCode = "";
      String errorType = "";
      try {
        JsonNode err = MAPPER.readTree(response.body()).path("error");
        errorCode = err.path("code").asText("");
        errorType = err.path("type").asText("");
      } catch (Exception ignored) {}
      LOG.warn("AI provider HTTP {} errorCode={} errorType={}", response.statusCode(),
          safe(errorCode), safe(errorType));
      throw new IllegalStateException("AI provider unavailable");
    }
    JsonNode data;
    try {
      data = MAPPER.readTree(response.body());
    } catch (Exception e) {
      LOG.warn("AI provider response body unparseable: {}", e.getClass().getSimpleName());
      throw e;
    }
    JsonNode choice = data.path("choices").isArray() && !data.path("choices").isEmpty() ? data.path("choices").get(0) : null;
    if (choice == null) {
      LOG.warn("AI output incomplete: no choices in response");
      throw new IllegalStateException("Incomplete AI output");
    }
    if (choice.path("message").path("refusal").isTextual()) return GenerateResult.ofRefusal();
    if (!"stop".equals(choice.path("finish_reason").asText()) || !choice.path("message").path("content").isTextual()) {
      LOG.warn("AI output incomplete: finishReason={} contentPresent={} completionTokens={} reasoningTokens={}",
          safe(choice.path("finish_reason").asText("")), choice.path("message").path("content").isTextual(),
          data.path("usage").path("completion_tokens").asInt(-1),
          data.path("usage").path("completion_tokens_details").path("reasoning_tokens").asInt(-1));
      throw new IllegalStateException("Incomplete AI output");
    }
    try {
      return GenerateResult.ofData(MAPPER.readTree(choice.path("message").path("content").asText()));
    } catch (Exception e) {
      LOG.warn("AI content not parseable as JSON: {}", e.getClass().getSimpleName());
      throw e;
    }
  }
}
