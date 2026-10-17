package com.scopepay.store;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "scope_drafts")
public class ScopeDraft {
  @Id
  @Column(name = "scope_id", length = 64)
  private String scopeId;
  @Column(name = "owner_hash", length = 64, nullable = false)
  private String ownerHash;
  @Column(name = "state", length = 40, nullable = false)
  private String state;
  @Column(name = "summary", columnDefinition = "text", nullable = false)
  private String summary;
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "deliverables", nullable = false)
  private JsonNode deliverables;
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "questions", nullable = false)
  private JsonNode questions;
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "conflicts", nullable = false)
  private JsonNode conflicts;
  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt = OffsetDateTime.now();
  @Column(name = "reviewed_at")
  private OffsetDateTime reviewedAt;

  public String getScopeId() { return scopeId; }
  public void setScopeId(String v) { scopeId = v; }
  public String getOwnerHash() { return ownerHash; }
  public void setOwnerHash(String v) { ownerHash = v; }
  public String getState() { return state; }
  public void setState(String v) { state = v; }
  public String getSummary() { return summary; }
  public void setSummary(String v) { summary = v; }
  public JsonNode getDeliverables() { return deliverables; }
  public void setDeliverables(JsonNode v) { deliverables = v; }
  public JsonNode getQuestions() { return questions; }
  public void setQuestions(JsonNode v) { questions = v; }
  public JsonNode getConflicts() { return conflicts; }
  public void setConflicts(JsonNode v) { conflicts = v; }
  public OffsetDateTime getCreatedAt() { return createdAt; }
  public void setCreatedAt(OffsetDateTime v) { createdAt = v; }
  public OffsetDateTime getReviewedAt() { return reviewedAt; }
  public void setReviewedAt(OffsetDateTime v) { reviewedAt = v; }
}
