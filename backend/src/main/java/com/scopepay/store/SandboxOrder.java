package com.scopepay.store;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.OffsetDateTime;
import org.springframework.data.domain.Persistable;

@Entity
@Table(name = "sandbox_orders")
public class SandboxOrder implements Persistable<String> {
  @Transient
  private boolean isNew = true;

  @PostLoad
  void markLoaded() { this.isNew = false; }

  /** Call after the row is known to exist (e.g. right after saveAndFlush). */
  public void markPersisted() { this.isNew = false; }

  @Override
  public String getId() { return requestId; }

  @Override
  public boolean isNew() { return isNew; }
  @Id
  @Column(name = "request_id", length = 80)
  private String requestId;
  @Column(name = "owner_hash", length = 64, nullable = false)
  private String ownerHash;
  @Column(name = "order_id", length = 40, unique = true)
  private String orderId;
  @Column(name = "state", length = 40, nullable = false)
  private String state;
  @Column(name = "scope_id", length = 64)
  private String scopeId;
  @Column(name = "capture_id", length = 40)
  private String captureId;
  @Column(name = "refund_id", length = 40)
  private String refundId;
  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt = OffsetDateTime.now();
  @Column(name = "completed_at")
  private OffsetDateTime completedAt;

  public String getRequestId() { return requestId; }
  public void setRequestId(String v) { requestId = v; }
  public String getOwnerHash() { return ownerHash; }
  public void setOwnerHash(String v) { ownerHash = v; }
  public String getOrderId() { return orderId; }
  public void setOrderId(String v) { orderId = v; }
  public String getState() { return state; }
  public void setState(String v) { state = v; }
  public String getScopeId() { return scopeId; }
  public void setScopeId(String v) { scopeId = v; }
  public String getCaptureId() { return captureId; }
  public void setCaptureId(String v) { captureId = v; }
  public String getRefundId() { return refundId; }
  public void setRefundId(String v) { refundId = v; }
  public OffsetDateTime getCreatedAt() { return createdAt; }
  public void setCreatedAt(OffsetDateTime v) { createdAt = v; }
  public OffsetDateTime getCompletedAt() { return completedAt; }
  public void setCompletedAt(OffsetDateTime v) { completedAt = v; }
}
