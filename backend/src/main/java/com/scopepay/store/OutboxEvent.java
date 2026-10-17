package com.scopepay.store;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Transactional outbox row: written in the same transaction as the state change it describes. */
@Entity
@Table(name = "outbox_events")
public class OutboxEvent {
  @Id
  @Column(name = "event_id", length = 64)
  private String eventId;
  @Column(name = "aggregate", length = 40, nullable = false)
  private String aggregate;
  @Column(name = "aggregate_id", length = 80, nullable = false)
  private String aggregateId;
  @Column(name = "type", length = 60, nullable = false)
  private String type;
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "payload", nullable = false)
  private JsonNode payload;
  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt = OffsetDateTime.now();
  @Column(name = "published_at")
  private OffsetDateTime publishedAt;

  public String getEventId() { return eventId; }
  public void setEventId(String v) { eventId = v; }
  public String getAggregate() { return aggregate; }
  public void setAggregate(String v) { aggregate = v; }
  public String getAggregateId() { return aggregateId; }
  public void setAggregateId(String v) { aggregateId = v; }
  public String getType() { return type; }
  public void setType(String v) { type = v; }
  public JsonNode getPayload() { return payload; }
  public void setPayload(JsonNode v) { payload = v; }
  public OffsetDateTime getCreatedAt() { return createdAt; }
  public void setCreatedAt(OffsetDateTime v) { createdAt = v; }
  public OffsetDateTime getPublishedAt() { return publishedAt; }
  public void setPublishedAt(OffsetDateTime v) { publishedAt = v; }
}
