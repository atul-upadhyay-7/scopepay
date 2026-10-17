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

  /** Test constructor: endpoint and transport injection. */
  public GroqScopeProvider(String apiKey, boolean syntheticOnly, int maxRequests, String endpoint, HttpClient client) {
    this.apiKey = apiKey; this.syntheticOnly = syntheticOnly; this.maxRequests = maxRequests;
    this.endpoint = endpoint; this.client = client;
  }

  @Override
  public GenerateResult generate(String instructions, String brief, String schemaJson) throws Exception {
    if (!syntheticOnly || requests.get() >= maxRequests) throw new IllegalStateException("AI request policy limit");
    requests.incrementAndGet();
    ObjectNode body = MAPPER.createObjectNode();
    body.put("model", "openai/gpt-oss-20b");
    body.put("temperature", 0);
    body.put("max_completion_tokens", 2500);
    ArrayNode messages = body.putArray("messages");
    messages.addObject().put("role", "system").put("content",
        instructions + "\nRequired JSON schema (all keys must be present, including conflicts: [] when none): " + schemaJson);
    messages.addObject().put("role", "user").put("content", brief);
    ObjectNode responseFormat = body.putObject("response_format");
    responseFormat.put("type", "json_schema");
    ObjectNode jsonSchema = responseFormat.putObject("json_schema");
    jsonSchema.put("name", "scope_draft");
    jsonSchema.put("strict", true);
    jsonSchema.set("schema", MAPPER.readTree(schemaJson));

    HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
        .timeout(Duration.ofSeconds(20))
        .header("Authorization", "Bearer " + apiKey)
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body))).build();
    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
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
      throw new IllegalStateException("AI provider unavailable");
    }
    JsonNode data = MAPPER.readTree(response.body());
    JsonNode choice = data.path("choices").isArray() && !data.path("choices").isEmpty() ? data.path("choices").get(0) : null;
    if (choice == null) throw new IllegalStateException("Incomplete AI output");
    if (choice.path("message").path("refusal").isTextual()) return GenerateResult.ofRefusal();
    if (!"stop".equals(choice.path("finish_reason").asText()) || !choice.path("message").path("content").isTextual())
      throw new IllegalStateException("Incomplete AI output");
    return GenerateResult.ofData(MAPPER.readTree(choice.path("message").path("content").asText()));
  }
}
