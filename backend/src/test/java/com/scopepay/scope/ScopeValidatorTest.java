package com.scopepay.scope;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

class ScopeValidatorTest {
  private static final ObjectMapper MAPPER = new ObjectMapper();
  static final String BRIEF = "Build a website with a contact form. No login. Require login for every visitor.";

  static ObjectNode sample() {
    ObjectNode d = MAPPER.createObjectNode();
    d.put("summary", "Website with contact form; login needs clarification.");
    var deliverables = d.putArray("deliverables");
    deliverables.addObject().put("description", "Contact form").put("evidence", "a contact form");
    d.putArray("questions").add("Who should receive contact messages?");
    var conflicts = d.putArray("conflicts");
    var c = conflicts.addObject().put("description", "Login requirement conflicts.");
    c.putArray("evidence").add("No login.").add("Require login for every visitor.");
    return d;
  }

  @Test
  void schemaRejectsUnknownAmountApprovalFields() {
    for (String k : new String[]{"price", "amount", "approved", "payment", "__proto__"}) {
      ObjectNode d = sample();
      d.put(k, 1);
      ScopeException e = assertThrows(ScopeException.class, () -> ScopeValidator.validateScope(d, BRIEF));
      assertEquals("INVALID_MODEL_OUTPUT", e.getCode(), k);
    }
  }

  @Test
  void deliverableExactEvidenceMustOccurInBrief() {
    ObjectNode d = sample();
    ((ObjectNode) d.get("deliverables").get(0)).put("evidence", "AI invented a mobile app");
    assertEquals("UNGROUNDED_DELIVERABLE",
        assertThrows(ScopeException.class, () -> ScopeValidator.validateScope(d, BRIEF)).getCode());
  }

  @Test
  void conflictMustHaveAtLeastTwoGroundedSpans() {
    ObjectNode d = sample();
    var c = (ObjectNode) d.get("conflicts").get(0);
    c.putArray("evidence").add("No login.");
    assertEquals("UNGROUNDED_CONFLICT",
        assertThrows(ScopeException.class, () -> ScopeValidator.validateScope(d, BRIEF)).getCode());
  }

  @Test
  void emptyExtractionWithoutQuestionsRejected() {
    ObjectNode d = sample();
    d.putArray("deliverables");
    d.putArray("questions");
    assertEquals("INSUFFICIENT_SCOPE",
        assertThrows(ScopeException.class, () -> ScopeValidator.validateScope(d, BRIEF)).getCode());
  }

  @Test
  void questionAndOutputLimitsEnforced() {
    ObjectNode d = sample();
    var qs = d.putArray("questions");
    for (int i = 0; i < 9; i++) qs.add("question");
    assertEquals("INVALID_MODEL_OUTPUT",
        assertThrows(ScopeException.class, () -> ScopeValidator.validateScope(d, BRIEF)).getCode());
  }

  @Test
  void validSamplePasses() {
    assertDoesNotThrow(() -> ScopeValidator.validateScope(sample(), BRIEF));
  }
}
