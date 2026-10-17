package com.scopepay.store;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SandboxOrderRepository extends JpaRepository<SandboxOrder, String> {
  Optional<SandboxOrder> findByOrderId(String orderId);
  Optional<SandboxOrder> findByCaptureId(String captureId);
  List<SandboxOrder> findTop20ByOwnerHashOrderByCreatedAtDesc(String ownerHash);
}
