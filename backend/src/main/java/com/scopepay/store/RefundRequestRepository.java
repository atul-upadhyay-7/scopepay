package com.scopepay.store;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

@Repository
public interface RefundRequestRepository extends JpaRepository<RefundRequest, String> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<RefundRequest> findWithLockByRefundRequestId(String refundRequestId);
  List<RefundRequest> findByOrderRequestId(String orderRequestId);
  List<RefundRequest> findByState(String state);
  List<RefundRequest> findTop20ByOwnerHashOrderByCreatedAtDesc(String ownerHash);
}
