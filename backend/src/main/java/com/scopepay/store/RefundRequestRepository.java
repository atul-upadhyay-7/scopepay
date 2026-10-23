package com.scopepay.store;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RefundRequestRepository extends JpaRepository<RefundRequest, String> {
  List<RefundRequest> findByOrderRequestId(String orderRequestId);
  List<RefundRequest> findByState(String state);
  List<RefundRequest> findTop20ByOwnerHashOrderByCreatedAtDesc(String ownerHash);
}
