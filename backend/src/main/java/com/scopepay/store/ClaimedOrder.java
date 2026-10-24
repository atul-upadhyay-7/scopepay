package com.scopepay.store;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.PostLoad;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.OffsetDateTime;
import org.springframework.data.domain.Persistable;

@Entity
@Table(name = "claimed_orders")
@IdClass(ClaimedOrderId.class)
public class ClaimedOrder implements Persistable<ClaimedOrderId> {
  @Transient
  private boolean isNew = true;

  @PostLoad
  void markLoaded() { this.isNew = false; }

  @Override
  public ClaimedOrderId getId() { return new ClaimedOrderId(orderRequestId, claimantHash); }

  @Override
  public boolean isNew() { return isNew; }

  @Id
  @Column(name = "order_request_id", length = 80, nullable = false)
  private String orderRequestId;
  @Id
  @Column(name = "claimant_hash", length = 64, nullable = false)
  private String claimantHash;
  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt = OffsetDateTime.now();

  protected ClaimedOrder() {}

  public ClaimedOrder(String orderRequestId, String claimantHash) {
    this.orderRequestId = orderRequestId;
    this.claimantHash = claimantHash;
  }

  public String getOrderRequestId() { return orderRequestId; }
  public String getClaimantHash() { return claimantHash; }
  public OffsetDateTime getCreatedAt() { return createdAt; }
}
