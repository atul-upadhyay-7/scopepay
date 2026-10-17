package com.scopepay.store;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, String> {
  List<LedgerEntry> findByGroupId(String groupId);
  List<LedgerEntry> findByRequestIdOrderByCreatedAtAsc(String requestId);
}
