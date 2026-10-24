package com.scopepay.store;

import java.io.Serializable;
import java.util.Objects;

public class ClaimedOrderId implements Serializable {
  private String orderRequestId;
  private String claimantHash;

  public ClaimedOrderId() {}

  public ClaimedOrderId(String orderRequestId, String claimantHash) {
    this.orderRequestId = orderRequestId;
    this.claimantHash = claimantHash;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof ClaimedOrderId other)) return false;
    return Objects.equals(orderRequestId, other.orderRequestId)
        && Objects.equals(claimantHash, other.claimantHash);
  }

  @Override
  public int hashCode() { return Objects.hash(orderRequestId, claimantHash); }
}
