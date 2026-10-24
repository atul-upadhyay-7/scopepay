package com.scopepay.store;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ClaimedOrderRepository extends JpaRepository<ClaimedOrder, ClaimedOrderId> {
  boolean existsByOrderRequestIdAndClaimantHash(String orderRequestId, String claimantHash);
  List<ClaimedOrder> findByClaimantHash(String claimantHash);
}
