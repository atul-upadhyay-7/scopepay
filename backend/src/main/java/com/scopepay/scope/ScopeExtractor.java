package com.scopepay.scope;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class ScopeExtractor {
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private final Optional<ScopeProvider> provider;
  private final long timeoutMs;

  public ScopeExtractor(Optional<ScopeProvider> provider) { this(provider, 15000); }
  public ScopeExtractor(Optional<ScopeProvider> provider, long timeoutMs) {
    this.provider = provider; this.timeoutMs = timeoutMs;
  }

  public ObjectNode extract(String brief) {
    if (brief == null || brief.trim().length() < 20 || brief.length() > 6000) throw new ScopeException("INVALID_BRIEF", 400);
    if (provider.isEmpty()) throw new ScopeException("AI_NOT_CONFIGURED", 503);
    try {
      GenerateResult result = CompletableFuture
          .supplyAsync(() -> {
            try { return provider.get().generate(ScopeValidator.SCOPE_INSTRUCTIONS, brief, ScopeValidator.SCOPE_SCHEMA); }
            catch (ScopeException e) { throw e; }
            catch (Exception e) { throw new ScopeException("AI_UNAVAILABLE", 502); }
          })
          .orTimeout(timeoutMs, TimeUnit.MILLISECONDS).join();
      if (result.refusal()) throw new ScopeException("AI_REFUSED");
      JsonNode data = result.data();
      ObjectNode valid = ScopeValidator.validateScope(data, brief);
      ObjectNode out = MAPPER.createObjectNode();
      out.put("state", "DRAFT_REQUIRES_REVIEW");
      out.setAll(valid);
      return out;
    } catch (java.util.concurrent.CompletionException e) {
      if (e.getCause() instanceof ScopeException se) throw se;
      if (e.getCause() instanceof java.util.concurrent.TimeoutException) throw new ScopeException("AI_TIMEOUT", 504);
      throw new ScopeException("AI_UNAVAILABLE", 502);
    }
  }
}
