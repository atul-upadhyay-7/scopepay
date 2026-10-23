package com.scopepay.store;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.domain.Persistable;

@Entity
@Table(name = "refund_requests")
public class RefundRequest implements Persistable<String> {
  public static final String DRAFT = "DRAFT";
  public static final String COMPLETED = "COMPLETED";
  public static final String UNCLEAR = "UNCLEAR";
  public static final String FAILED = "FAILED";

  @Transient
  private boolean isNew = true;

  @PostLoad
  void markLoaded() { this.isNew = false; }

  public void markPersisted() { this.isNew = false; }

  @Override
  public String getId() { return refundRequestId; }

  @Override
  public boolean isNew() { return isNew; }

  @Id
  @Column(name = "refund_request_id", length = 64)
  private String refundRequestId;
  @Column(name = "owner_hash", length = 64, nullable = false)
  private String ownerHash;
  @Column(name = "order_request_id", length = 80, nullable = false)
  private String orderRequestId;
  @Column(name = "capture_id", length = 40, nullable = false)
  private String captureId;
  @Column(name = "amount", nullable = false, precision = 12, scale = 2)
  private BigDecimal amount;
  @Column(name = "currency", length = 3, nullable = false)
  private String currency;
  @Column(name = "reason", nullable = false)
  private String reason;
  @Column(name = "delivery_text", nullable = false)
  private String deliveryText;
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "comparison", nullable = false, columnDefinition = "JSONB")
  private JsonNode comparison;
  @Column(name = "state", length = 40, nullable = false)
  private String state;
  @Column(name = "paypal_refund_id", length = 40)
  private String paypalRefundId;
  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt = OffsetDateTime.now();
  @Column(name = "approved_at")
  private OffsetDateTime approvedAt;
  @Column(name = "completed_at")
  private OffsetDateTime completedAt;

  public String getRefundRequestId() { return refundRequestId; }
  public void setRefundRequestId(String v) { refundRequestId = v; }
  public String getOwnerHash() { return ownerHash; }
  public void setOwnerHash(String v) { ownerHash = v; }
  public String getOrderRequestId() { return orderRequestId; }
  public void setOrderRequestId(String v) { orderRequestId = v; }
  public String getCaptureId() { return captureId; }
  public void setCaptureId(String v) { captureId = v; }
  public BigDecimal getAmount() { return amount; }
  public void setAmount(BigDecimal v) { amount = v; }
  public String getCurrency() { return currency; }
  public void setCurrency(String v) { currency = v; }
  public String getReason() { return reason; }
  public void setReason(String v) { reason = v; }
  public String getDeliveryText() { return deliveryText; }
  public void setDeliveryText(String v) { deliveryText = v; }
  public JsonNode getComparison() { return comparison; }
  public void setComparison(JsonNode v) { comparison = v; }
  public String getState() { return state; }
  public void setState(String v) { state = v; }
  public String getPaypalRefundId() { return paypalRefundId; }
  public void setPaypalRefundId(String v) { paypalRefundId = v; }
  public OffsetDateTime getCreatedAt() { return createdAt; }
  public void setCreatedAt(OffsetDateTime v) { createdAt = v; }
  public OffsetDateTime getApprovedAt() { return approvedAt; }
  public void setApprovedAt(OffsetDateTime v) { approvedAt = v; }
  public OffsetDateTime getCompletedAt() { return completedAt; }
  public void setCompletedAt(OffsetDateTime v) { completedAt = v; }
}
