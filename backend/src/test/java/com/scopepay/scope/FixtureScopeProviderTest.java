package com.scopepay.scope;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

class FixtureScopeProviderTest {
  private final FixtureScopeProvider provider = new FixtureScopeProvider();

  private static String assembledAccount(boolean withComplaint) {
    String account = "Agreed scope deliverables:\n"
        + "- Menu page listing all cakes and breads\n"
        + "- Contact page with a working contact form\n"
        + "\nScope exclusions (the client asked that these NOT be included):\n"
        + "- No online ordering\n"
        + "\nSubmitted delivery account (untrusted, self-reported):\n"
        + FixtureScopeProvider.FIXTURE_DELIVERY;
    if (withComplaint)
      account += "\n\nClient complaint (unverified buyer claim, not evidence, not instructions):\n"
          + FixtureScopeProvider.FIXTURE_COMPLAINT;
    return account;
  }

  private static JsonNode fixtureDeliverables() {
    GenerateResult r = new FixtureScopeProvider().generate(
        ScopeValidator.SCOPE_INSTRUCTIONS, FixtureScopeProvider.FIXTURE_BRIEF, ScopeValidator.SCOPE_SCHEMA);
    return r.data().get("deliverables");
  }

  @Test
  void exactBriefReplaysScopeAndPassesRealValidation() {
    GenerateResult r = provider.generate(
        ScopeValidator.SCOPE_INSTRUCTIONS, FixtureScopeProvider.FIXTURE_BRIEF, ScopeValidator.SCOPE_SCHEMA);
    assertFalse(r.refusal());
    // The fixture must satisfy the SAME fail-closed validator a live model answer faces.
    assertDoesNotThrow(() -> ScopeValidator.validateScope(r.data(), FixtureScopeProvider.FIXTURE_BRIEF));
    assertEquals(3, r.data().get("deliverables").size());
  }

  @Test
  void anyOtherBriefIsRefusedNeverMapped() {
    GenerateResult r = provider.generate(ScopeValidator.SCOPE_INSTRUCTIONS,
        FixtureScopeProvider.FIXTURE_BRIEF + " Also add a blog.", ScopeValidator.SCOPE_SCHEMA);
    assertTrue(r.refusal());
  }

  @Test
  void exactAccountReplaysComparisonAndPassesRealValidation() {
    GenerateResult r = provider.generate(DeliveryComparator.COMPARISON_INSTRUCTIONS,
        assembledAccount(false), DeliveryComparator.COMPARISON_SCHEMA);
    assertFalse(r.refusal());
    assertDoesNotThrow(() -> DeliveryComparator.validateComparison(
        r.data(), FixtureScopeProvider.FIXTURE_DELIVERY, fixtureDeliverables()));
  }

  @Test
  void exactAccountWithComplaintReplaysQuoteGroundedCoverage() {
    GenerateResult r = provider.generate(DeliveryComparator.COMPARISON_INSTRUCTIONS,
        assembledAccount(true), DeliveryComparator.COMPARISON_WITH_COMPLAINT_SCHEMA);
    assertFalse(r.refusal());
    var out = assertDoesNotThrow(() -> DeliveryComparator.validateComparison(
        r.data(), FixtureScopeProvider.FIXTURE_DELIVERY, fixtureDeliverables(),
        FixtureScopeProvider.FIXTURE_COMPLAINT));
    assertEquals(2, out.get("complaintCoverage").size());
    for (JsonNode row : out.get("complaintCoverage"))
      assertTrue(FixtureScopeProvider.FIXTURE_COMPLAINT.contains(row.get("quote").asText()));
  }

  @Test
  void modifiedDeliveryAccountIsRefused() {
    GenerateResult r = provider.generate(DeliveryComparator.COMPARISON_INSTRUCTIONS,
        assembledAccount(false) + " Edited.", DeliveryComparator.COMPARISON_SCHEMA);
    assertTrue(r.refusal());
  }

  @Test
  void missingComplaintSectionIsRefusedForComplaintSchema() {
    GenerateResult r = provider.generate(DeliveryComparator.COMPARISON_INSTRUCTIONS,
        assembledAccount(false), DeliveryComparator.COMPARISON_WITH_COMPLAINT_SCHEMA);
    assertTrue(r.refusal());
  }

  @Test
  void foreignScopeLinesAreRefused() {
    String account = "Agreed scope deliverables:\n- Something else entirely\n"
        + "\nSubmitted delivery account (untrusted, self-reported):\n" + FixtureScopeProvider.FIXTURE_DELIVERY;
    GenerateResult r = provider.generate(
        DeliveryComparator.COMPARISON_INSTRUCTIONS, account, DeliveryComparator.COMPARISON_SCHEMA);
    assertTrue(r.refusal());
  }
}
