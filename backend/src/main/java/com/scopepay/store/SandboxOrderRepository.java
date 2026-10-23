package com.scopepay.store;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface SandboxOrderRepository extends JpaRepository<SandboxOrder, String> {
  Optional<SandboxOrder> findByOrderId(String orderId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select o from SandboxOrder o where o.orderId = :orderId")
  Optional<SandboxOrder> findWithLockByOrderId(@Param("orderId") String orderId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select o from SandboxOrder o where o.requestId = :requestId")
  Optional<SandboxOrder> findWithLockByRequestId(@Param("requestId") String requestId);
  Optional<SandboxOrder> findByCaptureId(String captureId);
  Optional<SandboxOrder> findByRefundId(String refundId);
  List<SandboxOrder> findByStateAndOrderIdIsNotNull(String state);
  List<SandboxOrder> findTop20ByOwnerHashOrderByCreatedAtDesc(String ownerHash);
}
