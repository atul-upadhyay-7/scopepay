package com.scopepay.scope;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class ScopeExtractorTest {
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private final ScopeProvider good = (instructions, brief, schema) ->
      GenerateResult.ofData(ScopeValidatorTest.sample());

  @Test
  void draftExtractionUsesExplicitStateAndNoPaymentAuthority() {
    JsonNode r = new ScopeExtractor(Optional.of(good)).extract(ScopeValidatorTest.BRIEF);
    assertEquals("DRAFT_REQUIRES_REVIEW", r.get("state").asText());
    assertFalse(r.has("price"));
  }

  @Test
  void missingProviderNeverSilentlyReturnsFixtureAi() {
    ScopeException e = assertThrows(ScopeException.class,
        () -> new ScopeExtractor(Optional.empty()).extract(ScopeValidatorTest.BRIEF));
    assertEquals("AI_NOT_CONFIGURED", e.getCode());
    assertEquals(503, e.getStatus());
  }

  @Test
  void briefLengthAndTypeConstraints() {
    ScopeExtractor ex = new ScopeExtractor(Optional.of(good));
    for (String b : new String[]{"", "short", "x".repeat(6001)}) {
      ScopeException e = assertThrows(ScopeException.class, () -> ex.extract(b));
      assertEquals("INVALID_BRIEF", e.getCode());
      assertEquals(400, e.getStatus());
    }
    assertThrows(ScopeException.class, () -> ex.extract(null));
  }

  @Test
  void refusalDistinctFromValidScope() {
    ScopeProvider refusing = (i, b, s) -> GenerateResult.ofRefusal();
    ScopeException e = assertThrows(ScopeException.class,
        () -> new ScopeExtractor(Optional.of(refusing)).extract(ScopeValidatorTest.BRIEF));
    assertEquals("AI_REFUSED", e.getCode());
  }

  @Test
  void providerErrorCannotDiscloseRawErrorOrSecret() {
    ScopeProvider broken = (i, b, s) -> { throw new Exception("secret-key"); };
    ScopeException e = assertThrows(ScopeException.class,
        () -> new ScopeExtractor(Optional.of(broken)).extract(ScopeValidatorTest.BRIEF));
    assertEquals("AI_UNAVAILABLE", e.getCode());
    assertFalse(e.toString().contains("secret-key"));
  }

  @Test
  void providerTimeoutFailsClosed() {
    ScopeProvider hanging = (i, b, s) -> { Thread.sleep(60_000); return GenerateResult.ofRefusal(); };
    ScopeException e = assertThrows(ScopeException.class,
        () -> new ScopeExtractor(Optional.of(hanging), 50).extract(ScopeValidatorTest.BRIEF));
    assertEquals("AI_TIMEOUT", e.getCode());
    assertEquals(504, e.getStatus());
  }

  @Test
  void instructionsAndInputAreSeparate() {
    AtomicReference<String> capturedBrief = new AtomicReference<>();
    AtomicReference<String> capturedInstructions = new AtomicReference<>();
    AtomicReference<String> capturedSchema = new AtomicReference<>();
    ScopeProvider spy = (i, b, s) -> {
      capturedBrief.set(b); capturedInstructions.set(i); capturedSchema.set(s);
      return GenerateResult.ofData(ScopeValidatorTest.sample());
    };
    new ScopeExtractor(Optional.of(spy)).extract(ScopeValidatorTest.BRIEF);
    assertEquals(ScopeValidatorTest.BRIEF, capturedBrief.get());
    assertTrue(capturedInstructions.get().contains("untrusted data"));
    assertTrue(capturedSchema.get().contains("\"additionalProperties\":false"));
  }
}
