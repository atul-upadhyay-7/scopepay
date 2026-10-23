package com.scopepay.scope;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DeliveryComparatorTest {
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final String DELIVERY = "I delivered the contact form yesterday but the gallery is not started yet.";

  private static com.fasterxml.jackson.databind.JsonNode scopeDeliverables() {
    var d = MAPPER.createArrayNode();
    d.addObject().put("description", "Contact form").put("evidence", "a contact form");
    d.addObject().put("description", "Gallery page").put("evidence", "a gallery page");
    return d;
  }

  private static ObjectNode validComparison() {
    var root = MAPPER.createObjectNode();
    var items = root.putArray("comparisons");
    items.addObject().put("deliverable", "Contact form").put("verdict", "CLAIMED_ADDRESSED")
        .put("quote", "delivered the contact form").put("note", "claims done");
    items.addObject().put("deliverable", "Gallery page").put("verdict", "NOT_EVIDENCED")
        .put("quote", "").put("note", "gallery only mentioned as not started, no claim of delivery");
    root.put("overallNote", "One claimed, one not evidenced.");
    return root;
  }

  @Test
  void validComparisonPasses() {
    assertDoesNotThrow(() -> DeliveryComparator.validateComparison(validComparison(), DELIVERY, scopeDeliverables()));
  }

  @Test
  void quoteOutsideDeliveryTextFailsClosed() {
    ObjectNode bad = validComparison();
    ((ObjectNode) bad.get("comparisons").get(0)).put("quote", "this phrase is not in the account");
    ScopeException e = assertThrows(ScopeException.class,
        () -> DeliveryComparator.validateComparison(bad, DELIVERY, scopeDeliverables()));
    assertEquals("UNGROUNDED_COMPARISON", e.getCode());
  }

  @Test
  void inventedDeliverableFailsClosed() {
    ObjectNode bad = validComparison();
    ((ObjectNode) bad.get("comparisons").get(0)).put("deliverable", "Invented feature");
    ScopeException e = assertThrows(ScopeException.class,
        () -> DeliveryComparator.validateComparison(bad, DELIVERY, scopeDeliverables()));
    assertEquals("UNGROUNDED_COMPARISON", e.getCode());
  }

  @Test
  void notEvidencedWithQuoteFailsClosed() {
    ObjectNode bad = validComparison();
    ((ObjectNode) bad.get("comparisons").get(1)).put("quote", "gallery is not started yet");
    ScopeException e = assertThrows(ScopeException.class,
        () -> DeliveryComparator.validateComparison(bad, DELIVERY, scopeDeliverables()));
    assertEquals("UNGROUNDED_COMPARISON", e.getCode());
  }

  @Test
  void missingDeliverableCoverageFailsClosed() {
    ObjectNode bad = validComparison();
    ((com.fasterxml.jackson.databind.node.ArrayNode) bad.get("comparisons")).remove(1);
    ScopeException e = assertThrows(ScopeException.class,
        () -> DeliveryComparator.validateComparison(bad, DELIVERY, scopeDeliverables()));
    assertEquals("UNGROUNDED_COMPARISON", e.getCode());
  }

  @Test
  void providerRefusalBecomesAiRefused() {
    DeliveryComparator c = new DeliveryComparator(Optional.of((i, b, s) -> GenerateResult.ofRefusal()));
    ScopeException e = assertThrows(ScopeException.class, () -> c.compare(scopeDeliverables(), DELIVERY));
    assertEquals("AI_REFUSED", e.getCode());
  }

  @Test
  void absentProviderIsHonest() {
    DeliveryComparator c = new DeliveryComparator(Optional.empty());
    ScopeException e = assertThrows(ScopeException.class, () -> c.compare(scopeDeliverables(), DELIVERY));
    assertEquals("AI_NOT_CONFIGURED", e.getCode());
    assertEquals(503, e.getStatus());
  }

  @Test
  void tooShortAccountRejected() {
    DeliveryComparator c = new DeliveryComparator(Optional.of((i, b, s) -> GenerateResult.ofData(validComparison())));
    ScopeException e = assertThrows(ScopeException.class, () -> c.compare(scopeDeliverables(), "short"));
    assertEquals("INVALID_DELIVERY_ACCOUNT", e.getCode());
  }
}
