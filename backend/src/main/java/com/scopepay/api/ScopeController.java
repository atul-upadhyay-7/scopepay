package com.scopepay.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.scopepay.scope.ScopeException;
import com.scopepay.scope.ScopeExtractor;
import com.scopepay.store.OutboxService;
import com.scopepay.store.ScopeDraft;
import com.scopepay.store.ScopeDraftRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ScopeController {
  private final ScopeExtractor extractor;
  private final ScopeDraftRepository drafts;
  private final OutboxService outbox;

  public ScopeController(ScopeExtractor extractor, ScopeDraftRepository drafts, OutboxService outbox) {
    this.extractor = extractor; this.drafts = drafts; this.outbox = outbox;
  }

  @PostMapping("/api/scopes/extract")
  public ObjectNode extract(@RequestBody(required = false) JsonNode body, HttpServletRequest request) {
    if (body == null || !body.isObject() || body.size() != 1 || !body.has("brief") || !body.get("brief").isTextual())
      throw new ScopeException("INVALID_BRIEF_REQUEST", 400);
    ObjectNode draft = extractor.extract(body.get("brief").asText());
    ScopeDraft doc = new ScopeDraft();
    doc.setScopeId(UUID.randomUUID().toString());
    doc.setOwnerHash(SessionGate.ownerHash(request));
    doc.setState("DRAFT_REQUIRES_REVIEW");
    doc.setSummary(draft.get("summary").asText());
    doc.setDeliverables(draft.get("deliverables"));
    doc.setQuestions(draft.get("questions"));
    doc.setConflicts(draft.get("conflicts"));
    drafts.save(doc);
    outbox.record("scope", doc.getScopeId(), "SCOPE_EXTRACTED", Map.of("state", "DRAFT_REQUIRES_REVIEW"));
    draft.put("scopeId", doc.getScopeId());
    return draft;
  }

  @PostMapping("/api/scopes/{scopeId}/review")
  public Map<String, String> review(@PathVariable String scopeId, @RequestBody(required = false) JsonNode body,
      HttpServletRequest request) {
    if (body == null || !body.isObject() || body.size() != 1
        || !body.has("acknowledge") || !body.get("acknowledge").asBoolean(false))
      throw new ScopeException("REVIEW_ACKNOWLEDGMENT_REQUIRED", 400);
    String owner = SessionGate.ownerHash(request);
    ScopeDraft draft = drafts.findById(scopeId)
        .filter(d -> owner.equals(d.getOwnerHash()))
        .orElseThrow(() -> new ScopeException("SCOPE_NOT_FOUND", 404));
    draft.setState("REVIEWED_FOR_SANDBOX");
    draft.setReviewedAt(OffsetDateTime.now());
    drafts.save(draft);
    outbox.record("scope", scopeId, "SCOPE_REVIEWED", Map.of("state", "REVIEWED_FOR_SANDBOX"));
    return Map.of("scopeId", scopeId, "state", "REVIEWED_FOR_SANDBOX");
  }
}
